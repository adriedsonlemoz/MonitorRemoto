package br.dev.monitorremoto.ui

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer

/** Leitura local dos pixels Y; não grava fotos nem envia imagens para a rede. */
object QrFrameDecoder {
    private val hints = mapOf(
        DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
        DecodeHintType.TRY_HARDER to true
    )

    fun decode(luminance: ByteArray, width: Int, height: Int): String? {
        if (width <= 0 || height <= 0 || width.toLong() * height != luminance.size.toLong()) return null
        tryRead(luminance, width, height)?.let { return it }
        // Sensor costuma ser paisagem com a tela na vertical. Tenta rotação 90° sem outro scanner.
        val rotated = ByteArray(luminance.size)
        for (y in 0 until height) for (x in 0 until width) {
            rotated[x * height + (height - 1 - y)] = luminance[y * width + x]
        }
        tryRead(rotated, height, width)?.let { return it }
        // QR parcialmente invertido ou orientação 180/270 graus em fabricantes diferentes.
        val rotated180 = ByteArray(luminance.size) { idx -> luminance[luminance.lastIndex - idx] }
        tryRead(rotated180, width, height)?.let { return it }
        val rotated270 = ByteArray(luminance.size) { idx -> rotated[rotated.lastIndex - idx] }
        return tryRead(rotated270, height, width)
    }

    private fun tryRead(bytes: ByteArray, width: Int, height: Int): String? {
        val source = PlanarYUVLuminanceSource(bytes, width, height, 0, 0, width, height, false)
        val reader = MultiFormatReader().apply { setHints(hints) }
        return try {
            reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text
        } catch (_: NotFoundException) {
            null
        } catch (_: com.google.zxing.ReaderException) {
            null
        } finally {
            reader.reset()
        }
    }
}
