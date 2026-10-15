package br.dev.monitorremoto.core

import org.junit.Assert.*
import org.junit.Test

class BatteryTelemetryTest {
    @Test fun batteryPercentageUsesRealLevelAndScale() {
        assertEquals(72, BatteryTelemetry.percentage(72, 100))
        assertEquals(73, BatteryTelemetry.percentage(145, 200))
        assertEquals(0, BatteryTelemetry.percentage(0, 100))
        assertEquals(100, BatteryTelemetry.percentage(7, 7))
        assertNull(BatteryTelemetry.percentage(-1, 100))
        assertNull(BatteryTelemetry.percentage(101, 100))
        assertNull(BatteryTelemetry.percentage(1, 0))
    }

    @Test fun statusNeverAssumesUnknownIsDischarging() {
        assertEquals("Carregando", BatteryTelemetry.state(2))
        assertEquals("Descarregando", BatteryTelemetry.state(3))
        assertEquals("Não carregando", BatteryTelemetry.state(4))
        assertEquals("Cheia", BatteryTelemetry.state(5))
        assertEquals("Desconhecido", BatteryTelemetry.state(1))
        assertEquals("Desconhecido", BatteryTelemetry.state(-1))
    }

    @Test fun typeOnlyReportedWhenRecognized() {
        assertEquals("Tomada", BatteryTelemetry.powerSource(1))
        assertEquals("USB", BatteryTelemetry.powerSource(2))
        assertEquals("Sem fio", BatteryTelemetry.powerSource(4))
        assertEquals("Base", BatteryTelemetry.powerSource(8))
        assertNull(BatteryTelemetry.powerSource(0))
        assertNull(BatteryTelemetry.powerSource(-1))
        assertNull(BatteryTelemetry.powerSource(3))
    }

    @Test fun powerIndicatorReflectsChargingOrPoweredFullBattery() {
        assertTrue(BatteryTelemetry.showChargingIndicator(2, 2))
        assertTrue(BatteryTelemetry.showChargingIndicator(2, 0))
        assertTrue(BatteryTelemetry.showChargingIndicator(5, 1))
        assertFalse(BatteryTelemetry.showChargingIndicator(3, 0))
        assertFalse(BatteryTelemetry.showChargingIndicator(4, 1))
        assertFalse(BatteryTelemetry.showChargingIndicator(1, -1))
        assertFalse(BatteryTelemetry.showChargingIndicator(5, 0))
    }
}
