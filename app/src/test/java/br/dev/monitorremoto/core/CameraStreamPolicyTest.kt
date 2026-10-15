package br.dev.monitorremoto.core

import org.junit.Assert.*
import org.junit.Test

class CameraStreamPolicyTest {
    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0, 0xFF.toByte(), 0xD9.toByte())
    @Test fun aceitaMarcadoresJpeg() { assertTrue(CameraStreamPolicy.validFrame(jpeg)) }
    @Test fun rejeitaJpegVazioOuTruncado() {
        assertFalse(CameraStreamPolicy.validFrame(byteArrayOf()))
        assertFalse(CameraStreamPolicy.validFrame(byteArrayOf(1, 2, 3, 4, 5)))
        assertFalse(CameraStreamPolicy.validFrame(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2, 3)))
    }
    @Test fun rejeitaPayloadGrande() {
        val oversized = ByteArray(CameraStreamPolicy.MAX_FRAME_BYTES + 1)
        oversized[0] = 0xFF.toByte(); oversized[1] = 0xD8.toByte()
        oversized[oversized.lastIndex - 1] = 0xFF.toByte(); oversized[oversized.lastIndex] = 0xD9.toByte()
        assertFalse(CameraStreamPolicy.validFrame(oversized))
    }
    @Test fun intervaloMinimo() {
        assertFalse(CameraStreamPolicy.shouldEncode(false, 1_000_000))
        assertFalse(CameraStreamPolicy.shouldEncode(true, 999))
        assertTrue(CameraStreamPolicy.shouldEncode(true, 1000))
    }
    @Test fun demandaAutenticadaExpiraSemPedidosRecentes() {
        assertFalse(CameraStreamPolicy.hasRecentViewer(0L, 5_000L))
        assertFalse(CameraStreamPolicy.hasRecentViewer(1_000L, 999L))
        assertTrue(CameraStreamPolicy.hasRecentViewer(1_000L, 1_000L))
        assertTrue(CameraStreamPolicy.hasRecentViewer(1_000L, 5_500L))
        assertFalse(CameraStreamPolicy.hasRecentViewer(1_000L, 5_501L))
    }
    @Test fun expiracao() { assertEquals(3000L, CameraStreamPolicy.MAX_FRAME_AGE_MS) }
}
