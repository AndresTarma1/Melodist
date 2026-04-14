package com.example.melodist.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.melodist.navigation.Route
import com.example.melodist.ui.components.layout.AppVerticalScrollbar
import com.example.melodist.ui.components.DownloadIndicator
import com.example.melodist.ui.components.layout.HorizontalScrollableRow
import com.example.melodist.ui.components.MelodistImage
import com.example.melodist.ui.components.PlaceholderType
import com.example.melodist.ui.components.SongContextMenu
import com.example.melodist.utils.LocalPlayerViewModel
import com.example.melodist.utils.SyncUtils
import org.koin.compose.koinInject
import com.example.melodist.viewmodels.DownloadViewModel
import com.example.melodist.viewmodels.LibraryTab
import com.example.melodist.viewmodels.LibraryAlbumsViewModel
import com.example.melodist.viewmodels.LibraryArtistsViewModel
import com.example.melodist.viewmodels.LibraryPlaylistsViewModel
import com.example.melodist.viewmodels.LibrarySongsViewModel
import com.example.melodist.viewmodels.LibraryViewModel
import com.example.melodist.viewmodels.PlayerViewModel
import com.example.melodist.viewmodels.YtmLibraryState
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import com.example.melodist.ui.helpers.rememberSongDownloadState
import com.example.melodist.ui.helpers.contextMenuArea
import com.example.melodist.ui.screens.shared.formatDuration
import com.example.melodist.utils.LocalDownloadViewModel

data class LibraryScreenState(
    val selectedTab: LibraryTab? = null,
    val songs: List<SongItem> = emptyList(),
    val albums: List<AlbumItem> = emptyList(),
    val artists: List<ArtistItem> = emptyList(),
    val playlists: List<PlaylistItem> = emptyList(),
    val ytmState: YtmLibraryState = YtmLibraryState.Idle
)

data class LibraryActions(
    val onTabSelected: (LibraryTab) -> Unit,
    val onNavigate: (Route) -> Unit,
    val onRemoveSong: (String) -> Unit,
    val onRemoveAlbum: (String) -> Unit,
    val onRemoveArtist: (String) -> Unit,
    val onRemovePlaylist: (String) -> Unit,
    val onRefreshYtm: () -> Unit,
    val onSyncAll: () -> Unit,
    val onCreatePlaylist: (String) -> Unit
)

// ────────────────────────────────────────────────────────
// Route wrapper
// ────────────────────────────────────────────────────────

@Composable
fun LibraryScreenRoute(
    viewModel: LibraryViewModel,
    onNavigate: (Route) -> Unit,
) {

    val playerViewModel = LocalPlayerViewModel.current
    val songsViewModel: LibrarySongsViewModel = koinInject()
    val albumsViewModel: LibraryAlbumsViewModel = koinInject()
    val artistsViewModel: LibraryArtistsViewModel = koinInject()
    val playlistsViewModel: LibraryPlaylistsViewModel = koinInject()
    val syncUtils: SyncUtils = koinInject()
    val selectedTab by viewModel.selectedTab.collectAsState()
    val songs by songsViewModel.savedSongs.collectAsState()
    val albums by albumsViewModel.savedAlbums.collectAsState()
    val artists by artistsViewModel.savedArtists.collectAsState()
    val playlists by playlistsViewModel.savedPlaylists.collectAsState()
    val ytmState by viewModel.ytmState.collectAsState()

    val state = LibraryScreenState(
        selectedTab = selectedTab,
        songs = songs,
        albums = albums,
        artists = artists,
        playlists = playlists,
        ytmState = ytmState
    )

    val actions = remember(viewModel, onNavigate) {
        LibraryActions(
            onTabSelected = { viewModel.selectTab(it) },
            onNavigate = onNavigate,
            onRemoveSong = { viewModel.removeSong(it) },
            onRemoveAlbum = { viewModel.removeAlbum(it) },
            onRemoveArtist = { viewModel.removeArtist(it) },
            onRemovePlaylist = { viewModel.removePlaylist(it) },
            onRefreshYtm = { viewModel.refreshYtmLibrary() },
            onSyncAll = { syncUtils.performFullSync() },
            onCreatePlaylist = { playlistsViewModel.createLocalPlaylist(it) }
        )
    }

    LibraryScreen(
        state = state,
        actions = actions,
        playerViewModel = playerViewModel
    )
}

// ────────────────────────────────────────────────────────
// Main screen
// ────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    state: LibraryScreenState,
    actions: LibraryActions,
    playerViewModel: PlayerViewModel? = null
) {
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Biblioteca",
                        fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.displaySmall.copy(fontSize = 32.sp)
                    )
                },
                actions = {
                    IconButton(
                        onClick = { showCreatePlaylistDialog = true },
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.PlaylistAdd,
                            contentDescription = "Crear playlist local",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    // Mostrar botón refresh de YTM solo cuando hay sesión
                    if (state.ytmState !is YtmLibraryState.Idle) {
                        IconButton(
                            onClick = actions.onRefreshYtm,
                            enabled = state.ytmState !is YtmLibraryState.Loading
                        ) {
                            if (state.ytmState is YtmLibraryState.Loading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = "Refrescar biblioteca de YTM",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // ── Tab Row ──
                LibraryTabRow(state.selectedTab, actions.onTabSelected)

            Spacer(Modifier.height(8.dp))

            // ── Content ──
            when (state.selectedTab ?: LibraryTab.LIBRARY) {
                LibraryTab.SONGS -> SongsTab(
                    songs = state.songs,
                    ytmSongs = if (state.ytmState is YtmLibraryState.Success) state.ytmState.likedSongs else emptyList(),
                    isLoadingYtm = state.ytmState is YtmLibraryState.Loading,
                    onRemove = actions.onRemoveSong,
                    playerViewModel = playerViewModel
                )
                LibraryTab.ALBUMS -> AlbumsTab(
                    albums = state.albums,
                    ytmAlbums = if (state.ytmState is YtmLibraryState.Success) state.ytmState.albums else emptyList(),
                    isLoadingYtm = state.ytmState is YtmLibraryState.Loading,
                    onNavigate = actions.onNavigate,
                    onRemove = actions.onRemoveAlbum
                )
                LibraryTab.ARTISTS -> ArtistsTab(
                    artists = state.artists,
                    ytmArtists = if (state.ytmState is YtmLibraryState.Success) state.ytmState.artists else emptyList(),
                    isLoadingYtm = state.ytmState is YtmLibraryState.Loading,
                    onNavigate = actions.onNavigate,
                    onRemove = actions.onRemoveArtist
                )
                LibraryTab.PLAYLISTS -> PlaylistsTab(
                    playlists = state.playlists,
                    ytmPlaylists = if (state.ytmState is YtmLibraryState.Success) state.ytmState.playlists else emptyList(),
                    isLoadingYtm = state.ytmState is YtmLibraryState.Loading,
                    onNavigate = actions.onNavigate,
                    onRemove = actions.onRemovePlaylist
                )
                LibraryTab.DOWNLOADS -> DownloadsTab(
                    onNavigate = actions.onNavigate,
                )

                LibraryTab.LIBRARY -> LibraryMixedTab(
                    onNavigate = actions.onNavigate,
                    onRemoveSong = actions.onRemoveSong,
                    onRemoveAlbum = actions.onRemoveAlbum,
                    onRemoveArtist = actions.onRemoveArtist,
                    onRemovePlaylist = actions.onRemovePlaylist,
                    onSyncAll = actions.onSyncAll,
                    playerViewModel = playerViewModel
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
                    singleLine = true
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
                    }
                ) { Text("Crear") }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylistDialog = false }) { Text("Cancelar") }
            }
        )
    }
}

// ────────────────────────────────────────────────────────
// Tab Row
// ────────────────────────────────────────────────────────

@Composable
private fun LibraryTabRow(selectedTab: LibraryTab?, onTabSelected: (LibraryTab) -> Unit) {
    val tabs = listOf(
        LibraryTab.SONGS to "Canciones",
        LibraryTab.ALBUMS to "Álbumes",
        LibraryTab.ARTISTS to "Artistas",
        LibraryTab.PLAYLISTS to "Playlists",
        LibraryTab.DOWNLOADS to "Descargas"
    )

    HorizontalScrollableRow(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        state = rememberLazyListState(),
        contentPadding = PaddingValues(horizontal = 24.dp)
    ){

        tabs.forEach { (tab, label) ->
            val isSelected = selectedTab == tab
            item {

                FilterChip(
                    selected = isSelected,
                    onClick = { onTabSelected(tab) },
                    label = {
                        Text(
                            label,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    shape = RoundedCornerShape(50.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    ),
                    border = null,
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                )
            }
        }
    }

}

// ────────────────────────────────────────────────────────
// Section headers
// ────────────────────────────────────────────────────────

@Composable
private fun YtmSectionHeader(title: String, isLoading: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            Icons.Default.CloudDone,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
        )
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 1.5.dp)
        }
    }
}

@Composable
private fun LocalSectionHeader(title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            Icons.Default.PhoneAndroid,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ────────────────────────────────────────────────────────
// Skeleton loader (shimmer)
// ────────────────────────────────────────────────────────

@Composable
private fun LibrarySongSkeleton() {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.25f, targetValue = 0.6f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "shimmerAlpha"
    )
    val color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha * 0.15f)
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(color))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.fillMaxWidth(0.55f).height(13.dp).clip(RoundedCornerShape(6.dp)).background(color))
            Box(Modifier.fillMaxWidth(0.35f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(color))
        }
    }
}

@Composable
private fun LibraryGridSkeleton(count: Int = 4, isCircle: Boolean = false) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.25f, targetValue = 0.6f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "shimmerAlpha"
    )
    val color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha * 0.15f)
    val shape = if (isCircle) CircleShape else RoundedCornerShape(12.dp)
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(count) {
            Column(horizontalAlignment = if (isCircle) Alignment.CenterHorizontally else Alignment.Start) {
                Box(
                    Modifier.aspectRatio(1f).fillMaxWidth()
                        .clip(shape).background(color)
                )
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth(0.7f).height(13.dp).clip(RoundedCornerShape(6.dp)).background(color))
                Spacer(Modifier.height(4.dp))
                Box(Modifier.fillMaxWidth(0.45f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(color))
            }
        }
    }
}

// ────────────────────────────────────────────────────────
// Songs Tab
// ────────────────────────────────────────────────────────

@Composable
private fun SongsTab(
    songs: List<SongItem>,
    ytmSongs: List<SongItem> = emptyList(),
    isLoadingYtm: Boolean = false,
    onRemove: (String) -> Unit,
    playerViewModel: PlayerViewModel? = null
) {
    val isEmpty = songs.isEmpty() && ytmSongs.isEmpty() && !isLoadingYtm
    if (isEmpty) {
        LibraryEmptyState(Icons.Default.MusicNote, "No hay canciones guardadas", "Las canciones que guardes aparecerán aquí")
        return
    }

    val listState = rememberLazyListState()
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // ── YTM liked songs ──
            if (isLoadingYtm) {
                item { YtmSectionHeader("Me gusta en YouTube Music", isLoading = true) }
                items(4) { LibrarySongSkeleton() }
            } else if (ytmSongs.isNotEmpty()) {
                item { YtmSectionHeader("Me gusta en YouTube Music") }
                items(ytmSongs, key = { "ytm_${it.id}" }) { song ->
                    LibrarySongItem(
                        song = song, onRemove = {}, isRemovable = false,
                        onClick = { playerViewModel?.playCustom(ytmSongs, ytmSongs.indexOf(song)) },
                    )
                }
                if (songs.isNotEmpty()) item { Spacer(Modifier.height(8.dp)) }
            }
            // ── Local saved songs ──
            if (songs.isNotEmpty()) {
                if (ytmSongs.isNotEmpty() || isLoadingYtm) item { LocalSectionHeader("Guardadas localmente") }
                items(songs, key = { it.id }) { song ->
                    LibrarySongItem(
                        song = song, onRemove = { onRemove(song.id) },
                        onClick = { playerViewModel?.playCustom(songs, songs.indexOf(song)) }
                    )
                }
            }
        }
        AppVerticalScrollbar(
            state = listState,
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(12.dp)
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun LibrarySongItem(
    song: SongItem,
    onRemove: () -> Unit,
    onClick: () -> Unit = {},
    isRemovable: Boolean = true,
) {
    val downloadViewModel: DownloadViewModel = org.koin.compose.koinInject()
    val downloadState by rememberSongDownloadState(song.id, downloadViewModel)

    var isHovered by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var menuOffset by remember { mutableStateOf(DpOffset.Unspecified) }

    Box {
        ListItem(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(if (isHovered) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f) else Color.Transparent)
                .clickable { onClick() }
                .pointerHoverIcon(PointerIcon.Hand)
                .contextMenuArea(
                    enabled = true,
                    onHoverChange = { isHovered = it },
                    onMenuAction = { offset ->
                        menuOffset = offset
                        showMenu = true
                    }
                ),
            headlineContent = {
                Text(song.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            supportingContent = {
                Text(
                    song.artists.joinToString(", ") { it.name }.ifEmpty { "Artista desconocido" },
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            leadingContent = {
                MelodistImage(
                    url = song.thumbnail, contentDescription = song.title,
                    modifier = Modifier.size(48.dp), shape = RoundedCornerShape(8.dp),
                    placeholderType = PlaceholderType.SONG, iconSize = 22.dp
                )
            },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Download indicator
                    DownloadIndicator(
                        state = downloadState,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    song.duration?.let { dur ->
                        Text(
                            formatDuration(dur), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(4.dp))
                    }
                    IconButton(onClick = {
                        menuOffset = DpOffset(0.dp, 0.dp)
                        showMenu = true
                    }) {
                        Icon(Icons.Default.MoreVert, "Opciones", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )

        // Context menu with download options
        SongContextMenu(
            expanded = showMenu,
            onDismiss = { showMenu = false },
            song = song,
            onRemoveFromLibrary = if (isRemovable) onRemove else null,
            offset = menuOffset
        )
    }
}

// ────────────────────────────────────────────────────────
// Albums Tab
// ────────────────────────────────────────────────────────

@Composable
private fun AlbumsTab(
    albums: List<AlbumItem>,
    ytmAlbums: List<AlbumItem> = emptyList(),
    isLoadingYtm: Boolean = false,
    onNavigate: (Route) -> Unit,
    onRemove: (String) -> Unit
) {
    val isEmpty = albums.isEmpty() && ytmAlbums.isEmpty() && !isLoadingYtm
    if (isEmpty) {
        LibraryEmptyState(Icons.Default.Album, "No hay álbumes guardados", "Guarda álbumes y aparecerán aquí")
        return
    }
    if (isLoadingYtm && ytmAlbums.isEmpty()) {
        Column {
            YtmSectionHeader("Álbumes en YouTube Music", isLoading = true)
            LibraryGridSkeleton(count = 4)
        }
        return
    }
    val gridState = rememberLazyGridState()
    Box(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(minSize = 160.dp),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 80.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (ytmAlbums.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    YtmSectionHeader("Álbumes en YouTube Music")
                }
                items(ytmAlbums, key = { "ytm_${it.browseId}" }) { album ->
                    LibraryGridItem(
                        title = album.title,
                        subtitle = album.artists?.firstOrNull()?.name ?: album.year?.toString() ?: "Álbum",
                        thumbnailUrl = album.thumbnail, placeholderType = PlaceholderType.ALBUM,
                        shape = RoundedCornerShape(12.dp),
                        onClick = { onNavigate(Route.Album(album.browseId)) },
                        onRemove = {}, isRemovable = false
                    )
                }
            }
            if (albums.isNotEmpty()) {
                if (ytmAlbums.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        LocalSectionHeader("Guardados localmente")
                    }
                }
                items(albums, key = { it.browseId }) { album ->
                    LibraryGridItem(
                        title = album.title,
                        subtitle = album.artists?.firstOrNull()?.name ?: album.year?.toString() ?: "Álbum",
                        thumbnailUrl = album.thumbnail, placeholderType = PlaceholderType.ALBUM,
                        shape = RoundedCornerShape(12.dp),
                        onClick = { onNavigate(Route.Album(album.browseId)) },
                        onRemove = { onRemove(album.browseId) }
                    )
                }
            }
        }
        AppVerticalScrollbar(
            state = gridState,
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 4.dp, horizontal = 2.dp)
        )
    }
}

// ────────────────────────────────────────────────────────
// Artists Tab
// ────────────────────────────────────────────────────────

@Composable
private fun ArtistsTab(
    artists: List<ArtistItem>,
    ytmArtists: List<ArtistItem> = emptyList(),
    isLoadingYtm: Boolean = false,
    onNavigate: (Route) -> Unit,
    onRemove: (String) -> Unit
) {
    val isEmpty = artists.isEmpty() && ytmArtists.isEmpty() && !isLoadingYtm
    if (isEmpty) {
        LibraryEmptyState(Icons.Default.Person, "No hay artistas guardados", "Sigue artistas y aparecerán aquí")
        return
    }
    if (isLoadingYtm && ytmArtists.isEmpty()) {
        Column {
            YtmSectionHeader("Artistas suscritos", isLoading = true)
            LibraryGridSkeleton(count = 4, isCircle = true)
        }
        return
    }
    val gridState = rememberLazyGridState()
    Box(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(minSize = 150.dp),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 80.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (ytmArtists.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    YtmSectionHeader("Artistas suscritos")
                }
                items(ytmArtists, key = { "ytm_${it.id}" }) { artist ->
                    LibraryGridItem(
                        title = artist.title, subtitle = "Artista",
                        thumbnailUrl = artist.thumbnail, placeholderType = PlaceholderType.ARTIST,
                        shape = CircleShape, onClick = { onNavigate(Route.Artist(artist.id)) },
                        onRemove = {}, isRemovable = false
                    )
                }
            }
            if (artists.isNotEmpty()) {
                if (ytmArtists.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        LocalSectionHeader("Guardados localmente")
                    }
                }
                items(artists, key = { it.id }) { artist ->
                    LibraryGridItem(
                        title = artist.title, subtitle = "Artista",
                        thumbnailUrl = artist.thumbnail, placeholderType = PlaceholderType.ARTIST,
                        shape = CircleShape, onClick = { onNavigate(Route.Artist(artist.id)) },
                        onRemove = { onRemove(artist.id) }
                    )
                }
            }
        }
        AppVerticalScrollbar(
            state = gridState,
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 4.dp, horizontal = 2.dp)
        )
    }
}

// ────────────────────────────────────────────────────────
// Playlists Tab
// ────────────────────────────────────────────────────────

@Composable
private fun PlaylistsTab(
    playlists: List<PlaylistItem>,
    ytmPlaylists: List<PlaylistItem> = emptyList(),
    isLoadingYtm: Boolean = false,
    onNavigate: (Route) -> Unit,
    onRemove: (String) -> Unit
) {
    val isEmpty = playlists.isEmpty() && ytmPlaylists.isEmpty() && !isLoadingYtm
    if (isEmpty) {
        LibraryEmptyState(Icons.AutoMirrored.Filled.PlaylistPlay, "No hay playlists guardadas", "Guarda playlists y aparecerán aquí")
        return
    }
    if (isLoadingYtm && ytmPlaylists.isEmpty()) {
        Column {
            YtmSectionHeader("Playlists de YouTube Music", isLoading = true)
            LibraryGridSkeleton(count = 4)
        }
        return
    }
    val gridState = rememberLazyGridState()
    Box(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(minSize = 160.dp),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 80.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (ytmPlaylists.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    YtmSectionHeader("Playlists de YouTube Music")
                }
                items(ytmPlaylists, key = { "ytm_${it.id}" }) { playlist ->
                    LibraryGridItem(
                        title = playlist.title,
                        subtitle = playlist.author?.name ?: playlist.songCountText ?: "Playlist",
                        thumbnailUrl = playlist.thumbnail, placeholderType = PlaceholderType.PLAYLIST,
                        shape = RoundedCornerShape(12.dp),
                        onClick = { onNavigate(Route.Playlist(playlist.id)) },
                        onRemove = {}, isRemovable = false
                    )
                }
            }
            if (playlists.isNotEmpty()) {
                if (ytmPlaylists.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        LocalSectionHeader("Guardadas localmente")
                    }
                }
                items(playlists, key = { it.id }) { playlist ->
                    LibraryGridItem(
                        title = playlist.title,
                        subtitle = playlist.author?.name ?: playlist.songCountText ?: "Playlist",
                        thumbnailUrl = playlist.thumbnail, placeholderType = PlaceholderType.PLAYLIST,
                        shape = RoundedCornerShape(12.dp),
                        onClick = { onNavigate(Route.Playlist(playlist.id)) },
                        onRemove = { onRemove(playlist.id) }
                    )
                }
            }
        }
        AppVerticalScrollbar(
            state = gridState,
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 4.dp, horizontal = 2.dp)
        )
    }
}

// ────────────────────────────────────────────────────────
// Mixed Tab
// ────────────────────────────────────────────────────────

@Composable
private fun LibraryMixedTab(
    onNavigate: (Route) -> Unit,
    onRemoveSong: (String) -> Unit,
    onRemoveAlbum: (String) -> Unit,
    onRemoveArtist: (String) -> Unit,
    onRemovePlaylist: (String) -> Unit,
    onSyncAll: () -> Unit,
    playerViewModel: PlayerViewModel? = null,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        LocalSectionHeader("Biblioteca")
        Text("Canciones, álbumes, artistas y playlists locales")
        Button(onClick = onSyncAll) { Text("Sincronizar todo") }
        TextButton(onClick = { onNavigate(Route.Library) }) { Text("Ver sección completa") }
    }
}

// ────────────────────────────────────────────────────────
// Downloads Tab
// ────────────────────────────────────────────────────────

@Composable
private fun DownloadsTab(
    onNavigate: (Route) -> Unit,
) {
    val downloadViewModel: DownloadViewModel = LocalDownloadViewModel.current
    val downloadedSongs by downloadViewModel.downloadedSongs.collectAsState()
    val downloadedCount by downloadViewModel.downloadedCount.collectAsState()
    val fullyDownloadedAlbums by downloadViewModel.fullyDownloadedAlbums.collectAsState()
    val fullyDownloadedPlaylists by downloadViewModel.fullyDownloadedPlaylists.collectAsState()

    val hasContent = downloadedCount > 0 || fullyDownloadedAlbums.isNotEmpty() || fullyDownloadedPlaylists.isNotEmpty()

    if (!hasContent) {
        LibraryEmptyState(
            Icons.Default.DownloadDone,
            "No hay descargas",
            "Las canciones que descargues aparecerán aquí"
        )
        return
    }

    val gridState = rememberLazyGridState()
    Box(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(minSize = 160.dp),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 80.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Playlists section ──
            item(span = { GridItemSpan(maxLineSpan) }) {
                LocalSectionHeader("Playlists")
            }

            // "Descargas" card — navigates to PlaylistScreen with LOCAL_DOWNLOADS
            item {
                LibraryGridItem(
                    title = "Descargas",
                    subtitle = "$downloadedCount canciones",
                    thumbnailUrl = downloadedSongs.firstOrNull()?.thumbnail,
                    placeholderType = PlaceholderType.PLAYLIST,
                    shape = RoundedCornerShape(12.dp),
                    onClick = { onNavigate(Route.Playlist("LOCAL_DOWNLOADS")) },
                    isRemovable = false
                )
            }

            // Fully downloaded playlists
            items(fullyDownloadedPlaylists, key = { "dlpl_${it.playlistId}" }) { playlistInfo ->
                LibraryGridItem(
                    title = playlistInfo.playlistName,
                    subtitle = "${playlistInfo.downloadedSongCount} canciones",
                    thumbnailUrl = playlistInfo.thumbnail,
                    placeholderType = PlaceholderType.PLAYLIST,
                    shape = RoundedCornerShape(12.dp),
                    onClick = { onNavigate(Route.Playlist(playlistInfo.playlistId)) },
                    isRemovable = false
                )
            }

            // ── Albums section ──
            if (fullyDownloadedAlbums.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    LocalSectionHeader("Álbumes")
                }
                items(fullyDownloadedAlbums, key = { "dlal_${it.albumId}" }) { albumInfo ->
                    LibraryGridItem(
                        title = albumInfo.albumName,
                        subtitle = "${albumInfo.songs.size} canciones",
                        thumbnailUrl = albumInfo.thumbnail,
                        placeholderType = PlaceholderType.ALBUM,
                        shape = RoundedCornerShape(12.dp),
                        onClick = { onNavigate(Route.Album(albumInfo.albumId)) },
                        isRemovable = false
                    )
                }
            }
        }
        AppVerticalScrollbar(
            state = gridState,
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 4.dp, horizontal = 2.dp)
        )
    }
}

/** Generic card for a downloaded album or playlist — click navigates to the screen. */
@Composable
private fun DownloadedItemCard(
    title: String,
    subtitle: String,
    thumbnail: String?,
    placeholderType: PlaceholderType,
    onClick: () -> Unit,
    onPlay: (() -> Unit)? = null
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MelodistImage(
                url = thumbnail,
                contentDescription = title,
                modifier = Modifier.size(56.dp),
                shape = if (placeholderType == PlaceholderType.ARTIST) CircleShape else RoundedCornerShape(10.dp),
                placeholderType = placeholderType,
                iconSize = 24.dp,
                alignment = if (placeholderType == PlaceholderType.ARTIST) Alignment.TopCenter else Alignment.Center
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (onPlay != null) {
                FilledTonalIconButton(
                    onClick = onPlay,
                    modifier = Modifier.size(36.dp).pointerHoverIcon(PointerIcon.Hand)
                ) {
                    Icon(Icons.Default.PlayArrow, "Reproducir", modifier = Modifier.size(20.dp))
                }
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Abrir",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ────────────────────────────────────────────────────────
// Shared grid item
// ────────────────────────────────────────────────────────

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun LibraryGridItem(
    title: String,
    subtitle: String,
    thumbnailUrl: String?,
    placeholderType: PlaceholderType,
    shape: Shape,
    onClick: () -> Unit,
    onRemove: () -> Unit = {},
    isRemovable: Boolean = true
) {
    val isCircle = shape == CircleShape
    val alignment = if (isCircle) Alignment.CenterHorizontally else Alignment.Start
    val textAlign = if (isCircle) TextAlign.Center else TextAlign.Start
    var isHovered by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    Box {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(if (isHovered) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f) else Color.Transparent)
                .clickable(onClick = onClick)
                .pointerHoverIcon(PointerIcon.Hand)
                .onPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Enter) { isHovered = true }
                .onPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Exit) { isHovered = false }
                .padding(8.dp),
            horizontalAlignment = alignment
        ) {
            Box {
                MelodistImage(
                    url = thumbnailUrl, contentDescription = title,
                    modifier = Modifier.aspectRatio(1f).fillMaxWidth(),
                    shape = shape, placeholderType = placeholderType, iconSize = 40.dp,
                    contentScale = ContentScale.Fit,
                    alignment = if (isCircle) Alignment.TopCenter else Alignment.Center
                )
                if (isRemovable) {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.align(Alignment.TopEnd).size(32.dp).padding(4.dp)
                    ) {
                        Icon(Icons.Default.MoreVert, "Opciones",
                            modifier = Modifier.size(18.dp), tint = Color.White.copy(alpha = 0.9f))
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Eliminar de la biblioteca") },
                                onClick = { onRemove(); showMenu = false },
                                leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(), textAlign = textAlign)
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(), textAlign = textAlign)
        }
    }
}

// ────────────────────────────────────────────────────────
// Empty state
// ────────────────────────────────────────────────────────

@Composable
private fun LibraryEmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
            Spacer(Modifier.height(16.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
        }
    }
}

// ────────────────────────────────────────────────────────
// Helpers
// ────────────────────────────────────────────────────────

