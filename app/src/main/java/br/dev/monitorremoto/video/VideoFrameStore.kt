package br.dev.monitorremoto.video

import android.os.SystemClock
import java.util.ArrayDeque

/** Ring volátil e curto, sem áudio, gravação ou cache em disco. Somente sessão consentida. */
object VideoFrameStore {
    data class Frame(val seq: Long, val width: Int, val height: Int, val timestampUs: Long,
        val keyFrame: Boolean, val data: ByteArray, val sps: ByteArray?, val pps: ByteArray?)
    private val frames = ArrayDeque<Frame>()
    @Volatile var supported = false
        private set
    private var serial = 0L
    private var lastAt = 0L
    private var requestSync = false
    private var lastSyncRequest = 0L
    const val MAX_FRAME_BYTES = VideoStreamPolicy.MAX_FRAME_BYTES
    const val MAX_QUEUE = 30

    @Synchronized fun reset() {
        frames.clear()
        supported = false
        lastAt = 0L
        requestSync = false
        lastSyncRequest = 0L
        serial++
    }
    @Synchronized fun enable() { supported = true }
    @Synchronized fun disable() { reset() }
    @Synchronized fun syncNeeded(): Boolean {
        val need = requestSync
        requestSync = false
        return need
    }
    @Synchronized fun requestKeyFrame() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastSyncRequest >= 900L) { requestSync = true; lastSyncRequest = now }
    }
    @Synchronized fun publish(width: Int, height: Int, ptsUs: Long, key: Boolean,
        encoded: ByteArray, sps: ByteArray?, pps: ByteArray?) {
        if (!supported || !VideoStreamPolicy.canPublish(encoded.size, width, height)) return
        serial++
        frames.addLast(Frame(serial, width, height, ptsUs, key, encoded.copyOf(),
            if (key) sps?.copyOf() else null, if (key) pps?.copyOf() else null))
        while (frames.size > MAX_QUEUE) frames.removeFirst()
        lastAt = SystemClock.elapsedRealtime()
    }
    /** Entrega sequência contígua; se cliente atrasou, espera próximo IDR. */
    @Synchronized fun next(after: Long): Frame? {
        if (!supported || SystemClock.elapsedRealtime() - lastAt > 3_000L) return null
        val missed = after != 0L && frames.isNotEmpty() && after < frames.first().seq - 1L
        val candidate = when {
            after == 0L || missed -> frames.lastOrNull { it.keyFrame }
            else -> frames.firstOrNull { it.seq == after + 1L }
        }
        if (candidate != null) return candidate.copy(data = candidate.data.copyOf(),
            sps = candidate.sps?.copyOf(), pps = candidate.pps?.copyOf())
        if (after == 0L || (frames.isNotEmpty() && after < frames.first().seq - 1L)) requestSync = true
        return null
    }
    @Synchronized fun latestAgeMs(): Long = if (lastAt == 0L) -1L
        else (SystemClock.elapsedRealtime() - lastAt).coerceAtLeast(0)
}
