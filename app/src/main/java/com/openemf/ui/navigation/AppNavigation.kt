package com.openemf.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.openemf.data.database.MeasurementEntity
import com.openemf.data.repository.PlaceWithStats
import com.openemf.sensors.api.*
import com.openemf.ui.screens.DashboardScreen
import com.openemf.ui.screens.MapScreen
import com.openemf.ui.screens.StatisticsScreen
import com.openemf.ui.screens.SolutionsScreen

/**
 * Navigation destinations for the app.
 */
enum class NavDestination(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    LIVE(
        label = "Live",
        selectedIcon = Icons.Filled.Sensors,
        unselectedIcon = Icons.Outlined.Sensors
    ),
    MAP(
        label = "Map",
        selectedIcon = Icons.Filled.Map,
        unselectedIcon = Icons.Outlined.Map
    ),
    STATISTICS(
        label = "Statistics",
        selectedIcon = Icons.Filled.BarChart,
        unselectedIcon = Icons.Outlined.BarChart
    ),
    SOLUTIONS(
        label = "Solutions",
        selectedIcon = Icons.Filled.Lightbulb,
        unselectedIcon = Icons.Outlined.Lightbulb
    )
}

/**
 * Main app scaffold with bottom navigation.
 * Provides navigation between Live, Map, Statistics, and Solutions screens.
 */
@Composable
fun MainAppScaffold(
    sensorState: SensorState,
    scanBudget: ScanBudget,
    lastMeasurement: Measurement?,
    measurementHistory: List<Measurement>,
    storedMeasurements: List<MeasurementEntity>,
    places: List<PlaceWithStats>,
    onScanClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onAddPlace: (Double, Double) -> Unit,
    onPlaceClick: (PlaceWithStats) -> Unit,
    modifier: Modifier = Modifier
) {
    var currentDestination by remember { mutableStateOf(NavDestination.LIVE) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavDestination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = currentDestination == destination,
                        onClick = { currentDestination = destination },
                        icon = {
                            Icon(
                                imageVector = if (currentDestination == destination) {
                                    destination.selectedIcon
                                } else {
                                    destination.unselectedIcon
                                },
                                contentDescription = destination.label
                            )
                        },
                        label = { Text(destination.label) }
                    )
                }
            }
        },
        modifier = modifier
    ) { padding ->
        // Content based on current destination
        when (currentDestination) {
            NavDestination.LIVE -> {
                DashboardScreen(
                    sensorState = sensorState,
                    scanBudget = scanBudget,
                    lastMeasurement = lastMeasurement,
                    onScanClick = onScanClick,
                    onHistoryClick = { currentDestination = NavDestination.STATISTICS },
                    onSettingsClick = onSettingsClick,
                    modifier = Modifier.padding(padding)
                )
            }
            NavDestination.MAP -> {
                MapScreen(
                    measurements = storedMeasurements,
                    places = places,
                    onAddPlace = onAddPlace,
                    onPlaceClick = onPlaceClick,
                    onSettingsClick = onSettingsClick,
                    modifier = Modifier.padding(padding)
                )
            }
            NavDestination.STATISTICS -> {
                StatisticsScreen(
                    measurementHistory = measurementHistory,
                    currentMeasurement = lastMeasurement,
                    onSettingsClick = onSettingsClick,
                    modifier = Modifier.padding(padding)
                )
            }
            NavDestination.SOLUTIONS -> {
                SolutionsScreen(
                    currentMeasurement = lastMeasurement,
                    onSettingsClick = onSettingsClick,
                    modifier = Modifier.padding(padding)
                )
            }
        }
    }
}
