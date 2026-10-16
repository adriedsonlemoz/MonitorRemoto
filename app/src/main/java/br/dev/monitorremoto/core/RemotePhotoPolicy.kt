package br.dev.monitorremoto.core

/** Regras puras para foto sob demanda, independentes de Camera2 e Android. */
object RemotePhotoPolicy {
    const val MAX_JPEG_BYTES = 320 * 1024
    const val COOLDOWN_MS = 2_000L
    fun validLens(lens: String) = lens == "Traseira" || lens == "Frontal"
    fun canRequest(nowElapsedMs: Long, previousElapsedMs: Long): Boolean =
        nowElapsedMs > 0L && (previousElapsedMs == 0L || nowElapsedMs - previousElapsedMs >= COOLDOWN_MS)
    fun validJpeg(bytes: ByteArray): Boolean = bytes.size in 512..MAX_JPEG_BYTES &&
        bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte() &&
        bytes[bytes.lastIndex - 1] == 0xff.toByte() && bytes.last() == 0xd9.toByte()
}
