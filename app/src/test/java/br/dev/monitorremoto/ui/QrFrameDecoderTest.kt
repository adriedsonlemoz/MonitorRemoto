package br.dev.monitorremoto.ui

import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QrFrameDecoderTest {
    @Test fun decodeGeneratedQr() {
        val payload = "monitorremoto://pair?v=1&host=192.168.1.4&port=37841&sid=test&secret=test"
        val size = 240
        val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, size, size)
        val pixels = ByteArray(size * size) { idx ->
            if (matrix[idx % size, idx / size]) 0 else 255.toByte()
        }
        assertEquals(payload, QrFrameDecoder.decode(pixels, size, size))
        // Testa rotação e dimensões não quadradas via borda branca extra.
        val width = size + 24
        val padded = ByteArray(width * size) { 255.toByte() }
        for (y in 0 until size) for (x in 0 until size) padded[y * width + x] = pixels[y * size + x]
        assertEquals(payload, QrFrameDecoder.decode(padded, width, size))
        val upsideDown = ByteArray(padded.size) { i -> padded[padded.lastIndex - i] }
        assertEquals(payload, QrFrameDecoder.decode(upsideDown, width, size))
        val clockwise = ByteArray(padded.size)
        for (y in 0 until size) for (x in 0 until width) {
            clockwise[x * size + (size - 1 - y)] = padded[y * width + x]
        }
        assertEquals(payload, QrFrameDecoder.decode(clockwise, size, width))
    }

    @Test fun invalidFrames() {
        assertNull(QrFrameDecoder.decode(byteArrayOf(1, 2), 1, 3))
        assertNull(QrFrameDecoder.decode(byteArrayOf(), 0, 0))
        assertNull(QrFrameDecoder.decode(ByteArray(30 * 30) { 255.toByte() }, 30, 30))
    }
}
