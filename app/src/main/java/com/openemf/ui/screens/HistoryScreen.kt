package com.openemf.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.openemf.sensors.api.Measurement
import com.openemf.ui.components.EScoreCompact
import com.openemf.ui.theme.getEScoreColor
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * History screen showing past measurements.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    measurements: List<Measurement>,
    onBackClick: () -> Unit,
    onDeleteClick: (Measurement) -> Unit,
    onMeasurementClick: (Measurement) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Measurement History") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { padding ->
        if (measurements.isEmpty()) {
            EmptyHistoryState(modifier = Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = measurements,
                    key = { it.id }
                ) { measurement ->
                    MeasurementHistoryCard(
                        measurement = measurement,
                        onClick = { onMeasurementClick(measurement) },
                        onDeleteClick = { onDeleteClick(measurement) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MeasurementHistoryCard(
    measurement: Measurement,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val dateFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
        .withZone(ZoneId.systemDefault())

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // E-Score
            EScoreCompact(score = measurement.eScore.score)

            Spacer(modifier = Modifier.width(16.dp))

            // Details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = dateFormatter.format(measurement.timestamp),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "${measurement.totalSources} sources detected",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "WiFi: ${measurement.wifiSources.size} • BT: ${measurement.bluetoothSources.size} • Cell: ${measurement.cellularSources.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Delete button
            IconButton(onClick = onDeleteClick) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun EmptyHistoryState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "No measurements yet",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Your measurement history will appear here",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Detailed view of a single measurement.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeasurementDetailScreen(
    measurement: Measurement,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.FULL)
        .withZone(ZoneId.systemDefault())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Measurement Details") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // E-Score card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = getEScoreColor(measurement.eScore.score).copy(alpha = 0.1f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "${measurement.eScore.score}",
                        style = MaterialTheme.typography.displayLarge,
                        color = getEScoreColor(measurement.eScore.score)
                    )
                    Text(
                        text = "E-Score",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            // Timestamp
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Measured at",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = dateFormatter.format(measurement.timestamp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            // Source counts
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Sources Detected",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        SourceCountItem("WiFi", measurement.wifiSources.size)
                        SourceCountItem("Bluetooth", measurement.bluetoothSources.size)
                        SourceCountItem("Cellular", measurement.cellularSources.size)
                    }
                }
            }

            // Context info
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Context",
                        style = MaterialTheme.typography.titleMedium
                    )

                    DetailRow("Motion", measurement.context.motionState.name.lowercase())
                    DetailRow("Foreground", if (measurement.context.isForeground) "Yes" else "No")
                    measurement.context.locationHash?.let {
                        DetailRow("Location Hash", it.take(8) + "...")
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceCountItem(label: String, count: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
