package com.ghost.tagger.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun SelectionOverlay(
    selectedCount: Int,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onInvert: () -> Unit,
    onRemoveSelected: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Only show if something is selected
    AnimatedVisibility(
        visible = selectedCount > 0,
        enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier
                .padding(top = 16.dp)
                .height(56.dp)
                .wrapContentWidth(),
            shape = RoundedCornerShape(50), // Pill shape
            color = MaterialTheme.colorScheme.primaryContainer,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(0.2f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. Selection Count
                Badge(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(28.dp)
                ) {
                    Text("$selectedCount", fontWeight = FontWeight.Bold)
                }

                Text(
                    text = "Items Selected",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )

                VerticalDivider(
                    modifier = Modifier.height(24.dp).padding(horizontal = 4.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(0.2f)
                )

                // 2. Action Buttons
                SelectionActionButton(Icons.Rounded.SelectAll, "All", onSelectAll)
                SelectionActionButton(Icons.Rounded.Flip, "Invert", onInvert)
                SelectionActionButton(Icons.Rounded.ClearAll, "Clear", onDeselectAll)

                VerticalDivider(
                    modifier = Modifier.height(24.dp).padding(horizontal = 4.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(0.2f)
                )

                // 3. Destructive Action
                IconButton(
                    onClick = onRemoveSelected,
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Rounded.DeleteOutline, "Remove Selected")
                }
            }
        }
    }
}

@Composable
private fun SelectionActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 8.dp),
        colors = ButtonDefaults.textButtonColors(
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    ) {
        Icon(icon, null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}