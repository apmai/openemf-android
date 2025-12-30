package com.openemf.ui.theme

import androidx.compose.ui.graphics.Color

// Primary brand colors
val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

// OpenEMF brand colors
val OpenEMFPrimary = Color(0xFF6366F1) // Indigo
val OpenEMFPrimaryDark = Color(0xFF4F46E5)
val OpenEMFSecondary = Color(0xFF10B981) // Emerald
val OpenEMFTertiary = Color(0xFFF59E0B) // Amber

// E-Score colors (6-level scale)
val ScoreExcellent = Color(0xFF10B981) // Emerald - Excellent (0-10)
val ScoreGood = Color(0xFF22C55E) // Green - Good (11-25)
val ScoreModerate = Color(0xFFEAB308) // Yellow - Moderate (26-50)
val ScoreElevated = Color(0xFFF97316) // Orange - Elevated (51-75)
val ScoreHigh = Color(0xFFEF4444) // Red - High (76-90)
val ScoreVeryHigh = Color(0xFFDC2626) // Dark Red - Very High (90+)

// Source type colors
val WiFiColor = Color(0xFF3B82F6) // Blue
val BluetoothColor = Color(0xFF8B5CF6) // Violet/Purple
val CellularColor = Color(0xFFF59E0B) // Amber/Orange

// Surface colors
val SurfaceLight = Color(0xFFFFFBFE)
val SurfaceDark = Color(0xFF1C1B1F)
val SurfaceContainerLight = Color(0xFFF3F4F6)
val SurfaceContainerDark = Color(0xFF2D2D30)

// Background
val BackgroundLight = Color(0xFFFFFBFE)
val BackgroundDark = Color(0xFF121212)

/**
 * Get color for E-Score value using 6-level thresholds.
 * 0-10: Excellent, 11-25: Good, 26-50: Moderate, 51-75: Elevated, 76-90: High, 90+: Very High
 */
fun getEScoreColor(score: Int): Color = when {
    score <= 10 -> ScoreExcellent  // Emerald - Excellent (Very low exposure)
    score <= 25 -> ScoreGood       // Green - Good (Low exposure)
    score <= 50 -> ScoreModerate   // Yellow - Moderate (Typical urban levels)
    score <= 75 -> ScoreElevated   // Orange - Elevated (Above average)
    score <= 90 -> ScoreHigh       // Red - High (Consider reducing sources)
    else -> ScoreVeryHigh          // Dark Red - Very High
}

/**
 * Get color for source type
 */
fun getSourceTypeColor(type: String): Color = when (type.lowercase()) {
    "wifi" -> WiFiColor
    "bluetooth" -> BluetoothColor
    "cellular" -> CellularColor
    else -> Color.Gray
}
