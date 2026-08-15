package example.nucleus.ui.screens.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.alorma.compose.settings.ui.SettingsGroup
import com.alorma.compose.settings.ui.expressive.SettingsMenuLink
import com.alorma.compose.settings.ui.expressive.SettingsSwitch
import example.nucleus.data.AppDirs
import example.nucleus.shared.generated.resources.Res
import example.nucleus.shared.generated.resources.*
import example.nucleus.ui.screens.shared.openFolder
import example.nucleus.viewmodels.AdvancedSettingsViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AdvancedSettingsGroup(
    onShowClearDownloadsDialog: () -> Unit,
    onOpenJvmSettings: () -> Unit
) {
    val colors = LocalSettingsColors.current
    val viewModel: AdvancedSettingsViewModel = koinInject()
    val cacheImages by viewModel.cacheImages.collectAsState()

    SettingsGroup(
        title = { Text(stringResource(Res.string.section_advanced)) },
        colors = colors
    ) {
        SettingsSwitch(
            icon = { Icon(Icons.Rounded.Image, null) },
            title = { Text(stringResource(Res.string.cache_images)) },
            shapes = ListItemDefaults.segmentedShapes(index = 0, count = 4),
            colors = colors,
            state = cacheImages,
            onCheckedChange = { viewModel.setCacheImages(it) }
        )
        ActionRow(
            label = stringResource(Res.string.open_data_folder),
            icon = Icons.Rounded.FolderOpen,
            btnLabel = stringResource(Res.string.btn_open),
            segmentedShape = ListItemDefaults.segmentedShapes(index = 1, count = 4),
            onClick = { openFolder(AppDirs.dataRoot) }
        )
        ActionRow(
            label = stringResource(Res.string.clear_download_cache),
            icon = Icons.Rounded.DeleteSweep,
            btnLabel = stringResource(Res.string.btn_clear),
            segmentedShape = ListItemDefaults.segmentedShapes(index = 2, count = 4),
            isDestructive = true,
            onClick = onShowClearDownloadsDialog
        )
        SettingsMenuLink(
            icon = { Icon(Icons.Rounded.AutoFixHigh, null) },
            enabled = false,
            shapes = ListItemDefaults.segmentedShapes(index = 3, count = 4),
            title = { Text(stringResource(Res.string.skiko_rendering)) },
            colors = colors,
            subtitle = { Text(stringResource(Res.string.render_api_restart)) },
            action = {
                IconButton(onClick = onOpenJvmSettings) {
                    Icon(Icons.Rounded.ChevronRight, null)
                }
            },
            onClick = onOpenJvmSettings
        )
    }
}
