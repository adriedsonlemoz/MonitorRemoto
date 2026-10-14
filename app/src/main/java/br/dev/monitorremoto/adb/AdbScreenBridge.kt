package br.dev.monitorremoto.adb

import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors

/**
 * Entrega explícita de uma sessão ADB já autorizada para o visualizador de tela.
 * Não é serviço: só existe no processo e é encerrada quando o visualizador sai do primeiro plano.
 * A imagem nunca passa por este objeto: MediaProjection/Monitor Remoto continuam independentes.
 */
object AdbScreenBridge {
    private data class Session(val host: String, val client: AdbClient)
    @Volatile private var session: Session? = null
    private val commands = Executors.newSingleThreadExecutor { task ->
        Thread(task, "MonitorRemoto-ADB-viewer").apply { isDaemon = true }
    }
    private val main = Handler(Looper.getMainLooper())

    @Synchronized fun handoff(host: String, client: AdbClient) {
        val old = session
        session = Session(host, client)
        if (old != null && old.client !== client) release(old.client)
    }

    fun connectedFor(host: String): Boolean {
        val current = session ?: return false
        return current.host == host && current.client.connected()
    }

    fun execute(host: String, command: AdbPolicy.Command, done: (Boolean, String) -> Unit) {
        val current = session
        if (current == null || current.host != host || !current.client.connected()) {
            main.post { done(false, "Sessão ADB indisponível para este aparelho.") }
            return
        }
        commands.execute {
            val outcome = runCatching {
                check(session === current && current.client.connected()) { "Sessão ADB encerrada" }
                current.client.execute(command)
            }
            if (outcome.isFailure) close(host)
            main.post {
                done(outcome.isSuccess, if (outcome.isSuccess) "Comando ADB enviado" else
                    "ADB desconectado. O comando não pôde ser confirmado.")
            }
        }
    }

    @Synchronized fun close(host: String? = null) {
        val old = session ?: return
        if (host != null && old.host != host) return
        session = null
        release(old.client)
    }

    private fun release(client: AdbClient) {
        client.requestCancel()
        Thread({ client.close() }, "MonitorRemoto-ADB-close").start()
    }
}
