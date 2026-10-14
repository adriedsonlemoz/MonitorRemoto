package br.dev.monitorremoto.capture

/** Apenas RAM, sem gravação de telas ou chaves em disco. Limite temporal independente do TCP. */
object ScreenFrameStore {
    data class Frame(val sequence: Long, val width: Int, val height: Int, val jpeg: ByteArray)
    @Volatile var sharing = false
        private set
    private var current: Frame? = null
    private var savedAtMs = 0L
    private var sequence = 0L
    const val MAX_JPEG_BYTES = 110_000
    const val STALE_MS = 3_000L

    @Synchronized fun begin() {
        current = null
        savedAtMs = 0L
        sharing = true
        sequence++
    }
    @Synchronized fun publish(width: Int, height: Int, bytes: ByteArray) {
        if (!sharing || width <= 0 || height <= 0 || bytes.isEmpty() || bytes.size > MAX_JPEG_BYTES) return
        sequence++
        current = Frame(sequence, width, height, bytes.copyOf())
        savedAtMs = android.os.SystemClock.elapsedRealtime()
    }
    @Synchronized fun snapshot(): Frame? {
        if (!sharing || android.os.SystemClock.elapsedRealtime() - savedAtMs > STALE_MS) return null
        return current?.let { it.copy(jpeg = it.jpeg.copyOf()) }
    }
    @Synchronized fun stop() {
        sharing = false
        current = null
        savedAtMs = 0L
        sequence++
    }
}
