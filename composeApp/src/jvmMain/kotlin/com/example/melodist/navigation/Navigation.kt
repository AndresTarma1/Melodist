package com.example.melodist.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.stack.animation.fade
import com.arkivanov.decompose.extensions.compose.stack.animation.stackAnimation
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.example.melodist.ui.components.MiniPlayer
import com.example.melodist.ui.components.NowPlayingPanel
import com.example.melodist.viewmodels.PlayerViewModel
import com.example.melodist.ui.screens.*
import com.example.melodist.utils.LocalDownloadViewModel
import com.example.melodist.utils.LocalPlayerViewModel
import com.example.melodist.viewmodels.DownloadViewModel
import org.koin.compose.koinInject

// ─── Rail tab definitions ──────────────────────────────

data class TabInfo(
    val config: ScreenConfig,
    val label: String,
    val icon: ImageVector
)

private val mainTabs = listOf(
    TabInfo(ScreenConfig.Home, "Inicio", Icons.Filled.Home),
    TabInfo(ScreenConfig.Search, "Buscar", Icons.Filled.Search),
    TabInfo(ScreenConfig.Library, "Librería", Icons.Filled.LibraryMusic),
)

private val bottomTabs = listOf(
    TabInfo(ScreenConfig.Account, "Cuenta", Icons.Filled.Person),
    TabInfo(ScreenConfig.Settings, "Ajustes", Icons.Filled.Settings),
)

// ─── Main desktop layout ──────────────────────────────

@Composable
fun NavigationDesktop(rootComponent: RootComponent) {
    val childStack by rootComponent.childStack.subscribeAsState()
    val activeConfig = childStack.active.configuration

    val playerViewModel: PlayerViewModel = koinInject()
    val downloadViewModel: DownloadViewModel = koinInject()

    val playerState by playerViewModel.uiState.collectAsState()
    val progressState by playerViewModel.progressState.collectAsState()
    var isNowPlayingExpanded by remember { mutableStateOf(false) }

    CompositionLocalProvider(
        LocalPlayerViewModel provides playerViewModel,
        LocalDownloadViewModel provides downloadViewModel
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface
        ) {
            Row(modifier = Modifier.fillMaxSize()) {

                // Navigation Rail
                NavigationRail(
                    modifier = Modifier.width(90.dp),
                    containerColor = Color.Transparent,
                    header = {
                        Icon(
                            Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 24.dp).size(32.dp)
                        )
                    }
                ) {
                    Column(
                        modifier = Modifier.fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        mainTabs.forEach { tab ->
                            NavigationRailItem(
                                selected = activeConfig == tab.config,
                                onClick = {
                                    isNowPlayingExpanded = false  // Collapse full player on nav change
                                    rootComponent.switchTab(tab.config)
                                },
                                icon = { Icon(tab.icon, null) },
                                label = { Text(tab.label) },
                                alwaysShowLabel = false,
                                colors = navigationRailItemCustomColors()
                            )
                        }

                        Spacer(Modifier.weight(1f))

                        bottomTabs.forEach { tab ->
                            NavigationRailItem(
                                selected = activeConfig == tab.config,
                                onClick = {
                                    isNowPlayingExpanded = false  // Collapse full player on nav change
                                    rootComponent.switchTab(tab.config)
                                },
                                icon = { Icon(tab.icon, null) },
                                label = { Text(tab.label) },
                                alwaysShowLabel = false,
                                colors = navigationRailItemCustomColors()
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                }

                // Content area + MiniPlayer
                val gradientBrush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.07f),
                        MaterialTheme.colorScheme.surface
                    ),
                    startY = 0f,
                    endY = 500f
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 4.dp, top = 12.dp, bottom = 12.dp, end = 12.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .background(gradientBrush)
                ) {
                    // Expanded Now Playing Panel (overlays everything)
                    if (isNowPlayingExpanded && playerState.currentSong != null) {
                        NowPlayingPanel(
                            state = playerState,
                            progressState = progressState,
                            onTogglePlayPause = { playerViewModel.togglePlayPause() },
                            onNext = { playerViewModel.next() },
                            onPrevious = { playerViewModel.previous() },
                            onSeek = { playerViewModel.seekTo(it) },
                            onVolumeChange = { playerViewModel.setVolume(it) },
                            onToggleShuffle = { playerViewModel.toggleShuffle() },
                            onToggleRepeat = { playerViewModel.toggleRepeat() },
                            onCollapse = { isNowPlayingExpanded = false },
                            onQueueItemClick = { playerViewModel.playAtIndex(it) },
                            onNavigate = { route ->
                                isNowPlayingExpanded = false
                                rootComponent.navigateTo(route.toConfig())
                            }
                        )
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Screen content
                            Box(modifier = Modifier.weight(1f)) {
                                Children(
                                    stack = rootComponent.childStack,
                                    animation = stackAnimation(fade())
                                ) { child ->

                                    ScreenRouter(
                                        instance = child.instance,
                                        rootComponent = rootComponent,
                                    )

                                }
                            }

                            // MiniPlayer at the bottom
                            if (playerState.currentSong != null) {
                                MiniPlayer(
                                    state = playerState,
                                    progressState = progressState,
                                    onTogglePlayPause = { playerViewModel.togglePlayPause() },
                                    onNext = { playerViewModel.next() },
                                    onPrevious = { playerViewModel.previous() },
                                    onClickExpand = { isNowPlayingExpanded = true }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Route → ScreenConfig bridge ──────────────────────

fun Route.toConfig(): ScreenConfig = when (this) {
    Route.Home -> ScreenConfig.Home
    Route.Search -> ScreenConfig.Search
    Route.Library -> ScreenConfig.Library
    Route.Account -> ScreenConfig.Account
    Route.Settings -> ScreenConfig.Settings
    is Route.Album -> ScreenConfig.Album(browseId)
    is Route.Playlist -> ScreenConfig.Playlist(playlistId)
    is Route.Artist -> ScreenConfig.Artist(artistId)
}

@Composable
fun ScreenRouter(
    instance: RootComponent.Child,
    rootComponent: RootComponent,
) {
    val navigator = createNavigator(rootComponent)
    when (instance) {
        is RootComponent.Child.Home -> {
            HomeScreenRoute(
                viewModel = instance.component.viewModel,
                onNavigate = navigator,
            )
        }

        is RootComponent.Child.Search -> {
            SearchScreenRoute(
                viewModel = instance.component.viewModel,
                onNavigate = navigator,
            )
        }

        is RootComponent.Child.Album -> {
            AlbumScreenRoute(
                viewModel = instance.component.viewModel,
                onNavigate = navigator,
                onBack = { rootComponent.onBack() },
            )
        }

        is RootComponent.Child.Playlist -> {
            PlaylistScreenRoute(
                viewModel = instance.component.viewModel,
                onNavigate = navigator,
                onBack = { rootComponent.onBack() },
            )
        }

        is RootComponent.Child.Artist -> {
            ArtistScreenRoute(
                onNavigate = navigator,
                onBack = { rootComponent.onBack() },
                viewModel = instance.component.viewModel,
            )
        }

        is RootComponent.Child.Library -> {
            LibraryScreenRoute(
                viewModel = instance.component.viewModel,
                onNavigate = navigator,
            )
        }

        is RootComponent.Child.Account -> {
            AccountScreenRoute(
                viewModel = instance.component.viewModel,
                onNavigate = navigator,
            )
        }

        is RootComponent.Child.Settings -> {
            SettingsScreen()
        }
    }
}

// ─── Utilities ─────────────────────────────────────────

// Helper para simplificar las llamadas
@Composable
fun createNavigator(rootComponent: RootComponent): (Route) -> Unit = { route ->
    rootComponent.navigateTo(route.toConfig())
}

@Composable
fun navigationRailItemCustomColors() = NavigationRailItemDefaults.colors(
    selectedIconColor = MaterialTheme.colorScheme.primary,
    selectedTextColor = MaterialTheme.colorScheme.primary,
    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
    indicatorColor = Color.Transparent
)

@Composable
fun FullScreenMessage(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.headlineMedium)
    }
}
