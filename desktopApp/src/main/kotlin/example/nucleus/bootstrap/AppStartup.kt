package example.nucleus.bootstrap

import example.nucleus.listentogether.ListenTogetherManager
import example.nucleus.player.WindowsMediaSession
import example.nucleus.viewmodels.PlayerViewModel
import org.koin.core.context.GlobalContext

/**
 * Inicialización diferida de servicios que no deben bloquear la primera ventana:
 * volumen de mpv, media controls del sistema (SMTC/MPRIS) y Listen Together.
 * Corre en un hilo daemon en segundo plano.
 */
object AppStartup {

    fun startDeferred(playerViewModel: PlayerViewModel) {
        val koin = GlobalContext.get()
        Thread {
            // mpv NO se inicializa aquí: se crea en el primer play() (ahorra ~20 MB de arranque).
            // Solo se hidrata el volumen guardado para que la UI lo muestre desde el inicio.
            PlatformCrashHandler.runSafely("Error cargando volumen inicial") {
                playerViewModel.primeVolume()
            }

            // Validación experimental del binding FFM de mpv en el binario nativo:
            // PALTASOUND_TEST_MPV=1 fuerza mpv_create/initialize al arranque. Si falla,
            // runSafely escribe el error en startup.log. Solo activo con la env var.
            if (System.getenv("PALTASOUND_TEST_MPV") == "1") {
                PlatformCrashHandler.runSafely("mpv init test (FFM)") {
                    playerViewModel.initialize()
                }
            }

            // Media controls del sistema (SMTC en Windows / MPRIS en Linux) vía Nucleus
            // `MediaControlService`. No requiere HWND ni retry: el backend se configura solo.
            PlatformCrashHandler.runSafely("Error iniciando media controls") {
                val mediaSession = koin.get<WindowsMediaSession>()
                mediaSession.setCallbacks(
                    onPlay = { playerViewModel.togglePlayPause() },
                    onPause = { playerViewModel.togglePlayPause() },
                    onNext = { playerViewModel.next() },
                    onPrevious = { playerViewModel.previous() },
                    onStop = { playerViewModel.stop() },
                )
                mediaSession.setPositionProvider { playerViewModel.progressState.value.positionMs }
                mediaSession.initialize()
            }

            PlatformCrashHandler.runSafely("Error iniciando ListenTogetherManager") {
                val listenTogetherManager = koin.get<ListenTogetherManager>()
                listenTogetherManager.initialize()
                listenTogetherManager.setPlayer(playerViewModel)
            }
        }.apply { name = "nucleus-deferred-init"; isDaemon = true }.start()
    }
}
