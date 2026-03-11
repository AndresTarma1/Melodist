package com.example.melodist.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.melodist.data.AppPreferences
import com.example.melodist.navigation.Route
import com.example.melodist.player.DownloadState
import com.example.melodist.player.PlaybackState
import com.example.melodist.viewmodels.DownloadViewModel
import com.example.melodist.viewmodels.PlayerProgressState
import com.example.melodist.viewmodels.PlayerUiState
import com.example.melodist.viewmodels.QueueSource
import com.example.melodist.viewmodels.RepeatMode
import com.metrolist.innertube.models.SongItem
import org.koin.compose.koinInject

@Composable
fun WideLayout(
    state: PlayerUiState,
    progressState: PlayerProgressState,
    song: SongItem,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onVolumeChange: (Int) -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onCollapse: () -> Unit,
    onQueueItemClick: (Int) -> Unit,
    artworkColors: ArtworkColors = ArtworkColors.Default,
    onNavigate: ((Route) -> Unit)? = null,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val h = maxHeight
        val isShort = h < 600.dp
        val coverSize = when {
            h < 500.dp -> 260; h < 650.dp -> 340; else -> 420
        }
        val spacerLg = if (isShort) 12.dp else 24.dp
        val spacerMd = if (isShort) 8.dp else 16.dp
        val vertPad = if (isShort) 12.dp else 24.dp

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = vertPad),
            horizontalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Row(modifier = Modifier.fillMaxWidth()) { CollapseButton(onCollapse) }
                Spacer(Modifier.height(spacerLg))
                CoverArt(
                    url = song.thumbnail,
                    title = song.title,
                    size = coverSize,
                    glowColor = artworkColors.vibrant
                )
                Spacer(Modifier.height(spacerLg))
                SongHeader(state, song, TextAlign.Center, onNavigate, onCollapse)
                Spacer(Modifier.height(spacerLg))
                ProgressBar(progressState, onSeek)
                Spacer(Modifier.height(spacerMd))
                TransportControls(
                    state,
                    onTogglePlayPause,
                    onNext,
                    onPrevious,
                    onToggleShuffle,
                    onToggleRepeat
                )
                Spacer(Modifier.height(spacerMd))
                VolumeRow(state, onVolumeChange)
                Spacer(Modifier.height(spacerMd))
            }

            VerticalDivider(
                modifier = Modifier.fillMaxHeight(0.85f).align(Alignment.CenterVertically),
                color = Color.White.copy(alpha = 0.10f),
                thickness = 1.dp
            )

            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                Spacer(Modifier.height(56.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Cola de reproducción",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                    Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.14f)) {
                        Text(
                            "${state.queue.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.10f))
                Spacer(Modifier.height(8.dp))

                val scrollState = rememberScrollState()
                val downloadViewModel: DownloadViewModel = koinInject()
                val downloadStates by downloadViewModel.downloadStates.collectAsState()

                Box(modifier = Modifier.weight(1f)) {
                    Column(
                        modifier = Modifier.fillMaxSize().verticalScroll(scrollState),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        state.queue.forEachIndexed { index, queueSong ->
                            QueueItem(
                                song = queueSong,
                                index = index,
                                isCurrent = index == state.currentIndex,
                                onClick = { onQueueItemClick(index) },
                                downloadState = downloadStates[queueSong.id]
                            )
                        }
                        Spacer(Modifier.height(32.dp))
                    }
                    VerticalScrollbar(
                        adapter = rememberScrollbarAdapter(scrollState),
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                        style = LocalScrollbarStyle.current.copy(
                            thickness = 4.dp,
                            unhoverColor = Color.White.copy(alpha = 0.10f),
                            hoverColor = Color.White.copy(alpha = 0.30f),
                            shape = CircleShape
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun CompactLayout(
    state: PlayerUiState,
    progressState: PlayerProgressState,
    song: SongItem,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onVolumeChange: (Int) -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onCollapse: () -> Unit,
    artworkColors: ArtworkColors = ArtworkColors.Default,
    onNavigate: ((Route) -> Unit)? = null,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val w = maxWidth
        val h = maxHeight
        val isVeryNarrow = w < 280.dp
        val isNarrow = w < 360.dp
        val isVeryShort = h < 420.dp
        val isShort = h < 560.dp
        val hPad = when {
            isVeryNarrow -> 12.dp; isNarrow -> 16.dp; else -> 24.dp
        }
        val maxCoverW = when {
            isVeryNarrow -> 180; isNarrow -> 240; else -> 340
        }
        val maxCoverH = when {
            isVeryShort -> 140; isShort -> 200; h < 700.dp -> 280; else -> 380
        }
        val coverSize = minOf(maxCoverW, maxCoverH)
        val spacerLg = when {
            isVeryShort -> 6.dp; isShort -> 10.dp; else -> 10.dp
        }
        val spacerMd = when {
            isVeryShort -> 4.dp; isShort -> 8.dp; else -> 14.dp
        }
        val spacerSm = when {
            isVeryShort -> 2.dp; isShort -> 4.dp; else -> 8.dp
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = hPad, vertical = if (isVeryShort) 8.dp else 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(modifier = Modifier.fillMaxWidth()) { CollapseButton(onCollapse) }
            Spacer(Modifier.height(spacerMd))
            CoverArt(
                url = song.thumbnail,
                title = song.title,
                size = coverSize,
                glowColor = artworkColors.vibrant
            )
            Spacer(Modifier.height(spacerLg))
            SongHeader(state, song, TextAlign.Center, onNavigate, onCollapse)
            Spacer(Modifier.height(spacerLg))
            ProgressBar(progressState, onSeek)
            Spacer(Modifier.height(spacerSm))
            TransportControls(
                state,
                onTogglePlayPause,
                onNext,
                onPrevious,
                onToggleShuffle,
                onToggleRepeat
            )
            Spacer(Modifier.height(spacerMd))
            VolumeRow(state, onVolumeChange)
            Spacer(Modifier.height(spacerLg))
        }
    }
}

@Composable
fun CollapseButton(onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)) {
        Icon(
            Icons.Rounded.KeyboardArrowDown,
            "Minimizar",
            tint = Color.White.copy(alpha = 0.75f),
            modifier = Modifier.size(28.dp)
        )
    }
}

@Composable
fun CoverArt(url: String?, title: String, size: Int, glowColor: Color = Color.Black) {
    val highRes by AppPreferences.highResCoverArt.collectAsState()
    val highResUrl = if (highRes) upscaleThumbnailUrl(url, 1080) else url

    val animGlow by animateColorAsState(
        glowColor.copy(alpha = 0.70f),
        tween(600),
        label = "coverGlow"
    )

    val width = size.dp
    val height = (size * 9f / 16f).dp
    val corner = when {
        size >= 400 -> 20.dp; size >= 280 -> 18.dp; else -> 14.dp
    }

    Card(
        modifier = Modifier
            .width(width)
            .height(height)
            .shadow(
                elevation = 48.dp,
                shape = RoundedCornerShape(corner),
                ambientColor = animGlow,
                spotColor = animGlow
            ),
        shape = RoundedCornerShape(corner),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        MelodistImage(
            url = highResUrl,
            contentDescription = title,
            modifier = Modifier.fillMaxSize(),
            placeholderType = PlaceholderType.SONG,
            iconSize = (size * 0.12f).dp
        )
    }
}

@Composable
fun SongHeader(
    state: PlayerUiState,
    song: SongItem,
    textAlign: TextAlign,
    onNavigate: ((Route) -> Unit)? = null,
    onCollapse: (() -> Unit)? = null
) {
    val centerH = if (textAlign == TextAlign.Center) Alignment.CenterHorizontally else Alignment.Start

    Column(horizontalAlignment = centerH, modifier = Modifier.fillMaxWidth()) {
        state.queueSource?.let { source ->
            val label = when (source) {
                is QueueSource.Album -> "De: ${source.title}"
                is QueueSource.Playlist -> "De: ${source.title}"
                is QueueSource.Single -> "Radio de la canción"
                QueueSource.Custom -> "Cola personalizada"
            }
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = Color.White.copy(alpha = 0.12f),
                modifier = Modifier.padding(bottom = 10.dp)
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.70f),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }

        Text(
            song.title,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = textAlign,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (textAlign == TextAlign.Center) Arrangement.Center else Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            song.artists.forEachIndexed { i, artist ->
                val hasId = artist.id != null
                Text(
                    artist.name,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = if (hasId) FontWeight.Medium else FontWeight.Normal),
                    color = if (hasId) Color.White else Color.White.copy(alpha = 0.60f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (hasId) Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable {
                            onCollapse?.invoke()
                            onNavigate?.invoke(Route.Artist(artist.id!!))
                        }
                        .pointerHoverIcon(PointerIcon.Hand)
                        .padding(horizontal = 2.dp)
                    else Modifier.padding(horizontal = 2.dp)
                )
                if (i < song.artists.size - 1) {
                    Text(
                        ", ",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.45f)
                    )
                }
            }
        }

        song.album?.let { album ->
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (textAlign == TextAlign.Center) Arrangement.Center else Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.Album,
                    null,
                    tint = Color.White.copy(alpha = 0.45f),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    album.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.70f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable {
                            onCollapse?.invoke()
                            onNavigate?.invoke(Route.Album(album.id))
                        }
                        .pointerHoverIcon(PointerIcon.Hand)
                        .padding(horizontal = 2.dp)
                )
            }
        }
    }
}

@Composable
fun ProgressBar(progressState: PlayerProgressState, onSeek: (Long) -> Unit) {
    var seekPos by remember { mutableStateOf<Float?>(null) }
    val progress = seekPos
        ?: if (progressState.durationMs > 0) progressState.positionMs.toFloat() / progressState.durationMs.toFloat() else 0f

    Column(modifier = Modifier.fillMaxWidth()) {
        Slider(
            value = progress,
            onValueChange = { seekPos = it },
            onValueChangeFinished = {
                seekPos?.let { onSeek((it * progressState.durationMs).toLong()) }
                seekPos = null
            },
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color.White.copy(alpha = 0.22f),
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent
            )
        )
        Row(
            modifier = Modifier.fillMaxWidth().offset(y = (-6).dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                formatPlayerTimeValue(seekPos?.let { (it * progressState.durationMs).toLong() } ?: progressState.positionMs),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.55f)
            )
            Text(
                formatPlayerTimeValue(progressState.durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.55f)
            )
        }
    }
}

@Composable
fun TransportControls(
    state: PlayerUiState,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val isVN = maxWidth < 280.dp
        val isN = maxWidth < 360.dp
        val playSize = when {
            isVN -> 48.dp; isN -> 56.dp; else -> 64.dp
        }
        val playIcon = when {
            isVN -> 24.dp; isN -> 28.dp; else -> 34.dp
        }
        val skipSize = when {
            isVN -> 36.dp; isN -> 40.dp; else -> 48.dp
        }
        val skipIcon = when {
            isVN -> 22.dp; isN -> 26.dp; else -> 32.dp
        }
        val toggleSize = when {
            isVN -> 32.dp; isN -> 36.dp; else -> 40.dp
        }
        val toggleIcon = when {
            isVN -> 16.dp; isN -> 18.dp; else -> 20.dp
        }
        val spacing = when {
            isVN -> 4.dp; isN -> 6.dp; else -> 8.dp
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing, Alignment.CenterHorizontally),
            modifier = Modifier.fillMaxWidth()
        ) {
            ToggleIconButton(
                state.isShuffled,
                onToggleShuffle,
                toggleSize,
                activeIcon = { Icon(Icons.Rounded.Shuffle, null, modifier = Modifier.size(toggleIcon)) },
                inactiveIcon = { Icon(Icons.Rounded.Shuffle, null, modifier = Modifier.size(toggleIcon)) }
            )

            IconButton(
                onClick = onPrevious,
                modifier = Modifier.size(skipSize).pointerHoverIcon(PointerIcon.Hand)
            ) {
                Icon(
                    Icons.Rounded.SkipPrevious,
                    "Anterior",
                    tint = Color.White,
                    modifier = Modifier.size(skipIcon)
                )
            }

            Surface(
                onClick = onTogglePlayPause,
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.18f),
                border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.40f)),
                modifier = Modifier.size(playSize).pointerHoverIcon(PointerIcon.Hand)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    when (state.playbackState) {
                        PlaybackState.LOADING, PlaybackState.BUFFERING -> CircularProgressIndicator(
                            modifier = Modifier.size(playIcon * 0.82f),
                            color = Color.White,
                            strokeWidth = 2.5.dp,
                            strokeCap = StrokeCap.Round
                        )

                        else -> AnimatedContent(
                            targetState = state.playbackState == PlaybackState.PLAYING,
                            transitionSpec = {
                                (scaleIn(tween(200)) + fadeIn(tween(180))) togetherWith
                                    (scaleOut(tween(150)) + fadeOut(tween(130)))
                            },
                            label = "playPause"
                        ) { playing ->
                            Icon(
                                if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                null,
                                tint = Color.White,
                                modifier = Modifier.size(playIcon)
                            )
                        }
                    }
                }
            }

            IconButton(
                onClick = onNext,
                modifier = Modifier.size(skipSize).pointerHoverIcon(PointerIcon.Hand)
            ) {
                Icon(
                    Icons.Rounded.SkipNext,
                    "Siguiente",
                    tint = Color.White,
                    modifier = Modifier.size(skipIcon)
                )
            }

            ToggleIconButton(
                state.repeatMode != RepeatMode.OFF,
                onToggleRepeat,
                toggleSize,
                activeIcon = {
                    Icon(
                        if (state.repeatMode == RepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                        null,
                        modifier = Modifier.size(toggleIcon)
                    )
                },
                inactiveIcon = { Icon(Icons.Rounded.Repeat, null, modifier = Modifier.size(toggleIcon)) }
            )
        }
    }
}

@Composable
fun ToggleIconButton(
    active: Boolean,
    onClick: () -> Unit,
    size: Dp = 40.dp,
    activeIcon: @Composable () -> Unit,
    inactiveIcon: @Composable () -> Unit
) {
    val bg by animateColorAsState(
        if (active) Color.White.copy(alpha = 0.20f) else Color.Transparent,
        tween(200),
        label = "tBg"
    )
    val fg by animateColorAsState(
        if (active) Color.White else Color.White.copy(alpha = 0.45f),
        tween(200),
        label = "tFg"
    )

    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = bg,
        modifier = Modifier.size(size).pointerHoverIcon(PointerIcon.Hand)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            CompositionLocalProvider(LocalContentColor provides fg) {
                if (active) activeIcon() else inactiveIcon()
            }
        }
    }
}

@Composable
fun VolumeRow(state: PlayerUiState, onVolumeChange: (Int) -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(if (maxWidth < 360.dp) 1f else 0.75f).align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                when {
                    state.volume == 0 -> Icons.AutoMirrored.Filled.VolumeOff
                    state.volume < 50 -> Icons.AutoMirrored.Filled.VolumeDown
                    else -> Icons.AutoMirrored.Filled.VolumeUp
                },
                "Volumen",
                tint = Color.White.copy(alpha = 0.55f),
                modifier = Modifier.size(20.dp)
            )
            Slider(
                value = state.volume / 100f,
                onValueChange = { onVolumeChange((it * 100).toInt()) },
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color.White.copy(alpha = 0.20f),
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent
                )
            )
        }
    }
}

@Composable
fun QueueItem(
    song: SongItem,
    index: Int,
    isCurrent: Boolean,
    onClick: () -> Unit,
    downloadState: DownloadState? = null
) {
    val bg by animateColorAsState(
        if (isCurrent) Color.White.copy(alpha = 0.16f) else Color.Transparent,
        tween(200),
        label = "qBg"
    )

    Surface(
        color = bg,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(modifier = Modifier.width(20.dp), contentAlignment = Alignment.Center) {
                if (isCurrent) {
                    Icon(Icons.Rounded.GraphicEq, null, tint = Color.White, modifier = Modifier.size(16.dp))
                } else {
                    Text(
                        "${index + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.38f)
                    )
                }
            }

            MelodistImage(
                url = song.thumbnail,
                contentDescription = song.title,
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(8.dp),
                placeholderType = PlaceholderType.SONG,
                iconSize = 18.dp
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    song.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isCurrent) Color.White else Color.White.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    song.artists.joinToString(", ") { it.name },
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.48f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            DownloadIndicator(state = downloadState, modifier = Modifier.padding(end = 6.dp))

            song.duration?.let {
                Text(
                    formatPlayerTimeValue(it * 1000L),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.38f)
                )
            }
        }
    }
}
