package com.openemf.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.openemf.ui.theme.getEScoreColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.openemf.data.database.MeasurementEntity
import com.openemf.data.database.PlaceIcon
import com.openemf.data.repository.PlaceWithStats
import kotlinx.coroutines.tasks.await
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.File
import java.time.format.DateTimeFormatter

/**
 * Map screen showing measurement locations with color-coded markers.
 * Uses OpenStreetMap (no API key required).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    measurements: List<MeasurementEntity>,
    places: List<PlaceWithStats>,
    onAddPlace: (Double, Double) -> Unit,
    onPlaceClick: (PlaceWithStats) -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showPlacesList by remember { mutableStateOf(false) }
    var selectedMeasurement by remember { mutableStateOf<MeasurementEntity?>(null) }
    var requestMyLocation by remember { mutableStateOf(0) } // Increment to trigger location update
    val context = LocalContext.current

    // Initialize osmdroid with proper cache configuration
    LaunchedEffect(Unit) {
        val config = Configuration.getInstance()
        // User agent is required by OSM tile servers
        config.userAgentValue = "OpenEMF/1.0 (Android; contact@openemf.com)"
        // Set cache paths for tile storage
        val osmdroidDir = File(context.filesDir, "osmdroid")
        osmdroidDir.mkdirs()
        val tileCache = File(context.cacheDir, "osmdroid/tiles")
        tileCache.mkdirs()
        config.osmdroidBasePath = osmdroidDir
        config.osmdroidTileCache = tileCache
        // Enable network
        config.isMapViewHardwareAccelerated = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("EMF Map") },
                actions = {
                    IconButton(onClick = { showPlacesList = !showPlacesList }) {
                        Icon(
                            if (showPlacesList) Icons.Default.Map else Icons.AutoMirrored.Filled.List,
                            contentDescription = if (showPlacesList) "Show Map" else "Show Places"
                        )
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        modifier = modifier
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (showPlacesList) {
                // Places list view
                PlacesList(
                    places = places,
                    onPlaceClick = onPlaceClick
                )
            } else {
                // Map view
                EMFMapView(
                    measurements = measurements,
                    places = places,
                    onMeasurementClick = { selectedMeasurement = it },
                    onMapLongClick = onAddPlace,
                    myLocationTrigger = requestMyLocation
                )
            }

            // Measurement detail sheet
            selectedMeasurement?.let { measurement ->
                MeasurementDetailSheet(
                    measurement = measurement,
                    onDismiss = { selectedMeasurement = null }
                )
            }

            // Legend
            if (!showPlacesList) {
                MapLegend(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                )

                // My Location FAB
                FloatingActionButton(
                    onClick = { requestMyLocation++ },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(
                        Icons.Default.MyLocation,
                        contentDescription = "My Location",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

// Custom tile source that uses HTTPS and proper headers
private val osmTileSource = object : OnlineTileSourceBase(
    "OpenStreetMap",
    0, 19, 256, ".png",
    arrayOf(
        "https://a.tile.openstreetmap.org/",
        "https://b.tile.openstreetmap.org/",
        "https://c.tile.openstreetmap.org/"
    )
) {
    override fun getTileURLString(pMapTileIndex: Long): String {
        val zoom = MapTileIndex.getZoom(pMapTileIndex)
        val x = MapTileIndex.getX(pMapTileIndex)
        val y = MapTileIndex.getY(pMapTileIndex)
        return "$baseUrl$zoom/$x/$y$mImageFilenameEnding"
    }
}

@Composable
private fun EMFMapView(
    measurements: List<MeasurementEntity>,
    places: List<PlaceWithStats>,
    onMeasurementClick: (MeasurementEntity) -> Unit,
    onMapLongClick: (Double, Double) -> Unit,
    myLocationTrigger: Int = 0
) {
    val context = LocalContext.current

    // State for current location
    var currentLocation by remember { mutableStateOf<GeoPoint?>(null) }
    var hasInitializedLocation by remember { mutableStateOf(false) }
    var shouldAnimateToLocation by remember { mutableStateOf(false) }

    // Function to get current location
    suspend fun fetchCurrentLocation(): GeoPoint? {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) return null

        return try {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
            val cancellationToken = CancellationTokenSource()
            val location = fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                cancellationToken.token
            ).await()

            location?.let { GeoPoint(it.latitude, it.longitude) }
        } catch (e: Exception) {
            null
        }
    }

    // Get initial location
    LaunchedEffect(Unit) {
        if (!hasInitializedLocation) {
            currentLocation = fetchCurrentLocation()
            hasInitializedLocation = true
        }
    }

    // Handle My Location button press
    LaunchedEffect(myLocationTrigger) {
        if (myLocationTrigger > 0) {
            val newLocation = fetchCurrentLocation()
            if (newLocation != null) {
                currentLocation = newLocation
                shouldAnimateToLocation = true
            }
        }
    }

    // Calculate center point: prioritize measurements, then current location
    val centerPoint = remember(measurements, places, currentLocation) {
        // First, try to center on measurements with valid coordinates
        val validMeasurements = measurements.filter { it.latitude != null && it.longitude != null }
        if (validMeasurements.isNotEmpty()) {
            val avgLat = validMeasurements.mapNotNull { it.latitude }.average()
            val avgLon = validMeasurements.mapNotNull { it.longitude }.average()
            return@remember GeoPoint(avgLat, avgLon)
        }

        // If no measurements, try places
        if (places.isNotEmpty()) {
            val avgLat = places.map { it.place.latitude }.average()
            val avgLon = places.map { it.place.longitude }.average()
            return@remember GeoPoint(avgLat, avgLon)
        }

        // If no places, use current location
        currentLocation ?: GeoPoint(0.0, 0.0) // 0,0 as absolute fallback
    }

    // Remember map view to avoid recreation
    val mapView = remember {
        MapView(context).apply {
            // Use custom tile source with HTTPS
            setTileSource(osmTileSource)
            setMultiTouchControls(true)
            // Enable built-in zoom controls
            setBuiltInZoomControls(true)
            // Set initial zoom
            controller.setZoom(15.0)
            // Minimum zoom level
            minZoomLevel = 3.0
            maxZoomLevel = 19.0
        }
    }

    // Update center when centerPoint changes and is valid
    LaunchedEffect(centerPoint) {
        if (centerPoint.latitude != 0.0 || centerPoint.longitude != 0.0) {
            mapView.controller.animateTo(centerPoint)
        }
    }

    // Animate to current location when My Location is pressed
    LaunchedEffect(shouldAnimateToLocation, currentLocation) {
        if (shouldAnimateToLocation && currentLocation != null) {
            mapView.controller.animateTo(currentLocation, 17.0, 1000L)
            shouldAnimateToLocation = false
        }
    }

    AndroidView(
        factory = {
            mapView.apply {
                // Long click to add place
                setOnLongClickListener {
                    val center = mapCenter
                    onMapLongClick(center.latitude, center.longitude)
                    true
                }
            }
        },
        update = { mapView ->
            // Clear existing markers
            mapView.overlays.clear()

            // Add measurement markers
            measurements.forEach { measurement ->
                if (measurement.latitude != null && measurement.longitude != null) {
                    val marker = Marker(mapView).apply {
                        position = GeoPoint(measurement.latitude, measurement.longitude)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        title = "E-Score: ${measurement.eScore}"
                        snippet = measurement.timestamp.atZone(java.time.ZoneId.systemDefault())
                            .format(DateTimeFormatter.ofPattern("MMM d, HH:mm"))

                        // Color based on score (6-level thresholds)
                        val color = getEScoreColor(measurement.eScore)

                        // Create colored circle marker
                        icon = createCircleDrawable(color.toArgb(), 24)

                        setOnMarkerClickListener { _, _ ->
                            onMeasurementClick(measurement)
                            true
                        }
                    }
                    mapView.overlays.add(marker)
                }
            }

            // Add place markers
            places.forEach { placeWithStats ->
                val place = placeWithStats.place
                val marker = Marker(mapView).apply {
                    position = GeoPoint(place.latitude, place.longitude)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    title = place.name
                    snippet = if (place.measurementCount > 0) {
                        "Avg: ${place.averageScore} (${place.measurementCount} readings)"
                    } else {
                        "No readings yet"
                    }

                    // Use different icon for places
                    icon = createPlaceDrawable(placeWithStats.icon)
                }
                mapView.overlays.add(marker)
            }

            mapView.invalidate()
        },
        modifier = Modifier.fillMaxSize()
    )
}

private fun createCircleDrawable(color: Int, sizeDp: Int): android.graphics.drawable.Drawable {
    return GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
        setSize(sizeDp * 3, sizeDp * 3)
        setStroke(4, android.graphics.Color.WHITE)
    }
}

private fun createPlaceDrawable(icon: PlaceIcon): android.graphics.drawable.Drawable {
    // Simple colored marker for places
    return GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(android.graphics.Color.parseColor("#6366F1")) // Indigo
        setSize(36, 36)
        setStroke(4, android.graphics.Color.WHITE)
    }
}

@Composable
private fun MapLegend(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
        )
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = "E-Score",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
            LegendItem(color = Color(0xFF10B981), label = "0-10")
            LegendItem(color = Color(0xFF22C55E), label = "11-25")
            LegendItem(color = Color(0xFFEAB308), label = "26-50")
            LegendItem(color = Color(0xFFF97316), label = "51-75")
            LegendItem(color = Color(0xFFEF4444), label = "76-90")
            LegendItem(color = Color(0xFFDC2626), label = "90+")
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun PlacesList(
    places: List<PlaceWithStats>,
    onPlaceClick: (PlaceWithStats) -> Unit
) {
    if (places.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Place,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No Places Yet",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Long-press on the map to add a place",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(places) { placeWithStats ->
                PlaceCard(
                    placeWithStats = placeWithStats,
                    onClick = { onPlaceClick(placeWithStats) }
                )
            }
        }
    }
}

@Composable
private fun PlaceCard(
    placeWithStats: PlaceWithStats,
    onClick: () -> Unit
) {
    val place = placeWithStats.place
    val scoreColor = if (place.measurementCount == 0) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        getEScoreColor(place.averageScore)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Icon
            Text(
                text = placeWithStats.icon.emoji,
                style = MaterialTheme.typography.headlineMedium
            )

            // Details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = place.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = if (place.measurementCount > 0) {
                        "${place.measurementCount} measurements"
                    } else {
                        "No measurements yet"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Average score
            if (place.measurementCount > 0) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${place.averageScore}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = scoreColor
                    )
                    Text(
                        text = "avg",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MeasurementDetailSheet(
    measurement: MeasurementEntity,
    onDismiss: () -> Unit
) {
    val scoreColor = getEScoreColor(measurement.eScore)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "${measurement.eScore}",
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = scoreColor
            )
            Text(
                text = measurement.exposureLevel,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Timestamp
            Text(
                text = measurement.timestamp.atZone(java.time.ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("EEEE, MMMM d 'at' HH:mm")),
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Source breakdown
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SourceStat("WiFi", measurement.wifiCount, measurement.wifiPercent)
                SourceStat("Bluetooth", measurement.bluetoothCount, measurement.bluetoothPercent)
                SourceStat("Cellular", measurement.cellularCount, measurement.cellularPercent)
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SourceStat(type: String, count: Int, percent: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "$count",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = type,
            style = MaterialTheme.typography.labelSmall
        )
        Text(
            text = "$percent%",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
