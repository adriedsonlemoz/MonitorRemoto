package br.dev.monitorremoto.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AdbWizardStatusTest {
    @Test fun disconnectIsNotConnection() {
        assertEquals(AdbWizardStatus.State.DISCONNECTED, AdbWizardStatus.resolve(false, false, false))
    }

    @Test fun pairWithoutSocketIsNotConnected() {
        assertEquals(AdbWizardStatus.State.PAIRED_NOT_CONNECTED, AdbWizardStatus.resolve(false, true, false))
    }

    @Test fun activeConnectionWins() {
        assertEquals(AdbWizardStatus.State.CONNECTED, AdbWizardStatus.resolve(true, false, false))
        assertEquals(AdbWizardStatus.State.CONNECTED, AdbWizardStatus.resolve(true, true, false))
    }

    @Test fun monitoredCannotVerifyOtherAdbClients() {
        assertEquals(AdbWizardStatus.State.MONITORED_NOT_VERIFIABLE, AdbWizardStatus.resolve(false, false, true))
    }
}
