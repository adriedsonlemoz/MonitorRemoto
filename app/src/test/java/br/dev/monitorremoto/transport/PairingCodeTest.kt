package br.dev.monitorremoto.transport

import org.junit.Assert.*
import org.junit.Test

class PairingCodeTest {
    @Test fun roundTrip() {
        val offer = PairingSession.issue().copy(host = "192.168.43.1")
        val parsed = PairingCode.decode(PairingCode.encode(offer))
        assertEquals(offer.host, parsed.host)
        assertEquals(offer.session, parsed.session)
        assertArrayEquals(offer.ephemeralKey, parsed.ephemeralKey)
        PairingSession.revoke()
    }

    @Test fun privateNetworksOnly() {
        assertTrue(PairingCode.isPrivateIpv4("10.1.2.3"))
        assertTrue(PairingCode.isPrivateIpv4("172.31.255.1"))
        assertTrue(PairingCode.isPrivateIpv4("192.168.0.1"))
        listOf("8.8.8.8", "169.254.1.1", "127.0.0.1", "172.32.0.1", "192.169.1.1", "192.168.1.999", "192.168.01.1", "example.org")
            .forEach { assertFalse(it, PairingCode.isPrivateIpv4(it)) }
    }

    @Test fun oneTimeAndRevoke() {
        val first = PairingSession.issue()
        assertNotNull(PairingSession.active())
        assertFalse(PairingSession.consume("invalid", first.ephemeralKey))
        assertTrue(PairingSession.consume(first.session, first.ephemeralKey))
        assertFalse(PairingSession.consume(first.session, first.ephemeralKey))
        assertNull(PairingSession.active())
        val second = PairingSession.issue()
        PairingSession.revoke(second.session)
        assertNull(PairingSession.active())
    }

    @Test fun rejectsUnexpectedQr() {
        val offer = PairingSession.issue().copy(host = "192.168.1.2")
        val qr = PairingCode.encode(offer)
        listOf(
            qr.replace("port=37841", "port=8080"),
            qr.replace("host=192.168.1.2", "host=8.8.8.8"),
            qr + "&v=1",
            qr.replace("monitorremoto://", "https://"),
            "https://example.org/pair?secret=foo",
        ).forEach { bad ->
            try { PairingCode.decode(bad); fail("QR deveria ser rejeitado") }
            catch (_: IllegalArgumentException) { }
        }
        PairingSession.revoke()
    }
}
