package com.ghost.tagger.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.ghost.tagger.data.models.ImageTag
import com.ghost.tagger.ui.section.AestheticTagChip
import com.ghost.tagger.ui.section.SectionLabel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagsSection(
    title: String = "Tags",
    tags: List<ImageTag>,
    onRemove: (ImageTag) -> Unit,
    onAdd: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Collapsing Logic
    var isExpanded by remember { mutableStateOf(false) }
    val showTags = if (isExpanded) tags else tags.take(15)
    val hiddenCount = tags.size - 15

    // Add Tag Input Logic
    var newTagText by remember { mutableStateOf("") }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionLabel("$title (${tags.size})", Icons.AutoMirrored.Rounded.Label)

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

    }
}