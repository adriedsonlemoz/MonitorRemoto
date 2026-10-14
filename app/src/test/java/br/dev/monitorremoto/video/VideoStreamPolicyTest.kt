package br.dev.monitorremoto.video

import org.junit.Assert.*
import org.junit.Test

class VideoStreamPolicyTest {
    @Test fun limitsPacketSize() {
        assertTrue(VideoStreamPolicy.canPublish(8_000, 432, 896))
        assertFalse(VideoStreamPolicy.canPublish(0, 432, 896))
        assertFalse(VideoStreamPolicy.canPublish(200_000, 432, 896))
        assertFalse(VideoStreamPolicy.canPublish(500, 431, 896))
        assertFalse(VideoStreamPolicy.canPublish(500, 432, 895))
    }
    @Test fun keepsEvenVideoDimensions() {
        assertEquals(432 to 896, VideoStreamPolicy.dimensions(1080, 2240))
        val dims = VideoStreamPolicy.dimensions(2400, 1080)!!
        assertTrue(dims.first % 2 == 0 && dims.second % 2 == 0)
        assertTrue(dims.first <= 432 && dims.second <= 896)
        assertNull(VideoStreamPolicy.dimensions(0, 100))
    }
    @Test fun wakeRateLimit() {
        assertTrue(VideoStreamPolicy.allowWake(50_000, 0))
        assertFalse(VideoStreamPolicy.allowWake(50_100, 50_000))
        assertTrue(VideoStreamPolicy.allowWake(62_000, 50_000))
    }
}
