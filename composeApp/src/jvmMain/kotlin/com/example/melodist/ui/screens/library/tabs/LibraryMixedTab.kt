package com.example.melodist.ui.screens.library.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.example.melodist.navigation.Route
import com.example.melodist.ui.components.ItemContentSource
import com.example.melodist.ui.components.MediaGridItem
import com.example.melodist.ui.components.PlaceholderType
import com.example.melodist.ui.components.layout.AppVerticalScrollbar
import com.example.melodist.ui.screens.library.LibraryScreenState
import com.example.melodist.utils.LocalDownloadViewModel
import com.example.melodist.viewmodels.YtmLibraryState

@Composable
fun LibraryMixedTab(
    state: LibraryScreenState,
    onNavigate: (Route) -> Unit,
) {
    val ytm = state.ytmState as? YtmLibraryState.Success
    val downloadViewModel = LocalDownloadViewModel.current
    val downloadedSongs by downloadViewModel.downloadedSongs.collectAsState()
    val downloadedCount by downloadViewModel.downloadedCount.collectAsState()
    val fullyDownloadedAlbums by downloadViewModel.fullyDownloadedAlbums.collectAsState()
    val fullyDownloadedPlaylists by downloadViewModel.fullyDownloadedPlaylists.collectAsState()

    data class MixedGridEntry(
        val key: String,
        val title: String,
        val subtitle: String,
        val thumbnailUrl: String?,
        val placeholderType: PlaceholderType,
        val shape: Shape,
        val source: ItemContentSource,
        val onClick: () -> Unit,
    )

    val items = remember(
        state.albums,
        state.artists,
        state.playlists,
        ytm,
        downloadedSongs,
        downloadedCount,
        fullyDownloadedAlbums,
        fullyDownloadedPlaylists,
    ) {
        buildList {
            val mergedAlbums = (ytm?.albums.orEmpty() + state.albums).distinctBy { it.id }
            val mergedArtists = (ytm?.artists.orEmpty() + state.artists).distinctBy { it.id }
            val mergedPlaylists = (ytm?.playlists.orEmpty() + state.playlists).distinctBy { it.id }

            mergedAlbums.forEach { album ->
                add(
                    MixedGridEntry(
                        key = "alb_${album.id}",
                        title = album.title,
                        subtitle = album.artists?.firstOrNull()?.name ?: "Album",
                        thumbnailUrl = album.thumbnail,
                        placeholderType = PlaceholderType.ALBUM,
                        shape = RoundedCornerShape(12.dp),
                        source = if (ytm?.albums?.any { it.id == album.id } == true) ItemContentSource.YOUTUBE else ItemContentSource.LOCAL,
                        onClick = { onNavigate(Route.Album(album.browseId)) },
                    )
                )
            }

            mergedArtists.forEach { artist ->
                add(
                    MixedGridEntry(
                        key = "art_${artist.id}",
                        title = artist.title,
                        subtitle = "Artista",
                        thumbnailUrl = artist.thumbnail,
                        placeholderType = PlaceholderType.ARTIST,
                        shape = CircleShape,
                        source = if (ytm?.artists?.any { it.id == artist.id } == true) ItemContentSource.YOUTUBE else ItemContentSource.LOCAL,
                        onClick = { onNavigate(Route.Artist(artist.id)) },
                    )
                )
            }

            mergedPlaylists.forEach { playlist ->
                add(
                    MixedGridEntry(
                        key = "pl_${playlist.id}",
                        title = playlist.title,
                        subtitle = playlist.author?.name ?: playlist.songCountText ?: "Playlist",
                        thumbnailUrl = playlist.thumbnail,
                        placeholderType = PlaceholderType.PLAYLIST,
                        shape = RoundedCornerShape(12.dp),
                        source = if (ytm?.playlists?.any { it.id == playlist.id } == true) ItemContentSource.YOUTUBE else ItemContentSource.LOCAL,
                        onClick = { onNavigate(Route.Playlist(playlist.id)) },
                    )
                )
            }

            if (downloadedCount > 0) {
                add(
                    MixedGridEntry(
                        key = "downloads_all",
                        title = "Descargas",
                        subtitle = "$downloadedCount canciones",
                        thumbnailUrl = downloadedSongs.firstOrNull()?.thumbnail,
                        placeholderType = PlaceholderType.PLAYLIST,
                        shape = RoundedCornerShape(12.dp),
                        source = ItemContentSource.LOCAL,
                        onClick = { onNavigate(Route.Playlist("LOCAL_DOWNLOADS")) },
                    )
                )
            }

            fullyDownloadedPlaylists.forEach { playlistInfo ->
                add(
                    MixedGridEntry(
                        key = "dlpl_${playlistInfo.playlistId}",
                        title = playlistInfo.playlistName,
                        subtitle = "${playlistInfo.downloadedSongCount} canciones",
                        thumbnailUrl = playlistInfo.thumbnail,
                        placeholderType = PlaceholderType.PLAYLIST,
                        shape = RoundedCornerShape(12.dp),
                        source = ItemContentSource.LOCAL,
                        onClick = { onNavigate(Route.Playlist(playlistInfo.playlistId)) },
                    )
                )
            }

            fullyDownloadedAlbums.forEach { albumInfo ->
                add(
                    MixedGridEntry(
                        key = "dlal_${albumInfo.albumId}",
                        title = albumInfo.albumName,
                        subtitle = "${albumInfo.songs.size} canciones",
                        thumbnailUrl = albumInfo.thumbnail,
                        placeholderType = PlaceholderType.ALBUM,
                        shape = RoundedCornerShape(12.dp),
                        source = ItemContentSource.LOCAL,
                        onClick = { onNavigate(Route.Album(albumInfo.albumId)) },
                    )
                )
            }
        }
    }

    if (items.isEmpty()) {
        LibraryEmptyState(Icons.Default.LibraryMusic, "Biblioteca vacia", "Guarda albumes, artistas o playlists")
        return
    }

    val gridState = rememberLazyGridState()
    Box(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(minSize = 150.dp),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 80.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(items = items, key = { it.key }) { entry ->
                MediaGridItem(
                    title = entry.title,
                    subtitle = entry.subtitle,
                    thumbnailUrl = entry.thumbnailUrl,
                    placeholderType = entry.placeholderType,
                    shape = entry.shape,
                    onClick = entry.onClick,
                    isRemovable = false,
                    source = entry.source,
                )
            }
        }

        AppVerticalScrollbar(
            state = gridState,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(12.dp)
                .padding(vertical = 4.dp, horizontal = 2.dp)
        )
    }
}

