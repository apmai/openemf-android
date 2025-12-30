package com.openemf.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import com.openemf.sensors.api.ScanBudget

/**
 * Main scan button with loading state and budget indicator.
 */
@Composable
fun ScanButton(
    isScanning: Boolean,
    budget: ScanBudget,
    onScanClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "scan")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Main button
        Button(
            onClick = onScanClick,
            enabled = !isScanning && budget.canScan(),
            modifier = Modifier.height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            if (isScanning) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Scanning",
                    modifier = Modifier
                        .size(24.dp)
                        .rotate(rotation)
                )
                Spacer(Modifier.width(8.dp))
                Text("Scanning...")
            } else if (!budget.canScan()) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Throttled",
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("Throttled")
            } else {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Scan",
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("Measure Now")
            }
        }

        // Budget indicator
        ScanBudgetIndicator(budget = budget)
    }
}

/**
 * Shows remaining scan budget.
 */
@Composable
fun ScanBudgetIndicator(
    budget: ScanBudget,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Scan dots
        repeat(ScanBudget.MAX_SCANS_PER_WINDOW) { index ->
            val isUsed = index >= budget.scansRemaining
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .padding(1.dp)
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = MaterialTheme.shapes.small,
                    color = if (isUsed) {
                        MaterialTheme.colorScheme.surfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                ) {}
            }
        }

        Spacer(Modifier.width(8.dp))

        // Text label
        Text(
            text = if (budget.isThrottled) {
                "Resets in ${budget.windowResetInSeconds}s"
            } else {
                "${budget.scansRemaining} scans left"
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Floating action button for quick scan.
 */
@Composable
fun ScanFAB(
    isScanning: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "fab")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    FloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.primary
    ) {
        Icon(
            imageVector = Icons.Default.Refresh,
            contentDescription = "Scan",
            modifier = if (isScanning) Modifier.rotate(rotation) else Modifier
        )
    }
}
