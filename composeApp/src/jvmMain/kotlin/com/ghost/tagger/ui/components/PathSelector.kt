package com.ghost.tagger.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.io.File

@Composable
fun PathSelector(
    label: String?,
    path: File,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onFolderClick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = modifier) {
        label?.let{
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            onClick = { onClick() }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 0.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = path.invariantSeparatorsPath.ifEmpty { "Select .onnx file..." },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (path.name.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1
                )
                IconButton(onClick = onFolderClick, modifier = Modifier) {
                    Icon(Icons.Rounded.FolderOpen, null, modifier = Modifier.size(16.dp).padding(0.dp))
                }

            }
        }
    }
}