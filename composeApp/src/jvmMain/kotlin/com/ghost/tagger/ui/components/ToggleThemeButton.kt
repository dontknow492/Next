package com.ghost.tagger.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.ghost.tagger.data.enums.ThemeMode


@Composable
fun ToggleThemeButton(
    modifier: Modifier = Modifier,
    mode: ThemeMode,
    onModeChange: (ThemeMode) -> Unit,
    showText: Boolean = false
) {
    val (icon, label) = when (mode) {
        ThemeMode.DARK -> Icons.Outlined.DarkMode to "Dark"
        ThemeMode.LIGHT -> Icons.Outlined.LightMode to "Light"
        ThemeMode.AUTO -> Icons.Outlined.BrightnessAuto to "Auto"
    }

    val backgroundColor by animateColorAsState(
        targetValue = when (mode) {
            ThemeMode.DARK -> MaterialTheme.colorScheme.surfaceVariant
            ThemeMode.LIGHT -> MaterialTheme.colorScheme.surfaceVariant
            ThemeMode.AUTO -> MaterialTheme.colorScheme.surfaceVariant
        },
        label = "theme-bg"
    )

    Surface(
        modifier = modifier
            .clip(CircleShape)
            .clickable {
                val next = when (mode) {
                    ThemeMode.AUTO -> ThemeMode.LIGHT
                    ThemeMode.LIGHT -> ThemeMode.DARK
                    ThemeMode.DARK -> ThemeMode.AUTO
                }
                onModeChange(next)
            }
            .animateContentSize(),
        color = backgroundColor,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.onSurface
            )
            if (showText) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}