package com.openemf.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.openemf.data.database.PlaceIcon

/**
 * Dialog for adding a new favorite place.
 */
@Composable
fun AddPlaceDialog(
    latitude: Double,
    longitude: Double,
    onDismiss: () -> Unit,
    onConfirm: (name: String, icon: PlaceIcon) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedIcon by remember { mutableStateOf(PlaceIcon.HOME) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Place") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Name input
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Place Name") },
                    placeholder = { Text("e.g., Home, Office") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Icon selection
                Text(
                    text = "Choose Icon",
                    style = MaterialTheme.typography.labelMedium
                )

                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.height(200.dp)
                ) {
                    items(PlaceIcon.entries) { icon ->
                        IconOption(
                            icon = icon,
                            isSelected = selectedIcon == icon,
                            onClick = { selectedIcon = icon }
                        )
                    }
                }

                // Location info
                Text(
                    text = "Location: ${String.format("%.4f", latitude)}, ${String.format("%.4f", longitude)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.ifBlank { selectedIcon.displayName }, selectedIcon) },
                enabled = name.isNotBlank() || selectedIcon != PlaceIcon.OTHER
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun IconOption(
    icon: PlaceIcon,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        tonalElevation = if (isSelected) 4.dp else 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = icon.emoji,
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = icon.displayName,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}
