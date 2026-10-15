package br.dev.monitorremoto.core

import br.dev.monitorremoto.capture.CameraFrameStore
import org.junit.Assert.*
import org.junit.Test

class CameraFrameStoreGenerationTest {
    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0, 0xFF.toByte(), 0xD9.toByte())

    @Test fun sessaoAntigaNaoPublicaEmNovaCamera() {
        CameraFrameStore.stop()
        try {
            val old = CameraFrameStore.begin("Traseira")
            CameraFrameStore.publish(old, jpeg)
            assertEquals("Traseira", CameraFrameStore.snapshot()?.lens)
            val latest = CameraFrameStore.begin("Frontal")
            assertNull(CameraFrameStore.snapshot())
            CameraFrameStore.publish(old, jpeg)
            assertNull(CameraFrameStore.snapshot())
            CameraFrameStore.publish(latest, jpeg)
            assertEquals("Frontal", CameraFrameStore.snapshot()?.lens)
        } finally { CameraFrameStore.stop() }
    }

    @Test fun paradaRevogaTokenDeCapturaAnterior() {
        CameraFrameStore.stop()
        val old = CameraFrameStore.begin("Frontal")
        CameraFrameStore.stop()
        assertFalse(CameraFrameStore.isActive())
        CameraFrameStore.publish(old, jpeg)
        assertNull(CameraFrameStore.snapshot())
        assertEquals(-1L, CameraFrameStore.frameAgeMs())
    }

    @Test fun consultasIguaisNaoDuplicamJpegEEstadoEhCoerente() {
        CameraFrameStore.stop()
        try {
            val token = CameraFrameStore.begin("Traseira")
            CameraFrameStore.publish(token, jpeg)
            val first = CameraFrameStore.status(0L)
            assertTrue(first.active)
            assertEquals("Traseira", first.lens)
            assertTrue(first.sequence > 0L)
            assertArrayEquals(jpeg, first.jpeg)
            val same = CameraFrameStore.status(first.sequence)
            assertEquals(first.sequence, same.sequence)
            assertNull(same.jpeg)
            assertTrue(same.ageMs >= 0L)
            val old = CameraFrameStore.status(first.sequence - 1L)
            assertArrayEquals(jpeg, old.jpeg)
            CameraFrameStore.stop()
            val closed = CameraFrameStore.status(0L)
            assertFalse(closed.active)
            assertNull(closed.jpeg)
            assertNull(closed.lens)
            assertEquals(-1L, closed.ageMs)
        } finally { CameraFrameStore.stop() }
    }

    @Test fun somenteSolicitacaoComCameraAbertaCriaDemanda() {
        CameraFrameStore.stop()
        assertFalse(CameraFrameStore.hasRecentViewer())
        CameraFrameStore.viewerPing()
        assertFalse(CameraFrameStore.hasRecentViewer())
        val first = CameraFrameStore.begin("Frontal")
        try {
            assertFalse(CameraFrameStore.hasRecentViewer())
            CameraFrameStore.viewerPing()
            assertTrue(CameraFrameStore.hasRecentViewer())
            val second = CameraFrameStore.begin("Traseira")
            assertTrue(second > first)
            assertFalse(CameraFrameStore.hasRecentViewer())
        } finally { CameraFrameStore.stop() }
        assertFalse(CameraFrameStore.hasRecentViewer())
    }

    @Test fun dadosInvalidosNaoSaoRetidos() {
        CameraFrameStore.stop()
        try {
            val token = CameraFrameStore.begin("Traseira")
            CameraFrameStore.publish(token, byteArrayOf(1,2,3,4))
            assertNull(CameraFrameStore.snapshot())
        } finally { CameraFrameStore.stop() }
    }
}
