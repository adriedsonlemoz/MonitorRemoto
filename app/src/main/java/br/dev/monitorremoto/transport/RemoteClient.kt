package br.dev.monitorremoto.transport

import br.dev.monitorremoto.security.CryptoBox
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Socket

object RemoteClient {
    /** Executar somente fora da thread principal. */
    fun snapshot(host: String, key: ByteArray): JSONObject {
        require(host.isNotBlank() && host.length <= 253)
        require(!host.contains('/') && !host.contains(':') && !host.contains(' ')) {
            "Informe apenas o IP IPv4 ou hostname (sem http:// ou porta)."
        }
        val request = JSONObject()
            .put("v", WireProtocol.VERSION)
            .put("op", "snapshot")
            .put("ts", System.currentTimeMillis())
        return Socket().use { socket ->
            socket.connect(InetSocketAddress(host, WireProtocol.PORT), 4500)
            socket.soTimeout = 6500
            WireProtocol.send(socket.getOutputStream(), WireProtocol.encode(key, request))
            val answer = WireProtocol.receive(socket.getInputStream(), 128 * 1024)
            WireProtocol.decode(key, answer).also { check(it.getInt("v") == WireProtocol.VERSION) }
        }
    }

    /** Pareamento único por QR: nunca gravar a credencial temporária, apenas a chave recebida. */
    fun pair(code: PairingCode.Offer): ByteArray {
        require(PairingCode.isPrivateIpv4(code.host))
        // Reconstrói e valida o esquema completo inclusive porta, versão e sessão.
        PairingCode.decode(PairingCode.encode(code))
        return Socket().use { socket ->
            socket.connect(InetSocketAddress(code.host, WireProtocol.PORT), 4500)
            socket.soTimeout = 6500
            val request = JSONObject()
                .put("v", WireProtocol.VERSION)
                .put("op", "pair")
                .put("ts", System.currentTimeMillis())
                .put("session", code.session)
            WireProtocol.send(socket.getOutputStream(), WireProtocol.encode(code.ephemeralKey, request))
            val response = WireProtocol.decode(
                code.ephemeralKey, WireProtocol.receive(socket.getInputStream(), 16 * 1024)
            )
            check(response.optInt("v") == WireProtocol.VERSION &&
                response.optString("op") == "pairOk" &&
                response.optString("session") == code.session) { "Resposta de pareamento inválida." }
            CryptoBox.decodeKey(response.getString("key"))
        }
    }
}
