package br.dev.monitorremoto.core

import org.junit.Assert.*
import org.junit.Test

class ScreenCapturePolicyTest {
    private fun jpeg(n: Int): ByteArray = ByteArray(n).apply {
        this[0] = 0xFF.toByte(); this[1] = 0xD8.toByte()
        this[lastIndex - 1] = 0xFF.toByte(); this[lastIndex] = 0xD9.toByte()
    }
    @Test fun validHeaders() {
        assertTrue(ScreenCapturePolicy.validJpeg(jpeg(100)))
        assertTrue(ScreenCapturePolicy.validJpeg(jpeg(480_000)))
        assertFalse(ScreenCapturePolicy.validJpeg(jpeg(480_001)))
        assertFalse(ScreenCapturePolicy.validJpeg(byteArrayOf()))
        assertFalse(ScreenCapturePolicy.validJpeg(byteArrayOf(0, 0, 0, 0)))
        val incomplete = jpeg(50).apply { this[lastIndex] = 0 }
        assertFalse(ScreenCapturePolicy.validJpeg(incomplete))
    }
    @Test fun freshPreview() {
        assertTrue(ScreenCapturePolicy.freshPreview(0))
        assertTrue(ScreenCapturePolicy.freshPreview(2_999))
        assertTrue(ScreenCapturePolicy.freshPreview(3_000))
        assertFalse(ScreenCapturePolicy.freshPreview(-1))
        assertFalse(ScreenCapturePolicy.freshPreview(3_001))
    }
}
