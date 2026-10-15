package br.dev.monitorremoto.core

/** Limites pequenos: 1 quadro JPEG/segundo, somente memória, sem áudio ou armazenamento. */
object CameraStreamPolicy {
    const val MAX_FRAME_BYTES = 110_000
    const val MAX_FRAME_AGE_MS = 3_000L
    const val MIN_INTERVAL_MS = 1_000L

    fun validFrame(bytes: ByteArray): Boolean = bytes.size in 4..MAX_FRAME_BYTES &&
        bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() &&
        bytes[bytes.lastIndex - 1] == 0xFF.toByte() && bytes[bytes.lastIndex] == 0xD9.toByte()

    fun shouldEncode(active: Boolean, sinceLastMs: Long): Boolean =
        active && sinceLastMs >= MIN_INTERVAL_MS
}
