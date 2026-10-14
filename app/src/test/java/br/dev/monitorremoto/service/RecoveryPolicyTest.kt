package br.dev.monitorremoto.service

import org.junit.Assert.*
import org.junit.Test

class RecoveryPolicyTest {
    @Test fun backoffCapsAtFiveMinutes() {
        val delays = listOf(2_000L, 5_000L, 10_000L, 30_000L, 60_000L, 300_000L)
        for (i in 1..6) assertEquals(delays[i - 1], RecoveryPolicy.nextDelayMs(i))
        assertEquals(300_000L, RecoveryPolicy.nextDelayMs(20))
        assertEquals(2_000L, RecoveryPolicy.nextDelayMs(0))
    }
    @Test fun doesNotRetryBeforeDeadline() {
        assertFalse(RecoveryPolicy.canRetry(999L, 1000L))
        assertTrue(RecoveryPolicy.canRetry(1000L, 1000L))
        assertTrue(RecoveryPolicy.canRetry(1001L, 1000L))
    }
}
