package br.dev.monitorremoto.adb

import android.content.Context
import io.github.muntashirakon.adb.AdbConnection
import io.github.muntashirakon.adb.PairingConnectionCtx
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/** Separate from Monitor Remoto's AES-GCM protocol. No background shell or arbitrary commands. */
class AdbClient(private val context: Context) {
    @Volatile var endpoint: String? = null
        private set
    @Volatile private var connection: AdbConnection? = null
    @Volatile private var cancelled = false
    @Volatile private var pairing: PairingConnectionCtx? = null
    private var identity: AdbIdentity.Identity? = null
    private fun key() = identity ?: AdbIdentity.load(context).also { identity = it }
    fun pair(host: String, port: Int, password: String) {
        check(!cancelled)
        PairingConnectionCtx(host, port, password.toByteArray(Charsets.UTF_8), key().privateKey, key().certificate, "MonitorRemoto").use {
            pairing = it
            try { check(!cancelled); it.start() } finally { pairing = null }
        }
        check(!cancelled) { "Operação cancelada" }
    }
    fun connect(host: String, port: Int) {
        check(!cancelled)
        connection?.close()
        val next = AdbConnection.Builder(host, port).setApi(30).setPrivateKey(key().privateKey).setCertificate(key().certificate).setDeviceName("MonitorRemoto").build()
        connection = next
        endpoint = "$host:$port"
        if (cancelled) { next.close(); error("Operação cancelada") }
        try { check(next.connect(15, TimeUnit.SECONDS, true)) { "ADB não confirmou a conexão" } }
        catch (e: Exception) { next.close(); connection = null; throw e }
    }
    fun connected() = connection?.let { it.isConnected && it.isConnectionEstablished } == true
    fun execute(command: AdbPolicy.Command): String {
        check(!cancelled) { "Operação cancelada" }
        val active = connection ?: error("Conecte primeiro")
        check(connected()) { "ADB desconectado" }
        active.open("shell:" + command.shell).use { stream ->
            stream.openInputStream().use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(1024)
                while (output.size() < 8192) {
                    val n = input.read(buffer, 0, minOf(buffer.size, 8192-output.size()))
                    if (n < 0) break
                    output.write(buffer, 0, n)
                }
                return output.toString("UTF-8").filter { it == '\n' || !it.isISOControl() }
            }
        }
    }
    fun requestCancel() { cancelled = true }
    fun close() {
        cancelled = true
        pairing?.cancel()
        runCatching { connection?.close() }
        connection = null
        endpoint = null
    }
}
