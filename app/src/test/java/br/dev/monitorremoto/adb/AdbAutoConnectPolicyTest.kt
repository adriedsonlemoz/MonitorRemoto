package br.dev.monitorremoto.adb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdbAutoConnectPolicyTest {
    private fun endpoint(host: String, port: Int, type: String = "_adb-tls-connect._tcp.") =
        AdbDiscovery.Endpoint("service", type, host, port)

    @Test fun neverUsesForeignHostOrPairingPort() {
        val services = listOf(
            endpoint("192.168.1.10", 37123),
            endpoint("192.168.1.11", 41120),
            endpoint("192.168.1.10", 34115, "_adb-tls-pairing._tcp.")
        )
        assertEquals(listOf(37123), AdbAutoConnectPolicy.connectionServices("192.168.1.10", services).map { it.port })
    }

    @Test fun ambiguousPortsNeedManualChoice() {
        val services = listOf(endpoint("192.168.1.10", 37123), endpoint("192.168.1.10", 42111))
        assertEquals(2, AdbAutoConnectPolicy.connectionServices("192.168.1.10", services).size)
        assertTrue(AdbAutoConnectPolicy.connectionServices("192.168.1.12", services).isEmpty())
    }
}
