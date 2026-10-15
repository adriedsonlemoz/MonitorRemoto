package br.dev.monitorremoto.adb

import org.junit.Assert.*
import org.junit.Test

class AdbPolicyTest {
    @Test fun privateNetworks() {
        for (ip in listOf("10.0.0.2", "172.16.0.1", "172.31.255.254", "192.168.0.4")) {
            assertEquals(ip to 37219, AdbPolicy.endpoint(ip, "37219"))
        }
    }

    @Test fun rejectInternetLoopbackAndNames() {
        for (ip in listOf("8.8.8.8", "127.0.0.1", "0.0.0.0", "172.32.0.1", "172.15.1.1", "192.169.1.1", "169.254.2.3", "example.com", "::1", "10.0.0.256", "10.0.0.-1", "10.0.0.1;id")) {
            assertTrue(ip, runCatching { AdbPolicy.endpoint(ip, "1234") }.isFailure)
        }
    }

    @Test fun dynamicPortsAndBounds() {
        for (port in listOf("0", "65536", "-1", "abc", "")) {
            assertTrue(runCatching { AdbPolicy.endpoint("192.168.1.2", port) }.isFailure)
        }
        assertEquals(65535, AdbPolicy.endpoint("192.168.1.2", "65535").second)
    }

    @Test fun numericCodePreservesLeadingZero() {
        assertEquals("001234", AdbPolicy.code("001234"))
        for (code in listOf("12345", "1234567", "abcdef", "１２３４５６")) {
            assertTrue(runCatching { AdbPolicy.code(code) }.isFailure)
        }
    }

    @Test fun qrFormatEntropyAndExpiration() {
        val qr = AdbPolicy.newQr(100)
        val other = AdbPolicy.newQr(100)
        assertTrue(qr.payload().matches(Regex("WIFI:T:ADB;S:studio-[0-9a-f]{32};P:[0-9a-f]{32};;")))
        assertNotEquals(qr.service, other.service)
        assertNotEquals(qr.password, other.password)
        assertTrue(qr.valid(120099))
        assertFalse(qr.valid(120100))
        assertFalse(qr.toString().contains(qr.password))
    }

    @Test fun commandAllowlist() {
        // Regressão do workflow #53: a contagem fixa antiga (4) deixou de acompanhar
        // HEARTBEAT e os comandos de volume. Verificar nomes E shells exatos evita
        // simplesmente aumentar a contagem e permitir comandos imprevistos.
        val expected = mapOf(
            AdbPolicy.Command.DIAGNOSTIC to "getprop ro.product.model; getprop ro.build.version.release; getprop ro.product.marketname; getprop ro.product.vendor.marketname",
            AdbPolicy.Command.HEARTBEAT to "echo monitor_remoto_alive",
            AdbPolicy.Command.BACK to "input keyevent 4",
            AdbPolicy.Command.HOME to "input keyevent 3",
            AdbPolicy.Command.RECENTS to "input keyevent 187",
            AdbPolicy.Command.VOLUME_UP to "input keyevent 24",
            AdbPolicy.Command.VOLUME_DOWN to "input keyevent 25"
        )
        assertEquals(expected.keys, AdbPolicy.Command.entries.toSet())
        assertEquals(7, AdbPolicy.Command.entries.size)
        expected.forEach { (command, shell) -> assertEquals(command.name, shell, command.shell) }
        val forbidden = listOf("tcpip", "screencap", "poweroff", "shutdown", "reboot", "settings put", "rm -", "su ")
        for (command in AdbPolicy.Command.entries) {
            for (term in forbidden) assertFalse("${command.name} cannot contain $term", command.shell.contains(term, ignoreCase = true))
        }
    }
}
