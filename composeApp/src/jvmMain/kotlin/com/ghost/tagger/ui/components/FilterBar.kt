package com.ghost.tagger.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.FolderSpecial
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ghost.tagger.data.enums.GalleryMode
import com.ghost.tagger.data.enums.SortBy
import com.ghost.tagger.data.enums.SortOrder
import com.ghost.tagger.data.models.settings.DirectorySettings
import kotlinx.coroutines.launch

@Composable
fun FilterBar(
    searchQuery: String,
    onSearch: (String) -> Unit,
    viewMode: GalleryMode,
    onViewModeChange: (GalleryMode) -> Unit,
    // Sorting Params
    sortBy: SortBy,
    onSortByChange: (SortBy) -> Unit,
    sortOrder: SortOrder,
    onSortOrderChange: (SortOrder) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp), // Breathing room
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Modern Search Field (Takes available space)
        ModernSearchBar(
            query = searchQuery,
            onQueryChange = onSearch,
            modifier = Modifier.weight(1f)
        )

        // Vertical Divider for separation
        VerticalDivider(
            modifier = Modifier.height(24.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )

        // 2. Sort Menu (Compact Dropdown)
        SortMenuButton(
            currentSortBy = sortBy,
            currentSortOrder = sortOrder,
            onSortByChange = onSortByChange,
            onSortOrderChange = onSortOrderChange
        )

        // 3. View Mode Switcher
        ViewModeMenu(
            viewMode = viewMode,
            onModeChange = onViewModeChange
        )
    }
}

@Composable
fun DirectorySection(
    modifier: Modifier = Modifier,
    currentDir: String?,
    // Use the simplified data class
    currentSettings: DirectorySettings = DirectorySettings(),
    onClear: () -> Unit,
    onLoadDirectory: (String) -> Unit,
    onUpdateSettings: (DirectorySettings) -> Unit
){
    var isFileDialogOpened by remember { mutableStateOf(false) }
    var isSettingsDialogOpened by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val openDir = rememberDirectoryPicker(title = "Select Folder") { file ->
        if (file != null) {
            onLoadDirectory(file.absolutePath)
            isFileDialogOpened = false
        }
    }

    if (isSettingsDialogOpened) {
        DirectorySettingsDialog(
            currentDir = currentDir,
            settings = currentSettings,
            onDismiss = { isSettingsDialogOpened = false },
            onApply = onUpdateSettings
        )
    }

    // UI Layout remains the same as before...
    Row(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Settings Button
        FilledTonalIconButton(
            onClick = { isSettingsDialogOpened = true },
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Rounded.Tune, contentDescription = "Folder Options")
        }

        // Open Button
        Button(
            onClick = { scope.launch { isFileDialogOpened = true; openDir() } },
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Icon(Icons.Rounded.FolderOpen, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Open Folder")
        }

        // Clear Button (only if directory loaded)
        if (currentDir != null) {
            OutlinedIconButton(
                onClick = onClear,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
            ) {
                Icon(Icons.Rounded.DeleteSweep, null, tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}


@Composable
fun DirectorySettingsDialog(
    currentDir: String?,
    settings: DirectorySettings,
    onDismiss: () -> Unit,
    onApply: (DirectorySettings) -> Unit
) {
    var tempSettings by remember { mutableStateOf(settings) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.width(380.dp),
            shape = RoundedCornerShape(28.dp), // Extra rounded for modern feel
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // --- Header ---
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Using a tonal container for the icon makes it pop
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Rounded.FolderSpecial,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "Folder Options",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // --- 1. Current Path Indicator ---
                // Only show if a directory is actually selected
                if (currentDir != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {

                        Text(
                            text = "SELECTED PATH",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            SelectionContainer {
                                Text(
                                    text = currentDir,
                                    modifier = Modifier.padding(12.dp),
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    maxLines = 2,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                        }
                    }
                }

                // --- 2. Recursion Logic (The Main Logic) ---
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Toggle Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Recursive Scan", style = MaterialTheme.typography.titleSmall)
                            Text("Look inside subfolders", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = tempSettings.isRecursive,
                            onCheckedChange = { tempSettings = tempSettings.copy(isRecursive = it) }
                        )
                    }

                    // Depth Slider (Animated)
                    AnimatedVisibility(
                        visible = tempSettings.isRecursive,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(modifier = Modifier.padding(top = 8.dp)) {
                            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            Spacer(Modifier.height(12.dp))

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Max Depth",
                                    style = MaterialTheme.typography.labelMedium
                                )
                                Spacer(Modifier.weight(1f))
                                Text(
                                    text = "${tempSettings.maxDepth}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Slider(
                                value = tempSettings.maxDepth.toFloat(),
                                onValueChange = { tempSettings = tempSettings.copy(maxDepth = it.toInt()) },
                                valueRange = 1f..10f,
                                steps = 9
                            )
                        }
                    }
                }

                // --- 3. Hidden Files ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = tempSettings.includeHiddenFiles,
                        onCheckedChange = { tempSettings = tempSettings.copy(includeHiddenFiles = it) }
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Include hidden folders (.git, .temp)", style = MaterialTheme.typography.bodyMedium)
                }

                Spacer(modifier = Modifier.height(4.dp))

                // --- Actions ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { onApply(tempSettings); onDismiss() }) {
                        Text("Apply")
                    }
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewModeMenu(
    viewMode: GalleryMode,
    onModeChange: (GalleryMode) -> Unit,
    modifier: Modifier = Modifier
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        // 1. Grid Mode
        SegmentedButton(
            selected = viewMode == GalleryMode.GRID,
            onClick = { onModeChange(GalleryMode.GRID) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            // We pass empty lambda to 'icon' to hide the checkmark
            // because for view toggles, the color change is enough context.
            icon = {}
        ) {
            Icon(
                imageVector = Icons.Rounded.GridView,
                contentDescription = "Grid View",
                modifier = Modifier.size(20.dp)
            )
        }

        // 2. Landscape Mode
        SegmentedButton(
            selected = viewMode == GalleryMode.LANDSCAPE,
            onClick = { onModeChange(GalleryMode.LANDSCAPE) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            icon = {}
        ) {
            Icon(
                // AutoMirrored handles RTL layouts automatically
                imageVector = Icons.AutoMirrored.Rounded.List,
                contentDescription = "Landscape View",
                modifier = Modifier.size(20.dp)
            )
        }
    }
}