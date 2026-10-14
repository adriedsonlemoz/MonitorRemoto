package br.dev.monitorremoto.capture

import android.os.SystemClock

/** Apenas PCM em RAM com validade curta. Áudio de chamadas/microfone não capturado. */
object AudioChunkStore {
    const val RATE = 16_000
    const val MAX_CHUNK = 16_000 // até 500ms, PCM mono 16bits
    const val STALE_MS = 1800L
    data class Chunk(val sequence: Long, val pcm: ByteArray)
    @Volatile var enabled = false
        private set
    private var current: Chunk? = null
    private var sequence = 0L
    private var atMs = 0L

    @Synchronized fun start() {
        enabled = true
        sequence++
        current = null
        atMs = 0L
    }
    @Synchronized fun publish(bytes: ByteArray) {
        if (!enabled || bytes.isEmpty() || bytes.size > MAX_CHUNK) return
        sequence++
        current = Chunk(sequence, bytes.copyOf())
        atMs = SystemClock.elapsedRealtime()
    }
    @Synchronized fun snapshot(): Chunk? {
        if (!enabled || SystemClock.elapsedRealtime() - atMs > STALE_MS) return null
        return current?.let { it.copy(pcm = it.pcm.copyOf()) }
    }
    @Synchronized fun stop() {
        enabled = false
        current = null
        atMs = 0L
        sequence++
    }
}
