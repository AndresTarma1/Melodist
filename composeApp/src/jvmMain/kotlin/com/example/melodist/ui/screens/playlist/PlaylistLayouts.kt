package com.example.melodist.ui.screens.playlist

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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.melodist.ui.components.dialogs.DownloadConfirmationDialog
import com.example.melodist.ui.components.layout.AppVerticalScrollbar
import com.example.melodist.ui.components.LoadingMoreSongsItem
import com.example.melodist.ui.components.MelodistImage
import com.example.melodist.ui.components.PlaceholderType
import com.example.melodist.utils.LocalDownloadViewModel
import com.example.melodist.utils.LocalPlayerViewModel
import com.example.melodist.ui.screens.PlaylistActions
import com.example.melodist.ui.screens.PlaylistScreenState
import com.metrolist.innertube.pages.PlaylistPage

@Composable
internal fun PlaylistLayout(
    playlistPage: PlaylistPage,
    state: PlaylistScreenState,
    actions: PlaylistActions
) {
    val playerViewModel = LocalPlayerViewModel.current
    val downloadViewModel = LocalDownloadViewModel.current

    val songIds = remember(state.songs) { state.songs.map { it.id } }

    val isAnyDownloadingState = remember(songIds, downloadViewModel) {
        downloadViewModel.isAnyDownloadingFlow(songIds)
    }.collectAsState(initial = false)

    val isFullyDownloadedState = remember(songIds, downloadViewModel) {
        downloadViewModel.isFullyDownloadedFlow(songIds)
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
        val horizontalPadding = if (isCompact) 18.dp else 48.dp
        val sidePanelWidth = if (isCompact) 240.dp else 300.dp
        val coverSize = if (isCompact) 190.dp else 240.dp

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = if (isCompact) 20.dp else 32.dp, end = horizontalPadding, start = horizontalPadding, bottom = 16.dp)
        ) {
        Column(
            modifier = Modifier
                .width(sidePanelWidth)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(top = 8.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            PlaylistInfoPanel(
                playlistPage = playlistPage,
                coverSize = coverSize,
                controls = PlaylistInfoPanelControls(
                    isSaved = state.isSaved,
                    isSaving = state.isSaving,
                    isLoadingForPlay = state.isLoadingForPlay,
                ),
                actions = actions,
                isDownloadingAny = { isAnyDownloadingState.value },
                isFullyDownloaded = { isFullyDownloadedState.value },
                onDownloadClick = {
                    val isDownloadsPlaylist = playlistPage.playlist.id == "LOCAL_DOWNLOADS"
                    if (isFullyDownloadedState.value && !isDownloadsPlaylist) {
                        showDeleteDialog = true
                    } else if (!isAnyDownloadingState.value) {
                        actions.onDownloadPlaylist()
                    }
                }
            )
        }

        Spacer(Modifier.width(if (isCompact) 16.dp else 32.dp))

        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            val lazyListState = rememberLazyListState()

            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxSize().padding(end=16.dp)
            ) {
                itemsIndexed(state.songs, key = { _, song -> song.id }) { index, song ->
                    // El PlaylistSongItem ya estaba bien optimizado gracias a `rememberSongDownloadState`
                    SongListItem(
                        song = song,
                        onPlay = {
                            playerViewModel.playPlaylist(
                                state.songs, index,
                                playlistPage.playlist.id,
                                playlistPage.playlist.title
                            )
                        },
                        onLike = actions.onLike,
                        onDislike = actions.onDislike,
                        isLocalPlaylist = actions.isLocalPlaylist,
                        onRemoveFromPlaylist = actions.onRemoveSongFromPlaylist,
                        modifier = Modifier.animateItem(
                            placementSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        )
                    )
                    if (index < state.songs.lastIndex) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f),
                            modifier = Modifier.padding(start = 48.dp)
                        )
                    }
                }
                if (state.hasMore) item { LoadingMoreSongsItem(onLoadMore = actions.onLoadMore) }
            }

            AppVerticalScrollbar(
                state = lazyListState,
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(12.dp)
            )
        }
        }
    }
}

internal data class PlaylistInfoPanelControls(
    val isSaved: Boolean,
    val isSaving: Boolean,
    val isLoadingForPlay: Boolean,
)

@Composable
internal fun PlaylistInfoPanel(
    playlistPage: PlaylistPage,
    coverSize: Dp,
    controls: PlaylistInfoPanelControls,
    actions: PlaylistActions,
    isDownloadingAny: () -> Boolean,
    isFullyDownloaded: () -> Boolean,
    onDownloadClick: () -> Unit
) {
    playlistPage.playlist.author?.let { author ->
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .clickable { }
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
                        contentScale = ContentScale.FillBounds,
                        placeholderType = PlaceholderType.ARTIST,
                        iconSize = 14.dp
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    author.name,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        Spacer(Modifier.height(20.dp))
    }

    Card(
        modifier = Modifier
            .size(coverSize)
            .shadow(24.dp, RoundedCornerShape(8.dp)),
        shape = RoundedCornerShape(8.dp)
    ) {
        MelodistImage(
            url = playlistPage.playlist.thumbnail,
            contentDescription = playlistPage.playlist.title,
            modifier = Modifier.fillMaxSize(),
            placeholderType = PlaceholderType.PLAYLIST,
            contentScale = ContentScale.Fit,
            iconSize = coverSize * 0.33f
        )
    }

    Spacer(Modifier.height(24.dp))

    Text(
        text = playlistPage.playlist.title,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        color = MaterialTheme.colorScheme.onSurface
    )

    Spacer(Modifier.height(6.dp))

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            "Playlist",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        val songCountText = playlistPage.playlist.songCountText
        if (!songCountText.isNullOrBlank()) {
            Text(
                " • $songCountText",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

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
                    tint = if (controls.isSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        FloatingActionButton(
            onClick = { if (!controls.isLoadingForPlay) actions.onPlayAll() },
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .size(56.dp)
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
            Icon(
                Icons.Default.Shuffle,
                null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp)
            )
        }

        // OPTIMIZACIÓN 3: Extraemos el botón de descarga a su propio Composable.
        // Ahora, SOLO este botón se redibuja cuando el estado de descarga cambia.
        DownloadAllButton(
            isDownloadingAny = isDownloadingAny,
            isFullyDownloaded = isFullyDownloaded,
            onClick = onDownloadClick
        )
    }
}

// Nuevo Composable aislado para evitar redibujar el panel completo
@Composable
internal fun DownloadAllButton(
    isDownloadingAny: () -> Boolean,
    isFullyDownloaded: () -> Boolean,
    onClick: () -> Unit
) {
    val downloading = isDownloadingAny()
    val fullyDownloaded = isFullyDownloaded()

    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .pointerHoverIcon(PointerIcon.Hand)
    ) {
        if (downloading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
        } else {
            Icon(
                if (fullyDownloaded) Icons.Default.DownloadDone else Icons.Default.Download,
                null,
                tint = if (fullyDownloaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
