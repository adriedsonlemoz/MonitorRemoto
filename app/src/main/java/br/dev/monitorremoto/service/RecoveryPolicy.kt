package br.dev.monitorremoto.service

/** Política determinística e sem tráfego para recuperar falhas transitórias de bind. */
object RecoveryPolicy {
    fun nextDelayMs(failures: Int): Long = when {
        failures <= 1 -> 2_000L
        failures == 2 -> 5_000L
        failures == 3 -> 10_000L
        failures == 4 -> 30_000L
        failures == 5 -> 60_000L
        else -> 300_000L
    }
    fun canRetry(nowElapsedMs: Long, nextAllowedMs: Long): Boolean = nowElapsedMs >= nextAllowedMs
}
