package com.ghost.tagger.ui.section

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ghost.tagger.ui.state.GalleryUiState
import com.ghost.tagger.ui.state.SettingsUiState
import com.ghost.tagger.ui.viewmodels.GalleryViewModel

@Composable
fun ImageDetailArea(
    modifier: Modifier = Modifier,
    width: Dp,
    isBatch: Boolean,
    isPanelVisible: Boolean,
    onClearSelection: () -> Unit,
    onCloseImagePreview: () -> Unit,
) {

    AnimatedVisibility(
        visible = isPanelVisible,
        enter = slideInHorizontally(
            // start the content off‑screen to the right
            initialOffsetX = { fullWidth -> fullWidth }
        ),
        exit = slideOutHorizontally(
            // slide the content off‑screen to the right
            targetOffsetX = { fullWidth -> fullWidth }
        )
    ){
        when (isBatch) {
            true -> BatchDetailPanel(
                onClose = onClearSelection,
                modifier = modifier.width(width)
            )
            false -> ImageDetailPreview(
                modifier = modifier.width(width),
                onClose = onCloseImagePreview
            )
        }
    }

}
