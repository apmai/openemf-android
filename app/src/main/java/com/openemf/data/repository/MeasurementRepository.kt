package com.openemf.data.repository

import com.openemf.data.database.*
import com.openemf.sensors.api.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

/**
 * Repository for measurement data with location support.
 */
@Singleton
class MeasurementRepository @Inject constructor(
    private val measurementDao: MeasurementDao,
    private val placeDao: PlaceDao
) {
    // ==================== Measurements ====================

    /**
     * Save a measurement with optional location.
     */
    suspend fun saveMeasurement(
        measurement: Measurement,
        latitude: Double? = null,
        longitude: Double? = null,
        accuracy: Float? = null
    ) {
        // Find matching place if location provided
        val placeId = if (latitude != null && longitude != null) {
            findNearestPlace(latitude, longitude)?.id
        } else null

        val entity = measurement.toEntity(latitude, longitude, accuracy, placeId)
        measurementDao.insert(entity)

        // Update place statistics if matched
        placeId?.let { updatePlaceStats(it) }
    }

    /**
     * Get all measurements as Flow.
     */
    fun getAllMeasurements(): Flow<List<MeasurementEntity>> =
        measurementDao.getAllFlow()

    /**
     * Get recent measurements.
     */
    fun getRecentMeasurements(limit: Int = 50): Flow<List<MeasurementEntity>> =
        measurementDao.getRecentFlow(limit)

    /**
     * Get measurements with location for map display.
     */
    fun getMeasurementsWithLocation(): Flow<List<MeasurementEntity>> =
        measurementDao.getWithLocationFlow()

    /**
     * Get measurements for a specific place.
     */
    fun getMeasurementsForPlace(placeId: String): Flow<List<MeasurementEntity>> =
        measurementDao.getByPlaceFlow(placeId)

    /**
     * Get summary statistics.
     */
    suspend fun getStatistics(): MeasurementStats {
        val recent = measurementDao.getRecent(100)
        if (recent.isEmpty()) {
            return MeasurementStats(0, 0, 0, 0, null, null)
        }

        val scores = recent.map { it.eScore }
        return MeasurementStats(
            totalCount = measurementDao.getTotalCount(),
            averageScore = scores.average().toInt(),
            minScore = scores.minOrNull() ?: 0,
            maxScore = scores.maxOrNull() ?: 0,
            mostRecentTimestamp = recent.firstOrNull()?.timestamp,
            dominantSourceType = recent
                .mapNotNull { it.dominantSourceType }
                .groupingBy { it }
                .eachCount()
                .maxByOrNull { it.value }
                ?.key
        )
    }

    /**
     * Delete old measurements (data retention).
     */
    suspend fun deleteOlderThan(days: Int) {
        val cutoff = Instant.now().minusSeconds(days * 24 * 60 * 60L)
        measurementDao.deleteOlderThan(cutoff)
    }

    /**
     * Delete all measurements (clear history).
     */
    suspend fun deleteAllMeasurements() {
        measurementDao.deleteAll()
        // Reset all place statistics
        placeDao.getAll().forEach { place ->
            placeDao.updateStats(place.id, 0, 0, place.lastMeasuredAt)
        }
    }

    /**
     * Get all measurements for export.
     */
    suspend fun getAllMeasurementsForExport(): List<MeasurementEntity> =
        measurementDao.getRecent(Int.MAX_VALUE)

    // ==================== Places ====================

    /**
     * Get all places.
     */
    fun getAllPlaces(): Flow<List<PlaceEntity>> = placeDao.getAllFlow()

    /**
     * Add a new place.
     */
    suspend fun addPlace(
        name: String,
        icon: PlaceIcon,
        latitude: Double,
        longitude: Double,
        radiusMeters: Float = 100f
    ): PlaceEntity {
        val place = PlaceEntity(
            id = UUID.randomUUID().toString(),
            name = name,
            icon = icon.name,
            latitude = latitude,
            longitude = longitude,
            radiusMeters = radiusMeters,
            createdAt = Instant.now()
        )
        placeDao.insert(place)
        return place
    }

    /**
     * Update place details.
     */
    suspend fun updatePlace(place: PlaceEntity) {
        placeDao.update(place)
    }

    /**
     * Delete a place.
     */
    suspend fun deletePlace(place: PlaceEntity) {
        placeDao.delete(place)
    }

    /**
     * Find the nearest place within radius.
     */
    suspend fun findNearestPlace(latitude: Double, longitude: Double): PlaceEntity? {
        val places = placeDao.getAll()
        return places
            .map { place ->
                val distance = haversineDistance(
                    latitude, longitude,
                    place.latitude, place.longitude
                )
                place to distance
            }
            .filter { (place, distance) -> distance <= place.radiusMeters }
            .minByOrNull { (_, distance) -> distance }
            ?.first
    }

    /**
     * Update statistics for a place.
     */
    private suspend fun updatePlaceStats(placeId: String) {
        val count = measurementDao.getCountForPlace(placeId)
        val avgScore = measurementDao.getAverageScoreForPlace(placeId)?.toInt() ?: 0
        placeDao.updateStats(placeId, count, avgScore, Instant.now())
    }

    /**
     * Get places with their statistics for display.
     */
    fun getPlacesWithStats(): Flow<List<PlaceWithStats>> =
        placeDao.getAllFlow().map { places ->
            places.map { place ->
                PlaceWithStats(
                    place = place,
                    icon = PlaceIcon.entries.find { it.name == place.icon } ?: PlaceIcon.OTHER
                )
            }
        }

    // ==================== Helpers ====================

    /**
     * Haversine formula for distance between two points in meters.
     */
    private fun haversineDistance(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Float {
        val r = 6371000.0 // Earth radius in meters

        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return (r * c).toFloat()
    }

    private fun Measurement.toEntity(
        latitude: Double?,
        longitude: Double?,
        accuracy: Float?,
        placeId: String?
    ): MeasurementEntity {
        return MeasurementEntity(
            id = id,
            timestamp = timestamp,
            eScore = eScore.score,
            exposureLevel = eScore.level.name,
            cumulativeDbm = eScore.cumulativeDbm,
            wifiCount = wifiSources.size,
            bluetoothCount = bluetoothSources.size,
            cellularCount = cellularSources.size,
            wifiPercent = eScore.breakdown[SourceType.WIFI] ?: 0,
            bluetoothPercent = eScore.breakdown[SourceType.BLUETOOTH] ?: 0,
            cellularPercent = eScore.breakdown[SourceType.CELLULAR] ?: 0,
            latitude = latitude,
            longitude = longitude,
            accuracy = accuracy,
            placeId = placeId,
            dominantSourceName = eScore.dominantSource?.name,
            dominantSourceType = eScore.dominantSource?.type?.name,
            dominantSourcePercent = eScore.dominantSource?.contributionPercent
        )
    }
}

/**
 * Summary statistics for measurements.
 */
data class MeasurementStats(
    val totalCount: Int,
    val averageScore: Int,
    val minScore: Int,
    val maxScore: Int,
    val mostRecentTimestamp: Instant?,
    val dominantSourceType: String?
)

/**
 * Place with resolved icon.
 */
data class PlaceWithStats(
    val place: PlaceEntity,
    val icon: PlaceIcon
)
