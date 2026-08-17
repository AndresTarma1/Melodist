package example.nucleus.ui.components.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import example.nucleus.data.repository.LyricsAnimationStyle
import example.nucleus.lyrics.LyricLine
import example.nucleus.lyrics.Romanizer
import example.nucleus.ui.themes.LocalMiniPlayerInset
import example.nucleus.ui.themes.expressiveScrollDuration
import example.nucleus.ui.themes.expressiveTween
import example.nucleus.ui.themes.uiTween
import example.nucleus.utils.LocalAnimationsEnabled
import example.nucleus.utils.LocalUserPreferences
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlin.math.abs

/**
 * Letras sincronizadas al estilo karaoke: la línea activa se resalta (con relleno por palabra
 * cuando existen tiempos por palabra), la vista se desplaza automáticamente para mantenerla
 * centrada y al tocar una línea se busca esa posición.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SyncedLyricsView(
    lines: List<LyricLine>,
    positionMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    textAlign: Boolean = false, // false = centrado, true = alineado al inicio
) {
    val userPreferences = LocalUserPreferences.current
    val textSize by userPreferences.lyricsTextSize.collectAsState(40f)
    val lineSpacing by userPreferences.lyricsLineSpacing.collectAsState(54f)
    val animationStyle by userPreferences.lyricsAnimationStyle.collectAsState(LyricsAnimationStyle.KARAOKE)
    val romanizeEnabled by userPreferences.lyricsRomanize.collectAsState(false)
    val animationsEnabled = LocalAnimationsEnabled.current

    val listState = rememberLazyListState()
    val linesIdentity = remember(lines) { lines.firstOrNull()?.timeMs to lines.size }

    // Última línea cuyo tiempo de inicio es <= posición actual.
    val activeIndex = remember(lines, positionMs) {
        var idx = -1
        for (i in lines.indices) {
            if (lines[i].timeMs <= positionMs) idx = i else break
        }
        idx
    }
    val latestActiveIndex by rememberUpdatedState(activeIndex)

    Box(modifier = modifier) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val viewportPx = with(LocalDensity.current) { maxHeight.toPx() }
            val topAnchorPx = viewportPx * 0.34f

            // Al cambiar de canción / set de letras: ir al inicio sin animar (evita arrastre residual).
            LaunchedEffect(linesIdentity) {
                listState.scrollToItem(0, 0)
            }

            // Scroll suave hacia la línea activa. Preferimos animateScrollBy sobre el ítem visible
            // (interpolación continua); si no está en viewport, saltamos cerca y luego afinamos.
            LaunchedEffect(activeIndex, linesIdentity) {
                val target = latestActiveIndex
                if (target < 0 || lines.isEmpty()) return@LaunchedEffect

                val scrollAnim = uiTween<Float>(
                    animationsEnabled = animationsEnabled,
                    durationMillis = expressiveScrollDuration,
                )

                suspend fun deltaToAnchor(index: Int): Float? {
                    val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
                        ?: return null
                    return item.offset - topAnchorPx
                }

                var delta = deltaToAnchor(target)
                if (delta == null) {
                    // Traer el ítem cerca del ancla sin animación larga; luego suavizar.
                    listState.scrollToItem(target, -topAnchorPx.toInt())
                    // Esperar a que el layout exponga el ítem.
                    snapshotFlow { listState.layoutInfo.visibleItemsInfo.any { it.index == target } }
                        .filter { it }
                        .first()
                    delta = deltaToAnchor(target)
                }

                val distance = delta ?: return@LaunchedEffect
                if (abs(distance) < 1.5f) return@LaunchedEffect

                // Si el salto es enorme (seek manual lejano), acortar un poco la sensación de viaje.
                val spec = if (abs(distance) > viewportPx * 1.8f && animationsEnabled) {
                    expressiveTween<Float>(durationMillis = 380)
                } else {
                    scrollAnim
                }
                listState.animateScrollBy(distance, animationSpec = spec)
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(
                    top = 24.dp,
                    bottom = maxHeight * 0.74f + LocalMiniPlayerInset.current,
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                itemsIndexed(
                    items = lines,
                    key = { i, line -> "${line.timeMs}-$i-${line.text.hashCode()}" },
                ) { i, line ->
                    LyricLineRow(
                        line = line,
                        positionMs = positionMs,
                        isActive = i == activeIndex,
                        isPast = i < activeIndex,
                        startAligned = textAlign,
                        activeTextSize = textSize,
                        lineSpacing = lineSpacing,
                        animationStyle = animationStyle,
                        romanize = romanizeEnabled,
                        onClick = { onSeek(line.timeMs) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LyricLineRow(
    line: LyricLine,
    positionMs: Long,
    isActive: Boolean,
    isPast: Boolean,
    startAligned: Boolean,
    activeTextSize: Float,
    lineSpacing: Float,
    animationStyle: LyricsAnimationStyle,
    romanize: Boolean,
    onClick: () -> Unit,
) {
    val activeColor = MaterialTheme.colorScheme.onSurface
    val sungColor = MaterialTheme.colorScheme.primary
    val idleColor = MaterialTheme.colorScheme.onSurface.copy(alpha = if (isPast) 0.24f else 0.42f)
    val animationsEnabled = LocalAnimationsEnabled.current

    val useScale = animationStyle == LyricsAnimationStyle.KARAOKE
    val useAlpha = animationStyle != LyricsAnimationStyle.NONE

    // Ease suave (sin spring/rebote) para jerarquía de línea activa.
    val motionSpec = uiTween<Float>(animationsEnabled, durationMillis = 280)

    val rowAlpha by animateFloatAsState(
        targetValue = when {
            !useAlpha -> 1f
            isActive -> 1f
            isPast -> 0.68f
            else -> 0.88f
        },
        animationSpec = motionSpec,
        label = "lyricAlpha",
    )

    val scale by animateFloatAsState(
        targetValue = if (!useScale) 1f else if (isActive) 1.03f else 0.99f,
        animationSpec = motionSpec,
        label = "lyricScale",
    )

    // Tamaño de fuente animado (antes saltaba de golpe al activar la línea).
    val fontSizeSp by animateFloatAsState(
        targetValue = if (isActive) activeTextSize else (activeTextSize - 5f).coerceAtLeast(12f),
        animationSpec = motionSpec,
        label = "lyricFontSize",
    )

    val weightProgress by animateFloatAsState(
        targetValue = if (isActive) 1f else 0f,
        animationSpec = motionSpec,
        label = "lyricWeight",
    )

    val baseModifier = Modifier
        .fillMaxWidth()
        .clip(MaterialTheme.shapes.medium)
        .background(
            color = Color.Transparent,
            shape = MaterialTheme.shapes.medium,
        )
        .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        )
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            alpha = rowAlpha
            transformOrigin = TransformOrigin(0.5f, 0.5f)
        }
        .padding(horizontal = 14.dp, vertical = 8.dp)

    // Glow: sombra con el color primario sobre la línea activa.
    val rowModifier = if (isActive && animationStyle == LyricsAnimationStyle.GLOW) {
        baseModifier.shadow(
            elevation = 10.dp,
            shape = MaterialTheme.shapes.medium,
            clip = false,
            ambientColor = sungColor.copy(alpha = 0.45f),
            spotColor = sungColor.copy(alpha = 0.45f),
        )
    } else {
        baseModifier
    }

    val style = MaterialTheme.typography.headlineMedium.copy(
        fontSize = fontSizeSp.sp,
        lineHeight = lineSpacing.sp,
        fontWeight = if (weightProgress > 0.55f) FontWeight.Bold else FontWeight.SemiBold,
    )
    val arrangement = if (startAligned) Arrangement.Start else Arrangement.Center

    // Romanización (JA/KO/RU) de la línea, calculada una sola vez por línea.
    val romanized = remember(line.text, romanize) {
        if (romanize) Romanizer.romanize(line.text) else null
    }

    if (line.text.isBlank()) {
        // Línea vacía tipo espaciador (por ejemplo, espacio instrumental)
        Box(rowModifier.padding(vertical = 2.dp)) {
            Text("♪", style = style, color = if (isActive) sungColor else idleColor)
        }
        return
    }

    if (isActive && line.words.isNotEmpty()) {
        // Relleno de karaoke por palabra.
        FlowRow(
            modifier = rowModifier,
            horizontalArrangement = arrangement,
        ) {
            line.words.forEach { w ->
                val color = when {
                    positionMs >= w.endMs -> sungColor
                    positionMs >= w.startMs -> {
                        val frac = ((positionMs - w.startMs).toFloat() /
                            (w.endMs - w.startMs).coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
                        lerp(activeColor, sungColor, frac)
                    }
                    else -> activeColor
                }
                Text(
                    text = w.text + " ",
                    style = style,
                    color = color,
                )
            }
        }
    } else {
        Text(
            text = line.text,
            style = style,
            color = if (isActive) activeColor else idleColor,
            modifier = rowModifier,
            textAlign = if (startAligned) TextAlign.Start else TextAlign.Center,
        )
    }

    if (romanized != null) {
        Text(
            text = romanized,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = (activeTextSize - 22f).coerceAtLeast(11f).sp,
                lineHeight = (lineSpacing - 22f).coerceAtLeast(14f).sp,
                fontWeight = FontWeight.Medium,
            ),
            color = if (isActive) sungColor.copy(alpha = 0.8f) else idleColor.copy(alpha = 0.6f),
            textAlign = if (startAligned) TextAlign.Start else TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
        )
    }
}
