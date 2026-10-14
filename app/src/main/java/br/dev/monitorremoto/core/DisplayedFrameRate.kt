package br.dev.monitorremoto.core
import java.util.ArrayDeque

/** Mede a taxa de quadros realmente exibidos, nunca a taxa-alvo do codificador. */
class DisplayedFrameRate {
    private val samples = ArrayDeque<Long>()
    private var latest = 0L

    fun record(elapsedRealtimeMs: Long): Float {
        if (elapsedRealtimeMs < latest) reset() // relógio monotônico reiniciado
        latest = elapsedRealtimeMs
        samples.addLast(elapsedRealtimeMs)
        trim(elapsedRealtimeMs)
        return measured()
    }

    fun current(elapsedRealtimeMs: Long): Float {
        if (samples.isEmpty() || elapsedRealtimeMs - latest > 2_500L ||
            elapsedRealtimeMs < latest) {
            reset()
            return 0f
        }
        trim(elapsedRealtimeMs)
        return measured()
    }

    fun reset() { samples.clear(); latest = 0L }

    private fun trim(now: Long) {
        while (samples.size > 12 ||
            (samples.isNotEmpty() && now - samples.first() > 2_000L)) {
            samples.removeFirst()
        }
    }

    private fun measured(): Float {
        if (samples.size < 2) return 0f
        val interval = (samples.last() - samples.first()).coerceAtLeast(1L)
        return (samples.size - 1) * 1_000f / interval
    }
}
