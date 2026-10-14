package br.dev.monitorremoto.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryDecisionTest {
    @Test fun onlyAttemptsWhenExplicitlyAuthorizedAndServiceMissing() {
        assertTrue(RecoveryDecision.shouldAttempt(true, true, false))
        assertFalse(RecoveryDecision.shouldAttempt(false, true, false))
        assertFalse(RecoveryDecision.shouldAttempt(true, false, false))
        assertFalse(RecoveryDecision.shouldAttempt(true, true, true))
        assertFalse(RecoveryDecision.shouldAttempt(false, false, false))
    }
}
