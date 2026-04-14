package com.example.melodist

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.rememberWindowState
import com.example.melodist.data.repository.ThemeMode
import com.example.melodist.data.repository.UserPreferencesRepository
import com.example.melodist.navigation.NavigationDesktop
import com.example.melodist.navigation.RootComponent
import com.example.melodist.player.PlaybackState
import com.example.melodist.ui.components.artwork.LocalArtworkColors
import com.example.melodist.ui.components.artwork.rememberArtworkColors
import com.example.melodist.ui.themes.MelodistTheme
import com.example.melodist.utils.LocalSnackbarHostState
import com.example.melodist.utils.LocalDownloadViewModel
import com.example.melodist.utils.LocalLibraryViewModel
import com.example.melodist.utils.LocalPlayerViewModel
import com.example.melodist.utils.LocalUserPreferences
import com.example.melodist.viewmodels.DownloadViewModel
import com.example.melodist.viewmodels.LibraryViewModel
import com.example.melodist.viewmodels.PlayerViewModel
import com.kdroid.composetray.tray.api.Tray
import dev.hydraulic.conveyor.control.SoftwareUpdateController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import melodist.composeapp.generated.resources.Res
import melodist.composeapp.generated.resources.music_icon
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.intui.standalone.theme.darkThemeDefinition
import org.jetbrains.jewel.intui.standalone.theme.lightThemeDefinition
import org.jetbrains.jewel.intui.window.decoratedWindow
import org.jetbrains.jewel.intui.window.styling.dark
import org.jetbrains.jewel.intui.window.styling.light
import org.jetbrains.jewel.ui.ComponentStyling
import org.jetbrains.jewel.window.DecoratedWindow
import org.jetbrains.jewel.window.TitleBar
import org.jetbrains.jewel.window.TitleBarScope
import org.jetbrains.jewel.window.styling.TitleBarColors
import org.jetbrains.jewel.window.styling.TitleBarStyle
import java.awt.Color
import java.awt.Dimension
import java.awt.EventQueue
import java.awt.Frame
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent

@Composable
fun ApplicationScope.App(
    rootComponent: RootComponent,
    playerViewModel: PlayerViewModel,
    downloadViewModel: DownloadViewModel,
    libraryViewModel: LibraryViewModel,
    userPreferences: UserPreferencesRepository,
    onExit: () -> Unit,
    windowState: WindowState,
) {

    val scope = rememberCoroutineScope()

    var updateInfo by remember { mutableStateOf<Pair<String, String>?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        val controller = SoftwareUpdateController.getInstance() ?: return@LaunchedEffect
        val availability = controller.canTriggerUpdateCheckUI()
        if (availability != SoftwareUpdateController.Availability.AVAILABLE) return@LaunchedEffect

        val current = controller.currentVersion ?: return@LaunchedEffect
        val latest = withContext(Dispatchers.IO) {
            try {
                controller.currentVersionFromRepository
            } catch (_: SoftwareUpdateController.UpdateCheckException) {
                null
            }
        } ?: return@LaunchedEffect

        if (latest > current) {
            updateInfo = current.toString() to latest.toString()
        }
    }



    var isVisible by remember { mutableStateOf(false) }
    val minimizeToTray by remember { userPreferences.minimizeToTray }.collectAsState(false)



    fun handleExit() {
        scope.launch {
            if(windowState.placement == WindowPlacement.Maximized) {
                userPreferences.setWindowMaximized(true)
            }else {
                userPreferences.setWindowMaximized(false)
                userPreferences.setWindowSize(windowState.size.width.value.toInt(), windowState.size.height.value.toInt())
            }
            onExit()
        }
    }


    // ── Tray ─────────────────────────────────────────────────────────────────
    if (!isVisible || minimizeToTray) {
        val trayState by playerViewModel.uiState.collectAsState()
        val isPlaying = trayState.playbackState == PlaybackState.PLAYING
        Tray(
            icon = Icons.Filled.MusicNote,
            tooltip = trayState.currentSong?.title ?: "Melodist",
            primaryAction = { isVisible = !isVisible },
        ) {
            Item(label = if (isPlaying) "Pausar" else "Reproducir", onClick = { playerViewModel.togglePlayPause() })
            Item(label = "Siguiente", onClick = { playerViewModel.next() })
            Item(label = "Anterior", onClick = { playerViewModel.previous() })
            Divider()
            Item(label = "Abrir Melodist", onClick = { isVisible = true })
            Item(label = "Salir", onClick = { handleExit() })
        }
    }

    // ── Theme ─────────────────────────────────────────────────────────────────
    val playerState by playerViewModel.uiState.collectAsState()
    val artworkColors = rememberArtworkColors(playerState.currentSong?.thumbnail)
    val themeMode by remember { userPreferences.themeMode}.collectAsState(ThemeMode.SYSTEM)
    val isDark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        else -> { isSystemInDarkTheme()}
    }

    // ── Window ────────────────────────────────────────────────────────────────
    MelodistTheme(artworkColors = artworkColors, userPreferences = userPreferences) {
        val surfaceColor = MaterialTheme.colorScheme.surface

        val titleBarStyle = if (isDark)
            TitleBarStyle.dark(
                colors = TitleBarColors.dark(
                    backgroundColor = surfaceColor,
                    inactiveBackground = surfaceColor,
                    borderColor = surfaceColor,
                )
            )
        else
            TitleBarStyle.light(
                colors = TitleBarColors.light(
                    backgroundColor = surfaceColor,
                    inactiveBackground = surfaceColor,
                    borderColor = surfaceColor,
                )
            )

        IntUiTheme(
            theme = if (isDark)
                JewelTheme.darkThemeDefinition()
            else
                JewelTheme.lightThemeDefinition(),
            styling = ComponentStyling.decoratedWindow(titleBarStyle = titleBarStyle),
        ) {
            CompositionLocalProvider(
                LocalArtworkColors provides artworkColors,
                LocalSnackbarHostState provides snackbarHostState,
                LocalLibraryViewModel provides libraryViewModel,
                LocalPlayerViewModel provides playerViewModel,
                LocalDownloadViewModel provides downloadViewModel,
                LocalUserPreferences provides userPreferences,
            ) {
                DecoratedWindow(
                    onCloseRequest = { if (minimizeToTray) isVisible = false else handleExit() },
                    state = windowState,
                    visible = isVisible,
                    title = "Melodist",
                    icon = painterResource(Res.drawable.music_icon),
                ) {
                    updateInfo?.let { (currentVersion, latestVersion) ->
                        AlertDialog(
                            onDismissRequest = { updateInfo = null },
                            title = { Text("Actualización disponible") },
                            text = {
                                Text(
                                    "Hay una nueva versión de Melodist disponible.\n\n" +
                                        "Versión actual: $currentVersion\n" +
                                        "Nueva versión: $latestVersion\n\n" +
                                        "¿Deseas actualizar ahora?"
                                )
                            },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        updateInfo = null
                                        SoftwareUpdateController.getInstance()?.triggerUpdateCheckUI()
                                    }
                                ) {
                                    Text("Actualizar")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { updateInfo = null }) {
                                    Text("Más tarde")
                                }
                            }
                        )
                    }

                    window.minimumSize = Dimension(1024, 600)
                    DisposableEffect(Unit) {
                        val startMaximized = windowState.placement == WindowPlacement.Maximized
                        val listener = object : ComponentAdapter() {
                            override fun componentResized(e: ComponentEvent) {
                                window.removeComponentListener(this)
                                if (startMaximized) {
                                    window.extendedState = Frame.MAXIMIZED_BOTH
                                }
                                EventQueue.invokeLater {
                                    window.isVisible = true
                                    isVisible = true
                                }
                            }
                        }

                        window.addComponentListener(listener)
                        onDispose { window.removeComponentListener(listener) }
                    }
                    TitleBar {
                        MelodistTitleBar(
                            currentSong = playerState.currentSong?.title,
                            isPlaying = playerState.playbackState == PlaybackState.PLAYING,
                        )
                    }


                    NavigationDesktop(rootComponent)

                    SnackbarHost(hostState = snackbarHostState)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TitleBar content
// ─────────────────────────────────────────────────────────────────────────────



@Composable
private fun TitleBarScope.MelodistTitleBar(
    currentSong: String?,
    isPlaying: Boolean,
) {
    Row(
        modifier = Modifier.align(Alignment.Start).padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "Melodist",
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }

    AnimatedContent(
        targetState = currentSong,
        transitionSpec = {
            (fadeIn() + slideInVertically { it / 2 })
                .togetherWith(fadeOut() + slideOutVertically { -it / 2 })
        },
        modifier = Modifier.align(Alignment.CenterHorizontally),
    ) { song ->
        if (song != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.GraphicEq else Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = song,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 300.dp),
                )
            }
        }
    }
}
