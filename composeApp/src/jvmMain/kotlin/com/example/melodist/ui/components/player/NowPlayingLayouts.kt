package com.example.melodist.ui.components.player

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.melodist.navigation.Route
import com.example.melodist.ui.components.*
import com.example.melodist.ui.components.images.upscaleThumbnailUrl
import com.example.melodist.ui.helpers.rememberSongDownloadState
import com.example.melodist.utils.LocalDownloadViewModel
import com.example.melodist.utils.LocalPlayerViewModel
import com.example.melodist.utils.LocalUserPreferences
import com.example.melodist.viewmodels.PlayerUiState
import com.example.melodist.viewmodels.QueueSource
import com.metrolist.innertube.models.SongItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.jewel.foundation.modifier.onHover
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlin.time.Duration.Companion.milliseconds
import androidx.compose.animation.core.RepeatMode as InfiniteRepeatMode

@Composable
fun NowPlayingLayout(
    state: PlayerUiState,
    song: SongItem,
    onCollapse: () -> Unit,
    onNavigate: ((Route) -> Unit)? = null,
) {
    val playerViewModel = LocalPlayerViewModel.current
    val highRes by playerViewModel.highResCoverArt.collectAsState(false)

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
                AlbumDiscLarge(url = song.thumbnail, title = song.title, highRes = highRes)
            } else {
                CoverArt(url = song.thumbnail, title = song.title, size = 440, highRes = highRes)
            }
            Spacer(Modifier.height(24.dp))
            SongHeader(
                state = state,
                song = song,
                textAlign = TextAlign.Center,
                onNavigate = onNavigate,
                onCollapse = onCollapse
            )
        }
    }
}

@Composable
private fun AlbumDiscLarge(url: String?, title: String, highRes: Boolean) {
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
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val playerViewModel = LocalPlayerViewModel.current
    val coroutineScope = rememberCoroutineScope()
    val preferencesRepo = LocalUserPreferences.current
    val listState = rememberLazyListState()

    val queueLocked by preferencesRepo.queueLocked.collectAsState(initial = false)

    val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
        playerViewModel.moveQueueItem(from.index, to.index)
    }

    LaunchedEffect(state.isShuffled) {
        if (state.queue.isNotEmpty() && state.currentIndex in state.queue.indices) {
            delay(100.milliseconds)
            listState.animateScrollToItem(state.currentIndex)
        }
    }

    Surface(
        modifier = Modifier.width(380.dp).fillMaxHeight(), // Más ancho para mejor visualización
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 2.dp,
        shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 20.dp), // Más espacio
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Cola de reproducción",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "${state.queue.size} canciones",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                preferencesRepo.setQueueLocked(!queueLocked)
                            }
                        },
                        modifier = Modifier.size(36.dp).pointerHoverIcon(PointerIcon.Hand)
                    ) {
                        Icon(
                            if (queueLocked) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                            "Bloquear/Desbloquear cola",
                            modifier = Modifier.size(20.dp),
                            tint = if (queueLocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp).pointerHoverIcon(PointerIcon.Hand) // Botón más grande
                    ) {
                        Icon(
                            Icons.Default.Close, "Cerrar",
                            modifier = Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            // ── Lista ─────────────────────────────────────────────────────
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(
                    items = state.queue,
                    key = { index, _ ->
                        state.queueSession.order.getOrNull(index) ?: index
                    } // Clave estable basada en el índice original
                ) { index, queueSong ->
                    val itemKey = state.queueSession.order.getOrNull(index) ?: index
                    ReorderableItem(reorderableState, key = itemKey) { isDragging ->
                        val dragModifier = if (!queueLocked) Modifier.draggableHandle() else Modifier
                        QueueItem(
                            song = queueSong,
                            index = index,
                            isCurrent = index == state.currentIndex,
                            isDragging = isDragging,
                            dragModifier = dragModifier,
                            onClick = { playerViewModel.playAtIndex(index) },
                            modifier = Modifier.animateItem(
                                placementSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            )
                        )
                    }
                }
            }
        }
    }

}

@Composable
fun CoverArt(url: String?, title: String, size: Int, highRes: Boolean) {
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
                Icon(
                    Icons.Rounded.Album,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
fun QueueItem(
    song: SongItem,
    index: Int,
    isCurrent: Boolean,
    isDragging: Boolean = false,
    dragModifier: Modifier = Modifier,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val downloadViewModel = LocalDownloadViewModel.current
    val downloadState by rememberSongDownloadState(song.id, downloadViewModel)

    var isHovered by remember { mutableStateOf(false) }
    val bgColor = if (isDragging) MaterialTheme.colorScheme.surfaceContainerHighest
    else if (isHovered) MaterialTheme.colorScheme.secondaryContainer
    else Color.Transparent
    Surface(
        color = if (isCurrent && !isDragging)
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
        else
            bgColor,
        shape = RoundedCornerShape(10.dp),
        shadowElevation = if (isDragging) 4.dp else 0.dp,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand)
            .onHover { isHovered = it }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp), // Mayor padding general
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp) // Mayor separación
        ) {
            // Drag Handle
            if (dragModifier != Modifier) {
                Icon(
                    imageVector = Icons.Rounded.DragHandle,
                    contentDescription = "Arrastrar",
                    modifier = dragModifier
                        .pointerHoverIcon(PointerIcon.Hand)
                        .size(24.dp), // Ícono más grande
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }

            // Número o ícono de reproducción
            Box(modifier = Modifier.width(22.dp), contentAlignment = Alignment.Center) { // Más ancho
                if (isCurrent) {
                    Icon(
                        Icons.Rounded.GraphicEq, null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp) // Ícono más grande
                    )
                } else {
                    Text(
                        "${index + 1}",
                        style = MaterialTheme.typography.labelMedium, // Textos más grandes
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }

            // Thumbnail
            MelodistImage(
                url = song.thumbnail,
                contentDescription = song.title,
                modifier = Modifier.size(48.dp), // Imagen de carátula más grande
                shape = RoundedCornerShape(8.dp),
                placeholderType = PlaceholderType.SONG,
                iconSize = 22.dp,
                contentScale = ContentScale.Crop
            )

            // Título + artistas
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    song.title,
                    style = MaterialTheme.typography.bodyMedium.copy( // Cambiado de bodySmall
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium // Tipografía más destacada
                    ),
                    color = if (isCurrent) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    song.artists.joinToString(", ") { it.name },
                    style = MaterialTheme.typography.bodySmall, // Cambiado de labelSmall
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            DownloadIndicator(state = downloadState)

            // Duración
            song.duration?.let {
                Text(
                    formatPlayerTimeValue(it * 1000L),
                    style = MaterialTheme.typography.labelMedium, // Texto un poco más grande
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}
