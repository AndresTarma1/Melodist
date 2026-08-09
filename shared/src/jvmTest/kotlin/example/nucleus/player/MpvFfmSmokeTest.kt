package example.nucleus.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

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
}
