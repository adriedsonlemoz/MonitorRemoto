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
    /** Diagnóstico somente leitura, sem shell arbitrário ou varredura de arquivos. */
    fun readThermalService(): String {
        check(!cancelled && connected()) { "ADB sem sessão TLS ativa" }
        val active = connection ?: error("ADB desconectado")
        active.open("shell:dumpsys thermalservice").use { stream ->
            stream.openInputStream().use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                while (output.size() < 65536) {
                    val n = input.read(buffer, 0, minOf(buffer.size, 65536 - output.size()))
                    if (n < 0) break
                    output.write(buffer, 0, n)
                }
                return output.toString("UTF-8")
            }
        }
    }

    /** Apenas pacotes validados e duas ações fixas. ADB sempre exige sessão TLS ativa. */
    fun executeAppAction(packageName: String, action: String): String {
        val command = br.dev.monitorremoto.core.RemoteAppsPolicy.command(packageName, action)
        check(!cancelled) { "Operação cancelada" }
        val active = connection ?: error("Conecte primeiro")
        check(connected()) { "ADB desconectado" }
        active.open("shell:" + command).use { stream ->
            stream.openInputStream().use { input ->
                val buffer = ByteArray(1024)
                val output = ByteArrayOutputStream()
                while (output.size() < 4_096) {
                    val n = input.read(buffer, 0, minOf(buffer.size, 4096 - output.size()))
                    if (n < 0) break
                    output.write(buffer, 0, n)
                }
                val result = output.toString("UTF-8")
                if (result.contains("Error:", ignoreCase = true) || result.contains("Exception", ignoreCase = true))
                    error(result.take(200))
                return "ADB enviou ${when (action) { "stop" -> "parada"; "settings" -> "configurações"; else -> "abertura" }} para $packageName. Confirme o resultado no monitorado."
            }
        }
    }

    /** Equivalente ao ADB reboot normal: serviço `reboot:`, sem modo recovery/poweroff. */
    fun requestNormalReboot() {
        check(!cancelled) { "Operação cancelada" }
        val active = connection ?: error("ADB não conectado")
        check(connected()) { "ADB não conectado" }
        // O dispositivo derruba a conexão durante o reboot. Não repetir se der timeout/EOF:
        // uma exceção após o envio pode significar que a reinicialização já começou.
        active.open("reboot:").use { stream ->
            // A abertura do serviço já é o pedido; não enviamos argumentos nem comandos adicionais.
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
