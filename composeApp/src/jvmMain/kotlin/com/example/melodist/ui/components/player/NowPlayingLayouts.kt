package com.example.melodist.ui.components.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode as InfiniteRepeatMode
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.melodist.data.AppPreferences
import com.example.melodist.navigation.Route
import com.example.melodist.player.PlaybackState
import com.example.melodist.ui.components.BlurredImageBackground
import com.example.melodist.ui.components.DownloadIndicator
import com.example.melodist.ui.components.MelodistImage
import com.example.melodist.ui.components.PlaceholderType
import com.example.melodist.ui.components.formatPlayerTimeValue
import com.example.melodist.ui.components.images.upscaleThumbnailUrl
import com.example.melodist.ui.helpers.rememberSongDownloadState
import com.example.melodist.utils.LocalDownloadViewModel
import com.example.melodist.utils.LocalPlayerViewModel
import com.example.melodist.viewmodels.PlayerProgressState
import com.example.melodist.viewmodels.PlayerUiState
import com.example.melodist.viewmodels.QueueSource
import com.example.melodist.viewmodels.RepeatMode
import com.metrolist.innertube.models.SongItem

@Composable
fun NowPlayingLayout(
    state: PlayerUiState,
    song: SongItem,
    onCollapse: () -> Unit,
    onNavigate: ((Route) -> Unit)? = null,
) {
    BlurredImageBackground(
        imageUrl = song.thumbnail,
        modifier = Modifier.fillMaxSize(),
        darkOverlayAlpha = 0.62f,
        gradientFraction = 0.52f
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 48.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            if (state.queueSource is QueueSource.Album) {
                AlbumDiscLarge(url = song.thumbnail, title = song.title)
            } else {
                CoverArt(url = song.thumbnail, title = song.title, size = 440)
            }
            Spacer(Modifier.height(24.dp))
            SongHeader(state = state, song = song, textAlign = TextAlign.Center, onNavigate = onNavigate, onCollapse = onCollapse)
        }
    }
}

@Composable
private fun AlbumDiscLarge(url: String?, title: String) {
    val highRes by AppPreferences.highResCoverArt.collectAsState()
    val highResUrl = if (highRes) upscaleThumbnailUrl(url, 1080) else url
    val rotation by rememberInfiniteTransition(label = "nowPlayingAlbumDisc").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = InfiniteRepeatMode.Restart
        ),
        label = "nowPlayingAlbumDiscRotation"
    )

    Box(
        modifier = Modifier
            .size(460.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.82f)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(420.dp)
                .clip(CircleShape)
                .rotate(rotation)
        ) {
            MelodistImage(
                url = highResUrl,
                contentDescription = title,
                modifier = Modifier.fillMaxSize(),
                shape = CircleShape,
                placeholderType = PlaceholderType.ALBUM,
                iconSize = 64.dp,
                contentScale = ContentScale.Crop
            )
        }

        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
        )
    }
}

@Composable
fun PlaybackQueuePanel(
    state: PlayerUiState,
    isVisible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val playerViewModel = LocalPlayerViewModel.current
    val panelWidth = 420.dp
    val listState = rememberLazyListState()

    AnimatedVisibility(
        visible = isVisible,
        enter = expandHorizontally(tween(220), expandFrom = Alignment.End) + fadeIn(tween(180)),
        exit = shrinkHorizontally(tween(180), shrinkTowards = Alignment.End) + fadeOut(tween(160)),
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier
                .width(panelWidth)
                .fillMaxHeight(),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 6.dp,
            shadowElevation = 12.dp,
            shape = RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Fila de reproduccion",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar fila")
                    }
                }

                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(Modifier.height(8.dp))

                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(state.queue, key = { index, item -> "${item.id}-$index" }) { index, queueSong ->
                        QueueItem(
                            song = queueSong,
                            index = index,
                            isCurrent = index == state.currentIndex,
                            onClick = { playerViewModel.playAtIndex(index) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CoverArt(url: String?, title: String, size: Int) {
    val highRes by AppPreferences.highResCoverArt.collectAsState()
    val highResUrl = if (highRes) upscaleThumbnailUrl(url, 1080) else url

    val width = size.dp
    val height = (size * 9f / 16f).dp
    val corner = 20.dp

    Card(
        modifier = Modifier.width(width).height(height),
        shape = RoundedCornerShape(corner),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        MelodistImage(
            url = highResUrl,
            contentDescription = title,
            modifier = Modifier.fillMaxSize(),
            placeholderType = PlaceholderType.SONG,
            iconSize = (size * 0.12f).dp,
            contentScale = ContentScale.Crop
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
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth(0.72f)) {
        state.queueSource?.let { source ->
            val label = when (source) {
                is QueueSource.Album -> "De: ${source.title}"
                is QueueSource.Playlist -> "De: ${source.title}"
                is QueueSource.Single -> "Radio de la cancion"
                QueueSource.Custom -> "Cola personalizada"
            }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.62f),
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        Text(
            text = song.title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = textAlign,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            song.artists.forEachIndexed { i, artist ->
                val hasId = artist.id != null
                Text(
                    text = artist.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (hasId) Modifier
                        .clip(RoundedCornerShape(6.dp))
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
                        text = ", ",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        song.album?.let { album ->
            Spacer(Modifier.height(4.dp))
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.Album, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(4.dp))
                Text(
                    text = album.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
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
private fun PlayerModeButtons(state: PlayerUiState) {
    val playerViewModel = LocalPlayerViewModel.current
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { playerViewModel.toggleShuffle() }, modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)) {
            Icon(
                Icons.Rounded.Shuffle,
                contentDescription = "Shuffle",
                tint = if (state.isShuffled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = { playerViewModel.toggleRepeat() }, modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)) {
            Icon(
                imageVector = if (state.repeatMode == RepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                contentDescription = "Repeat",
                tint = if (state.repeatMode != RepeatMode.OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun VolumeRow(state: PlayerUiState, onVolumeChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(0.52f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = when {
                state.volume == 0 -> Icons.AutoMirrored.Filled.VolumeOff
                state.volume < 50 -> Icons.AutoMirrored.Filled.VolumeDown
                else -> Icons.AutoMirrored.Filled.VolumeUp
            },
            contentDescription = "Volumen",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Slider(
            value = state.volume / 100f,
            onValueChange = { onVolumeChange((it * 100).toInt()) },
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.20f)
            )
        )
    }
}

@Composable
fun QueueItem(
    song: SongItem,
    index: Int,
    isCurrent: Boolean,
    onClick: () -> Unit
) {
    val downloadViewModel = LocalDownloadViewModel.current
    val downloadState by rememberSongDownloadState(song.id, downloadViewModel)

    val emphasis by animateFloatAsState(targetValue = if (isCurrent) 1f else 0f, animationSpec = tween(180), label = "queueCurrent")

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.45f + (0.2f * emphasis)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
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
                    Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                } else {
                    Text(
                        text = "${index + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            MelodistImage(
                url = song.thumbnail,
                contentDescription = song.title,
                modifier = Modifier.size(42.dp),
                shape = RoundedCornerShape(8.dp),
                placeholderType = PlaceholderType.SONG,
                iconSize = 18.dp
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song.artists.joinToString(", ") { it.name },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            DownloadIndicator(state = downloadState, modifier = Modifier.padding(end = 4.dp))

            song.duration?.let {
                Text(
                    text = formatPlayerTimeValue(it * 1000L),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
