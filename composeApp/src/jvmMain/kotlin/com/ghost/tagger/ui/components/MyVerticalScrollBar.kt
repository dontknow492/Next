package com.ghost.tagger.ui.components

import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.v2.ScrollbarAdapter
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun MyVerticalScrollBar(
    adapter: ScrollbarAdapter,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(12.dp)
            .background(
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f),
                RoundedCornerShape(6.dp)
            )
    ) {
        VerticalScrollbar(
            adapter = adapter,
            style = ScrollbarStyle(
                minimalHeight = 16.dp,
                thickness = 10.dp,
                shape = RoundedCornerShape(6.dp),
                hoverDurationMillis = 300,
                unhoverColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                hoverColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),

                ),
            modifier = Modifier.fillMaxHeight()
        )
    }
}

