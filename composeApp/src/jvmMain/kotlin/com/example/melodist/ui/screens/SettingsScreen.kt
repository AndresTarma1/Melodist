package com.example.melodist.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.melodist.data.AppPreferences
import com.example.melodist.data.AudioQuality
import com.example.melodist.data.ThemeMode
import com.example.melodist.viewmodels.DownloadViewModel

@Composable
fun SettingsScreen() {
    val scrollState = rememberScrollState()
    val downloadViewModel: DownloadViewModel = org.koin.compose.koinInject()

    val audioQuality by AppPreferences.audioQuality.collectAsState()
    val themeMode by AppPreferences.themeMode.collectAsState()
    val dynamicColor by AppPreferences.dynamicColorFromArtwork.collectAsState()
    val highResCover by AppPreferences.highResCoverArt.collectAsState()
    val cacheImages by AppPreferences.cacheImages.collectAsState()
    val imagesEnabled by AppPreferences.imagesEnabled.collectAsState()
    val minimizeToTray by AppPreferences.minimizeToTray.collectAsState()
    val cacheSizeText by downloadViewModel.cacheSizeText.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        // Header
        Text(
            "Configuración",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 24.dp, top = 8.dp)
        )

        // ── Audio ──
        SettingsSectionHeader(Icons.AutoMirrored.Filled.VolumeUp, "Audio")
        Spacer(Modifier.height(8.dp))

        SettingsOptionSelector(
            title = "Calidad de audio",
            subtitle = "Selecciona la calidad de streaming y descarga",
            options = AudioQuality.entries.map { it.label },
            selectedIndex = AudioQuality.entries.indexOf(audioQuality),
            onSelect = { AppPreferences.setAudioQuality(AudioQuality.entries[it]) }
        )

        Spacer(Modifier.height(16.dp))

        // ── Apariencia ──
        SettingsSectionHeader(Icons.Default.Palette, "Apariencia")
        Spacer(Modifier.height(8.dp))

        SettingsOptionSelector(
            title = "Tema",
            subtitle = "Modo de color de la interfaz",
            options = ThemeMode.entries.map { it.label },
            selectedIndex = ThemeMode.entries.indexOf(themeMode),
            onSelect = { AppPreferences.setThemeMode(ThemeMode.entries[it]) }
        )

        Spacer(Modifier.height(4.dp))

        SettingsToggle(
            title = "Colores dinámicos desde la carátula",
            subtitle = "El tema Material se adapta al color dominante de la canción o álbum actual",
            icon = Icons.Default.ColorLens,
            checked = dynamicColor,
            onCheckedChange = { AppPreferences.setDynamicColorFromArtwork(it) }
        )

        Spacer(Modifier.height(16.dp))

        // ── Reproductor ──
        SettingsSectionHeader(Icons.Default.PlayCircle, "Reproductor")
        Spacer(Modifier.height(8.dp))

        SettingsToggle(
            title = "Carátulas en alta resolución",
            subtitle = "Descarga imágenes en mayor calidad para el reproductor (usa más datos)",
            icon = Icons.Default.HighQuality,
            checked = highResCover,
            onCheckedChange = { AppPreferences.setHighResCoverArt(it) }
        )

        Spacer(Modifier.height(4.dp))

        SettingsToggle(
            title = "Mostrar imágenes",
            subtitle = "Desactiva para ahorrar recursos y ancho de banda (no se cargarán carátulas ni thumbnails)",
            icon = Icons.Default.HideImage,
            checked = imagesEnabled,
            onCheckedChange = { AppPreferences.setImagesEnabled(it) }
        )

        Spacer(Modifier.height(16.dp))

        // ── Comportamiento ──
        SettingsSectionHeader(Icons.Default.DesktopWindows, "Comportamiento")
        Spacer(Modifier.height(8.dp))

        SettingsToggle(
            title = "Minimizar a la bandeja del sistema",
            subtitle = "Al cerrar la ventana, la app se minimiza al área de notificaciones en vez de cerrarse",
            icon = Icons.Default.NotificationsActive,
            checked = minimizeToTray,
            onCheckedChange = { AppPreferences.setMinimizeToTray(it) }
        )

        Spacer(Modifier.height(16.dp))

        // ── Almacenamiento ──
        SettingsSectionHeader(Icons.Default.Storage, "Almacenamiento")
        Spacer(Modifier.height(8.dp))

        SettingsToggle(
            title = "Caché de imágenes en disco",
            subtitle = "Guarda las imágenes descargadas para cargarlas más rápido",
            icon = Icons.Default.Image,
            checked = cacheImages,
            onCheckedChange = { AppPreferences.setCacheImages(it) }
        )

        Spacer(Modifier.height(4.dp))

        SettingsInfoRow(
            title = "Tamaño de caché de descargas",
            value = cacheSizeText,
            icon = Icons.Default.FolderOpen
        )

        Spacer(Modifier.height(4.dp))

        SettingsActionButton(
            title = "Limpiar caché de descargas",
            subtitle = "Eliminar todas las canciones descargadas",
            icon = Icons.Default.DeleteSweep,
            buttonText = "Limpiar",
            isDestructive = true,
            onClick = {
                downloadViewModel.clearCache()
            }
        )

        Spacer(Modifier.height(32.dp))

        // ── About ──
        SettingsSectionHeader(Icons.Default.Info, "Acerca de")
        Spacer(Modifier.height(8.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 1.dp
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    "Melodist",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Reproductor de música de escritorio",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Versión 1.0.1 Beta",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }

        Spacer(Modifier.height(80.dp))
    }
}

// ─── Reusable settings components ─────────────────────

@Composable
private fun SettingsSectionHeader(icon: ImageVector, title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        Icon(
            icon, null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun SettingsToggle(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onCheckedChange(!checked) }
            .pointerHoverIcon(PointerIcon.Hand),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon, null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}

@Composable
private fun SettingsOptionSelector(
    title: String,
    subtitle: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
            Spacer(Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                options.forEachIndexed { index, label ->
                    val isSelected = index == selectedIndex
                    val bgColor by animateColorAsState(
                        if (isSelected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                        label = "optionBg"
                    )
                    val textColor by animateColorAsState(
                        if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        label = "optionText"
                    )
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSelect(index) }
                            .pointerHoverIcon(PointerIcon.Hand),
                        shape = RoundedCornerShape(10.dp),
                        color = bgColor,
                        border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
                    ) {
                        Text(
                            text = label,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal),
                            color = textColor,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsInfoRow(title: String, value: String, icon: ImageVector) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(14.dp))
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun SettingsActionButton(
    title: String,
    subtitle: String,
    icon: ImageVector,
    buttonText: String,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon, null,
                modifier = Modifier.size(24.dp),
                tint = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium), color = MaterialTheme.colorScheme.onSurface)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
            }
            Spacer(Modifier.width(8.dp))
            if (isDestructive) {
                OutlinedButton(
                    onClick = onClick,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                ) { Text(buttonText) }
            } else {
                FilledTonalButton(onClick = onClick) { Text(buttonText) }
            }
        }
    }
}


