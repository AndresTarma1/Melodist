package com.example.melodist.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.melodist.ui.components.artwork.LocalArtworkColors
import com.example.melodist.viewmodels.PlayerProgressState
import com.example.melodist.viewmodels.PlayerUiState

// ─────────────────────────────────────────────────────────────────────────────
// NOW PLAYING PANEL — shell + fondo + selección responsive de layout
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun NowPlayingPanel(
    state: PlayerUiState,
    progressState: PlayerProgressState,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onVolumeChange: (Int) -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onCollapse: () -> Unit,
    onQueueItemClick: (Int) -> Unit,
    onNavigate: ((com.example.melodist.navigation.Route) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val song = state.currentSong ?: return
    val artworkColors = LocalArtworkColors.current



        // 5. Layout principal
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            if (maxWidth >= 800.dp) {
                WideLayout(
                    state = state,
                    progressState = progressState,
                    song = song,
                    onTogglePlayPause = onTogglePlayPause,
                    onNext = onNext,
                    onPrevious = onPrevious,
                    onSeek = onSeek,
                    onVolumeChange = onVolumeChange,
                    onToggleShuffle = onToggleShuffle,
                    onToggleRepeat = onToggleRepeat,
                    onCollapse = onCollapse,
                    onQueueItemClick = onQueueItemClick,
                    artworkColors = artworkColors,
                    onNavigate = onNavigate
                )
            } else {
                CompactLayout(
                    state = state,
                    progressState = progressState,
                    song = song,
                    onTogglePlayPause = onTogglePlayPause,
                    onNext = onNext,
                    onPrevious = onPrevious,
                    onSeek = onSeek,
                    onVolumeChange = onVolumeChange,
                    onToggleShuffle = onToggleShuffle,
                    onToggleRepeat = onToggleRepeat,
                    onCollapse = onCollapse,
                    artworkColors = artworkColors,
                    onNavigate = onNavigate
                )
            }
        }
    }
