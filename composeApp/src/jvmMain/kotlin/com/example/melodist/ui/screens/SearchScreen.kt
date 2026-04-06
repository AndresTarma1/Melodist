package com.example.melodist.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NorthWest
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.example.melodist.viewmodels.SearchViewModel
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.melodist.db.entities.SearchHistoryEntry
import com.example.melodist.navigation.Route
import com.example.melodist.ui.components.layout.AppVerticalScrollbar
import com.example.melodist.ui.components.ChipRowSkeleton
import com.example.melodist.ui.components.layout.HorizontalScrollableRow
import com.example.melodist.ui.components.MelodistImage
import com.example.melodist.ui.components.PlaceholderType
import com.example.melodist.ui.components.SongSkeleton
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.DpOffset
import com.example.melodist.ui.helpers.contextMenuArea
import com.example.melodist.utils.LocalPlayerViewModel
import com.example.melodist.viewmodels.PlayerViewModel
import com.example.melodist.viewmodels.SearchState
import com.example.melodist.ui.components.SongContextMenu
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.YTItem


@Composable
fun SearchScreenRoute(
    viewModel: SearchViewModel,
    onNavigate: (Route) -> Unit,
) {

    val playerViewModel = LocalPlayerViewModel.current

    val uiState by viewModel.uiState.collectAsState()
    val query by viewModel.searchQuery.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val searchHistory by viewModel.searchHistory.collectAsState()

    SearchScreen(
        uiState = uiState,
        query = query,
        suggestions = suggestions,
        filter = filter,
        searchHistory = searchHistory,
        onQueryChange = { viewModel.onQueryChange(it) },
        onSearch = { viewModel.search() },
        onFilterChange = { viewModel.onFilterChange(it) },
        onLoadMore = { viewModel.searchContinuation() },
        onNavigate = onNavigate,
        onDeleteHistoryEntry = { viewModel.deleteHistoryEntry(it) },
        onClearHistory = { viewModel.clearHistory() },
        playerViewModel = playerViewModel
    )
}

@Composable
fun SearchScreen(
    uiState: SearchState,
    query: String,
    suggestions: List<String>,
    filter: YouTube.SearchFilter?,
    searchHistory: List<SearchHistoryEntry>,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onFilterChange: (YouTube.SearchFilter?) -> Unit,
    onLoadMore: () -> Unit,
    onNavigate: (Route) -> Unit,
    onDeleteHistoryEntry: (String) -> Unit,
    onClearHistory: () -> Unit,
    playerViewModel: PlayerViewModel? = null
) {
    var active by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Color.Transparent
    ) {
        Column(
            Modifier
                .fillMaxSize()
        ) {
            SearchSection(
                query = query,
                active = active,
                suggestions = suggestions,
                searchHistory = searchHistory,
                onActiveChange = { active = it },
                onQueryChange = onQueryChange,
                onSearch = {
                    onSearch()
                    active = false
                },
                onDeleteHistoryEntry = onDeleteHistoryEntry,
                onClearHistory = onClearHistory
            )

            ResultsList(
                uiState = uiState,
                filter = filter,
                onItemClick = { item ->
                    when (item) {

                        is SongItem -> {
                            playerViewModel?.playSingle(item)
                        }
                        else -> {
                            val route = when (item) {
                                is AlbumItem -> Route.Album(item.id)
                                is PlaylistItem -> Route.Playlist(item.id)
                                is ArtistItem -> Route.Artist(item.id)
                            }
                            onNavigate(route)
                        }
                    }
                },
                onFilterChange = onFilterChange,
                onLoadMore = onLoadMore,
            )
        }
    }

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchSection(
    query: String,
    active: Boolean,
    suggestions: List<String>,
    searchHistory: List<SearchHistoryEntry>,
    onActiveChange: (Boolean) -> Unit,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onDeleteHistoryEntry: (String) -> Unit,
    onClearHistory: () -> Unit
) {
    SearchBar(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Transparent)
            .padding(horizontal = if (active) 0.dp else 16.dp)
            .animateContentSize(), // Suaviza la expansión
        inputField = {
            SearchBarDefaults.InputField(
                query = query,
                onQueryChange = onQueryChange,
                onSearch = { onSearch(query) },
                expanded = active,
                onExpandedChange = onActiveChange,
                placeholder = {
                    Text(
                        "Canciones, artistas, álbumes...",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                }
            )
        },
        expanded = active,
        onExpandedChange = onActiveChange,
    ) {
        // Usamos LazyColumn para mejor rendimiento que Column + verticalScroll
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (query.isEmpty()) {
                if (searchHistory.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(end = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SectionHeader("Búsquedas recientes")
                            Text(
                                text = "Borrar todo",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onClearHistory() }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    items(searchHistory) { entry ->
                        HistoryListItem(
                            query = entry.query,
                            onClick = {
                                onQueryChange(entry.query)
                                onSearch(entry.query)
                            },
                            onDelete = { onDeleteHistoryEntry(entry.query) }
                        )
                    }
                } else {
                    item {
                        SectionHeader("Búsquedas recientes")
                    }
                    item {
                        EmptyStateText()
                    }
                }
            } else {
                item {
                    SectionHeader("Sugerencias")
                }

                items(suggestions) { suggestion ->
                    SuggestionListItem(
                        suggestion = suggestion,
                        onClick = {
                            onQueryChange(suggestion)
                            onSearch(suggestion)
                        }
                    )
                }

                if (suggestions.isEmpty() && query.length >= 2) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                strokeWidth = 3.dp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp
        ),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 12.dp)
    )
}

@Composable
private fun SuggestionListItem(suggestion: String, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        headlineContent = {
            Text(
                text = suggestion,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Medium
                )
            )
        },
        leadingContent = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.TrendingUp, // Icono más dinámico
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        },
        trailingContent = {
            Icon(
                imageVector = Icons.Default.NorthWest, // Flecha típica de "completar búsqueda"
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
private fun HistoryListItem(query: String, onClick: () -> Unit, onDelete: () -> Unit) {
    ListItem(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        headlineContent = {
            Text(
                text = query,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Medium
                )
            )
        },
        leadingContent = {
            Icon(
                imageVector = Icons.Default.History,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        },
        trailingContent = {
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Eliminar",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
private fun EmptyStateText() {
    Text(
        text = "No hay búsquedas recientes aún.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
    )
}

@Composable
fun FilterRow(
    selectedFilter: YouTube.SearchFilter?,
    onFilterSelected: (YouTube.SearchFilter?) -> Unit
) {
    val filters = listOf(
        "Todo" to null,
        "Videos" to YouTube.SearchFilter.FILTER_VIDEO,
        "Canciones" to YouTube.SearchFilter.FILTER_SONG,
        "Álbumes" to YouTube.SearchFilter.FILTER_ALBUM,
        "Artistas" to YouTube.SearchFilter.FILTER_ARTIST,
        "Playlists" to YouTube.SearchFilter.FILTER_COMMUNITY_PLAYLIST
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HorizontalScrollableRow(
            modifier = Modifier.fillMaxWidth(),
            state = rememberLazyListState(),
            contentPadding = PaddingValues(horizontal = 0.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filters){ (label, f) ->
                val isSelected = selectedFilter == f
                FilterChip(
                    selected = isSelected,
                    onClick = { onFilterSelected(f) },
                    label = { Text(label) },
                    shape = RoundedCornerShape(50.dp),
                )

            }
        }


    }

}


@Composable
fun ResultsList(
    uiState: SearchState,
    onItemClick: (YTItem) -> Unit,
    filter: YouTube.SearchFilter?,
    onFilterChange: (YouTube.SearchFilter?) -> Unit,
    onLoadMore: () -> Unit,
) {
    val scrollable = rememberLazyListState()

    val items = when (uiState) {
        is SearchState.Success -> uiState.items
        is SearchState.SummarySuccess ->
            uiState.summary.summaries.flatMap { it.items }

        else -> emptyList()
    }

    val shouldLoadMore = remember(uiState) {
        derivedStateOf {
            if (uiState !is SearchState.Success) return@derivedStateOf false

            val layoutInfo = scrollable.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisibleItem =
                layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0

            lastVisibleItem >= totalItems - 3 &&
                    !uiState.isLoadingMore &&
                    uiState.continuation != null
        }
    }

    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value) {
            onLoadMore()
        }
    }

    when (uiState) {

        is SearchState.Loading -> {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                item {
                    ChipRowSkeleton()
                }

                items(10) {
                    SongSkeleton()
                }
            }
        }

        is SearchState.Error -> {
            EmptyStateView(
                icon = Icons.Default.Close,
                message = "Algo salió mal"
            )
        }

        else -> {

            if (items.isEmpty()) {
                EmptyStateView(
                    icon = Icons.Default.Search,
                    message = "No se encontraron resultados"
                )
            } else {
                Box {

                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(end = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        state = scrollable
                    ) {

                        item {
                            FilterRow(
                                selectedFilter = filter,
                                onFilterSelected = onFilterChange
                            )
                        }

                        items(items) { item ->
                            SearchResultItem(item, onItemClick)
                        }

                        if (uiState is SearchState.Success && uiState.isLoadingMore) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        strokeWidth = 3.dp
                                    )
                                }
                            }
                        }
                    }

                    AppVerticalScrollbar(
                        state = scrollable,
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(end = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyStateView(icon: ImageVector, message: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun SearchResultItem(item: YTItem, onItemClick: (YTItem) -> Unit) {
    // 1. Definimos propiedades dinámicas basadas en el tipo
    val shape = when (item) {
        is ArtistItem -> CircleShape // Los artistas siempre son circulares
        is PlaylistItem -> RoundedCornerShape(4.dp) // Las playlists suelen ser más "cuadradas"
        else -> RoundedCornerShape(8.dp) // Canciones y álbumes con bordes suavizados
    }

    val imageSize = when (item) {
        is PlaylistItem -> 64.dp // Un poco más grande para destacar
        is ArtistItem -> 56.dp
        else -> 52.dp
    }



    var showMenu by remember { mutableStateOf(false) }
    var menuOffset by remember { mutableStateOf(DpOffset.Zero) }

    var isHovered by remember { mutableStateOf(false) }

    Box {
        ListItem(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(if (isHovered) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f) else Color.Transparent)
                .clickable { onItemClick(item) }
                .pointerHoverIcon(PointerIcon.Hand)
                .contextMenuArea(
                    enabled = item is SongItem,
                    onHoverChange = { isHovered = it },
                    onMenuAction = { offset ->
                        menuOffset = offset
                        showMenu = true
                    }
                )
                .padding(vertical = 2.dp), // Espaciado sutil entre items
            headlineContent = {
            Text(
                text = item.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        supportingContent = {
            // 2. Construimos un subtítulo rico en información
            val subtitle = when (item) {
                is SongItem -> {
                    val artists = item.artists.joinToString { it.name }
                    val album = item.album?.name?.let { " • $it" } ?: ""
                    "$artists$album"
                }

                is AlbumItem -> {
                    val artists = item.artists?.joinToString { it.name } ?: "Álbum"
                    "Álbum • $artists"
                }

                is ArtistItem -> "Artista"
                is PlaylistItem -> {
                    val author = item.author?.name?.let { " • $it" } ?: ""
                    "Playlist$author"
                }
            }
            Text(
                text = subtitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        leadingContent = {
            // 3. Contenedor de imagen dinámico
            MelodistImage(
                url = item.thumbnail,
                contentDescription = item.title,
                modifier = Modifier.size(imageSize),
                shape = shape,
                placeholderType = when (item) {
                    is ArtistItem -> PlaceholderType.ARTIST
                    is AlbumItem -> PlaceholderType.ALBUM
                    is PlaylistItem -> PlaceholderType.PLAYLIST
                    else -> PlaceholderType.SONG
                },
                contentScale = ContentScale.Crop,
                iconSize = if (item is PlaylistItem) 32.dp else 24.dp
            )
        },
        trailingContent = {
            if (item is SongItem) {
                IconButton(onClick = { 
                    menuOffset = DpOffset.Zero
                    showMenu = true 
                }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Más opciones",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )

    if (item is SongItem) {
        SongContextMenu(
            expanded = showMenu,
            onDismiss = { showMenu = false },
            song = item,
            offset = menuOffset
        )
    }
    }
}

//@Composable
//fun SearchResultItem(item: YTItem, onItemClick: (YTItem) -> Unit) {
//    ListItem(
//        modifier = Modifier
//            .padding(horizontal = 8.dp)
//            .clip(RoundedCornerShape(12.dp))
//            .clickable { onItemClick(item) },
//        headlineContent = {
//            Text(item.title, maxLines = 1, fontWeight = FontWeight.SemiBold)
//        },
//        supportingContent = {
//            val subtitle = when (item) {
//                is SongItem -> item.artists.joinToString { it.name }
//                is AlbumItem -> item.artists?.joinToString { it.name } ?: "Álbum"
//                is ArtistItem -> "Artista"
//                is PlaylistItem -> "Playlist"
//            }
//            Text(subtitle, maxLines = 1)
//        },
//        leadingContent = {
//            Surface(
//                shape = if (item is ArtistItem) RoundedCornerShape(50.dp) else RoundedCornerShape(8.dp),
//                modifier = Modifier.size(52.dp),
//            ) {
//                Box(modifier = Modifier.fillMaxSize().background(Color.Gray.copy(alpha = 0.3f))) {
//                    Icon(
//                        Icons.Default.MusicNote, contentDescription = null, modifier =
//
//                            Modifier.size(24.dp).align(Alignment.Center)
//                    )
//                }
//            }
//        },
//        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
//    )
//}
