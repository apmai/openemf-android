package com.openemf.sensors.scheduling

import com.openemf.sensors.api.MotionState
import com.openemf.sensors.api.ScanBudget
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScanScheduler @Inject constructor() {

    private var windowStart = System.currentTimeMillis()
    private var scansInWindow = 0
    private var lastScanTime: Long? = null

    companion object {
        const val MAX_SCANS = 4
        const val WINDOW_MS = 2 * 60 * 1000L  // 2 minutes
        const val MIN_SCAN_INTERVAL_MS = 30_000L  // Minimum 30 seconds between scans

        // Adaptive intervals based on motion
        val MOTION_INTERVALS = mapOf(
            MotionState.STATIONARY to 60_000L,   // 1 minute
            MotionState.WALKING to 30_000L,      // 30 seconds
            MotionState.RUNNING to 30_000L,
            MotionState.CYCLING to 20_000L,
            MotionState.DRIVING to 15_000L,      // Full budget for rapid changes
            MotionState.UNKNOWN to 45_000L
        )
    }
    
    /** Check if we can perform a scan without exceeding throttle limits. */
    fun canScan(): Boolean {
        refreshWindow()
        return scansInWindow < MAX_SCANS
    }
    
    /** Consume one scan from the budget. */
    fun consumeScan() {
        refreshWindow()
        if (scansInWindow < MAX_SCANS) {
            scansInWindow++
            lastScanTime = System.currentTimeMillis()
        }
    }
    
    /** Get current scan budget status. */
    fun getCurrentBudget(): ScanBudget {
        refreshWindow()
        val now = System.currentTimeMillis()
        val resetIn = ((windowStart + WINDOW_MS - now) / 1000).toInt().coerceAtLeast(0)
        
        return ScanBudget(
            scansRemaining = MAX_SCANS - scansInWindow,
            windowResetInSeconds = resetIn,
            isThrottled = scansInWindow >= MAX_SCANS,
            lastScanTimestamp = lastScanTime,
            windowStartTimestamp = windowStart
        )
    }
    
    /** Get recommended scan interval based on motion state. */
    fun getAdaptiveInterval(motionState: MotionState): Long {
        val baseInterval = MOTION_INTERVALS[motionState] ?: 45_000L

        val budget = getCurrentBudget()
        return when {
            budget.scansRemaining <= 1 -> baseInterval * 2
            budget.scansRemaining <= 2 -> (baseInterval * 1.5).toLong()
            else -> baseInterval
        }
    }

    /**
     * Get time in milliseconds until next scan is allowed.
     * Returns 0 if scan is allowed now.
     */
    fun getTimeUntilNextScan(): Long {
        refreshWindow()

        // Check if we have budget
        if (scansInWindow >= MAX_SCANS) {
            // Wait until window resets
            val now = System.currentTimeMillis()
            return (windowStart + WINDOW_MS - now).coerceAtLeast(0)
        }

        // Check minimum interval since last scan
        val lastScan = lastScanTime ?: return 0
        val now = System.currentTimeMillis()
        val timeSinceLastScan = now - lastScan

        return if (timeSinceLastScan >= MIN_SCAN_INTERVAL_MS) {
            0
        } else {
            MIN_SCAN_INTERVAL_MS - timeSinceLastScan
        }
    }

    /**
     * Flow that emits when a scan is allowed.
     * Use this for smart auto-scanning when budget is available.
     */
    fun observeScanOpportunities(): Flow<Unit> = flow {
        while (true) {
            val waitTime = getTimeUntilNextScan()
            if (waitTime > 0) {
                delay(waitTime)
            }

            // Check again after waiting (budget might have changed)
            if (canScan()) {
                emit(Unit)
                // Wait minimum interval before checking again
                delay(MIN_SCAN_INTERVAL_MS)
            } else {
                // Budget exhausted during wait, wait for window reset
                delay(1000)
            }
        }
    }

    private fun refreshWindow() {
        val now = System.currentTimeMillis()
        if (now - windowStart >= WINDOW_MS) {
            windowStart = now
            scansInWindow = 0
        }
    }
}
