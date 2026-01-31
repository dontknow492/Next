package com.ghost.tagger.ui.components

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import java.awt.Cursor

@Composable
fun VerticalDraggableSplitter(
    modifier: Modifier = Modifier,
    onResize: (Float) -> Unit, // Renamed for clarity
    color: Color = MaterialTheme.colorScheme.outlineVariant
) {
    // 1. Get the current screen density
    val density = LocalDensity.current

    Box(
        modifier = modifier
            .width(10.dp)
            .fillMaxHeight()
            .pointerHoverIcon(PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR)))
            .draggable(
                state = rememberDraggableState { deltaPixels ->
                    // 2. CONVERSION: Pixels -> Dp
                    // "deltaPixels" is how many physical dots the mouse moved.
                    // We divide by density to get the "Dp" equivalent.
                    val deltaDp = with(density) { deltaPixels.toDp().value }

                    onResize(deltaDp)
                },
                orientation = Orientation.Horizontal
            )
    ) {
        VerticalDivider(
            modifier = Modifier.align(Alignment.Center),
            color = color
        )
    }
}