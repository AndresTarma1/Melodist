package com.example.melodist.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.melodist.navigation.Route
import com.example.melodist.ui.components.AlbumScreenSkeleton
import com.example.melodist.ui.components.BlurredImageBackground
import com.example.melodist.utils.LocalPlayerViewModel
import com.example.melodist.viewmodels.AlbumState
import com.example.melodist.viewmodels.AlbumViewModel
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.pages.AlbumPage

@Composable
fun AlbumScreenRoute(
    onNavigate: (Route) -> Unit,
    onBack: () -> Unit,
    viewModel: AlbumViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val songs by viewModel.songs.collectAsState()
    val hasMoreSongs by viewModel.hasMoreSongs.collectAsState()

    val playerViewModel = LocalPlayerViewModel.current
    val successState = uiState as? AlbumState.Success

    AlbumScreen(
        uiState = uiState,
        songs = songs,
        hasMore = hasMoreSongs,
        onLoadMore = { viewModel.loadMoreSongs() },
        onNavigate = onNavigate,
        onBack = onBack,
        isSaved = successState?.isSaved ?: false,
        isSaving = successState?.isSaving ?: false,
        isLoadingForPlay = successState?.isLoadingForPlay ?: false,
        onToggleSave = { viewModel.toggleSave() },
        onPlayAll = {
            val state = successState ?: return@AlbumScreen
            viewModel.playAllSongs(shuffle = false) { allSongs, startIndex ->
                playerViewModel.playAlbum(
                    allSongs,
                    startIndex,
                    state.albumPage.album.browseId,
                    state.albumPage.album.title
                )
            }
        },
        onShuffle = {
            val state = successState ?: return@AlbumScreen
            viewModel.playAllSongs(shuffle = true) { allSongs, startIndex ->
                playerViewModel.playAlbum(
                    allSongs,
                    startIndex,
                    state.albumPage.album.browseId,
                    state.albumPage.album.title
                )
            }
        },
    )
}

@Composable
fun AlbumScreen(
    uiState: AlbumState,
    songs: List<SongItem> = emptyList(),
    hasMore: Boolean = false,
    onLoadMore: () -> Unit = {},
    onNavigate: (Route) -> Unit,
    onBack: () -> Unit,
    isSaved: Boolean = false,
    isSaving: Boolean = false,
    isLoadingForPlay: Boolean = false,
    onToggleSave: () -> Unit = {},
    onPlayAll: () -> Unit = {},
    onShuffle: () -> Unit = {},
) {
    val thumbnailUrl = (uiState as? AlbumState.Success)?.albumPage?.album?.thumbnail

    BlurredImageBackground(
        imageUrl = thumbnailUrl,
        modifier = Modifier.fillMaxSize(),
        darkOverlayAlpha = 0.52f,
        gradientFraction = 0.45f
    ) {
        when (uiState) {
            is AlbumState.Loading -> AlbumScreenSkeleton()
            is AlbumState.Success -> AlbumScreenContent(
                albumPage = uiState.albumPage,
                songs = songs,
                hasMore = hasMore,
                onLoadMore = onLoadMore,
                onNavigate = onNavigate,
                isSaved = isSaved,
                isSaving = isSaving,
                isLoadingForPlay = isLoadingForPlay,
                onToggleSave = onToggleSave,
                onPlayAll = onPlayAll,
                onShuffle = onShuffle,
            )
            is AlbumState.Error -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(uiState.message, color = MaterialTheme.colorScheme.error)
            }
        }

        IconButton(
            onClick = onBack,
            modifier = Modifier.padding(8.dp).align(Alignment.TopStart)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Atrás",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun AlbumScreenContent(
    albumPage: AlbumPage,
    songs: List<SongItem>,
    hasMore: Boolean = false,
    onLoadMore: () -> Unit = {},
    onNavigate: (Route) -> Unit,
    isSaved: Boolean = false,
    isSaving: Boolean = false,
    isLoadingForPlay: Boolean = false,
    onToggleSave: () -> Unit = {},
    onPlayAll: () -> Unit = {},
    onShuffle: () -> Unit = {},
) {
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)

    androidx.compose.foundation.layout.BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        if (maxWidth < 800.dp) {
            AlbumScreenCompact(
                albumPage = albumPage,
                songs = songs,
                hasMore = hasMore,
                onLoadMore = onLoadMore,
                onSurfaceColor = onSurfaceColor,
                onSurfaceVariant = onSurfaceVariant,
                onNavigate = onNavigate,
                isSaved = isSaved,
                isSaving = isSaving,
                isLoadingForPlay = isLoadingForPlay,
                onToggleSave = onToggleSave,
                onPlayAll = onPlayAll,
                onShuffle = onShuffle,
            )
        } else {
            AlbumScreenWide(
                albumPage = albumPage,
                songs = songs,
                hasMore = hasMore,
                onLoadMore = onLoadMore,
                onSurfaceColor = onSurfaceColor,
                onSurfaceVariant = onSurfaceVariant,
                onNavigate = onNavigate,
                isSaved = isSaved,
                isSaving = isSaving,
                isLoadingForPlay = isLoadingForPlay,
                onToggleSave = onToggleSave,
                onPlayAll = onPlayAll,
                onShuffle = onShuffle,
            )
        }
    }
}
