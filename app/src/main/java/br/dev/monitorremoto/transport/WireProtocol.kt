package br.dev.monitorremoto.transport

import br.dev.monitorremoto.security.CryptoBox
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets

/** Envelope JSON delimitado por LF; payload é cifrado e autenticado. */
object WireProtocol {
    const val PORT = 37841
    const val VERSION = 1

    fun encode(key: ByteArray, message: JSONObject): ByteArray {
        val packet = CryptoBox.seal(key, message.toString())
        return (JSONObject().put("n", packet.nonce).put("c", packet.ciphertext).toString() + "\n")
            .toByteArray(StandardCharsets.UTF_8)
    }

    fun decode(key: ByteArray, packet: ByteArray): JSONObject {
        val envelope = JSONObject(String(packet, StandardCharsets.UTF_8))
        return JSONObject(CryptoBox.open(
            key, CryptoBox.Packet(envelope.getString("n"), envelope.getString("c"))
        ))
    }

    fun receive(input: InputStream, maxBytes: Int): ByteArray {
        val data = java.io.ByteArrayOutputStream()
        while (data.size() < maxBytes) {
            val byte = input.read()
            if (byte == -1) throw java.io.EOFException("Conexão interrompida")
            if (byte == 10) return data.toByteArray()
            data.write(byte)
        }
        throw IllegalArgumentException("Mensagem acima do limite permitido")
    }

    fun send(out: OutputStream, bytes: ByteArray) {
        out.write(bytes)
        out.flush()
    }
}
