package com.openemf.sensors.wifi

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.ScanResult
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.openemf.sensors.api.WifiBand
import com.openemf.sensors.api.WifiChannelWidth
import com.openemf.sensors.api.WifiSecurity
import com.openemf.sensors.api.WifiSource
import com.openemf.sensors.api.WifiStandard
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * WiFi scanner that wraps Android's WifiManager.
 * Respects Android 9+ throttling (4 scans per 2 minutes).
 */
@Singleton
class WifiScanner @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val wifiManager: WifiManager? by lazy {
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    }

    private val connectivityManager: ConnectivityManager? by lazy {
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    }

    /**
     * Check if WiFi scanning is available.
     */
    fun isAvailable(): Boolean {
        return wifiManager?.isWifiEnabled == true || isScanAlwaysAvailable()
    }

    /**
     * Check if scan is always available (even with WiFi off).
     * Note: isScanAlwaysAvailable is deprecated in API 31+ but still functional.
     * No direct replacement exists - the user setting still controls this behavior.
     */
    @Suppress("DEPRECATION")
    private fun isScanAlwaysAvailable(): Boolean {
        return wifiManager?.isScanAlwaysAvailable == true
    }

    /**
     * Check if we have the required permissions.
     */
    fun hasPermissions(): Boolean {
        val fineLocation = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        // Android 13+ requires NEARBY_WIFI_DEVICES
        val nearbyWifi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.NEARBY_WIFI_DEVICES
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        return fineLocation && nearbyWifi
    }

    /**
     * Perform a single WiFi scan.
     * Returns list of detected WiFi sources.
     */
    suspend fun scan(): Result<List<WifiSource>> = suspendCancellableCoroutine { continuation ->
        val manager = wifiManager
        if (manager == null) {
            continuation.resume(Result.failure(WifiScanException("WifiManager not available")))
            return@suspendCancellableCoroutine
        }

        if (!hasPermissions()) {
            continuation.resume(Result.failure(WifiScanException("Missing permissions")))
            return@suspendCancellableCoroutine
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                try {
                    context.unregisterReceiver(this)
                } catch (_: IllegalArgumentException) {
                    // Already unregistered
                }

                val success = intent?.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false) ?: false

                if (success) {
                    try {
                        val results = manager.scanResults ?: emptyList()
                        val sources = results.map { it.toWifiSource(manager) }
                        continuation.resume(Result.success(sources))
                    } catch (e: SecurityException) {
                        continuation.resume(Result.failure(WifiScanException("Permission denied: ${e.message}")))
                    }
                } else {
                    // Even on "failure", we can try to get cached results
                    try {
                        val results = manager.scanResults ?: emptyList()
                        val sources = results.map { it.toWifiSource(manager) }
                        continuation.resume(Result.success(sources))
                    } catch (e: SecurityException) {
                        continuation.resume(Result.failure(WifiScanException("Scan failed and no cached results")))
                    }
                }
            }
        }

        val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        context.registerReceiver(receiver, filter)

        continuation.invokeOnCancellation {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: IllegalArgumentException) {
                // Already unregistered
            }
        }

        @Suppress("DEPRECATION")
        val scanStarted = manager.startScan()
        if (!scanStarted) {
            // Scan didn't start (throttled), but we might have cached results
            try {
                context.unregisterReceiver(receiver)
            } catch (_: IllegalArgumentException) {}

            try {
                val results = manager.scanResults ?: emptyList()
                val sources = results.map { it.toWifiSource(manager) }
                continuation.resume(Result.success(sources))
            } catch (e: SecurityException) {
                continuation.resume(Result.failure(WifiScanException("Scan throttled and no cached results")))
            }
        }
    }

    /**
     * Get cached scan results without triggering a new scan.
     */
    fun getCachedResults(): List<WifiSource> {
        if (!hasPermissions()) return emptyList()

        return try {
            wifiManager?.scanResults?.map { it.toWifiSource(wifiManager!!) } ?: emptyList()
        } catch (_: SecurityException) {
            emptyList()
        }
    }

    /**
     * Observe WiFi scan results as a Flow.
     */
    fun observeScans(): Flow<List<WifiSource>> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                try {
                    val results = wifiManager?.scanResults ?: emptyList()
                    val sources = results.map { it.toWifiSource(wifiManager!!) }
                    trySend(sources)
                } catch (_: SecurityException) {
                    trySend(emptyList())
                }
            }
        }

        val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        context.registerReceiver(receiver, filter)

        awaitClose {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: IllegalArgumentException) {}
        }
    }

    /**
     * Convert Android ScanResult to our WifiSource model.
     */
    private fun ScanResult.toWifiSource(manager: WifiManager): WifiSource {
        val band = when {
            frequency < 3000 -> WifiBand.WIFI_2_4_GHZ
            frequency < 6000 -> WifiBand.WIFI_5_GHZ
            frequency >= 6000 -> WifiBand.WIFI_6_GHZ
            else -> WifiBand.UNKNOWN
        }

        val channel = frequencyToChannel(frequency)
        val channelWidth = extractChannelWidth()
        val standard = extractWifiStandard(band)
        val security = extractSecurity()

        // Check if this is the connected network using modern API
        val connectedBssid = getConnectedBssid()

        // Extract SSID - API 33+ may return null for privacy, handle gracefully
        val ssidName = extractSsid()

        return WifiSource(
            id = UUID.randomUUID().toString(),
            name = ssidName,
            bssid = BSSID,
            rssi = level,
            frequency = frequency,
            band = band,
            channel = channel,
            channelWidth = channelWidth,
            standard = standard,
            security = security,
            isConnected = BSSID == connectedBssid,
            contributionPercent = 0 // Calculated later by EScoreCalculator
        )
    }

    /**
     * Get the BSSID of currently connected WiFi network.
     * Uses modern NetworkCapabilities API on Android 12+, falls back to deprecated API.
     */
    private fun getConnectedBssid(): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Android 12+: Use ConnectivityManager with NetworkCapabilities
                val network = connectivityManager?.activeNetwork ?: return null
                val capabilities = connectivityManager?.getNetworkCapabilities(network) ?: return null

                if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    // Get WifiInfo from TransportInfo
                    val transportInfo = capabilities.transportInfo
                    if (transportInfo is WifiInfo) {
                        transportInfo.bssid
                    } else null
                } else null
            } else {
                // Pre-Android 12: Use deprecated connectionInfo
                @Suppress("DEPRECATION")
                wifiManager?.connectionInfo?.bssid
            }
        } catch (_: SecurityException) {
            null
        }
    }

    /**
     * Extract SSID from ScanResult.
     * Handles API 33+ deprecation where SSID field is deprecated.
     */
    @Suppress("DEPRECATION")
    private fun ScanResult.extractSsid(): String? {
        // On API 33+, wifiSsid returns a WifiSsid object, but SSID still works
        // The SSID field is deprecated but functional; suppress warning
        val ssid = SSID
        return ssid.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
    }

    /**
     * Extract channel width from ScanResult.
     */
    private fun ScanResult.extractChannelWidth(): WifiChannelWidth {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            when (channelWidth) {
                ScanResult.CHANNEL_WIDTH_20MHZ -> WifiChannelWidth.WIDTH_20_MHZ
                ScanResult.CHANNEL_WIDTH_40MHZ -> WifiChannelWidth.WIDTH_40_MHZ
                ScanResult.CHANNEL_WIDTH_80MHZ -> WifiChannelWidth.WIDTH_80_MHZ
                ScanResult.CHANNEL_WIDTH_160MHZ -> WifiChannelWidth.WIDTH_160_MHZ
                ScanResult.CHANNEL_WIDTH_80MHZ_PLUS_MHZ -> WifiChannelWidth.WIDTH_80_PLUS_80_MHZ
                else -> {
                    // Check for WiFi 7 320 MHz (API 33+)
                    if (Build.VERSION.SDK_INT >= 33 && channelWidth == 5) {
                        WifiChannelWidth.WIDTH_320_MHZ
                    } else {
                        WifiChannelWidth.UNKNOWN
                    }
                }
            }
        } else {
            WifiChannelWidth.UNKNOWN
        }
    }

    /**
     * Extract WiFi standard from ScanResult.
     * Uses wifiStandard on Android 11+, otherwise infers from capabilities and frequency.
     */
    private fun ScanResult.extractWifiStandard(band: WifiBand): WifiStandard {
        // Android 11+ has direct wifiStandard property
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return when (wifiStandard) {
                ScanResult.WIFI_STANDARD_LEGACY -> WifiStandard.LEGACY
                ScanResult.WIFI_STANDARD_11N -> WifiStandard.WIFI_4
                ScanResult.WIFI_STANDARD_11AC -> WifiStandard.WIFI_5
                ScanResult.WIFI_STANDARD_11AX -> {
                    if (band == WifiBand.WIFI_6_GHZ) WifiStandard.WIFI_6E else WifiStandard.WIFI_6
                }
                else -> {
                    // Check for WiFi 7 (API 34+)
                    if (Build.VERSION.SDK_INT >= 34 && wifiStandard == 8) {
                        WifiStandard.WIFI_7
                    } else {
                        WifiStandard.UNKNOWN
                    }
                }
            }
        }

        // Pre-Android 11: Infer from capabilities string
        val caps = capabilities.uppercase()
        return when {
            band == WifiBand.WIFI_6_GHZ -> WifiStandard.WIFI_6E
            caps.contains("[HE]") || caps.contains("802.11AX") -> WifiStandard.WIFI_6
            caps.contains("[VHT]") || caps.contains("802.11AC") -> WifiStandard.WIFI_5
            caps.contains("[HT]") || caps.contains("802.11N") -> WifiStandard.WIFI_4
            else -> WifiStandard.LEGACY
        }
    }

    /**
     * Extract security type from capabilities string.
     */
    private fun ScanResult.extractSecurity(): WifiSecurity {
        val caps = capabilities.uppercase()
        return when {
            caps.contains("WPA3") && caps.contains("WPA2") -> WifiSecurity.WPA2_WPA3
            caps.contains("WPA3") || caps.contains("SAE") -> WifiSecurity.WPA3
            caps.contains("WPA2") || caps.contains("RSN") -> WifiSecurity.WPA2
            caps.contains("WPA") -> WifiSecurity.WPA
            caps.contains("WEP") -> WifiSecurity.WEP
            caps.contains("ESS") && !caps.contains("WPA") && !caps.contains("WEP") -> WifiSecurity.OPEN
            else -> WifiSecurity.UNKNOWN
        }
    }

    /**
     * Convert frequency (MHz) to WiFi channel number.
     */
    private fun frequencyToChannel(frequency: Int): Int {
        return when {
            // 2.4 GHz band
            frequency in 2412..2484 -> (frequency - 2412) / 5 + 1
            // 5 GHz band
            frequency in 5170..5825 -> (frequency - 5170) / 5 + 34
            // 6 GHz band (WiFi 6E)
            frequency >= 5925 -> (frequency - 5925) / 5 + 1
            else -> 0
        }
    }
}

class WifiScanException(message: String) : Exception(message)
