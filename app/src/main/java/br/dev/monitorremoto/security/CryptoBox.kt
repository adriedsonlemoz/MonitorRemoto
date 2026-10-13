package br.dev.monitorremoto.security

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Protocolo v1: AES-256-GCM com nonce aleatório de 96 bits e AAD fixo. */
object CryptoBox {
    private val rng = SecureRandom()
    private val aad = "monitor-remoto/protocolo-v1".toByteArray(Charsets.UTF_8)
    private const val TAG_BITS = 128

    data class Packet(val nonce: String, val ciphertext: String)

    fun newPairingKey(): ByteArray = ByteArray(32).also(rng::nextBytes)

    fun encode(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    fun decodeKey(value: String): ByteArray {
        val key = Base64.getUrlDecoder().decode(value.trim())
        require(key.size == 32) { "A chave deve conter 32 bytes (base64url)." }
        return key
    }

    fun seal(key: ByteArray, plaintext: String): Packet {
        require(key.size == 32)
        val nonce = ByteArray(12).also(rng::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(aad)
        val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return Packet(encode(nonce), encode(encrypted))
    }

    fun open(key: ByteArray, packet: Packet): String {
        require(key.size == 32)
        val nonce = Base64.getUrlDecoder().decode(packet.nonce)
        val ciphertext = Base64.getUrlDecoder().decode(packet.ciphertext)
        require(nonce.size == 12 && ciphertext.size >= 16) { "Pacote inválido" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(aad)
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }
}
