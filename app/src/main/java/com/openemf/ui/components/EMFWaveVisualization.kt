package com.openemf.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.unit.dp
import com.openemf.sensors.api.SourceType
import com.openemf.ui.theme.getEScoreColor
import kotlin.math.*

/**
 * Elegant 3D-style EMF wave visualization.
 * Shows animated waves emanating from center, color-coded by source type,
 * with intensity based on actual signal readings.
 *
 * Visual design:
 * - Concentric waves pulse outward from center
 * - WiFi: Blue waves (cool, digital feel)
 * - Bluetooth: Purple/Indigo waves (short-range, personal)
 * - Cellular: Orange/Amber waves (powerful, far-reaching)
 * - Wave intensity reflects actual signal strength
 * - Subtle 3D perspective with depth gradient
 */
@Composable
fun EMFWaveVisualization(
    wifiIntensity: Float,      // 0-1, based on aggregate WiFi signal
    bluetoothIntensity: Float, // 0-1, based on aggregate Bluetooth signal
    cellularIntensity: Float,  // 0-1, based on aggregate Cellular signal
    overallScore: Int,         // 0-100 E-Score for center glow
    modifier: Modifier = Modifier,
    isAnimating: Boolean = true
) {
    // Animation states for each wave layer
    val infiniteTransition = rememberInfiniteTransition(label = "emf_waves")

    // Staggered wave animations for depth effect
    val wavePhase1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave1"
    )

    val wavePhase2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave2"
    )

    val wavePhase3 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave3"
    )

    // Breathing/pulse animation for center
    val breathe by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathe"
    )

    // Rotation for subtle 3D effect
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    // Colors
    val wifiColor = Color(0xFF3B82F6)      // Blue
    val bluetoothColor = Color(0xFF8B5CF6) // Purple
    val cellularColor = Color(0xFFF59E0B)  // Amber

    // Score-based center color (6-level thresholds)
    val centerColor = getEScoreColor(overallScore)

    val backgroundColor = MaterialTheme.colorScheme.surface

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
    ) {
        val center = Offset(size.width / 2, size.height / 2)
        val maxRadius = size.minDimension / 2 * 0.95f

        // Draw background gradient for depth
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    backgroundColor,
                    backgroundColor.copy(alpha = 0.95f),
                    Color(0xFF0F172A).copy(alpha = 0.3f)
                ),
                center = center,
                radius = maxRadius
            ),
            radius = maxRadius,
            center = center
        )

        // Draw grid lines for 3D effect
        drawPerspectiveGrid(center, maxRadius, rotation, 0.15f)

        // Draw wave layers (back to front for proper depth)
        if (cellularIntensity > 0.01f) {
            drawWaveLayer(
                center = center,
                maxRadius = maxRadius,
                phase = wavePhase3,
                color = cellularColor,
                intensity = cellularIntensity,
                waveCount = 3,
                strokeWidth = 2.5f + cellularIntensity * 2f
            )
        }

        if (bluetoothIntensity > 0.01f) {
            drawWaveLayer(
                center = center,
                maxRadius = maxRadius * 0.7f, // Bluetooth is shorter range
                phase = wavePhase2,
                color = bluetoothColor,
                intensity = bluetoothIntensity,
                waveCount = 4,
                strokeWidth = 2f + bluetoothIntensity * 1.5f
            )
        }

        if (wifiIntensity > 0.01f) {
            drawWaveLayer(
                center = center,
                maxRadius = maxRadius * 0.85f,
                phase = wavePhase1,
                color = wifiColor,
                intensity = wifiIntensity,
                waveCount = 5,
                strokeWidth = 2f + wifiIntensity * 2f
            )
        }

        // Draw particle effects for high intensity signals
        if (wifiIntensity > 0.5f || bluetoothIntensity > 0.5f || cellularIntensity > 0.5f) {
            drawParticleField(
                center = center,
                maxRadius = maxRadius,
                phase = wavePhase1,
                wifiIntensity = wifiIntensity,
                bluetoothIntensity = bluetoothIntensity,
                cellularIntensity = cellularIntensity,
                wifiColor = wifiColor,
                bluetoothColor = bluetoothColor,
                cellularColor = cellularColor
            )
        }

        // Draw center glow
        val glowRadius = 40f * breathe
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    centerColor.copy(alpha = 0.8f),
                    centerColor.copy(alpha = 0.4f),
                    centerColor.copy(alpha = 0.1f),
                    Color.Transparent
                ),
                center = center,
                radius = glowRadius * 2
            ),
            radius = glowRadius * 2,
            center = center
        )

        // Draw center core
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White,
                    centerColor.copy(alpha = 0.9f),
                    centerColor
                ),
                center = center,
                radius = glowRadius * 0.6f
            ),
            radius = glowRadius * 0.6f,
            center = center
        )

        // Draw signal strength arcs around center
        drawSignalArcs(
            center = center,
            radius = glowRadius * 1.5f,
            wifiIntensity = wifiIntensity,
            bluetoothIntensity = bluetoothIntensity,
            cellularIntensity = cellularIntensity,
            wifiColor = wifiColor,
            bluetoothColor = bluetoothColor,
            cellularColor = cellularColor,
            rotation = rotation
        )
    }
}

/**
 * Draw a single wave layer with multiple concentric rings.
 */
private fun DrawScope.drawWaveLayer(
    center: Offset,
    maxRadius: Float,
    phase: Float,
    color: Color,
    intensity: Float,
    waveCount: Int,
    strokeWidth: Float
) {
    for (i in 0 until waveCount) {
        val waveOffset = i.toFloat() / waveCount
        val animatedPhase = (phase + waveOffset) % 1f

        // Ease out for natural wave dissipation
        val easedPhase = 1f - (1f - animatedPhase).pow(2)
        val radius = maxRadius * easedPhase

        // Alpha fades as wave expands
        val alpha = (1f - easedPhase) * intensity * 0.7f

        if (alpha > 0.01f && radius > 10f) {
            // Draw main wave circle
            drawCircle(
                color = color.copy(alpha = alpha),
                radius = radius,
                center = center,
                style = Stroke(
                    width = strokeWidth * (1f - easedPhase * 0.5f),
                    cap = StrokeCap.Round
                )
            )

            // Draw subtle inner glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.Transparent,
                        color.copy(alpha = alpha * 0.3f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = radius
                ),
                radius = radius,
                center = center
            )
        }
    }
}

/**
 * Draw perspective grid lines for 3D depth effect.
 */
private fun DrawScope.drawPerspectiveGrid(
    center: Offset,
    maxRadius: Float,
    rotation: Float,
    alpha: Float
) {
    val gridColor = Color.White.copy(alpha = alpha * 0.3f)
    val lineCount = 8

    // Draw concentric circles
    for (i in 1..4) {
        val radius = maxRadius * (i / 4f)
        drawCircle(
            color = gridColor.copy(alpha = alpha * (1f - i / 5f)),
            radius = radius,
            center = center,
            style = Stroke(width = 0.5f)
        )
    }

    // Draw radial lines with rotation
    for (i in 0 until lineCount) {
        val angle = Math.toRadians((rotation + i * 360.0 / lineCount))
        val endX = center.x + cos(angle).toFloat() * maxRadius
        val endY = center.y + sin(angle).toFloat() * maxRadius

        drawLine(
            color = gridColor,
            start = center,
            end = Offset(endX, endY),
            strokeWidth = 0.5f
        )
    }
}

/**
 * Draw floating particles for high-intensity signals.
 */
private fun DrawScope.drawParticleField(
    center: Offset,
    maxRadius: Float,
    phase: Float,
    wifiIntensity: Float,
    bluetoothIntensity: Float,
    cellularIntensity: Float,
    wifiColor: Color,
    bluetoothColor: Color,
    cellularColor: Color
) {
    val particleCount = 20

    for (i in 0 until particleCount) {
        // Deterministic but varied positions based on index
        val seed = i * 137.5f // Golden angle for good distribution
        val angleOffset = seed % 360f
        val radiusOffset = (seed * 0.618f) % 1f // Golden ratio

        val animatedRadius = ((phase + radiusOffset) % 1f) * maxRadius * 0.8f
        val angle = Math.toRadians((angleOffset + phase * 60).toDouble())

        val x = center.x + cos(angle).toFloat() * animatedRadius
        val y = center.y + sin(angle).toFloat() * animatedRadius

        // Choose color based on which signal is strongest at this position
        val (color, intensity) = when {
            i % 3 == 0 && wifiIntensity > 0.3f -> wifiColor to wifiIntensity
            i % 3 == 1 && bluetoothIntensity > 0.3f -> bluetoothColor to bluetoothIntensity
            i % 3 == 2 && cellularIntensity > 0.3f -> cellularColor to cellularIntensity
            else -> return@drawParticleField
        }

        val particleAlpha = (1f - animatedRadius / (maxRadius * 0.8f)) * intensity * 0.6f
        val particleSize = 3f + intensity * 4f

        if (particleAlpha > 0.05f) {
            drawCircle(
                color = color.copy(alpha = particleAlpha),
                radius = particleSize,
                center = Offset(x, y)
            )
        }
    }
}

/**
 * Draw signal strength indicator arcs around center.
 */
private fun DrawScope.drawSignalArcs(
    center: Offset,
    radius: Float,
    wifiIntensity: Float,
    bluetoothIntensity: Float,
    cellularIntensity: Float,
    wifiColor: Color,
    bluetoothColor: Color,
    cellularColor: Color,
    rotation: Float
) {
    val arcStroke = 4f
    val gap = 8f // degrees

    // WiFi arc (top-right quadrant)
    if (wifiIntensity > 0.01f) {
        val sweepAngle = 100f * wifiIntensity
        drawArc(
            color = wifiColor.copy(alpha = 0.8f),
            startAngle = -90f + rotation * 0.1f,
            sweepAngle = sweepAngle,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
            style = Stroke(width = arcStroke, cap = StrokeCap.Round)
        )
    }

    // Bluetooth arc (bottom-right quadrant)
    if (bluetoothIntensity > 0.01f) {
        val sweepAngle = 100f * bluetoothIntensity
        drawArc(
            color = bluetoothColor.copy(alpha = 0.8f),
            startAngle = 30f + rotation * 0.1f,
            sweepAngle = sweepAngle,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
            style = Stroke(width = arcStroke, cap = StrokeCap.Round)
        )
    }

    // Cellular arc (left side)
    if (cellularIntensity > 0.01f) {
        val sweepAngle = 100f * cellularIntensity
        drawArc(
            color = cellularColor.copy(alpha = 0.8f),
            startAngle = 150f + rotation * 0.1f,
            sweepAngle = sweepAngle,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
            style = Stroke(width = arcStroke, cap = StrokeCap.Round)
        )
    }
}

/**
 * Compact wave visualization for use in cards or smaller spaces.
 */
@Composable
fun EMFWaveMini(
    intensity: Float,
    sourceType: SourceType,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "mini_wave")

    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "mini_phase"
    )

    val color = when (sourceType) {
        SourceType.WIFI -> Color(0xFF3B82F6)
        SourceType.BLUETOOTH -> Color(0xFF8B5CF6)
        SourceType.CELLULAR -> Color(0xFFF59E0B)
    }

    Canvas(
        modifier = modifier.size(48.dp)
    ) {
        val center = Offset(size.width / 2, size.height / 2)
        val maxRadius = size.minDimension / 2 * 0.9f

        // Draw 3 concentric waves
        for (i in 0 until 3) {
            val waveOffset = i / 3f
            val animatedPhase = (phase + waveOffset) % 1f
            val radius = maxRadius * animatedPhase
            val alpha = (1f - animatedPhase) * intensity * 0.8f

            if (alpha > 0.01f) {
                drawCircle(
                    color = color.copy(alpha = alpha),
                    radius = radius,
                    center = center,
                    style = Stroke(width = 2f)
                )
            }
        }

        // Center dot
        drawCircle(
            color = color,
            radius = 4f,
            center = center
        )
    }
}

/**
 * Helper to convert RSSI to intensity (0-1).
 * Uses the exposure index formula for consistency.
 */
fun rssiToIntensity(rssiDbm: Int): Float {
    return when {
        rssiDbm < -100 -> 0.1f
        rssiDbm > -30 -> 1f
        else -> ((rssiDbm + 100) / 70f).coerceIn(0.1f, 1f)
    }
}

/**
 * Calculate aggregate intensity from multiple sources.
 */
fun calculateAggregateIntensity(rssiValues: List<Int>): Float {
    if (rssiValues.isEmpty()) return 0f

    // Use the strongest signal as primary, with diminishing contribution from others
    val sorted = rssiValues.sortedDescending()
    var totalIntensity = 0f
    var weight = 1f

    for (rssi in sorted.take(5)) { // Consider top 5 sources
        totalIntensity += rssiToIntensity(rssi) * weight
        weight *= 0.5f // Each subsequent source contributes less
    }

    return totalIntensity.coerceIn(0f, 1f)
}
