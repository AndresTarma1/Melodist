package example.nucleus.ui.components.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import example.nucleus.data.repository.LyricsAnimationStyle
import example.nucleus.lyrics.LyricLine
import example.nucleus.lyrics.Romanizer
import example.nucleus.ui.themes.LocalMiniPlayerInset
import example.nucleus.utils.LocalAnimationsEnabled
import example.nucleus.utils.LocalUserPreferences

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

    val listState = rememberLazyListState()

    // Última línea cuyo tiempo de inicio es <= posición actual. Se recalcula en cada tick (barato
    // para ~decenas de líneas); El LaunchedEffect a continuación solo vuelve a desplazarse
    // cuando el Int realmente cambia.
    val activeIndex = run {
        var idx = -1
        for (i in lines.indices) {
            if (lines[i].timeMs <= positionMs) idx = i else break
        }
        idx
    }

    Box(modifier = modifier) {
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
            val viewportPx = with(androidx.compose.ui.platform.LocalDensity.current) { maxHeight.toPx() }
            val topAnchorPx = (viewportPx * 0.34f).toInt()

            LaunchedEffect(activeIndex) {
                listState.animateScrollToItem(activeIndex.coerceAtLeast(0), -topAnchorPx)
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(top = 24.dp, bottom = maxHeight * 0.74f + LocalMiniPlayerInset.current),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                itemsIndexed(lines, key = { i, _ -> i }) { i, line ->
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

    val rowAlpha by animateFloatAsState(
        targetValue = when {
            !useAlpha -> 1f
            isActive -> 1f
            isPast -> 0.72f
            else -> 0.9f
        },
        animationSpec = if (animationsEnabled) tween(300) else snap(),
        label = "lyricAlpha"
    )

    val scale by animateFloatAsState(
        targetValue = if (!useScale) 1f else if (isActive) 1.06f else 0.98f,
        animationSpec = if (animationsEnabled) tween(300) else snap(),
        label = "lyricScale"
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
            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.5f)
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
        fontSize = if (isActive) activeTextSize.sp else (activeTextSize - 6f).sp,
        lineHeight = lineSpacing.sp,
        fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
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
