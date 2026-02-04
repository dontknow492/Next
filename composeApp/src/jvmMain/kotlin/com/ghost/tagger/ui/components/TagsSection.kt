package com.ghost.tagger.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

    Column(modifier= modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
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