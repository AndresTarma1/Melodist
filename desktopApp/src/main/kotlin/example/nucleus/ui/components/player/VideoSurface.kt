package example.nucleus.ui.components.player

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import example.nucleus.player.MpvVideoRenderer
import kotlinx.coroutines.isActive
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Image as SkiaImage

/**
 * Superficie de video: sondea el [MpvVideoRenderer] en cada vsync y dibuja el frame BGRA
 * publicado más reciente. Sin buffer propio de píxeles: cada frame nuevo se copia a un
 * ByteArray reutilizado y se envuelve en un ImageBitmap (Skia envuelve sin copiar).
 */
@Composable
fun VideoSurface(
    renderer: MpvVideoRenderer?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
) {
    var frame by remember(renderer) { mutableStateOf<ImageBitmap?>(null) }
    val frameBuffer = remember(renderer) { VideoFrameBuffer() }

    LaunchedEffect(renderer) {
        if (renderer == null) return@LaunchedEffect
        var seenVersion = -1L
        while (isActive) {
            val version = renderer.frameVersion
            if (version != seenVersion) {
                seenVersion = version
                val size = renderer.frameSize
                if (size != null) {
                    val bytes = frameBuffer.bufferFor(size)
                    val read = renderer.readFrame(bytes)
                    if (read != null) {
                        frame = bytesToImageBitmap(bytes, read.first, read.second)
                    }
                }
            }
            withFrameNanos { }
        }
    }

    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        val current = frame
        if (current != null) {
            Image(
                bitmap = current,
                contentDescription = null,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Buffer de píxeles reutilizado para copiar frames del renderer sin re-alocar por frame. */
private class VideoFrameBuffer {
    private var bytes: ByteArray? = null

    fun bufferFor(size: Pair<Int, Int>): ByteArray {
        val needed = size.first * size.second * 4
        val current = bytes
        if (current == null || current.size != needed) {
            bytes = ByteArray(needed)
        }
        return bytes!!
    }
}

private fun bytesToImageBitmap(bytes: ByteArray, width: Int, height: Int): ImageBitmap {
    // Copia poseída (array fresco por frame) para que el ImageBitmap no comparta el buffer
    // reutilizado por el llamador; evita tearing cuando el buffer se sobreescribe en el
    // siguiente frame.
    val owned = bytes.copyOf()
    val bitmap = Bitmap()
    val info = ImageInfo(
        width,
        height,
        ColorType.BGRA_8888,
        ColorAlphaType.OPAQUE,
    )
    bitmap.installPixels(info, owned, width * 4)
    return SkiaImage.makeFromBitmap(bitmap).toComposeImageBitmap()
}
