package br.dev.monitorremoto.core

import org.junit.Assert.*
import org.junit.Test

class DisplayedFrameRateTest {
    @Test fun measuresActualDisplayedFrames() {
        val fps = DisplayedFrameRate()
        assertEquals(0f, fps.record(1_000L), .001f)
        assertEquals(10f, fps.record(1_100L), .01f)
        assertEquals(10f, fps.record(1_200L), .01f)
    }

    @Test fun clearsOldFramesInsteadOfClaimingStaleFps() {
        val fps = DisplayedFrameRate()
        fps.record(1_000L); fps.record(1_100L)
        assertEquals(0f, fps.current(4_000L), .001f)
        assertEquals(0f, fps.record(4_100L), .001f)
    }

    @Test fun neverRetainsUnboundedFrameHistory() {
        val fps = DisplayedFrameRate()
        for (i in 0..1000) fps.record((100L * i) + 1L)
        assertTrue(fps.current(100_001L) in 9f..11f)
    }

    @Test fun handlesCounterReset() {
        val fps = DisplayedFrameRate()
        fps.record(9_000L); fps.record(9_100L)
        assertEquals(0f, fps.record(1L), .001f)
    }
}
