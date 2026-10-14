package br.dev.monitorremoto.adb

/** Intervalos crescentes para reconectar uma sessão ADB já autorizada.
 * Não tenta habilitar a depuração do Android e não faz varredura de IPs.
 */
object AdbReconnectPolicy {
    private val delaysMs = longArrayOf(2_000L, 4_000L, 8_000L, 15_000L, 30_000L)
    const val HEARTBEAT_INTERVAL_MS = 30_000L
    const val HEARTBEAT_TIMEOUT_MS = 8_000L
    fun delayMs(failedRounds: Int): Long = delaysMs[failedRounds.coerceIn(0, delaysMs.lastIndex)]
    fun shouldRetry(visible: Boolean, optedIn: Boolean, connected: Boolean, busy: Boolean): Boolean =
        visible && optedIn && !connected && !busy
}
