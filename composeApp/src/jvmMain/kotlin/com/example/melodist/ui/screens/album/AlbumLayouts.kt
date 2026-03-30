package com.example.melodist.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Explicit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.example.melodist.navigation.Route
import com.example.melodist.player.DownloadState
import com.example.melodist.ui.components.DownloadIndicator
import com.example.melodist.ui.components.LoadingMoreSongsItem
import com.example.melodist.ui.components.MelodistImage
import com.example.melodist.ui.components.PlaceholderType
import com.example.melodist.ui.components.SongContextMenu
import com.example.melodist.utils.LocalDownloadViewModel
import com.example.melodist.utils.LocalPlayerViewModel
import com.example.melodist.ui.helpers.rememberSongDownloadState
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.pages.AlbumPage

@Composable
internal fun AlbumScreenWide(
    albumPage: AlbumPage,
    songs: List<SongItem>,
    hasMore: Boolean,
    onLoadMore: () -> Unit,
    onSurfaceColor: Color,
    onSurfaceVariant: Color,
    onNavigate: (Route) -> Unit,
    isSaved: Boolean,
    isSaving: Boolean = false,
    isLoadingForPlay: Boolean = false,
    onToggleSave: () -> Unit,
    onPlayAll: () -> Unit = {},
    onShuffle: () -> Unit = {},
) {
    val playerViewModel = LocalPlayerViewModel.current

    Row(modifier = Modifier.fillMaxSize().padding(start = 48.dp, end = 24.dp, top = 48.dp)) {
        Column(
            modifier = Modifier
                .width(320.dp)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(top = 8.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AlbumInfoPanel(
                albumPage = albumPage,
                songs = songs,
                onSurfaceColor = onSurfaceColor,
                onSurfaceVariant = onSurfaceVariant,
                coverSize = 240.dp,
                onNavigate = onNavigate,
                isSaved = isSaved,
                isSaving = isSaving,
                isLoadingForPlay = isLoadingForPlay,
                onToggleSave = onToggleSave,
                onPlayAll = onPlayAll,
                onShuffle = onShuffle,
            )
        }

        Spacer(Modifier.width(32.dp))

        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            AlbumSongsList(
                songs = songs,
                hasMore = hasMore,
                onLoadMore = onLoadMore,
                onSurfaceVariant = onSurfaceVariant,
                onSongClick = { index ->
                    playerViewModel.playAlbum(songs, index, albumPage.album.browseId, albumPage.album.title)
                },
            )
        }
    }
}

@Composable
internal fun AlbumScreenCompact(
    albumPage: AlbumPage,
    songs: List<SongItem>,
    hasMore: Boolean,
    onLoadMore: () -> Unit,
    onSurfaceColor: Color,
    onSurfaceVariant: Color,
    onNavigate: (Route) -> Unit,
    isSaved: Boolean,
    isSaving: Boolean = false,
    isLoadingForPlay: Boolean = false,
    onToggleSave: () -> Unit,
    onPlayAll: () -> Unit = {},
    onShuffle: () -> Unit = {},
) {
    val playerViewModel = LocalPlayerViewModel.current
    val lazyListState = rememberLazyListState()

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 20.dp, end = 20.dp, top = 48.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                AlbumInfoPanel(
                    albumPage = albumPage,
                    songs = songs,
                    onSurfaceColor = onSurfaceColor,
                    onSurfaceVariant = onSurfaceVariant,
                    coverSize = 200.dp,
                    onNavigate = onNavigate,
                    isSaved = isSaved,
                    isSaving = isSaving,
                    isLoadingForPlay = isLoadingForPlay,
                    onToggleSave = onToggleSave,
                    onPlayAll = onPlayAll,
                    onShuffle = onShuffle,
                )
                Spacer(Modifier.height(24.dp))
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                Spacer(Modifier.height(8.dp))
            }

            itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                NewSongListItem(
                    index = index + 1,
                    song = song,
                    onPlay = {
                        playerViewModel.playAlbum(songs, index, albumPage.album.browseId, albumPage.album.title)
                    }
                )
                if (index < songs.lastIndex) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f),
                        modifier = Modifier.padding(start = 48.dp)
                    )
                }
            }

            if (hasMore) item { LoadingMoreSongsItem(onLoadMore = onLoadMore) }
            item { Spacer(modifier = Modifier.height(80.dp)) }
        }

        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(lazyListState),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 12.dp),
            style = LocalScrollbarStyle.current.copy(
                thickness = 4.dp,
                unhoverColor = onSurfaceVariant.copy(alpha = 0.08f),
                hoverColor = onSurfaceVariant.copy(alpha = 0.25f),
                shape = RoundedCornerShape(2.dp)
            )
        )
    }
}

@Composable
internal fun AlbumInfoPanel(
    albumPage: AlbumPage,
    songs: List<SongItem>,
    onSurfaceColor: Color,
    onSurfaceVariant: Color,
    coverSize: Dp,
    onNavigate: (Route) -> Unit,
    isSaved: Boolean,
    isSaving: Boolean = false,
    isLoadingForPlay: Boolean = false,
    onToggleSave: () -> Unit,
    onPlayAll: () -> Unit = {},
    onShuffle: () -> Unit = {},
) {
    val downloadViewModel = LocalDownloadViewModel.current

    val songIds = remember(songs) { songs.map { it.id } }
    val isDownloading by remember(songIds, downloadViewModel) {
        downloadViewModel.isAnyDownloadingFlow(songIds)
    }.collectAsState(initial = false)

    val isFullyDownloaded by remember(songIds, downloadViewModel) {
        downloadViewModel.isFullyDownloadedFlow(songIds)
    }.collectAsState(initial = false)

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable { albumPage.album.artists?.firstOrNull()?.id?.let { onNavigate(Route.Artist(it)) } }
            .pointerHoverIcon(PointerIcon.Hand)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            ) {
                MelodistImage(
                    url = null,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    shape = CircleShape,
                    placeholderType = PlaceholderType.ARTIST,
                    iconSize = 14.dp
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                albumPage.album.artists?.firstOrNull()?.name ?: "Artista",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = onSurfaceColor
            )
        }
    }

    Spacer(Modifier.height(20.dp))

    Card(
        modifier = Modifier.size(coverSize).shadow(24.dp, RoundedCornerShape(8.dp)),
        shape = RoundedCornerShape(8.dp)
    ) {
        MelodistImage(
            url = albumPage.album.thumbnail,
            contentDescription = albumPage.album.title,
            modifier = Modifier.fillMaxSize(),
            placeholderType = PlaceholderType.ALBUM,
            contentScale = ContentScale.Crop,
            iconSize = coverSize * 0.33f
        )
    }

    Spacer(Modifier.height(24.dp))

    Text(
        text = albumPage.album.title,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        color = onSurfaceColor
    )

    Spacer(Modifier.height(6.dp))

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (albumPage.album.explicit) {
            Icon(Icons.Default.Explicit, null, modifier = Modifier.size(16.dp), tint = onSurfaceVariant)
            Spacer(Modifier.width(4.dp))
        }
        Text(
            "Álbum • ${albumPage.album.year ?: ""}",
            style = MaterialTheme.typography.bodyMedium,
            color = onSurfaceVariant
        )
    }

    Text(
        "${songs.size} canciones • ${calculateTotalDuration(songs)}",
        style = MaterialTheme.typography.bodySmall,
        color = onSurfaceVariant.copy(alpha = 0.7f)
    )

    Spacer(Modifier.height(24.dp))

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        IconButton(
            onClick = { if (!isSaving) onToggleSave() },
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .pointerHoverIcon(if (isSaving) PointerIcon.Default else PointerIcon.Hand)
        ) {
            if (isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Icon(
                    if (isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                    null,
                    tint = if (isSaved) MaterialTheme.colorScheme.primary else onSurfaceColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        FloatingActionButton(
            onClick = { if (!isLoadingForPlay) onPlayAll() },
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(56.dp)
                .pointerHoverIcon(if (isLoadingForPlay) PointerIcon.Default else PointerIcon.Hand)
        ) {
            if (isLoadingForPlay) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.5.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(28.dp))
            }
        }

        IconButton(
            onClick = { if (!isLoadingForPlay) onShuffle() },
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .pointerHoverIcon(PointerIcon.Hand)
        ) {
            Icon(Icons.Default.Shuffle, null, tint = onSurfaceColor, modifier = Modifier.size(20.dp))
        }

        IconButton(
            onClick = { downloadViewModel.downloadAll(songs) },
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .pointerHoverIcon(PointerIcon.Hand)
        ) {
            if (isDownloading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Icon(
                    if (isFullyDownloaded) Icons.Default.DownloadDone else Icons.Default.Download,
                    null,
                    tint = if (isFullyDownloaded) MaterialTheme.colorScheme.primary else onSurfaceColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
internal fun AlbumSongsList(
    songs: List<SongItem>,
    hasMore: Boolean,
    onLoadMore: () -> Unit,
    onSurfaceVariant: Color,
    onSongClick: (index: Int) -> Unit,
) {
    val scrollState = rememberLazyListState()

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(top = 8.dp, end = 12.dp),
            state = scrollState,
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                NewSongListItem(
                    index = index + 1,
                    song = song,
                    onPlay = { onSongClick(index) }
                )
                if (index < songs.lastIndex) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f),
                        modifier = Modifier.padding(start = 48.dp)
                    )
                }
            }

            if (hasMore) item { LoadingMoreSongsItem(onLoadMore = onLoadMore) }
            item { Spacer(modifier = Modifier.height(80.dp)) }
        }

        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(scrollState),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 12.dp),
            style = LocalScrollbarStyle.current.copy(
                thickness = 4.dp,
                unhoverColor = onSurfaceVariant.copy(alpha = 0.08f),
                hoverColor = onSurfaceVariant.copy(alpha = 0.25f),
                shape = RoundedCornerShape(2.dp)
            )
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun NewSongListItem(
    index: Int,
    song: SongItem,
    onPlay: () -> Unit
) {
    val playerViewModel = LocalPlayerViewModel.current
    val downloadViewModel = LocalDownloadViewModel.current
    val downloadState by rememberSongDownloadState(song.id, downloadViewModel)

    var isHovered by remember { mutableStateOf(false) }
    var showContextMenu by remember { mutableStateOf(false) }
    var menuOffset by remember { mutableStateOf(DpOffset.Zero) }
    var itemHeight by remember { mutableStateOf(0) }
    val density = LocalDensity.current

    val color = if (isHovered) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f) else Color.Transparent

    Box(modifier = Modifier.onGloballyPositioned { itemHeight = it.size.height }) {
        Surface(
            color = color,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { onPlay() }
                .pointerHoverIcon(PointerIcon.Hand)
                .onPointerEvent(PointerEventType.Enter) { isHovered = true }
                .onPointerEvent(PointerEventType.Exit) { isHovered = false }
                .onPointerEvent(PointerEventType.Press) {
                    if (it.button == PointerButton.Secondary) {
                        val position = it.changes.first().position
                        val xDp = with(density) { position.x.toDp() }
                        val yDp = with(density) { (position.y - itemHeight).toDp() }
                        menuOffset = DpOffset(xDp, yDp)
                        showContextMenu = true
                    }
                }
        ) {
            Row(
                modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.width(36.dp), contentAlignment = Alignment.Center) {
                    if (isHovered) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    } else {
                        Text(
                            text = index.toString(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }

                Spacer(Modifier.width(8.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (song.explicit) {
                            Icon(
                                Icons.Default.Explicit, null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(
                            text = song.artists.joinToString(", ") { it.name }.ifEmpty { "Artista desconocido" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                DownloadIndicator(state = downloadState, modifier = Modifier.padding(end = 8.dp))

                Text(
                    text = formatDuration(song.duration ?: 0),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Normal
                )
            }
        }

        SongContextMenu(
            expanded = showContextMenu,
            onDismiss = { showContextMenu = false },
            song = song,
            downloadState = downloadState,
            onDownload = { downloadViewModel.downloadSong(song) },
            onRemoveDownload = { downloadViewModel.removeDownload(song.id) },
            onCancelDownload = { downloadViewModel.cancelDownload(song.id) },
            onAddToQueue = { playerViewModel.addToQueue(song) },
            onPlayNext = { playerViewModel.playNext(song) }
        )
    }
}
