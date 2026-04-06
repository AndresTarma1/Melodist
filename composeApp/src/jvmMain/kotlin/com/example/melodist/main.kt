package com.example.melodist

import androidx.compose.runtime.remember
import androidx.compose.ui.window.application
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.example.melodist.data.AppDirs
import com.example.melodist.data.account.AccountManager
import com.example.melodist.di.appModule
import com.example.melodist.navigation.RootComponent
import com.example.melodist.player.PlayerService
import com.example.melodist.player.WindowsMediaSession
import com.example.melodist.viewmodels.DownloadViewModel
import com.example.melodist.viewmodels.PlayerViewModel
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.LocalDateTime

fun main() {

    // Es mejor el Direct3D que el OpenGL, pero en algunos casos puede causar problemas de renderizado, así que se puede forzar el uso de OpenGL si es necesario.
    //System.setProperty("skiko.renderApi", "OPENGL")

    setupEnvironments()

    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        logStartupError("Uncaught exception on thread '${thread.name}'", throwable)
    }

    val koinApp = try {
        startKoin { modules(appModule) }.also {
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

    application {
        val lifecycle = remember { LifecycleRegistry() }
        val rootComponent = remember {
            RootComponent(
                componentContext = DefaultComponentContext(lifecycle),
                musicRepository = koinApp.koin.get(),
                searchRepository = koinApp.koin.get(),
            )
        }

        fun doExit() {
            koinApp.koin.get<WindowsMediaSession>().release()
            runCatching { koinApp.koin.get<PlayerService>().release() }
            stopKoin()
            exitApplication()
        }

        App(
            rootComponent = rootComponent,
            playerViewModel = playerViewModel,
            downloadViewModel = downloadViewModel,
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
