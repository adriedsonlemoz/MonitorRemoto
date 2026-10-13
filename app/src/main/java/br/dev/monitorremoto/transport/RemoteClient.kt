package br.dev.monitorremoto.transport

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
}
