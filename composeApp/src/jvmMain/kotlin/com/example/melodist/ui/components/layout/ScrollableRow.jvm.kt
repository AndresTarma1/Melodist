package com.example.melodist.ui.components.layout

import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp


@Composable
fun HorizontalScrollableRow(
    modifier: Modifier = Modifier,
    state: LazyListState,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    content: LazyListScope.() -> Unit
) {
    val scrollbarStyle = appScrollbarStyle()

    Box(modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth()) {
            LazyRow(
                state = state,
                contentPadding = contentPadding,
                horizontalArrangement = horizontalArrangement,
                modifier = Modifier.fillMaxWidth(),
                content = content
            )
            // Espaciador para que la barra no tape el contenido (opcional, ajusta según diseño)
            Spacer(modifier = Modifier.height(12.dp))
        }

        HorizontalScrollbar(
            adapter = rememberScrollbarAdapter(state),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth() // Ocupa todo el ancho para simular la integración nativa
                .padding(horizontal = 12.dp) // Un pequeño margen para que no toque los bordes laterales
                .height(12.dp), // Área de interacción (el track es de 6dp por el style)
            style = scrollbarStyle
        )
    }
}
