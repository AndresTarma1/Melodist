package com.example.melodist.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.melodist.navigation.Route
import com.example.melodist.ui.components.ChipRowSkeleton
import com.example.melodist.ui.components.layout.AppVerticalScrollbar
import com.example.melodist.ui.components.layout.HorizontalScrollableRow
import com.example.melodist.ui.components.MelodistImage
import com.example.melodist.ui.components.PlaceholderType
import com.example.melodist.ui.components.SectionSkeleton
import com.example.melodist.utils.LocalPlayerViewModel
import com.example.melodist.utils.thumbnailAspectRatio
import com.example.melodist.viewmodels.HomeState
import com.example.melodist.viewmodels.HomeViewModel
import com.example.melodist.viewmodels.PlayerViewModel
import com.metrolist.innertube.models.YTItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.pages.HomePage
import com.example.melodist.ui.components.SongContextMenu
import com.example.melodist.ui.helpers.rememberSongDownloadState
import com.example.melodist.utils.LocalDownloadViewModel
import com.example.melodist.ui.helpers.contextMenuArea
import com.example.melodist.ui.components.DownloadIndicator

@Composable
fun HomeScreenRoute(
    viewModel: HomeViewModel,
    onNavigate: (Route) -> Unit,
) {

    val playerViewModel = LocalPlayerViewModel.current
    val downloadViewModel = LocalDownloadViewModel.current
    val uiState by viewModel.uiState.collectAsState()
    val currentParams by viewModel.currentParams.collectAsState()

    HomeScreen(
        uiState = uiState,
        currentParams = currentParams,
        onChipClick = { params -> viewModel.loadHome(params) },
        onLoadMore = { viewModel.loadMore() },
        onRetry = { viewModel.loadHome() },
        onNavigate = onNavigate,
        playerViewModel = playerViewModel
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: HomeState,
    currentParams: String?,
    onChipClick: (String?) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onNavigate: (Route) -> Unit,
    playerViewModel: PlayerViewModel? = null
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Melodist",
                        fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.displaySmall.copy(fontSize = 32.sp)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
                ),
                scrollBehavior = scrollBehavior
            )
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (uiState) {
                is HomeState.Loading -> HomeScreenLoading()
                is HomeState.Success -> {
                    HomeScreenContent(
                        page = uiState.page,
                        isLoadingMore = uiState.isLoadingMore,
                        selectedParams = currentParams,
                        onChipClick = onChipClick,
                        onLoadMore = onLoadMore,
                        onNavigate = onNavigate,
                        playerViewModel = playerViewModel
                    )
                }

                is HomeState.Error -> HomeScreenError(
                    message = uiState.message,
                    onRetry = onRetry
                )
            }
        }
    }
}

@Composable
fun HomeScreenContent(
    page: HomePage,
    isLoadingMore: Boolean,
    selectedParams: String?,
    onChipClick: (String?) -> Unit,
    onLoadMore: () -> Unit,
    onNavigate: (Route) -> Unit,
    playerViewModel: PlayerViewModel? = null
) {
    val scrollState = rememberScrollState()

    // Cargar más cuando se llega al final
    LaunchedEffect(scrollState.value, scrollState.maxValue) {
        if (scrollState.value > 0 && scrollState.maxValue > 0 && scrollState.value >= scrollState.maxValue - 1000) {
            onLoadMore()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            // Filtros (Chips)
            if (!page.chips.isNullOrEmpty()) {
                val lazyListState = rememberLazyListState()
                Column(Modifier.padding(end = 16.dp)) {
                    HorizontalScrollableRow(
                        modifier = Modifier.padding(vertical = 12.dp),
                        state = lazyListState,
                        contentPadding = PaddingValues(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(page.chips!!.size) { index ->
                            val chip = page.chips!![index]
                            val isSelected = chip.endpoint?.params == selectedParams

                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    if (isSelected) onChipClick(null)
                                    else onChipClick(chip.endpoint?.params)
                                },
                                label = {
                                    Text(
                                        chip.title,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                },
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                ),
                                border = null
                            )
                        }
                    }
                }
            }

            // Secciones
            page.sections.forEach { section ->
                Column(modifier = Modifier.padding(top = 12.dp, bottom = 12.dp, end= 16.dp)) {
                    Text(
                        text = section.title,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                    )

                    val sectionScrollState = rememberLazyListState()
                    HorizontalScrollableRow(
                        modifier = Modifier.fillMaxWidth(),
                        state = sectionScrollState,
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(section.items.size) { index ->
                            MusicItem(
                                item = section.items[index],
                                onClick = { item ->
                                    when (item) {
                                        is AlbumItem -> onNavigate(Route.Album(item.browseId))
                                        is ArtistItem -> onNavigate(Route.Artist(item.id))
                                        is PlaylistItem -> onNavigate(Route.Playlist(item.id))
                                        is SongItem -> {
                                            playerViewModel?.playSingle(item)
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }

            if (isLoadingMore) {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(40.dp), strokeWidth = 3.dp)
                }
            }
        }

        AppVerticalScrollbar(
            state = scrollState,
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(12.dp)
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MusicItem(item: YTItem, onClick: (YTItem) -> Unit) {
    val isArtist = item is ArtistItem
    val imageShape = if (isArtist) CircleShape else RoundedCornerShape(8.dp)
    val alignment = if (isArtist) Alignment.CenterHorizontally else Alignment.Start
    val titleTextAlign: TextAlign = if (isArtist) TextAlign.Center else TextAlign.Start

    val placeholderType = when (item) {
        is ArtistItem -> PlaceholderType.ARTIST
        is AlbumItem -> PlaceholderType.ALBUM
        is PlaylistItem -> PlaceholderType.PLAYLIST
        else -> PlaceholderType.SONG
    }

    val cardHeight = 180.dp
    val aspectRatio = item.thumbnailAspectRatio()
    val cardWidth = cardHeight * aspectRatio
    val contentPadding = 10.dp
    val downloadViewModel = LocalDownloadViewModel.current

    var showMenu by remember { mutableStateOf(false) }
    var menuOffset by remember { mutableStateOf(DpOffset.Zero) }
    var isHovered by remember { mutableStateOf(false) }

    // Material 3: Usamos surfaceVariant para el hover en lugar de modificar alphas a mano
    val hoverColor = if (isHovered) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f) else Color.Transparent

    val downloadState by if (item is SongItem) {
        rememberSongDownloadState(item.id, downloadViewModel)
    } else {
        remember { mutableStateOf(null) }
    }

    // Movemos los modificadores de interacción al Box raíz
    Box(
        modifier = Modifier
            .width(cardWidth + contentPadding + contentPadding)
            .clip(RoundedCornerShape(12.dp))
            .background(hoverColor)
            .clickable { onClick(item) }
            .pointerHoverIcon(PointerIcon.Hand)
            .contextMenuArea(
                enabled = item is SongItem,
                onHoverChange = { isHovered = it },
                onMenuAction = { offset ->
                    menuOffset = offset
                    showMenu = true
                }
            )
            .padding(contentPadding) // Padding interno para que actúe como una tarjeta
    ) {
        Column(
            horizontalAlignment = alignment
        ) {
            Box {
                MelodistImage(
                    url = item.thumbnail,
                    contentDescription = item.title,
                    modifier = Modifier
                        .height(cardHeight)
                        .width(cardWidth),
                    shape = imageShape,
                    placeholderType = placeholderType,
                    iconSize = if (isArtist) 56.dp else 40.dp,
                    // Crop suele verse más premium en portadas de álbumes para evitar bordes negros
                    contentScale = ContentScale.Crop,
                    alignment = if (isArtist) Alignment.TopCenter else Alignment.Center
                )

                if (item is SongItem && downloadState != null) {
                    DownloadIndicator(
                        state = downloadState,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), CircleShape)
                            .padding(4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold, // SemiBold es más limpio que Bold para títulos
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
                textAlign = titleTextAlign
            )

            val artistText = when (item) {
                is SongItem -> item.artists.firstOrNull()?.name ?: ""
                is AlbumItem -> item.artists?.firstOrNull()?.name ?: "Álbum"
                is ArtistItem -> "Artista"
                is PlaylistItem -> item.author?.name ?: "Lista"
            }

            if (artistText.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = artistText,
                    style = MaterialTheme.typography.bodyMedium,
                    // Semántica de Material 3 para textos secundarios
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = titleTextAlign
                )
            }
        }

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
@Composable
fun HomeScreenLoading() {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ChipRowSkeleton()
        repeat(3) {
            SectionSkeleton()
        }
    }
}

@Composable
fun HomeScreenError(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.ErrorOutline,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Oops! Algo salió mal",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRetry) {
            Text("Reintentar")
        }
    }
}
