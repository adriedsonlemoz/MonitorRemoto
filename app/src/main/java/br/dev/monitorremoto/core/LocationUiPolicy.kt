package br.dev.monitorremoto.core

/** Regras puras de apresentação da localização remota. */
object LocationUiPolicy {
    const val TRACKING_DELAY_MS = 3_000L
    const val STALE_AGE_MS = 30_000L

    fun refreshDelayMs(tracking: Boolean, automatic: Boolean, intervalMs: Long): Long? = when {
        tracking -> TRACKING_DELAY_MS
        automatic && intervalMs in 5_000L..300_000L -> intervalMs
        else -> null
    }

    fun gpsIsRecent(remoteState: String, ageMs: Long): Boolean =
        remoteState == "disponivel" && ageMs in 0L..STALE_AGE_MS

    fun elapsedAgeMs(ageAtResponseMs: Long, passedMs: Long): Long =
        if (ageAtResponseMs < 0L) -1L else ageAtResponseMs.coerceAtMost(Long.MAX_VALUE - passedMs.coerceAtLeast(0L)) + passedMs.coerceAtLeast(0L)
}
