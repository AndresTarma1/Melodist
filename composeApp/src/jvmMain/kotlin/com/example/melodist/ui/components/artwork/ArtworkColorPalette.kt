package com.example.melodist.ui.components

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

private val artworkColorCache = object : LinkedHashMap<String, ArtworkColors>(32, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ArtworkColors>?): Boolean = size > 50
}

/**
 * Remembers dominant colors extracted from an image URL.
 */
@Composable
fun rememberArtworkColors(url: String?): ArtworkColors {
    var colors by remember { mutableStateOf(ArtworkColors.Default) }
    val currentUrl = url

    LaunchedEffect(currentUrl) {
        if (currentUrl.isNullOrBlank()) {
            colors = ArtworkColors.Default
            return@LaunchedEffect
        }
        val cached = synchronized(artworkColorCache) { artworkColorCache[currentUrl] }
        if (cached != null) {
            colors = cached
            return@LaunchedEffect
        }
        try {
            val extracted = withContext(Dispatchers.IO) { extractColorsFromUrl(currentUrl) }
            synchronized(artworkColorCache) { artworkColorCache[currentUrl] = extracted }
            colors = extracted
        } catch (_: Exception) {
            colors = ArtworkColors.Default
        }
    }

    return colors
}

private fun extractColorsFromUrl(url: String): ArtworkColors {
    // 1. Redimensionar antes de procesar (esencial para el rendimiento)
    val image: BufferedImage = URI(url).toURL().openStream().use { stream ->
        val original = ImageIO.read(stream) ?: return ArtworkColors.Default
        // Escalamos a 32x32: es suficiente para extraer colores y muy rápido
        val resized = BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB)
        val g = resized.createGraphics()
        g.drawImage(original, 0, 0, 32, 32, null)
        g.dispose()
        resized
    }

    // 2. Usar un IntArray para evitar crear miles de objetos 'Triple'
    val pixels = IntArray(32 * 32)
    image.getRGB(0, 0, 32, 32, pixels, 0, 32)

    val pixelList = pixels.map { rgb ->
        val r = (rgb shr 16) and 0xFF
        val g = (rgb shr 8) and 0xFF
        val b = rgb and 0xFF
        Triple(r, g, b)
    }

    // 3. Procesar clusters (Tu lógica de kMeans se mantiene igual)
    val clusters = kMeansClusters(pixelList, k = 4, iterations = 8)
    val sorted = clusters.sortedByDescending { it.count }

    val dominant = sorted[0].toColor()
    val vibrant = sorted.maxByOrNull { it.saturation() }?.toColor() ?: dominant
    val muted = sorted.minByOrNull { it.saturation() }?.toColor() ?: dominant
    val darkMuted = sorted.minByOrNull { it.luminance() }?.toColor() ?: dominant

    return ArtworkColors(
        dominant = dominant,
        vibrant = vibrant,
        muted = muted,
        darkMuted = darkMuted,
        isLight = sorted[0].luminance() > 0.55f
    )
}

private data class Cluster(
    var r: Float,
    var g: Float,
    var b: Float,
    var count: Int = 0,
    var sumR: Float = 0f,
    var sumG: Float = 0f,
    var sumB: Float = 0f
) {
    fun toColor(): Color = Color(
        red = (r / 255f).coerceIn(0f, 1f),
        green = (g / 255f).coerceIn(0f, 1f),
        blue = (b / 255f).coerceIn(0f, 1f)
    )

    fun saturation(): Float {
        val rf = r / 255f
        val gf = g / 255f
        val bf = b / 255f
        val max = maxOf(rf, gf, bf)
        val min = minOf(rf, gf, bf)
        return if (max == 0f) 0f else (max - min) / max
    }

    fun luminance(): Float = 0.2126f * (r / 255f) + 0.7152f * (g / 255f) + 0.0722f * (b / 255f)

    fun reset() {
        count = 0
        sumR = 0f
        sumG = 0f
        sumB = 0f
    }

    fun update() {
        if (count > 0) {
            r = sumR / count
            g = sumG / count
            b = sumB / count
        }
    }
}

private fun kMeansClusters(
    pixels: List<Triple<Int, Int, Int>>,
    k: Int,
    iterations: Int
): List<Cluster> {
    val step = maxOf(1, pixels.size / k)
    val sortedByLum = pixels.sortedBy { (r, g, b) -> 0.2126f * r + 0.7152f * g + 0.0722f * b }
    val clusters = (0 until k).map { i ->
        val idx = (i * step).coerceAtMost(sortedByLum.lastIndex)
        val (r, g, b) = sortedByLum[idx]
        Cluster(r.toFloat(), g.toFloat(), b.toFloat())
    }

    repeat(iterations) {
        clusters.forEach { it.reset() }
        for ((r, g, b) in pixels) {
            var minDist = Float.MAX_VALUE
            var nearest = clusters[0]
            for (c in clusters) {
                val dr = r - c.r
                val dg = g - c.g
                val db = b - c.b
                val dist = dr * dr + dg * dg + db * db
                if (dist < minDist) {
                    minDist = dist
                    nearest = c
                }
            }
            nearest.count++
            nearest.sumR += r
            nearest.sumG += g
            nearest.sumB += b
        }
        clusters.forEach { it.update() }
    }

    return clusters
}

