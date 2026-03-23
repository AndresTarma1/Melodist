package com.example.melodist

import androidx.compose.material.Divider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.example.melodist.data.AppDirs
import com.example.melodist.data.AppPreferences
import com.example.melodist.data.account.AccountManager
import com.example.melodist.di.appModule
import com.example.melodist.navigation.RootComponent
import com.example.melodist.player.PlaybackState
import com.example.melodist.player.PlayerService
import com.example.melodist.player.WindowsMediaSession
import com.example.melodist.viewmodels.PlayerViewModel
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import com.kdroid.composetray.tray.api.Tray
import com.sun.jna.platform.win32.WinUser
import melodist.composeapp.generated.resources.Res
import melodist.composeapp.generated.resources.music_icon
import org.jetbrains.compose.resources.painterResource
import java.awt.Dimension
import java.util.logging.Level
import java.util.logging.Logger
import javax.swing.JRootPane

private val logger = Logger.getLogger("Melodist")

fun main() {
    setupEnvironments()

    val koinApp = try {
        startKoin { modules(appModule) }
    } catch (e: Exception) {
        logger.log(Level.SEVERE, "Error iniciando dependencias", e)
        throw e
    }

    try {
        koinApp.koin.get<PlayerService>().init()
    } catch (e: Exception) {
        logger.log(Level.SEVERE, "Error iniciando servicios de audio", e)
    }

    val playerViewModel = koinApp.koin.get<PlayerViewModel>()
    val mediaSession = koinApp.koin.get<WindowsMediaSession>()
    mediaSession.initialize()
    mediaSession.setCallbacks(
        onPlay = { playerViewModel.togglePlayPause() },
        onPause = { playerViewModel.togglePlayPause() },
        onNext = { playerViewModel.next() },
        onPrevious = { playerViewModel.previous() },
        onStop = { playerViewModel.stop() },
    )
    mediaSession.setPositionProvider { playerViewModel.progressState.value.positionMs }

    application {
        val lifecycle = remember { LifecycleRegistry() }
        val rootComponent = remember {
            RootComponent(
                componentContext = DefaultComponentContext(lifecycle = lifecycle),
                musicRepository = koinApp.koin.get(),
                searchRepository = koinApp.koin.get()
            )
        }

        val windowState = rememberWindowState(
            width = 1200.dp, height = 600.dp
        )
        var isVisible by remember { mutableStateOf(true) }
        val minimizeToTray by AppPreferences.minimizeToTray.collectAsState()

        fun doExit() {
            mediaSession.release()
            try { koinApp.koin.get<PlayerService>().release() } catch (_: Exception) {}
            stopKoin()
            exitApplication()
        }

        if (!isVisible || minimizeToTray) {
            val playerState by playerViewModel.uiState.collectAsState()
            val isPlaying = playerState.playbackState == PlaybackState.PLAYING

            Tray(
                icon = Icons.Filled.MusicNote,
                tooltip = playerState.currentSong?.title ?: "Melodist",
                primaryAction = { isVisible = !isVisible },
            ) {
                Item(label = if (isPlaying) "Pausar" else "Reproducir", onClick = { playerViewModel.togglePlayPause() })
                Item(label = "Siguiente", onClick = { playerViewModel.next() })
                Item(label = "Anterior",  onClick = { playerViewModel.previous() })
                Divider()
                Item(label = "Abrir Melodist", onClick = { isVisible = true })
                Item(label = "Salir",          onClick = { doExit() })
            }
        }



        val appIcon = painterResource(Res.drawable.music_icon)
        Window(
            onCloseRequest = { if (minimizeToTray) isVisible = false else doExit() },
            visible = isVisible,
            title = "Melodist",
            state = windowState,
            icon = appIcon,
            undecorated = true,
            transparent = false,
        ) {
            window.minimumSize = Dimension(800, 500)


            App(
                rootComponent = rootComponent,
                windowState = windowState,
                onClose = { if (minimizeToTray) isVisible = false else doExit() }
            )
        }
    }
}

private fun setupEnvironments() {
    AppDirs.ensureDirectories()
    val tmpDir = AppDirs.dataRoot.resolve("tmp").also { it.mkdirs() }
    System.setProperty("java.io.tmpdir", tmpDir.absolutePath)
    AccountManager.init()

    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        logger.log(Level.SEVERE, "Error crítico en ${thread.name}", throwable)
    }
}