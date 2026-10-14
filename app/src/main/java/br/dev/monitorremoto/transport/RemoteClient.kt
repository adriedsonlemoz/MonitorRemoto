package br.dev.monitorremoto.transport

import android.content.Context
import android.os.Build
import android.util.Base64
import br.dev.monitorremoto.core.SettingsStore
import br.dev.monitorremoto.core.RemoteGesturePolicy
import br.dev.monitorremoto.core.ScreenCapturePolicy
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
        operation: String, destinationOrToken: String, text: String, subscriptionId: Int = -1): String {
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
        else {
            request.put("destination", destinationOrToken)
            request.put("subscriptionId", subscriptionId)
        }
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

    /** Quadro JPEG de sessão consentida. Nunca armazena frame nem credencial fora da memória. */
    fun screenFrame(host: String, key: ByteArray, context: Context, method: String): JSONObject {
        require(host.isNotBlank() && host.length <= 253 && !host.contains('/') &&
            !host.contains(':') && !host.contains(' '))
        val request = JSONObject().put("v", WireProtocol.VERSION)
            .put("op", "screenFrame").put("appVersion", AppVersion.NAME)
            .put("ts", System.currentTimeMillis())
            .put("clientId", SettingsStore.controllerId(context))
            .put("clientName", listOf(Build.MANUFACTURER, Build.MODEL).joinToString(" ").take(72))
            .put("accessMethod", method)
        return Socket().use { socket ->
            socket.connect(InetSocketAddress(host, WireProtocol.PORT), 4500)
            socket.soTimeout = 6500
            WireProtocol.send(socket.getOutputStream(), WireProtocol.encode(key, request))
            val result = WireProtocol.decode(key, WireProtocol.receive(socket.getInputStream(), 300 * 1024))
            if (result.optString("op") == "versionMismatch")
                error(AppVersion.incompatibility(result.optString("appVersion")))
            check(result.optString("op") == "screenFrame" &&
                result.optInt("v") == WireProtocol.VERSION &&
                AppVersion.compatible(result.optString("appVersion"))) { "Resposta de tela inválida." }
            result
        }
    }

    /** Foto única, mais nítida que a prévia. Somente com projeção local autorizada. */
    fun screenCapture(host: String, key: ByteArray, context: Context, method: String): ByteArray {
        require(host.isNotBlank() && host.length <= 253 && !host.contains('/') &&
            !host.contains(':') && !host.contains(' '))
        val request = JSONObject().put("v", WireProtocol.VERSION)
            .put("op", "screenCapture").put("appVersion", AppVersion.NAME)
            .put("ts", System.currentTimeMillis())
            .put("clientId", SettingsStore.controllerId(context))
            .put("clientName", listOf(Build.MANUFACTURER, Build.MODEL).joinToString(" ").take(72))
            .put("accessMethod", method)
        return Socket().use { socket ->
            socket.connect(InetSocketAddress(host, WireProtocol.PORT), 4500)
            socket.soTimeout = 6500
            WireProtocol.send(socket.getOutputStream(), WireProtocol.encode(key, request))
            val result = WireProtocol.decode(key, WireProtocol.receive(socket.getInputStream(), 1_250_000))
            if (result.optString("op") == "versionMismatch")
                error(AppVersion.incompatibility(result.optString("appVersion")))
            check(result.optString("op") == "screenCapture" && result.optInt("v") == WireProtocol.VERSION &&
                AppVersion.compatible(result.optString("appVersion"))) { "Resposta de captura inválida" }
            check(result.optBoolean("ok")) { "Captura indisponível ou sessão interrompida." }
            val encoded = result.optString("jpeg")
            require(encoded.length in 4..650_000) { "Captura acima do limite." }
            Base64.decode(encoded, Base64.NO_WRAP).also { bytes ->
                require(ScreenCapturePolicy.validJpeg(bytes)) {
                    "Imagem JPEG inválida."
                }
            }
        }
    }

    /** Comando de toque solicitado diretamente pelo controlador, no canal autenticado. */
    fun touch(host: String, key: ByteArray, context: Context, method: String,
        action: String, x: Float = 0.5f, y: Float = 0.5f,
        endX: Float = x, endY: Float = y, duration: Long = 240L): Boolean {
        require(RemoteGesturePolicy.valid(action, x, y, endX, endY, duration))
        return remoteScreenCommand(host, key, context, method, "remoteTouch") {
            put("gesture", action).put("x", x.toDouble()).put("y", y.toDouble())
                .put("endX", endX.toDouble()).put("endY", endY.toDouble())
                .put("duration", duration)
        }.optBoolean("ok")
    }

    fun requestScreen(host: String, key: ByteArray, context: Context, method: String): Boolean =
        remoteScreenCommand(host, key, context, method, "requestScreen") { }.optBoolean("ok")

    /** PCM mono efêmero, sem áudio de chamadas ou microfone. */
    fun audioChunk(host: String, key: ByteArray, context: Context, method: String): JSONObject =
        remoteScreenCommand(host, key, context, method, "audioChunk") { }

    private fun remoteScreenCommand(host: String, key: ByteArray, context: Context,
        method: String, op: String, extras: JSONObject.() -> Unit): JSONObject {
        require(host.isNotBlank() && host.length <= 253 && !host.contains('/') &&
            !host.contains(':') && !host.contains(' '))
        val request = JSONObject().put("v", WireProtocol.VERSION).put("op", op)
            .put("appVersion", AppVersion.NAME).put("ts", System.currentTimeMillis())
            .put("clientId", SettingsStore.controllerId(context))
            .put("clientName", listOf(Build.MANUFACTURER, Build.MODEL).joinToString(" ").take(72))
            .put("accessMethod", method).apply(extras)
        return Socket().use { socket ->
            // Navegação não deve ficar segundos esperando um TCP perdido.
            socket.connect(InetSocketAddress(host, WireProtocol.PORT), if (op == "remoteTouch") 2200 else 4500)
            socket.soTimeout = if (op == "remoteTouch") 3200 else 6500
            WireProtocol.send(socket.getOutputStream(), WireProtocol.encode(key, request))
            val response = WireProtocol.decode(key, WireProtocol.receive(socket.getInputStream(), 64 * 1024))
            if (response.optString("op") == "versionMismatch")
                error(AppVersion.incompatibility(response.optString("appVersion")))
            check(response.optString("op") == op && response.optInt("v") == WireProtocol.VERSION &&
                AppVersion.compatible(response.optString("appVersion"))) { "Resposta remota inválida" }
            response
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
