package example.nucleus.player

import java.lang.foreign.ValueLayout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Smoke test del binding FFM de libmpv (java.lang.foreign). Valida que los downcalls
 * funcionan igual que el binding JNA anterior: crear handle, inicializar, setear una
 * propiedad y destruir. Requiere libmpv-2.dll (mpv-resources/windows del proyecto).
 */
class MpvFfmSmokeTest {

    @Test
    fun `mpv create initialize set property destroy via FFM`() {
        val handle = MpvLib.mpv_create()
        assertNotNull(handle, "mpv_create() devolvió null — libmpv-2.dll no se cargó")

        val rc = MpvLib.mpv_initialize(handle)
        assertEquals(0, rc, "mpv_initialize() falló")

        val setRc = MpvLib.mpv_set_property_string(handle, "video", "no")
        assertEquals(0, setRc, "mpv_set_property_string(video=no) falló")

        val setRc2 = MpvLib.mpv_set_property_string(handle, "pause", "yes")
        assertEquals(0, setRc2, "mpv_set_property_string(pause=yes) falló")

        MpvLib.mpv_command(handle, arrayOf("stop", null))

        MpvLib.mpv_terminate_destroy(handle)
    }

    @Test
    fun `mpv render context SW via FFM`() {
        val handle = MpvLib.mpv_create()
        assertNotNull(handle, "mpv_create() devolvió null — libmpv-2.dll no se cargó")

        val rc = MpvLib.mpv_initialize(handle)
        assertEquals(0, rc, "mpv_initialize() falló")

        MpvLib.mpv_set_property_string(handle, "video", "yes")
        // El render API se consume vía vo=libmpv; sin esto mpv abriría su propia ventana.
        MpvLib.mpv_set_property_string(handle, "vo", "libmpv")
        MpvLib.mpv_set_property_string(handle, "pause", "yes")

        // Igual que en la app: el render context se crea ANTES de cargar el archivo (vo=libmpv
        // lo requiere para poder inicializar el video).
        MpvRenderContext(handle).use { renderCtx ->
            assertTrue(renderCtx.create(), "mpv_render_context_create() falló")

            // Fuente de video sintética (lavfi testsrc) — libmpv trae ffmpeg embebido.
            MpvLib.mpv_command(handle, arrayOf("loadfile", "av://lavfi:testsrc=size=320x180:rate=10", "replace", null))

            // Esperar PLAYBACK_RESTART (el archivo está decodificándose).
            val deadline = System.currentTimeMillis() + 3000
            var restarted = false
            while (System.currentTimeMillis() < deadline && !restarted) {
                val evPtr = MpvLib.mpv_wait_event(handle, 0.5) ?: continue
                val ev = evPtr.reinterpret(64)
                if (ev.get(ValueLayout.JAVA_INT, 0L) == MpvLib.MPV_EVENT_PLAYBACK_RESTART) restarted = true
            }
            assertTrue(restarted, "el archivo av://lavfi:testsrc no inició a tiempo")

            // El primer frame puede tardar unos ticks; sondear update()+render() hasta 2s.
            val frameDeadline = System.currentTimeMillis() + 2000
            var rendered = false
            while (System.currentTimeMillis() < frameDeadline && !rendered) {
                val flags = renderCtx.update()
                assertTrue(flags >= 0L, "mpv_render_context_update() devolvió un valor inesperado")
                rendered = renderCtx.render(320, 180)
                if (!rendered) Thread.sleep(50)
            }
            assertTrue(rendered, "no se pudo renderizar ningún frame de testsrc")

            renderCtx.reportSwap()

            val frame = ByteArray(renderCtx.frameBytes)
            val copied = renderCtx.copyFrameInto(frame)
            assertEquals(320 * 180 * 4, copied, "copyFrameInto() copió un número incorrecto de bytes")
        }

        MpvLib.mpv_terminate_destroy(handle)
    }
}
