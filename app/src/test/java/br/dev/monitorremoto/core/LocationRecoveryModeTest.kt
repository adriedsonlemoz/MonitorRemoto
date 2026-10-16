package br.dev.monitorremoto.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationRecoveryModeTest {
    @Test fun onlyFullyAuthorizedRecoveryRuns() {
        assertTrue(LocationRecoveryMode.canRecover(true, true, true, true, true))
        assertFalse(LocationRecoveryMode.canRecover(false, true, true, true, true))
        assertFalse(LocationRecoveryMode.canRecover(true, false, true, true, true))
        assertFalse(LocationRecoveryMode.canRecover(true, true, false, true, true))
        assertFalse(LocationRecoveryMode.canRecover(true, true, true, false, true))
        assertFalse(LocationRecoveryMode.canRecover(true, true, true, true, false))
    }
}
