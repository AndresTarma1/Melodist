@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package example.nucleus.ui.components.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import example.nucleus.navigation.Route
import example.nucleus.models.MediaMetadata
import example.nucleus.ui.components.EqualizerDialog
import example.nucleus.ui.themes.AppShapes
import example.nucleus.ui.themes.expressiveFadeDuration
import example.nucleus.ui.themes.expressiveFadeTween
import example.nucleus.ui.themes.expressiveLayoutTween
import example.nucleus.ui.themes.expressiveTween
import example.nucleus.ui.themes.LocalMiniPlayerInset
import example.nucleus.utils.LocalUserPreferences
import example.nucleus.utils.LocalAnimationsEnabled
import example.nucleus.viewmodels.PlayerUiState
import com.metrolist.innertube.models.MediaInfo
import example.nucleus.shared.generated.resources.Res
import example.nucleus.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import kotlinx.coroutines.launch

enum class NowPlayingTab { LYRICS, QUEUE, INFO }

/**
 * Entrada simple de la carátula en NowPlaying: escala+fade desde el tamaño del thumbnail.
 * Sustituye a la transición hero compartida (que tiembla con el runtime Tao).
 */
@Composable
private fun rememberCoverEnter(): Pair<Animatable<Float, *>, Animatable<Float, *>> {
    val animationsEnabled = LocalAnimationsEnabled.current
    val scale = remember { Animatable(if (animationsEnabled) 0.55f else 1f) }
    val alpha = remember { Animatable(if (animationsEnabled) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (animationsEnabled) {
            launch { scale.animateTo(1f, expressiveTween(320)) }
            launch { alpha.animateTo(1f, expressiveTween(260)) }
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
) {
    val scope = rememberCoroutineScope()
    var showMenu by remember { mutableStateOf(false) }
    var showEqualizer by remember { mutableStateOf(false) }
    val preferencesRepo = LocalUserPreferences.current
    val equalizerBands by preferencesRepo.equalizerBands.collectAsState(initial = List(5) { 0f })

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isCompact = maxWidth < 900.dp || maxHeight < 560.dp
        val miniInset = LocalMiniPlayerInset.current

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = miniInset),
        ) {
            // Top chrome Apple Music-like: tabs centrados, acciones a la derecha, sin fondo
            NowPlayingTopBar(
                selectedTab = selectedTab,
                onTabSelected = onTabSelected,
                queueCount = state.queue.size,
                showMenu = showMenu,
                onMenuToggle = { showMenu = it },
                onOpenEqualizer = { showEqualizer = true },
                compact = isCompact,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 12.dp, top = 10.dp, bottom = 4.dp),
            )

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (isCompact) {
                    CompactNowPlayingLayout(
                        song = song,
                        state = state,
                        lyrics = lyrics,
                        mediaInfo = mediaInfo,
                        selectedTab = selectedTab,
                        onNavigate = onNavigate,
                        onCollapse = onCollapse,
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope,
                    )
                } else {
                    ExpandedNowPlayingLayout(
                        song = song,
                        state = state,
                        lyrics = lyrics,
                        mediaInfo = mediaInfo,
                        selectedTab = selectedTab,
                        onNavigate = onNavigate,
                        onCollapse = onCollapse,
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope,
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

/**
 * Barra superior transparente: [spacer] · icon tabs · velocidad / más
 */
@Composable
private fun NowPlayingTopBar(
    selectedTab: NowPlayingTab,
    onTabSelected: (NowPlayingTab) -> Unit,
    queueCount: Int,
    showMenu: Boolean,
    onMenuToggle: (Boolean) -> Unit,
    onOpenEqualizer: () -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Equilibrio visual a la izquierda (espejo del cluster de acciones)
        Spacer(Modifier.width(if (compact) 8.dp else 120.dp))

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
        )
    }
}

/**
 * Tabs tipo Apple Music: píldoras suaves / iconos flotantes, sin segmented opaco.
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

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                colorScheme.onSecondaryContainer
            } else {
                colorScheme.onSurfaceVariant.copy(alpha = 0.88f)
            }
            val containerColor = if (selected) {
                colorScheme.secondaryContainer.copy(alpha = 0.55f)
            } else {
                Color.Transparent
            }

            Surface(
                onClick = { onTabSelected(tab) },
                shape = if (showLabels) AppShapes.large else CircleShape,
                color = containerColor,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                modifier = Modifier
                    .height(40.dp)
                    .pointerHoverIcon(PointerIcon.Hand),
            ) {
                Row(
                    modifier = Modifier.padding(
                        horizontal = if (showLabels) 14.dp else 11.dp,
                        vertical = 8.dp,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = tabIcon(tab),
                        contentDescription = if (showLabels) null else label,
                        modifier = Modifier.size(20.dp),
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

@Composable
private fun ExpandedNowPlayingLayout(
    song: MediaMetadata,
    state: PlayerUiState,
    lyrics: String?,
    mediaInfo: MediaInfo?,
    selectedTab: NowPlayingTab,
    onNavigate: ((Route) -> Unit)?,
    onCollapse: () -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    val (coverScale, coverAlpha) = rememberCoverEnter()

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 48.dp, end = 40.dp, top = 12.dp, bottom = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(48.dp),
    ) {
        // Hero transparente: cover + meta, sin tarjeta envolvente
        Column(
            modifier = Modifier
                .weight(0.40f)
                .fillMaxHeight()
                .padding(end = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(0.dp, Alignment.CenterVertically),
        ) {
            CoverArt(
                url = song.thumbnailUrl,
                title = song.title,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .sizeIn(maxHeight = 440.dp, maxWidth = 440.dp)
                    .fillMaxWidth(0.90f)
                    .heroCoverElement(song.id, sharedTransitionScope, animatedVisibilityScope)
                    .coverEnter(coverScale, coverAlpha),
            )
            Spacer(Modifier.height(28.dp))
            SongHeader(
                state = state,
                song = song,
                textAlign = TextAlign.Center,
                onNavigate = onNavigate,
                onCollapse = onCollapse,
                compact = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
            )
        }

        // Contenido a pantalla abierta (letras / cola / info) — sin panel opaco
        Box(
            modifier = Modifier
                .weight(0.60f)
                .fillMaxHeight()
                .padding(start = 8.dp, top = 8.dp, bottom = 8.dp),
        ) {
            NowPlayingTabContent(
                tab = selectedTab,
                song = song,
                state = state,
                lyrics = lyrics,
                mediaInfo = mediaInfo,
                lyricsTextStyle = MaterialTheme.typography.headlineSmall,
                onNavigate = onNavigate,
            )
        }
    }
}

@Composable
private fun CompactNowPlayingLayout(
    song: MediaMetadata,
    state: PlayerUiState,
    lyrics: String?,
    mediaInfo: MediaInfo?,
    selectedTab: NowPlayingTab,
    onNavigate: ((Route) -> Unit)?,
    onCollapse: () -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    val (coverScale, coverAlpha) = rememberCoverEnter()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 22.dp, end = 22.dp, top = 8.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CoverArt(
                url = song.thumbnailUrl,
                title = song.title,
                modifier = Modifier
                    .size(96.dp)
                    .heroCoverElement(song.id, sharedTransitionScope, animatedVisibilityScope)
                    .coverEnter(coverScale, coverAlpha),
            )
            SongHeader(
                state = state,
                song = song,
                textAlign = TextAlign.Start,
                onNavigate = onNavigate,
                onCollapse = onCollapse,
                compact = true,
                modifier = Modifier.weight(1f),
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
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

private fun tabIcon(tab: NowPlayingTab) = when (tab) {
    NowPlayingTab.LYRICS -> Icons.Rounded.Lyrics
    NowPlayingTab.QUEUE -> Icons.AutoMirrored.Filled.QueueMusic
    NowPlayingTab.INFO -> Icons.Rounded.Info
}

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
                    slideInVertically(animationSpec = expressiveLayoutTween()) { it / 28 })
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
                textAlign = TextAlign.Center,
                style = lyricsTextStyle,
            )
            NowPlayingTab.QUEUE -> PlaybackQueuePanel(
                state = state,
                onDismiss = {},
                modifier = Modifier.fillMaxSize(),
                containerColor = Color.Transparent,
                showCloseButton = false,
                bottomInset = LocalMiniPlayerInset.current,
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
