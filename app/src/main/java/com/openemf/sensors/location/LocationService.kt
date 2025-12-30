package com.openemf.sensors.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Location service for getting current position.
 * Uses FusedLocationProvider for best accuracy with low power.
 */
@Singleton
class LocationService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val fusedLocationClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    /**
     * Check if location permission is granted.
     */
    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Get last known location (fast, may be null or stale).
     */
    suspend fun getLastLocation(): LocationResult? {
        if (!hasPermission()) return null

        return try {
            suspendCancellableCoroutine { continuation ->
                fusedLocationClient.lastLocation
                    .addOnSuccessListener { location ->
                        if (location != null) {
                            continuation.resume(location.toLocationResult())
                        } else {
                            continuation.resume(null)
                        }
                    }
                    .addOnFailureListener {
                        continuation.resume(null)
                    }
            }
        } catch (e: SecurityException) {
            null
        }
    }

    /**
     * Get current location with fresh GPS fix.
     * More accurate but slower than getLastLocation.
     */
    suspend fun getCurrentLocation(): LocationResult? {
        if (!hasPermission()) return null

        return try {
            suspendCancellableCoroutine { continuation ->
                val request = CurrentLocationRequest.Builder()
                    .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                    .setMaxUpdateAgeMillis(30_000) // Accept location up to 30s old
                    .build()

                fusedLocationClient.getCurrentLocation(request, null)
                    .addOnSuccessListener { location ->
                        if (location != null) {
                            continuation.resume(location.toLocationResult())
                        } else {
                            continuation.resume(null)
                        }
                    }
                    .addOnFailureListener {
                        continuation.resume(null)
                    }
            }
        } catch (e: SecurityException) {
            null
        }
    }

    /**
     * Observe location updates as a Flow.
     */
    fun observeLocation(intervalMs: Long = 10_000): Flow<LocationResult> = callbackFlow {
        if (!hasPermission()) {
            close()
            return@callbackFlow
        }

        val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, intervalMs)
            .setMinUpdateIntervalMillis(intervalMs / 2)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: com.google.android.gms.location.LocationResult) {
                result.lastLocation?.let { location ->
                    trySend(location.toLocationResult())
                }
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            close(e)
            return@callbackFlow
        }

        awaitClose {
            fusedLocationClient.removeLocationUpdates(callback)
        }
    }

    private fun Location.toLocationResult(): LocationResult {
        return LocationResult(
            latitude = latitude,
            longitude = longitude,
            accuracy = accuracy,
            timestamp = time
        )
    }
}

/**
 * Simple location data class.
 */
data class LocationResult(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val timestamp: Long
)
