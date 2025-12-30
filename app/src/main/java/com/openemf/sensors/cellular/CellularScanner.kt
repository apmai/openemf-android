package com.openemf.sensors.cellular

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.CellIdentityGsm
import android.telephony.CellIdentityLte
import android.telephony.CellIdentityNr
import android.telephony.CellIdentityWcdma
import android.telephony.CellInfo
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoWcdma
import android.telephony.CellSignalStrengthGsm
import android.telephony.CellSignalStrengthLte
import android.telephony.CellSignalStrengthNr
import android.telephony.CellSignalStrengthWcdma
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.openemf.sensors.api.CellularDetails
import com.openemf.sensors.api.CellularSource
import com.openemf.sensors.api.CellularTechnology
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cellular scanner that reads cell tower information from TelephonyManager.
 * Requires ACCESS_FINE_LOCATION permission on Android 10+.
 */
@Singleton
class CellularScanner @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val telephonyManager: TelephonyManager? by lazy {
        context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
    }

    /**
     * Check if cellular radio is available.
     */
    fun isAvailable(): Boolean {
        val tm = telephonyManager ?: return false
        return tm.simState == TelephonyManager.SIM_STATE_READY
    }

    /**
     * Check if we have required permissions.
     */
    fun hasPermissions(): Boolean {
        val fineLocation = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        // Android 10+ requires FINE_LOCATION for cell info
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            fineLocation
        } else {
            val coarseLocation = ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            fineLocation || coarseLocation
        }
    }

    /**
     * Scan for cell tower information.
     */
    suspend fun scan(): Result<List<CellularSource>> = withContext(Dispatchers.Default) {
        val tm = telephonyManager
        if (tm == null) {
            return@withContext Result.failure(CellularScanException("TelephonyManager not available"))
        }

        if (!hasPermissions()) {
            return@withContext Result.failure(CellularScanException("Missing permissions"))
        }

        try {
            val cellInfoList = tm.allCellInfo ?: emptyList()
            val sources = cellInfoList.mapNotNull { cellInfo ->
                cellInfoToSource(cellInfo, tm)
            }
            Result.success(sources)
        } catch (e: SecurityException) {
            Result.failure(CellularScanException("Permission denied: ${e.message}"))
        } catch (e: Exception) {
            Result.failure(CellularScanException("Scan failed: ${e.message}"))
        }
    }

    /**
     * Get current registered cell only.
     */
    fun getRegisteredCell(): CellularSource? {
        if (!hasPermissions()) return null

        return try {
            val cellInfoList = telephonyManager?.allCellInfo ?: return null
            val registered = cellInfoList.firstOrNull { it.isRegistered }
            registered?.let { cellInfoToSource(it, telephonyManager!!) }
        } catch (_: SecurityException) {
            null
        }
    }

    /**
     * Get carrier name.
     */
    fun getCarrierName(): String? {
        return try {
            telephonyManager?.networkOperatorName?.takeIf { it.isNotBlank() }
        } catch (_: SecurityException) {
            null
        }
    }

    /**
     * Observe cellular info changes as a continuous Flow.
     * Uses TelephonyCallback on Android 12+ or PhoneStateListener on older versions.
     * This does NOT consume WiFi scan budget - cellular updates are free!
     */
    @Suppress("DEPRECATION")
    fun observeCellInfo(): Flow<List<CellularSource>> = callbackFlow {
        val tm = telephonyManager
        if (tm == null || !hasPermissions()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ uses TelephonyCallback
            val executor: Executor = Executors.newSingleThreadExecutor()
            val callback = object : TelephonyCallback(), TelephonyCallback.CellInfoListener {
                override fun onCellInfoChanged(cellInfoList: MutableList<CellInfo>) {
                    val sources = cellInfoList.mapNotNull { cellInfoToSource(it, tm) }
                    trySend(sources)
                }
            }

            try {
                tm.registerTelephonyCallback(executor, callback)
            } catch (e: SecurityException) {
                close(e)
                return@callbackFlow
            }

            // Emit initial state
            try {
                val initialList = tm.allCellInfo ?: emptyList()
                val sources = initialList.mapNotNull { cellInfoToSource(it, tm) }
                trySend(sources)
            } catch (_: SecurityException) {}

            awaitClose {
                tm.unregisterTelephonyCallback(callback)
            }
        } else {
            // Pre-Android 12 uses PhoneStateListener (deprecated but necessary for older devices)
            @Suppress("DEPRECATION")
            val listener = object : PhoneStateListener() {
                @Deprecated("Deprecated in Java")
                override fun onCellInfoChanged(cellInfoList: MutableList<CellInfo>?) {
                    val sources = cellInfoList?.mapNotNull { cellInfoToSource(it, tm) } ?: emptyList()
                    trySend(sources)
                }
            }

            @Suppress("DEPRECATION")
            try {
                tm.listen(listener, PhoneStateListener.LISTEN_CELL_INFO)
            } catch (e: SecurityException) {
                close(e)
                return@callbackFlow
            }

            // Emit initial state
            try {
                val initialList = tm.allCellInfo ?: emptyList()
                val sources = initialList.mapNotNull { cellInfoToSource(it, tm) }
                trySend(sources)
            } catch (_: SecurityException) {}

            @Suppress("DEPRECATION")
            awaitClose {
                tm.listen(listener, PhoneStateListener.LISTEN_NONE)
            }
        }
    }

    /**
     * Convert CellInfo to our CellularSource model.
     */
    private fun cellInfoToSource(cellInfo: CellInfo, tm: TelephonyManager): CellularSource? {
        return when (cellInfo) {
            is CellInfoLte -> cellInfoLteToSource(cellInfo, tm)
            is CellInfoGsm -> cellInfoGsmToSource(cellInfo, tm)
            is CellInfoWcdma -> cellInfoWcdmaToSource(cellInfo, tm)
            else -> {
                // Check for 5G NR (Android 10+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && cellInfo is CellInfoNr) {
                    cellInfoNrToSource(cellInfo, tm)
                } else {
                    null
                }
            }
        }
    }

    private fun cellInfoLteToSource(cellInfo: CellInfoLte, tm: TelephonyManager): CellularSource {
        val identity = cellInfo.cellIdentity
        val strength = cellInfo.cellSignalStrength

        // Extract detailed LTE info
        val details = extractLteDetails(identity, strength)

        return CellularSource(
            id = UUID.randomUUID().toString(),
            name = getCarrierName(),
            rssi = strength.dbm,
            technology = CellularTechnology.LTE_4G,
            band = identity.earfcnToLteBand(identity.earfcn),
            cellId = identity.ci.takeIf { it != Int.MAX_VALUE },
            isRegistered = cellInfo.isRegistered,
            contributionPercent = 0,
            details = details
        )
    }

    /**
     * Extract detailed LTE cell information.
     * This provides the comprehensive info like ElectroSmart displays.
     */
    private fun extractLteDetails(
        identity: CellIdentityLte,
        strength: CellSignalStrengthLte
    ): CellularDetails {
        val mcc = identity.mccString?.toIntOrNull()
        val mnc = identity.mncString?.toIntOrNull()
        val tac = identity.tac.takeIf { it != Int.MAX_VALUE }
        val ci = identity.ci.takeIf { it != Int.MAX_VALUE }
        val pci = identity.pci.takeIf { it != Int.MAX_VALUE }
        val earfcn = identity.earfcn.takeIf { it != Int.MAX_VALUE }

        // ECI is the full 28-bit E-UTRAN Cell ID
        val eci = ci?.toLong()

        // eNodeB ID = ECI / 256 (upper 20 bits)
        val enb = eci?.let { (it / 256).toInt() }

        // Signal quality metrics
        val rsrp = strength.rsrp.takeIf { it != Int.MAX_VALUE }
        val rsrq = strength.rsrq.takeIf { it != Int.MAX_VALUE }
        val rssnr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            strength.rssnr.takeIf { it != Int.MAX_VALUE }
        } else null
        val cqi = strength.cqi.takeIf { it != Int.MAX_VALUE }
        val ta = strength.timingAdvance.takeIf { it != Int.MAX_VALUE }

        // Bandwidth (API 28+)
        val bandwidth = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            identity.bandwidth.takeIf { it != Int.MAX_VALUE }?.let { it / 1000 } // Convert kHz to MHz
        } else null

        val plmn = if (mcc != null && mnc != null) {
            String.format("%03d%02d", mcc, mnc)
        } else null

        return CellularDetails(
            mcc = mcc,
            mnc = mnc,
            tac = tac,
            ci = ci,
            pci = pci,
            enb = enb,
            eci = eci,
            rsrp = rsrp,
            rsrq = rsrq,
            rssnr = rssnr,
            cqi = cqi,
            ta = ta,
            earfcn = earfcn,
            bandwidth = bandwidth,
            plmn = plmn
        )
    }

    private fun cellInfoGsmToSource(cellInfo: CellInfoGsm, tm: TelephonyManager): CellularSource {
        val identity = cellInfo.cellIdentity
        val strength = cellInfo.cellSignalStrength

        return CellularSource(
            id = UUID.randomUUID().toString(),
            name = getCarrierName(),
            rssi = strength.dbm,
            technology = CellularTechnology.GSM_2G,
            band = identity.arfcnToGsmBand(identity.arfcn),
            cellId = identity.cid.takeIf { it != Int.MAX_VALUE },
            isRegistered = cellInfo.isRegistered,
            contributionPercent = 0
        )
    }

    private fun cellInfoWcdmaToSource(cellInfo: CellInfoWcdma, tm: TelephonyManager): CellularSource {
        val identity = cellInfo.cellIdentity
        val strength = cellInfo.cellSignalStrength

        return CellularSource(
            id = UUID.randomUUID().toString(),
            name = getCarrierName(),
            rssi = strength.dbm,
            technology = CellularTechnology.UMTS_3G,
            band = identity.uarfcnTo3gBand(identity.uarfcn),
            cellId = identity.cid.takeIf { it != Int.MAX_VALUE },
            isRegistered = cellInfo.isRegistered,
            contributionPercent = 0
        )
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun cellInfoNrToSource(cellInfo: CellInfoNr, tm: TelephonyManager): CellularSource {
        val identity = cellInfo.cellIdentity as CellIdentityNr
        val strength = cellInfo.cellSignalStrength as CellSignalStrengthNr

        return CellularSource(
            id = UUID.randomUUID().toString(),
            name = getCarrierName(),
            rssi = strength.dbm,
            technology = CellularTechnology.NR_5G,
            band = nrarfcnTo5gBand(identity.nrarfcn),
            cellId = identity.nci.toInt().takeIf { it != Int.MAX_VALUE },
            isRegistered = cellInfo.isRegistered,
            contributionPercent = 0
        )
    }

    // Band conversion helpers

    private fun CellIdentityLte.earfcnToLteBand(earfcn: Int): Int? {
        return when (earfcn) {
            in 0..599 -> 1
            in 600..1199 -> 2
            in 1200..1949 -> 3
            in 1950..2399 -> 4
            in 2400..2649 -> 5
            in 2650..2749 -> 6
            in 2750..3449 -> 7
            in 3450..3799 -> 8
            in 6150..6449 -> 12
            in 6600..7399 -> 13
            in 9210..9659 -> 20
            in 66436..67335 -> 66
            in 68336..68585 -> 71
            else -> null
        }
    }

    private fun CellIdentityGsm.arfcnToGsmBand(arfcn: Int): Int? {
        return when (arfcn) {
            in 1..124 -> 900     // GSM 900
            in 512..885 -> 1800  // DCS 1800
            in 128..251 -> 850   // GSM 850
            in 512..810 -> 1900  // PCS 1900
            else -> null
        }
    }

    private fun CellIdentityWcdma.uarfcnTo3gBand(uarfcn: Int): Int? {
        return when (uarfcn) {
            in 10562..10838 -> 1   // Band I (2100)
            in 9662..9938 -> 2     // Band II (1900)
            in 1162..1513 -> 3     // Band III (1800)
            in 1537..1738 -> 4     // Band IV (AWS)
            in 4357..4458 -> 5     // Band V (850)
            in 2937..3088 -> 8     // Band VIII (900)
            else -> null
        }
    }

    private fun nrarfcnTo5gBand(nrarfcn: Int): Int? {
        return when (nrarfcn) {
            in 422000..434000 -> 1
            in 386000..398000 -> 2
            in 361000..376000 -> 3
            in 173800..178800 -> 5
            in 524000..538000 -> 7
            in 185000..192000 -> 8
            in 145800..149200 -> 12
            in 151600..153600 -> 14
            in 158200..164200 -> 20
            in 620000..680000 -> 77  // C-Band
            in 693334..733333 -> 78
            in 2054166..2104165 -> 257 // mmWave
            in 2016667..2070832 -> 258
            in 2270833..2337499 -> 260
            in 2070833..2087499 -> 261
            else -> null
        }
    }
}

class CellularScanException(message: String) : Exception(message)
