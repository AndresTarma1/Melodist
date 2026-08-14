package example.nucleus.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Tarjeta estilo YouTube Music: fondo de la tarjeta + una franja vertical de color en el borde
 * izquierdo y la etiqueta en negrita. Se usa en la sección de estados de ánimo y géneros.
 */
@Composable
fun CustomLabeledCard(
    text: String,
    borderColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cornerRadius = 16.dp

    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(cornerRadius),
        modifier = modifier
            // REEMPLAZO: Cambiamos fillMaxWidth por un ancho fijo ideal para escritorio
            .width(260.dp)
            .height(80.dp)
            .pointerHoverIcon(PointerIcon.Hand),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxSize(),
        ) {
            // Franja vertical de color en el borde izquierdo.
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(10.dp)
                    .background(
                        color = borderColor,
                        shape = RoundedCornerShape(
                            topStart = cornerRadius,
                            bottomStart = cornerRadius,
                        ),
                    ),
            )

            Spacer(Modifier.width(20.dp))

            Text(
                text = text,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = 16.dp),
            )
        }
    }
}