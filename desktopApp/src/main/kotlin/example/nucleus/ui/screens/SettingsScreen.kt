package example.nucleus.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import example.nucleus.overlay.GlobalHotkeyManager
import example.nucleus.ui.components.EqualizerDialog
import example.nucleus.ui.components.layout.AppVerticalScrollbar
import example.nucleus.ui.screens.settings.*
import example.nucleus.utils.LocalDownloadViewModel
import example.nucleus.viewmodels.ApplicationViewModel
import example.nucleus.viewmodels.JvmSettingsViewModel
import example.nucleus.viewmodels.SettingsViewModel
import example.nucleus.shared.generated.resources.*
import example.nucleus.viewmodels.AppViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val listState = rememberLazyListState()
    val downloadViewModel = LocalDownloadViewModel.current

    var showClearDownloadsDialog by remember { mutableStateOf(false) }
    var showEqualizerDialog by remember { mutableStateOf(false) }
    var showJvmSettingsDialog by remember { mutableStateOf(false) }
    var showYtmSyncWarning by remember { mutableStateOf(false) }
    var showOverlayCapture by remember { mutableStateOf(false) }
    var showSeekBarStyleDialog by remember { mutableStateOf(false) }
    val appViewModel: AppViewModel = koinInject()

    val colors = ListItemDefaults.segmentedColors(
        // Estado base (sin seleccionar)
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
        leadingContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        trailingContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        overlineContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        supportingContentColor = MaterialTheme.colorScheme.onSurfaceVariant,

        // Estado seleccionado — contenedor SÓLIDO (sin alpha), no un tinte débil.
        // El salto de "surfaceContainerLow" a "primaryContainer" ya es visible por sí solo.
        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
        selectedContentColor = MaterialTheme.colorScheme.onSurface,
        selectedLeadingContentColor = MaterialTheme.colorScheme.primary,
        selectedTrailingContentColor = MaterialTheme.colorScheme.primary,
        selectedOverlineContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        selectedSupportingContentColor = MaterialTheme.colorScheme.onPrimaryContainer,

        // Estado "dragged" — usa un color DISTINTO al seleccionado (tertiary),
        // para que arrastrar y estar seleccionado no se confundan visualmente.
        draggedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
        draggedContentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        draggedLeadingContentColor = MaterialTheme.colorScheme.tertiary,
        draggedTrailingContentColor = MaterialTheme.colorScheme.tertiary,
        draggedOverlineContentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        draggedSupportingContentColor = MaterialTheme.colorScheme.onTertiaryContainer,

        // Estado deshabilitado — sigue el estándar de Material (38% de opacidad sobre onSurface)
        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        disabledLeadingContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        disabledTrailingContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        disabledOverlineContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        disabledSupportingContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
    )

    val equalizerBands by viewModel.equalizerBands.collectAsState()
    val seekBarStyle by viewModel.seekBarStyle.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .align(Alignment.TopCenter),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Column(modifier = Modifier.padding(bottom = 16.dp)) {
                    Text(
                        text = stringResource(Res.string.settings_title),
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
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
                    viewModel = viewModel,
                    colors = colors,
                    onOpenEqualizer = { showEqualizerDialog = true }
                )
            }

            item {
                AppearanceSettingsGroup(viewModel = viewModel, colors = colors)
            }

            item {
                PlayerSettingsGroup(
                    viewModel = viewModel,
                    colors = colors,
                    onOpenSeekBarStyleDialog = { showSeekBarStyleDialog = true }
                )
            }

            item {
                SyncSettingsGroup(
                    viewModel = viewModel,
                    colors = colors,
                    onShowYtmSyncWarning = { showYtmSyncWarning = true }
                )
            }

            item {
                OverlaySettingsGroup(
                    viewModel = viewModel,
                    colors = colors,
                    onOpenCapture = { showOverlayCapture = true }
                )
            }

            item {
                SystemSettingsGroup(
                    viewModel = viewModel,
                    colors = colors,
                    onShowClearDownloadsDialog = { showClearDownloadsDialog = true },
                    onOpenJvmSettings = { showJvmSettingsDialog = true }
                )
            }

            item {
                SupportSettingsGroup(appViewModel = appViewModel, colors = colors)
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

    if (showOverlayCapture) {
        val hotkeyManager: GlobalHotkeyManager = koinInject()
        DisposableEffect(Unit) {
            hotkeyManager.beginCapture { combo ->
                viewModel.setOverlayHotkey(combo.keyCode, combo.modsMask, combo.label())
                showOverlayCapture = false
            }
            onDispose { hotkeyManager.cancelCapture() }
        }
        AlertDialog(
            onDismissRequest = { showOverlayCapture = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 0.dp,
            icon = { Icon(Icons.Rounded.Keyboard, null) },
            title = { Text(stringResource(Res.string.overlay_capture_title)) },
            text = { Text(stringResource(Res.string.overlay_capture_message)) },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showOverlayCapture = false }) { Text(stringResource(Res.string.cancel)) }
            },
        )
    }

    if (showYtmSyncWarning) {
        AlertDialog(
            onDismissRequest = { showYtmSyncWarning = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 0.dp,
            icon = { Icon(Icons.Rounded.WarningAmber, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(Res.string.ytm_sync_warning_title)) },
            text = { Text(stringResource(Res.string.ytm_sync_warning_message)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setYtmSyncEnabled(true)
                    showYtmSyncWarning = false
                }) { Text(stringResource(Res.string.ytm_sync_warning_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showYtmSyncWarning = false }) { Text(stringResource(Res.string.cancel)) }
            },
        )
    }

    if (showClearDownloadsDialog) {
        AlertDialog(
            onDismissRequest = { showClearDownloadsDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 0.dp,
            title = { Text(stringResource(Res.string.clear_downloads_title)) },
            text = { Text(stringResource(Res.string.clear_downloads_message)) },
            confirmButton = {
                TextButton(onClick = { downloadViewModel.clearCache(); showClearDownloadsDialog = false }) {
                    Text(stringResource(Res.string.btn_clear))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDownloadsDialog = false }) { Text(stringResource(Res.string.cancel)) }
            }
        )
    }

    if (showEqualizerDialog) {
        EqualizerDialog(
            onDismiss = { showEqualizerDialog = false },
            bands = equalizerBands,
            onBandsChange = { viewModel.setEqualizerBands(it) }
        )
    }

    if (showSeekBarStyleDialog) {
        ResponsiveSettingsDialog(
            onDismiss = { showSeekBarStyleDialog = false },
            icon = Icons.AutoMirrored.Rounded.ShowChart,
            title = stringResource(Res.string.seek_bar_style_title),
        ) {
            SeekBarStylePickerContent(
                current = seekBarStyle,
                onSelect = { viewModel.setSeekBarStyle(it) },
            )
        }
    }

    if (showJvmSettingsDialog) {
        val jvmSettingsViewModel: JvmSettingsViewModel = koinInject()
        AdvancedJvmSettingsScreen(
            viewModel = jvmSettingsViewModel,
            onDismiss = { showJvmSettingsDialog = false },
        )
    }
}
