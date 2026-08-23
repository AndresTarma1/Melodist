@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package example.nucleus.ui.components.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.metrolist.innertube.models.MediaInfo
import example.nucleus.models.MediaMetadata
import example.nucleus.navigation.Route
import example.nucleus.player.MpvVideoRenderer
import example.nucleus.shared.generated.resources.*
import example.nucleus.shared.generated.resources.Res
import example.nucleus.ui.components.EqualizerDialog
import example.nucleus.ui.themes.AppShapes
import example.nucleus.ui.themes.LocalMiniPlayerInset
import example.nucleus.ui.themes.expressiveFadeTween
import example.nucleus.ui.themes.expressiveLayoutTween
import example.nucleus.ui.themes.expressiveTween
import example.nucleus.utils.LocalAnimationsEnabled
import example.nucleus.data.repository.VideoQuality
import example.nucleus.data.repository.VideoScale
import example.nucleus.ui.screens.shared.displayName
import example.nucleus.utils.LocalPlayerViewModel
import example.nucleus.utils.LocalUserPreferences
import example.nucleus.viewmodels.PlayerUiState
import example.nucleus.viewmodels.QueueSource
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

enum class NowPlayingTab { LYRICS, QUEUE, INFO }

/**
 * Animación de entrada para la carátula en Now Playing: escala y desvanecimiento suaves.
 */
@Composable
private fun rememberCoverEnter(): Pair<Animatable<Float, *>, Animatable<Float, *>> {
    val animationsEnabled = LocalAnimationsEnabled.current
    val scale = remember { Animatable(if (animationsEnabled) 0.82f else 1f) }
    val alpha = remember { Animatable(if (animationsEnabled) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (animationsEnabled) {
            launch { scale.animateTo(1f, expressiveTween(340)) }
            launch { alpha.animateTo(1f, expressiveTween(280)) }
        }
    }
    return scale to alpha
}

@Composable
private fun Modifier.coverEnter(scaleAnim: Animatable<Float, *>, alphaAnim: Animatable<Float, *>): Modifier =
    this.graphicsLayer {
        scaleX = scaleAnim.value
        scaleY = scaleAnim.value
        alpha = alphaAnim.value
    }

/**
 * Diseño único, moderno y espacioso para la pantalla Now Playing de Melodist.
 */
@Composable
fun NowPlayingLayout(
    state: PlayerUiState,
    song: MediaMetadata,
    onCollapse: () -> Unit,
    onNavigate: ((Route) -> Unit)? = null,
    selectedTab: NowPlayingTab = NowPlayingTab.QUEUE,
    onTabSelected: (NowPlayingTab) -> Unit = {},
    lyrics: String? = null,
    mediaInfo: MediaInfo? = null,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    videoRenderer: MpvVideoRenderer? = null,
) {
    val scope = rememberCoroutineScope()
    var showMenu by remember { mutableStateOf(false) }
    var showEqualizer by remember { mutableStateOf(false) }
    var showVideoSettings by remember { mutableStateOf(false) }
    val preferencesRepo = LocalUserPreferences.current
    val equalizerBands by preferencesRepo.equalizerBands.collectAsState(initial = List(5) { 0f })
    val bottomInset = LocalMiniPlayerInset.current
    val queueCount = state.queue.size
    val videoEnabled by preferencesRepo.videoEnabled.collectAsState(initial = false)
    val playerViewModel = LocalPlayerViewModel.current
    val showVideo by playerViewModel.showVideo.collectAsState(initial = true)

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isCompact = maxWidth < 640.dp || maxHeight < 400.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = bottomInset),
        ) {
            NowPlayingTopBar(
                selectedTab = selectedTab,
                onTabSelected = onTabSelected,
                queueCount = queueCount,
                showMenu = showMenu,
                onMenuToggle = { showMenu = it },
                onOpenEqualizer = { showEqualizer = true },
                onOpenVideoSettings = { showVideoSettings = true },
                compact = isCompact,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 8.dp),
            )

            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                ) {
                    SpaciousNowPlayingBody(
                        state = state,
                        song = song,
                        lyrics = lyrics,
                        mediaInfo = mediaInfo,
                        selectedTab = selectedTab,
                        onNavigate = onNavigate,
                        onCollapse = onCollapse,
                        compact = isCompact,
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope,
                        videoRenderer = videoRenderer,
                        videoEnabled = videoEnabled,
                        showVideo = showVideo,
                        onToggleVideo = { playerViewModel.setShowVideo(!showVideo) },
                    )
                }
                AnimatedVisibility(
                    visible = showVideoSettings,
                    enter = expandHorizontally(),
                    exit = shrinkHorizontally(),
                ) {
                    VideoSettingsPanel(
                        onDismiss = { showVideoSettings = false },
                        modifier = Modifier.fillMaxHeight().width(340.dp),
                    )
                }
            }
        }
    }

    if (showEqualizer) {
        EqualizerDialog(
            bands = equalizerBands,
            onBandsChange = { scope.launch { preferencesRepo.setEqualizerBands(it) } },
            onDismiss = { showEqualizer = false },
        )
    }
}

@Composable
private fun VideoSettingsPanel(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val prefs = LocalUserPreferences.current
    val scope = rememberCoroutineScope()
    val videoEnabled by prefs.videoEnabled.collectAsState(false)
    val videoQuality by prefs.videoQuality.collectAsState(VideoQuality.AUTO)
    val videoFullscreen by prefs.videoFullscreen.collectAsState(false)
    val videoScale by prefs.videoScale.collectAsState(VideoScale.CROP)
    var showQuality by remember { mutableStateOf(false) }
    var showScale by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.video_settings),
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = null)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            ListItem(
                headlineContent = { Text(stringResource(Res.string.video_mode)) },
                supportingContent = {
                    Text(
                        stringResource(Res.string.video_mode_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
                trailingContent = {
                    Switch(
                        checked = videoEnabled,
                        onCheckedChange = { scope.launch { prefs.setVideoEnabled(it) } },
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
            ListItem(
                headlineContent = { Text(stringResource(Res.string.video_quality)) },
                supportingContent = { Text(videoQuality.displayName(), style = MaterialTheme.typography.bodySmall) },
                trailingContent = {
                    Box {
                        TextButton(onClick = { showQuality = true }) {
                            Text(videoQuality.displayName())
                        }
                        DropdownMenu(
                            expanded = showQuality,
                            onDismissRequest = { showQuality = false },
                        ) {
                            VideoQuality.entries.forEach { q ->
                                DropdownMenuItem(
                                    text = { Text(q.displayName()) },
                                    onClick = {
                                        scope.launch { prefs.setVideoQuality(q) }
                                        showQuality = false
                                    },
                                )
                            }
                        }
                    }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
            ListItem(
                headlineContent = { Text(stringResource(Res.string.video_scale)) },
                supportingContent = { Text(videoScale.displayName(), style = MaterialTheme.typography.bodySmall) },
                trailingContent = {
                    Box {
                        TextButton(onClick = { showScale = true }) {
                            Text(videoScale.displayName())
                        }
                        DropdownMenu(
                            expanded = showScale,
                            onDismissRequest = { showScale = false },
                        ) {
                            VideoScale.entries.forEach { s ->
                                DropdownMenuItem(
                                    text = { Text(s.displayName()) },
                                    onClick = {
                                        scope.launch { prefs.setVideoScale(s) }
                                        showScale = false
                                    },
                                )
                            }
                        }
                    }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
            ListItem(
                headlineContent = { Text(stringResource(Res.string.video_fullscreen)) },
                supportingContent = {
                    Text(
                        stringResource(Res.string.video_fullscreen_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
                trailingContent = {
                    Switch(
                        checked = videoFullscreen,
                        onCheckedChange = { scope.launch { prefs.setVideoFullscreen(it) } },
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
            Text(
                text = stringResource(Res.string.video_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}

/**
 * Barra superior con selector de pestañas centrado y acciones rápidas.
 * El retroceso de navegación se gestiona globalmente desde la TitleBar.
 */
@Composable
private fun NowPlayingTopBar(
    selectedTab: NowPlayingTab,
    onTabSelected: (NowPlayingTab) -> Unit,
    queueCount: Int,
    showMenu: Boolean,
    onMenuToggle: (Boolean) -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenVideoSettings: () -> Unit = {},
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!compact) {
            Spacer(Modifier.width(88.dp))
        }

        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            NowPlayingIconTabs(
                selectedTab = selectedTab,
                onTabSelected = onTabSelected,
                queueCount = queueCount,
                showLabels = !compact,
            )
        }

        NowPlayingTopActions(
            showMenu = showMenu,
            onMenuToggle = onMenuToggle,
            onOpenEqualizer = onOpenEqualizer,
            onOpenVideoSettings = onOpenVideoSettings,
        )
    }
}

/**
 * Píldoras de navegación entre pestañas (Letras, Cola, Información).
 */
@Composable
private fun NowPlayingIconTabs(
    selectedTab: NowPlayingTab,
    onTabSelected: (NowPlayingTab) -> Unit,
    queueCount: Int,
    showLabels: Boolean,
    modifier: Modifier = Modifier,
) {
    val lyricsLabel = stringResource(Res.string.tab_lyrics)
    val queueLabel = stringResource(Res.string.tab_queue)
    val infoLabel = stringResource(Res.string.tab_info)
    val colorScheme = MaterialTheme.colorScheme

    Surface(
        shape = AppShapes.extraLarge,
        color = colorScheme.surfaceContainer.copy(alpha = 0.5f),
        tonalElevation = 0.dp,
        modifier = modifier.border(
            width = 0.5.dp,
            color = colorScheme.outlineVariant.copy(alpha = 0.3f),
            shape = AppShapes.extraLarge,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NowPlayingTab.entries.forEach { tab ->
                val selected = tab == selectedTab
                val label = when (tab) {
                    NowPlayingTab.LYRICS -> lyricsLabel
                    NowPlayingTab.QUEUE -> if (queueCount > 0) "$queueLabel · $queueCount" else queueLabel
                    NowPlayingTab.INFO -> infoLabel
                }
                val contentColor = if (selected) {
                    colorScheme.onPrimaryContainer
                } else {
                    colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                }
                val containerColor = if (selected) {
                    colorScheme.primaryContainer.copy(alpha = 0.65f)
                } else {
                    Color.Transparent
                }

                Surface(
                    onClick = { onTabSelected(tab) },
                    shape = AppShapes.large,
                    color = containerColor,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    modifier = Modifier
                        .height(36.dp)
                        .pointerHoverIcon(PointerIcon.Hand),
                ) {
                    Row(
                        modifier = Modifier.padding(
                            horizontal = if (showLabels) 14.dp else 10.dp,
                            vertical = 6.dp,
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = tabIcon(tab),
                            contentDescription = if (showLabels) null else label,
                            modifier = Modifier.size(18.dp),
                            tint = contentColor,
                        )
                        if (showLabels) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelLargeEmphasized,
                                color = contentColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Contenedor transparente para alojar el contenido de la pestaña activa derivando del AppBackground.
 */
@Composable
private fun TransparentPanel(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        content()
    }
}

/**
 * Cuerpo principal espacioso: en escritorio divide la pantalla con proporción aireada,
 * y en pantallas muy estrechas (< 640dp) organiza la carátula y el contenido en una columna fluida.
 */
@Composable
private fun SpaciousNowPlayingBody(
    state: PlayerUiState,
    song: MediaMetadata,
    lyrics: String?,
    mediaInfo: MediaInfo?,
    selectedTab: NowPlayingTab,
    onNavigate: ((Route) -> Unit)?,
    onCollapse: () -> Unit,
    compact: Boolean,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    videoRenderer: MpvVideoRenderer? = null,
    videoEnabled: Boolean = false,
    showVideo: Boolean = true,
    onToggleVideo: () -> Unit = {},
) {
    val (coverScale, coverAlpha) = rememberCoverEnter()

    if (compact) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CoverArt(
                        url = song.thumbnailUrl,
                        title = song.title,
                        modifier = Modifier
                            .size(104.dp)
                            .heroCoverElement(song.id, sharedTransitionScope, animatedVisibilityScope)
                            .coverEnter(coverScale, coverAlpha),
                    )
                    NowPlayingSongDetails(
                        state = state,
                        song = song,
                        textAlign = TextAlign.Start,
                        onNavigate = onNavigate,
                        onCollapse = onCollapse,
                        compact = true,
                        modifier = Modifier.weight(1f),
                    )
                }

                TransparentPanel(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    Box(Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 4.dp)) {
                        NowPlayingTabContent(
                            tab = selectedTab,
                            song = song,
                            state = state,
                            lyrics = lyrics,
                            lyricsTextStyle = MaterialTheme.typography.bodyLarge,
                            onNavigate = onNavigate,
                            mediaInfo = mediaInfo,
                        )
                    }
                }
            }
        } else {
            // Diseño espacioso de escritorio: carátula e info a la izquierda, panel transparente a la derecha
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Columna izquierda: Carátula generosa + Información del tema + Acciones rápidas
                Column(
                    modifier = Modifier
                        .weight(0.42f)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    val coverDimension = 380.dp

                    VideoOrCoverArt(
                        state = state,
                        song = song,
                        coverDimension = coverDimension,
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope,
                        coverScale = coverScale,
                        coverAlpha = coverAlpha,
                        videoRenderer = videoRenderer,
                        videoEnabled = videoEnabled,
                        showVideo = showVideo,
                        onToggleVideo = onToggleVideo,
                    )

                    Spacer(Modifier.height(20.dp))

                    NowPlayingSongDetails(
                        state = state,
                        song = song,
                        textAlign = TextAlign.Center,
                        onNavigate = onNavigate,
                        onCollapse = onCollapse,
                        compact = false,
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 440.dp)
                            .padding(horizontal = 8.dp),
                    )
                }

                // Columna derecha: Panel transparente de pestañas (Cola exclusiva / Letras sincronizadas / Info)
                TransparentPanel(
                    modifier = Modifier
                        .weight(0.58f)
                        .fillMaxHeight(),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                    ) {
                        NowPlayingTabContent(
                            tab = selectedTab,
                            song = song,
                            state = state,
                            lyrics = lyrics,
                            lyricsTextStyle = MaterialTheme.typography.headlineSmall,
                            onNavigate = onNavigate,
                            mediaInfo = mediaInfo,
                        )
                    }
                }
            }
        }
}

/**
 * Carátula de la pista o, si el modo video está activo y la pista trae video, la superficie
 * de video con un botón para alternar entre video y carátula.
 */
@Composable
private fun VideoOrCoverArt(
    state: PlayerUiState,
    song: MediaMetadata,
    coverDimension: Dp,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    coverScale: Animatable<Float, *>,
    coverAlpha: Animatable<Float, *>,
    videoRenderer: MpvVideoRenderer?,
    videoEnabled: Boolean,
    showVideo: Boolean,
    onToggleVideo: () -> Unit,
) {
    val videoSize = state.videoSize
    val videoAvailable = videoEnabled && videoRenderer != null && videoSize != null

    Box(
        modifier = Modifier.size(coverDimension),
        contentAlignment = Alignment.Center,
    ) {
        if (videoAvailable && showVideo) {
            VideoSurface(
                renderer = videoRenderer,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(videoSize!!.first.toFloat() / videoSize.second.toFloat())
                    .clip(AppShapes.extraLarge),
            )
        } else {
            CoverArt(
                url = song.thumbnailUrl,
                title = song.title,
                modifier = Modifier
                    .size(coverDimension)
                    .heroCoverElement(song.id, sharedTransitionScope, animatedVisibilityScope)
                    .coverEnter(coverScale, coverAlpha),
            )
        }

        if (videoAvailable) {
            Surface(
                onClick = onToggleVideo,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .size(34.dp)
                    .pointerHoverIcon(PointerIcon.Hand),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (showVideo) Icons.Rounded.VideocamOff else Icons.Rounded.Videocam,
                        contentDescription = if (showVideo) {
                            stringResource(Res.string.video_hide)
                        } else {
                            stringResource(Res.string.video_show)
                        },
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

/**
 * Detalles y metadatos de la canción con tipografía destacada y enlaces a artistas y álbum.
 */
@Composable
private fun NowPlayingSongDetails(
    state: PlayerUiState,
    song: MediaMetadata,
    textAlign: TextAlign,
    onNavigate: ((Route) -> Unit)?,
    onCollapse: (() -> Unit)?,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = when (textAlign) {
            TextAlign.Start -> Alignment.Start
            else -> Alignment.CenterHorizontally
        },
        modifier = modifier,
    ) {
        // Chip de origen / procedencia
        state.queueSource?.let { source ->
            val label = when (source) {
                is QueueSource.Album -> stringResource(Res.string.from_album, source.title)
                is QueueSource.Playlist -> stringResource(Res.string.from_playlist, source.title)
                is QueueSource.Single -> stringResource(Res.string.song_radio)
                QueueSource.Custom -> stringResource(Res.string.custom_queue)
            }
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                modifier = Modifier.padding(bottom = if (compact) 8.dp else 12.dp),
            ) {
                Text(
                    text = label,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 1,
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                        .widthIn(max = if (compact) 200.dp else 320.dp)
                        .basicMarquee(),
                )
            }
        }

        // Título de la pista
        Text(
            text = song.title,
            style = if (compact) MaterialTheme.typography.titleLargeEmphasized else MaterialTheme.typography.headlineMediumEmphasized,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = if (compact) 1 else 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = textAlign,
            modifier = Modifier
                .fillMaxWidth()
                .basicMarquee(),
        )

        Spacer(Modifier.height(if (compact) 4.dp else 8.dp))

        // Artistas interactivos
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (textAlign == TextAlign.Start) Arrangement.Start else Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            song.artists.forEachIndexed { i, artist ->
                val hasId = artist.id != null
                Text(
                    text = artist.name,
                    style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (hasId) {
                        Modifier
                            .clip(AppShapes.small)
                            .clickable {
                                onCollapse?.invoke()
                                onNavigate?.invoke(Route.Artist(artist.id!!))
                            }
                            .pointerHoverIcon(PointerIcon.Hand)
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    } else {
                        Modifier.padding(horizontal = 4.dp)
                    },
                )
                if (i < song.artists.size - 1) {
                    Text(
                        text = " · ",
                        style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.padding(horizontal = 2.dp),
                    )
                }
            }
        }

        // Álbum si está presente
        song.album?.let { album ->
            if (!compact) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = album.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = textAlign,
                    modifier = Modifier
                        .clip(AppShapes.small)
                        .clickable {
                            onCollapse?.invoke()
                            onNavigate?.invoke(Route.Album(album.id))
                        }
                        .pointerHoverIcon(PointerIcon.Hand)
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }
        }
    }
}

private fun tabIcon(tab: NowPlayingTab) = when (tab) {
    NowPlayingTab.LYRICS -> Icons.Rounded.Lyrics
    NowPlayingTab.QUEUE -> Icons.AutoMirrored.Filled.QueueMusic
    NowPlayingTab.INFO -> Icons.Rounded.Info
}

/**
 * Contenido animado de cada pestaña dentro de Now Playing.
 */
@Composable
private fun NowPlayingTabContent(
    tab: NowPlayingTab,
    song: MediaMetadata,
    state: PlayerUiState,
    lyrics: String?,
    mediaInfo: MediaInfo?,
    lyricsTextStyle: androidx.compose.ui.text.TextStyle,
    onNavigate: ((Route) -> Unit)?,
) {
    val animationsEnabled = LocalAnimationsEnabled.current
    AnimatedContent(
        targetState = tab,
        transitionSpec = {
            if (animationsEnabled) {
                (fadeIn(expressiveFadeTween()) +
                    slideInVertically(animationSpec = expressiveLayoutTween()) { it / 24 })
                    .togetherWith(fadeOut(expressiveTween(140)))
            } else {
                fadeIn(expressiveTween(0)) togetherWith fadeOut(expressiveTween(0))
            }
        },
        label = "now_playing_tab_content",
        modifier = Modifier.fillMaxSize(),
    ) { targetTab ->
        when (targetTab) {
            NowPlayingTab.LYRICS -> LyricsContent(
                lyrics = lyrics,
                textAlign = TextAlign.Start,
                style = lyricsTextStyle,
            )
            NowPlayingTab.QUEUE -> NowPlayingQueuePanel(
                state = state,
                modifier = Modifier.fillMaxSize(),
                bottomInset = 0.dp,
            )
            NowPlayingTab.INFO -> SongInfoContent(
                song = song,
                state = state,
                mediaInfo = mediaInfo,
                onNavigate = onNavigate,
            )
        }
    }
}