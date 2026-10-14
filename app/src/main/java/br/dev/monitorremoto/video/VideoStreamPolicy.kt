package br.dev.monitorremoto.video

/** Limites de transmissão; não confundir alvo de FPS com medição real no aparelho. */
object VideoStreamPolicy {
    const val TARGET_FPS = 10
    const val MAX_FRAME_BYTES = 128_000
    const val WAKE_COOLDOWN_MS = 12_000L
    fun dimensions(sourceWidth: Int, sourceHeight: Int): Pair<Int, Int>? {
        if (sourceWidth <= 0 || sourceHeight <= 0) return null
        val scale = minOf(1.0, 432.0 / sourceWidth, 896.0 / sourceHeight)
        val width = ((sourceWidth * scale).toInt() / 2 * 2).coerceAtLeast(64)
        val height = ((sourceHeight * scale).toInt() / 2 * 2).coerceAtLeast(64)
        return width to height
    }
    fun allowWake(nowMs: Long, previousMs: Long): Boolean =
        previousMs <= 0L || nowMs - previousMs >= WAKE_COOLDOWN_MS
    fun canPublish(size: Int, width: Int, height: Int): Boolean =
        size in 1..MAX_FRAME_BYTES && width in 64..1280 && height in 64..1280 &&
            width % 2 == 0 && height % 2 == 0
}
