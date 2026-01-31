package com.ghost.tagger.ui.components

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ghost.tagger.data.viewmodels.TaggerViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun LeftNavigationBar(
    modifier: Modifier = Modifier,
    isSettingVisible: Boolean = false,
    onSettingClick: () -> Unit,
) {
    val taggerViewModel: TaggerViewModel = koinViewModel()
    val uiState by taggerViewModel.uiState.collectAsStateWithLifecycle()

    val arrowRotation by animateFloatAsState(
        targetValue = if(isSettingVisible) 0f else 180f,
        label = "Setting Arrow Rotation",
        animationSpec = tween(durationMillis = 300, easing = LinearOutSlowInEasing)
    )

    NavigationRail(
//        backgroundColor = MaterialTheme.colorScheme.surface,
//        elevation = 2.dp,
        modifier = modifier.width(72.dp).fillMaxHeight() // Standard rail width
    ) {
        // ... Top items (Gallery, Settings, etc.) ...
        IconButton(
            onClick = onSettingClick,
        ){
            Icon(
                imageVector = Icons.Rounded.ArrowBackIosNew,
                contentDescription = "Setting Button",
                modifier = Modifier.rotate(arrowRotation),
            )
        }

        Spacer(Modifier.weight(1f)) // Push content to bottom

        // --- Bottom Area ---
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            // The Download Widget
            DownloadSidebarItem(
                isDownloading = uiState.isDownloading,
                progress = uiState.downloadStatus?.progress ?: 0f,
                status = uiState.downloadStatus,
                activeModelName = uiState.selectedModelId
            )
        }
    }
}