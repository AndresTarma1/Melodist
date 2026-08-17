package example.nucleus.ui.components.player

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import example.nucleus.shared.generated.resources.Res
import example.nucleus.shared.generated.resources.lyrics_loading
import example.nucleus.shared.generated.resources.lyrics_not_found
import example.nucleus.ui.components.layout.AppVerticalScrollbar
import example.nucleus.ui.themes.LocalMiniPlayerInset
import example.nucleus.utils.LocalPlayerViewModel
import example.nucleus.utils.LocalUserPreferences
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LyricsContent(
    lyrics: String?,
    textAlign: TextAlign = TextAlign.Center,
    style: TextStyle
) {
    val playerViewModel = LocalPlayerViewModel.current
    val userPreferences = LocalUserPreferences.current
    val synced by playerViewModel.syncedLyrics.collectAsState()
    val progress by playerViewModel.progressState.collectAsState()
    val loading by playerViewModel.lyricsLoading.collectAsState()
    // Offset solo desde Settings (Now Playing); sin chrome flotante aquí.
    val lyricsOffsetMs by userPreferences.lyricsOffsetMs.collectAsState(0)
    val syncedLines = synced

    val baseSize = if (style.fontSize == TextUnit.Unspecified) 20.sp else style.fontSize
    val baseLineHeight = if (style.lineHeight == TextUnit.Unspecified) 30.sp else style.lineHeight
    val immersiveLyricsStyle = style.copy(
        fontSize = baseSize * 1.7f,
        lineHeight = baseLineHeight * 1.9f,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.12.sp
    )

    // Positivo en prefs = letras más tarde → restamos offset a la posición del audio.
    val adjustedPositionMs = (progress.positionMs - lyricsOffsetMs).coerceAtLeast(0L)

    when {
        !syncedLines.isNullOrEmpty() -> {
            SyncedLyricsView(
                lines = syncedLines,
                positionMs = adjustedPositionMs,
                onSeek = { lineTimeMs ->
                    playerViewModel.seekTo((lineTimeMs + lyricsOffsetMs).coerceAtLeast(0L))
                },
                modifier = Modifier.fillMaxSize(),
                textAlign = textAlign == TextAlign.Start,
            )
        }
        loading || lyrics == null -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                LoadingIndicator()
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(Res.string.lyrics_loading),
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
                    textAlign = TextAlign.Center
                )
            }
        }
        lyrics.isBlank() -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Rounded.Lyrics,
                    contentDescription = null,
                    modifier = Modifier.size(62.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                )
                Spacer(Modifier.height(20.dp))
                Text(
                    stringResource(Res.string.lyrics_not_found),
                    style = MaterialTheme.typography.headlineSmallEmphasized,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
        else -> {
            val scrollState = rememberScrollState()
            val fadeHeight = 72.dp

            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(
                            start = 18.dp,
                            end = 18.dp,
                            bottom = LocalMiniPlayerInset.current
                        )
                        .fadingEdges(fadeHeight)
                ) {
                    Spacer(Modifier.height(18.dp))
                    Text(
                        text = lyrics,
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 920.dp)
                            .align(Alignment.CenterHorizontally),
                        style = immersiveLyricsStyle,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.95f),
                        textAlign = textAlign,
                    )
                    Spacer(Modifier.height(140.dp))
                }
                AppVerticalScrollbar(
                    state = scrollState,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .width(8.dp)
                )
            }
        }
    }
}

private fun Modifier.fadingEdges(fadeHeight: Dp): Modifier = this.then(
    Modifier.drawWithContent {
        drawContent()
        val fadePx = fadeHeight.toPx()
        if (fadePx <= 0f || size.height <= 0f) return@drawWithContent
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, Color.Black),
                startY = 0f,
                endY = fadePx
            ),
            blendMode = BlendMode.DstIn
        )
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Black, Color.Transparent),
                startY = size.height - fadePx,
                endY = size.height
            ),
            blendMode = BlendMode.DstIn
        )
    }
)
