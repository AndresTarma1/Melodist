package example.nucleus.ui.themes

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.SystemFont
import java.awt.GraphicsEnvironment

/**
 * Nombres de las familias de fuentes instaladas en el sistema, ordenados alfabéticamente.
 * AWT se usa solo para enumerarlas; el renderizado lo sigue haciendo Compose/Skia, así que
 * funciona igual con el backend Tao.
 */
fun systemFontNames(): List<String> =
    GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames.sorted()

/** [FontFamily] que apunta a una familia instalada en el sistema. Nombre vacío → la predeterminada. */
@OptIn(ExperimentalTextApi::class)
fun systemFontFamily(name: String): FontFamily {
    if (name.isBlank()) return FontFamily.Default
    return FontFamily(
        SystemFont(name, FontWeight.Normal),
        SystemFont(name, FontWeight.Medium),
        SystemFont(name, FontWeight.SemiBold),
        SystemFont(name, FontWeight.Bold),
    )
}
