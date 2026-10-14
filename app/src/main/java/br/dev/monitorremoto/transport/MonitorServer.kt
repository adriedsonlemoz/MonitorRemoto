package br.dev.monitorremoto.transport

import android.content.Context
import br.dev.monitorremoto.core.BatteryReader
import br.dev.monitorremoto.core.NetworkReader
import br.dev.monitorremoto.core.EventJournal
import br.dev.monitorremoto.core.SettingsStore
import br.dev.monitorremoto.security.CryptoBox
import br.dev.monitorremoto.security.SecretVault
import org.json.JSONObject
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.LinkedHashSet
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/** Porta única: consulta autenticada e pareamento QR temporário em canal AES-GCM. */
class MonitorServer(private val context: Context) {
    private var server: ServerSocket? = null
    @Volatile private var running = false
    // Fila limitada evita retenção ilimitada de sockets em caso de tráfego abusivo na LAN.
    private val workers = ThreadPoolExecutor(
        3, 3, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue<Runnable>(12),
        ThreadPoolExecutor.AbortPolicy()
    )
    private val seenNonces = LinkedHashSet<String>()

    @Synchronized fun start() {
        check(!running)
        // Abrir porta antes de informar que o serviço está pronto para QR.
        val listener = ServerSocket(WireProtocol.PORT)
        server = listener
        running = true
        Thread({
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
            }
        }, "MonitorRemoto-server").apply { isDaemon = true; start() }
    }

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
                        regular.optString("op") !in setOf("snapshot", "disconnect")) return
                    if (abs(now - regular.optLong("ts")) > 5 * 60 * 1000L) return
                    synchronized(seenNonces) {
                        if (!seenNonces.add(nonce)) return
                        while (seenNonces.size > 1024) seenNonces.remove(seenNonces.first())
                    }
                    // A chave válida não dispensa a exigência da mesma versão do app.
                    if (!AppVersion.compatible(regular.optString("appVersion"))) return
                    if (regular.optString("op") == "disconnect") {
                        val reply = JSONObject()
                            .put("v", WireProtocol.VERSION)
                            .put("op", "disconnectOk")
                            .put("appVersion", AppVersion.NAME)
                        WireProtocol.send(socket.getOutputStream(), WireProtocol.encode(permanentKey, reply))
                        AuthenticatedAccess.disconnected(socket.inetAddress?.hostAddress.orEmpty(), regular.optString("clientId"))
                        return
                    }
                    val response = JSONObject().apply {
                        put("v", WireProtocol.VERSION)
                        put("appVersion", AppVersion.NAME)
                        put("serverMs", now)
                        put("battery", BatteryReader.snapshot(context))
                        put("network", NetworkReader.snapshot(context))
                        put("events", if (SettingsStore.isMonitorEnabled(context))
                            EventJournal.latestJson(SettingsStore.notificationAllowlist(context))
                            else org.json.JSONArray())
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
