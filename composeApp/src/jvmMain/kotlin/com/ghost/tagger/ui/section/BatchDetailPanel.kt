package com.ghost.tagger.ui.section

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.SdStorage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.PlatformContext
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.ghost.tagger.data.models.ImageItem
import com.ghost.tagger.ui.viewmodels.BatchDetailViewModel
import org.koin.compose.viewmodel.koinViewModel

// -------------------------------------------------------------------------
// UI Component
// -------------------------------------------------------------------------
@Composable
fun BatchDetailPanel(
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel: BatchDetailViewModel = koinViewModel()

    val state by viewModel.uiState.collectAsStateWithLifecycle()


    // Derived colors for consistency
    val containerColor = MaterialTheme.colorScheme.surfaceContainer
    val contentColor = MaterialTheme.colorScheme.onSurface

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(360.dp) // Standard sidebar width
            .background(containerColor)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // 1. Header & Close
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Batch Selection",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close")
            }
        }

        // 2. Visual Stack (The "Pile" of photos)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            contentAlignment = Alignment.Center
        ) {
            BatchImageStack(images = state.selectedImages)
        }

        // 3. Info Cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            InfoChip(
                icon = Icons.Default.DoneAll,
                label = "${state.count} items",
                modifier = Modifier.weight(1f)
            )
            InfoChip(
                icon = Icons.Outlined.SdStorage,
                label = state.totalSize,
                modifier = Modifier.weight(1f)
            )
        }

        Divider(color = MaterialTheme.colorScheme.outlineVariant)

        // 4. Batch Actions (AI)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Actions",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )

            Button(
                onClick = viewModel::generateTagsForBatch,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isGenerating,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                if (state.isGenerating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Processing Batch...")
                } else {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Auto-Tag All Images")
                }
            }

            OutlinedButton(
                onClick = viewModel::clearTagsBatch,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Clear Tags from All")
            }
        }

        // 5. Tag Management
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "Tags",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            // Input
            var tagInput by remember { mutableStateOf("") }
            OutlinedTextField(
                value = tagInput,
                onValueChange = { tagInput = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Add tag to ${state.count} images...") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                trailingIcon = {
                    IconButton(
                        onClick = {
                            if (tagInput.isNotBlank()) {
                                viewModel.addTagToBatch(tagInput)
                                tagInput = ""
                            }
                        }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add")
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    if (tagInput.isNotBlank()) {
                        viewModel.addTagToBatch(tagInput)
                        tagInput = ""
                    }
                })
            )

            // Common Tags Section
            if (state.commonTags.isNotEmpty()) {
                Text(
                    text = "Shared Tags (In All)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    state.commonTags.forEach { tag ->
                        SuggestionChip(
                            onClick = { /* Could open tag editor context menu */ },
                            label = { Text(tag.name) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                labelColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            border = null,
                            icon = {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remove",
                                    modifier = Modifier.size(14.dp),
                                    // Make the X clickable to remove from all
                                )
                            },
                            // Override click to remove for this demo
                            modifier = Modifier.height(32.dp)
                        )
                    }
                }
            }

            // Mixed Tags Section
            if (state.mixedTags.isNotEmpty()) {
                Text(
                    text = "Mixed Tags (In Some)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary
                )
                Text(
                    text = "Click to add to all images",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    state.mixedTags.forEach { tag ->
                        FilterChip(
                            selected = false,
                            onClick = { viewModel.addTagToBatch(tag.name) }, // Clicking promotes it to all
                            label = { Text(tag.name) },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = Color.Transparent,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = false,
                                borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            )
                        )
                    }
                }
            }

            if (state.commonTags.isEmpty() && state.mixedTags.isEmpty()) {
                Text(
                    text = "No tags found in selection.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }
        }

        Spacer(Modifier.height(32.dp)) // Bottom padding
    }
}

// -------------------------------------------------------------------------
// Sub-Components
// -------------------------------------------------------------------------

@Composable
private fun InfoChip(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Creates a visual stack of images.
 * Shows up to 3 thumbnails stacked with slight rotation/offset.
 */
@Composable
private fun BatchImageStack(images: List<ImageItem>) {
    // We take the last 3 to stack them, or first 3, depending on visual preference.
    // Taking first 3. Reverse them so index 0 is on top in Box.
    val displayImages = images.take(3).reversed()

    Box(contentAlignment = Alignment.Center) {
        displayImages.forEachIndexed { index, item ->
            // Logic to determine visual offset based on "depth"
            // If we have 3 images:
            // index 0 (was 3rd) -> bottom
            // index 2 (was 1st) -> top

            val realIndex = displayImages.size - 1 - index // 0 is top

            // Visual tweaks
            val rotation = when (realIndex) {
                0 -> 0f
                1 -> -4f
                else -> 4f
            }
            val xOffset = when (realIndex) {
                0 -> 0.dp
                1 -> (-12).dp
                else -> 12.dp
            }
            val scale = 1f - (realIndex * 0.05f)
            val opacity = 1f - (realIndex * 0.15f)

            Surface(
                modifier = Modifier
                    .size(160.dp)
                    .offset(x = xOffset, y = (realIndex * -8).dp)
                    .rotate(rotation)
                    .shadow(elevation = (8 - (realIndex * 2)).dp, shape = RoundedCornerShape(16.dp))
                    .border(2.dp, MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(PlatformContext.INSTANCE)
                        .data(item.metadata.path)
                        .crossfade(true)
                        .build(),
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    error = rememberVectorPainter(Icons.Default.Image)
                )

                // Darken lower cards
                if (realIndex > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.2f))
                    )
                }
            }
        }

        // Count badge if more than shown
        if (images.size > 3) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 10.dp, y = 10.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "+${images.size - 3}",
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// Quick helper for AsyncImage error placeholder
@Composable
fun rememberVectorPainter(image: ImageVector) = androidx.compose.ui.graphics.vector.rememberVectorPainter(image)