package com.example.melodist.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.melodist.player.PlaybackState
import com.example.melodist.viewmodels.PlayerProgressState
import com.example.melodist.viewmodels.PlayerUiState

@Composable
internal fun MiniPlayerContent(
    state: PlayerUiState,
    progressState: PlayerProgressState,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onClickExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    val song = state.currentSong ?: return

    val progress = remember(progressState.positionMs, progressState.durationMs) {
        if (progressState.durationMs > 0) progressState.positionMs.toFloat() / progressState.durationMs.toFloat() else 0f
    }

    val isError = state.playbackState == PlaybackState.ERROR

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)
            .clickable(onClick = onClickExpand)
            .pointerHoverIcon(PointerIcon.Hand),
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.6f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(4.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                strokeCap = StrokeCap.Round,
                gapSize = 0.dp
            )

            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MelodistImage(
                    url = song.thumbnail,
                    contentDescription = song.title,
                    modifier = Modifier.width(64.dp).height(36.dp),
                    shape = RoundedCornerShape(8.dp),
                    placeholderType = PlaceholderType.SONG,
                    iconSize = 18.dp
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    AnimatedContent(
                        targetState = isError,
                        transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
                        label = "subtitleContent"
                    ) { hasError ->
                        Text(
                            text = if (hasError) state.error ?: "Error de reproducción" else song.artists.joinToString(", ") { it.name },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (hasError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    IconButton(
                        onClick = onPrevious,
                        modifier = Modifier.size(40.dp).pointerHoverIcon(PointerIcon.Hand)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SkipPrevious,
                            contentDescription = "Anterior",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    FilledIconButton(
                        onClick = onTogglePlayPause,
                        modifier = Modifier.size(48.dp).pointerHoverIcon(PointerIcon.Hand),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        when (state.playbackState) {
                            PlaybackState.LOADING, PlaybackState.BUFFERING -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    strokeWidth = 2.5.dp,
                                    strokeCap = StrokeCap.Round
                                )
                            }
                            else -> {
                                AnimatedContent(
                                    targetState = state.playbackState == PlaybackState.PLAYING,
                                    transitionSpec = {
                                        (scaleIn(tween(200)) + fadeIn(tween(200))) togetherWith
                                            (scaleOut(tween(150)) + fadeOut(tween(150)))
                                    },
                                    label = "playPauseIcon"
                                ) { isPlaying ->
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                        contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }
                        }
                    }

                    IconButton(
                        onClick = onNext,
                        modifier = Modifier.size(40.dp).pointerHoverIcon(PointerIcon.Hand)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SkipNext,
                            contentDescription = "Siguiente",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}
