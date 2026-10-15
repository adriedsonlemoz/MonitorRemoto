package br.dev.monitorremoto.core

import org.junit.Assert.*
import org.junit.Test

class DeviceControlPolicyTest {
    @Test fun durationWhitelist() {
        listOf(5, 10, 20, 30).forEach { assertTrue(DeviceControlPolicy.valid("soundStart", it)) }
        listOf(-1, 0, 1, 4, 6, 31, 1000).forEach { assertFalse(DeviceControlPolicy.valid("soundStart", it)) }
    }
    @Test fun closedOperations() {
        DeviceControlPolicy.actions.forEach {
            assertTrue(DeviceControlPolicy.valid(it, if (it == "soundStart") 5 else 0,
                if (it == "lockNow") "BLOQUEAR" else ""))
        }
        assertFalse(DeviceControlPolicy.valid("torchOn", 5))
        assertFalse(DeviceControlPolicy.valid("lockNow", 0))
        assertTrue(DeviceControlPolicy.valid("lockNow", 0, "BLOQUEAR"))
        assertFalse(DeviceControlPolicy.valid("lockNow", 0, "bloquear"))
        assertFalse(DeviceControlPolicy.valid("lockNow", 5, "BLOQUEAR"))
        assertFalse(DeviceControlPolicy.valid("status", 0, "BLOQUEAR"))
        assertFalse(DeviceControlPolicy.valid("soundStop", 30))
        listOf("", "shell", "adb", "reboot", "cameraCapture", "openApp", "soundLoop").forEach {
            assertFalse(DeviceControlPolicy.valid(it, 0))
        }
    }
    @Test fun independentLocalPermissions() {
        assertTrue(DeviceControlPolicy.permitted("status", false, false))
        assertTrue(DeviceControlPolicy.permitted("soundStop", false, false))
        assertFalse(DeviceControlPolicy.permitted("soundStart", false, true))
        assertTrue(DeviceControlPolicy.permitted("soundStart", true, false))
        assertFalse(DeviceControlPolicy.permitted("torchOn", true, false))
        assertFalse(DeviceControlPolicy.permitted("torchOff", true, false))
        assertTrue(DeviceControlPolicy.permitted("torchOn", false, true))
        assertTrue(DeviceControlPolicy.permitted("torchOff", false, true))
        assertFalse(DeviceControlPolicy.permitted("lockNow", true, true))
        assertFalse(DeviceControlPolicy.permitted("lockNow", false, false, false))
        assertTrue(DeviceControlPolicy.permitted("lockNow", false, false, true))
    }
}
