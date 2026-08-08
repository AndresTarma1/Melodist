package example.nucleus

import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import coil3.compose.setSingletonImageLoaderFactory
import com.arkivanov.decompose.DecomposeSettings
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.decompose.ExperimentalDecomposeApi
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import dev.nucleusframework.application.NucleusBackend
import dev.nucleusframework.application.nucleusApplication
import example.nucleus.bootstrap.AppEnvironment
import example.nucleus.bootstrap.JvmConfigLauncher
import example.nucleus.bootstrap.PlatformCrashHandler
import example.nucleus.data.account.AccountManager
import example.nucleus.data.repository.UserPreferencesRepository
import example.nucleus.di.appModule
import example.nucleus.di.dataStoreModule
import example.nucleus.lifecycle.AppLifecycleManager
import example.nucleus.listentogether.ListenTogetherManager
import example.nucleus.navigation.RootComponent
import example.nucleus.overlay.GlobalHotkeyManager
import example.nucleus.player.WindowsMediaSession
import example.nucleus.ui.components.CoilSetup
import example.nucleus.utils.OfflineModeController
import example.nucleus.viewmodels.AppViewModel
import example.nucleus.windows.AppUserModelId
import example.nucleus.viewmodels.DownloadViewModel
import example.nucleus.viewmodels.PlayerViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.koin.core.context.GlobalContext.startKoin


@OptIn(ExperimentalDecomposeApi::class)
fun main() = nucleusApplication(backend = NucleusBackend.Tao) {
    // Windows solo muestra el panel de medios del sistema si el proceso tiene un AppUserModelID explícito.
    // Debe llamarse antes de crear cualquier ventana.
    AppUserModelId.register()

    // Configuración inicial
    DecomposeSettings.update { DecomposeSettings(mainThreadCheckEnabled = false) }
    AppEnvironment.initialize()
    PlatformCrashHandler.register()

    // Inicializar Koin
    val koinApp = PlatformCrashHandler.runSafely("Error al iniciar Koin") {
        startKoin { modules(appModule, dataStoreModule) }
    }
    val koin = koinApp.koin

    val userPreferencesRepository = PlatformCrashHandler.runSafely("Error creando UserPreferencesRepository") {
        koin.get<UserPreferencesRepository>()
    }
    runBlocking {
        userPreferencesRepository.migrateDisabledIslandsLayout()
    }

    // Inicializar AccountManager
    PlatformCrashHandler.runSafely("Error iniciando AccountManager") {
        val dataStore = koin.get<DataStore<Preferences>>()
        AccountManager.init(dataStore)
    }

    // Configurar app
    koin.get<JvmConfigLauncher>().applySync()
    koin.get<OfflineModeController>()

    // Obtener ViewModels
    val playerViewModel = PlatformCrashHandler.runSafely("Error creando PlayerViewModel") {
        koin.get<PlayerViewModel>()
    }
    val downloadViewModel = PlatformCrashHandler.runSafely("Error creando DownloadViewModel") {
        koin.get<DownloadViewModel>()
    }
    val appViewModel = PlatformCrashHandler.runSafely("Error creando AppViewModel") {
        koin.get<AppViewModel>()
    }
    val lifecycleManager = koin.get<AppLifecycleManager>()

    // Inicializar servicios nativos en background
    Thread {
        PlatformCrashHandler.runSafely("Error inicializando reproductor (mpv)") {
            playerViewModel.initialize()
        }

            java.awt.EventQueue.invokeLater {
                PlatformCrashHandler.runSafely("Error iniciando WindowsMediaSession") {
                    val mediaSession = koin.get<WindowsMediaSession>()

                    // Orden de LyriK 0.7.0 (donde funcionaba): inicializar la sesión primero y, solo
                    // cuando existe, conectar devoluciones de llamada y proveedor de posición. El SMTC
                    // de Windows necesita que la creación ocurra en el hilo del bucle de mensajes
                    // (EDT) y que el proceso tenga AppUserModelID (registrado arriba en main()).
                    fun initializeWithRetry(attempt: Int) {
                        mediaSession.initialize()
                        if (mediaSession.isInitialized()) {
                            mediaSession.setCallbacks(
                                onPlay = { playerViewModel.togglePlayPause() },
                                onPause = { playerViewModel.togglePlayPause() },
                                onNext = { playerViewModel.next() },
                                onPrevious = { playerViewModel.previous() },
                                onStop = { playerViewModel.stop() },
                            )
                            mediaSession.setPositionProvider { playerViewModel.progressState.value.positionMs }
                            return
                        }
                        if (attempt < 9) {
                            javax.swing.Timer(1000) { initializeWithRetry(attempt + 1) }
                                .apply { isRepeats = false }
                                .start()
                        } else {
                            val reason = mediaSession.initError() ?: "desconocido"
                            PlatformCrashHandler.logStartupError(
                                "MediaSession",
                                RuntimeException("No se pudo inicializar el MediaSession: $reason"),
                            )
                        }
                    }
                    initializeWithRetry(0)
                }
            }

        PlatformCrashHandler.runSafely("Error iniciando ListenTogetherManager") {
            val listenTogetherManager = koin.get<ListenTogetherManager>()
            listenTogetherManager.initialize()
            listenTogetherManager.setPlayer(playerViewModel)
        }

        PlatformCrashHandler.runSafely("Error iniciando GlobalHotkeyManager") {
            koin.get<GlobalHotkeyManager>().start()
        }
    }.apply { name = "nucleus-deferred-init"; isDaemon = true }.start()

    // Restaurar estado de ventana
    val saved = runBlocking {
        Triple(
            userPreferencesRepository.windowWidth.first(),
            userPreferencesRepository.windowHeight.first(),
            userPreferencesRepository.windowMaximized.first(),
        )
    }

    setSingletonImageLoaderFactory { context -> CoilSetup.createImageLoader(context) }
    val windowState = rememberWindowState(
        width = saved.first.coerceAtLeast(900).dp,
        height = saved.second.coerceAtLeast(600).dp,
        position = WindowPosition(Alignment.Center),
        placement = if (saved.third) WindowPlacement.Maximized else WindowPlacement.Floating,
    )
    val lifecycle = remember { LifecycleRegistry() }
    val rootComponent = remember {
        RootComponent(DefaultComponentContext(lifecycle))
    }

    App(
        rootComponent = rootComponent,
        appViewModel = appViewModel,
        playerViewModel = playerViewModel,
        downloadViewModel = downloadViewModel,
        userPreferences = userPreferencesRepository,
        windowState = windowState,
        onExit = { lifecycleManager.cleanUpAndExit() },
    )
}
