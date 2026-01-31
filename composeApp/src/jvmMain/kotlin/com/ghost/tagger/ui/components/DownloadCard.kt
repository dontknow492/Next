package com.ghost.tagger.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ghost.tagger.data.models.DownloadStatus

import androidx.compose.ui.window.Popup
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.PopupProperties

// Import your data classes
//import com.ghost.tagger.core.DownloadStatus

@Composable
fun DownloadStatusCard(
    modelName: String,
    status: DownloadStatus?,
    isDownloading: Boolean,
    onCancel: () -> Unit
) {
    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .width(320.dp)
            .padding(4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (isDownloading) "Downloading Model..." else "Downloads",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (isDownloading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Model Name
            Text(
                text = modelName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (status != null && isDownloading) {
                val animatedProgress by animateFloatAsState(targetValue = status.progress)

                LinearProgressIndicator(
                    progress = animatedProgress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Stats Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Speed", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        Text(status.speed, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("ETA", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        Text(status.eta, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = status.downloadedText,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.End),
                    color = Color.Gray
                )
            } else if (!isDownloading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, null, tint = Color.Green, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("All downloads complete", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}



@Composable
fun DownloadSidebarItem(
    isDownloading: Boolean,
    progress: Float, // 0.0 to 1.0 (from your status)
    status: DownloadStatus?,
    activeModelName: String
) {
    var isPopupOpen by remember { mutableStateOf(false) }

    Box {
        // --- The Sidebar Button ---
        IconButton(
            onClick = { isPopupOpen = !isPopupOpen },
            modifier = Modifier
                .size(48.dp) // Standard Sidebar item size
                .background(
                    if (isPopupOpen) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                    else Color.Transparent,
                    shape = RoundedCornerShape(8.dp)
                )
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (isDownloading) {
                    // Show Circular Progress AROUND the icon
                    CircularProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.size(32.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp,
                        trackColor = ProgressIndicatorDefaults.circularIndeterminateTrackColor,
                        strokeCap = ProgressIndicatorDefaults.CircularDeterminateStrokeCap,
                    )
                    // Smaller icon inside
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Downloading",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                } else {
                    // Standard Icon when idle
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Downloads",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }

        // --- The Popup ---
        if (isPopupOpen) {
             Popup(
                 alignment = Alignment.TopEnd,
                 offset = IntOffset(x = 340, y = -100), // Adjust 'x' based on your sidebar width!
                 onDismissRequest = { isPopupOpen = false },
                 properties = PopupProperties(focusable = true, dismissOnClickOutside = true),
             ){
                DownloadStatusCard(
                    modelName = activeModelName,
                    status = status,
                    isDownloading = isDownloading,
                    onCancel = { /* Cancel logic */ }
                )
            }
        }
    }
}