package com.example.melodist.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.example.melodist.ui.components.CornerQuickPlayConfig
import com.example.melodist.ui.components.PlaceholderType
import com.example.melodist.ui.components.YouTubeGridItem
import com.example.melodist.ui.components.context.SongContextMenu
import com.example.melodist.ui.components.song.DownloadIndicator
import com.example.melodist.ui.helpers.rememberSongDownloadState
import com.example.melodist.utils.LocalDownloadViewModel
import com.example.melodist.utils.LocalPlayerViewModel
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.YTItem
import kotlinx.coroutines.launch
import kotlin.collections.isNotEmpty
import kotlin.collections.orEmpty

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SongHomeItem(
    item: SongItem,
    onClick: (YTItem) -> Unit
) {
    val downloadViewModel = LocalDownloadViewModel.current
    val downloadState by rememberSongDownloadState(item.id, downloadViewModel)

    var showMenu by remember { mutableStateOf(false) }
    var menuOffset by remember { mutableStateOf(DpOffset.Zero) }

    YouTubeGridItem(
        item = item,
        onClick = onClick,
        imageShape = RoundedCornerShape(10.dp),
        alignment = Alignment.Start,
        titleAlign = TextAlign.Start,
        placeholderType = PlaceholderType.SONG,
        centerPlayVisible = true,
        contextMenuEnabled = true,
        onContextMenuAction = { offset -> menuOffset = offset; showMenu = true },
        subtitle = item.artists.firstOrNull()?.name.orEmpty(),
        topStartOverlay = {
            if (downloadState != null) {
                DownloadIndicator(
                    state = downloadState,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                        .padding(4.dp)
                )
            }
        },
        overlayContent = {
            SongContextMenu(
                expanded = showMenu,
                onDismiss = { showMenu = false },
                song = item,
                offset = menuOffset
            )
        }
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun AlbumHomeItem(item: AlbumItem, onClick: (YTItem) -> Unit) {
    val playerViewModel = LocalPlayerViewModel.current
    val scope = rememberCoroutineScope()

    YouTubeGridItem(
        item = item,
        onClick = onClick,
        imageShape = RoundedCornerShape(10.dp),
        alignment = Alignment.Start,
        titleAlign = TextAlign.Start,
        placeholderType = PlaceholderType.ALBUM,
        centerPlayVisible = false,
        contextMenuEnabled = false,
        onMoreClick = { onClick(item) },
        quickPlay = CornerQuickPlayConfig(
            size = 28.dp,
            iconSize = 16.dp,
            onClick = {
                scope.launch {
                    val page = YouTube.album(item.browseId).getOrNull()
                    val songs = page?.songs.orEmpty()
                    if (songs.isNotEmpty()) {
                        playerViewModel.playAlbum(
                            songs = songs,
                            startIndex = 0,
                            browseId = item.browseId,
                            title = item.title
                        )
                    } else {
                        onClick(item)
                    }
                }
            }
        ),
        subtitle = item.artists?.firstOrNull()?.name ?: "Álbum"
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PlaylistHomeItem(item: PlaylistItem, onClick: (YTItem) -> Unit) {
    val playerViewModel = LocalPlayerViewModel.current
    val scope = rememberCoroutineScope()

    YouTubeGridItem(
        item = item,
        onClick = onClick,
        imageShape = RoundedCornerShape(10.dp),
        alignment = Alignment.Start,
        titleAlign = TextAlign.Start,
        placeholderType = PlaceholderType.PLAYLIST,
        centerPlayVisible = false,
        contextMenuEnabled = false,
        onMoreClick = { onClick(item) },
        quickPlay = CornerQuickPlayConfig(
            size = 42.dp,
            iconSize = 20.dp,
            onClick = {
                scope.launch {
                    val page = YouTube.playlist(item.id).getOrNull()
                    val songs = page?.songs.orEmpty()
                    if (songs.isNotEmpty()) {
                        playerViewModel.playPlaylist(
                            songs = songs,
                            startIndex = 0,
                            playlistId = item.id,
                            title = item.title
                        )
                    } else {
                        onClick(item)
                    }
                }
            }
        ),
        subtitle = item.author?.name ?: "Lista"
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ArtistHomeItem(item: ArtistItem, onClick: (YTItem) -> Unit) {
    YouTubeGridItem(
        item = item,
        onClick = onClick,
        imageShape = CircleShape,
        alignment = Alignment.CenterHorizontally,
        titleAlign = TextAlign.Center,
        placeholderType = PlaceholderType.ARTIST,
        centerPlayVisible = false,
        contextMenuEnabled = false,
        onMoreClick = { onClick(item) },
        subtitle = "Artista"
    )
}

