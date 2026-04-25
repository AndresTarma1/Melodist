package com.example.melodist.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.melodist.player.PlaybackState
import com.example.melodist.utils.LocalPlayerViewModel
import com.example.melodist.utils.isWideThumbnail
import com.example.melodist.viewmodels.PlayerProgressState
import com.example.melodist.viewmodels.QueueSource
import com.example.melodist.viewmodels.RepeatMode
import com.example.melodist.utils.upscaleThumbnailUrl


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MiniPlayer(
    progressState: PlayerProgressState,
    onClickExpand: () -> Unit,
    onToggleNowPlaying: () -> Unit,
    isNowPlayingExpanded: Boolean,
    onToggleQueue: () -> Unit,
    isQueueVisible: Boolean,
    modifier: Modifier = Modifier
) {
    val playerViewModel = LocalPlayerViewModel.current
    val state by playerViewModel.uiState.collectAsState()
    val song = state.currentSong ?: return

    val computedProgress = remember(progressState.positionMs, progressState.durationMs) {
        if (progressState.durationMs > 0)
            progressState.positionMs.toFloat() / progressState.durationMs.toFloat()
        else 0f
    }
    var seekValue by remember { mutableStateOf<Float?>(null) }
    val sliderProgress = seekValue ?: computedProgress
    val isError = state.playbackState == PlaybackState.ERROR

    val isPlaying = state.playbackState == PlaybackState.PLAYING
    val isLoading = state.playbackState == PlaybackState.LOADING

    val imageUrl = song.thumbnailUrl
    val ratio = if(isWideThumbnail(imageUrl)) 16f/ 9f else 1f

    Surface(
        modifier = modifier.fillMaxWidth().height(88.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 2.dp
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            HorizontalDivider(
                modifier = Modifier.align(Alignment.TopCenter),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                thickness = 0.5.dp
            )

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // —─ IZQUIERDA: Portada e Info —─—─—─—─—─—─—─—─—─—
                BoxWithConstraints(
                    modifier = Modifier.weight(1f).padding(vertical = 10.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    val infoHeight = maxHeight
                    val thumbSize = (infoHeight * 0.85f).coerceAtMost(64.dp)
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        MelodistImage(
                            url = upscaleThumbnailUrl(song.thumbnailUrl, 256),
                            contentDescription = song.title,
                            modifier = Modifier
                                .sizeIn(maxWidth = thumbSize * ratio, maxHeight = thumbSize)
                                .aspectRatio(ratio)
                                .clickable(onClick = onClickExpand)
                                .pointerHoverIcon(PointerIcon.Hand),
                            shape = RoundedCornerShape(8.dp),
                            contentScale = ContentScale.Crop,
                            placeholderType = PlaceholderType.SONG,
                            iconSize = 24.dp
                        )

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable(onClick = onClickExpand)
                                .pointerHoverIcon(PointerIcon.Hand),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = song.title,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (isError) state.error ?: "Error" else song.artists.joinToString(", ") { it.name },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // —─ CENTRO: Controles y Progreso (Espaciado corregido) —─—─—─—─—─
                Column(
                    modifier = Modifier.weight(1.6f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
                    ) {
                        // Botón Aleatorio
                        IconButton(
                            onClick = { playerViewModel.toggleShuffle() },
                            modifier = Modifier.size(36.dp).pointerHoverIcon(PointerIcon.Hand),
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = if (state.isShuffled) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                else Color.Transparent,
                            )
                        ) {
                            Icon(
                                Icons.Rounded.Shuffle, "Aleatorio",
                                modifier = Modifier.size(20.dp),
                                tint = if (state.isShuffled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(
                            onClick = { playerViewModel.previous() },
                            modifier = Modifier.size(40.dp).pointerHoverIcon(PointerIcon.Hand)
                        ) {
                            Icon(
                                Icons.Rounded.SkipPrevious, "Anterior",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        FilledIconButton(
                            onClick = { playerViewModel.togglePlayPause() },
                            modifier = Modifier.size(46.dp).pointerHoverIcon(PointerIcon.Hand),
                            shape = CircleShape,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.onSurface,
                                contentColor = MaterialTheme.colorScheme.surface
                            )
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = { playerViewModel.next() },
                            modifier = Modifier.size(40.dp).pointerHoverIcon(PointerIcon.Hand)
                        ) {
                            Icon(
                                Icons.Rounded.SkipNext, "siguiente",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        // Botón Repetir
                        IconButton(
                            onClick = { playerViewModel.toggleRepeat() },
                            modifier = Modifier.size(36.dp).pointerHoverIcon(PointerIcon.Hand),
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = if (state.repeatMode != RepeatMode.OFF) MaterialTheme.colorScheme.primary.copy(
                                    alpha = 0.12f
                                )
                                else Color.Transparent
                            )
                        ) {
                            val isRepeatOff = state.repeatMode == RepeatMode.OFF
                            val repeatIcon =
                                if (state.repeatMode == RepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat
                            Icon(
                                repeatIcon, "Repetir",
                                modifier = Modifier.size(20.dp),
                                tint = if (isRepeatOff) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Slider con mayor separación visual
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            formatPlayerTimeValue(seekValue?.let { (it * progressState.durationMs).toLong() }
                                ?: progressState.positionMs),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(32.dp)
                        )

                        CompactSlider(
                            value = sliderProgress,
                            onValueChange = { seekValue = it },
                            onValueChangeFinished = {
                                seekValue?.let { playerViewModel.seekTo((it * progressState.durationMs).toLong()) }
                                seekValue = null
                            },
                            modifier = Modifier.weight(1f)
                        )

                        Text(
                            formatPlayerTimeValue(progressState.durationMs),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(32.dp)
                        )
                    }
                }

                // —— DERECHA: Botón Extra, Cola y Volumen ———————————————————
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    var isVolumeHovered by remember { mutableStateOf(false) }

                    Row(
                        modifier = Modifier
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        when (event.type) {
                                            PointerEventType.Enter -> isVolumeHovered = true
                                            PointerEventType.Exit -> isVolumeHovered = false
                                        }
                                    }
                                }
                            }
                            .pointerHoverIcon(PointerIcon.Hand),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val volumeFloat = (state.volume.coerceIn(0, 100)) / 100f

                        val sliderWidth by animateDpAsState(
                            if (isVolumeHovered) 80.dp else 0.dp,
                            tween(200, easing = FastOutSlowInEasing),
                            label = "volWidth"
                        )
                        val sliderAlpha by animateFloatAsState(
                            if (isVolumeHovered) 1f else 0f,
                            tween(180), label = "volAlpha"
                        )

                        if (sliderWidth > 0.dp) {
                            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                                Slider(
                                    value = volumeFloat,
                                    onValueChange = { playerViewModel.setVolume((it * 100).toInt()) },
                                    modifier = Modifier
                                        .width(sliderWidth)
                                        .height(16.dp)
                                        .graphicsLayer { alpha = sliderAlpha },
                                    colors = SliderDefaults.colors(
                                        thumbColor = MaterialTheme.colorScheme.onSurface,
                                        activeTrackColor = MaterialTheme.colorScheme.onSurface,
                                        inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f),
                                        activeTickColor = Color.Transparent,
                                        inactiveTickColor = Color.Transparent
                                    )
                                )
                            }
                        }

                        IconButton(
                            onClick = { playerViewModel.setVolume(if (volumeFloat > 0f) 0 else 80) },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                when {
                                    volumeFloat == 0f -> Icons.AutoMirrored.Rounded.VolumeOff
                                    volumeFloat < 0.4f -> Icons.AutoMirrored.Rounded.VolumeDown
                                    else -> Icons.AutoMirrored.Rounded.VolumeUp
                                },
                                "Volumen",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = onToggleQueue,
                        modifier = Modifier.size(40.dp).pointerHoverIcon(PointerIcon.Hand),
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = if (isQueueVisible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            containerColor = if (isQueueVisible) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            else Color.Transparent
                        )
                    ) {
                        Icon(Icons.AutoMirrored.Filled.QueueMusic, "Cola", modifier = Modifier.size(24.dp))
                    }

                    IconButton(
                        onClick = onToggleNowPlaying,
                        modifier = Modifier.size(40.dp).pointerHoverIcon(PointerIcon.Hand),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = if (isNowPlayingExpanded) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            else Color.Transparent
                        )
                    ) {
                        val rotation by animateFloatAsState(
                            targetValue = if (isNowPlayingExpanded) 0f else 180f,
                            animationSpec = tween(
                                durationMillis = 300,
                                easing = FastOutSlowInEasing
                            )
                        )
                        Icon(
                            Icons.Default.ArrowDropDown,
                            "Opciones",
                            modifier = Modifier.size(32.dp).graphicsLayer(rotationZ = rotation),
                            tint = if (isNowPlayingExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompactSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isDragged by interactionSource.collectIsDraggedAsState()
    val isHovered by interactionSource.collectIsHoveredAsState()
    val active = isDragged || isHovered

    val trackColor by animateColorAsState(
        if (active) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurface,
        tween(150), label = "trackColor"
    )

    // Desactiva el tamaño táctil mínimo de Material 3 para evitar saltos en la UI
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            interactionSource = interactionSource,
            modifier = modifier.height(16.dp),
            colors = SliderDefaults.colors(
                thumbColor = trackColor,
                activeTrackColor = trackColor,
                inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f),
                activeTickColor = trackColor,
                inactiveTickColor = trackColor
            )
        )
    }
}
