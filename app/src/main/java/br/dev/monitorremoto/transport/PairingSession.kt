package br.dev.monitorremoto.transport

import br.dev.monitorremoto.security.CryptoBox
import java.security.MessageDigest

/** Sessão temporária apenas em memória, sem chave longa no QR e sem persistência. */
object PairingSession {
    const val EXPIRES_MS = 120_000L
    private data class Stored(val id: String, val key: ByteArray, val expiresAt: Long)
    private var current: Stored? = null

    private fun elapsedMs(): Long = System.nanoTime() / 1_000_000L

    @Synchronized fun issue(): PairingCode.Offer {
        val id = CryptoBox.encode(CryptoBox.newPairingKey().copyOf(16))
        val key = CryptoBox.newPairingKey()
        current = Stored(id, key, elapsedMs() + EXPIRES_MS)
        // Host é definido pela interface após escolha da rede local.
        return PairingCode.Offer("", id, key.copyOf())
    }

    @Synchronized fun active(): Pair<String, ByteArray>? {
        val session = current ?: return null
        if (elapsedMs() >= session.expiresAt) { current = null; return null }
        return session.id to session.key.copyOf()
    }

    @Synchronized fun consume(id: String, key: ByteArray): Boolean {
        val session = current ?: return false
        if (elapsedMs() >= session.expiresAt) { current = null; return false }
        if (session.id != id || !MessageDigest.isEqual(session.key, key)) return false
        current = null
        return true
    }

    @Synchronized fun revoke(id: String? = null) {
        if (id == null || current?.id == id) current = null
    }
}
