package br.dev.monitorremoto.core

import org.junit.Assert.*
import org.junit.Test

class HealthAlertPolicyTest {
    @Test fun temperatureThresholdsAndRecovery() {
        val p = HealthAlertPolicy
        assertEquals(HealthAlertPolicy.Level.NORMAL, p.classify(68, 37.0, 0).level)
        assertEquals(HealthAlertPolicy.Level.WARNING, p.classify(68, 38.0, 0).level)
        assertEquals(HealthAlertPolicy.Level.CRITICAL, p.classify(68, 43.0, 0).level)
        assertEquals(HealthAlertPolicy.Level.CRITICAL, p.classify(68, 41.0, 0, HealthAlertPolicy.Level.CRITICAL).level)
        assertEquals(HealthAlertPolicy.Level.WARNING, p.classify(68, 39.0, 0, HealthAlertPolicy.Level.CRITICAL).level)
        assertEquals(HealthAlertPolicy.Level.NORMAL, p.classify(68, 34.9, 0, HealthAlertPolicy.Level.WARNING).level)
    }
    @Test fun batteryAndAndroidThermalAreIndependent() {
        val p = HealthAlertPolicy
        assertEquals(HealthAlertPolicy.Level.WARNING, p.classify(20, null, null).level)
        assertEquals(HealthAlertPolicy.Level.CRITICAL, p.classify(10, null, null).level)
        assertEquals(HealthAlertPolicy.Level.CRITICAL, p.classify(12, null, null, HealthAlertPolicy.Level.CRITICAL).level)
        assertEquals(HealthAlertPolicy.Level.WARNING, p.classify(14, null, null, HealthAlertPolicy.Level.CRITICAL).level)
        assertEquals(HealthAlertPolicy.Level.CRITICAL, p.classify(90, null, 3).level)
        assertTrue(p.shouldProtect(3, HealthAlertPolicy.Level.CRITICAL, null))
        assertTrue(p.shouldProtect(null, HealthAlertPolicy.Level.CRITICAL, 43.0))
        assertFalse(p.shouldProtect(null, HealthAlertPolicy.Level.CRITICAL, 37.0))
        assertEquals(HealthAlertPolicy.Level.NORMAL, p.classify(null, null, null).level)
    }
}
