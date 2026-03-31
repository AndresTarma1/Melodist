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

fun main() {

    System.setProperty("skiko.renderApi", "OPENGL")

    setupEnvironments()

    val koinApp = startKoin { modules(appModule) }.also {
        runCatching { it.koin.get<PlayerService>().init() }
    }

    val playerViewModel = koinApp.koin.get<PlayerViewModel>()
    val downloadViewModel = koinApp.koin.get<DownloadViewModel>()

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
    System.setProperty(
        "java.io.tmpdir",
        AppDirs.dataRoot.resolve("tmp").also { it.mkdirs() }.absolutePath,
    )
    AccountManager.init()
}