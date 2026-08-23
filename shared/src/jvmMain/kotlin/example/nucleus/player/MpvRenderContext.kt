package example.nucleus.player

import java.lang.foreign.Arena
import java.lang.foreign.MemoryLayout
import java.lang.foreign.MemorySegment
import java.lang.foreign.SegmentAllocator
import java.lang.foreign.ValueLayout
import java.util.logging.Logger

/**
 * Contexto de renderizado de video de libmpv vía render API con backend de software
 * (`MPV_RENDER_API_TYPE_SW`): mpv entrega cada frame como píxeles BGRA en un buffer
 * gestionado por este wrapper, listos para envolver en un ImageBitmap de Compose.
 *
 * Reglas de libmpv respetadas aquí:
 *  - `create()` debe llamarse tras `mpv_initialize` (handle vivo).
 *  - `close()` debe llamarse ANTES de `mpv_terminate_destroy`.
 *  - `update()`/`render()` se invocan desde un único hilo dedicado (el hilo de render).
 *  - No se usa `set_update_callback` (upcall FFM) a propósito: se hace polling de
 *    `update()` desde el hilo de render, evitando metadata de callbacks en GraalVM native-image.
 */
class MpvRenderContext(private val mpvHandle: MemorySegment) : AutoCloseable {

    companion object {
        private val log = Logger.getLogger("MpvRenderContext")
        private const val PARAM_SIZE = 16L

        /** mpv_render_param: { int type; void* data; } — 16 bytes en x64 (int + padding + ptr). */
        private val PARAM_LAYOUT: MemoryLayout = MemoryLayout.structLayout(
            ValueLayout.JAVA_INT.withName("type"),
            MemoryLayout.paddingLayout(4),
            ValueLayout.ADDRESS.withName("data"),
        )

        private fun SegmentAllocator.utf8(s: String): MemorySegment {
            val bytes = s.toByteArray(Charsets.UTF_8)
            val seg = allocate(bytes.size + 1L)
            bytes.forEachIndexed { i, b -> seg.set(ValueLayout.JAVA_BYTE, i.toLong(), b) }
            seg.set(ValueLayout.JAVA_BYTE, bytes.size.toLong(), 0.toByte())
            return seg
        }
    }

    private val arena = Arena.ofShared()
    private var ctx: MemorySegment? = null

    // Buffer de píxeles SW (BGRA, stride = ancho*4). Se realoja al crecer el video; los
    // segmentos viejos viven en el arena hasta close() (resizes raros: coste aceptable).
    private var buffer: MemorySegment? = null
    private var bufferW = 0
    private var bufferH = 0

    val isActive: Boolean get() = ctx != null

    /** Ancho/alto actuales del buffer de píxeles (frameSize de Compose). */
    val frameSize: Pair<Int, Int>? get() = if (buffer != null) bufferW to bufferH else null

    /** Crea el contexto de render con backend de software. Idempotente. */
    fun create(): Boolean {
        if (ctx != null) return true
        return try {
            Arena.ofConfined().use { a ->
                val params = a.allocate(PARAM_LAYOUT, 2)
                setParam(params, 0, MpvLib.MPV_RENDER_PARAM_API_TYPE, a.utf8(MpvLib.MPV_RENDER_API_TYPE_SW))
                // Sin MPV_RENDER_PARAM_ADVANCED_CONTROL: mpv mantiene su propio timing de video y
                // render() se encarga de bloquear/entregar frames según corresponda. ADVANCED
                // CONTROL exigiría que el hilo de render nunca espere en llamadas mpv no-render_*
                // (regla de render.h) y puede congelar el core si se incumple; no lo usamos.
                val created = MpvLib.mpv_render_context_create(mpvHandle, params)
                ctx = created
                created != null
            }
        } catch (e: Throwable) {
            log.warning("MpvRenderContext: create falló: ${e.message}")
            false
        }
    }

    /**
     * Consulta los flags de actualización pendientes. `MPV_RENDER_UPDATE_FRAME` indica que hay
     * un frame nuevo listo para [render]. Debe llamarse desde el hilo de render.
     */
    fun update(): Long {
        val c = ctx ?: return 0L
        return try {
            MpvLib.mpv_render_context_update(c)
        } catch (e: Throwable) {
            log.warning("MpvRenderContext: update falló: ${e.message}")
            0L
        }
    }

    /**
     * Renderiza el próximo frame (si lo hay) en el buffer interno. Debe llamarse desde el hilo
     * de render, idealmente tras ver `MPV_RENDER_UPDATE_FRAME` en [update].
     *
     * @return true si el renderizado terminó sin error y el buffer contiene un frame nuevo.
     */
    fun render(width: Int, height: Int): Boolean {
        val c = ctx ?: return false
        if (width <= 0 || height <= 0) return false
        val buf = ensureBuffer(width, height) ?: return false
        return try {
            var rc = -1
            Arena.ofConfined().use { a ->
                val params = a.allocate(PARAM_LAYOUT, 5)

                val sizeSeg = a.allocate(ValueLayout.JAVA_INT, 2)
                sizeSeg.set(ValueLayout.JAVA_INT, 0, width)
                sizeSeg.set(ValueLayout.JAVA_INT, 4, height)
                setParam(params, 0, MpvLib.MPV_RENDER_PARAM_SW_SIZE, sizeSeg)

                setParam(params, 1, MpvLib.MPV_RENDER_PARAM_SW_FORMAT, a.utf8("bgr0"))

                val strideSeg = a.allocate(ValueLayout.JAVA_INT)
                strideSeg.set(ValueLayout.JAVA_INT, 0, width * 4)
                setParam(params, 2, MpvLib.MPV_RENDER_PARAM_SW_STRIDE, strideSeg)

                setParam(params, 3, MpvLib.MPV_RENDER_PARAM_SW_POINTER, buf)

                rc = MpvLib.mpv_render_context_render(c, params)
            }
            if (rc < 0) {
                log.warning("MpvRenderContext: render devolvió error code=$rc")
            }
            rc >= 0
        } catch (e: Throwable) {
            log.warning("MpvRenderContext: render falló: ${e.message}")
            false
        }
    }

    /** Informa a mpv de que el frame fue presentado (mantiene el timing de video correcto). */
    fun reportSwap() {
        val c = ctx ?: return
        try {
            MpvLib.mpv_render_context_report_swap(c)
        } catch (e: Throwable) {
            log.warning("MpvRenderContext: report_swap falló: ${e.message}")
        }
    }

    /**
     * Copia el frame renderizado (BGRA, `bufferW*bufferH*4` bytes) a [dst]. [dst] debe tener
     * capacidad al menos `frameBytes`. Devuelve el número de bytes copiados.
     */
    fun copyFrameInto(dst: ByteArray): Int {
        val buf = buffer ?: return 0
        val count = bufferW * bufferH * 4
        MemorySegment.copy(buf, 0L, MemorySegment.ofArray(dst), 0L, count.toLong())
        return count
    }

    /** Bytes del frame completo (w*h*4). */
    val frameBytes: Int get() = bufferW * bufferH * 4

    private fun ensureBuffer(width: Int, height: Int): MemorySegment? {
        if (buffer != null && bufferW == width && bufferH == height) return buffer
        val bytes = width.toLong() * height.toLong() * 4L
        if (bytes <= 0 || bytes > Int.MAX_VALUE.toLong()) {
            log.warning("MpvRenderContext: tamaño de frame inválido ${width}x$height")
            return null
        }
        buffer = arena.allocate(bytes)
        bufferW = width
        bufferH = height
        return buffer
    }

    private fun setParam(arr: MemorySegment, index: Long, type: Int, data: MemorySegment) {
        val off = index * PARAM_SIZE
        arr.set(ValueLayout.JAVA_INT, off, type)
        arr.set(ValueLayout.ADDRESS, off + 8, data)
    }

    /** Libera el contexto de render. Debe llamarse antes de mpv_terminate_destroy. Idempotente. */
    override fun close() {
        val c = ctx ?: run { arena.close(); return }
        try {
            MpvLib.mpv_render_context_free(c)
        } catch (e: Throwable) {
            log.warning("MpvRenderContext: free falló: ${e.message}")
        }
        ctx = null
        buffer = null
        bufferW = 0
        bufferH = 0
        arena.close()
    }
}
