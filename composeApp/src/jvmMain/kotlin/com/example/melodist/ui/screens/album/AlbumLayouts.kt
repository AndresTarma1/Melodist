package com.example.melodist.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.melodist.ui.components.dialogs.DownloadConfirmationDialog
import com.example.melodist.navigation.Route
import com.example.melodist.ui.components.layout.AppVerticalScrollbar
import com.example.melodist.ui.components.LoadingMoreSongsItem
import com.example.melodist.ui.components.MelodistImage
import com.example.melodist.ui.components.PlaceholderType
import com.example.melodist.utils.LocalDownloadViewModel
import com.example.melodist.utils.LocalPlayerViewModel
import com.example.melodist.ui.screens.playlist.SongListItem
import com.example.melodist.ui.screens.shared.calculateTotalDuration
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.pages.AlbumPage

@Composable
internal fun AlbumScreenLayout(
    albumPage: AlbumPage,
    state: AlbumScreenState,
    actions: AlbumScreenActions,
) {
    val playerViewModel = LocalPlayerViewModel.current
    val downloadViewModel = LocalDownloadViewModel.current
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)

    val songIds = remember(state.songs) { state.songs.map { it.id } }
    val isFullyDownloadedState = remember(songIds, downloadViewModel) {
        downloadViewModel.isFullyDownloadedFlow(songIds)
    }.collectAsState(initial = false)
    val isAnyDownloadingState = remember(songIds, downloadViewModel) {
        downloadViewModel.isAnyDownloadingFlow(songIds)
    }.collectAsState(initial = false)

    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        DownloadConfirmationDialog(
            onConfirm = {
                downloadViewModel.removeDownloads(songIds)
            },
            onDismiss = { showDeleteDialog = false }
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isCompact = maxWidth < 980.dp
        val startPadding = if (isCompact) 18.dp else 48.dp
        val endPadding = if (isCompact) 18.dp else 24.dp
        val sidePanelWidth = if (isCompact) 250.dp else 320.dp
        val coverSize = if (isCompact) 190.dp else 240.dp

        Row(modifier = Modifier.fillMaxSize().padding(start = startPadding, end = endPadding, top = if (isCompact) 12.dp else 16.dp)) {
        Column(
            modifier = Modifier
                .width(sidePanelWidth)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(top = 8.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AlbumInfoPanel(
                albumPage = albumPage,
                songs = state.songs,
                onSurfaceColor = onSurfaceColor,
                onSurfaceVariant = onSurfaceVariant,
                coverSize = coverSize,
                actions = actions,
                controls = AlbumInfoPanelControls(
                    isSaved = state.isSaved,
                    isSaving = state.isSaving,
                    isLoadingForPlay = state.isLoadingForPlay,
                ),
            )
        }

        Spacer(Modifier.width(if (isCompact) 16.dp else 32.dp))

        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            AlbumSongsList(
                songs = state.songs,
                hasMore = state.hasMore,
                onLoadMore = actions.onLoadMore,
                onSongClick = { index ->
                    playerViewModel.playAlbum(state.songs, index, albumPage.album.browseId, albumPage.album.title)
                },
            )
        }
        }
    }
}

internal data class AlbumInfoPanelControls(
    val isSaved: Boolean,
    val isSaving: Boolean,
    val isLoadingForPlay: Boolean,
)

@Composable
internal fun AlbumInfoPanel(
    albumPage: AlbumPage,
    songs: List<SongItem>,
    onSurfaceColor: Color,
    onSurfaceVariant: Color,
    coverSize: Dp,
    actions: AlbumScreenActions,
    controls: AlbumInfoPanelControls,
) {
    val downloadViewModel = LocalDownloadViewModel.current

    val songIds = remember(songs) { songs.map { it.id } }
    val isDownloading by remember(songIds, downloadViewModel) {
        downloadViewModel.isAnyDownloadingFlow(songIds)
    }.collectAsState(initial = false)

    val isFullyDownloaded by remember(songIds, downloadViewModel) {
        downloadViewModel.isFullyDownloadedFlow(songIds)
    }.collectAsState(initial = false)

    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        DownloadConfirmationDialog(
            onConfirm = { downloadViewModel.removeDownloads(songIds) },
            onDismiss = { showDeleteDialog = false }
        )
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable { albumPage.album.artists?.firstOrNull()?.id?.let { actions.onNavigate(Route.Artist(it)) } }
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
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center

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
            onClick = { if (!controls.isSaving) actions.onToggleSave() },
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .pointerHoverIcon(if (controls.isSaving) PointerIcon.Default else PointerIcon.Hand)
        ) {
            if (controls.isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Icon(
                    if (controls.isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                    null,
                    tint = if (controls.isSaved) MaterialTheme.colorScheme.primary else onSurfaceColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        FloatingActionButton(
            onClick = { if (!controls.isLoadingForPlay) actions.onPlayAll() },
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(56.dp)
                .pointerHoverIcon(if (controls.isLoadingForPlay) PointerIcon.Default else PointerIcon.Hand)
        ) {
            if (controls.isLoadingForPlay) {
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
            onClick = { if (!controls.isLoadingForPlay) actions.onShuffle() },
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .pointerHoverIcon(PointerIcon.Hand)
        ) {
            Icon(Icons.Default.Shuffle, null, tint = onSurfaceColor, modifier = Modifier.size(20.dp))
        }

        IconButton(
            onClick = {
                if (isFullyDownloaded) {
                    showDeleteDialog = true
                } else if (!isDownloading) {
                    downloadViewModel.downloadAll(songs)
                }
            },
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
    onSongClick: (index: Int) -> Unit,
) {
    val scrollState = rememberLazyListState()

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(end = 16.dp),
            state = scrollState
        ) {
            itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                SongListItem(
                    albumIndex = index + 1,
                    song = song,
                    onPlay = { onSongClick(index)},
                    modifier = Modifier.animateItem(
                        placementSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    )
                )
                if (index < songs.lastIndex) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f),
                        modifier = Modifier.padding(start = 48.dp)
                    )
                }
            }

            if (hasMore) item { LoadingMoreSongsItem(onLoadMore = onLoadMore) }
        }

        AppVerticalScrollbar(
            state = scrollState,
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(12.dp)
        )
    }
}

