package br.dev.monitorremoto.core

/** Limites estáticos para não salvar imagens incompletas ou quadros antigos. */
object ScreenCapturePolicy {
    const val MAX_CAPTURE_BYTES = 480_000
    const val FRESH_PREVIEW_MS = 3_000L

    fun validJpeg(bytes: ByteArray): Boolean = bytes.size in 4..MAX_CAPTURE_BYTES &&
        bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() &&
        bytes[bytes.lastIndex - 1] == 0xFF.toByte() && bytes[bytes.lastIndex] == 0xD9.toByte()

    fun freshPreview(ageMs: Long): Boolean = ageMs in 0..FRESH_PREVIEW_MS
}
