package br.dev.monitorremoto.adb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AdbScreenRoutingTest {
    @Test fun navigationUsesAdbOnlyAfterBothChannelsAreValidated() {
        assertEquals(AdbPolicy.Command.BACK, AdbScreenRouting.navigation("back", true, true))
        assertEquals(AdbPolicy.Command.HOME, AdbScreenRouting.navigation("home", true, true))
        assertEquals(AdbPolicy.Command.RECENTS, AdbScreenRouting.navigation("recents", true, true))
        assertNull(AdbScreenRouting.navigation("back", true, false))
        assertNull(AdbScreenRouting.navigation("home", false, true))
        assertNull(AdbScreenRouting.navigation("notifications", true, true))
        assertNull(AdbScreenRouting.navigation("tap", true, true))
    }

    @Test fun labelDoesNotClaimVideoTransportOverAdb() {
        val label = AdbScreenRouting.label(true)
        assert(label.contains("Navegação: ADB conectado"))
        assert(label.contains("MediaProjection"))
        assert(label.contains("Imagem: Monitor Remoto"))
        assertEquals("Navegação: Monitor Remoto • Imagem: MediaProjection", AdbScreenRouting.label(false))
    }
}
