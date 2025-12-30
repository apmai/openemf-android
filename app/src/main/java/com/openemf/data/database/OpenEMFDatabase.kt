package com.openemf.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * OpenEMF Room Database
 * Stores measurements with location data and user-defined places.
 */
@Database(
    entities = [
        MeasurementEntity::class,
        PlaceEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class OpenEMFDatabase : RoomDatabase() {
    abstract fun measurementDao(): MeasurementDao
    abstract fun placeDao(): PlaceDao
}

// ==================== Entities ====================

/**
 * Stored measurement with optional location.
 */
@Entity(tableName = "measurements")
data class MeasurementEntity(
    @PrimaryKey
    val id: String,
    val timestamp: Instant,
    val eScore: Int,
    val exposureLevel: String, // LOW, MODERATE, HIGH
    val cumulativeDbm: Int,

    // Source counts
    val wifiCount: Int,
    val bluetoothCount: Int,
    val cellularCount: Int,

    // Source breakdown percentages
    val wifiPercent: Int,
    val bluetoothPercent: Int,
    val cellularPercent: Int,

    // Location (optional)
    val latitude: Double?,
    val longitude: Double?,
    val accuracy: Float?,

    // Associated place (if any)
    val placeId: String?,

    // Dominant source info
    val dominantSourceName: String?,
    val dominantSourceType: String?,
    val dominantSourcePercent: Int?
)

/**
 * User-defined favorite place (Home, Work, etc.)
 */
@Entity(tableName = "places")
data class PlaceEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val icon: String, // Icon name (home, work, gym, cafe, etc.)
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float = 100f, // Geofence radius
    val createdAt: Instant,

    // Statistics (updated periodically)
    val measurementCount: Int = 0,
    val averageScore: Int = 0,
    val lastMeasuredAt: Instant? = null
)

// ==================== DAOs ====================

@Dao
interface MeasurementDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(measurement: MeasurementEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(measurements: List<MeasurementEntity>)

    @Query("SELECT * FROM measurements ORDER BY timestamp DESC")
    fun getAllFlow(): Flow<List<MeasurementEntity>>

    @Query("SELECT * FROM measurements ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentFlow(limit: Int): Flow<List<MeasurementEntity>>

    @Query("SELECT * FROM measurements ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<MeasurementEntity>

    @Query("SELECT * FROM measurements WHERE latitude IS NOT NULL ORDER BY timestamp DESC")
    fun getWithLocationFlow(): Flow<List<MeasurementEntity>>

    @Query("SELECT * FROM measurements WHERE placeId = :placeId ORDER BY timestamp DESC")
    fun getByPlaceFlow(placeId: String): Flow<List<MeasurementEntity>>

    @Query("SELECT * FROM measurements WHERE placeId = :placeId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getByPlace(placeId: String, limit: Int): List<MeasurementEntity>

    @Query("SELECT * FROM measurements WHERE timestamp > :since ORDER BY timestamp DESC")
    suspend fun getSince(since: Instant): List<MeasurementEntity>

    @Query("SELECT AVG(eScore) FROM measurements WHERE placeId = :placeId")
    suspend fun getAverageScoreForPlace(placeId: String): Double?

    @Query("SELECT COUNT(*) FROM measurements WHERE placeId = :placeId")
    suspend fun getCountForPlace(placeId: String): Int

    @Query("SELECT * FROM measurements WHERE id = :id")
    suspend fun getById(id: String): MeasurementEntity?

    @Delete
    suspend fun delete(measurement: MeasurementEntity)

    @Query("DELETE FROM measurements WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Instant)

    @Query("SELECT COUNT(*) FROM measurements")
    suspend fun getTotalCount(): Int

    @Query("DELETE FROM measurements")
    suspend fun deleteAll()
}

@Dao
interface PlaceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(place: PlaceEntity)

    @Update
    suspend fun update(place: PlaceEntity)

    @Query("SELECT * FROM places ORDER BY name ASC")
    fun getAllFlow(): Flow<List<PlaceEntity>>

    @Query("SELECT * FROM places ORDER BY name ASC")
    suspend fun getAll(): List<PlaceEntity>

    @Query("SELECT * FROM places WHERE id = :id")
    suspend fun getById(id: String): PlaceEntity?

    @Delete
    suspend fun delete(place: PlaceEntity)

    @Query("UPDATE places SET measurementCount = :count, averageScore = :avgScore, lastMeasuredAt = :lastMeasured WHERE id = :placeId")
    suspend fun updateStats(placeId: String, count: Int, avgScore: Int, lastMeasured: Instant?)
}

// ==================== Type Converters ====================

class Converters {
    @TypeConverter
    fun fromInstant(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun toInstant(value: Long?): Instant? = value?.let { Instant.ofEpochMilli(it) }
}

// ==================== Place Icons ====================

/**
 * Predefined place types with icons.
 */
enum class PlaceIcon(val displayName: String, val emoji: String) {
    HOME("Home", "🏠"),
    WORK("Work", "💼"),
    GYM("Gym", "🏋️"),
    CAFE("Cafe", "☕"),
    RESTAURANT("Restaurant", "🍽️"),
    SCHOOL("School", "🏫"),
    HOSPITAL("Hospital", "🏥"),
    PARK("Park", "🌳"),
    SHOPPING("Shopping", "🛒"),
    TRANSPORT("Transport", "🚇"),
    HOTEL("Hotel", "🏨"),
    OTHER("Other", "📍")
}
