package com.example.melodist.ui.screens.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.melodist.navigation.Route
import com.example.melodist.ui.components.layout.HorizontalScrollableRow
import com.example.melodist.ui.screens.library.tabs.AlbumsTab
import com.example.melodist.ui.screens.library.tabs.ArtistsTab
import com.example.melodist.ui.screens.library.tabs.LibraryMixedTab
import com.example.melodist.ui.screens.library.tabs.PlaylistsTab
import com.example.melodist.viewmodels.LibraryTab
import com.example.melodist.viewmodels.LibraryViewModel
import com.example.melodist.viewmodels.PlayerViewModel
import com.example.melodist.viewmodels.YtmLibraryState
import com.example.melodist.utils.LocalPlayerViewModel
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.PlaylistItem

data class LibraryScreenState(
    val selectedTab: LibraryTab? = null,
    val albums: List<AlbumItem> = emptyList(),
    val artists: List<ArtistItem> = emptyList(),
    val playlists: List<PlaylistItem> = emptyList(),
    val ytmState: YtmLibraryState = YtmLibraryState.Idle,
)

data class LibraryActions(
    val onTabSelected: (LibraryTab) -> Unit,
    val onNavigate: (Route) -> Unit,
    val onRemoveAlbum: (String) -> Unit,
    val onRemoveArtist: (String) -> Unit,
    val onRemovePlaylist: (String) -> Unit,
    val onQuickPlayAlbum: (browseId: String, title: String, onFallback: () -> Unit) -> Unit,
    val onQuickShuffleAlbum: (browseId: String, title: String, onFallback: () -> Unit) -> Unit,
    val onQuickPlayPlaylist: (playlistId: String, title: String, onFallback: () -> Unit) -> Unit,
    val onQuickShufflePlaylist: (playlistId: String, title: String, onFallback: () -> Unit) -> Unit,
    val onRefreshYtm: () -> Unit,
    val onCreatePlaylist: (String) -> Unit,
)

@Composable
fun LibraryScreenRoute(
    viewModel: LibraryViewModel,
    onNavigate: (Route) -> Unit,
) {
    val playerViewModel = LocalPlayerViewModel.current
    val selectedTab by viewModel.selectedTab.collectAsState()
    val albums by viewModel.savedAlbums.collectAsState()
    val artists by viewModel.savedArtists.collectAsState()
    val playlists by viewModel.savedPlaylists.collectAsState()
    val ytmState by viewModel.ytmState.collectAsState()

    val state = LibraryScreenState(
        selectedTab = selectedTab,
        albums = albums,
        artists = artists,
        playlists = playlists,
        ytmState = ytmState,
    )

    val actions = remember(viewModel, onNavigate, playerViewModel) {
        LibraryActions(
            onTabSelected = viewModel::selectTab,
            onNavigate = onNavigate,
            onRemoveAlbum = viewModel::removeAlbum,
            onRemoveArtist = viewModel::removeArtist,
            onRemovePlaylist = viewModel::removePlaylist,
            onQuickPlayAlbum = { browseId, title, onFallback ->
                viewModel.resolveAlbumSongsForPlayback(
                    browseId = browseId,
                    onResolved = { songs ->
                        playerViewModel.playAlbum(
                            songs = songs,
                            startIndex = 0,
                            browseId = browseId,
                            title = title,
                        )
                    },
                    onFallback = onFallback,
                )
            },
            onQuickShuffleAlbum = { browseId, title, onFallback ->
                viewModel.resolveAlbumSongsForPlayback(
                    browseId = browseId,
                    onResolved = { songs ->
                        playerViewModel.playAlbum(
                            songs = songs,
                            startIndex = 0,
                            browseId = browseId,
                            title = title,
                        )
                        playerViewModel.toggleShuffle()
                    },
                    onFallback = onFallback,
                )
            },
            onQuickPlayPlaylist = { playlistId, title, onFallback ->
                viewModel.resolvePlaylistSongsForPlayback(
                    playlistId = playlistId,
                    onResolved = { songs ->
                        playerViewModel.playPlaylist(
                            songs = songs,
                            startIndex = 0,
                            playlistId = playlistId,
                            title = title,
                        )
                    },
                    onFallback = onFallback,
                )
            },
            onQuickShufflePlaylist = { playlistId, title, onFallback ->
                viewModel.resolvePlaylistSongsForPlayback(
                    playlistId = playlistId,
                    onResolved = { songs ->
                        playerViewModel.playPlaylist(
                            songs = songs,
                            startIndex = 0,
                            playlistId = playlistId,
                            title = title,
                        )
                        playerViewModel.toggleShuffle()
                    },
                    onFallback = onFallback,
                )
            },
            onRefreshYtm = viewModel::refreshYtmLibrary,
            onCreatePlaylist = viewModel::createLocalPlaylist,
        )
    }

    LibraryScreen(
        state = state,
        actions = actions,
        playerViewModel = playerViewModel,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    state: LibraryScreenState,
    actions: LibraryActions,
    playerViewModel: PlayerViewModel? = null,
) {
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0f),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Biblioteca",
                        fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.displaySmall.copy(fontSize = 32.sp),
                    )
                },
                actions = {
                    IconButton(
                        onClick = { showCreatePlaylistDialog = true },
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.PlaylistAdd,
                            contentDescription = "Crear playlist local",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (state.ytmState !is YtmLibraryState.Idle) {
                        IconButton(
                            onClick = actions.onRefreshYtm,
                            enabled = state.ytmState !is YtmLibraryState.Loading,
                        ) {
                            if (state.ytmState is YtmLibraryState.Loading) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = "Refrescar biblioteca de YTM",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0f)),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            LibraryTabRow(state.selectedTab, actions.onTabSelected)
            Spacer(Modifier.height(8.dp))

            when (state.selectedTab ?: LibraryTab.LIBRARY) {
                LibraryTab.ALBUMS -> AlbumsTab(
                    albums = state.albums,
                    ytmAlbums = (state.ytmState as? YtmLibraryState.Success)?.albums.orEmpty(),
                    isLoadingYtm = state.ytmState is YtmLibraryState.Loading,
                    onNavigate = actions.onNavigate,
                    onRemove = actions.onRemoveAlbum,
                    onQuickPlayAlbum = actions.onQuickPlayAlbum,
                    onQuickShuffleAlbum = actions.onQuickShuffleAlbum,
                )

                LibraryTab.ARTISTS -> ArtistsTab(
                    artists = state.artists,
                    ytmArtists = (state.ytmState as? YtmLibraryState.Success)?.artists.orEmpty(),
                    isLoadingYtm = state.ytmState is YtmLibraryState.Loading,
                    onNavigate = actions.onNavigate,
                    onRemove = actions.onRemoveArtist,
                )

                LibraryTab.PLAYLISTS -> PlaylistsTab(
                    playlists = state.playlists,
                    ytmPlaylists = (state.ytmState as? YtmLibraryState.Success)?.playlists.orEmpty(),
                    isLoadingYtm = state.ytmState is YtmLibraryState.Loading,
                    onNavigate = actions.onNavigate,
                    onRemove = actions.onRemovePlaylist,
                    playerViewModel = playerViewModel,
                    onQuickPlayPlaylist = actions.onQuickPlayPlaylist,
                    onQuickShufflePlaylist = actions.onQuickShufflePlaylist,
                )

                LibraryTab.LIBRARY -> LibraryMixedTab(
                    state = state,
                    onNavigate = actions.onNavigate,
                    playerViewModel = playerViewModel,
                    onQuickPlayAlbum = actions.onQuickPlayAlbum,
                    onQuickShuffleAlbum = actions.onQuickShuffleAlbum,
                    onQuickPlayPlaylist = actions.onQuickPlayPlaylist,
                    onQuickShufflePlaylist = actions.onQuickShufflePlaylist,
                )
            }
        }
    }

    if (showCreatePlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            title = { Text("Crear playlist local") },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Nombre") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = newPlaylistName.trim()
                        if (name.isNotEmpty()) {
                            actions.onCreatePlaylist(name)
                            newPlaylistName = ""
                            showCreatePlaylistDialog = false
                        }
                    },
                ) { Text("Crear") }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylistDialog = false }) { Text("Cancelar") }
            },
        )
    }
}

@Composable
private fun LibraryTabRow(
    selectedTab: LibraryTab?,
    onTabSelected: (LibraryTab) -> Unit,
) {
    val tabs = listOf(
        LibraryTab.ALBUMS to "Álbumes",
        LibraryTab.ARTISTS to "Artistas",
        LibraryTab.PLAYLISTS to "Playlists",
    )

    HorizontalScrollableRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        state = androidx.compose.foundation.lazy.rememberLazyListState(),
        contentPadding = PaddingValues(horizontal = 24.dp),
    ) {
        tabs.forEach { (tab, label) ->
            val isSelected = selectedTab == tab
            item {
                FilterChip(
                    selected = isSelected,
                    onClick = { onTabSelected(if (isSelected) LibraryTab.LIBRARY else tab) },
                    label = {
                        Text(
                            label,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        )
                    },
                    shape = RoundedCornerShape(50.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    ),
                    border = null,
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                )
            }
        }
    }
}
