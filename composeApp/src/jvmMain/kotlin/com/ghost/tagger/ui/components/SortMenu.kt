package com.ghost.tagger.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ghost.tagger.data.enums.SortBy
import com.ghost.tagger.data.enums.SortOrder


@Composable
fun SortMenuButton(
    currentSortBy: SortBy,
    currentSortOrder: SortOrder,
    onSortByChange: (SortBy) -> Unit,
    onSortOrderChange: (SortOrder) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        // The Trigger Button
        FilledTonalIconButton(
            onClick = { expanded = true },
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        ) {
            Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = "Sort Options")
        }

        // The Dropdown Menu
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(200.dp).background(MaterialTheme.colorScheme.surfaceContainer)
        ) {
            // Section 1: Sort Criteria
            DropdownMenuItem(
                text = {
                    Text(
                        "Date Modified",
                        fontWeight = if (currentSortBy == SortBy.DATE) FontWeight.Bold else FontWeight.Normal
                    )
                },
                onClick = { onSortByChange(SortBy.DATE); expanded = false },
                leadingIcon = { Icon(Icons.Rounded.CalendarToday, null) },
                trailingIcon = { if (currentSortBy == SortBy.DATE) Icon(Icons.Rounded.Check, null) }
            )
            DropdownMenuItem(
                text = {
                    Text(
                        "File Name",
                        fontWeight = if (currentSortBy == SortBy.NAME) FontWeight.Bold else FontWeight.Normal
                    )
                },
                onClick = { onSortByChange(SortBy.NAME); expanded = false },
                leadingIcon = { Icon(Icons.Rounded.Abc, null) },
                trailingIcon = { if (currentSortBy == SortBy.NAME) Icon(Icons.Rounded.Check, null) }
            )
            DropdownMenuItem(
                text = {
                    Text(
                        "File Size",
                        fontWeight = if (currentSortBy == SortBy.SIZE) FontWeight.Bold else FontWeight.Normal
                    )
                },
                onClick = { onSortByChange(SortBy.SIZE); expanded = false },
                leadingIcon = { Icon(Icons.Rounded.SdStorage, null) },
                trailingIcon = { if (currentSortBy == SortBy.SIZE) Icon(Icons.Rounded.Check, null) }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Section 2: Order
            DropdownMenuItem(
                text = {
                    Text(
                        "Ascending",
                        fontWeight = if (currentSortOrder == SortOrder.ASCENDING) FontWeight.Bold else FontWeight.Normal
                    )
                },
                onClick = { onSortOrderChange(SortOrder.ASCENDING); expanded = false },
                leadingIcon = { Icon(Icons.Rounded.ArrowUpward, null) },
                trailingIcon = { if (currentSortOrder == SortOrder.ASCENDING) Icon(Icons.Rounded.Check, null) }
            )
            DropdownMenuItem(
                text = {
                    Text(
                        "Descending",
                        fontWeight = if (currentSortOrder == SortOrder.DESCENDING) FontWeight.Bold else FontWeight.Normal
                    )
                },
                onClick = { onSortOrderChange(SortOrder.DESCENDING); expanded = false },
                leadingIcon = { Icon(Icons.Rounded.ArrowDownward, null) },
                trailingIcon = { if (currentSortOrder == SortOrder.DESCENDING) Icon(Icons.Rounded.Check, null) }
            )
        }
    }
}