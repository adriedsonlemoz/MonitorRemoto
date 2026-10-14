package br.dev.monitorremoto.capture

/** Apenas RAM, sem gravação de telas ou chaves em disco. Limite temporal independente do TCP. */
object ScreenFrameStore {
    data class Frame(val sequence: Long, val width: Int, val height: Int, val jpeg: ByteArray)
    @Volatile var sharing = false
        private set
    @Volatile private var viewerAtMs = 0L
    fun viewerPing() { viewerAtMs = android.os.SystemClock.elapsedRealtime() }
    fun viewerActive(): Boolean = sharing && viewerAtMs != 0L &&
        android.os.SystemClock.elapsedRealtime() - viewerAtMs < 5_000L
    private var current: Frame? = null
    private var savedAtMs = 0L
    private var sequence = 0L
    const val MAX_JPEG_BYTES = 110_000
    // Captura única em RAM, solicitada manualmente pelo controlador autenticado.
    // NUNCA gerar screenshot em repouso, e nunca persistir no monitorado.
    const val MAX_STILL_BYTES = br.dev.monitorremoto.core.ScreenCapturePolicy.MAX_CAPTURE_BYTES
    private var stillPendingId = 0L
    private var stillDeadlineMs = 0L
    private var stillResult: Pair<Long, ByteArray>? = null
    private var nextStillId = 0L

    @Synchronized fun requestStill(): Long? {
        if (!sharing) return null
        val now = android.os.SystemClock.elapsedRealtime()
        if (stillPendingId != 0L && now <= stillDeadlineMs) return null
        stillResult = null
        nextStillId++
        stillPendingId = nextStillId
        stillDeadlineMs = now + 2_700L
        return stillPendingId
    }
    @Synchronized fun pendingStill(): Long {
        if (!sharing || android.os.SystemClock.elapsedRealtime() > stillDeadlineMs) return 0L
        return stillPendingId
    }
    @Synchronized fun publishStill(id: Long, bytes: ByteArray) {
        if (id == 0L || id != stillPendingId || !sharing ||
            android.os.SystemClock.elapsedRealtime() > stillDeadlineMs ||
            bytes.isEmpty() || bytes.size > MAX_STILL_BYTES) return
        stillResult = id to bytes.copyOf()
        stillPendingId = 0L
    }
    @Synchronized fun takeStill(id: Long): ByteArray? {
        val found = stillResult?.takeIf { it.first == id }?.second?.copyOf()
        if (found != null) stillResult = null
        return found
    }
    @Synchronized fun cancelStill(id: Long) {
        if (stillPendingId == id) stillPendingId = 0L
        if (stillResult?.first == id) stillResult = null
    }
    const val STALE_MS = 3_000L

    @Synchronized fun begin() {
        current = null
        savedAtMs = 0L
        sharing = true
        viewerAtMs = 0L
        stillPendingId = 0L
        stillResult = null
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
        viewerAtMs = 0L
        stillPendingId = 0L
        stillResult = null
        current = null
        savedAtMs = 0L
        sequence++
    }
}
