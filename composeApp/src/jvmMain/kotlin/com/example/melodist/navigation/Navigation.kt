package com.example.melodist.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.stack.animation.fade
import com.arkivanov.decompose.extensions.compose.stack.animation.stackAnimation
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.example.melodist.ui.components.MiniPlayer
import com.example.melodist.ui.components.player.NowPlayingLayout
import com.example.melodist.ui.components.player.PlaybackQueuePanel
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

    val playerViewModel: PlayerViewModel = LocalPlayerViewModel.current

    val playerState by playerViewModel.uiState.collectAsState()
    val progressState by playerViewModel.progressState.collectAsState()
    var isNowPlayingExpanded by remember { mutableStateOf(false) }
    var isQueueVisible by remember { mutableStateOf(false) }

    // Estado para el ancho de la cola. Empieza en 300.dp
    var queueWidth by remember { mutableStateOf(300.dp) }
    val animatedWidth by animateDpAsState(queueWidth)
    val density = LocalDensity.current

    // Fondo global (el "océano" sobre el que flotan las islas)
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(modifier = Modifier.fillMaxSize()) {

            // ─── Navigation Rail (Intacto) ────────────────────────
            NavigationRail(
                modifier = Modifier.width(90.dp),
                containerColor = Color.Transparent,
            ) {
                Column(
                    modifier = Modifier.fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    mainTabs.forEach { tab ->
                        NavigationRailItem(
                            selected = activeConfig == tab.config,
                            onClick = {
                                isNowPlayingExpanded = false
                                isQueueVisible = false
                                rootComponent.switchTab(tab.config)
                            },
                            icon = { Icon(tab.icon, null) },
                            label = { Text(tab.label) },
                            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                            alwaysShowLabel = false,
                            colors = navigationRailItemCustomColors()
                        )
                    }

                    Spacer(Modifier.weight(1f))

                    bottomTabs.forEach { tab ->
                        NavigationRailItem(
                            selected = activeConfig == tab.config,
                            onClick = {
                                isNowPlayingExpanded = false
                                isQueueVisible = false
                                rootComponent.switchTab(tab.config)
                            },
                            icon = { Icon(tab.icon, null) },
                            label = { Text(tab.label) },
                            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                            alwaysShowLabel = false,
                            colors = navigationRailItemCustomColors()
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }

            // ─── Área de contenido (El Archipiélago de Islas) ─────
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(end = 16.dp, bottom = 16.dp) // Margen exterior seguro
            ) {
                val currentSong = playerState.currentSong

                // Fila Superior: Pantalla Principal + Cola Lateral
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .animateContentSize() // 🔥 suaviza cambios de layout
                ) {

                    // 🏝️ CONTENIDO PRINCIPAL
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                    ) {
                        if (isNowPlayingExpanded && currentSong != null) {
                            NowPlayingLayout(
                                state = playerState,
                                song = currentSong,
                                onCollapse = { isNowPlayingExpanded = false },
                                onNavigate = { route ->
                                    isNowPlayingExpanded = false
                                    rootComponent.navigateTo(route.toConfig())
                                }
                            )
                        } else {
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
                    }

                    // 🏝️ PANEL DERECHO COMPLETO (DIVISOR + QUEUE)
                    androidx.compose.animation.AnimatedContent(
                        modifier = Modifier
                        .clip(RoundedCornerShape(16.dp)),
                        targetState = isQueueVisible,
                        transitionSpec = {
                            slideInHorizontally(
                                animationSpec = spring(dampingRatio = 0.85f),
                                initialOffsetX = { it }
                            ) + fadeIn() togetherWith
                                    slideOutHorizontally(
                                        animationSpec = spring(dampingRatio = 0.85f),
                                        targetOffsetX = { it }
                                    ) + fadeOut()
                        }
                    ) { visible ->

                        if (visible) {
                            Row(
                                modifier = Modifier.fillMaxHeight()
                            ) {

                                // 🎚️ DIVISOR
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .width(12.dp)
                                        .pointerHoverIcon(PointerIcon.Hand)
                                        .pointerInput(Unit) {
                                            detectHorizontalDragGestures { _, dragAmount ->
                                                val dragDp = with(density) { dragAmount.toDp() }
                                                queueWidth = (queueWidth - dragDp)
                                                    .coerceIn(250.dp, 600.dp)
                                            }
                                        }
                                )

                                // 🎵 QUEUE PANEL
                                PlaybackQueuePanel(
                                    state = playerState,
                                    onDismiss = { isQueueVisible = false },
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .width(animatedWidth)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(MaterialTheme.colorScheme.surfaceContainer)
                                )
                            }
                        } else {
                            Spacer(Modifier.width(0.dp)) // evita saltos raros
                        }
                    }


                }
                AnimatedVisibility(
                    visible = currentSong != null,
                    enter = slideInVertically(
                        animationSpec = spring(dampingRatio = 0.8f),
                        initialOffsetY = { it }
                    ) + fadeIn(),
                    exit = slideOutVertically(
                        animationSpec = spring(dampingRatio = 0.8f),
                        targetOffsetY = { it }
                    ) + fadeOut()
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))

                        MiniPlayer(
                            progressState = progressState,
                            onClickExpand = { isNowPlayingExpanded = true },
                            onToggleNowPlaying = { isNowPlayingExpanded = !isNowPlayingExpanded },
                            isNowPlayingExpanded = isNowPlayingExpanded,
                            onToggleQueue = { isQueueVisible = !isQueueVisible },
                            isQueueVisible = isQueueVisible,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                        )
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
            SettingsScreen(viewModel = instance.component.viewModel)
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
