package com.ghost.tagger.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.LocalPlatformContext
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Precision
import java.io.File

@Composable
fun ImageView(
    modifier: Modifier = Modifier,
    path: File,
    contentDescription: String?,
    contentScale: ContentScale = ContentScale.Crop
) {
    // 1. Measure the exact size of the container in pixels
    BoxWithConstraints(modifier = modifier) {
        val widthPx = constraints.maxWidth
        val heightPx = constraints.maxHeight

        // 2. Build the request with the specific size
        val request = ImageRequest.Builder(LocalPlatformContext.current)
            .data(path)
            .apply {
                // Only set size if we have valid dimensions
                // This forces Coil to generate a NEW cache key when size changes significantly
                if (widthPx > 0 && widthPx != Int.MAX_VALUE &&
                    heightPx > 0 && heightPx != Int.MAX_VALUE
                ) {
                    size(widthPx, heightPx)
                }
            }
            // 3. Force exact match (prevents using a small thumbnail for a large view)
            .precision(Precision.EXACT)
            // 4. Add a smooth fade when the higher-res image replaces the pixelated one
            .crossfade(true)
            .build()

        SubcomposeAsyncImage(
            model = request,
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = Modifier.fillMaxSize(),
            loading = {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
                }
            },
            error = {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.BrokenImage,
                        contentDescription = "Error loading image",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                    )
                }
            }
        )
    }
}