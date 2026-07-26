package com.example.musicApp.ui.screens

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.musicApp.navigation.Route
import com.example.musicApp.ui.components.background.BackgroundStyle
import com.example.musicApp.ui.components.player.NowPlayingLayout
import com.example.musicApp.ui.components.player.NowPlayingTab
import com.example.musicApp.viewmodels.PlayerViewModel

@Composable
fun NowPlayingScreen(
    viewModel: PlayerViewModel,
    onNavigate: (Route) -> Unit,
    onBack: () -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    val playerState by viewModel.uiState.collectAsState()
    val currentLyrics by viewModel.currentLyrics.collectAsState()
    val currentSongMediaInfo by viewModel.currentMediaInfo.collectAsState()
    val currentSong = playerState.currentSong

    var selectedTab by remember { mutableStateOf(NowPlayingTab.QUEUE) }

    if (currentSong != null) {

            NowPlayingLayout(
                state = playerState,
                song = currentSong,
                onCollapse = onBack,
                onNavigate = { route ->
                    onNavigate(route)
                },
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it },
                lyrics = currentLyrics,
                mediaInfo = currentSongMediaInfo,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
            )
        }

}
