package com.openemf.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.openemf.ui.theme.getEScoreColor
import kotlin.math.cos
import kotlin.math.sin

// 6-level threshold scores
private const val EXCELLENT_THRESHOLD = 10   // Excellent/Good boundary
private const val GOOD_THRESHOLD = 25        // Good/Moderate boundary
private const val MODERATE_THRESHOLD = 50    // Moderate/Elevated boundary
private const val ELEVATED_THRESHOLD = 75    // Elevated/High boundary
private const val HIGH_THRESHOLD = 90        // High/Very High boundary

/**
 * Circular gauge component showing the E-Score with threshold markers.
 * Uses 6-level color scale from Excellent (emerald) to Very High (red).
 * The full circumference is color-coded based on the current score level.
 */
@Composable
fun EScoreGauge(
    score: Int,
    label: String,
    modifier: Modifier = Modifier,
    size: Dp = 200.dp,
    strokeWidth: Dp = 16.dp,
    animated: Boolean = true,
    showThresholds: Boolean = true
) {
    val animatedScore by animateFloatAsState(
        targetValue = score.toFloat(),
        animationSpec = tween(durationMillis = if (animated) 1000 else 0),
        label = "score"
    )

    val scoreColor = getEScoreColor(score)
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidthPx = strokeWidth.toPx()
            val arcSize = Size(
                width = this.size.width - strokeWidthPx,
                height = this.size.height - strokeWidthPx
            )
            val topLeft = Offset(strokeWidthPx / 2, strokeWidthPx / 2)
            val center = Offset(this.size.width / 2, this.size.height / 2)
            val radius = (this.size.width - strokeWidthPx) / 2

            // Draw background - full circumference tinted with score color
            // This makes the entire ring color-coded based on the current reading
            drawArc(
                color = scoreColor.copy(alpha = 0.15f),
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
            )

            // Draw zone indicators (subtle markers at thresholds)
            if (showThresholds) {
                // Draw threshold tick marks for all 5 boundaries
                drawThresholdTick(center, radius, EXCELLENT_THRESHOLD, strokeWidthPx, onSurfaceVariant)
                drawThresholdTick(center, radius, GOOD_THRESHOLD, strokeWidthPx, onSurfaceVariant)
                drawThresholdTick(center, radius, MODERATE_THRESHOLD, strokeWidthPx, onSurfaceVariant)
                drawThresholdTick(center, radius, ELEVATED_THRESHOLD, strokeWidthPx, onSurfaceVariant)
                drawThresholdTick(center, radius, HIGH_THRESHOLD, strokeWidthPx, onSurfaceVariant)
            }

            // Score arc (foreground) - shows current score level with full color
            val sweepAngle = (animatedScore / 100f) * 270f
            drawArc(
                color = scoreColor,
                startAngle = 135f,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
            )

            // Inner glow circle for emphasis
            val innerRadius = radius - strokeWidthPx - 8f
            if (innerRadius > 0) {
                drawCircle(
                    color = scoreColor.copy(alpha = 0.08f),
                    radius = innerRadius,
                    center = center
                )
            }
        }

        // Score text
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = score.toString(),
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = scoreColor
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Draw a tick mark at the specified threshold score position.
 */
private fun DrawScope.drawThresholdTick(
    center: Offset,
    radius: Float,
    threshold: Int,
    strokeWidth: Float,
    color: Color
) {
    // Convert threshold (0-100) to angle (gauge goes from 135° to 405°, i.e., 270° sweep)
    val angle = 135f + (threshold / 100f) * 270f
    val angleRad = Math.toRadians(angle.toDouble())

    // Calculate tick positions (inner and outer edges of the arc)
    val innerRadius = radius - strokeWidth / 2 - 4
    val outerRadius = radius + strokeWidth / 2 + 4

    val startX = center.x + innerRadius * cos(angleRad).toFloat()
    val startY = center.y + innerRadius * sin(angleRad).toFloat()
    val endX = center.x + outerRadius * cos(angleRad).toFloat()
    val endY = center.y + outerRadius * sin(angleRad).toFloat()

    drawLine(
        color = color.copy(alpha = 0.6f),
        start = Offset(startX, startY),
        end = Offset(endX, endY),
        strokeWidth = 2f
    )
}

/**
 * Compact version of the E-Score display.
 */
@Composable
fun EScoreCompact(
    score: Int,
    modifier: Modifier = Modifier
) {
    val scoreColor = getEScoreColor(score)

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Color indicator
        Canvas(modifier = Modifier.size(12.dp)) {
            drawCircle(color = scoreColor)
        }

        Text(
            text = score.toString(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = scoreColor
        )
    }
}
