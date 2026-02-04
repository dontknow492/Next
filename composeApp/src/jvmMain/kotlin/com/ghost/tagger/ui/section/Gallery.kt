package com.ghost.tagger.ui.section

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.touchlab.kermit.Logger
import com.ghost.tagger.data.enums.GalleryMode
import com.ghost.tagger.data.models.ImageItem
import com.ghost.tagger.ui.components.*
import com.ghost.tagger.ui.viewmodels.GalleryViewModel
import org.koin.compose.viewmodel.koinViewModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.io.File


sealed class GalleryCommand {
    // 1. Navigation
    data class LoadDirectory(val path: File, val recursive: Boolean) : GalleryCommand()
    object Refresh : GalleryCommand()

    // 2. View mode
    data class SetViewMode(val mode: GalleryMode) : GalleryCommand()

    // 3. Selection
    data class ToggleSelection(val id: String, val ctrlPressed: Boolean, val shiftPressed: Boolean) : GalleryCommand()
    object SelectAll : GalleryCommand()
    object InvertSelection : GalleryCommand()
    object ClearSelection : GalleryCommand()

    // 4. Focus
    data class FocusImage(val id: String) : GalleryCommand()

    // 5. File ops
    object DeleteSelectedImages : GalleryCommand()

    data class onZoom(val isIncrement: Boolean) : GalleryCommand()

    data class removeImage(val id: String) : GalleryCommand()

    data class openInExplorer(val path: File) : GalleryCommand()

    data class onMove(val from: Int, val to: Int) : GalleryCommand()
}

@Composable
fun GallerySection(
    modifier: Modifier = Modifier,
) {
    val viewModel: GalleryViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = modifier) {
        Row(modifier = Modifier.heightIn(0.dp, 68.dp)) {
            FilterBar(
                searchQuery = uiState.searchQuery,
                onSearch = viewModel::onSearch,
                viewMode = uiState.viewMode,
                onViewModeChange = viewModel::setViewMode,
                sortBy = uiState.sortBy,
                onSortByChange = viewModel::onSortByChange,
                sortOrder = uiState.sortOrder,
                onSortOrderChange = viewModel::onSortOrderChange,
                modifier = Modifier.weight(2.0f)
            )
            VerticalDivider()
            DirectorySection(
                modifier = Modifier.weight(1.0f),
                currentDir = uiState.currentDirectory,
                onClear = viewModel::clearGallery,
                onLoadDirectory = viewModel::loadDirectory,
                currentSettings = uiState.advanceDirSettings,
                refreshing = uiState.isLoading,
                onRefresh = viewModel::refresh,
                onUpdateSettings = viewModel::updateAdvanceFolderSettings
            )
        }
        DragDropFileBox(
            modifier = Modifier.weight(1.0f),
            onFolderDrop = viewModel::loadDirectory
        ) {
            ImageSection(viewModel = viewModel)
        }

    }
}

@Composable
private fun ImageSection(
    modifier: Modifier = Modifier,
    viewModel: GalleryViewModel,
) {
    val uiState by viewModel.uiState.collectAsState()

    val hapticFeedback = LocalHapticFeedback.current

    val action_map: (GalleryCommand) -> Unit = { command ->
        when (command) {
            is GalleryCommand.LoadDirectory -> viewModel.loadDirectory(command.path)
            is GalleryCommand.Refresh -> {
                if (uiState.currentDirectory != null) viewModel.loadDirectory(uiState.currentDirectory!!) else Unit
            }

            is GalleryCommand.SetViewMode -> viewModel.setViewMode(command.mode)
            is GalleryCommand.ToggleSelection -> viewModel.toggleSelection(
                command.id,
                command.ctrlPressed,
                command.shiftPressed
            )

            is GalleryCommand.SelectAll -> viewModel.selectAll()
            is GalleryCommand.InvertSelection -> viewModel.invertSelection()
            is GalleryCommand.ClearSelection -> viewModel.clearSelection()
            is GalleryCommand.FocusImage -> {
                viewModel.focusImage(command.id)
            }

            is GalleryCommand.DeleteSelectedImages -> Unit
            is GalleryCommand.onZoom -> viewModel.modifyThumbnailSize(command.isIncrement)
            is GalleryCommand.removeImage -> viewModel.removeImage(command.id)
            is GalleryCommand.openInExplorer -> viewModel.openInExplorer(command.path)

            is GalleryCommand.onMove -> viewModel.onMove(command.from, command.to)
        }
    }

    val openDir = rememberDirectoryPicker(title = "Open Image Folder") { file ->
        if (file != null) {
            viewModel.loadDirectory(file)
        }
    }
    PullToRefreshBox(
        isRefreshing = uiState.isLoading,
        onRefresh = viewModel::refresh,
    ) {
        SharedTransitionLayout {
            AnimatedContent(
                targetState = uiState.viewMode,
                label = "GalleryModeTransition",
            ) { targetMode ->
                Box(modifier = modifier) {
                    when {
                        // Case 1: No Directory
                        uiState.currentDirectory == null -> OpenDirectoryPanel(
                            modifier = Modifier.align(Alignment.Center),
                            onDirectorySelected = viewModel::loadDirectory
                        )

                        // Case 2: Empty Folder
                        uiState.images.isEmpty() -> EmptyGalleryState(
                            currentDirectory = uiState.currentDirectory,
                            onPickNewFolder = openDir
                        )

                        targetMode == GalleryMode.LANDSCAPE -> LandscapeSection(
                            modifier = Modifier.fillMaxSize(),
                            images = uiState.images,
                            focusedImageId = uiState.focusedImageId,
                            selectedIds = uiState.selectedIds,
                            hapticFeedback = hapticFeedback,
                            sharedTransitionScope = this@SharedTransitionLayout,
                            animatedContentScope = this@AnimatedContent,
                            actions = action_map
                        )

                        targetMode == GalleryMode.GRID -> GridSection(
                            modifier = Modifier.fillMaxSize(),
                            images = uiState.images,
                            focusedImageId = uiState.focusedImageId,
                            minImageDp = uiState.minThumbnailSizeDp.dp,
                            hapticFeedback = hapticFeedback,
                            actions = action_map,
                            sharedTransitionScope = this@SharedTransitionLayout,
                            animatedContentScope = this@AnimatedContent,
                            selectedIds = uiState.selectedIds
                        )
                    }

                    SelectionOverlay(
                        modifier = Modifier.align(Alignment.BottomCenter),
                        selectedCount = uiState.selectedIds.size,
                        onSelectAll = viewModel::selectAll,
                        onDeselectAll = viewModel::clearSelection,
                        onInvert = viewModel::invertSelection,
                        onRemoveSelected = viewModel::removeSelectedImages
                    )
                }
            }
        }
    }


}


@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun GridSection(
    modifier: Modifier = Modifier,
    minImageDp: Dp = 128.dp,
    images: List<ImageItem>,
    focusedImageId: String? = null,
    selectedIds: Set<String>,
    isLoading: Boolean = false,
    hapticFeedback: HapticFeedback,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    actions: (GalleryCommand) -> Unit,
) {

    val lazyGridState = rememberLazyGridState()
    val reorderableLazyState = rememberReorderableLazyGridState(lazyGridState) { from, to ->
        //        viewModel.onMove(from.index, to.index)
        if (from.index == to.index) return@rememberReorderableLazyGridState
        Logger.i("onMove: ${from.index}, ${to.index}", tag = "GridSection")
        actions(GalleryCommand.onMove(from.index, to.index))
        hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
    }

    val spacing = remember(minImageDp) {
        (minImageDp * 0.08f).coerceIn(8.dp, 18.dp)
    }

    Box(modifier = modifier) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minImageDp),
            state = lazyGridState,
            horizontalArrangement = Arrangement.spacedBy(spacing),
            verticalArrangement = Arrangement.spacedBy(spacing),
            modifier = Modifier.fillMaxSize()
                // 1. THE GATEKEEPER: Intercept Scroll Events
                .onPointerEvent(PointerEventType.Scroll) { event ->
                    val changes = event.changes
                    val isCtrlPressed = event.keyboardModifiers.isCtrlPressed || event.keyboardModifiers.isMetaPressed

                    // Only interfere if CTRL is pressed
                    if (isCtrlPressed && changes.isNotEmpty()) {
                        val scrollDelta = changes.first().scrollDelta.y

                        // 2. Determine Direction
                        // Desktop scroll conventions:
                        // Negative Y (Wheel Up) usually means "Zoom In"
                        // Positive Y (Wheel Down) usually means "Zoom Out"
                        if (scrollDelta != 0f) {
                            val isZoomIn = scrollDelta < 0
                            actions(GalleryCommand.onZoom(isZoomIn))

                            // 3. CONSUME THE EVENT
                            // This prevents the Grid from scrolling vertically while you are zooming
                            changes.forEach { it.consume() }
                        }
                    }
                }
            //        state = listState,
        ) {
            items(items = images, key = { it.id }) { imageItem ->
                ReorderableItem(reorderableLazyState, imageItem.id) {
                    val interactionSource = remember { MutableInteractionSource() }
                    ThumbnailCard(
                        item = imageItem,
                        focused = imageItem.id == focusedImageId,
                        selected = imageItem.id in selectedIds,
                        isSelectionVisible = selectedIds.isNotEmpty(),
                        onFocus = { actions(GalleryCommand.FocusImage(imageItem.id)) },
                        onToggleSelection = { ctrl, shift ->
                            actions(
                                GalleryCommand.ToggleSelection(
                                    imageItem.id,
                                    ctrl,
                                    shift
                                )
                            )
                        },
                        onDeleteClick = { actions(GalleryCommand.removeImage(imageItem.id)) },
                        sharedTransitionScope = sharedTransitionScope,
                        animatedContentScope = animatedContentScope,
                        dragHandler = {
                            IconButton(
                                modifier = Modifier.draggableHandle(
                                    onDragStarted = {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                                    },
                                    onDragStopped = {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureEnd)
                                    },
                                    interactionSource = interactionSource,
                                ),
                                onClick = {},
                            ) {
                                Icon(Icons.Rounded.DragHandle, contentDescription = "Reorder")
                            }
                        }
                    )
                }
            }
            // Loading indicator as the last item
            if (isLoading) {
                item {
                    LoadingIndictor()
                }
            }
        }
        MyVerticalScrollBar(
            modifier = Modifier.align(Alignment.CenterEnd),
            adapter = rememberScrollbarAdapter(lazyGridState),
        )
    }
}


@Composable
fun LandscapeSection(
    modifier: Modifier = Modifier,
    focusedImageId: String? = null,
    images: List<ImageItem>,
    selectedIds: Set<String>,
    isLoading: Boolean = false,
    hapticFeedback: HapticFeedback,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    actions: (GalleryCommand) -> Unit,
) {
    val lazyListState = rememberLazyListState()
    val reorderableLazyListState = rememberReorderableLazyListState(lazyListState) { from, to ->
//        viewModel.onMove(from.index, to.index)
        if (from.index == to.index) return@rememberReorderableLazyListState
        Logger.i("onMove: ${from.index}, ${to.index}", tag = "LandscapeSection")
        actions(GalleryCommand.onMove(from.index, to.index))
        hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
    }
    Box {
        LazyColumn(
            state = lazyListState,
            modifier = modifier
        ) {
            items(items = images, key = { it.id }) { imageItem ->
                ReorderableItem(reorderableLazyListState, imageItem.id) {
                    val interactionSource = remember { MutableInteractionSource() }
                    LandscapeImageCard(
                        item = imageItem,
                        focused = imageItem.id == focusedImageId,
                        onFocus = { actions(GalleryCommand.FocusImage(imageItem.id)) },
                        selected = imageItem.id in selectedIds,
                        isSelectionVisible = selectedIds.isNotEmpty(),
                        onPathClick = { actions(GalleryCommand.openInExplorer(imageItem.metadata.path)) },
                        onToggleSelection = { ctrl, shift ->
                            actions(
                                GalleryCommand.ToggleSelection(
                                    imageItem.id,
                                    ctrl,
                                    shift
                                )
                            )
                        },
                        onDeleteClick = { actions(GalleryCommand.removeImage(imageItem.id)) },
                        sharedTransitionScope = sharedTransitionScope,
                        animatedContentScope = animatedContentScope,
                        dragHandler = {
                            IconButton(
                                modifier = Modifier.draggableHandle(
                                    onDragStarted = {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                                    },
                                    onDragStopped = {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureEnd)
                                    },
                                    interactionSource = interactionSource,
                                ),
                                onClick = {},
                            ) {
                                Icon(Icons.Rounded.DragHandle, contentDescription = "Reorder")
                            }
                        }
                    )
                }
            }
            // Loading indicator as the last item
            if (isLoading) {
                item {
                    LoadingIndictor()
                }
            }
        }
        MyVerticalScrollBar(
            modifier = Modifier.align(Alignment.CenterEnd),
            adapter = rememberScrollbarAdapter(lazyListState),
        )
    }
}

@Composable
private fun LoadingIndictor(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(48.dp)
            .padding(end = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            strokeWidth = 2.dp,
            modifier = Modifier.size(24.dp)
        )
    }
}


