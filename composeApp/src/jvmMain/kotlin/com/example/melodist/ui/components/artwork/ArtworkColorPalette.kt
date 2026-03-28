package com.example.melodist.ui.components.artwork

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.kmpalette.color
import com.kmpalette.loader.rememberNetworkLoader
import com.kmpalette.rememberPaletteState
import io.ktor.http.Url
import java.awt.image.BufferedImage
import java.net.URI
import javax.imageio.ImageIO


/**
 * Extracted color palette from a song's artwork.
 */
data class ArtworkColors(
    val dominant: Color,
    val vibrant: Color,
    val muted: Color,
    val darkMuted: Color,
    val isLight: Boolean
) {
    companion object {
        val Default = ArtworkColors(
            dominant = Color(0xFF1A1A2E),
            vibrant = Color(0xFF6C63FF),
            muted = Color(0xFF2D2D44),
            darkMuted = Color(0xFF121225),
            isLight = false
        )
    }
}

/**
 * CompositionLocal that provides artwork colors from the App level.
 */
val LocalArtworkColors = staticCompositionLocalOf { ArtworkColors.Default }

@Composable
fun rememberArtworkColors(url: String?): ArtworkColors {
    val loader = rememberNetworkLoader()
    val paletteState = rememberPaletteState(loader = loader)

    var colors by remember { mutableStateOf(ArtworkColors.Default) }

    LaunchedEffect(url) {
        if (url.isNullOrBlank()) {
            colors = ArtworkColors.Default
            return@LaunchedEffect
        }

        try {
            paletteState.generate(Url(url))

            val dominant = paletteState.palette?.dominantSwatch?.color
            val vibrant = paletteState.palette?.vibrantSwatch?.color
            val muted = paletteState.palette?.mutedSwatch?.color
            val darkMuted = paletteState.palette?.darkMutedSwatch?.color

            val finalDominant = dominant ?: Color(0xFF1A1A2E)

            colors = ArtworkColors(
                dominant = finalDominant,
                vibrant = vibrant ?: finalDominant,
                muted = muted ?: finalDominant,
                darkMuted = darkMuted ?: finalDominant,
                isLight = finalDominant.luminance() > 0.5f
            )

        } catch (e: Exception) {
            colors = ArtworkColors.Default
        }
    }

    return colors
}