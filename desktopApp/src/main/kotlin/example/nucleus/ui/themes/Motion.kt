package example.nucleus.ui.themes

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * Motion de Material 3 Expressive: muelles con overshoot moderado para transiciones
 * de "énfasis" (entradas de pantallas, mini reproductor, tabs), y un muelle rápido
 * sin rebote para interacciones (hover/press). Se combinan siempre con la preferencia
 * de animaciones del usuario.
 *
 * Son funciones genéricas para poder usarlas con cualquier tipo animado
 * (Float, IntSize, IntOffset, Dp...).
 */

/** Muelle expresivo — overshoot moderado, el sello de M3E. */
fun <T> expressiveSpring(): SpringSpec<T> = spring(
    dampingRatio = 0.55f,
    stiffness = Spring.StiffnessMedium,
)

/** Muelle rápido y contenido para estados de interacción. */
fun <T> interactionSpring(): SpringSpec<T> = spring(
    dampingRatio = Spring.DampingRatioMediumBouncy,
    stiffness = Spring.StiffnessHigh,
)

/** Duración corta para fundidos que acompañan a los muelles (220ms). */
const val expressiveFadeDuration = 220

fun expressiveFadeTween() = tween<Float>(durationMillis = expressiveFadeDuration)
