package br.dev.monitorremoto.core

/** Escolhe uma amostra de Bitmap pequena para a UI sem alterar o JPEG recebido para salvar. */
object PhotoPreviewPolicy {
    const val MAX_PREVIEW_EDGE = 1600
    fun sampleSize(width: Int, height: Int): Int {
        require(width > 0 && height > 0) { "Dimensões inválidas" }
        var divisor = 1
        while (width / divisor > MAX_PREVIEW_EDGE || height / divisor > MAX_PREVIEW_EDGE) {
            if (divisor >= (1 shl 29)) return divisor
            divisor *= 2
        }
        return divisor
    }
}
