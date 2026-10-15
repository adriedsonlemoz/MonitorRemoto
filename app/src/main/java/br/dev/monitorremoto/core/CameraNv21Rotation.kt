package br.dev.monitorremoto.core

/** Rotação de quadros NV21 compactos antes do JPEG; sem APIs Android e sem espelhamento. */
object CameraNv21Rotation {
    data class Frame(val bytes: ByteArray, val width: Int, val height: Int)

    /**
     * SENSOR_ORIENTATION é medido em relação à orientação natural do aparelho.
     * Surface.ROTATION_* (0..3) é a rotação atual da tela, no sentido horário.
     * A câmera frontal usa sentido oposto ao da traseira. Não espelhar selfie silenciosamente.
     */
    fun degrees(sensorOrientation: Int, displayRotation: Int, facingFront: Boolean): Int {
        require(sensorOrientation in 0..359 && sensorOrientation % 90 == 0) { "Sensor inválido" }
        require(displayRotation in 0..3) { "Rotação de tela inválida" }
        val screenDegrees = displayRotation * 90
        return (sensorOrientation + (if (facingFront) screenDegrees else -screenDegrees) + 360) % 360
    }

    /** Rotação horária de Y e VU por blocos 2×2, preservando a ordem NV21. */
    fun rotate(data: ByteArray, width: Int, height: Int, degrees: Int): Frame {
        require(width in 2..800 && height in 2..800 && width % 2 == 0 && height % 2 == 0)
        require(degrees == 0 || degrees == 90 || degrees == 180 || degrees == 270)
        require(data.size == width * height * 3 / 2) { "NV21 incompleto" }
        if (degrees == 0) return Frame(data, width, height)
        val rotatedWidth = if (degrees == 90 || degrees == 270) height else width
        val rotatedHeight = if (degrees == 90 || degrees == 270) width else height
        val result = ByteArray(data.size)
        fun source(x: Int, y: Int, w: Int, h: Int): Pair<Int, Int> = when (degrees) {
            90 -> y to (h - 1 - x)
            180 -> (w - 1 - x) to (h - 1 - y)
            270 -> (w - 1 - y) to x
            else -> x to y
        }
        for (y in 0 until rotatedHeight) for (x in 0 until rotatedWidth) {
            val (sx, sy) = source(x, y, width, height)
            result[y * rotatedWidth + x] = data[sy * width + sx]
        }
        val originalLuma = width * height
        val cw = width / 2
        val ch = height / 2
        val newChromaWidth = rotatedWidth / 2
        for (y in 0 until rotatedHeight / 2) for (x in 0 until newChromaWidth) {
            val (sx, sy) = source(x, y, cw, ch)
            val src = originalLuma + 2 * (sy * cw + sx)
            val dst = originalLuma + 2 * (y * newChromaWidth + x)
            result[dst] = data[src] // V
            result[dst + 1] = data[src + 1] // U
        }
        return Frame(result, rotatedWidth, rotatedHeight)
    }
}
