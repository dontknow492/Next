package com.ghost.tagger.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Upcoming
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.io.File


@Composable
fun EmptyGalleryState(
    currentDirectory: File?,
    onPickNewFolder: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. The Illustration (Ghost or Cat Box)
        // We use a Box to create a nice circular background glow
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(120.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = CircleShape
                )
        ) {
            Icon(
                // Use a fun icon like 'Upcoming' (looks like an eye/ghost) or 'Inbox' (box)
                imageVector = Icons.Rounded.Upcoming,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                modifier = Modifier.size(64.dp)
            )
        }

        Spacer(Modifier.height(24.dp))

        // 2. The Title
        Text(
            text = "No Images Found",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(Modifier.height(8.dp))

        // 3. The Details (Where are we?)
        if (currentDirectory != null) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Text(
                    text = currentDirectory.invariantSeparatorsPath,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(8.dp),
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // 4. Helpful Tip
        Text(
            text = "Try checking the 'Recursive' setting if images are in sub-folders.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(32.dp))

        // 5. Call to Action
        Button(
            onClick = onPickNewFolder,
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
        ) {
            Icon(Icons.Rounded.FolderOpen, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Open Different Folder")
        }
    }
}