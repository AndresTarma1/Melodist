package example.nucleus.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import example.nucleus.ui.components.layout.AppVerticalScrollbar
import example.nucleus.ui.screens.settings.*
import example.nucleus.ui.themes.LocalMiniPlayerInset
import example.nucleus.ui.themes.screenTitle
import example.nucleus.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen() {
    val listState = rememberLazyListState()
    var activeDialog by remember { mutableStateOf<SettingsDialog?>(null) }

    CompositionLocalProvider(LocalSettingsColors provides rememberSettingsListItemColors()) {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .align(Alignment.TopCenter),
                contentPadding = PaddingValues(
                    start = 24.dp,
                    end = 24.dp,
                    top = 24.dp,
                    bottom = 24.dp + LocalMiniPlayerInset.current
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    Column(modifier = Modifier.padding(bottom = 16.dp)) {
                        Text(
                            text = stringResource(Res.string.settings_title),
                            style = MaterialTheme.typography.screenTitle,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(Res.string.settings_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                item {
                    AudioSettingsGroup(
                        onOpenEqualizer = { activeDialog = SettingsDialog.EQUALIZER }
                    )
                }

                item {
                    AppearanceSettingsGroup()
                }

                item {
                    MiniPlayerSettingsGroup(
                        onOpenSeekBarStyleDialog = { activeDialog = SettingsDialog.SEEK_BAR_STYLE }
                    )
                }

                item {
                    NowPlayingSettingsGroup()
                }

                item {
                    SyncSettingsGroup(
                        onShowYtmSyncWarning = { activeDialog = SettingsDialog.YTM_SYNC_WARNING }
                    )
                }

                item {
                    OverlaySettingsGroup(
                        onOpenCapture = { activeDialog = SettingsDialog.OVERLAY_CAPTURE }
                    )
                }

                item {
                    ApplicationSettingsGroup()
                }

                item {
                    AdvancedSettingsGroup(
                        onShowClearDownloadsDialog = { activeDialog = SettingsDialog.CLEAR_DOWNLOADS },
                        onOpenJvmSettings = { activeDialog = SettingsDialog.JVM_SETTINGS }
                    )
                }

                item {
                    SupportSettingsGroup()
                }

                item {
                    AboutCard()
                    Spacer(Modifier.height(16.dp))
                }
            }

            AppVerticalScrollbar(
                state = listState,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .padding(end = 2.dp, top = 4.dp, bottom = 4.dp)
            )
        }
    }

    SettingsDialogsHost(
        dialog = activeDialog,
        onDismiss = { activeDialog = null },
    )
}
