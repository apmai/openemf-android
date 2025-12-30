package com.openemf.sensors.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.openemf.sensors.api.BluetoothDeviceType
import com.openemf.sensors.api.BluetoothSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Bluetooth scanner using BLE (Bluetooth Low Energy) for device discovery.
 * Handles both classic and BLE devices.
 */
@Singleton
class BluetoothScanner @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val bluetoothManager: BluetoothManager? by lazy {
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    }

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        bluetoothManager?.adapter
    }

    private val leScanner: BluetoothLeScanner? by lazy {
        bluetoothAdapter?.bluetoothLeScanner
    }

    private val handler = Handler(Looper.getMainLooper())

    companion object {
        private const val SCAN_DURATION_MS = 5000L // 5 seconds
    }

    /**
     * Check if Bluetooth is available and enabled.
     */
    fun isAvailable(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }

    /**
     * Check if we have required permissions.
     */
    fun hasPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+
            val scanPermission = ContextCompat.checkSelfPermission(
                context, Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED

            val connectPermission = ContextCompat.checkSelfPermission(
                context, Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED

            scanPermission && connectPermission
        } else {
            // Pre-Android 12
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Perform a BLE scan for the specified duration.
     */
    suspend fun scan(durationMs: Long = SCAN_DURATION_MS): Result<List<BluetoothSource>> =
        suspendCancellableCoroutine { continuation ->
            val scanner = leScanner
            if (scanner == null) {
                continuation.resume(Result.failure(BluetoothScanException("BLE scanner not available")))
                return@suspendCancellableCoroutine
            }

            if (!hasPermissions()) {
                continuation.resume(Result.failure(BluetoothScanException("Missing permissions")))
                return@suspendCancellableCoroutine
            }

            val devices = mutableMapOf<String, BluetoothSource>()
            var isCompleted = false

            val callback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    val source = result.toBluetoothSource()
                    val existing = devices[source.address]
                    if (existing == null || source.rssi > existing.rssi) {
                        devices[source.address] = source
                    }
                }

                override fun onBatchScanResults(results: List<ScanResult>) {
                    results.forEach { result ->
                        val source = result.toBluetoothSource()
                        val existing = devices[source.address]
                        if (existing == null || source.rssi > existing.rssi) {
                            devices[source.address] = source
                        }
                    }
                }

                override fun onScanFailed(errorCode: Int) {
                    // Don't fail - we might have partial results
                }
            }

            val stopRunnable = Runnable {
                if (!isCompleted) {
                    isCompleted = true
                    try {
                        scanner.stopScan(callback)
                    } catch (_: SecurityException) {}
                    continuation.resume(Result.success(devices.values.toList()))
                }
            }

            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setReportDelay(0)
                .build()

            try {
                scanner.startScan(null, settings, callback)
            } catch (e: SecurityException) {
                continuation.resume(Result.failure(BluetoothScanException("Permission denied")))
                return@suspendCancellableCoroutine
            }

            // Schedule scan stop
            handler.postDelayed(stopRunnable, durationMs)

            continuation.invokeOnCancellation {
                handler.removeCallbacks(stopRunnable)
                if (!isCompleted) {
                    isCompleted = true
                    try {
                        scanner.stopScan(callback)
                    } catch (_: SecurityException) {}
                }
            }
        }

    /**
     * Get bonded (paired) devices.
     */
    fun getBondedDevices(): List<BluetoothSource> {
        if (!hasPermissions()) return emptyList()

        return try {
            bluetoothAdapter?.bondedDevices?.map { device ->
                device.toBluetoothSource()
            } ?: emptyList()
        } catch (_: SecurityException) {
            emptyList()
        }
    }

    /**
     * Observe BLE devices as a continuous Flow.
     */
    fun observeScans(): Flow<List<BluetoothSource>> = callbackFlow {
        val scanner = leScanner
        if (scanner == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val devices = mutableMapOf<String, BluetoothSource>()

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val source = result.toBluetoothSource()
                devices[source.address] = source
                trySend(devices.values.toList())
            }

            override fun onScanFailed(errorCode: Int) {
                // Continue with partial results
            }
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
            .setReportDelay(1000)
            .build()

        try {
            scanner.startScan(null, settings, callback)
        } catch (_: SecurityException) {
            close()
            return@callbackFlow
        }

        awaitClose {
            try {
                scanner.stopScan(callback)
            } catch (_: SecurityException) {}
        }
    }

    /**
     * Convert BLE ScanResult to our BluetoothSource model.
     */
    private fun ScanResult.toBluetoothSource(): BluetoothSource {
        val deviceName = try {
            device.name
        } catch (_: SecurityException) {
            null
        }

        val deviceType = classifyDevice(deviceName, device)

        return BluetoothSource(
            id = UUID.randomUUID().toString(),
            name = deviceName,
            address = device.address,
            rssi = rssi,
            deviceType = deviceType,
            isConnected = false,
            contributionPercent = 0
        )
    }

    /**
     * Convert bonded BluetoothDevice to our model.
     */
    private fun BluetoothDevice.toBluetoothSource(): BluetoothSource {
        val deviceName = try {
            name
        } catch (_: SecurityException) {
            null
        }

        return BluetoothSource(
            id = UUID.randomUUID().toString(),
            name = deviceName,
            address = address,
            rssi = -70,
            deviceType = classifyDevice(deviceName, this),
            isConnected = true,
            contributionPercent = 0
        )
    }

    /**
     * Classify device type based on name and device class.
     */
    private fun classifyDevice(name: String?, device: BluetoothDevice): BluetoothDeviceType {
        val lowerName = name?.lowercase() ?: ""

        return when {
            lowerName.contains("airtag") || lowerName.contains("tile") -> BluetoothDeviceType.AIRTAG
            lowerName.contains("airpod") || lowerName.contains("earbud") -> BluetoothDeviceType.AIRPODS
            lowerName.contains("watch") -> BluetoothDeviceType.SMARTWATCH
            lowerName.contains("band") || lowerName.contains("fitbit") -> BluetoothDeviceType.FITNESS_BAND
            lowerName.contains("speaker") || lowerName.contains("jbl") || lowerName.contains("bose") -> BluetoothDeviceType.SPEAKER
            lowerName.contains("headphone") || lowerName.contains("beats") -> BluetoothDeviceType.HEADPHONES
            lowerName.contains("phone") || lowerName.contains("iphone") || lowerName.contains("galaxy") -> BluetoothDeviceType.PHONE
            lowerName.contains("macbook") || lowerName.contains("laptop") || lowerName.contains("pc") -> BluetoothDeviceType.COMPUTER
            else -> BluetoothDeviceType.UNKNOWN
        }
    }
}

class BluetoothScanException(message: String) : Exception(message)
