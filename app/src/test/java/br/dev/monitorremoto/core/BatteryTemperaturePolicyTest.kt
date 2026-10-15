package br.dev.monitorremoto.core

import org.junit.Assert.*
import org.junit.Test

class BatteryTemperaturePolicyTest {
    @Test fun zonesAreConservativeIndicatorsNotSafetyThresholds() {
        assertEquals(BatteryTemperaturePolicy.Zone.UNKNOWN, BatteryTemperaturePolicy.zone(null))
        assertEquals(BatteryTemperaturePolicy.Zone.UNKNOWN, BatteryTemperaturePolicy.zone(Double.NaN))
        assertEquals(BatteryTemperaturePolicy.Zone.COLD, BatteryTemperaturePolicy.zone(14.9))
        assertEquals(BatteryTemperaturePolicy.Zone.NORMAL, BatteryTemperaturePolicy.zone(15.0))
        assertEquals(BatteryTemperaturePolicy.Zone.NORMAL, BatteryTemperaturePolicy.zone(37.9))
        assertEquals(BatteryTemperaturePolicy.Zone.WARM, BatteryTemperaturePolicy.zone(38.0))
        assertEquals(BatteryTemperaturePolicy.Zone.HOT, BatteryTemperaturePolicy.zone(43.0))
    }
}
