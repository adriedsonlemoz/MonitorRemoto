package br.dev.monitorremoto.transport

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Quadros binários autenticados para downloads SAF (alpha.81).
 * O pedido e o ACK continuam no protocolo JSON autenticado. Depois do ACK,
 * cada registro é: tamanho int32, nonce aleatório de 12 bytes, ciphertext AES-GCM + tag.
 * AAD inclui desafio fresco do pedido, prevenindo replay entre transferências.
 */
internal object FastFileStream {
    const val MAX_DATA_BYTES = 192 * 1024
    private const val NONCE_BYTES = 12
    private const val TAG_BYTES = 16
    private const val HEADER_BYTES = 9 // tipo + offset de 64 bits
    private const val MAX_RECORD = NONCE_BYTES + HEADER_BYTES + MAX_DATA_BYTES + TAG_BYTES
    const val DATA: Byte = 1
    const val DONE: Byte = 2
    const val ERROR: Byte = 3
    /** Mensagem cifrada de progresso durante a releitura do prefixo remoto. */
    const val PROGRESS: Byte = 4
    private val random = SecureRandom()
    private val domain = "monitor-remoto/file-stream-alpha81".toByteArray(Charsets.UTF_8)

    data class Frame(val type: Byte, val offset: Long, val payload: ByteArray)

    fun freshChallenge(): String = ByteArray(16).also(random::nextBytes).let {
        Base64.getUrlEncoder().withoutPadding().encodeToString(it)
    }

    fun challengeBytes(challenge: String): ByteArray {
        require(challenge.length in 20..24 && challenge.all { it.isLetterOrDigit() || it == '-' || it == '_' }) {
            "Desafio de transferência inválido."
        }
        return Base64.getUrlDecoder().decode(challenge).also { require(it.size == 16) }
    }

    private fun cipher(mode: Int, key: ByteArray, nonce: ByteArray, challenge: ByteArray): Cipher {
        require(key.size == 32 && nonce.size == NONCE_BYTES && challenge.size == 16)
        return Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
            updateAAD(domain)
            updateAAD(challenge)
        }
    }

    fun send(output: OutputStream, key: ByteArray, challenge: ByteArray,
        type: Byte, offset: Long, payload: ByteArray, count: Int = payload.size) {
        require(type in DATA..PROGRESS && offset >= 0 && count in 0..MAX_DATA_BYTES && count <= payload.size)
        require((type == DATA && count > 0) || (type == DONE && count == 32) ||
            (type == ERROR && count in 1..512) || (type == PROGRESS && count == 0))
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val plaintext = ByteArray(HEADER_BYTES + count)
        plaintext[0] = type
        for (i in 0..7) plaintext[i + 1] = (offset ushr (56 - 8 * i)).toByte()
        System.arraycopy(payload, 0, plaintext, HEADER_BYTES, count)
        val sealed = cipher(Cipher.ENCRYPT_MODE, key, nonce, challenge).doFinal(plaintext)
        DataOutputStream(output).apply {
            writeInt(nonce.size + sealed.size)
            write(nonce)
            write(sealed)
        }
    }

    fun receive(input: InputStream, key: ByteArray, challenge: ByteArray): Frame {
        val source = DataInputStream(input)
        val size = source.readInt()
        require(size in (NONCE_BYTES + HEADER_BYTES + TAG_BYTES)..MAX_RECORD) {
            "Quadro de transferência fora do limite."
        }
        val nonce = ByteArray(NONCE_BYTES).also(source::readFully)
        val encrypted = ByteArray(size - NONCE_BYTES).also(source::readFully)
        val clear = cipher(Cipher.DECRYPT_MODE, key, nonce, challenge).doFinal(encrypted)
        require(clear.size >= HEADER_BYTES)
        val type = clear[0]
        var offset = 0L
        for (i in 1..8) offset = (offset shl 8) or (clear[i].toLong() and 0xffL)
        val payload = clear.copyOfRange(HEADER_BYTES, clear.size)
        check(offset >= 0 && when (type) {
            DATA -> payload.isNotEmpty() && payload.size <= MAX_DATA_BYTES
            DONE -> payload.size == 32
            ERROR -> payload.size in 1..512
            PROGRESS -> payload.isEmpty()
            else -> false
        }) { "Quadro remoto inválido." }
        return Frame(type, offset, payload)
    }

    /** ACK pequeno é lido sem buffer que antecipe o início dos quadros binários. */
    fun readAck(input: InputStream, maxBytes: Int = 16 * 1024): ByteArray {
        val data = java.io.ByteArrayOutputStream()
        repeat(maxBytes) {
            val next = input.read()
            if (next == -1) throw java.io.EOFException("ACK da transferência interrompido.")
            if (next == 10) return data.toByteArray()
            data.write(next)
        }
        error("ACK maior que o permitido.")
    }
}
