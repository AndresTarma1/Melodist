package com.example.melodist.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.melodist.data.AppPreferences
import com.example.melodist.data.AudioQuality
import com.example.melodist.data.ThemeMode
import com.example.melodist.viewmodels.DownloadViewModel

@Composable
fun SettingsScreen() {
    val scrollState = rememberScrollState()
    val downloadViewModel: DownloadViewModel = org.koin.compose.koinInject()

    val audioQuality    by AppPreferences.audioQuality.collectAsState()
    val themeMode       by AppPreferences.themeMode.collectAsState()
    val dynamicColor    by AppPreferences.dynamicColorFromArtwork.collectAsState()
    val highResCover    by AppPreferences.highResCoverArt.collectAsState()
    val cacheImages     by AppPreferences.cacheImages.collectAsState()
    val imagesEnabled   by AppPreferences.imagesEnabled.collectAsState()
    val minimizeToTray  by AppPreferences.minimizeToTray.collectAsState()
    val cacheSizeText   by downloadViewModel.cacheSizeText.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 32.dp)
        ) {
            Spacer(Modifier.height(32.dp))

            // ── Header ────────────────────────────────────────────────────────
            Text(
                "Configuración",
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "Personaliza tu experiencia de escucha",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(Modifier.height(36.dp))

            // ── Audio ─────────────────────────────────────────────────────────
            SettingsGroup(
                icon    = Icons.Rounded.GraphicEq,
                title   = "Audio",
                accent  = MaterialTheme.colorScheme.primary
            ) {
                SettingsOptionSelector(
                    title         = "Calidad de streaming",
                    subtitle      = "Afecta el uso de datos y la calidad del audio",
                    options       = AudioQuality.entries.map { it.label },
                    selectedIndex = AudioQuality.entries.indexOf(audioQuality),
                    onSelect      = { AppPreferences.setAudioQuality(AudioQuality.entries[it]) }
                )
            }

            Spacer(Modifier.height(24.dp))

            // ── Apariencia ────────────────────────────────────────────────────
            SettingsGroup(
                icon   = Icons.Rounded.Palette,
                title  = "Apariencia",
                accent = MaterialTheme.colorScheme.tertiary
            ) {
                SettingsOptionSelector(
                    title         = "Tema de color",
                    subtitle      = "Elige entre claro, oscuro o el del sistema",
                    options       = ThemeMode.entries.map { it.label },
                    selectedIndex = ThemeMode.entries.indexOf(themeMode),
                    onSelect      = { AppPreferences.setThemeMode(ThemeMode.entries[it]) }
                )
                SettingsDivider()
                SettingsToggle(
                    title          = "Colores dinámicos",
                    subtitle       = "El tema se adapta al color dominante de la carátula actual",
                    icon           = Icons.Rounded.ColorLens,
                    checked        = dynamicColor,
                    onCheckedChange = { AppPreferences.setDynamicColorFromArtwork(it) }
                )
            }

            Spacer(Modifier.height(24.dp))

            // ── Reproductor ───────────────────────────────────────────────────
            SettingsGroup(
                icon   = Icons.Rounded.PlayCircle,
                title  = "Reproductor",
                accent = MaterialTheme.colorScheme.secondary
            ) {
                SettingsToggle(
                    title          = "Carátulas en alta resolución",
                    subtitle       = "Imágenes más nítidas en el reproductor (usa más datos)",
                    icon           = Icons.Rounded.HighQuality,
                    checked        = highResCover,
                    onCheckedChange = { AppPreferences.setHighResCoverArt(it) }
                )
                SettingsDivider()
                SettingsToggle(
                    title          = "Mostrar imágenes",
                    subtitle       = "Desactiva para ahorrar recursos y ancho de banda",
                    icon           = Icons.Rounded.HideImage,
                    checked        = imagesEnabled,
                    onCheckedChange = { AppPreferences.setImagesEnabled(it) }
                )
            }

            Spacer(Modifier.height(24.dp))

            // ── Comportamiento ────────────────────────────────────────────────
            SettingsGroup(
                icon   = Icons.Rounded.DesktopWindows,
                title  = "Comportamiento",
                accent = MaterialTheme.colorScheme.primary
            ) {
                SettingsToggle(
                    title          = "Minimizar a la bandeja",
                    subtitle       = "Al cerrar, la app se minimiza al área de notificaciones",
                    icon           = Icons.Rounded.NotificationsActive,
                    checked        = minimizeToTray,
                    onCheckedChange = { AppPreferences.setMinimizeToTray(it) }
                )
            }

            Spacer(Modifier.height(24.dp))

            // ── Almacenamiento ────────────────────────────────────────────────
            SettingsGroup(
                icon   = Icons.Rounded.Storage,
                title  = "Almacenamiento",
                accent = MaterialTheme.colorScheme.tertiary
            ) {
                SettingsToggle(
                    title          = "Caché de imágenes en disco",
                    subtitle       = "Guarda las imágenes para cargarlas más rápido",
                    icon           = Icons.Rounded.Image,
                    checked        = cacheImages,
                    onCheckedChange = { AppPreferences.setCacheImages(it) }
                )
                SettingsDivider()
                SettingsInfoRow(
                    title = "Tamaño de caché de descargas",
                    value = cacheSizeText,
                    icon  = Icons.Rounded.FolderOpen
                )
                SettingsDivider()
                SettingsActionButton(
                    title        = "Limpiar caché de descargas",
                    subtitle     = "Eliminar todas las canciones descargadas localmente",
                    icon         = Icons.Rounded.DeleteSweep,
                    buttonText   = "Limpiar",
                    isDestructive = true,
                    onClick      = { downloadViewModel.clearCache() }
                )
            }

            Spacer(Modifier.height(24.dp))

            // ── Acerca de ─────────────────────────────────────────────────────
            AboutCard()

            Spacer(Modifier.height(80.dp))
        }

        // Scrollbar
        VerticalScrollbar(
            adapter  = rememberScrollbarAdapter(scrollState),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding( end = 4.dp).padding(vertical = 8.dp),
            style    = LocalScrollbarStyle.current.copy(
                thickness    = 4.dp,
                unhoverColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                hoverColor   = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f),
                shape        = CircleShape
            )
        )
    }
}

// ——─—─—─—─—─—─—─—─—─—─—─—─—─—─—─—─—─—─—─—─—─—─—─—─—─—─—─—─—─—─—─—─—─—─—─—─—─—─
// GROUP — agrupa ajustes relacionados en una card con encabezado coloreado
// —─—─—─—─—─—─—─—─—─—─—─—─—─—─—─—─—─—─—─—─—─—─—─—─—─—─—─—─—─—─—─—─—─—─—─—─—─—─—

@Composable
private fun SettingsGroup(
    icon:    ImageVector,
    title:   String,
    accent:  Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        // Encabezado de la sección
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier          = Modifier.padding(start = 4.dp, bottom = 10.dp)
        ) {
            Box(
                modifier         = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = accent, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight   = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                ),
                color = accent
            )
        }

        // Card que contiene todos los ítems del grupo
        Surface(
            modifier       = Modifier.fillMaxWidth(),
            shape          = RoundedCornerShape(18.dp),
            color          = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 0.dp,
            border         = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                content()
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TOGGLE
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SettingsToggle(
    title:           String,
    subtitle:        String,
    icon:            ImageVector,
    checked:         Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon, null,
            modifier = Modifier.size(22.dp),
            tint     = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                subtitle,
                style  = MaterialTheme.typography.bodySmall,
                color  = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Spacer(Modifier.width(16.dp))
        Switch(
            checked         = checked,
            onCheckedChange = onCheckedChange,
            colors          = SwitchDefaults.colors(
                checkedThumbColor       = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor       = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor     = MaterialTheme.colorScheme.outline,
                uncheckedTrackColor     = MaterialTheme.colorScheme.surfaceContainerHighest,
                uncheckedBorderColor    = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
            )
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// OPTION SELECTOR — chips horizontales
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SettingsOptionSelector(
    title:         String,
    subtitle:      String,
    options:       List<String>,
    selectedIndex: Int,
    onSelect:      (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.Tune, null,
                modifier = Modifier.size(22.dp),
                tint     = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    subtitle,
                    style  = MaterialTheme.typography.bodySmall,
                    color  = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // Segmented button row
        Surface(
            shape  = RoundedCornerShape(12.dp),
            color  = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(modifier = Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                options.forEachIndexed { index, label ->
                    val isSelected = index == selectedIndex
                    val bgColor by animateColorAsState(
                        if (isSelected) MaterialTheme.colorScheme.primaryContainer
                        else Color.Transparent,
                        animationSpec = tween(200),
                        label         = "segBg"
                    )
                    val textColor by animateColorAsState(
                        if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        animationSpec = tween(200),
                        label         = "segText"
                    )
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(9.dp))
                            .clickable { onSelect(index) }
                            .pointerHoverIcon(PointerIcon.Hand),
                        shape = RoundedCornerShape(9.dp),
                        color = bgColor
                    ) {
                        Text(
                            text     = label,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 9.dp),
                            style    = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            ),
                            color    = textColor,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// INFO ROW — dato de solo lectura
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SettingsInfoRow(title: String, value: String, icon: ImageVector) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Text(
            title,
            style    = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            color    = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
        ) {
            Text(
                value,
                style    = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color    = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ACTION BUTTON
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SettingsActionButton(
    title:        String,
    subtitle:     String,
    icon:         ImageVector,
    buttonText:   String,
    isDestructive: Boolean = false,
    onClick:      () -> Unit
) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon, null,
            modifier = Modifier.size(22.dp),
            tint     = if (isDestructive) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                subtitle,
                style  = MaterialTheme.typography.bodySmall,
                color  = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Spacer(Modifier.width(16.dp))
        if (isDestructive) {
            OutlinedButton(
                onClick = onClick,
                colors  = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                border  = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                shape   = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Rounded.DeleteSweep, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(buttonText, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold))
            }
        } else {
            FilledTonalButton(onClick = onClick, shape = RoundedCornerShape(10.dp)) {
                Text(buttonText)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// DIVIDER interno de grupo — sutil, solo entre ítems
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier  = Modifier.padding(horizontal = 20.dp),
        color     = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
        thickness = 0.5.dp
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// ABOUT CARD
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AboutCard() {
    Surface(
        modifier       = Modifier.fillMaxWidth(),
        shape          = RoundedCornerShape(18.dp),
        color          = MaterialTheme.colorScheme.surfaceContainerLow,
        border         = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier          = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Ícono de la app
            Box(
                modifier         = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.tertiary
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.MusicNote, null,
                    tint     = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Melodist",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "Reproductor de música de escritorio",
                    style  = MaterialTheme.typography.bodySmall,
                    color  = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
            ) {
                Text(
                    "v1.0.1 Beta",
                    style    = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color    = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }
    }
}