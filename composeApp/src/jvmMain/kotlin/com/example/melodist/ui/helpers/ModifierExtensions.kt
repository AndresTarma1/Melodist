package com.example.melodist.ui.helpers

import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpOffset

@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.contextMenuArea(
    enabled: Boolean = true,
    onHoverChange: ((Boolean) -> Unit)? = null,
    onMenuAction: (DpOffset) -> Unit
): Modifier = composed {
    var itemHeight by remember { mutableStateOf(0) }
    val density = LocalDensity.current

    this
        .onGloballyPositioned { itemHeight = it.size.height }
        .onPointerEvent(PointerEventType.Enter) { onHoverChange?.invoke(true) }
        .onPointerEvent(PointerEventType.Exit) { onHoverChange?.invoke(false) }
        .onPointerEvent(PointerEventType.Press) {
            if (enabled && it.button == PointerButton.Secondary) {
                val position = it.changes.first().position
                val xDp = with(density) { position.x.toDp() }
                val yDp = with(density) { (position.y - itemHeight).toDp() }
                onMenuAction(DpOffset(xDp, yDp))
            }
        }
}
