package com.openemf.sensors

import com.openemf.sensors.api.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for Phase 3 enhanced source details.
 * Tests new data fields and CellularDetails functionality.
 */
class SourceDetailsTest {

    // ==================== WifiSource Enhanced Fields Tests ====================

    @Test
    fun `WifiSource has default channel width of UNKNOWN`() {
        val source = WifiSource(
            id = "test",
            name = "TestNetwork",
            bssid = "00:11:22:33:44:55",
            rssi = -50,
            frequency = 2437,
            band = WifiBand.WIFI_2_4_GHZ,
            channel = 6,
            contributionPercent = 0
        )
        assertEquals(WifiChannelWidth.UNKNOWN, source.channelWidth)
    }

    @Test
    fun `WifiSource accepts all channel width values`() {
        val widths = listOf(
            WifiChannelWidth.WIDTH_20_MHZ,
            WifiChannelWidth.WIDTH_40_MHZ,
            WifiChannelWidth.WIDTH_80_MHZ,
            WifiChannelWidth.WIDTH_160_MHZ,
            WifiChannelWidth.WIDTH_80_PLUS_80_MHZ,
            WifiChannelWidth.WIDTH_320_MHZ,
            WifiChannelWidth.UNKNOWN
        )

        widths.forEach { width ->
            val source = createWifiSource(channelWidth = width)
            assertEquals(width, source.channelWidth)
        }
    }

    @Test
    fun `WifiSource accepts all standard values`() {
        val standards = listOf(
            WifiStandard.LEGACY,
            WifiStandard.WIFI_4,
            WifiStandard.WIFI_5,
            WifiStandard.WIFI_6,
            WifiStandard.WIFI_6E,
            WifiStandard.WIFI_7,
            WifiStandard.UNKNOWN
        )

        standards.forEach { standard ->
            val source = createWifiSource(standard = standard)
            assertEquals(standard, source.standard)
        }
    }

    @Test
    fun `WifiSource accepts all security values`() {
        val securities = listOf(
            WifiSecurity.OPEN,
            WifiSecurity.WEP,
            WifiSecurity.WPA,
            WifiSecurity.WPA2,
            WifiSecurity.WPA3,
            WifiSecurity.WPA2_WPA3,
            WifiSecurity.UNKNOWN
        )

        securities.forEach { security ->
            val source = createWifiSource(security = security)
            assertEquals(security, source.security)
        }
    }

    // ==================== CellularDetails Tests ====================

    @Test
    fun `CellularSource has default null details`() {
        val source = CellularSource(
            id = "test",
            name = "Carrier",
            rssi = -80,
            technology = CellularTechnology.LTE_4G,
            band = null,
            cellId = null,
            contributionPercent = 0
        )
        assertNull(source.details)
    }

    @Test
    fun `CellularDetails stores identity information`() {
        val details = CellularDetails(
            mcc = 404,
            mnc = 49,
            tac = 12345,
            ci = 67890,
            pci = 123,
            enb = 265,
            eci = 67890L,
            rsrp = -85,
            rsrq = -10,
            rssnr = 15,
            cqi = 12,
            ta = 5,
            earfcn = 6300,
            bandwidth = 20,
            plmn = "40449"
        )

        assertEquals(404, details.mcc)
        assertEquals(49, details.mnc)
        assertEquals(12345, details.tac)
        assertEquals(67890, details.ci)
        assertEquals(123, details.pci)
        assertEquals(265, details.enb)
        assertEquals(67890L, details.eci)
    }

    @Test
    fun `CellularDetails stores signal quality metrics`() {
        val details = CellularDetails(
            mcc = 404, mnc = 49, tac = null, ci = null, pci = null,
            enb = null, eci = null,
            rsrp = -95,
            rsrq = -12,
            rssnr = 10,
            cqi = 8,
            ta = 3,
            earfcn = null, bandwidth = null, plmn = null
        )

        assertEquals(-95, details.rsrp)
        assertEquals(-12, details.rsrq)
        assertEquals(10, details.rssnr)
        assertEquals(8, details.cqi)
        assertEquals(3, details.ta)
    }

    @Test
    fun `CellularDetails formattedPlmn combines MCC and MNC`() {
        val details = CellularDetails(
            mcc = 310, mnc = 260,
            tac = null, ci = null, pci = null, enb = null, eci = null,
            rsrp = null, rsrq = null, rssnr = null, cqi = null, ta = null,
            earfcn = null, bandwidth = null, plmn = null
        )

        assertEquals("310-260", details.formattedPlmn)
    }

    @Test
    fun `CellularDetails formattedPlmn returns null when MCC or MNC missing`() {
        val detailsNoMcc = CellularDetails(
            mcc = null, mnc = 260,
            tac = null, ci = null, pci = null, enb = null, eci = null,
            rsrp = null, rsrq = null, rssnr = null, cqi = null, ta = null,
            earfcn = null, bandwidth = null, plmn = null
        )
        assertNull(detailsNoMcc.formattedPlmn)

        val detailsNoMnc = CellularDetails(
            mcc = 310, mnc = null,
            tac = null, ci = null, pci = null, enb = null, eci = null,
            rsrp = null, rsrq = null, rssnr = null, cqi = null, ta = null,
            earfcn = null, bandwidth = null, plmn = null
        )
        assertNull(detailsNoMnc.formattedPlmn)
    }

    @Test
    fun `CellularDetails calculatedEnb derives from ECI`() {
        // ECI = eNB * 256 + CID
        // If ECI = 265 * 256 + 123 = 67963
        val details = CellularDetails(
            mcc = null, mnc = null, tac = null, ci = null, pci = null,
            enb = null, eci = 67963L,
            rsrp = null, rsrq = null, rssnr = null, cqi = null, ta = null,
            earfcn = null, bandwidth = null, plmn = null
        )

        assertEquals(265, details.calculatedEnb)
    }

    @Test
    fun `CellularDetails localCellId derives from ECI`() {
        // ECI = eNB * 256 + CID
        // If ECI = 265 * 256 + 123 = 67963
        val details = CellularDetails(
            mcc = null, mnc = null, tac = null, ci = null, pci = null,
            enb = null, eci = 67963L,
            rsrp = null, rsrq = null, rssnr = null, cqi = null, ta = null,
            earfcn = null, bandwidth = null, plmn = null
        )

        assertEquals(123, details.localCellId)
    }

    @Test
    fun `CellularDetails calculatedEnb and localCellId return null when ECI missing`() {
        val details = CellularDetails(
            mcc = 404, mnc = 49, tac = 12345, ci = 67890, pci = 123,
            enb = 265, eci = null,
            rsrp = -85, rsrq = -10, rssnr = 15, cqi = 12, ta = 5,
            earfcn = 6300, bandwidth = 20, plmn = "40449"
        )

        assertNull(details.calculatedEnb)
        assertNull(details.localCellId)
    }

    // ==================== CellularSource with Details Tests ====================

    @Test
    fun `CellularSource with LTE details`() {
        val details = CellularDetails(
            mcc = 404, mnc = 49, tac = 12345, ci = 67890, pci = 123,
            enb = 265, eci = 67890L,
            rsrp = -85, rsrq = -10, rssnr = 15, cqi = 12, ta = 5,
            earfcn = 6300, bandwidth = 20, plmn = "40449"
        )

        val source = CellularSource(
            id = "lte_test",
            name = "Airtel",
            rssi = -85,
            technology = CellularTechnology.LTE_4G,
            band = 3,
            cellId = 67890,
            isRegistered = true,
            contributionPercent = 50,
            details = details
        )

        assertNotNull(source.details)
        assertEquals(404, source.details?.mcc)
        assertEquals(-85, source.details?.rsrp)
        assertEquals("404-49", source.details?.formattedPlmn)
    }

    // ==================== Helper Functions ====================

    private fun createWifiSource(
        channelWidth: WifiChannelWidth = WifiChannelWidth.UNKNOWN,
        standard: WifiStandard = WifiStandard.UNKNOWN,
        security: WifiSecurity = WifiSecurity.UNKNOWN
    ): WifiSource {
        return WifiSource(
            id = "test",
            name = "TestNetwork",
            bssid = "00:11:22:33:44:55",
            rssi = -50,
            frequency = 2437,
            band = WifiBand.WIFI_2_4_GHZ,
            channel = 6,
            channelWidth = channelWidth,
            standard = standard,
            security = security,
            contributionPercent = 0
        )
    }
}
