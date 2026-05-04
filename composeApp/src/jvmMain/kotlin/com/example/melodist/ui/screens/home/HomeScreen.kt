package com.example.melodist.ui.screens.home

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.melodist.navigation.Route
import com.example.melodist.ui.components.ChipRowSkeleton
import com.example.melodist.ui.components.SectionSkeleton
import com.example.melodist.ui.components.layout.AppVerticalScrollbar
import com.example.melodist.ui.components.layout.HorizontalScrollableRow
import com.example.melodist.utils.LocalPlayerViewModel
import com.example.melodist.viewmodels.HomeState
import com.example.melodist.viewmodels.HomeUiEvent
import com.example.melodist.viewmodels.HomeViewModel
import com.example.melodist.viewmodels.PlayerViewModel
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.YTItem
import com.metrolist.innertube.pages.ChartsPage
import com.metrolist.innertube.pages.HomePage

// ──────────────────────────────────────────────
// Route — conecta ViewModel con la pantalla tonta
// ──────────────────────────────────────────────
@Composable
fun HomeScreenRoute(
    viewModel: HomeViewModel,
    onNavigate: (Route) -> Unit,
) {
    val playerViewModel = LocalPlayerViewModel.current
    val uiState by viewModel.uiState.collectAsState()
    val charts by viewModel.charts.collectAsState()

    HomeScreen(
        uiState = uiState,
        charts = charts,
        onEvent = viewModel::onEvent,
        onNavigate = onNavigate,
        playerViewModel = playerViewModel,
    )
}

// ──────────────────────────────────────────────
// Screen — scaffold + estado → delega a sub-composables tontos
// ──────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: HomeState,
    charts: ChartsPage?,
    onEvent: (HomeUiEvent) -> Unit,
    onNavigate: (Route) -> Unit,
    playerViewModel: PlayerViewModel? = null,
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
                scrollBehavior = scrollBehavior,
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

                is HomeState.Success -> HomeScreenContent(
                    page = uiState.page,
                    charts = charts,
                    selectedParams = uiState.selectedParams,
                    isLoadingMore = uiState.isLoadingMore,
                    onChipClick = { params -> onEvent(HomeUiEvent.ChipSelected(params)) },
                    onScrollNearEnd = { onEvent(HomeUiEvent.LoadMore) },
                    onNavigate = onNavigate,
                    playerViewModel = playerViewModel,
                )

                is HomeState.Error -> HomeScreenError(
                    message = uiState.message,
                    onRetry = { onEvent(HomeUiEvent.Retry) },
                )
            }
        }
    }
}

// ──────────────────────────────────────────────
// Content — composable tonto, sin lógica de negocio
// ──────────────────────────────────────────────
@Composable
fun HomeScreenContent(
    page: HomePage,
    charts: ChartsPage?,
    selectedParams: String?,
    isLoadingMore: Boolean,
    onChipClick: (String?) -> Unit,
    onScrollNearEnd: () -> Unit,
    onNavigate: (Route) -> Unit,
    playerViewModel: PlayerViewModel? = null,
) {
    val scrollState = rememberScrollState()

    // Único LaunchedEffect permitido: notificar al ViewModel cuando el scroll
    // está cerca del final. La *decisión* de cargar más es del ViewModel.
    LaunchedEffect(scrollState.value, scrollState.maxValue) {
        val nearEnd = scrollState.maxValue > 0 &&
                scrollState.value >= scrollState.maxValue - 1_000
        if (nearEnd) onScrollNearEnd()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            // Chips de filtro
            if (!page.chips.isNullOrEmpty()) {
                ChipFilterRow(
                    chips = page.chips!!,
                    selectedParams = selectedParams,
                    onChipClick = onChipClick,
                )
            }

            // Secciones de contenido
            charts?.sections
                ?.take(2)
                ?.forEach { section ->
                    HomeChartsSection(section, onNavigate, playerViewModel)
                }

            page.sections.forEach { section ->
                HomeSectionRow(
                    section = section,
                    onNavigate = onNavigate,
                    playerViewModel = playerViewModel,
                )
            }

            // Indicador de carga al hacer paginación
            if (isLoadingMore) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(40.dp),
                        strokeWidth = 3.dp,
                    )
                }
            }
        }

        AppVerticalScrollbar(
            state = scrollState,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(12.dp),
        )
    }
}

// ──────────────────────────────────────────────
// Sub-composables tontos — solo renderizan, no deciden
// ──────────────────────────────────────────────

@Composable
private fun ChipFilterRow(
    chips: List<HomePage.Chip>,
    selectedParams: String?,
    onChipClick: (String?) -> Unit,
) {
    val lazyListState = rememberLazyListState()
    Column(Modifier.padding(end = 16.dp)) {
        HorizontalScrollableRow(
            modifier = Modifier.padding(vertical = 12.dp),
            state = lazyListState,
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(chips.size) { index ->
                val chip = chips[index]
                val isSelected = chip.endpoint?.params == selectedParams
                FilterChip(
                    selected = isSelected,
                    onClick = { onChipClick(chip.endpoint?.params) },
                    label = {
                        Text(
                            chip.title,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        )
                    },
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    ),
                    border = null,
                )
            }
        }
    }
}

@Composable
private fun HomeSectionRow(
    section: HomePage.Section,
    onNavigate: (Route) -> Unit,
    playerViewModel: PlayerViewModel?,
) {
    Column(modifier = Modifier.padding(top = 12.dp, bottom = 12.dp, end = 16.dp)) {
        Text(
            text = section.title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )

        val sectionScrollState = rememberLazyListState()
        HorizontalScrollableRow(
            modifier = Modifier.fillMaxWidth(),
            state = sectionScrollState,
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(
                count = section.items.size,
                key = { index -> section.items[index].id }
            ) { index ->
                HomeSectionItem(
                    item = section.items[index],
                    onNavigate = onNavigate,
                    playerViewModel = playerViewModel,
                    modifier = Modifier.animateItem(
                        placementSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    )
                )
            }
        }
    }
}

@Composable
private fun HomeSectionItem(
    item: YTItem,
    onNavigate: (Route) -> Unit,
    playerViewModel: PlayerViewModel?,
    modifier: Modifier = Modifier,
) {
    when (item) {
        is SongItem -> SongHomeItem(
            item = item,
            onClick = { playerViewModel?.playSingle(it as SongItem) },
            modifier = modifier,
        )
        is AlbumItem -> AlbumHomeItem(
            item = item,
            onClick = { onNavigate(Route.Album((it as AlbumItem).browseId)) },
            modifier = modifier,
        )
        is PlaylistItem -> PlaylistHomeItem(
            item = item,
            onClick = { onNavigate(Route.Playlist((it as PlaylistItem).id)) },
            modifier = modifier,
        )
        is ArtistItem -> ArtistHomeItem(
            item = item,
            onClick = { onNavigate(Route.Artist((it as ArtistItem).id)) },
            modifier = modifier,
        )
    }
}

// ──────────────────────────────────────────────
// Loading / Error — sin cambios de lógica
// ──────────────────────────────────────────────

@Composable
fun HomeScreenLoading() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        ChipRowSkeleton()
        repeat(3) { SectionSkeleton() }
    }
}

@Composable
fun HomeScreenError(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Default.ErrorOutline,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.error,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Oops! Algo salió mal",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRetry) { Text("Reintentar") }
    }
}

@Composable
private fun HomeChartsSection(
    section: ChartsPage.ChartSection,
    onNavigate: (Route) -> Unit,
    playerViewModel: PlayerViewModel?,
) {
    Column(modifier = Modifier.padding(top = 12.dp, bottom = 12.dp, end = 16.dp)) {
        Text(
            text = section.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )

        val sectionScrollState = rememberLazyListState()
        HorizontalScrollableRow(
            modifier = Modifier.fillMaxWidth(),
            state = sectionScrollState,
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(
                count = section.items.take(12).size,
                key = { index -> "chart_${section.title}_${section.items[index].id}" }
            ) { index ->
                val item = section.items[index]
                HomeSectionItem(
                    item = item,
                    onNavigate = onNavigate,
                    playerViewModel = playerViewModel,
                    modifier = Modifier.animateItem()
                )
            }
        }
    }
}
