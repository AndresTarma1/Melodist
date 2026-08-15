package example.nucleus.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.border
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.blurEffect
import dev.chrisbanes.haze.blur.materials.HazeMaterials
import dev.chrisbanes.haze.hazeEffect
import example.nucleus.data.repository.LayoutMode
import example.nucleus.data.repository.MiniPlayerBackgroundStyle
import example.nucleus.data.repository.SeekBarStyle
import example.nucleus.player.PlaybackState
import example.nucleus.ui.components.images.MusicPlayerImage
import example.nucleus.ui.components.images.PlaceholderType
import example.nucleus.ui.components.artwork.LocalArtworkColors
import example.nucleus.ui.components.player.heroCoverElement
import example.nucleus.ui.themes.LocalDimens
import example.nucleus.ui.themes.AppShapes
import example.nucleus.ui.themes.LocalChromeSurface
import example.nucleus.ui.themes.LocalLayoutMode
import example.nucleus.ui.utils.circleAwareShape
import example.nucleus.utils.LocalPlayerViewModel
import example.nucleus.utils.LocalAnimationsEnabled
import example.nucleus.utils.isWideThumbnail
import example.nucleus.viewmodels.PlayerProgressState
import example.nucleus.viewmodels.RepeatMode
import example.nucleus.shared.generated.resources.Res
import example.nucleus.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.modifier.onHover


@Composable
fun MiniPlayer(
    progressState: PlayerProgressState,
    isOnNowPlaying: Boolean,
    onNowPlaying: () -> Unit,
    onToggleQueue: () -> Unit,
    isQueueVisible: Boolean,
    bgTransparent: Boolean = false,
    floating: Boolean = false,
    backgroundStyle: MiniPlayerBackgroundStyle = MiniPlayerBackgroundStyle.TRANSLUCENT,
    hazeState: HazeState? = null,
    modifier: Modifier = Modifier,
    sharedTransitionScope: SharedTransitionScope? = null,
) {
    val playerViewModel = LocalPlayerViewModel.current
    val state by playerViewModel.uiState.collectAsState()
    val volume by playerViewModel.volume.collectAsState()
    val song = state.currentSong ?: return

    val isError = state.playbackState == PlaybackState.ERROR

    val isPlaying = state.playbackState == PlaybackState.PLAYING
    val isLoading = state.playbackState == PlaybackState.LOADING



    val ratio = remember(song.thumbnailUrl) {
        if (isWideThumbnail(song.thumbnailUrl)) 16f / 9f else 1f
    }

    val dimens = LocalDimens.current
    val chromeSurface = LocalChromeSurface.current
    val islands = LocalLayoutMode.current == LayoutMode.ISLANDS
    val square = LocalLayoutMode.current == LayoutMode.SQUARE
    val surfaceShape = if (islands) RoundedCornerShape(dimens.surfaceCorner) else RectangleShape

    val playerContent: @Composable () -> Unit = {
        Box(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BoxWithConstraints(
                    modifier = Modifier.weight(1f).padding(vertical = 10.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    val infoHeight = maxHeight
                    val thumbSize = (infoHeight * 0.85f).coerceAtMost(64.dp)

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val animationsEnabled = LocalAnimationsEnabled.current
                        AnimatedVisibility(
                            visible = !isOnNowPlaying,
                            enter = if (animationsEnabled) fadeIn(tween(220)) + expandHorizontally(tween(220)) else EnterTransition.None,
                            exit = if (animationsEnabled) fadeOut(tween(180)) + shrinkHorizontally(tween(220)) else ExitTransition.None,
                        ) {
                            var isHovered by remember { mutableStateOf(false) }
                            val overlayAlpha =if (isHovered) 0.38f else 0f

                            Box(
                                modifier = Modifier
                                .sizeIn(maxWidth = thumbSize * ratio, maxHeight = thumbSize)
                                .aspectRatio(ratio)
                                .heroCoverElement(song.id, sharedTransitionScope, this)
                                .onHover { isHovered = it }
                                .clickable(onClick = onNowPlaying)
                                .pointerHoverIcon(PointerIcon.Hand)
                            ) {
                                MusicPlayerImage(
                                    url = song.thumbnailUrl,
                                    contentDescription = song.title,
                                    modifier = Modifier.fillMaxSize(),
                                    shape = RoundedCornerShape(4.dp),
                                    contentScale = ContentScale.Crop,
                                    placeholderType = PlaceholderType.SONG,
                                    iconSize = 24.dp,
                                )

                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .alpha(overlayAlpha)
                                        .background(Color.Black)
                                )

                                if (isHovered) {
                                    Box(
                                        modifier = Modifier
                                            .matchParentSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (isOnNowPlaying) Icons.Default.ArrowDropDown else Icons.Default.ArrowDropUp,
                                            modifier = Modifier.size(32.dp),
                                            contentDescription = if (isOnNowPlaying) stringResource(Res.string.mp_collapse) else stringResource(Res.string.mp_expand),
                                            tint = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.width(12.dp))

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable(onClick = onNowPlaying)
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
                                text = if (isError) state.error
                                    ?: stringResource(Res.string.mp_error) else song.artists.joinToString(", ") { it.name },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                            IconButton(
                                onClick = { playerViewModel.toggleLike() },
                                modifier = Modifier.size(36.dp).pointerHoverIcon(PointerIcon.Hand)
                            ) {
                                Icon(
                                    imageVector = if (state.currentSong?.liked == true) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                    contentDescription = stringResource(Res.string.mp_like),
                                    tint = if (state.currentSong?.liked == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                    }
                }

                // —— CENTRO: Controles y Progreso ——
                Column(
                    modifier = Modifier.weight(1.6f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)
                ) {

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
                    ) {

                        IconButton(
                            onClick = { playerViewModel.toggleShuffle() },
                            modifier = Modifier.size(36.dp).pointerHoverIcon(PointerIcon.Hand),
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = if (state.isShuffled) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                else Color.Transparent,
                            )
                        ) {
                            Icon(
                                Icons.Rounded.Shuffle, stringResource(Res.string.mp_shuffle),
                                modifier = Modifier.size(20.dp),
                                tint = if (state.isShuffled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }



                        IconButton(
                            onClick = { playerViewModel.previous() },
                            modifier = Modifier.size(40.dp).pointerHoverIcon(PointerIcon.Hand)
                        ) {
                            Icon(
                                Icons.Rounded.SkipPrevious, stringResource(Res.string.mp_previous),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                        }




                        FilledIconButton(
                            onClick = { playerViewModel.togglePlayPause() },
                            modifier = Modifier.size(46.dp).pointerHoverIcon(PointerIcon.Hand),
                            shape = circleAwareShape(),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            if (isLoading) {
                                LoadingIndicator(
                                    modifier = Modifier.fillMaxSize(),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                            } else {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = if (isPlaying) stringResource(Res.string.tray_pause) else stringResource(
                                        Res.string.tray_play
                                    ),
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }



                        IconButton(
                            onClick = { playerViewModel.next() },
                            modifier = Modifier.size(40.dp).pointerHoverIcon(PointerIcon.Hand)
                        ) {
                            Icon(
                                Icons.Rounded.SkipNext, stringResource(Res.string.mp_next),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                        }

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
                                repeatIcon, stringResource(Res.string.mp_repeat),
                                modifier = Modifier.size(20.dp),
                                tint = if (isRepeatOff) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                            )
                        }

                    }
                    var localSliderValue by remember { mutableStateOf(0f) }
                    var isDragging by remember { mutableStateOf(false) }
                    val seekBarStyle by playerViewModel.seekBarStyle.collectAsState(SeekBarStyle.WAVY)

                    LaunchedEffect(progressState.positionMs, progressState.durationMs) {
                        if (!isDragging && progressState.durationMs > 0) {
                            localSliderValue = progressState.positionMs.toFloat() / progressState.durationMs
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        TimeText(
                            if (isDragging) (localSliderValue * progressState.durationMs).toLong() else progressState.positionMs,
                            null
                        )

                        PlayerSeekBar(
                            style = seekBarStyle,
                            value = localSliderValue,
                            onValueChange = {
                                isDragging = true
                                localSliderValue = it
                            },
                            onValueChangeFinished = {
                                val targetPosition = (localSliderValue * progressState.durationMs).toLong()
                                playerViewModel.seekTo(targetPosition)
                                isDragging = false
                            },
                            modifier = Modifier.weight(1f),
                            isPlaying = isPlaying || isDragging,
                        )

                        TimeText(progressState.durationMs)
                    }
                }

                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val volumeFloat = (volume.coerceIn(0, 100)) / 100f
                        val volumePercent = volume.coerceIn(0, 100)

                        Text(
                            text = "$volumePercent",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                fontFeatureSettings = "tnum"
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.width(24.dp),
                            textAlign = TextAlign.End
                        )
                        Spacer(Modifier.width(4.dp))

                        SlimSlider(
                            value = volumeFloat,
                            onValueChange = { playerViewModel.setVolume((it * 100).toInt()) },
                            modifier = Modifier.width(80.dp),
                            activeColor = MaterialTheme.colorScheme.onSurface,
                            inactiveColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f),
                        )


                        IconButton(
                            onClick = { playerViewModel.toggleMute() },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                when {
                                    volumeFloat == 0f -> Icons.AutoMirrored.Rounded.VolumeOff
                                    volumeFloat < 0.4f -> Icons.AutoMirrored.Rounded.VolumeDown
                                    else -> Icons.AutoMirrored.Rounded.VolumeUp
                                },
                                stringResource(Res.string.mp_volume),
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
                        )
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.QueueMusic,
                            stringResource(Res.string.mp_queue),
                            modifier = Modifier.size(24.dp)
                        )
                    }



                    IconButton(
                        onClick = onNowPlaying,
                        modifier = Modifier.size(40.dp).pointerHoverIcon(PointerIcon.Hand),
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = if (isOnNowPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    ) {
                        Icon(
                            if (isOnNowPlaying) Icons.Default.ArrowDropDown else Icons.Default.ArrowDropUp,
                            stringResource(Res.string.play_item),
                            modifier = Modifier.size(32.dp),
                            tint = if (isOnNowPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                }
            }
        }
    }

    if (floating) {
        // Tarjeta flotante: el fondo varía según MiniPlayerBackgroundStyle.
        //  - SOLID: surface opaco del tema.
        //  - COVER: carátula desenfocada + gradiente de sus colores.
        //  - TRANSLUCENT: velo REALMENTE translúcido — deja ver el contenido
        //    de la screen de la ruta que pasa por debajo de la tarjeta.
        val floatingShape = AppShapes.xLarge
        val artworkColors = LocalArtworkColors.current
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = dimens.miniPlayerFloatingMargin)
                .height(dimens.miniPlayerHeight)
                .clip(floatingShape)
                // Consume los taps sobre la tarjeta: el mini reproductor es independiente
                // y los clics no deben atravesarlo hacia la screen que está detrás.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
                .then(
                    when (backgroundStyle) {
                        // Vidrio esmerilado REAL: Haze desenfoca el contenido de la ruta
                        // que pasa por detrás de la tarjeta (hazeSource en Navigation).
                        // API explícita 2.x (igual que el sample oficial de Haze).
                        MiniPlayerBackgroundStyle.TRANSLUCENT -> if (hazeState != null) {
                            val style = HazeMaterials.ultraThin(
                                containerColor = MaterialTheme.colorScheme.surface,
                            )
                            Modifier.hazeEffect(state = hazeState) {
                                // En desktop la invalidación por pre-draw está desactivada por
                                // defecto; sin esto el efecto queda "stuck" (tarjeta transparente
                                // sin blur) al recrearse tras cambiar de estilo de fondo.
                                forceInvalidateOnPreDraw = true
                                blurEffect {
                                    blurEnabled = true
                                    this.style = style
                                }
                            }
                        } else {
                            Modifier.background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.80f),
                                    )
                                )
                            )
                        }
                        else -> Modifier.background(MaterialTheme.colorScheme.surface)
                    }
                )
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, floatingShape)
        ) {
            if (backgroundStyle == MiniPlayerBackgroundStyle.COVER) {
                MusicPlayerImage(
                    url = song.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .matchParentSize()
                        .blur(42.dp),
                    shape = RectangleShape,
                    contentScale = ContentScale.Crop,
                    placeholderType = PlaceholderType.SONG,
                    iconSize = 24.dp,
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    artworkColors.vibrant.copy(alpha = 0.72f),
                                    artworkColors.muted.copy(alpha = 0.58f),
                                )
                            )
                        )
                )
            }
            playerContent()
        }
    } else if (square) {
        Column(modifier = modifier.fillMaxWidth().height(dimens.miniPlayerHeight)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(dimens.chromeBorderWidth)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = chromeSurface,
                shape = RectangleShape,
                tonalElevation = 1.dp,
            ) {
                playerContent()
            }
        }
    } else {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .then(
                    if (islands) Modifier
                        .padding(horizontal = dimens.surfaceGap, vertical = dimens.surfaceGap)
                        .border(dimens.chromeBorderWidth, MaterialTheme.colorScheme.outlineVariant, surfaceShape)
                    else Modifier
                )
                .height(dimens.miniPlayerHeight),
            color = chromeSurface,
            shape = surfaceShape,
            tonalElevation = 1.dp,
        ) {
            playerContent()
        }
    }
}

@Composable
fun TimeText(millis: Long, seekValue: Float? = null) {
    Text(
        text = formatPlayerTimeValue(millis),
        style = MaterialTheme.typography.labelSmall.copy(
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            fontFeatureSettings = "tnum"
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.width(36.dp)
    )
}

