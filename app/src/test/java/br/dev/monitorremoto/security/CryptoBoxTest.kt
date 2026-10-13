package br.dev.monitorremoto.security

import org.junit.Assert.*
import org.junit.Test
import javax.crypto.AEADBadTagException

class CryptoBoxTest {
    @Test fun roundTripAndUniqueNonces() {
        val key = CryptoBox.newPairingKey()
        val one = CryptoBox.seal(key, "SMS com acentuação: coração")
        val two = CryptoBox.seal(key, "SMS com acentuação: coração")
        assertNotEquals(one.nonce, two.nonce)
        assertEquals("SMS com acentuação: coração", CryptoBox.open(key, one))
        assertArrayEquals(key, CryptoBox.decodeKey(CryptoBox.encode(key)))
    }

    @Test(expected = AEADBadTagException::class)
    fun wrongKeyCannotRead() {
        val packet = CryptoBox.seal(CryptoBox.newPairingKey(), "privado")
        CryptoBox.open(CryptoBox.newPairingKey(), packet)
    }

    @Test(expected = IllegalArgumentException::class)
    fun refusesShortKeys() { CryptoBox.decodeKey("YWJjZA") }
}
