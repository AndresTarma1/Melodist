package example.nucleus.ui.components.player

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import example.nucleus.data.repository.VideoQuality
import example.nucleus.data.repository.VideoScale
import example.nucleus.player.MpvVideoRenderer
import example.nucleus.shared.generated.resources.*
import example.nucleus.ui.components.MiniPlayer
import example.nucleus.ui.screens.shared.displayName
import example.nucleus.utils.LocalAppFullscreen
import example.nucleus.utils.LocalPlayerViewModel
import example.nucleus.utils.LocalUserPreferences
import example.nucleus.viewmodels.PlayerUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration.Companion.milliseconds

/**
 * Overlay de video a pantalla completa estilo YouTube.
 * - Clic simple = play/pause (YT)
 * - Doble clic = fullscreen
 * - Rueda = volumen
 * - Cola y ajustes empujan el video (no superponen)
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun VideoFullscreenOverlay(
    renderer: MpvVideoRenderer?,
    state: PlayerUiState,
    onHideVideo: () -> Unit,
    onCloseNowPlaying: () -> Unit = {},
) {
    var controlsVisible by remember { mutableStateOf(true) }
    var lastMove by remember { mutableStateOf(0L) }
    var queueVisible by remember { mutableStateOf(false) }
    var settingsVisible by remember { mutableStateOf(false) }

    val prefs = LocalUserPreferences.current
    val playerViewModel = LocalPlayerViewModel.current
    val scope = rememberCoroutineScope()
    val appFullscreenState = LocalAppFullscreen.current
    val isAppFullscreen by appFullscreenState

    val videoScale by prefs.videoScale.collectAsState(VideoScale.CROP)
    val videoEnabled by prefs.videoEnabled.collectAsState(false)
    val videoQuality by prefs.videoQuality.collectAsState(VideoQuality.AUTO)
    val videoAutoFullscreen by prefs.videoFullscreen.collectAsState(false)
    val volume by playerViewModel.volume.collectAsState()

    val contentScale = if (videoScale == VideoScale.CROP) ContentScale.Crop else ContentScale.Fit

    LaunchedEffect(lastMove) {
        controlsVisible = true
        delay(2600.milliseconds)
        controlsVisible = false
    }

    // Exclusividad: solo una de las dos puede estar abierta
    LaunchedEffect(queueVisible) { if (queueVisible) settingsVisible = false }
    LaunchedEffect(settingsVisible) { if (settingsVisible) queueVisible = false }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPointerEvent(PointerEventType.Move) { lastMove = System.currentTimeMillis() }
            .onPointerEvent(PointerEventType.Scroll) { event ->
                val delta = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                if (delta != 0f) {
                    val step = 5
                    val newVol = (volume + if (delta < 0) step else -step).coerceIn(0, 100)
                    playerViewModel.setVolume(newVol)
                    lastMove = System.currentTimeMillis()
                }
            },
    ) {
        Row(Modifier.fillMaxSize()) {
            // Área de video
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .pointerInput(playerViewModel, appFullscreenState) {
                        detectTapGestures(
                            onTap = {
                                playerViewModel.togglePlayPause()
                                lastMove = System.currentTimeMillis()
                            },
                            onDoubleTap = {
                                appFullscreenState.value = !appFullscreenState.value
                                lastMove = System.currentTimeMillis()
                            }
                        )
                    },
            ) {
                VideoSurface(
                    renderer = renderer,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = contentScale,
                )
            }

            // Panel de cola
            AnimatedVisibility(
                visible = queueVisible,
                modifier = Modifier.fillMaxHeight(),
                enter = expandHorizontally(),
                exit = shrinkHorizontally(),
            ) {
                PlaybackQueuePanel(
                    state = state,
                    onDismiss = { queueVisible = false },
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                    showCloseButton = true,
                    bottomInset = 0.dp,
                    modifier = Modifier.fillMaxHeight().width(380.dp),
                )
            }

            // Panel de ajustes in-player (empuja)
            AnimatedVisibility(
                visible = settingsVisible,
                modifier = Modifier.fillMaxHeight(),
                enter = expandHorizontally(),
                exit = shrinkHorizontally(),
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                    modifier = Modifier.fillMaxHeight().width(360.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = stringResource(Res.string.video_settings),
                            style = MaterialTheme.typography.titleMediumEmphasized,
                        )
                        // Video on/off
                        ListItem(
                            headlineContent = { Text(stringResource(Res.string.video_mode)) },
                            supportingContent = {
                                Text(
                                    stringResource(Res.string.video_mode_subtitle),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            },
                            trailingContent = {
                                Switch(
                                    checked = videoEnabled,
                                    onCheckedChange = { scope.launch { prefs.setVideoEnabled(it) } }
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        // Calidad
                        var showQuality by remember { mutableStateOf(false) }
                        ListItem(
                            headlineContent = { Text(stringResource(Res.string.video_quality)) },
                            supportingContent = {
                                Text(videoQuality.displayName(),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            },
                            trailingContent = {
                                TextButton(onClick = { showQuality = true }) {
                                    Text(videoQuality.displayName())

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
                                                }
                                            )
                                        }

                                    }
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                        )

                        // Ajuste Fit/Crop
                        var showScale by remember { mutableStateOf(false) }
                        ListItem(
                            headlineContent = { Text(stringResource(Res.string.video_scale)) },
                            supportingContent = {
                                Text(
                                    videoScale.displayName(),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            },
                            trailingContent = {
                                TextButton(onClick = { showScale = true }) {
                                    Text(videoScale.displayName())
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
                                                }
                                            )
                                        }

                                    }
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        ListItem(
                            headlineContent = { Text(stringResource(Res.string.video_fullscreen)) },
                            supportingContent = {
                                Text(
                                    stringResource(Res.string.video_fullscreen_subtitle),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            },
                            trailingContent = {
                                Switch(
                                    checked = videoAutoFullscreen,
                                    onCheckedChange = { scope.launch { prefs.setVideoFullscreen(it) } }
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                        Text(
                            text = "Click: play/pausa · Doble click: pantalla completa · Rueda: volumen",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = controlsVisible,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 12.dp, end = if (queueVisible || settingsVisible) 376.dp else 12.dp),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    onClick = {
                        settingsVisible = !settingsVisible
                        if (settingsVisible) queueVisible = false
                        lastMove = System.currentTimeMillis()
                    },
                    shape = CircleShape,
                    color = if (settingsVisible) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f) else MaterialTheme.colorScheme.scrim.copy(
                        alpha = 0.5f
                    ),
                    modifier = Modifier.size(40.dp).pointerHoverIcon(PointerIcon.Hand),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = "Ajustes de video",
                            tint = if (settingsVisible) MaterialTheme.colorScheme.onPrimaryContainer else Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Surface(
                    onClick = {
                        queueVisible = !queueVisible
                        if (queueVisible) settingsVisible = false
                        lastMove = System.currentTimeMillis()
                    },
                    shape = CircleShape,
                    color = if (queueVisible) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f) else MaterialTheme.colorScheme.scrim.copy(
                        alpha = 0.5f
                    ),
                    modifier = Modifier.size(40.dp).pointerHoverIcon(PointerIcon.Hand),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                            contentDescription = "Cola",
                            tint = if (queueVisible) MaterialTheme.colorScheme.onPrimaryContainer else Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Surface(
                    onClick = onHideVideo,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f),
                    modifier = Modifier.size(40.dp).pointerHoverIcon(PointerIcon.Hand),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.VideocamOff,
                            contentDescription = stringResource(Res.string.video_hide),
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Surface(
                    onClick = { appFullscreenState.value = !isAppFullscreen },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f),
                    modifier = Modifier.size(40.dp).pointerHoverIcon(PointerIcon.Hand),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isAppFullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                            contentDescription = if (isAppFullscreen) "Salir de pantalla completa" else "Pantalla completa",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }

        // MiniPlayer inferior (auto-hide, empuja con el video)
        AnimatedVisibility(
            visible = controlsVisible,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(end = if (queueVisible || settingsVisible) 376.dp else 0.dp),
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
        ) {
            val progressState by playerViewModel.progressState.collectAsState()
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                MiniPlayer(
                    progressState = progressState,
                    isOnNowPlaying = false,
                    onNowPlaying = onCloseNowPlaying,
                    onToggleQueue = {
                        queueVisible = !queueVisible
                        if (queueVisible) settingsVisible = false
                    },
                    isQueueVisible = queueVisible,
                    isDocked = true,
                    bgTransparent = true,
                )
            }
        }
    }
}
