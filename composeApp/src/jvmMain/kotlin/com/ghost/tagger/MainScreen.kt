package com.ghost.tagger

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ghost.tagger.core.ModelManager
import com.ghost.tagger.ui.components.ApiKeyDialog
import com.ghost.tagger.ui.components.VerticalDraggableSplitter
import com.ghost.tagger.ui.section.GallerySection
import com.ghost.tagger.ui.section.ImageDetailArea
import com.ghost.tagger.ui.section.LeftNavigationBar
import com.ghost.tagger.ui.section.SettingsSidebar
import com.ghost.tagger.ui.theme.AppTheme
import com.ghost.tagger.ui.viewmodels.*
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun MainScreen(
) {


    // 3. koinInject() now works perfectly because it's inside KoinContext
    val settingsViewModel: SettingsViewModel = koinViewModel()
    ModelManager.initialize(settingsViewModel.settingsFlow())
    val currentSettings by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val isSettingVisible = currentSettings.settings.session.isSidebarVisible

    val galleryViewModel: GalleryViewModel = koinViewModel()
    val galleryUiState by galleryViewModel.uiState.collectAsStateWithLifecycle()

    val imageDetailViewModel: ImageDetailViewModel = koinViewModel()

    val apiViewModel: ApiKeyViewModel = koinViewModel()
    val apiDialogState by apiViewModel.state.collectAsStateWithLifecycle()


    val batchDetailViewModel: BatchDetailViewModel = koinViewModel()


    var isPanelVisible: Boolean by remember(galleryUiState.focusedImageId) { mutableStateOf(galleryUiState.focusedImageId != null) }

    LaunchedEffect(galleryUiState.focusedImageId) {
        imageDetailViewModel.selectImage(galleryUiState.focusedImageId ?: return@LaunchedEffect)
    }

    LaunchedEffect(galleryUiState.selectedIds) {
        batchDetailViewModel.selectImages(galleryUiState.selectedIds)
    }



    AppTheme(themeMode = currentSettings.settings.themeMode) {
        Scaffold { innerPadding ->
            Row(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                SettingsSidebar()
                if (isSettingVisible) {
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
                    },
                    onApiClick = apiViewModel::openDialog,
                    themeMode = currentSettings.settings.themeMode,
                    onThemeChange = settingsViewModel::setThemeMode
                )
                // Main Gallery Area
//            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                GallerySection(Modifier.weight(1f).fillMaxHeight())
//            }

                if (isPanelVisible) {
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
                    ImageDetailArea(
                        modifier = Modifier.fillMaxHeight(),
                        width = currentSettings.settings.session.previewSectionWidthDp.dp,
                        isBatch = galleryUiState.selectedIds.isNotEmpty(),
                        onClearSelection = galleryViewModel::clearSelection,
                        onCloseImagePreview = { isPanelVisible = false },
                        isPanelVisible = isPanelVisible
                    )

                }



                ApiKeyDialog(
                    isOpen = apiDialogState.isOpen,
                    currentKey = apiDialogState.currentKey,
                    isVerifying = apiDialogState.isVerifying,
                    verificationError = apiDialogState.error,
                    onDismiss = apiViewModel::dismissDialog,
                    onVerify = apiViewModel::verifyAndSave
                )

            }
        }
    }

}

