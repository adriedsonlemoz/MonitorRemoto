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

    /** Comandos físicos restritos, cifrados e vinculados à versão e à chave já pareada. */
    fun deviceControl(host: String, key: ByteArray, context: Context, method: String,
        action: String, seconds: Int = 0, confirmation: String = ""): JSONObject {
        require(br.dev.monitorremoto.core.DeviceControlPolicy.valid(action, seconds, confirmation)) { "Comando recusado." }
        require(host.isNotBlank() && host.length <= 253 && !host.contains('/') &&
            !host.contains(':') && !host.contains(' ')) { "Host inválido." }
        val request = JSONObject().put("v", WireProtocol.VERSION).put("op", "deviceControl")
            .put("action", action).put("seconds", seconds).put("confirm", confirmation)
            .put("appVersion", AppVersion.NAME).put("ts", System.currentTimeMillis())
            .put("clientId", SettingsStore.controllerId(context))
            .put("clientName", listOf(Build.MANUFACTURER, Build.MODEL).joinToString(" ").take(72))
            .put("accessMethod", method)
        return Socket().use { socket ->
            socket.connect(InetSocketAddress(host, WireProtocol.PORT), 4500)
            socket.soTimeout = 6500
            WireProtocol.send(socket.getOutputStream(), WireProtocol.encode(key, request))
            val reply = WireProtocol.decode(key, WireProtocol.receive(socket.getInputStream(), 16 * 1024))
            if (reply.optString("op") == "versionMismatch")
                error(AppVersion.incompatibility(reply.optString("appVersion")))
            check(reply.optString("op") == "deviceControl" && reply.optInt("v") == WireProtocol.VERSION &&
                AppVersion.compatible(reply.optString("appVersion"))) { "Resposta remota inválida." }
            check(reply.optJSONObject("status") != null) { "Estado remoto ausente." }
            reply
        }
    }

    /** Consulta autorizada da lista de apps com lançador, em canal criptografado. */
    fun installedApps(host: String, key: ByteArray, context: Context, method: String): JSONObject {
        require(host.isNotBlank() && host.length <= 253 && !host.contains('/') && !host.contains(':') && !host.contains(' '))
        val request = JSONObject().put("v", WireProtocol.VERSION).put("op", "appsList")
            .put("appVersion", AppVersion.NAME).put("ts", System.currentTimeMillis())
            .put("clientId", SettingsStore.controllerId(context))
            .put("clientName", listOf(Build.MANUFACTURER, Build.MODEL).joinToString(" ").take(72))
            .put("accessMethod", method)
        return Socket().use { socket ->
            socket.connect(InetSocketAddress(host, WireProtocol.PORT), 4500)
            socket.soTimeout = 9000
            WireProtocol.send(socket.getOutputStream(), WireProtocol.encode(key, request))
            val result = WireProtocol.decode(key, WireProtocol.receive(socket.getInputStream(), 100 * 1024))
            if (result.optString("op") == "versionMismatch") error(AppVersion.incompatibility(result.optString("appVersion")))
            check(result.optString("op") == "appsList" && result.optInt("v") == WireProtocol.VERSION &&
                AppVersion.compatible(result.optString("appVersion"))) { "Resposta de aplicativos inválida." }
            check(result.optBoolean("ok")) { result.optString("message", "Acesso não autorizado no monitorado.") }
            result
        }
    }

    /** Lista somente a árvore SAF autorizada localmente no telefone monitorado. */
    fun dataList(host: String, key: ByteArray, context: Context, method: String,
        path: List<String>): JSONObject = dataRequest(host, key, context, method, "dataList", path, 0L, 0L, 0L)

    fun dataChunk(host: String, key: ByteArray, context: Context, method: String,
        path: List<String>, size: Long, modified: Long, offset: Long): ByteArray {
        val count = br.dev.monitorremoto.core.RemoteFilePolicy.nextChunkLength(size, offset)
        require(count > 0) { "Bloco de arquivo inválido." }
        val response = dataRequest(host, key, context, method, "dataChunk", path, size, modified, offset)
        check(response.optLong("offset", -1L) == offset && response.optLong("size", -1L) == size &&
            response.optLong("modified", -1L) == modified) { "Arquivo de origem mudou durante a cópia." }
        val data = Base64.decode(response.getString("bytes"), Base64.NO_WRAP)
        check(data.size == count) { "Bloco incompleto." }
        val hash = java.security.MessageDigest.getInstance("SHA-256").digest(data)
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        check(hash == response.optString("sha256")) { "Falha de integridade do arquivo." }
        return data
    }

    /** Até dois blocos autenticados por requisição, reduz conexões sem bufferizar o arquivo todo. */
    fun dataChunks(host: String, key: ByteArray, context: Context, method: String,
        path: List<String>, size: Long, modified: Long, offset: Long): List<ByteArray> {
        require(br.dev.monitorremoto.core.RemoteFilePolicy.validChunk(size, offset))
        val response = dataRequest(host, key, context, method, "dataChunks", path, size, modified, offset)
        check(response.optLong("size", -1) == size && response.optLong("modified", -1) == modified &&
            response.optLong("offset", -1) == offset) { "Metadados mudaram durante a cópia." }
        val blocks = response.optJSONArray("chunks") ?: error("Blocos ausentes.")
        val count = if (offset + br.dev.monitorremoto.core.RemoteFilePolicy.CHUNK_BYTES < size) 2 else 1
        check(blocks.length() == count) { "Quantidade de blocos inesperada." }
        return (0 until count).map { index ->
            val at = offset + index * br.dev.monitorremoto.core.RemoteFilePolicy.CHUNK_BYTES.toLong()
            val item = blocks.getJSONObject(index)
            check(item.optLong("offset", -1L) == at) { "Blocos fora de ordem." }
            val bytes = Base64.decode(item.getString("bytes"), Base64.NO_WRAP)
            check(bytes.size == br.dev.monitorremoto.core.RemoteFilePolicy.nextChunkLength(size, at)) {
                "Bloco de tamanho incorreto."
            }
            val digest = java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it.toInt() and 255) }
            check(item.optString("sha256") == digest) { "Falha de integridade do bloco." }
            bytes
        }
    }

    /** Verifica o digest completo na origem após todos os blocos (proteção contra alterações silenciosas). */
    fun dataHash(host: String, key: ByteArray, context: Context, method: String,
        path: List<String>, size: Long, modified: Long): String {
        val response = dataRequest(host, key, context, method, "dataHash", path, size, modified, 0L)
        check(response.optLong("size", -1) == size && response.optLong("modified", -1) == modified) {
            "A origem mudou antes da conferência."
        }
        return response.optString("sha256").also { hash ->
            check(hash.length == 64 && hash.all { it in '0'..'9' || it in 'a'..'f' }) {
                "Hash final inválido."
            }
        }
    }

    private fun dataRequest(host: String, key: ByteArray, context: Context, method: String,
        op: String, path: List<String>, size: Long, modified: Long, offset: Long): JSONObject {
        require(host.isNotBlank() && host.length <= 253 && !host.contains('/') &&
            !host.contains(':') && !host.contains(' '))
        require(br.dev.monitorremoto.core.RemoteFilePolicy.validPath(path)) { "Caminho inválido." }
        val parts = org.json.JSONArray()
        path.forEach { parts.put(it) }
        val request = JSONObject().put("v", WireProtocol.VERSION).put("op", op)
            .put("path", parts).put("appVersion", AppVersion.NAME)
            .put("ts", System.currentTimeMillis())
            .put("clientId", SettingsStore.controllerId(context))
            .put("clientName", listOf(Build.MANUFACTURER, Build.MODEL).joinToString(" ").take(72))
            .put("accessMethod", method)
        if (op in setOf("dataChunk", "dataChunks", "dataHash")) request.put("size", size).put("modified", modified).put("offset", offset)
        return Socket().use { socket ->
            socket.connect(InetSocketAddress(host, WireProtocol.PORT), 4500)
            socket.soTimeout = 10_000
            WireProtocol.send(socket.getOutputStream(), WireProtocol.encode(key, request))
            val result = WireProtocol.decode(key, WireProtocol.receive(socket.getInputStream(), 180 * 1024))
            if (result.optString("op") == "versionMismatch") error(AppVersion.incompatibility(result.optString("appVersion")))
            check(result.optString("op") == op && result.optInt("v") == WireProtocol.VERSION &&
                AppVersion.compatible(result.optString("appVersion"))) { "Resposta de arquivos inválida." }
            check(result.optBoolean("ok")) { result.optString("message", "Acesso aos arquivos recusado no monitorado.") }
            result
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

    /** Somente lê quadros de uma sessão Camera2 iniciada LOCALMENTE no monitorado. */
    fun cameraFrame(host: String, key: ByteArray, context: Context, method: String, afterSequence: Long = 0L): JSONObject {
        require(host.isNotBlank() && host.length <= 253 && !host.contains('/') &&
            !host.contains(':') && !host.contains(' ')) { "Host inválido." }
        require(afterSequence >= 0L) { "Sequência inválida." }
        val request = JSONObject().put("v", WireProtocol.VERSION).put("op", "cameraFrame")
            .put("afterSequence", afterSequence)
            .put("appVersion", AppVersion.NAME).put("ts", System.currentTimeMillis())
            .put("clientId", SettingsStore.controllerId(context))
            .put("clientName", listOf(Build.MANUFACTURER, Build.MODEL).joinToString(" ").take(72))
            .put("accessMethod", method)
        return Socket().use { socket ->
            socket.connect(InetSocketAddress(host, WireProtocol.PORT), 4500)
            socket.soTimeout = 6500
            WireProtocol.send(socket.getOutputStream(), WireProtocol.encode(key, request))
            val reply = WireProtocol.decode(key, WireProtocol.receive(socket.getInputStream(), 300 * 1024))
            if (reply.optString("op") == "versionMismatch")
                error(AppVersion.incompatibility(reply.optString("appVersion")))
            check(reply.optString("op") == "cameraFrame" && reply.optInt("v") == WireProtocol.VERSION &&
                AppVersion.compatible(reply.optString("appVersion"))) { "Resposta de câmera inválida." }
            reply
        }
    }

    /** Pede somente troca de lente com opt-in local ativo, não inicia captura. */
    fun cameraSwitch(host: String, key: ByteArray, context: Context, method: String,
        expectedSession: Long, targetLens: String) {
        require(host.isNotBlank() && host.length <= 253 && !host.contains('/') &&
            !host.contains(':') && !host.contains(' ')) { "Host inválido." }
        require(expectedSession > 0L && br.dev.monitorremoto.core.CameraRemoteSwitchPolicy.validLens(targetLens))
        val request = JSONObject().put("v", WireProtocol.VERSION).put("op", "cameraSwitch")
            .put("sessionToken", expectedSession).put("lens", targetLens)
            .put("appVersion", AppVersion.NAME).put("ts", System.currentTimeMillis())
            .put("clientId", SettingsStore.controllerId(context))
            .put("clientName", listOf(Build.MANUFACTURER, Build.MODEL).joinToString(" ").take(72))
            .put("accessMethod", method)
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, WireProtocol.PORT), 4500)
            socket.soTimeout = 6500
            WireProtocol.send(socket.getOutputStream(), WireProtocol.encode(key, request))
            val reply = WireProtocol.decode(key, WireProtocol.receive(socket.getInputStream(), 16 * 1024))
            if (reply.optString("op") == "versionMismatch")
                error(AppVersion.incompatibility(reply.optString("appVersion")))
            check(reply.optString("op") == "cameraSwitch" && reply.optInt("v") == WireProtocol.VERSION &&
                AppVersion.compatible(reply.optString("appVersion"))) { "Resposta de troca de câmera inválida." }
            check(reply.optBoolean("accepted")) { reply.optString("message", "Troca não permitida no monitorado.") }
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

    /** AVC autenticado, sequencial e limitado em tamanho, sem nova porta nem token. */
    fun videoFrame(host: String, key: ByteArray, context: Context, method: String, after: Long): JSONObject {
        require(after >= 0L)
        val request = JSONObject().put("v", WireProtocol.VERSION)
            .put("op", "videoFrame").put("after", after)
            .put("appVersion", AppVersion.NAME).put("ts", System.currentTimeMillis())
            .put("clientId", SettingsStore.controllerId(context))
            .put("clientName", listOf(Build.MANUFACTURER, Build.MODEL).joinToString(" ").take(72))
            .put("accessMethod", method)
        return Socket().use { socket ->
            socket.connect(InetSocketAddress(host, WireProtocol.PORT), 4500)
            socket.soTimeout = 6000
            WireProtocol.send(socket.getOutputStream(), WireProtocol.encode(key, request))
            val response = WireProtocol.decode(key, WireProtocol.receive(socket.getInputStream(), 430_000))
            if (response.optString("op") == "versionMismatch") error(AppVersion.incompatibility(response.optString("appVersion")))
            check(response.optString("op") == "videoFrame" && response.optInt("v") == WireProtocol.VERSION &&
                AppVersion.compatible(response.optString("appVersion"))) { "Resposta AVC inválida" }
            response
        }
    }

    /** Solicita somente acender tela com prévia consentida e opt-in local, nunca desbloquear. */
    fun wakeScreen(host: String, key: ByteArray, context: Context, method: String): String =
        remoteScreenCommand(host, key, context, method, "wakeScreen") { }
            .optString("message", "Sem confirmação do Android")

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
