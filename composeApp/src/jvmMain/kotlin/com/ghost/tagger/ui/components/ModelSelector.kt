package com.ghost.tagger.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ghost.tagger.core.onnx.`interface`.TaggerModel
import java.awt.Desktop
import java.net.URI

@Composable
fun ModelSelector(
    selectedModelId: String,
    models: List<TaggerModel>,
    onModelSelected: (TaggerModel) -> Unit,
    onDownloadClick: (TaggerModel) -> Unit,
    onOpenFolderClick: (TaggerModel) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedModel = models.find { it.id == selectedModelId } ?: models.firstOrNull()

    // Animation for the arrow rotation
    val arrowRotation by animateFloatAsState(targetValue = if (expanded) 180f else 0f)
    var isDownloadMessageBoxVisible by remember { mutableStateOf(false) }
    var downloadingModel: TaggerModel? by remember { mutableStateOf(null) }

    Column(modifier = Modifier.fillMaxWidth()) {

        // --- 1. The Trigger Card (Always Visible) ---
        OutlinedCard(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp) // Taller, more touch-friendly
                .clip(RoundedCornerShape(12.dp))
                .clickable { expanded = !expanded }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                // Leading Icon with Tonal Background
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.SmartToy,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Text Info
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Active AI Model",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = selectedModel?.displayName ?: "Select Model",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Arrow
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.rotate(arrowRotation)
                )
            }
        }

        // --- 2. The Dropdown Menu ---
        // We use a Box + AnimatedVisibility or standard DropdownMenu.
        // Standard DropdownMenu in M3 is easiest for positioning.
        MaterialTheme(shapes = MaterialTheme.shapes.copy(extraSmall = RoundedCornerShape(12.dp))) {
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier
                    .width(450.dp) // Wide menu for desktop comfort
                    .background(MaterialTheme.colorScheme.surfaceContainer) // Slightly distinct background
                    .padding(8.dp)
            ) {
                Text(
                    text = "AVAILABLE MODELS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 8.dp)
                )

                models.forEach { model ->
                    ModelOptionItemM3(
                        model = model,
                        isSelected = model.id == selectedModelId,
                        onSelect = {
                            onModelSelected(model)
                            expanded = false
                        },
                        onDownload = {
                            downloadingModel = model
                            isDownloadMessageBoxVisible = true
                        },
                        onOpenFolder = { onOpenFolderClick(model) }
                    )

                    // Divider between items (optional, usually cleaner without in M3 lists)
                    if (model != models.last()) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }

        if (isDownloadMessageBoxVisible && downloadingModel != null) {
            MessageBox(
                title = "Confirm Download",
                message = "Downloading model: ${downloadingModel?.displayName}",
                onConfirm = {
                    onDownloadClick(downloadingModel!!)
                    isDownloadMessageBoxVisible = false
                },
                onCancel = { downloadingModel = null; isDownloadMessageBoxVisible = false },
                confirmText = "Download",
                cancelText = "Cancel",
                isError = false
            )
        }
    }

}

@Composable
private fun ModelOptionItemM3(
    model: TaggerModel,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDownload: () -> Unit,
    onOpenFolder: () -> Unit
) {
    // Helper for URL
    fun openBrowser(url: String) {
        if (Desktop.isDesktopSupported()) {
            Desktop.getDesktop().browse(URI(url))
        }
    }

    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        Color.Transparent
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)) // Modern rounded list items
            .background(backgroundColor)
            .clickable { onSelect() }
            .padding(vertical = 12.dp, horizontal = 16.dp)
    ) {
        // 1. Radio / Status Indicator
        RadioButton(
            selected = isSelected,
            onClick = null, // Handled by Row click
            colors = RadioButtonDefaults.colors(
                selectedColor = MaterialTheme.colorScheme.primary,
                unselectedColor = MaterialTheme.colorScheme.outline
            ),
            modifier = Modifier.size(20.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        // 2. Content
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = model.displayName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Status Badges
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                // Status Text
                if (model.isDownloaded) {
                    Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.tertiary)
                    Spacer(Modifier.width(4.dp))
                    Text("Installed", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                } else {
                    Icon(Icons.Default.CloudDownload, null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(4.dp))
                    Text("Not Installed", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }

        // 3. Actions (Trailing Icons)
        Row(verticalAlignment = Alignment.CenterVertically) {

            // A. Website Button
            IconButton(
                onClick = { openBrowser(model.repoUrl) }, // Uses the repoUrl property
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Language, // Or Public
                    contentDescription = "HuggingFace Page",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            // B. Action Button (Download or Folder)
            if (model.isDownloaded) {
                FilledTonalIconButton(
                    onClick = onOpenFolder,
                    modifier = Modifier.size(36.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Icon(
                        imageVector = Icons.Outlined.FolderOpen,
                        contentDescription = "Open Folder",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            } else {
                FilledIconButton(
                    onClick = onDownload,
                    modifier = Modifier.size(36.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Download",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}