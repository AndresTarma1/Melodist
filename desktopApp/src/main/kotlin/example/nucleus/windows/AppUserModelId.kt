package example.nucleus.windows

import com.sun.jna.Native
import com.sun.jna.WString
import com.sun.jna.win32.StdCallLibrary
import io.github.aakira.napier.Napier

/**
 * Registra el AppUserModelID del proceso (shell32.SetCurrentProcessExplicitAppUserModelID).
 *
 * Windows solo muestra el panel de medios del Sistema (SMTC / volumen / centro de notificaciones) para
 * aplicaciones de escritorio no empaquetadas si el proceso tiene un AppUserModelID explícito. La librería
 * dev.toastbits:mediasession no lo hace (en 0.1.1 setIdentity() es un no-op en Windows), por eso hay que
 * llamarlo aquí, al inicio del programa y antes de crear cualquier ventana.
 */
object AppUserModelId {
    private const val AUMID = "Tarma.MusicPlayer"

    private interface Shell32 : StdCallLibrary {
        fun SetCurrentProcessExplicitAppUserModelID(appId: WString?): Int
    }

    private val shell32: Shell32 by lazy {
        Native.load("shell32", Shell32::class.java)
    }

    /** Llama SetCurrentProcessExplicitAppUserModelID; no lanza si falla (la app sigue funcionando). */
    fun register() {
        try {
            val hr = shell32.SetCurrentProcessExplicitAppUserModelID(WString(AUMID))
            if (hr == 0) {
                Napier.i("[appuserid] AppUserModelID registrado: $AUMID")
            } else {
                Napier.w("[appuserid] SetCurrentProcessExplicitAppUserModelID falló hr=0x${hr.toString(16)}")
            }
        } catch (error: Throwable) {
            Napier.w("[appuserid] no se pudo registrar AppUserModelID: ${error.message}")
        }
    }
}
