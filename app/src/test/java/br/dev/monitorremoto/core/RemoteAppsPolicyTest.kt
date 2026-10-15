package br.dev.monitorremoto.core

import org.junit.Assert.*
import org.junit.Test

class RemoteAppsPolicyTest {
    @Test fun `allow normal packages and deny shell metacharacters`() {
        assertTrue(RemoteAppsPolicy.validPackage("com.whatsapp"))
        assertTrue(RemoteAppsPolicy.validPackage("br.dev.minhacamera"))
        assertFalse(RemoteAppsPolicy.validPackage("com.app;reboot"))
        assertFalse(RemoteAppsPolicy.validPackage("com.app\nreboot"))
        assertFalse(RemoteAppsPolicy.validPackage("app"))
        assertFalse(RemoteAppsPolicy.validPackage("com.example/other"))
    }
    @Test fun `stop protects essential apps`() {
        assertFalse(RemoteAppsPolicy.canForceStop("br.dev.monitorremoto"))
        assertFalse(RemoteAppsPolicy.canForceStop("com.android.systemui"))
        assertFalse(RemoteAppsPolicy.canForceStop("com.miui.securitycenter"))
        assertFalse(RemoteAppsPolicy.canForceStop("com.google.android.gms"))
        assertTrue(RemoteAppsPolicy.canForceStop("com.whatsapp"))
    }
    @Test fun `commands are fixed and validated`() {
        assertEquals("am force-stop com.whatsapp", RemoteAppsPolicy.command("com.whatsapp", "stop"))
        assertTrue(RemoteAppsPolicy.command("com.whatsapp", "open").startsWith("monkey -p com.whatsapp"))
        assertTrue(RemoteAppsPolicy.command("com.whatsapp", "settings").contains("APPLICATION_DETAILS_SETTINGS"))
        assertThrows(IllegalArgumentException::class.java) { RemoteAppsPolicy.command("com.android.systemui", "stop") }
        assertThrows(IllegalStateException::class.java) { RemoteAppsPolicy.command("com.whatsapp", "shell") }
    }
}
