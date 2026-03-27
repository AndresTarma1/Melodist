package com.example.melodist

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.example.melodist.data.AppPreferences
import com.example.melodist.navigation.NavigationDesktop
import com.example.melodist.navigation.RootComponent
import com.example.melodist.player.PlaybackState
import com.example.melodist.ui.components.LocalArtworkColors
import com.example.melodist.ui.components.rememberArtworkColors
import com.example.melodist.ui.themes.MelodistTheme
import com.example.melodist.utils.LocalDownloadViewModel
import com.example.melodist.utils.LocalPlayerViewModel
import com.example.melodist.utils.WindowsUtils
import com.example.melodist.viewmodels.DownloadViewModel
import com.example.melodist.viewmodels.PlayerViewModel
import com.kdroid.composetray.tray.api.Tray
import kotlinx.coroutines.delay
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
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun ApplicationScope.App(
    rootComponent: RootComponent,
    playerViewModel: PlayerViewModel,
    downloadViewModel: DownloadViewModel,
    onExit: () -> Unit,
) {
    val windowState = rememberWindowState(
        placement = if (AppPreferences.windowMaximized) WindowPlacement.Maximized else WindowPlacement.Floating,
        width = AppPreferences.windowWidth.dp,
        height = AppPreferences.windowHeight.dp,
        position = WindowPosition(Alignment.Center)
    )

    var isVisible by remember { mutableStateOf(true) }
    val minimizeToTray by AppPreferences.minimizeToTray.collectAsState()


    // —─ Guardar estado al salir —─—─—─—─—─—─—─—─—─—─—─—─—─—─—─—─—─—─—─—─—─
    val handleExit = {
        if (windowState.placement == WindowPlacement.Maximized) {
            AppPreferences.windowMaximized = true
        } else {
            AppPreferences.windowMaximized = false
            AppPreferences.windowWidth = windowState.size.width.value.toInt()
            AppPreferences.windowHeight = windowState.size.height.value.toInt()
        }
        onExit()
    }

    // ── Tray ─────────────────────────────────────────────────────────────
    if (!isVisible || minimizeToTray) {
        val trayState by playerViewModel.uiState.collectAsState()
        val isPlaying = trayState.playbackState == PlaybackState.PLAYING

        Tray(
            icon = Icons.Filled.MusicNote,
            tooltip = trayState.currentSong?.title ?: "Melodist",
            primaryAction = { isVisible = !isVisible },
        ) {
            Item(label = if (isPlaying) "Pausar" else "Reproducir", onClick = { playerViewModel.togglePlayPause() })
            Item(label = "siguiente", onClick = { playerViewModel.next() })
            Item(label = "Anterior", onClick = { playerViewModel.previous() })
            Divider()
            Item(label = "Abrir Melodist", onClick = { isVisible = true })
            Item(label = "salir", onClick = handleExit)
        }

    }

    // ── Theme state ───────────────────────────────────────────────────────
    val playerState by playerViewModel.uiState.collectAsState()
    val artworkColors = rememberArtworkColors(playerState.currentSong?.thumbnail)
    val isDark = !artworkColors.isLight

    // ── Window ────────────────────────────────────────────────────────────
    MelodistTheme(artworkColors = artworkColors) {
        val railBackground = MaterialTheme.colorScheme.surface
        val surfaceColor = MaterialTheme.colorScheme.surface

        val titleBarStyle = if (isDark)
            TitleBarStyle.dark(
                colors = TitleBarColors.dark(
                    backgroundColor = railBackground,
                    inactiveBackground = railBackground.copy(alpha = 0.85f),
                    borderColor = railBackground,
                )
            )
        else
            TitleBarStyle.light(
                colors = TitleBarColors.light(
                    backgroundColor = railBackground,
                    inactiveBackground = railBackground.copy(alpha = 0.85f),
                    borderColor = railBackground,
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
                LocalPlayerViewModel provides playerViewModel,
                LocalDownloadViewModel provides downloadViewModel,
            ) {


                DecoratedWindow(
                    onCloseRequest = { if (minimizeToTray) isVisible = false else handleExit() },
                    state = windowState,
                    visible = isVisible,
                    title = "Melodist",
                    icon = painterResource(Res.drawable.music_icon),
                ) {

                    // Forzar maximizado con JNA si la preferencia está activa
                    LaunchedEffect(Unit) {
                        if (AppPreferences.windowMaximized) {
                            WindowsUtils.maximizeWindow(window)
                        }
                    }

                    TitleBar {
                        MelodistTitleBar(
                            currentSong = playerState.currentSong?.title,
                            isPlaying = playerState.playbackState == PlaybackState.PLAYING,
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(surfaceColor)
                    ) {
                        NavigationDesktop(rootComponent)
                    }
                }
            }
        }
    }
}

// —─—─—─—─—─—─—─—─—─—─—─—─—─—─—─—─—─—─—─—─—─—─—─—─—─—─—─—─—─—─—─—─—─—─—─—─—─—─—
// TitleBar content
// —─—─—─—─—─—─—─—─—─—─—─—─—─—─—─—─—─—─—─—─—─—─—─—─—─—─—─—─—─—─—─—─—─—─—─—─—─—─—

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