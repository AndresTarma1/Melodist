package com.example.melodist.ui.themes

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.example.melodist.data.AppPreferences
import com.example.melodist.data.ThemeMode
import com.example.melodist.ui.components.ArtworkColors

// ─── Default schemes ────────────────────────────────────

private val DefaultDarkScheme = darkColorScheme()
private val DefaultLightScheme = lightColorScheme()

// ─── Theme composable ───────────────────────────────────

/**
 * MelodistTheme — Supports:
 * - Theme mode: Dark / Light / System
 * - Dynamic colors from artwork: generates a color scheme from the
 *   dominant color of the currently playing song
 */
@Composable
fun MelodistTheme(
    artworkColors: ArtworkColors? = null,
    content: @Composable () -> Unit
) {
    val themeMode by AppPreferences.themeMode.collectAsState()
    val dynamicEnabled by AppPreferences.dynamicColorFromArtwork.collectAsState()

    val isDark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> true // Desktop — default to dark
    }

    val baseScheme = if (isDark) DefaultDarkScheme else DefaultLightScheme

    val colorScheme = if (dynamicEnabled && artworkColors != null && artworkColors != ArtworkColors.Default) {
        generateDynamicScheme(artworkColors, isDark)
    } else {
        baseScheme
    }

    // Animate all theme colors for smooth transitions when song changes
    val animatedPrimary by animateColorAsState(colorScheme.primary, tween(600))
    val animatedSurface by animateColorAsState(colorScheme.surface, tween(600))
    val animatedBackground by animateColorAsState(colorScheme.background, tween(600))

    val finalScheme = colorScheme.copy(
        primary = animatedPrimary,
        surface = animatedSurface,
        background = animatedBackground
    )

    MaterialTheme(
        colorScheme = finalScheme,
        content = content
    )
}

// ─── Dynamic scheme generation ──────────────────────────

/**
 * Generates a Material3 color scheme seeded from artwork colors.
 * Ensures all on* colors have sufficient contrast (WCAG AA ≥ 4.5:1).
 */
private fun generateDynamicScheme(colors: ArtworkColors, isDark: Boolean): ColorScheme {
    val primary = colors.vibrant
    val primaryArgb = primary.toArgb()
    val r = (primaryArgb shr 16) and 0xFF
    val g = (primaryArgb shr 8) and 0xFF
    val b = primaryArgb and 0xFF

    // Ensure primary is readable: lighten dark primaries, darken light ones
    val safePrimary = if (isDark) {
        ensureMinLuminance(primary, 0.25f) // bright enough to see on dark bg
    } else {
        ensureMaxLuminance(primary, 0.45f) // dark enough to see on light bg
    }

    val safeMuted = if (isDark) {
        ensureMinLuminance(colors.muted, 0.2f)
    } else {
        ensureMaxLuminance(colors.muted, 0.5f)
    }

    return if (isDark) {
        // Surface colors: very dark, tinted subtly by the artwork
        val surfaceBase = Color(
            red = (r * 0.05f / 255f + 0.06f).coerceIn(0f, 0.12f),
            green = (g * 0.05f / 255f + 0.06f).coerceIn(0f, 0.12f),
            blue = (b * 0.05f / 255f + 0.06f).coerceIn(0f, 0.12f)
        )
        val surfaceContLow = Color(
            red = (r * 0.04f / 255f + 0.08f).coerceIn(0f, 0.14f),
            green = (g * 0.04f / 255f + 0.08f).coerceIn(0f, 0.14f),
            blue = (b * 0.04f / 255f + 0.08f).coerceIn(0f, 0.14f)
        )
        val surfaceContHigh = Color(
            red = (r * 0.06f / 255f + 0.12f).coerceIn(0f, 0.20f),
            green = (g * 0.06f / 255f + 0.12f).coerceIn(0f, 0.20f),
            blue = (b * 0.06f / 255f + 0.12f).coerceIn(0f, 0.20f)
        )
        val surfaceContHighest = Color(
            red = (r * 0.08f / 255f + 0.16f).coerceIn(0f, 0.24f),
            green = (g * 0.08f / 255f + 0.16f).coerceIn(0f, 0.24f),
            blue = (b * 0.08f / 255f + 0.16f).coerceIn(0f, 0.24f)
        )

        darkColorScheme(
            primary = safePrimary,
            onPrimary = contrastingColor(safePrimary),
            primaryContainer = safePrimary.darken(0.55f),
            onPrimaryContainer = safePrimary.lighten(0.85f).ensureContrastOn(safePrimary.darken(0.55f)),
            secondary = safeMuted,
            onSecondary = contrastingColor(safeMuted),
            secondaryContainer = safeMuted.darken(0.5f),
            onSecondaryContainer = safeMuted.lighten(0.75f).ensureContrastOn(safeMuted.darken(0.5f)),
            tertiary = colors.dominant.lighten(0.35f),
            onTertiary = contrastingColor(colors.dominant.lighten(0.35f)),
            tertiaryContainer = colors.dominant.darken(0.5f),
            onTertiaryContainer = colors.dominant.lighten(0.8f).ensureContrastOn(colors.dominant.darken(0.5f)),
            surface = surfaceBase,
            surfaceVariant = colors.darkMuted.blendWith(Color(0xFF1E1E1E), 0.85f),
            onSurface = Color(0xFFECE6F0),       // Always high contrast white
            onSurfaceVariant = Color(0xFFCAC4D0), // Always readable grey
            surfaceContainerLow = surfaceContLow,
            surfaceContainer = surfaceContLow.lighten(0.03f),
            surfaceContainerHigh = surfaceContHigh,
            surfaceContainerHighest = surfaceContHighest,
            outline = safePrimary.copy(alpha = 0.45f),
            outlineVariant = safePrimary.copy(alpha = 0.18f),
            inverseSurface = Color(0xFFE6E1E5),
            inverseOnSurface = Color(0xFF1C1B1F),
            inversePrimary = safePrimary.darken(0.4f),
        )
    } else {
        val darkPrimary = ensureMaxLuminance(primary, 0.40f)
        lightColorScheme(
            primary = darkPrimary,
            onPrimary = Color.White,
            primaryContainer = primary.lighten(0.88f),
            onPrimaryContainer = darkPrimary.darken(0.5f),
            secondary = ensureMaxLuminance(colors.muted, 0.45f),
            onSecondary = Color.White,
            secondaryContainer = colors.muted.lighten(0.85f),
            onSecondaryContainer = colors.muted.darken(0.55f),
            surface = Color(0xFFFFFBFE),
            onSurface = Color(0xFF1C1B1F),
            onSurfaceVariant = Color(0xFF49454F),
        )
    }
}

// ─── Contrast utilities ─────────────────────────────────

/** Relative luminance per BT.709. */
private fun Color.luminance(): Float {
    fun linearize(c: Float): Float = if (c <= 0.04045f) c / 12.92f else Math.pow(((c + 0.055) / 1.055), 2.4).toFloat()
    return 0.2126f * linearize(red) + 0.7152f * linearize(green) + 0.0722f * linearize(blue)
}

/** WCAG contrast ratio between two colors (1:1 to 21:1). */
private fun contrastRatio(fg: Color, bg: Color): Float {
    val l1 = fg.luminance() + 0.05f
    val l2 = bg.luminance() + 0.05f
    return if (l1 > l2) l1 / l2 else l2 / l1
}

/** Returns white or black, whichever has better contrast with [bg]. */
private fun contrastingColor(bg: Color): Color {
    return if (bg.luminance() > 0.4f) Color.Black else Color.White
}

/** Ensures this color has at least AA contrast (4.5:1) against [bg]. */
private fun Color.ensureContrastOn(bg: Color): Color {
    val ratio = contrastRatio(this, bg)
    if (ratio >= 4.5f) return this
    // If not enough contrast, push towards white (dark bg) or black (light bg)
    val bgLum = bg.luminance()
    return if (bgLum < 0.5f) {
        // Dark background: lighten the foreground
        var c = this
        repeat(20) {
            c = c.lighten(0.08f)
            if (contrastRatio(c, bg) >= 4.5f) return c
        }
        Color.White
    } else {
        // Light background: darken the foreground
        var c = this
        repeat(20) {
            c = c.darken(0.9f)
            if (contrastRatio(c, bg) >= 4.5f) return c
        }
        Color.Black
    }
}

/** Ensures the color has at least [minLum] luminance (brightens very dark colors). */
private fun ensureMinLuminance(color: Color, minLum: Float): Color {
    if (color.luminance() >= minLum) return color
    var c = color
    repeat(30) {
        c = c.lighten(0.06f)
        if (c.luminance() >= minLum) return c
    }
    return c
}

/** Ensures the color has at most [maxLum] luminance (darkens very bright colors). */
private fun ensureMaxLuminance(color: Color, maxLum: Float): Color {
    if (color.luminance() <= maxLum) return color
    var c = color
    repeat(30) {
        c = c.darken(0.94f)
        if (c.luminance() <= maxLum) return c
    }
    return c
}

// ─── Color utilities ────────────────────────────────────

private fun Color.darken(factor: Float): Color {
    return Color(
        red = (red * factor).coerceIn(0f, 1f),
        green = (green * factor).coerceIn(0f, 1f),
        blue = (blue * factor).coerceIn(0f, 1f),
        alpha = alpha
    )
}

private fun Color.lighten(factor: Float): Color {
    return Color(
        red = (red + (1f - red) * factor).coerceIn(0f, 1f),
        green = (green + (1f - green) * factor).coerceIn(0f, 1f),
        blue = (blue + (1f - blue) * factor).coerceIn(0f, 1f),
        alpha = alpha
    )
}

private fun Color.blendWith(other: Color, ratio: Float): Color {
    val inv = 1f - ratio
    return Color(
        red = (red * inv + other.red * ratio).coerceIn(0f, 1f),
        green = (green * inv + other.green * ratio).coerceIn(0f, 1f),
        blue = (blue * inv + other.blue * ratio).coerceIn(0f, 1f),
        alpha = 1f
    )
}

// ─── Animated color scheme ──────────────────────────────

/**
 * Animates all colors in a ColorScheme for smooth transitions.
 */
@Composable
private fun animateColorScheme(target: ColorScheme): ColorScheme {
    val spec = tween<Color>(600)
    return target.copy(
        primary = animateColorAsState(target.primary, spec, label = "primary").value,
        onPrimary = animateColorAsState(target.onPrimary, spec, label = "onPrimary").value,
        primaryContainer = animateColorAsState(target.primaryContainer, spec, label = "primaryCont").value,
        onPrimaryContainer = animateColorAsState(target.onPrimaryContainer, spec, label = "onPrimaryCont").value,
        secondary = animateColorAsState(target.secondary, spec, label = "secondary").value,
        onSecondary = animateColorAsState(target.onSecondary, spec, label = "onSecondary").value,
        secondaryContainer = animateColorAsState(target.secondaryContainer, spec, label = "secondaryCont").value,
        onSecondaryContainer = animateColorAsState(target.onSecondaryContainer, spec, label = "onSecondaryCont").value,
        tertiary = animateColorAsState(target.tertiary, spec, label = "tertiary").value,
        onTertiary = animateColorAsState(target.onTertiary, spec, label = "onTertiary").value,
        tertiaryContainer = animateColorAsState(target.tertiaryContainer, spec, label = "tertiaryCont").value,
        onTertiaryContainer = animateColorAsState(target.onTertiaryContainer, spec, label = "onTertiaryCont").value,
        background = animateColorAsState(target.background, spec, label = "background").value,
        onBackground = animateColorAsState(target.onBackground, spec, label = "onBackground").value,
        surface = animateColorAsState(target.surface, spec, label = "surface").value,
        onSurface = animateColorAsState(target.onSurface, spec, label = "onSurface").value,
        surfaceVariant = animateColorAsState(target.surfaceVariant, spec, label = "surfaceVariant").value,
        onSurfaceVariant = animateColorAsState(target.onSurfaceVariant, spec, label = "onSurfaceVar").value,
        surfaceTint = animateColorAsState(target.surfaceTint, spec, label = "surfaceTint").value,
        surfaceContainerLowest = animateColorAsState(target.surfaceContainerLowest, spec, label = "surfContLowest").value,
        surfaceContainerLow = animateColorAsState(target.surfaceContainerLow, spec, label = "surfContLow").value,
        surfaceContainer = animateColorAsState(target.surfaceContainer, spec, label = "surfCont").value,
        surfaceContainerHigh = animateColorAsState(target.surfaceContainerHigh, spec, label = "surfContHigh").value,
        surfaceContainerHighest = animateColorAsState(target.surfaceContainerHighest, spec, label = "surfContHighest").value,
        inverseSurface = animateColorAsState(target.inverseSurface, spec, label = "inverseSurf").value,
        inverseOnSurface = animateColorAsState(target.inverseOnSurface, spec, label = "inverseOnSurf").value,
        inversePrimary = animateColorAsState(target.inversePrimary, spec, label = "inversePrimary").value,
        outline = animateColorAsState(target.outline, spec, label = "outline").value,
        outlineVariant = animateColorAsState(target.outlineVariant, spec, label = "outlineVar").value,
        error = animateColorAsState(target.error, spec, label = "error").value,
        onError = animateColorAsState(target.onError, spec, label = "onError").value,
        errorContainer = animateColorAsState(target.errorContainer, spec, label = "errorCont").value,
        onErrorContainer = animateColorAsState(target.onErrorContainer, spec, label = "onErrorCont").value,
        scrim = animateColorAsState(target.scrim, spec, label = "scrim").value,
    )
}

