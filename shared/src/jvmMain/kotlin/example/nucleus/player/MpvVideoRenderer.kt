package example.nucleus.player

import java.util.logging.Logger
import kotlin.concurrent.thread

/**
 * Hilo de render de video sobre el [MpvRenderContext] del [MpvAudioPlayer].
 *
 * Reglas de libmpv respetadas (render.h):
 *  - El hilo de render SOLO llama funciones `mpv_render_*`; el tamaño del video lo entrega
 *    otro hilo (el ticker de [PlayerService]) vía [onVideoSize] y se cachea aquí.
 *  - Sin ADVANCED_CONTROL, `render()` maneja su propio timing (bloquea hasta el momento de
 *    mostrar cada frame); se invoca en bucle y mpv redibuja el último frame si no hay uno nuevo.
 *  - Se pausa el bucle mientras mpv no reproduce (pausa/buffering) para conservar el último
 *    frame publicado y evitar esperas largas en render().
 *
 * Publica cada frame como bytes BGRA crudos en un doble buffer con contador de versión; la
 * capa de UI (Compose) los envuelve en un ImageBitmap ([readFrame]).
 */
class MpvVideoRenderer internal constructor(
    private val player: MpvAudioPlayer,
) {
    companion object {
        private val log = Logger.getLogger("MpvVideoRenderer")
        private const val IDLE_SLEEP_MS = 50L
        private const val LOOP_SLEEP_MS = 4L
        private const val PLAY_INTERVAL_NS = 33_000_000L   // ~30 fps mientras reproduce
        private const val IDLE_INTERVAL_NS = 150_000_000L  // pausa/buffering: refresco ocasional
    }

    private val lock = Any()

    /** Doble buffer: el hilo de render escribe en el slot no publicado; el lector copia el publicado. */
    private val buffers = arrayOfNulls<ByteArray>(2)
    private val sizes = arrayOfNulls<Pair<Int, Int>>(2)
    private var latestIndex = -1

    /** Tamaño del video cacheado (lo actualiza el ticker de PlayerService, NO el hilo de render). */
    @Volatile
    private var cachedSize: Pair<Int, Int>? = null

    @Volatile
    var frameVersion: Long = 0L
        private set

    @Volatile
    private var running = false

    @Volatile
    private var renderThread: Thread? = null

    /** Tamaño (ancho x alto) del frame publicado más reciente. */
    val frameSize: Pair<Int, Int>?
        get() = synchronized(lock) { sizes.getOrNull(latestIndex) }

    /** True si hay al menos un frame publicado. */
    val hasFrame: Boolean
        get() = synchronized(lock) { latestIndex >= 0 }

    /** Actualiza el tamaño del video activo; null cuando no hay video (modo solo-audio). */
    fun onVideoSize(size: Pair<Int, Int>?) {
        cachedSize = size
    }

    fun start() {
        if (running) return
        running = true
        renderThread = thread(name = "mpv-video-render", isDaemon = true) { renderLoop() }
    }

    /**
     * Detiene el hilo de render y espera su salida. Es imprescindible llamarlo ANTES de destruir
     * el core de mpv (dispose del player): el render API no tolera llamadas tras terminate_destroy.
     */
    fun stop() {
        running = false
        renderThread?.let { t ->
            try {
                t.join(2000)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
        renderThread = null
    }

    private fun renderLoop() {
        var firstFramePublished = true
        var lastRenderNanos = 0L
        try {
            while (running) {
                val renderCtx = player.renderContext
                if (renderCtx == null || !player.videoActive.value) {
                    Thread.sleep(IDLE_SLEEP_MS)
                    continue
                }
                val size = cachedSize
                if (size == null || size.first <= 0 || size.second <= 0) {
                    Thread.sleep(IDLE_SLEEP_MS)
                    continue
                }
                // Sin ADVANCED_CONTROL, render() maneja su propio timing y mpv redibuja el último
                // frame si no hay uno nuevo. Se invoca por tiempo (~30fps reproduciendo) porque
                // update() no es fiable sin el update callback. Durante pausa/buffering se baja a
                // un refresco ocasional para no quemar CPU copiando frames idénticos.
                val now = System.nanoTime()
                val interval = if (player.isPlaying.value) PLAY_INTERVAL_NS else IDLE_INTERVAL_NS
                if (now - lastRenderNanos >= interval) {
                    if (renderCtx.render(size.first, size.second)) {
                        if (firstFramePublished) {
                            firstFramePublished = false
                            log.info("renderer de video: primer frame publicado ${size.first}x${size.second}")
                        }
                        publishFrame(renderCtx, size)
                    }
                    renderCtx.reportSwap()
                    lastRenderNanos = now
                }
                Thread.sleep(LOOP_SLEEP_MS)
            }
        } catch (e: Throwable) {
            log.warning("mpv-video-render loop died: ${e.message}")
        } finally {
            running = false
        }
    }

    private fun publishFrame(renderCtx: MpvRenderContext, size: Pair<Int, Int>) {
        val bytes = size.first * size.second * 4
        val slot = (latestIndex + 1) % 2
        synchronized(lock) {
            val buf = buffers[slot]?.takeIf { it.size == bytes } ?: ByteArray(bytes)
            renderCtx.copyFrameInto(buf)
            buffers[slot] = buf
            sizes[slot] = size
            latestIndex = slot
            frameVersion++
        }
    }

    /**
     * Copia el frame publicado más reciente a [dst] (debe tener capacidad ≥ frameSize bytes).
     * Devuelve el tamaño del frame copiado o null si no hay frame publicado o [dst] es pequeño.
     */
    fun readFrame(dst: ByteArray): Pair<Int, Int>? {
        synchronized(lock) {
            val idx = latestIndex
            if (idx < 0) return null
            val buf = buffers[idx] ?: return null
            val size = sizes[idx] ?: return null
            if (dst.size < buf.size) return null
            buf.copyInto(dst)
            return size
        }
    }
}
