package example.nucleus.ui.components.player

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Hero cover element para la transición de la carátula entre el MiniPlayer y NowPlaying.
 *
 * DESACTIVADO (no-op): la transición `sharedElement`/`sharedBounds` de Compose tiembla con el runtime
 * de ventana Tao (el overlay se interpola con frame pacing irregular y la imagen "tiembla" al abrir
 * NowPlaying). En su lugar, NowPlaying hace una entrada simple de escala+fade (ver NowPlayingLayouts).
 * La firma se mantiene para no tocar los llamadores.
 */
@Composable
fun Modifier.heroCoverElement(
    songId: String,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
): Modifier = this
