package br.dev.monitorremoto.transport

import android.content.Context
import android.os.Build
import br.dev.monitorremoto.core.SettingsStore
import br.dev.monitorremoto.security.CryptoBox
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Socket

object RemoteClient {
    /** Executar somente fora da thread principal. */
    fun snapshot(host: String, key: ByteArray, context: Context, method: String = "Chave salva", includeSms: Boolean = false): JSONObject {
        require(host.isNotBlank() && host.length <= 253)
        require(!host.contains('/') && !host.contains(':') && !host.contains(' ')) {
            "Informe apenas o IP IPv4 ou hostname (sem http:// ou porta)."
        }
        val request = JSONObject()
            .put("v", WireProtocol.VERSION)
            .put("op", "snapshot")
            .put("includeSms", includeSms)
            .put("appVersion", AppVersion.NAME)
            .put("ts", System.currentTimeMillis())
            .put("clientId", SettingsStore.controllerId(context))
            .put("clientName", listOf(Build.MANUFACTURER, Build.MODEL).joinToString(" ").take(72))
            .put("accessMethod", method)
        return Socket().use { socket ->
            socket.connect(InetSocketAddress(host, WireProtocol.PORT), 4500)
            socket.soTimeout = 6500
            WireProtocol.send(socket.getOutputStream(), WireProtocol.encode(key, request))
            val answer = WireProtocol.receive(socket.getInputStream(), 128 * 1024)
            WireProtocol.decode(key, answer).also {
                if (it.optString("op") == "versionMismatch") {
                    throw IllegalStateException(AppVersion.incompatibility(it.optString("appVersion")))
                }
                check(it.getInt("v") == WireProtocol.VERSION) { "Protocolo incompatível." }
                check(AppVersion.compatible(it.optString("appVersion"))) {
                    AppVersion.incompatibility(it.optString("appVersion"))
                }
                check(it.optJSONObject("battery") != null) { "Resposta remota sem dados válidos." }
            }
        }
    }

    /** Aviso explícito: encerra apenas o registro deste controlador no servidor. */
    fun disconnect(host: String, key: ByteArray, context: Context) {
        require(host.isNotBlank() && host.length <= 253 &&
            !host.contains('/') && !host.contains(':') && !host.contains(' '))
        val request = JSONObject()
            .put("v", WireProtocol.VERSION)
            .put("op", "disconnect")
            .put("appVersion", AppVersion.NAME)
            .put("ts", System.currentTimeMillis())
            .put("clientId", SettingsStore.controllerId(context))
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, WireProtocol.PORT), 3500)
            socket.soTimeout = 3500
            WireProtocol.send(socket.getOutputStream(), WireProtocol.encode(key, request))
            val reply = WireProtocol.decode(key, WireProtocol.receive(socket.getInputStream(), 16 * 1024))
            check(reply.optString("op") == "disconnectOk" && AppVersion.compatible(reply.optString("appVersion")))
        }
    }

    /** Uma instrução iniciada pelo usuário, protegida pela mesma sessão autenticada do snapshot. */
    fun messageCommand(host: String, key: ByteArray, context: Context, method: String,
        operation: String, destinationOrToken: String, text: String): String {
        require(operation in setOf("notificationReply", "smsReply"))
        require(host.isNotBlank() && host.length <= 253 && !host.contains('/') && !host.contains(':') && !host.contains(' '))
        require(text.isNotBlank() && text.length <= 500)
        val request = JSONObject().put("v", WireProtocol.VERSION)
            .put("op", operation).put("appVersion", AppVersion.NAME)
            .put("ts", System.currentTimeMillis())
            .put("clientId", SettingsStore.controllerId(context))
            .put("clientName", listOf(Build.MANUFACTURER, Build.MODEL).joinToString(" ").take(72))
            .put("accessMethod", method)
            .put("body", text)
        if (operation == "notificationReply") request.put("replyToken", destinationOrToken)
        else request.put("destination", destinationOrToken)
        return Socket().use { socket ->
            socket.connect(InetSocketAddress(host, WireProtocol.PORT), 4500)
            socket.soTimeout = 6500
            WireProtocol.send(socket.getOutputStream(), WireProtocol.encode(key, request))
            val response = WireProtocol.decode(key, WireProtocol.receive(socket.getInputStream(), 16 * 1024))
            if (response.optString("op") == "versionMismatch")
                error(AppVersion.incompatibility(response.optString("appVersion")))
            check(response.optString("op") == "commandResult" && response.optInt("v") == WireProtocol.VERSION &&
                AppVersion.compatible(response.optString("appVersion"))) { "Resposta inválida do monitorado." }
            check(response.optBoolean("ok")) { response.optString("message", "Operação recusada.") }
            response.optString("message")
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
                .put("appVersion", AppVersion.NAME)
                .put("ts", System.currentTimeMillis())
                .put("session", code.session)
            WireProtocol.send(socket.getOutputStream(), WireProtocol.encode(code.ephemeralKey, request))
            val response = WireProtocol.decode(
                code.ephemeralKey, WireProtocol.receive(socket.getInputStream(), 16 * 1024)
            )
            check(AppVersion.compatible(response.optString("appVersion"))) {
                AppVersion.incompatibility(response.optString("appVersion"))
            }
            check(response.optInt("v") == WireProtocol.VERSION &&
                response.optString("op") == "pairOk" &&
                response.optString("session") == code.session) { "Resposta de pareamento inválida." }
            CryptoBox.decodeKey(response.getString("key"))
        }
    }
}
