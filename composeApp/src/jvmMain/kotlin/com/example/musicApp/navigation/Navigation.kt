package com.example.musicApp.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.ExperimentalDecomposeApi

// Ahora (ojo: paquete "experimental", no se pueden mezclar con los anteriores)
import com.arkivanov.decompose.extensions.compose.experimental.stack.ChildStack
import com.arkivanov.decompose.extensions.compose.experimental.stack.animation.fade
import com.arkivanov.decompose.extensions.compose.experimental.stack.animation.stackAnimation

import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.example.musicApp.data.repository.LayoutMode
import com.example.musicApp.data.repository.NavigationRailStyle
import com.example.musicApp.data.repository.UserPreferencesRepository
import com.example.musicApp.ui.components.MiniPlayer
import com.example.musicApp.ui.components.dialogs.SnackBar
import com.example.musicApp.ui.components.player.PlaybackQueuePanel
import com.example.musicApp.ui.screens.library.CsvImportProgressOverlay
import com.example.musicApp.viewmodels.LibraryPlaylistsViewModel
import com.example.musicApp.viewmodels.PlayerProgressState
import com.example.musicApp.viewmodels.PlayerViewModel
import org.koin.compose.koinInject
import com.example.musicApp.ui.screens.*
import com.example.musicApp.ui.screens.album.AlbumScreenRoute
import com.example.musicApp.ui.screens.home.HomeScreenRoute
import com.example.musicApp.ui.screens.library.LibraryScreenRoute
import com.example.musicApp.ui.themes.LocalDimens
import com.example.musicApp.ui.themes.LocalLayoutMode
import com.example.musicApp.utils.LocalPlayerViewModel
import com.example.musicApp.utils.LocalSnackbarHostState
import lyrik.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource


data class TabInfo(
    val config: ScreenConfig,
    val icon: ImageVector
)

val mainTabs = listOf(
    TabInfo(ScreenConfig.Home, Icons.Filled.Home),
    TabInfo(ScreenConfig.Search, Icons.Filled.Search),
    TabInfo(ScreenConfig.Library, Icons.Filled.LibraryMusic),
)

val bottomTabs = listOf(
    TabInfo(ScreenConfig.ListenTogether, Icons.Filled.Groups),
    TabInfo(ScreenConfig.Account, Icons.Filled.Person),
    TabInfo(ScreenConfig.Settings, Icons.Filled.Settings),
)


@OptIn(ExperimentalDecomposeApi::class)
@Composable
fun NavigationDesktop(rootComponent: RootComponent, userPreferences: UserPreferencesRepository) {
    val childStack by rootComponent.childStack.subscribeAsState()
    val activeConfig = childStack.active.configuration

    val playerViewModel: PlayerViewModel = LocalPlayerViewModel.current
    val snackbarHostState = LocalSnackbarHostState.current
    val playlistsViewModel = koinInject<LibraryPlaylistsViewModel>()
    val csvImportState by playlistsViewModel.csvImportState.collectAsState()

    val navigationRailStyle by userPreferences.navigationRailStyle.collectAsState(NavigationRailStyle.DEFAULT)

    val playerState by playerViewModel.uiState.collectAsState()
    var isQueueVisible by remember { mutableStateOf(false) }

    val fullScreenPlayer by userPreferences.fullScreenPlayer.collectAsState(false)

    val isOnNowPlaying = activeConfig is ScreenConfig.NowPlaying

    val currentSong = playerState.currentSong
    val queueWidth = 420.dp

    // Nos permite entender o mostrar los errores de reproducción en un Snackbar, sin bloquear la UI principal.
    LaunchedEffect(playerViewModel) {
        playerViewModel.playbackMessages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }


    SharedTransitionLayout(modifier = Modifier.fillMaxSize()) {
        val sharedTransitionScope = this
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
        ) {
            Box(Modifier.fillMaxSize()) {

                Column(modifier = Modifier.fillMaxSize()) {
                    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {

                        AnimatedVisibility(visible = !isOnNowPlaying || !fullScreenPlayer) {
                            when (navigationRailStyle) {
                                NavigationRailStyle.DEFAULT -> {
                                    NavigationRailDefault(
                                        activeConfig = activeConfig,
                                        changeQueueVisible = { isQueueVisible = it },
                                        rootComponent = rootComponent,
                                    )
                                }

                                NavigationRailStyle.WIDE -> {
                                    WideNavigationRail(
                                        activeConfig = activeConfig,
                                        changeQueueVisible = { isQueueVisible = it },
                                        rootComponent = rootComponent,
                                    )
                                }
                            }
                        }


                        val islands = LocalLayoutMode.current == LayoutMode.ISLANDS
                        val dimens = LocalDimens.current
                        val contentShape = RoundedCornerShape(dimens.surfaceCorner)
                        val bottomPadding = if (currentSong != null) 0.dp else dimens.windowPadding

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(end = dimens.windowPadding, bottom = bottomPadding)
                        ) {

                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1F)
                                        .fillMaxHeight()
                                        .clip(contentShape)
                                        .then(
                                            if (islands) Modifier.border(
                                                0.5.dp,
                                                MaterialTheme.colorScheme.outlineVariant,
                                                contentShape
                                            )
                                            else Modifier
                                        )
                                ) {
                                    ChildStack(
                                        stack = rootComponent.childStack,
                                        animation = stackAnimation(fade()),
                                    ) { child ->

                                        ScreenRouter(
                                            instance = child.instance,
                                            rootComponent = rootComponent,
                                            sharedAnimatedTransitionScope = this@SharedTransitionLayout,
                                            animatedVisibilityScope = this,
                                        )

                                    }
                                }

                                AnimatedVisibility(
                                    visible = isQueueVisible && !isOnNowPlaying
                                ) {
                                    Row(modifier = Modifier.fillMaxHeight()) {
                                        Spacer(Modifier.width(dimens.surfaceGap))

                                        PlaybackQueuePanel(
                                            state = playerState,
                                            onDismiss = { isQueueVisible = false },
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .width(queueWidth)
                                                .clip(contentShape)
                                                .then(
                                                    if (islands) Modifier.border(
                                                        0.5.dp,
                                                        MaterialTheme.colorScheme.outlineVariant,
                                                        contentShape
                                                    )
                                                    else Modifier
                                                ),
                                            containerColor = Color.Transparent
                                        )
                                    }
                                }
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = currentSong != null,
                    ) {
                        MiniPlayerHost(
                            playerViewModel = playerViewModel,
                            isOnNowPlaying = isOnNowPlaying,
                            onNowPlaying = {
                                if (isOnNowPlaying) {
                                    rootComponent.onBack()
                                } else {
                                    isQueueVisible = false
                                    rootComponent.navigateTo(ScreenConfig.NowPlaying)
                                }
                            },
                            onToggleQueue = {
                                isQueueVisible = !isQueueVisible
                            },
                            isQueueVisible = isQueueVisible && !isOnNowPlaying,
                            modifier = Modifier.fillMaxWidth(),
                            sharedTransitionScope = sharedTransitionScope,
                        )
                    }
                }

                SnackBar(
                    currentSong = currentSong,
                    snackbarHostState = snackbarHostState,
                )

                CsvImportProgressOverlay(
                    state = csvImportState,
                    onCancel = { playlistsViewModel.cancelCsvImport() },
                    onDismiss = { playlistsViewModel.dismissCsvImportResult() },
                )
            }
        }
    }
}

@Composable
private fun MiniPlayerHost(
    playerViewModel: PlayerViewModel,
    isOnNowPlaying: Boolean,
    onNowPlaying: () -> Unit,
    onToggleQueue: () -> Unit,
    isQueueVisible: Boolean,
    modifier: Modifier = Modifier,
    sharedTransitionScope: SharedTransitionScope? = null,
) {
    val progressState: PlayerProgressState by playerViewModel.progressState.collectAsState()
    MiniPlayer(
        progressState = progressState,
        isOnNowPlaying = isOnNowPlaying,
        onNowPlaying = onNowPlaying,
        onToggleQueue = onToggleQueue,
        isQueueVisible = isQueueVisible,
        modifier = modifier,
        sharedTransitionScope = sharedTransitionScope,
    )
}


fun Route.toConfig(): ScreenConfig = when (this) {
    Route.Home -> ScreenConfig.Home
    Route.Search -> ScreenConfig.Search
    Route.Library -> ScreenConfig.Library
    Route.Account -> ScreenConfig.Account
    Route.Settings -> ScreenConfig.Settings
    Route.ListenTogether -> ScreenConfig.ListenTogether
    Route.NowPlaying -> ScreenConfig.NowPlaying
    is Route.Album -> ScreenConfig.Album(browseId)
    is Route.Playlist -> ScreenConfig.Playlist(playlistId)
    is Route.Artist -> ScreenConfig.Artist(artistId)
    is Route.YouTubeBrowse -> ScreenConfig.YouTubeBrowse(browseId, params)
}

@Composable
fun ScreenRouter(
    instance: RootComponent.Child,
    rootComponent: RootComponent,
    sharedAnimatedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
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

        is RootComponent.Child.ListenTogether -> {
            ListenTogetherScreen()
        }

        is RootComponent.Child.YouTubeBrowse -> {
            YouTubeBrowseScreenRoute(
                viewModel = instance.component.viewModel,
                onNavigate = navigator,
                onBack = { rootComponent.onBack() },
            )
        }

        is RootComponent.Child.NowPlaying -> {
            NowPlayingScreen(
                viewModel = instance.component.viewModel,
                onNavigate = navigator,
                onBack = { rootComponent.onBack() },
                sharedTransitionScope = sharedAnimatedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
            )
        }
    }
}


// Helper para simplificar las llamadas
@Composable
fun createNavigator(rootComponent: RootComponent): (Route) -> Unit = { route ->
    rootComponent.navigateTo(route.toConfig())
}
