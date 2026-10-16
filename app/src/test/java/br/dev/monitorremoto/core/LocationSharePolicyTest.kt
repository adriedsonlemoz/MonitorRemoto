package br.dev.monitorremoto.core

import org.junit.Assert.*
import org.junit.Test

class LocationSharePolicyTest {
    @Test fun perfisRespeitamIntervalos() {
        assertEquals(300_000L, LocationSharePolicy.intervalMs("economico"))
        assertEquals(60_000L, LocationSharePolicy.intervalMs("equilibrado"))
        assertEquals(30_000L, LocationSharePolicy.intervalMs("intermediario"))
        assertEquals(10_000L, LocationSharePolicy.intervalMs("preciso"))
        assertEquals(300_000L, LocationSharePolicy.intervalMs("invalido"))
    }
    @Test fun coordenadasForaDaTerraOuNaoFinitasSaoRejeitadas() {
        assertTrue(LocationSharePolicy.coordinatesValid(-20.0, -45.0))
        assertFalse(LocationSharePolicy.coordinatesValid(95.0, 0.0))
        assertFalse(LocationSharePolicy.coordinatesValid(0.0, 185.0))
        assertFalse(LocationSharePolicy.coordinatesValid(Double.NaN, 0.0))
        assertFalse(LocationSharePolicy.coordinatesValid(Double.POSITIVE_INFINITY, 0.0))
    }
    @Test fun posicoesExpiram() {
        assertTrue(LocationSharePolicy.usableAgeMs(30_000L))
        assertFalse(LocationSharePolicy.usableAgeMs(-1))
        assertFalse(LocationSharePolicy.usableAgeMs(1_800_001L))
    }
}
