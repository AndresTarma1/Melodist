package com.example.melodist.ui.themes

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import com.example.melodist.data.AppPreferences
import com.example.melodist.data.ThemeMode
import com.example.melodist.ui.components.artwork.ArtworkColors
import com.materialkolor.DynamicMaterialTheme
import com.materialkolor.PaletteStyle
import com.materialkolor.rememberDynamicMaterialThemeState

val Primary = Color(0xFF687988)
val Secondary = Color(0xFF72787E)

/**
 * MelodistTheme — Refactorizado para usar MaterialKolor.
 */
@Composable
fun MelodistTheme(
    artworkColors: ArtworkColors? = null,
    content: @Composable () -> Unit,
) {
    val themeMode by AppPreferences.themeMode.collectAsState()
    val dynamicEnabled by AppPreferences.dynamicColorFromArtwork.collectAsState()

    val isDarkTheme = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    // Usar colores de la carátula si el color dinámico está habilitado, 
    // de lo contrario usar los colores por defecto definidos arriba.
    val seedPrimary = if (dynamicEnabled && artworkColors != null && artworkColors != ArtworkColors.Default) {
        artworkColors.vibrant
    } else {
        Primary
    }

    val seedSecondary = if (dynamicEnabled && artworkColors != null && artworkColors != ArtworkColors.Default) {
        artworkColors.muted
    } else {
        Secondary
    }

    val dynamicThemeState = rememberDynamicMaterialThemeState(
        isDark = isDarkTheme,
        style = PaletteStyle.Content,
        primary = seedPrimary,
        secondary = seedSecondary,
    )

    DynamicMaterialTheme(
        state = dynamicThemeState,
        animate = true,
        content = content,
    )
}

