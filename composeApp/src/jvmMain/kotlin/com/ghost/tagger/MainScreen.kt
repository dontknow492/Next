package com.ghost.tagger

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ghost.tagger.data.viewmodels.GalleryViewModel
import com.ghost.tagger.data.viewmodels.SettingsViewModel
import com.ghost.tagger.ui.components.DetailAction
import com.ghost.tagger.ui.components.ImageDetailPreview
import com.ghost.tagger.ui.components.LeftNavigationBar
import com.ghost.tagger.ui.components.VerticalDraggableSplitter
import com.ghost.tagger.ui.section.GallerySection
import com.ghost.tagger.ui.section.SettingsSidebar
import org.koin.compose.viewmodel.koinViewModel
import kotlin.collections.firstOrNull

@Composable
fun MainScreen() {
    // 3. koinInject() now works perfectly because it's inside KoinContext
    val settingsViewModel: SettingsViewModel = koinViewModel()
    val currentSettings by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val isSettingVisible = currentSettings.settings.session.isSidebarVisible

    val galleryViewModel: GalleryViewModel = koinViewModel()
    val galleryUiState by galleryViewModel.uiState.collectAsStateWithLifecycle()

    val previewItem = remember(galleryUiState.focusedImageId) {
        if (galleryUiState.focusedImageId == null) null
        else
            galleryUiState.images.firstOrNull {
                it.id == galleryUiState.focusedImageId
            }
    }

    var isPreviewOpen by remember(galleryUiState.focusedImageId) { mutableStateOf(true) }

    Scaffold { innerPadding ->
        Row(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            SettingsSidebar()
            if(isSettingVisible){
                VerticalDraggableSplitter(
                    onResize = { delta ->
                        // Get current width
                        val currentWidth = currentSettings.settings.session.sidePanelWidthDp

                        // Calculate new width (Drag Left = Grow, Drag Right = Shrink)
                        // We typically limit min/max size to prevent UI breaking
                        val newWidth = (currentWidth + delta).coerceIn(200f, 800f)

                        // Call your ViewModel to update the settings
                        settingsViewModel.setSideBarWidth(newWidth)
                    }
                )
            }

            LeftNavigationBar(
                isSettingVisible = isSettingVisible,
                onSettingClick = {
                    settingsViewModel.toggleSideBarVisible()
                }
            )
            // Main Gallery Area
//            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            GallerySection(Modifier.weight(1f).fillMaxHeight())
//            }

            if (previewItem != null) {
                // INSERT SPLITTER HERE
                VerticalDraggableSplitter(
                    onResize = { delta ->
                        // Get current width
                        val currentWidth = currentSettings.settings.session.previewSectionWidthDp

                        // Calculate new width (Drag Left = Grow, Drag Right = Shrink)
                        // We typically limit min/max size to prevent UI breaking
                        val newWidth = (currentWidth - delta).coerceIn(200f, 800f)

                        // Call your ViewModel to update the settings
                        settingsViewModel.setPreviewPanelWidth(newWidth)
                    }
                )

                // 3. The Preview Panel (Fixed dynamic width)
                ImageDetailPreview(
                    modifier = Modifier.width(currentSettings.settings.session.previewSectionWidthDp.dp),
                    image = previewItem,
                    focusedImageId = galleryUiState.focusedImageId,
                    visible = isPreviewOpen,
                    actions = { command ->
                        when (command) {
                            is DetailAction.ClosePreview -> isPreviewOpen = false
                            else -> Unit
                        }
                    }
                )
            }



        }
    }
}