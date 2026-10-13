package br.dev.monitorremoto.transport

import android.content.Context
import br.dev.monitorremoto.core.BatteryReader
import br.dev.monitorremoto.core.EventJournal
import br.dev.monitorremoto.security.SecretVault
import org.json.JSONObject
import java.net.ServerSocket
import java.net.Socket
import java.net.InetSocketAddress
import java.net.SocketException
import java.util.LinkedHashSet
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.RejectedExecutionException
import kotlin.concurrent.thread
import kotlin.math.abs

/** Escuta no Wi-Fi ou na VPN. Não abre portas no roteador. */
class MonitorServer(private val context: Context) {
    @Volatile private var running = false
    @Volatile private var server: ServerSocket? = null
    private val workers = ThreadPoolExecutor(2, 2, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue(4))
    private val seenNonces = LinkedHashSet<String>()

    @Synchronized fun start() {
        if (running) return
        val listener = ServerSocket().apply {
            reuseAddress = true
            bind(InetSocketAddress(WireProtocol.PORT))
            soTimeout = 1200
        }
        server = listener
        running = true
        thread(name = "MonitorRemoto-accept", isDaemon = true) {
            try {
                listener.use { socket ->
                    while (running) {
                        val peer = try { socket.accept() } catch (_: java.net.SocketTimeoutException) { continue }
                        try {
                            workers.execute { handle(peer) }
                        } catch (_: RejectedExecutionException) {
                            peer.close()
                        }
                    }
                }
            } catch (_: SocketException) {
                // Fechamento normal ou porta já utilizada. UI deve mostrar serviço inativo.
            } catch (_: Exception) {
                // Erro no bind. Um novo start poderá ser tentado na UI.
            } finally {
                running = false
                server = null
            }
        }
    }

    @Synchronized fun stop() {
        running = false
        try { server?.close() } catch (_: Exception) { }
        workers.shutdownNow()
    }

    private fun handle(peer: Socket) {
        peer.use { socket ->
            try {
                socket.soTimeout = 4500
                val key = SecretVault.load(context, SecretVault.MONITOR_KEY) ?: return
                val encrypted = WireProtocol.receive(socket.getInputStream(), 16 * 1024)
                val envelope = JSONObject(String(encrypted, Charsets.UTF_8))
                val nonce = envelope.getString("n")
                val request = WireProtocol.decode(key, encrypted)
                if (request.getInt("v") != WireProtocol.VERSION) return
                if (request.getString("op") != "snapshot") return
                if (abs(System.currentTimeMillis() - request.getLong("ts")) > 5 * 60 * 1000L) return
                synchronized(seenNonces) {
                    if (seenNonces.contains(nonce)) return
                    seenNonces.add(nonce)
                    while (seenNonces.size > 1024) seenNonces.remove(seenNonces.first())
                }
                val response = JSONObject().apply {
                    put("v", WireProtocol.VERSION)
                    put("serverMs", System.currentTimeMillis())
                    put("battery", BatteryReader.snapshot(context))
                    put("events", EventJournal.latestJson())
                    put("epoch", EventJournal.epoch)
                }
                WireProtocol.send(socket.getOutputStream(), WireProtocol.encode(key, response))
            } catch (_: Exception) {
                // Sem resposta a pedidos não autenticados ou malformados.
            }
        }
    }
}
