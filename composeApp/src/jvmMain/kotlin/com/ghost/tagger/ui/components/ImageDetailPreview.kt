package com.ghost.tagger.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghost.tagger.data.models.ImageItem
import com.ghost.tagger.data.models.ImageTag


sealed interface DetailAction {
    data class UpdateDescription(val value: String) : DetailAction
    data class RemoveTag(val tag: ImageTag) : DetailAction
    data class AddTag(val tag: String) : DetailAction
    object ClearTags : DetailAction
    object GenerateTags : DetailAction
    object SaveMetadata : DetailAction
    object OpenInExplorer : DetailAction
    object ClosePreview : DetailAction
}

@Composable
fun ImageDetailPreview(
    modifier: Modifier = Modifier,
    image: ImageItem,
    visible: Boolean = true,
    focusedImageId: String? = null,
    actions: (DetailAction) -> Unit
) {
    // Scroll state for the whole panel
    val scrollState = rememberScrollState()

    AnimatedVisibility(
            visible = focusedImageId != null && visible,
            enter = slideInHorizontally(
                // start the content off‑screen to the right
                initialOffsetX = { fullWidth -> fullWidth }
            ),
            exit = slideOutHorizontally(
                // slide the content off‑screen to the right
                targetOffsetX = { fullWidth -> fullWidth }
            )
        ) {

        Row {
            VerticalDivider()
            Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // 1. Aesthetic Image Header (With Floating Badges)
        ImageHeader(image, actions)

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

        // 2. Metadata Grid (Quick Stats)
        MetadataGrid(image)

        // 3. Description Editor
        DescriptionSection(
            description = image.metadata.description ?: "",
            onUpdate = { actions(DetailAction.UpdateDescription(it)) }
        )

        // 4. Tags Management (The Complex Part)
        TagsSection(
            tags = image.metadata.tags,
            onRemove = { actions(DetailAction.RemoveTag(it)) },
            onAdd = { actions(DetailAction.AddTag(it)) },
            onClear = { actions(DetailAction.ClearTags) },
            onGenerate = { actions(DetailAction.GenerateTags) },
            isTagging = image.isTagging
        )

        Spacer(Modifier.height(40.dp)) // Bottom Padding
    }
        }
    }


}

// =========================================================================
// SECTIONS
// =========================================================================

@Composable
private fun ImageHeader(image: ImageItem, actions: (DetailAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.5f) // Landscape preview
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
        ) {
            // Actual Image
            ImageView(path = image.metadata.path, contentDescription = image.name, contentScale = ContentScale.Fit)

            // Floating Badges (Glassmorphism style)
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BadgeText(image.metadata.extension.uppercase())
                VerticalDivider(Modifier.height(10.dp), color = Color.White.copy(0.3f))
                BadgeText(image.metadata.resolution())
            }

            // Quick Actions (Top Right)
            IconButton(
                onClick = { actions(DetailAction.OpenInExplorer) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(32.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f), CircleShape)
            ) {
                Icon(Icons.Rounded.FolderOpen, null, modifier = Modifier.size(16.dp))
            }

            IconButton(
                onClick = { actions(DetailAction.ClosePreview) },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(32.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f), CircleShape)
            ) {
                Icon(Icons.Rounded.Close, tint = MaterialTheme.colorScheme.error,
                    contentDescription = "Close", modifier = Modifier.size(18.dp))
            }
        }

        // Title & Path
        Column {
            Text(
                text = image.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = image.metadata.path,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun MetadataGrid(image: ImageItem) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        MetaItem(Icons.Rounded.SdStorage, "Size", image.metadata.readableSize())
        MetaItem(Icons.Rounded.CalendarToday, "Date", "Oct 24, 2025") // Format date properly in real app
        MetaItem(Icons.Rounded.Image, "Type", image.metadata.extension.uppercase())
    }
}

@Composable
private fun DescriptionSection(description: String, onUpdate: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel("Caption / Prompt", Icons.Rounded.Description)

        OutlinedTextField(
            value = description,
            onValueChange = onUpdate,
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyMedium,
            placeholder = { Text("Enter image description or prompt...") },
            minLines = 3,
            maxLines = 6,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(0.5f)
            )
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagsSection(
    tags: List<ImageTag>,
    isTagging: Boolean,
    onRemove: (ImageTag) -> Unit,
    onAdd: (String) -> Unit,
    onClear: () -> Unit,
    onGenerate: () -> Unit
) {
    // Collapsing Logic
    var isExpanded by remember { mutableStateOf(false) }
    val showTags = if (isExpanded) tags else tags.take(15)
    val hiddenCount = tags.size - 15

    // Add Tag Input Logic
    var newTagText by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionLabel("Tags (${tags.size})", Icons.AutoMirrored.Rounded.Label)

            // Clear Button
            if (tags.isNotEmpty()) {
                TextButton(
                    onClick = onClear,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Clear All")
                }
            }
        }

        // 1. Tag Input Field
        OutlinedTextField(
            value = newTagText,
            onValueChange = { newTagText = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Add tag...") },
            trailingIcon = {
                IconButton(onClick = {
                    if (newTagText.isNotBlank()) {
                        onAdd(newTagText)
                        newTagText = ""
                    }
                }) {
                    Icon(Icons.Default.Add, null)
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                 if (newTagText.isNotBlank()) {
                    onAdd(newTagText)
                    newTagText = ""
                }
            }),
            singleLine = true,
            shape = RoundedCornerShape(50), // Pill shape input
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                unfocusedBorderColor = Color.Transparent,
                focusedBorderColor = MaterialTheme.colorScheme.primary.copy(0.5f)
            )
        )

        // 2. Tags Cloud (FlowRow)
        AnimatedContent(targetState = showTags) { currentTags ->
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                currentTags.forEach { tag ->
                    AestheticTagChip(tag, onRemove)
                }

                // "Show More" Button inside the flow
                if (!isExpanded && hiddenCount > 0) {
                    SuggestionChip(
                        onClick = { isExpanded = true },
                        label = { Text("+$hiddenCount more") },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    )
                } else if (isExpanded && tags.size > 15) {
                    SuggestionChip(
                        onClick = { isExpanded = false },
                        label = { Text("Show Less") }
                    )
                }
            }
        }

        // 3. Main Action Button
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onGenerate,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(12.dp),
            enabled = !isTagging
        ) {
            if (isTagging) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.width(12.dp))
                Text("Analyzing Image...")
            } else {
                Icon(Icons.Rounded.AutoAwesome, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Generate AI Tags")
            }
        }
    }
}

// =========================================================================
// ATOMS (The small reusable aesthetic bits)
// =========================================================================

@Composable
fun AestheticTagChip(tag: ImageTag, onRemove: (ImageTag) -> Unit) {
    InputChip(
        selected = false,
        onClick = { /* Could open tag editor/confidence adjuster */ },
        label = { Text(tag.name, style = MaterialTheme.typography.bodySmall) },
        trailingIcon = {
            Icon(
                Icons.Default.Close,
                contentDescription = "Remove",
                modifier = Modifier.size(14.dp).clickable { onRemove(tag) }
            )
        },
        shape = RoundedCornerShape(8.dp),
        colors = InputChipDefaults.inputChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            labelColor = MaterialTheme.colorScheme.onSurface,
            trailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        border = null // No border for cleaner look
    )
}

@Composable
fun MetaItem(icon: ImageVector, label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun SectionLabel(text: String, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
        Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun BadgeText(text: String) {
    Text(
        text = text,
        color = Color.White,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold)
    )
}