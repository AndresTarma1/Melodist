package example.nucleus.ui.components.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import example.nucleus.navigation.Route
import example.nucleus.models.MediaMetadata
import example.nucleus.ui.components.EqualizerDialog
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
            launch { scale.animateTo(1f, tween(320, easing = FastOutSlowInEasing)) }
            launch { alpha.animateTo(1f, tween(260, easing = FastOutSlowInEasing)) }
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

    var overlaySize by remember { mutableStateOf(IntSize.Zero) }
    val overlayWidthDp = with(LocalDensity.current) { overlaySize.width.toDp() }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
    ) {
        val isCompact = maxWidth < 900.dp || maxHeight < 560.dp

        Box(modifier = Modifier.fillMaxSize()) {
            if (isCompact) {
                CompactNowPlayingLayout(
                    song = song,
                    state = state,
                    lyrics = lyrics,
                    mediaInfo = mediaInfo,
                    selectedTab = selectedTab,
                    onTabSelected = onTabSelected,
                    onNavigate = onNavigate,
                    onCollapse = onCollapse,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                    reservedEndPadding = overlayWidthDp + 12.dp, // margen extra de respiro
                )
            } else {
                    ExpandedNowPlayingLayout(
                        song = song,
                        state = state,
                        lyrics = lyrics,
                        mediaInfo = mediaInfo,
                        selectedTab = selectedTab,
                        onTabSelected = onTabSelected,
                        onNavigate = onNavigate,
                        onCollapse = onCollapse,
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope,
                    )
                }
            }

        TopActionOverlay(
            showMenu = showMenu,
            onMenuToggle = { showMenu = it },
            onOpenEqualizer = { showEqualizer = true },
            modifier = Modifier.onSizeChanged { overlaySize = it }, // <- medimos el tamaño real
        )
//        }
    }

    if (showEqualizer) {
        EqualizerDialog(
            bands = equalizerBands,
            onBandsChange = { scope.launch { preferencesRepo.setEqualizerBands(it) } },
            onDismiss = { showEqualizer = false }
        )
    }
}

@Composable
private fun ExpandedNowPlayingLayout(
    song: MediaMetadata,
    state: PlayerUiState,
    lyrics: String?,
    mediaInfo: MediaInfo?,
    selectedTab: NowPlayingTab,
    onTabSelected: (NowPlayingTab) -> Unit,
    onNavigate: ((Route) -> Unit)?,
    onCollapse: () -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    val (coverScale, coverAlpha) = rememberCoverEnter()

    Row(
        modifier = Modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        Column(
            modifier = Modifier.weight(0.42f).fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CoverArt(
                url = song.thumbnailUrl,
                title = song.title,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .sizeIn(maxHeight = 380.dp, maxWidth = 380.dp)
                    .heroCoverElement(song.id, sharedTransitionScope, animatedVisibilityScope)
                    .coverEnter(coverScale, coverAlpha),
            )
            Spacer(Modifier.height(18.dp))
            SongHeader(
                state = state,
                song = song,
                textAlign = TextAlign.Center,
                onNavigate = onNavigate,
                onCollapse = onCollapse,
                compact = false
            )
        }

        Column(modifier = Modifier.weight(0.58f).fillMaxHeight()) {
            NowPlayingTabRow(
                selectedTab = selectedTab,
                onTabSelected = onTabSelected,
                queueCount = state.queue.size,
                modifier = Modifier.widthIn(400.dp)
            )
            Box(modifier = Modifier.weight(1f)) {
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
}

@Composable
private fun CompactNowPlayingLayout(
    song: MediaMetadata,
    state: PlayerUiState,
    lyrics: String?,
    mediaInfo: MediaInfo?,
    selectedTab: NowPlayingTab,
    onTabSelected: (NowPlayingTab) -> Unit,
    onNavigate: ((Route) -> Unit)?,
    onCollapse: () -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    reservedEndPadding: Dp = 0.dp,
) {
    val (coverScale, coverAlpha) = rememberCoverEnter()

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = reservedEndPadding),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
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
                modifier = Modifier.weight(1f)
            )

            NowPlayingTabRow(
                selectedTab = selectedTab,
                onTabSelected = onTabSelected,
                isCompact = true,
                queueCount = state.queue.size,
            )
        }

        Box(modifier = Modifier.weight(1f)) {
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
private fun NowPlayingTabRow(
    selectedTab: NowPlayingTab,
    onTabSelected: (NowPlayingTab) -> Unit,
    queueCount: Int,
    isCompact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val lyricsLabel = stringResource(Res.string.tab_lyrics)
    val queueLabel = stringResource(Res.string.tab_queue) + if (queueCount > 0) " ($queueCount)" else ""
    val infoLabel = stringResource(Res.string.tab_info)
    val tabs = NowPlayingTab.entries


    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        tabs.forEachIndexed { index, tab ->
            val selected = tab == selectedTab
            val label = when (tab) {
                NowPlayingTab.LYRICS -> lyricsLabel
                NowPlayingTab.QUEUE -> queueLabel
                NowPlayingTab.INFO -> infoLabel
            }
            SegmentedButton(
                selected = selected,
                onClick = { onTabSelected(tab) },
                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                shape = SegmentedButtonDefaults.itemShape(index = index, count = tabs.size),
                icon = { if(!isCompact) Icon(tabIcon(tab), contentDescription = null, modifier = Modifier.size(18.dp)) },
                label = {
                    if(isCompact){
                        Icon(tabIcon(tab), contentDescription = null, modifier = Modifier.size(18.dp))
                    }else {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
            )
        }
    }
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
                (fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 8 })
                    .togetherWith(fadeOut(tween(140)))
            } else {
                androidx.compose.animation.EnterTransition.None
                    .togetherWith(androidx.compose.animation.ExitTransition.None)
            }
        },
        label = "now_playing_tab_content"
    ) { targetTab ->
        when (targetTab) {
            NowPlayingTab.LYRICS -> LyricsContent(lyrics = lyrics, textAlign = TextAlign.Center, style = lyricsTextStyle)
            NowPlayingTab.QUEUE -> PlaybackQueuePanel(
                state = state,
                onDismiss = {},
                modifier = Modifier.fillMaxSize(),
                containerColor = Color.Transparent,
                showCloseButton = false,
            )
            NowPlayingTab.INFO -> SongInfoContent(song = song, state = state, mediaInfo = mediaInfo, onNavigate = onNavigate)
        }
    }
}
