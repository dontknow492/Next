package com.ghost.tagger.ui.section

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ghost.tagger.data.models.settings.ModelType
import com.ghost.tagger.data.models.settings.TaggerSettings
import com.ghost.tagger.ui.components.ModelSelector
import com.ghost.tagger.ui.components.TagsSection
import com.ghost.tagger.ui.components.rememberDirectoryPicker
import com.ghost.tagger.ui.viewmodels.SettingsViewModel
import com.ghost.tagger.ui.viewmodels.TaggerViewModel
import org.koin.compose.viewmodel.koinViewModel
import java.io.File

@Composable
fun SettingsSidebar() {
    // 1. MVVM Injection: Get the "Conductor"
    val viewModel: SettingsViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsState()
    val settings = uiState.settings

    val openDir = rememberDirectoryPicker(title = "Open Image Folder") { file ->
        if (file != null) {
            viewModel.setModelDownloadPath(file)
        }
    }

    AnimatedVisibility(
        visible = settings.session.isSidebarVisible,
        enter = slideInHorizontally(
            // start the content off‑screen to the left
            initialOffsetX = { -it }
        ),
        exit = slideOutHorizontally(
            // slide the content off‑screen to the left
            targetOffsetX = { -it }
        )
    ) {
        Surface(
            modifier = Modifier.width(settings.session.sidePanelWidthDp.dp).fillMaxHeight(),
            color = MaterialTheme.colorScheme.surface, // Clean background
            tonalElevation = 1.dp, // Slight separation from gallery
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()), // Make it scrollable
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Header
                Text(
                    text = "Model Configuration",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                PathSelector(
                    label = "Model Download Folder",
                    path = settings.modelDownloadPath,
                    onFolderClick = {},
                    onClick = openDir,
                )

                // 2. Mode Switcher (Tagger vs Descriptor)
                // Intuition: Changing this creates a "New Context" for the user
                SingleChoiceSegmentedButton(
                    selected = settings.lastModelType,
                    onSelect = { viewModel.setModelType(it) }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(0.5f))

                // 3. Dynamic Content Section
                // We use AnimatedVisibility for a smooth "Slide" feel when switching modes
                AnimatedVisibility(
                    visible = settings.lastModelType == ModelType.TAGGER,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    TaggerSettingsSection(viewModel, settings.tagger)
                }

                AnimatedVisibility(
                    visible = settings.lastModelType == ModelType.DESCRIPTOR,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    DescriptorSettingsSection(viewModel, settings.descriptor)
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(0.5f))

                // 4. Global System Settings (Always visible)
                SystemSettingsSection(viewModel, settings.system)
            }
        }
    }


}

// =========================================================================
// SECTIONS (The "Molecules")
// =========================================================================

@Composable
fun TaggerSettingsSection(viewModel: SettingsViewModel, settings: TaggerSettings) {
    // Tagger Settings
    val viewModel: TaggerViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        SectionHeader("Inference Settings", Icons.Rounded.Psychology)

        // Model Path
        ModelSelector(
            selectedModelId = uiState.selectedModelId,
            models = uiState.models,
            onModelSelected = viewModel::selectModel,
            onDownloadClick = viewModel::downloadModel,
            onOpenFolderClick = viewModel::openInExplorer,
//            models = settings.models,
        )

        // Confidence
        SliderSetting(
            label = "Confidence Threshold",
            value = uiState.confidenceThreshold,
            range = 0f..1f,
            displayValue = "${(uiState.confidenceThreshold * 100).toInt()}%",
            onValueChange = { viewModel.setConfidenceThreshold(it) },
            hint = "Higher values reduce false positives."
        )

        // Max Tags
        StepperSetting(
            label = "Max Tags",
            value = uiState.maxTags,
            range = 1..50,
            onValueChange = { viewModel.setMaxTags(it) }
        )

        // Excluded Tags
        // (Placeholder for a complex Chip Input - kept simple for now)
        TagsSection(
            title = "Excluded Tags",
            tags = uiState.excludedTags.toList(),
            onRemove = viewModel::removeExcludedTag,
            onAdd = viewModel::addExcludedTag,
            onClear = viewModel::clearExcludedTags,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun DescriptorSettingsSection(
    viewModel: SettingsViewModel,
    settings: com.ghost.tagger.data.models.settings.DescriptorSettings
) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        SectionHeader("Generation Settings", Icons.Rounded.AutoAwesome)

//        PathSelector(
//            label = "Model File (.onnx)",
//            path = settings.modelPath,
//            onClick = {},
//            onFolderClick = {}
//        )

        // Temperature (Creativity)
        SliderSetting(
            label = "Temperature (Creativity)",
            value = settings.temperature,
            range = 0.1f..2.0f,
            displayValue = String.format("%.1f", settings.temperature),
            onValueChange = { viewModel.setDescriptorTemperature(it) },
            hint = "Low = Accurate. High = Creative but chaotic."
        )

        // Description Length
        StepperSetting(
            label = "Max Token Length",
            value = settings.maxDescriptionLength,
            range = 10..200,
            onValueChange = { viewModel.setDescriptorLength(it) }
        )

        // Visual Prompt Toggle
        SwitchSetting(
            label = "Use Visual Prompting",
            checked = settings.useVisualPrompt,
            onCheckedChange = { viewModel.setVisualPrompt(it) }
        )
    }
}

@Composable
fun SystemSettingsSection(
    viewModel: SettingsViewModel,
    settings: com.ghost.tagger.data.models.settings.SystemSettings
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionHeader("System & Performance", Icons.Rounded.Memory)

        SwitchSetting(
            label = "Use GPU Acceleration",
            checked = settings.useGpu,
            onCheckedChange = { viewModel.setUseGpu(it) },
            description = "Requires CUDA or DirectML support."
        )

        SwitchSetting(
            label = "Parallel Processing",
            checked = settings.parallelProcessing,
            onCheckedChange = { viewModel.setParallelProcessing(it) },
            description = "High CPU usage. Processes multiple images at once."
        )

        SwitchSetting(
            label = "Auto Save to EXIF",
            checked = settings.autoSaveToExif,
            onCheckedChange = { viewModel.setAutoSaveToExif(it) },
            description = "Save tag/caption to image metadata(EXIF) automatically",
        )
        SwitchSetting(
            label = "Write to XMP",
            checked = settings.writeXmp,
            onCheckedChange = { viewModel.saveToXmpFile(it) },
            description = "Save tag/caption to <filename>.xmp file",
        )
    }
}


// =========================================================================
// ATOMS (Reusable Aesthetic Components)
// =========================================================================

@Composable
fun SectionHeader(title: String, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun SingleChoiceSegmentedButton(selected: ModelType, onSelect: (ModelType) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        ModelType.values().forEach { type ->
            val isSelected = selected == type
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(
                        color = if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
                        shape = RoundedCornerShape(6.dp)
                    )
                    .clickable { onSelect(type) }
                    .shadow(if (isSelected) 2.dp else 0.dp, RoundedCornerShape(6.dp)), // Subtle lift
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = type.name.lowercase().capitalize(), // "Tagger"
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun SliderSetting(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    displayValue: String,
    onValueChange: (Float) -> Unit,
    hint: String? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(displayValue, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier.height(24.dp) // Compact slider
        )
        if (hint != null) {
            Text(hint, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SwitchSetting(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, description: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            if (description != null) {
                Text(
                    description,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, modifier = Modifier.scale(0.8f))
    }
}

@Composable
fun PathSelector(
    label: String,
    path: File,
    onClick: () -> Unit,
    onFolderClick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

@Composable
fun StepperSetting(label: String, value: Int, range: IntRange, onValueChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = { onValueChange((value - 1).coerceIn(range)) }, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Rounded.Remove, null)
            }
            Text("$value", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(24.dp))
            IconButton(onClick = { onValueChange((value + 1).coerceIn(range)) }, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Rounded.Add, null)
            }
        }
    }
}

// Helper to fix the capitalize deprecation if needed
fun String.capitalize() = replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }