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
import com.example.melodist.models.MediaMetadata
import com.example.melodist.ui.components.*
import com.example.melodist.ui.components.background.BlurredImageBackground
import com.example.melodist.ui.components.skeletons.AnimatedEqualizer
import com.example.melodist.utils.upscaleThumbnailUrl
import com.example.melodist.ui.components.song.DownloadIndicator
import com.example.melodist.ui.helpers.rememberSongDownloadState
import com.example.melodist.utils.LocalDownloadViewModel
import com.example.melodist.utils.LocalPlayerViewModel
import com.example.melodist.utils.LocalUserPreferences
import com.example.melodist.utils.isWideThumbnail
import com.example.melodist.viewmodels.PlayerUiState
import com.example.melodist.viewmodels.QueueSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.jewel.foundation.modifier.onHover
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlin.time.Duration.Companion.milliseconds
import androidx.compose.animation.core.RepeatMode as InfiniteRepeatMode

import androidx.compose.material.icons.filled.PlayArrow // ¡Asegúrate de importar esto!
@Composable
fun NowPlayingLayout(
    state: PlayerUiState,
    song: MediaMetadata,
    onCollapse: () -> Unit,
    onNavigate: ((Route) -> Unit)? = null,
) {
    val playerViewModel = LocalPlayerViewModel.current
    val highRes by playerViewModel.highResCoverArt.collectAsState(false)

    BlurredImageBackground(
        imageUrl = song.thumbnailUrl,
        modifier = Modifier.fillMaxSize(),
        darkOverlayAlpha = 0.62f,
        gradientFraction = 0.52f
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 48.dp, vertical = 28.dp),
            contentAlignment = Alignment.Center
        ) {
            val isCompact = maxHeight < 600.dp
            val imageSize = if (isCompact) 280.dp else 420.dp
            
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CoverArt(
                    url = song.thumbnailUrl,
                    title = song.title,
                    modifier = Modifier.widthIn(max = imageSize),
                    highRes = highRes
                )

                Spacer(Modifier.height(if (isCompact) 16.dp else 32.dp))
                
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
                        val dragModifier = if (!queueLocked) Modifier.longPressDraggableHandle() else Modifier
                        QueueItem(
                            song = queueSong,
                            isCurrent = index == state.currentIndex,
                            isDragging = isDragging,
                            dragModifier = dragModifier,
                            onClick = { playerViewModel.playAtIndex(index) },
                        )
                    }
                }
            }
        }
    }

}

@Composable
fun CoverArt(url: String?, title: String, modifier: Modifier = Modifier, highRes: Boolean) {
    val imageUrl = if (highRes) upscaleThumbnailUrl(url, 1080) else url
    val ratio = if (isWideThumbnail(url)) 16f/  9f  else 1f
    val corner = 20.dp

    Card(
        modifier = modifier.aspectRatio(ratio),
        shape = RoundedCornerShape(corner),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        MelodistImage(
            url = imageUrl,
            contentDescription = title,
            modifier = Modifier.fillMaxSize(),
            placeholderType = PlaceholderType.SONG,
            contentScale = ContentScale.Crop

        )
    }
}

@Composable
fun SongHeader(
    state: PlayerUiState,
    song: MediaMetadata,
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
                    text = album.title,
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
    song: MediaMetadata,
    isCurrent: Boolean,
    isDragging: Boolean = false,
    dragModifier: Modifier = Modifier,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val downloadViewModel = LocalDownloadViewModel.current
    val downloadState by rememberSongDownloadState(song.id, downloadViewModel)

    var isHovered by remember { mutableStateOf(false) }

    // Eliminamos el color de fondo para isHovered, ya que ahora queremos
    // el efecto sobre la imagen
    val bgColor = if (isDragging) MaterialTheme.colorScheme.surfaceContainerHighest
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
            .then(dragModifier) // 1. Aplicamos el drag modifier a TODO el ítem
            .clickable(onClick = onClick)
            // 2. Cursor de cruz si está arrastrando, mano si solo pasa por encima
            .pointerHoverIcon(if (isDragging) PointerIcon.Crosshair else PointerIcon.Hand)
            .onHover { isHovered = it }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ¡ELIMINADO! Ya no necesitamos el Icon(Icons.Rounded.DragHandle) aquí

            Box {
                // Thumbnail
                MelodistImage(
                    url = song.thumbnailUrl,
                    contentDescription = song.title,
                    modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    placeholderType = PlaceholderType.SONG,
                    iconSize = 22.dp,
                    contentScale = ContentScale.Crop
                )

                if (isCurrent) {
                    Box(
                        modifier = Modifier.matchParentSize().background(
                            Color.Black.copy(alpha = 0.3f), shape = RoundedCornerShape(8.dp)
                        )
                    ) {
                        AnimatedEqualizer(
                            modifier = Modifier.size(20.dp).align(Alignment.Center)
                        )
                    }
                } else if (isHovered && !isDragging) {
                    // 3. Overlay negro con icono de Play al hacer Hover
                    Box(
                        modifier = Modifier.matchParentSize().background(
                            Color.Black.copy(alpha = 0.3f), shape = RoundedCornerShape(8.dp)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "Reproducir",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp).align(Alignment.Center)
                        )
                    }
                }
            }

            // Título + artistas
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    song.title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = if (isCurrent) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    song.artists.joinToString(", ") { it.name },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            DownloadIndicator(state = downloadState)

            // Duración
            if (song.duration > 0) {
                Text(
                    formatPlayerTimeValue(song.duration * 1000L),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}