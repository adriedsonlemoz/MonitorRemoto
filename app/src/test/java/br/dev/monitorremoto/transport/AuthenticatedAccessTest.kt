package br.dev.monitorremoto.transport

import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class AuthenticatedAccessTest {
    @After fun cleanup() { AuthenticatedAccess.clear() }

    @Test fun recordsVerifiedSnapshotAndDeclaredMethod() {
        AuthenticatedAccess.clear()
        AuthenticatedAccess.authenticated("192.168.1.23", "abcd1234-2345-4567-9876-abcdef123456", "Celular", "QR Code", 1000)
        val peers = AuthenticatedAccess.recent(1001)
        assertEquals(1, peers.size)
        assertEquals("Celular", peers[0].name)
        assertEquals("QR Code", peers[0].method)
        assertEquals("192.168.1.23", peers[0].address)
    }

    @Test fun expiresIfNoNewAuthenticatedRequest() {
        AuthenticatedAccess.clear()
        AuthenticatedAccess.authenticated("192.168.1.30", "", "", "", 1_000)
        assertEquals(1, AuthenticatedAccess.recent(45_999).size)
        assertTrue(AuthenticatedAccess.recent(46_001).isEmpty())
    }

    @Test fun refreshesLastSeenWithoutChangingInitialAccess() {
        AuthenticatedAccess.clear()
        AuthenticatedAccess.authenticated("192.168.1.20", "stable-id-12345", "X", "Manual", 2_000)
        AuthenticatedAccess.authenticated("192.168.1.20", "stable-id-12345", "X", "Chave salva", 16_000)
        val peer = AuthenticatedAccess.recent(16_001).single()
        assertEquals(2_000, peer.sinceMs)
        assertEquals(16_000, peer.lastSeenMs)
        assertEquals("Chave salva", peer.method)
    }

    @Test fun ignoresUnknownClientMethodAndNotifies() {
        AuthenticatedAccess.clear()
        var calls = 0
        val observer: () -> Unit = { calls++ }
        AuthenticatedAccess.subscribe(observer)
        try {
            AuthenticatedAccess.authenticated("192.168.1.8", "", "", "admin", 9_000)
            assertEquals("Não informado", AuthenticatedAccess.recent(9_001).single().method)
            assertEquals(1, calls)
        } finally { AuthenticatedAccess.unsubscribe(observer) }
    }
    @Test fun explicitDisconnectRemovesOnlyTheMatchingDevice() {
        AuthenticatedAccess.clear()
        AuthenticatedAccess.authenticated("192.168.1.20", "alpha-client-1234", "A", "Manual", 1000)
        AuthenticatedAccess.authenticated("192.168.1.21", "beta-client-5678", "B", "QR Code", 1100)
        AuthenticatedAccess.disconnected("192.168.1.20", "alpha-client-1234")
        val peers = AuthenticatedAccess.recent(1200)
        assertEquals(1, peers.size)
        assertEquals("192.168.1.21", peers[0].address)
    }

    @Test fun unknownDisconnectDoesNotEraseAnotherDevice() {
        AuthenticatedAccess.clear()
        AuthenticatedAccess.authenticated("192.168.1.20", "alpha-client-1234", "A", "Manual", 1000)
        AuthenticatedAccess.disconnected("192.168.1.20", "wrong-id-5678")
        assertEquals(1, AuthenticatedAccess.recent(1200).size)
    }

}
