package com.example.melodist

import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.example.melodist.data.AppDirs
import com.example.melodist.data.account.AccountManager
import com.example.melodist.data.repository.UserPreferencesRepository
import com.example.melodist.di.appModule
import com.example.melodist.di.dataStoreModule
import com.example.melodist.navigation.RootComponent
import com.example.melodist.player.PlayerService
import com.example.melodist.player.WindowsMediaSession
import com.example.melodist.viewmodels.AppViewModel
import com.example.melodist.viewmodels.DownloadViewModel
import com.example.melodist.viewmodels.PlayerViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.LocalDateTime
import kotlin.system.exitProcess

fun main() {

    setupEnvironments()

    System.setProperty("compose.swing.render.on.graphics", "true")
    System.setProperty("compose.interop.blending", "true")
    System.setProperty("compose.layers.type", "COMPONENT")

    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        logStartupError("Uncaught exception on thread '${thread.name}'", throwable)
    }

    val koinApp = try {
        startKoin { modules(appModule, dataStoreModule) }.also {
            runCatching { it.koin.get<PlayerService>().init() }
                .onFailure { error -> logStartupError("Error inicializando PlayerService", error) }
        }
    } catch (e: Throwable) {
        logStartupError("Error al iniciar Koin", e)
        throw e
    }

    val playerViewModel = try {
        koinApp.koin.get<PlayerViewModel>()
    } catch (e: Throwable) {
        logStartupError("Error creando PlayerViewModel", e)
        throw e
    }
    val downloadViewModel = try {
        koinApp.koin.get<DownloadViewModel>()
    } catch (e: Throwable) {
        logStartupError("Error creando DownloadViewModel", e)
        throw e
    }
    val appViewModel = try {
        koinApp.koin.get<AppViewModel>()
    } catch (e: Throwable) {
        logStartupError("Error creando AppViewModel", e)
        throw e
    }

    koinApp.koin.get<WindowsMediaSession>().apply {
        initialize()
        setCallbacks(
            onPlay = { playerViewModel.togglePlayPause() },
            onPause = { playerViewModel.togglePlayPause() },
            onNext = { playerViewModel.next() },
            onPrevious = { playerViewModel.previous() },
            onStop = { playerViewModel.stop() },
        )
        setPositionProvider { playerViewModel.progressState.value.positionMs }
    }

    val userPreferencesRepository = try {
        koinApp.koin.get<UserPreferencesRepository>()
    } catch (e: Throwable) {
        logStartupError("Error creando UserPreferencesRepository", e)
        throw e
    }

    val initialWidth: Int
    val initialHeight: Int
    val initialMaximized: Boolean

    runBlocking {
        initialWidth = userPreferencesRepository.windowWidth.first()
        initialHeight = userPreferencesRepository.windowHeight.first()
        initialMaximized = userPreferencesRepository.windowMaximized.first()
    }



    application {
        val windowState = rememberWindowState(
            placement = if (initialMaximized) WindowPlacement.Maximized else WindowPlacement.Floating,
            width = initialWidth.dp,
            height = initialHeight.dp,
            position = WindowPosition(Alignment.Center),
        )


        val lifecycle = remember { LifecycleRegistry() }
        val rootComponent = remember {
            RootComponent(
                componentContext = DefaultComponentContext(lifecycle)
            )
        }

        fun doExit() {
            koinApp.koin.get<WindowsMediaSession>().release()
            runCatching { koinApp.koin.get<PlayerService>().release() }
            stopKoin()
            exitProcess(0)
        }

        App(
            rootComponent = rootComponent,
            appViewModel = appViewModel,
            playerViewModel = playerViewModel,
            downloadViewModel = downloadViewModel,
            userPreferences = userPreferencesRepository,
            windowState = windowState,
            onExit = ::doExit
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Setup
// ─────────────────────────────────────────────────────────────────────────────

private fun setupEnvironments() {
    AppDirs.ensureDirectories()
    val tmpDir = AppDirs.tmpDir.also { it.mkdirs() }

    System.setProperty("org.sqlite.tmpdir", tmpDir.absolutePath)
    System.setProperty("java.io.tmpdir", tmpDir.absolutePath)

    AccountManager.init()
}

private fun logStartupError(context: String, throwable: Throwable) {
    runCatching {
        val logsDir = File(AppDirs.dataRoot, "logs")
        if (!logsDir.exists()) logsDir.mkdirs()

        val logFile = File(logsDir, "startup.log")
        val stackTrace = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()
        val entry = buildString {
            appendLine("[${LocalDateTime.now()}] $context")
            appendLine(stackTrace)
            appendLine("------------------------------------------------------------")
        }
        logFile.appendText(entry)
    }
}
