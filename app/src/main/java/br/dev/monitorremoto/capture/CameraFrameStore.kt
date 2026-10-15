package br.dev.monitorremoto.capture

import android.os.SystemClock
import br.dev.monitorremoto.core.CameraStreamPolicy

/** Quadros JPEG voláteis de uma Activity de câmera VISÍVEL no monitorado. Sem start remoto. */
object CameraFrameStore {
    data class Frame(val sequence: Long, val lens: String, val jpeg: ByteArray)
    private var current: Frame? = null
    private var receivedAt = 0L
    private var sequence = 0L
    private var activeLens: String? = null

    @Synchronized fun begin(lens: String) {
        require(lens in setOf("Frontal", "Traseira"))
        sequence++
        current = null
        receivedAt = 0L
        activeLens = lens
    }

    @Synchronized fun stop() {
        activeLens = null
        current = null
        receivedAt = 0L
        sequence++
    }

    @Synchronized fun isActive() = activeLens != null
    @Synchronized fun lens(): String? = activeLens

    @Synchronized fun publish(bytes: ByteArray) {
        val lens = activeLens ?: return
        if (!CameraStreamPolicy.validFrame(bytes)) return
        sequence++
        current = Frame(sequence, lens, bytes.copyOf())
        receivedAt = SystemClock.elapsedRealtime()
    }

    @Synchronized fun frameAgeMs(): Long =
        if (activeLens == null || receivedAt == 0L) -1L
        else (SystemClock.elapsedRealtime() - receivedAt).coerceAtLeast(0L)

    @Synchronized fun snapshot(): Frame? {
        if (frameAgeMs() !in 0..CameraStreamPolicy.MAX_FRAME_AGE_MS) return null
        return current?.let { it.copy(jpeg = it.jpeg.copyOf()) }
    }
}
