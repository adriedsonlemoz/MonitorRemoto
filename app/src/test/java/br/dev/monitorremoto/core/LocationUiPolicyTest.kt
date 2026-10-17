package br.dev.monitorremoto.core

import org.junit.Assert.*
import org.junit.Test

class LocationUiPolicyTest {
    @Test fun trackingIsFastButBoundedToForegroundUse() {
        assertEquals(3_000L, LocationUiPolicy.refreshDelayMs(true, false, 60_000L))
        assertEquals(10_000L, LocationUiPolicy.refreshDelayMs(false, true, 10_000L))
        assertNull(LocationUiPolicy.refreshDelayMs(false, false, 10_000L))
        assertNull(LocationUiPolicy.refreshDelayMs(false, true, 1_000L))
    }

    @Test fun staleFixIsNeverCalledRecent() {
        assertTrue(LocationUiPolicy.gpsIsRecent("disponivel", 10_000L))
        assertFalse(LocationUiPolicy.gpsIsRecent("antiga", 1_000L))
        assertFalse(LocationUiPolicy.gpsIsRecent("disponivel", 31_000L))
        assertFalse(LocationUiPolicy.gpsIsRecent("disponivel", -1L))
    }

    @Test fun ageIncreasesWithoutInventingNewFix() {
        assertEquals(15_000L, LocationUiPolicy.elapsedAgeMs(12_000L, 3_000L))
        assertEquals(-1L, LocationUiPolicy.elapsedAgeMs(-1L, 3_000L))
    }
}
