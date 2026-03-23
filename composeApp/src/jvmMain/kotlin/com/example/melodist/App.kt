package com.example.melodist

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.WindowState
import coil3.compose.setSingletonImageLoaderFactory
import com.example.melodist.navigation.NavigationDesktop
import com.example.melodist.navigation.RootComponent
import com.example.melodist.ui.components.ArtworkColors
import com.example.melodist.ui.components.CoilSetup
import com.example.melodist.ui.components.CustomTitleBar
import com.example.melodist.ui.components.LocalArtworkColors
import com.example.melodist.ui.components.WindowResizeBorder
import com.example.melodist.ui.components.rememberArtworkColors
import com.example.melodist.ui.themes.MelodistTheme
import com.example.melodist.viewmodels.PlayerViewModel
import org.koin.compose.koinInject

@Composable
fun FrameWindowScope.App(
    rootComponent: RootComponent,
    windowState: WindowState,
    onClose: () -> Unit,
) {
    setSingletonImageLoaderFactory { context ->
        CoilSetup.createImageLoader(context)
    }

    // Get the current song's thumbnail for dynamic theming
    val playerViewModel: PlayerViewModel = koinInject()
    val playerState by playerViewModel.uiState.collectAsState()
    val artworkColors = rememberArtworkColors(playerState.currentSong?.thumbnail)

    // Provide artwork colors globally so NowPlayingPanel doesn't re-extract them
    CompositionLocalProvider(LocalArtworkColors provides artworkColors) {
        MelodistTheme(artworkColors = artworkColors) {

            Column(modifier = Modifier.fillMaxSize()) {
                // Custom title bar (reemplaza la barra nativa de Windows)
                CustomTitleBar(
                    title = playerState.currentSong?.let { "Melodist — ${it.title}" } ?: "Melodist",
                    windowState = windowState,
                    onClose = onClose,
                )

                // Contenido principal
                NavigationDesktop(rootComponent)
            }
        }
    }
}