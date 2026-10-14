package br.dev.monitorremoto.adb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdbReconnectPolicyTest {
    @Test fun delaysGrowButStayBounded() {
        assertEquals(2000L, AdbReconnectPolicy.delayMs(0))
        assertEquals(4000L, AdbReconnectPolicy.delayMs(1))
        assertEquals(8000L, AdbReconnectPolicy.delayMs(2))
        assertEquals(15000L, AdbReconnectPolicy.delayMs(3))
        assertEquals(30000L, AdbReconnectPolicy.delayMs(10))
        assertEquals(2000L, AdbReconnectPolicy.delayMs(-1))
    }

    @Test fun neverReconnectsWithoutForegroundOrUserSelectedSession() {
        assertTrue(AdbReconnectPolicy.shouldRetry(true, true, false, false))
        assertFalse(AdbReconnectPolicy.shouldRetry(false, true, false, false))
        assertFalse(AdbReconnectPolicy.shouldRetry(true, false, false, false))
        assertFalse(AdbReconnectPolicy.shouldRetry(true, true, true, false))
        assertFalse(AdbReconnectPolicy.shouldRetry(true, true, false, true))
    }
}
