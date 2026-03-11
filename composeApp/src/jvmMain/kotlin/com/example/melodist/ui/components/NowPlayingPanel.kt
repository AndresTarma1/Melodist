package com.example.melodist.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
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

    val dominant by animateColorAsState(
        artworkColors.dominant,
        tween(900, easing = FastOutSlowInEasing),
        label = "dominant"
    )
    val vibrant by animateColorAsState(
        artworkColors.vibrant,
        tween(900, easing = FastOutSlowInEasing),
        label = "vibrant"
    )

    Box(modifier = modifier.fillMaxSize()) {
        val bgUrl = upscaleThumbnailUrl(song.thumbnail, 480)
        AsyncImage(
            model = bgUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    renderEffect = BlurEffect(
                        radiusX = 60f,
                        radiusY = 60f,
                        edgeTreatment = TileMode.Clamp
                    )
                    scaleX = 1.15f
                    scaleY = 1.15f
                }
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colorStops = arrayOf(
                            0.00f to vibrant.copy(alpha = 0.30f),
                            0.50f to dominant.copy(alpha = 0.15f),
                            1.00f to Color.Transparent
                        ),
                        center = Offset(0f, 0f),
                        radius = Float.MAX_VALUE
                    )
                )
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.00f to Color.Black.copy(alpha = 0.05f),
                        0.45f to Color.Black.copy(alpha = 0.30f),
                        0.75f to Color.Black.copy(alpha = 0.65f),
                        1.00f to Color.Black.copy(alpha = 0.88f)
                    )
                )
        )

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
}
