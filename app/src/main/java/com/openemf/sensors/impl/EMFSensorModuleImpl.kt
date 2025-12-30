package com.openemf.sensors.impl

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.openemf.sensors.api.*
import com.openemf.sensors.bluetooth.BluetoothScanner
import com.openemf.sensors.cellular.CellularScanner
import com.openemf.sensors.magnetic.MagneticScanner
import com.openemf.sensors.scheduling.ScanScheduler
import com.openemf.sensors.scoring.EScoreCalculator
import com.openemf.sensors.wifi.WifiScanner
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Main implementation of the EMF Sensor Module.
 * Coordinates all scanners and produces unified measurements.
 */
@Singleton
class EMFSensorModuleImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val wifiScanner: WifiScanner,
    private val bluetoothScanner: BluetoothScanner,
    private val cellularScanner: CellularScanner,
    private val magneticScanner: MagneticScanner,
    private val scheduler: ScanScheduler,
    private val eScoreCalculator: EScoreCalculator
) : EMFSensorModule {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _sensorState = MutableStateFlow<SensorState>(SensorState.Idle)
    override val sensorState: StateFlow<SensorState> = _sensorState.asStateFlow()

    private val _scanBudget = MutableStateFlow(ScanBudget.initial())
    override val scanBudget: StateFlow<ScanBudget> = _scanBudget.asStateFlow()

    private val _measurements = MutableSharedFlow<Measurement>(replay = 1)
    override val measurements: Flow<Measurement> = _measurements.asSharedFlow()

    private var currentContext = MeasurementContext()
    private var monitoringJob: Job? = null
    private var lastMeasurement: Measurement? = null

    init {
        // Update scan budget periodically
        scope.launch {
            while (isActive) {
                _scanBudget.value = scheduler.getCurrentBudget()
                delay(1000)
            }
        }
    }

    /**
     * Perform a single measurement (foreground, user-triggered).
     */
    override suspend fun measureNow(): Result<Measurement> {
        // Check permissions first
        val permissionStatus = checkPermissions()
        if (!permissionStatus.canMeasure) {
            _sensorState.value = SensorState.PermissionRequired(
                missing = permissionStatus.denied.map { it.toPermission() },
                canProceedDegraded = permissionStatus.degradedModalities.isNotEmpty()
            )
            return Result.failure(MeasurementException("Permissions required"))
        }

        // Check throttle budget
        if (!scheduler.canScan()) {
            val budget = scheduler.getCurrentBudget()
            _sensorState.value = SensorState.ThrottleLimited(
                scansRemaining = budget.scansRemaining,
                resetInSeconds = budget.windowResetInSeconds,
                cachedMeasurement = lastMeasurement
            )
            return lastMeasurement?.let { Result.success(it) }
                ?: Result.failure(MeasurementException("Throttled and no cached data"))
        }

        // Start scanning
        val startTime = System.currentTimeMillis()
        _sensorState.value = SensorState.Scanning(
            startedAt = Instant.now(),
            modalities = setOf(Modality.WIFI, Modality.BLUETOOTH, Modality.CELLULAR)
        )

        try {
            // Consume a scan from budget
            scheduler.consumeScan()

            // Run all scans in parallel
            val wifiDeferred: Deferred<List<WifiSource>>
            val bluetoothDeferred: Deferred<List<BluetoothSource>>
            val cellularDeferred: Deferred<List<CellularSource>>
            val magneticDeferred: Deferred<MagneticFieldReading?>

            coroutineScope {
                wifiDeferred = async { scanWifi() }
                bluetoothDeferred = async { scanBluetooth() }
                cellularDeferred = async { scanCellular() }
                magneticDeferred = async { scanMagnetic() }
            }

            val wifiSources = wifiDeferred.await()
            val bluetoothSources = bluetoothDeferred.await()
            val cellularSources = cellularDeferred.await()
            val magneticReading = magneticDeferred.await()

            // Calculate E-Score
            val allSources = mutableListOf<Source>()
            allSources.addAll(wifiSources)
            allSources.addAll(bluetoothSources)
            allSources.addAll(cellularSources)

            val eScore = eScoreCalculator.calculate(allSources)

            // Calculate confidence
            val confidence = calculateConfidence(wifiSources, bluetoothSources, cellularSources)

            // Create measurement
            val measurement = Measurement(
                id = UUID.randomUUID().toString(),
                timestamp = Instant.now(),
                eScore = eScore,
                wifiSources = wifiSources,
                bluetoothSources = bluetoothSources,
                cellularSources = cellularSources,
                magneticField = magneticReading,
                context = currentContext,
                confidence = confidence
            )

            lastMeasurement = measurement
            _measurements.emit(measurement)

            val durationMs = System.currentTimeMillis() - startTime
            _sensorState.value = SensorState.Complete(
                measurement = measurement,
                confidence = confidence,
                durationMs = durationMs
            )

            // Reset to Idle after a short delay
            scope.launch {
                delay(2000)
                if (_sensorState.value is SensorState.Complete) {
                    _sensorState.value = SensorState.Idle
                }
            }

            return Result.success(measurement)

        } catch (e: Exception) {
            _sensorState.value = SensorState.Error(
                type = ErrorType.SCAN_FAILED,
                message = e.message ?: "Unknown error",
                recoverable = true
            )
            return Result.failure(e)
        }
    }

    /**
     * Start continuous/hybrid monitoring.
     *
     * This implements a smart hybrid approach:
     * - Bluetooth: Continuous real-time updates (no throttle)
     * - Cellular: Continuous real-time updates (no throttle)
     * - WiFi: Auto-scan when budget allows (~30 sec when available)
     *
     * The UI updates seamlessly as any source changes, respecting Android limits.
     */
    override fun startMonitoring(config: MonitoringConfig) {
        monitoringJob?.cancel()

        monitoringJob = scope.launch {
            // State to hold latest data from each source
            var latestWifi = wifiScanner.getCachedResults()
            var latestBluetooth = emptyList<BluetoothSource>()
            var latestCellular = emptyList<CellularSource>()
            var wifiLastUpdated = System.currentTimeMillis()

            // Helper to emit combined measurement
            suspend fun emitMeasurement(isScanning: Boolean = false) {
                val allSources = mutableListOf<Source>()
                allSources.addAll(latestWifi)
                allSources.addAll(latestBluetooth)
                allSources.addAll(latestCellular)

                val eScore = eScoreCalculator.calculate(allSources)
                val confidence = calculateConfidence(latestWifi, latestBluetooth, latestCellular)
                val magnetic = scanMagnetic()

                val measurement = Measurement(
                    id = UUID.randomUUID().toString(),
                    timestamp = Instant.now(),
                    eScore = eScore,
                    wifiSources = latestWifi,
                    bluetoothSources = latestBluetooth,
                    cellularSources = latestCellular,
                    magneticField = magnetic,
                    context = currentContext,
                    confidence = confidence
                )

                lastMeasurement = measurement
                _measurements.emit(measurement)

                if (!isScanning) {
                    _sensorState.value = SensorState.Complete(
                        measurement = measurement,
                        confidence = confidence,
                        durationMs = 0
                    )
                }
            }

            // Launch parallel collectors for each source type
            val bluetoothJob = launch {
                bluetoothScanner.observeScans().collect { sources ->
                    latestBluetooth = sources
                    emitMeasurement()
                }
            }

            val cellularJob = launch {
                cellularScanner.observeCellInfo().collect { sources ->
                    latestCellular = sources
                    emitMeasurement()
                }
            }

            // Smart WiFi auto-scanning when budget allows
            val wifiJob = launch {
                scheduler.observeScanOpportunities().collect {
                    // Budget is available, trigger WiFi scan
                    _sensorState.value = SensorState.Scanning(
                        startedAt = Instant.now(),
                        modalities = setOf(Modality.WIFI)
                    )

                    scheduler.consumeScan()
                    val wifiResult = wifiScanner.scan()

                    wifiResult.onSuccess { sources ->
                        latestWifi = sources
                        wifiLastUpdated = System.currentTimeMillis()
                        emitMeasurement(isScanning = false)
                    }.onFailure {
                        // Use cached data if scan fails
                        latestWifi = wifiScanner.getCachedResults()
                        emitMeasurement(isScanning = false)
                    }
                }
            }

            // Initial emission with cached/current data
            emitMeasurement()

            // Wait for cancellation
            try {
                awaitCancellation()
            } finally {
                bluetoothJob.cancel()
                cellularJob.cancel()
                wifiJob.cancel()
            }
        }
    }

    /**
     * Stop continuous monitoring.
     */
    override fun stopMonitoring() {
        monitoringJob?.cancel()
        monitoringJob = null
        _sensorState.value = SensorState.Idle
    }

    /**
     * Check required permissions.
     */
    override fun checkPermissions(): PermissionStatus {
        val granted = mutableSetOf<Permission>()
        val denied = mutableSetOf<Permission>()
        val degraded = mutableSetOf<Modality>()

        // Check FINE_LOCATION
        if (hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            granted.add(Permission.FINE_LOCATION)
        } else {
            denied.add(Permission.FINE_LOCATION)
            // Without fine location, cellular and WiFi are degraded
            degraded.add(Modality.CELLULAR)
        }

        // Check COARSE_LOCATION
        if (hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)) {
            granted.add(Permission.COARSE_LOCATION)
        } else {
            denied.add(Permission.COARSE_LOCATION)
        }

        // Check Bluetooth permissions (Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (hasPermission(Manifest.permission.BLUETOOTH_SCAN)) {
                granted.add(Permission.BLUETOOTH_SCAN)
            } else {
                denied.add(Permission.BLUETOOTH_SCAN)
                degraded.add(Modality.BLUETOOTH)
            }

            if (hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                granted.add(Permission.BLUETOOTH_CONNECT)
            } else {
                denied.add(Permission.BLUETOOTH_CONNECT)
            }
        }

        // Check NEARBY_WIFI_DEVICES (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (hasPermission(Manifest.permission.NEARBY_WIFI_DEVICES)) {
                granted.add(Permission.NEARBY_WIFI_DEVICES)
            } else {
                denied.add(Permission.NEARBY_WIFI_DEVICES)
                degraded.add(Modality.WIFI)
            }
        }

        // Can measure if we have at least location permission
        val canMeasure = Permission.FINE_LOCATION in granted || Permission.COARSE_LOCATION in granted

        return PermissionStatus(
            granted = granted,
            denied = denied,
            canMeasure = canMeasure,
            degradedModalities = degraded
        )
    }

    /**
     * Update context (motion state, foreground/background, etc.)
     */
    override fun updateContext(context: MeasurementContext) {
        currentContext = context
    }

    // Private helper methods

    private suspend fun scanWifi(): List<WifiSource> {
        if (!wifiScanner.isAvailable() || !wifiScanner.hasPermissions()) {
            return wifiScanner.getCachedResults()
        }

        return wifiScanner.scan().getOrElse {
            wifiScanner.getCachedResults()
        }
    }

    private suspend fun scanBluetooth(): List<BluetoothSource> {
        if (!bluetoothScanner.isAvailable() || !bluetoothScanner.hasPermissions()) {
            return bluetoothScanner.getBondedDevices()
        }

        return bluetoothScanner.scan().getOrElse {
            bluetoothScanner.getBondedDevices()
        }
    }

    private suspend fun scanCellular(): List<CellularSource> {
        if (!cellularScanner.isAvailable() || !cellularScanner.hasPermissions()) {
            return listOfNotNull(cellularScanner.getRegisteredCell())
        }

        return cellularScanner.scan().getOrElse {
            listOfNotNull(cellularScanner.getRegisteredCell())
        }
    }

    private suspend fun scanMagnetic(): MagneticFieldReading? {
        if (!magneticScanner.isAvailable()) {
            return null
        }

        return magneticScanner.measure().getOrNull()?.let { reading ->
            MagneticFieldReading(
                magnitudeUt = reading.magnitudeUt,
                magnitudeMg = reading.magnitudeMg,
                isElevated = reading.isElevated,
                estimatedAcComponentUt = reading.estimatedAcComponent,
                confidence = reading.confidence
            )
        }
    }

    private fun calculateConfidence(
        wifiSources: List<WifiSource>,
        bluetoothSources: List<BluetoothSource>,
        cellularSources: List<CellularSource>
    ): ConfidenceFlags {
        // WiFi confidence based on fresh scan vs cached
        val wifiConfidence = if (wifiScanner.isAvailable() && wifiScanner.hasPermissions()) {
            if (wifiSources.isNotEmpty()) 1.0f else 0.5f
        } else {
            0.3f
        }

        // Bluetooth confidence
        val btConfidence = if (bluetoothScanner.isAvailable() && bluetoothScanner.hasPermissions()) {
            if (bluetoothSources.isNotEmpty()) 1.0f else 0.5f
        } else {
            0.3f
        }

        // Cellular confidence
        val cellConfidence = if (cellularScanner.isAvailable() && cellularScanner.hasPermissions()) {
            if (cellularSources.isNotEmpty()) 1.0f else 0.5f
        } else {
            0.3f
        }

        // Overall is weighted average (WiFi has most impact typically)
        val overall = (wifiConfidence * 0.5f + btConfidence * 0.25f + cellConfidence * 0.25f)

        return ConfidenceFlags(
            wifi = wifiConfidence,
            bluetooth = btConfidence,
            cellular = cellConfidence,
            overall = overall
        )
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            context, permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun Permission.toPermission(): Permission = this
}

private fun Set<Permission>.map(transform: (Permission) -> Permission): List<Permission> {
    return this.toList().map(transform)
}

class MeasurementException(message: String) : Exception(message)
