package br.dev.monitorremoto.transport

import br.dev.monitorremoto.service.MonitorService

import android.content.Context
import br.dev.monitorremoto.core.BatteryReader
import br.dev.monitorremoto.core.NetworkReader
import br.dev.monitorremoto.core.DeviceInfoReader
import br.dev.monitorremoto.core.TelephonyReader
import br.dev.monitorremoto.core.EventJournal
import br.dev.monitorremoto.core.SmsInboxReader
import br.dev.monitorremoto.core.SmsReplySender
import br.dev.monitorremoto.core.SmsSubscriptions
import br.dev.monitorremoto.core.SmsLiveJournal
import br.dev.monitorremoto.core.SmsDeliveryJournal
import br.dev.monitorremoto.core.MmsMetadataReader
import br.dev.monitorremoto.core.SensitiveContent
import br.dev.monitorremoto.core.SharedDataFolder
import br.dev.monitorremoto.capture.NotificationReplyActions
import br.dev.monitorremoto.capture.NotificationCaptureStatus
import br.dev.monitorremoto.core.DeviceControlPolicy
import br.dev.monitorremoto.core.RemotePhysicalControls
import br.dev.monitorremoto.core.RemoteScreenLock
import br.dev.monitorremoto.core.InstalledAppsReader
import br.dev.monitorremoto.capture.ScreenFrameStore
import br.dev.monitorremoto.capture.AudioChunkStore
import br.dev.monitorremoto.capture.ScreenShareRequest
import br.dev.monitorremoto.capture.RemoteTouchService
import br.dev.monitorremoto.video.VideoFrameStore
import br.dev.monitorremoto.video.VideoStreamPolicy
import android.util.Base64
import android.app.KeyguardManager
import android.os.PowerManager
import br.dev.monitorremoto.core.SettingsStore
import br.dev.monitorremoto.security.CryptoBox
import br.dev.monitorremoto.security.SecretVault
import org.json.JSONObject
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.LinkedHashMap
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/** Porta única: consulta autenticada e pareamento QR temporário em canal AES-GCM. */
class MonitorServer(private val context: Context) {
    private var server: ServerSocket? = null
    @Volatile private var running = false
    @Volatile private var acceptThread: Thread? = null
    // Fila limitada evita retenção ilimitada de sockets em caso de tráfego abusivo na LAN.
    private val workers = ThreadPoolExecutor(
        3, 3, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue<Runnable>(12),
        ThreadPoolExecutor.AbortPolicy()
    )
    // Compartilhado entre instâncias: reiniciar o listener não reabre a janela de replay.
    private companion object {
        val seenNonces = LinkedHashMap<String, Long>()
        var lastWakeAt = 0L
    }

    @Synchronized fun start() {
        check(!running)
        // Abrir porta antes de informar que o serviço está pronto para QR.
        val listener = ServerSocket(WireProtocol.PORT)
        server = listener
        running = true
        val thread = Thread({
            try {
                listener.use {
                    while (running) {
                        val peer = listener.accept()
                        try {
                            workers.execute { handle(peer) }
                        } catch (_: Exception) { peer.close() }
                    }
                }
            } catch (_: SocketException) {
                // Fechamento normal ou porta indisponível.
            } catch (_: Exception) {
                // Bind ou erro de I/O: permitir tentativa posterior.
            } finally {
                running = false
                server = null
                acceptThread = null
            }
        }, "MonitorRemoto-server").apply { isDaemon = true }
        acceptThread = thread
        thread.start()
    }

    /** Verifica a thread real e a porta vinculada sem criar tráfego de rede. */
    fun isHealthy(): Boolean = running && server?.isBound == true &&
        server?.isClosed == false && acceptThread?.isAlive == true && !workers.isShutdown

    @Synchronized fun stop() {
        running = false
        PairingSession.revoke()
        try { server?.close() } catch (_: Exception) { }
        workers.shutdownNow()
    }

    private fun handle(peer: Socket) {
        peer.use { socket ->
            try {
                if (!SettingsStore.isMonitorEnabled(context)) return
                socket.soTimeout = 4500
                val permanentKey = SecretVault.load(context, SecretVault.MONITOR_KEY) ?: return
                val encrypted = WireProtocol.receive(socket.getInputStream(), 16 * 1024)
                val envelope = JSONObject(String(encrypted, Charsets.UTF_8))
                val nonce = envelope.getString("n")
                val now = System.currentTimeMillis()

                // Clientes já pareados usam somente a chave permanente.
                val regular = runCatching { WireProtocol.decode(permanentKey, encrypted) }.getOrNull()
                if (regular != null) {
                    if (regular.optInt("v") != WireProtocol.VERSION ||
                        regular.optString("op") !in setOf("snapshot", "disconnect", "notificationReply", "smsReply", "screenFrame", "screenCapture", "remoteTouch", "audioChunk", "requestScreen", "videoFrame", "wakeScreen", "dataList", "dataChunk", "dataChunks", "dataHash", "appsList", "deviceControl", "cameraFrame", "cameraSwitch")) return
                    if (abs(now - regular.optLong("ts")) > 5 * 60 * 1000L) return
                    synchronized(seenNonces) {
                        // Retenção compatível com ±5 minutos de validade do envelope.
                        // Polling H.264 não deve antecipar a expulsão de nonces ainda válidos.
                        val expiresAt = now - 5 * 60 * 1000L
                        val iterator = seenNonces.entries.iterator()
                        while (iterator.hasNext()) {
                            val entry = iterator.next()
                            if (entry.value < expiresAt) iterator.remove() else break
                        }
                        if (seenNonces.containsKey(nonce)) return
                        seenNonces[nonce] = now
                        while (seenNonces.size > 60_000) seenNonces.remove(seenNonces.keys.first())
                    }
                    // A chave válida não dispensa a exigência da mesma versão do app.
                    if (!AppVersion.compatible(regular.optString("appVersion"))) {
                        // Apenas pedidos autenticados recebem a versão real do servidor.
                        // Clientes sem chave válida continuam sem resposta para evitar sondagem.
                        val mismatch = JSONObject().put("v", WireProtocol.VERSION)
                            .put("op", "versionMismatch")
                            .put("appVersion", AppVersion.NAME)
                        WireProtocol.send(socket.getOutputStream(), WireProtocol.encode(permanentKey, mismatch))
                        return
                    }
                    if (regular.optString("op") in setOf("dataList", "dataChunk", "dataChunks", "dataHash")) {
                        val response = SharedDataFolder.handle(context, regular)
                        WireProtocol.send(socket.getOutputStream(), WireProtocol.encode(permanentKey, response))
                        AuthenticatedAccess.authenticated(socket.inetAddress?.hostAddress.orEmpty(),
                            regular.optString("clientId"), regular.optString("clientName"), regular.optString("accessMethod"))
                        return
                    }
                    if (regular.optString("op") == "requestScreen") {
                        val alerted = ScreenShareRequest.notifyLocal(context)
                        val reply = JSONObject().put("v", WireProtocol.VERSION)
                            .put("op", "requestScreen").put("appVersion", AppVersion.NAME)
                            .put("ok", alerted).put("sharing", ScreenFrameStore.sharing)
                        WireProtocol.send(socket.getOutputStream(), WireProtocol.encode(permanentKey, reply))
                        AuthenticatedAccess.authenticated(socket.inetAddress?.hostAddress.orEmpty(),
                            regular.optString("clientId"), regular.optString("clientName"), regular.optString("accessMethod"))
                        return
                    }
                    if (regular.optString("op") == "wakeScreen") {
                        // Nunca ignora bloqueio por senha. Opção separada local e controle de frequência.
                        val allowed = SettingsStore.allowRemoteWake(context) && SettingsStore.isMonitorEnabled(context)
                        val power = context.getSystemService(PowerManager::class.java)
                        var accepted = false
                        val message = when {
                            !allowed -> "Acender tela não está autorizado nas Configurações do monitorado."
                            power.isInteractive -> { accepted = true; "Tela já está acesa." }
                            else -> {
                                val nowWake = android.os.SystemClock.elapsedRealtime()
                                val canWake = synchronized(seenNonces) {
                                    if (VideoStreamPolicy.allowWake(nowWake, lastWakeAt)) {
                                        lastWakeAt = nowWake
                                        true
                                    } else false
                                }
                                if (!canWake) "Aguarde alguns segundos antes de tentar novamente."
                                else runCatching {
                                    @Suppress("DEPRECATION")
                                    val lock = power.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                                        PowerManager.ACQUIRE_CAUSES_WAKEUP, "MonitorRemoto:AcenderTela")
                                    lock.acquire(1_500L)
                                    accepted = true
                                    "Solicitação enviada. O Android pode não acender a tela; não desbloqueia."
                                }.getOrElse { "Android recusou ativação: ${it.javaClass.simpleName}" }
                            }
                        }
                        val response = JSONObject().put("v", WireProtocol.VERSION)
                            .put("op", "wakeScreen").put("appVersion", AppVersion.NAME)
                            .put("ok", accepted).put("message", message)
                            .put("interactive", power.isInteractive)
                            .put("deviceLocked", context.getSystemService(KeyguardManager::class.java).isDeviceLocked)
                        WireProtocol.send(socket.getOutputStream(), WireProtocol.encode(permanentKey, response))
                        AuthenticatedAccess.authenticated(socket.inetAddress?.hostAddress.orEmpty(),
                            regular.optString("clientId"), regular.optString("clientName"), regular.optString("accessMethod"))
                        return
                    }
                    if (regular.optString("op") == "videoFrame") {
                        // Somente sessão MediaProjection já consentida: nenhuma captura remota oculta.
                        ScreenFrameStore.viewerPing()
                        VideoFrameStore.viewerPing()
                        val after = regular.optLong("after", 0L).coerceAtLeast(0L)
                        if (after == 0L) VideoFrameStore.requestKeyFrame()
                        val frame = if (ScreenFrameStore.sharing) VideoFrameStore.next(after) else null
                        val locked = context.getSystemService(KeyguardManager::class.java).isDeviceLocked
                        val interactive = context.getSystemService(PowerManager::class.java).isInteractive
                        val response = JSONObject().put("v", WireProtocol.VERSION)
                            .put("op", "videoFrame").put("appVersion", AppVersion.NAME)
                            .put("sharing", ScreenFrameStore.sharing)
                            .put("supported", VideoFrameStore.supported)
                            .put("deviceLocked", locked).put("screenInteractive", interactive)
                            .put("touchReady", ScreenFrameStore.sharing && !locked &&
                                SettingsStore.allowRemoteTouch(context) && RemoteTouchService.isReady())
                            .put("audioReady", ScreenFrameStore.sharing && SettingsStore.captureInternalAudio(context) &&
                                AudioChunkStore.enabled)
                            .put("frameAgeMs", VideoFrameStore.latestAgeMs())
                        if (frame != null) {
                            response.put("sequence", frame.seq).put("width", frame.width)
                                .put("height", frame.height).put("pts", frame.timestampUs)
                                .put("keyFrame", frame.keyFrame)
                                .put("avc", Base64.encodeToString(frame.data, Base64.NO_WRAP))
                            if (frame.keyFrame) {
                                frame.sps?.let { response.put("sps", Base64.encodeToString(it, Base64.NO_WRAP)) }
                                frame.pps?.let { response.put("pps", Base64.encodeToString(it, Base64.NO_WRAP)) }
                            }
                        }
                        WireProtocol.send(socket.getOutputStream(), WireProtocol.encode(permanentKey, response))
                        AuthenticatedAccess.authenticated(socket.inetAddress?.hostAddress.orEmpty(),
                            regular.optString("clientId"), regular.optString("clientName"), regular.optString("accessMethod"))
                        return
                    }
                    if (regular.optString("op") == "remoteTouch") {
                        val gesture = regular.optString("gesture")
                        val allowed = SettingsStore.allowRemoteTouch(context) && ScreenFrameStore.sharing &&
                            !context.getSystemService(KeyguardManager::class.java).isDeviceLocked &&
                            RemoteTouchService.isReady()
                        val ok = allowed && RemoteTouchService.send(
                            regular.optDouble("x", -1.0).toFloat(), regular.optDouble("y", -1.0).toFloat(),
                            regular.optDouble("endX", -1.0).toFloat(), regular.optDouble("endY", -1.0).toFloat(),
                            regular.optLong("duration", 240L), gesture)
                        val reply = JSONObject().put("v", WireProtocol.VERSION)
                            .put("op", "remoteTouch").put("appVersion", AppVersion.NAME)
                            .put("ok", ok)
                            .put("message", if (ok) "Ação aceita pelo Android" else
                                "Controle indisponível: verifique Acessibilidade, tela desbloqueada e captura ativa")
                        WireProtocol.send(socket.getOutputStream(), WireProtocol.encode(permanentKey, reply))
                        AuthenticatedAccess.authenticated(socket.inetAddress?.hostAddress.orEmpty(),
                            regular.optString("clientId"), regular.optString("clientName"), regular.optString("accessMethod"))
                        return
                    }
                    if (regular.optString("op") == "audioChunk") {
                        val enabled = ScreenFrameStore.sharing && AudioChunkStore.enabled &&
                            SettingsStore.captureInternalAudio(context)
                        val chunk = if (enabled) AudioChunkStore.snapshot() else null
                        val reply = JSONObject().put("v", WireProtocol.VERSION)
                            .put("op", "audioChunk").put("appVersion", AppVersion.NAME)
                            .put("enabled", enabled).put("rate", AudioChunkStore.RATE)
                        if (chunk != null) reply.put("sequence", chunk.sequence)
                            .put("pcm", Base64.encodeToString(chunk.pcm, Base64.NO_WRAP))
                        WireProtocol.send(socket.getOutputStream(), WireProtocol.encode(permanentKey, reply))
                        AuthenticatedAccess.authenticated(socket.inetAddress?.hostAddress.orEmpty(),
                            regular.optString("clientId"), regular.optString("clientName"), regular.optString("accessMethod"))
                        return
                    }
                    if (regular.optString("op") == "screenCapture") {
                        // Uma imagem de melhor resolução SOMENTE com captura já autorizada.
                        // O servidor espera no máximo 2,3 s por um novo frame; nunca ativa projeção.
                        val id = ScreenFrameStore.requestStill()
                        var bytes: ByteArray? = null
                        if (id != null) {
                            val deadline = android.os.SystemClock.elapsedRealtime() + 2_300L
                            while (android.os.SystemClock.elapsedRealtime() < deadline &&
                                ScreenFrameStore.sharing && bytes == null) {
                                bytes = ScreenFrameStore.takeStill(id)
                                if (bytes == null) Thread.sleep(65L)
                            }
                            ScreenFrameStore.cancelStill(id)
                        }
                        val reply = JSONObject().put("v", WireProtocol.VERSION)
                            .put("op", "screenCapture").put("appVersion", AppVersion.NAME)
                            .put("ok", bytes != null).put("sharing", ScreenFrameStore.sharing)
                        if (bytes != null) reply.put("jpeg", Base64.encodeToString(bytes, Base64.NO_WRAP))
                        WireProtocol.send(socket.getOutputStream(), WireProtocol.encode(permanentKey, reply))
                        AuthenticatedAccess.authenticated(socket.inetAddress?.hostAddress.orEmpty(),
                            regular.optString("clientId"), regular.optString("clientName"), regular.optString("accessMethod"))
                        return
                    }
                    if (regular.optString("op") == "cameraFrame") {
                        // Somente consulta autenticada solicita a próxima codificação JPEG.
                        // Nunca inicia Camera2, que depende da Activity local visível.
                        val allowed = SettingsStore.isMonitorEnabled(context) && MonitorService.isRunning
                        val store = br.dev.monitorremoto.capture.CameraFrameStore
                        if (allowed) store.viewerPing()
                        val knownSequence = regular.optLong("afterSequence", 0L).coerceAtLeast(0L)
                        val status = if (allowed) store.status(knownSequence)
                            else br.dev.monitorremoto.capture.CameraFrameStore.Status(false, null, -1L, 0L, null)
                        val response = JSONObject().put("v", WireProtocol.VERSION)
                            .put("op", "cameraFrame").put("appVersion", AppVersion.NAME)
                            .put("active", status.active)
                            .put("lens", status.lens ?: JSONObject.NULL)
                            .put("frameAgeMs", status.ageMs)
                            .put("sequence", status.sequence)
                            .put("sessionToken", status.sessionToken)
                            .put("remoteSwitchAllowed", status.remoteSwitchAllowed)
                        if (status.jpeg != null) {
                            response.put("jpeg", Base64.encodeToString(status.jpeg, Base64.NO_WRAP))
                        }
                        WireProtocol.send(socket.getOutputStream(), WireProtocol.encode(permanentKey, response))
                        AuthenticatedAccess.authenticated(socket.inetAddress?.hostAddress.orEmpty(),
                            regular.optString("clientId"), regular.optString("clientName"), regular.optString("accessMethod"))
                        return
                    }
                    if (regular.optString("op") == "cameraSwitch") {
                        // Opt-in separado na tela CameraShareActivity, válido só enquanto ela está aberta.
                        // Este endpoint NÃO abre câmera e não confirma que a troca terminou.
                        val allowed = SettingsStore.isMonitorEnabled(context) && MonitorService.isRunning
                        val targetLens = regular.optString("lens", "")
                        val expectedSession = regular.optLong("sessionToken", 0L)
                        val accepted = allowed &&
                            br.dev.monitorremoto.capture.CameraFrameStore.requestRemoteSwitch(expectedSession, targetLens)
                        val response = JSONObject().put("v", WireProtocol.VERSION)
                            .put("op", "cameraSwitch").put("appVersion", AppVersion.NAME)
                            .put("accepted", accepted)
                            .put("message", if (accepted) "Solicitação recebida; aguardando confirmação visual da troca."
                                else "Troca indisponível: autorize na tela da câmera ou atualize a sessão.")
                        WireProtocol.send(socket.getOutputStream(), WireProtocol.encode(permanentKey, response))
                        AuthenticatedAccess.authenticated(socket.inetAddress?.hostAddress.orEmpty(),
                            regular.optString("clientId"), regular.optString("clientName"), regular.optString("accessMethod"))
                        return
                    }
                    if (regular.optString("op") == "screenFrame") {
                        // Um quadro temporário por requisição autenticada; nenhum frame antes da aprovação local.
                        ScreenFrameStore.viewerPing()
                        val frame = ScreenFrameStore.snapshot()
                        val deviceLocked = context.getSystemService(KeyguardManager::class.java).isDeviceLocked
                        val screenInteractive = context.getSystemService(PowerManager::class.java).isInteractive
                        val response = JSONObject().put("v", WireProtocol.VERSION)
                            .put("op", "screenFrame").put("appVersion", AppVersion.NAME)
                            .put("sharing", ScreenFrameStore.sharing)
                            .put("deviceLocked", deviceLocked)
                            .put("screenInteractive", screenInteractive)
                            .put("frameAgeMs", ScreenFrameStore.frameAgeMs())
                            .put("touchReady", ScreenFrameStore.sharing && !deviceLocked &&
                                SettingsStore.allowRemoteTouch(context) && RemoteTouchService.isReady())
                            .put("audioReady", ScreenFrameStore.sharing && SettingsStore.captureInternalAudio(context) &&
                                AudioChunkStore.enabled)
                        if (frame != null) {
                            response.put("sequence", frame.sequence)
                                .put("width", frame.width).put("height", frame.height)
                                .put("jpeg", Base64.encodeToString(frame.jpeg, Base64.NO_WRAP))
                        }
                        WireProtocol.send(socket.getOutputStream(), WireProtocol.encode(permanentKey, response))
                        AuthenticatedAccess.authenticated(socket.inetAddress?.hostAddress.orEmpty(),
                            regular.optString("clientId"), regular.optString("clientName"), regular.optString("accessMethod"))
                        return
                    }
                    if (regular.optString("op") == "deviceControl") {
                        val action = regular.optString("action")
                        val seconds = regular.optInt("seconds", 0)
                        val confirmation = regular.optString("confirm", "")
                        val command = runCatching {
                            check(DeviceControlPolicy.valid(action, seconds, confirmation)) { "Operação não permitida." }
                            check(DeviceControlPolicy.permitted(action,
                                SettingsStore.allowRemoteSound(context), SettingsStore.allowRemoteTorch(context),
                                SettingsStore.allowRemoteLock(context))) {
                                "Autorize esta função nas Configurações do monitorado."
                            }
                            when (action) {
                                "soundStart" -> RemotePhysicalControls.startSound(seconds)
                                "soundStop" -> RemotePhysicalControls.stopSound()
                                "torchOn" -> { RemotePhysicalControls.start(context); RemotePhysicalControls.setTorch(true) }
                                "torchOff" -> { RemotePhysicalControls.start(context); RemotePhysicalControls.setTorch(false) }
                                "lockNow" -> RemoteScreenLock.requestLock(context)
                                else -> "Estado atualizado."
                            }
                        }
                        val reply = JSONObject().put("v", WireProtocol.VERSION)
                            .put("op", "deviceControl").put("appVersion", AppVersion.NAME)
                            .put("ok", command.isSuccess)
                            .put("message", command.getOrNull() ?: command.exceptionOrNull()?.message?.take(170).orEmpty())
                            .put("status", RemotePhysicalControls.status(context))
                        WireProtocol.send(socket.getOutputStream(), WireProtocol.encode(permanentKey, reply))
                        AuthenticatedAccess.authenticated(socket.inetAddress?.hostAddress.orEmpty(),
                            regular.optString("clientId"), regular.optString("clientName"), regular.optString("accessMethod"))
                        return
                    }
                    if (regular.optString("op") == "appsList") {
                        val authorized = SettingsStore.isMonitorEnabled(context) && SettingsStore.shareInstalledApps(context)
                        val response = JSONObject().put("v", WireProtocol.VERSION)
                            .put("op", "appsList").put("appVersion", AppVersion.NAME)
                            .put("ok", authorized)
                            .put("message", if (authorized) "" else "Autorize Aplicativos instalados nas Configurações do monitorado.")
                            .put("items", if (authorized) InstalledAppsReader.launchable(context) else org.json.JSONArray())
                        WireProtocol.send(socket.getOutputStream(), WireProtocol.encode(permanentKey, response))
                        AuthenticatedAccess.authenticated(socket.inetAddress?.hostAddress.orEmpty(),
                            regular.optString("clientId"), regular.optString("clientName"), regular.optString("accessMethod"))
                        return
                    }
                    if (regular.optString("op") == "disconnect") {
                        val reply = JSONObject()
                            .put("v", WireProtocol.VERSION)
                            .put("op", "disconnectOk")
                            .put("appVersion", AppVersion.NAME)
                        WireProtocol.send(socket.getOutputStream(), WireProtocol.encode(permanentKey, reply))
                        AuthenticatedAccess.disconnected(socket.inetAddress?.hostAddress.orEmpty(), regular.optString("clientId"))
                        return
                    }
                    if (regular.optString("op") in setOf("notificationReply", "smsReply")) {
                        val operation = regular.getString("op")
                        val body = regular.optString("body")
                        val commandResult = runCatching {
                            check(body.isNotBlank() && body.length <= 500 &&
                                !SensitiveContent.isSensitive("", body)) { "Mensagem rejeitada pelo filtro de segurança." }
                            if (operation == "notificationReply") {
                                check(SettingsStore.allowNotificationReplies(context)) { "Respostas a notificações desativadas no monitorado." }
                                check(NotificationReplyActions.reply(context, regular.optString("replyToken"),
                                    body, SettingsStore.notificationAllowlist(context), SettingsStore.allNotifications(context))) { "Notificação expirada, removida ou sem ação de resposta." }
                                "Ação de resposta acionada. Entrega ao destinatário não confirmada."
                            } else {
                                SmsReplySender.send(context, regular.optString("destination"), body,
                                    regular.optInt("subscriptionId", -1))
                            }
                        }
                        val reply = JSONObject().put("v", WireProtocol.VERSION)
                            .put("op", "commandResult").put("appVersion", AppVersion.NAME)
                            .put("ok", commandResult.isSuccess)
                            .put("message", commandResult.getOrNull() ?: commandResult.exceptionOrNull()?.message?.take(160).orEmpty())
                        WireProtocol.send(socket.getOutputStream(), WireProtocol.encode(permanentKey, reply))
                        AuthenticatedAccess.authenticated(socket.inetAddress?.hostAddress.orEmpty(),
                            regular.optString("clientId"), regular.optString("clientName"), regular.optString("accessMethod"))
                        return
                    }
                    val response = JSONObject().apply {
                        put("v", WireProtocol.VERSION)
                        put("appVersion", AppVersion.NAME)
                        put("serverMs", now)
                        put("battery", BatteryReader.snapshot(context))
                        put("network", NetworkReader.snapshot(context))
                        put("deviceState", br.dev.monitorremoto.core.DeviceStateReader.snapshot(context))
                        put("deviceInfo", DeviceInfoReader.snapshot(context))
                        put("telephony", TelephonyReader.snapshot(context))
                        put("events", if (SettingsStore.isMonitorEnabled(context))
                            EventJournal.latestJson(SettingsStore.notificationAllowlist(context),
                                SettingsStore.allowNotificationReplies(context), SettingsStore.allNotifications(context))
                            else org.json.JSONArray())
                        put("smsInbox", if (regular.optBoolean("includeSms")) SmsInboxReader.snapshot(context) else org.json.JSONArray())
                        put("smsLive", if (regular.optBoolean("includeSms") && SettingsStore.shareSmsInbox(context) &&
                            SettingsStore.receiveSmsEvents(context) && SettingsStore.isMonitorEnabled(context)) SmsLiveJournal.snapshot() else org.json.JSONArray())
                        put("smsStatus", if (regular.optBoolean("includeSms") && SettingsStore.shareSmsInbox(context)) SmsDeliveryJournal.latest() else org.json.JSONArray())
                        put("mmsMetadata", if (regular.optBoolean("includeSms")) MmsMetadataReader.snapshot(context) else org.json.JSONArray())
                        put("smsSubscriptions", if (regular.optBoolean("includeSms") && SettingsStore.allowSmsReplies(context)) SmsSubscriptions.options(context) else org.json.JSONArray())
                        put("notificationStatus", JSONObject()
                            .put("monitorEnabled", SettingsStore.isMonitorEnabled(context))
                            .put("listenerGranted", runCatching {
                                android.provider.Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
                                    .orEmpty().split(':').any { entry ->
                                        android.content.ComponentName.unflattenFromString(entry) ==
                                            android.content.ComponentName(context, br.dev.monitorremoto.capture.NotificationCaptureService::class.java)
                                    }
                            }.getOrDefault(false))
                            .put("listenerConnected", NotificationCaptureStatus.connected)
                            .put("allowedCount", SettingsStore.notificationAllowlist(context).size)
                            .put("allApps", SettingsStore.allNotifications(context))
                            .put("lastEventAt", NotificationCaptureStatus.lastEventAt)
                            .put("recoveredActive", NotificationCaptureStatus.lastRecoveryCount))
                        put("messagingPermissions", JSONObject().put("smsRead", SettingsStore.shareSmsInbox(context) && SmsInboxReader.hasAccess(context))
                            .put("smsReply", SettingsStore.allowSmsReplies(context) && SmsInboxReader.hasAccess(context) &&
                                context.checkSelfPermission(android.Manifest.permission.SEND_SMS) == android.content.pm.PackageManager.PERMISSION_GRANTED)
                            .put("notificationReply", SettingsStore.allowNotificationReplies(context))
                            .put("allNotifications", SettingsStore.allNotifications(context))
                            .put("smsRealtime", SettingsStore.receiveSmsEvents(context) &&
                                context.checkSelfPermission(android.Manifest.permission.RECEIVE_SMS) == android.content.pm.PackageManager.PERMISSION_GRANTED)
                            .put("smsSimChoice", SmsSubscriptions.hasAccess(context)))
                        put("epoch", EventJournal.epoch)
                    }
                    WireProtocol.send(socket.getOutputStream(), WireProtocol.encode(permanentKey, response))
                    // Register ONLY after a valid authenticated request and successful response.
                    AuthenticatedAccess.authenticated(
                        socket.inetAddress?.hostAddress.orEmpty(),
                        regular.optString("clientId"),
                        regular.optString("clientName"),
                        regular.optString("accessMethod")
                    )
                    return
                }

                // Apenas uma credencial de QR ativa, com validade de 2 minutos e uso único.
                val (id, temporaryKey) = PairingSession.active() ?: return
                val pairing = runCatching { WireProtocol.decode(temporaryKey, encrypted) }.getOrNull() ?: return
                if (pairing.optInt("v") != WireProtocol.VERSION || pairing.optString("op") != "pair") return
                if (pairing.optString("session") != id) return
                if (abs(now - pairing.optLong("ts")) > 120_000L) return
                if (!AppVersion.compatible(pairing.optString("appVersion"))) {
                    val mismatch = JSONObject().put("v", WireProtocol.VERSION)
                        .put("appVersion", AppVersion.NAME).put("op", "versionMismatch")
                    WireProtocol.send(socket.getOutputStream(), WireProtocol.encode(temporaryKey, mismatch))
                    return
                }
                if (!PairingSession.consume(id, temporaryKey)) return
                val response = JSONObject().apply {
                    put("v", WireProtocol.VERSION)
                    put("op", "pairOk")
                    put("appVersion", AppVersion.NAME)
                    put("session", id)
                    put("key", CryptoBox.encode(permanentKey))
                }
                WireProtocol.send(socket.getOutputStream(), WireProtocol.encode(temporaryKey, response))
            } catch (_: Exception) {
                // Não responder a pedidos malformados, sem chave ou não autenticados.
            }
        }
    }
}
