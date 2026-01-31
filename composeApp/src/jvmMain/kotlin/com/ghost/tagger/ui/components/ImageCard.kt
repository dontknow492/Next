package com.ghost.tagger.ui.components

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Reorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ghost.tagger.data.models.ImageItem



@Composable
fun DragHandle() {
    Icon(
        imageVector = Icons.Rounded.Reorder,
        contentDescription = "Drag to reorder",
        modifier = Modifier.padding(2.dp).size(18.dp)
    )
}



@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LandscapeImageCard(
    item: ImageItem,
    focused: Boolean = false,
    selected: Boolean = false,
    isSelectionVisible: Boolean = false,
    onFocus: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    onToggleSelection: (isCtrl: Boolean, isShift: Boolean) -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
    onPathClick: () -> Unit,
    dragHandler: @Composable () -> Unit = { DragHandle() },
) {
    var isHovered by remember { mutableStateOf(false) }
    // Optional: Customize how the bounds morph (Spring is usually best for UI)
//    val boundsTransform = BoundsTransform { _, _ ->
//        spring(stiffness = Spring.StiffnessLow, visibilityThreshold = 0.001f)
//    }
    with(sharedTransitionScope){
        Surface(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.first()

                        // CRITICAL FIX:
                        // If the Checkbox was clicked, it "consumed" this event.
                        // We stop here so the Column doesn't override the Checkbox logic.
                        if (change.isConsumed) {
                            continue
                        }

                        if (event.type == PointerEventType.Press) {
                            val config = event.keyboardModifiers
                            val isCtrl = config.isCtrlPressed || config.isMetaPressed
                            val isShift = config.isShiftPressed

                            // This handles the Column click (Simple, Ctrl, or Shift)
                            onToggleSelection(isCtrl, isShift)

                            // We consume this so no parents trigger
                            change.consume()
                        }
                    }
                }
            }
            .onPointerEvent(PointerEventType.Enter) { isHovered = true }
            .onPointerEvent(PointerEventType.Exit) { isHovered = false },
        shape = RoundedCornerShape(12.dp),
        // Use background colors to reinforce selection state in list view
        color = when {
            focused -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
            selected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
            isHovered -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            else -> Color.Transparent
        },
        border = BorderStroke(
            width = 1.dp,
            color = when {
                selected -> MaterialTheme.colorScheme.primary
                focused -> MaterialTheme.colorScheme.secondary
                else -> Color.Transparent
            }
        ),
    ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.Start)
            ) {
                // 1 & 2. Drag Handle / Checkbox
                if (isSelectionVisible) {
                    Checkbox(
                        checked = selected,
                        onCheckedChange = { onToggleSelection(false, false) },
                        modifier = Modifier.padding(end = 8.dp),
                        enabled = !item.isTagging, // Disable checkbox if busy
                        colors = CheckboxDefaults.colors(
                            checkedColor = MaterialTheme.colorScheme.primary
                        )
                    )
                } else {
                    Box(modifier = Modifier.alpha(if (isHovered) 1f else 0.3f)) {
                        dragHandler()
                    }
                }

                // 3. Image Preview with BUSY OVERLAY
                Box(modifier = Modifier.size(64.dp)) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        shape = RoundedCornerShape(8.dp),
                        color = Color.LightGray.copy(alpha = 0.2f)
                    ) {
                        ImageView(
                            modifier = Modifier
                                // 2. The Magic Modifier
                                .sharedElement(
                                    rememberSharedContentState(key = "img-${item.id}"),
                                    animatedContentScope,
//                                    boundsTransform = boundsTransform
                                ),
                            path = item.metadata.path,
                            contentDescription = item.name
                        )
                    }

                    // New Loading/Tagging Overlay for Landscape
                    if (item.isTagging) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = Color.Black.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            CircularProgressIndicator(
                                    modifier = Modifier.align(Alignment.Center).size(20.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                        }
                    }
                }

                // 4. Middle Content (Name + Path)
                Column(modifier = Modifier.weight(3f)) {
                    Text(
                        modifier = Modifier
                            // 3. Connect the Text too!
                            .sharedElement(
                                rememberSharedContentState(key = "text-${item.id}"),
                                animatedContentScope,
                            ),
                        text = item.name,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    TextButton(
                        onClick = onPathClick,
                        modifier = Modifier.padding(0.dp).height(16.dp),
                        contentPadding = PaddingValues(0.dp),
                        shape = MaterialTheme.shapes.extraSmall,
                    ) {
                        Text(
                            text = item.metadata.path,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            modifier = Modifier.padding(0.dp)
                        )
                    }
                }

                // 5. Metadata Stats
                val sizeFormatted = formatFileSize(item.metadata.fileSizeBytes)
                val res = "${item.metadata.width}x${item.metadata.height}"
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Text(sizeFormatted, maxLines = 1, style = MaterialTheme.typography.titleMedium, color = Color.Gray)
                    Text(res, maxLines = 1, style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                }

                // 6. Tags Count
                Text(
                    "Tags\n${item.metadata.tags.size}",
                    color = Color(0xFF4CAF50),
                    maxLines = 2,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )

                // 7. Delete Action
                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.weight(1f),
                    enabled = !item.isTagging // Prevent deletion while busy
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete image",
                        tint = if (item.isTagging) Color.Gray else Color.Red.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }

}


@OptIn(ExperimentalComposeUiApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ThumbnailCard(
    item: ImageItem,
    focused: Boolean = false,
    selected: Boolean = false,
    isSelectionVisible: Boolean = false,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    onFocus: () -> Unit,
    // Updated callback to pass modifier info to ViewModel
    onToggleSelection: (isCtrl: Boolean, isShift: Boolean) -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
    dragHandler: @Composable () -> Unit = { DragHandle() },
) {

    var isHovered by remember { mutableStateOf(false) }
    val boundsTransform = BoundsTransform { _, _ ->
        tween(durationMillis = 500, easing = FastOutSlowInEasing)
    }
//    val sharedState = rememberSharedContentState(key = "item-${item.id}")
    with(sharedTransitionScope){
        Column(
        modifier = modifier
            .border(
                BorderStroke(
                    width = if (focused || selected) 3.dp else 1.dp,
                    color = when {
                        selected -> MaterialTheme.colorScheme.primary
                        focused -> MaterialTheme.colorScheme.secondary
                        isHovered -> MaterialTheme.colorScheme.outlineVariant
                        else -> Color.Transparent
                    }
                ),
                shape = RoundedCornerShape(12.dp)
            )
            .clip(RoundedCornerShape(12.dp))
            // Using pointerInput to catch Keyboard Modifiers on Desktop
            .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.first()

                    // CRITICAL FIX:
                    // If the Checkbox was clicked, it "consumed" this event.
                    // We stop here so the Column doesn't override the Checkbox logic.
                    if (change.isConsumed) {
                        continue
                    }

                    if (event.type == PointerEventType.Press) {
                        val config = event.keyboardModifiers
                        val isCtrl = config.isCtrlPressed || config.isMetaPressed
                        val isShift = config.isShiftPressed

                        // This handles the Column click (Simple, Ctrl, or Shift)
                        onToggleSelection(isCtrl, isShift)

                        // We consume this so no parents trigger
                        change.consume()
                    }
                }
            }
        }
            .onPointerEvent(PointerEventType.Enter) { isHovered = true }
            .onPointerEvent(PointerEventType.Exit) { isHovered = false }
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
            ) {
                // 1. Main Image Surface
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        ImageView(
                            modifier = Modifier
                                // 2. The Matching Magic Modifier
                                .sharedElement(
                                    rememberSharedContentState(key = "img-${item.id}"),
                                    animatedContentScope,
                                ),
                            path = item.metadata.path,
                            contentDescription = item.name,
                        )
                    }
                }

                // 2. Loading / Tagging Overlay
                // Intuition: If the app is busy, we dim the image and show a progress ring
                if (item.isTagging) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = Color.Black.copy(alpha = 0.4f), // Dimming effect
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = if (item.isTagging) "Tagging..." else "Loading...",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White
                            )
                        }
                    }
                }
                else {
                    if (isHovered) {
                        IconButton(

                            onClick = onDeleteClick,
                            modifier = Modifier.align(Alignment.TopEnd).padding(2.dp).size(24.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Delete,
                                contentDescription = "Delete",
                                tint = Color.Red,
                                modifier = Modifier.padding(2.dp).size(24.dp)
                            )
                        }
                    }
                    if (isSelectionVisible) {
                        Checkbox(
                            checked = selected,
                            onCheckedChange = { onToggleSelection(true, false) },
                            modifier = Modifier.align(Alignment.TopStart).padding(2.dp).size(18.dp),
                        )
                    } else if (isHovered) {
                        Surface(
                            color = if (isSystemInDarkTheme()) Color.Black.copy(alpha = 0.4f) else Color.White.copy(
                                alpha = 0.4f
                            ),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            dragHandler()
                        }
                    }

                }
            }

            Spacer(Modifier.height(8.dp))

            // 4. Filename
            Text(
                modifier = Modifier
                    // 3. Match the text key
                    .sharedElement(
                        rememberSharedContentState(key = "text-${item.id}"),
                        animatedContentScope,
                    ),
                text = item.name,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
                softWrap = false
            )
        }
    }

}


fun formatFileSize(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return if (mb > 1) String.format("%.1f MB", mb) else String.format("%.1f KB", kb)
}
