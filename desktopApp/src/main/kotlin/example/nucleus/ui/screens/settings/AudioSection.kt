package example.nucleus.ui.screens.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.alorma.compose.settings.ui.SettingsGroup
import com.alorma.compose.settings.ui.expressive.SettingsMenuLink
import example.nucleus.data.repository.AudioQuality
import example.nucleus.ui.screens.shared.displayName
import example.nucleus.viewmodels.SettingsViewModel
import example.nucleus.shared.generated.resources.Res
import example.nucleus.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AudioSettingsGroup(
    viewModel: SettingsViewModel,
    colors: ListItemColors,
    onOpenEqualizer: () -> Unit
) {
    val audioQuality by viewModel.audioQuality.collectAsState()
    var showAudioDropdown by remember { mutableStateOf(false) }

    SettingsGroup(
        title = { Text(stringResource(Res.string.section_audio)) },
        colors = colors,
    ) {
        DropdownSelector(
            label = stringResource(Res.string.streaming_quality),
            icon = Icons.Rounded.Tune,
            colors = colors,
            currentValue = audioQuality.displayName(),
            segmentedShape = ListItemDefaults.segmentedShapes(index = 0, count = 2),
            expanded = showAudioDropdown,
            onExpandedChange = { showAudioDropdown = it },
            options = AudioQuality.entries.map { it to it.displayName() },
            isSelected = { it == audioQuality },
            onSelect = { viewModel.setAudioQuality(it); showAudioDropdown = false }
        )
        SettingsMenuLink(
            icon = { Icon(Icons.Rounded.GraphicEq, null) },
            colors = colors,
            shapes = ListItemDefaults.segmentedShapes(index = 1, count = 2),
            title = { Text(stringResource(Res.string.equalizer)) },
            action = {
                IconButton(onClick = onOpenEqualizer) {
                    Icon(Icons.Rounded.ChevronRight, null)
                }
            },
            subtitle = { Text(stringResource(Res.string.ten_bands)) },
            onClick = onOpenEqualizer
        )
    }
}
