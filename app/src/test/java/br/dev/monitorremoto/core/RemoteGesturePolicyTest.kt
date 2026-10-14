package br.dev.monitorremoto.core

import org.junit.Assert.*
import org.junit.Test

class RemoteGesturePolicyTest {
    @Test fun acceptsAndRejectsGestures() {
        assertTrue(RemoteGesturePolicy.valid("tap", .5f, .5f, .5f, .5f, 100L))
        assertTrue(RemoteGesturePolicy.valid("swipe", .1f, .2f, .9f, .8f, 1100L))
        assertFalse(RemoteGesturePolicy.valid("other", .5f, .5f, .5f, .5f, 100L))
        assertFalse(RemoteGesturePolicy.valid("tap", Float.NaN, .5f, .5f, .5f, 100L))
        assertFalse(RemoteGesturePolicy.valid("tap", -0.1f, .5f, .5f, .5f, 100L))
        assertFalse(RemoteGesturePolicy.valid("swipe", .2f, .2f, 1.1f, .5f, 100L))
        assertFalse(RemoteGesturePolicy.valid("tap", .5f, .5f, .5f, .5f, 10L))
    }
    @Test fun mapsLetterboxedCoordinates() {
        // Quadro retrato 500x1000 num viewport 1000x1000: x=250..750
        val center = RemoteGesturePolicy.fitCenterPoint(500f, 500f, 1000, 1000, 500, 1000)
        assertEquals(.5f, center!!.first, .001f)
        assertEquals(.5f, center.second, .001f)
        assertNull(RemoteGesturePolicy.fitCenterPoint(100f, 500f, 1000, 1000, 500, 1000))
        assertNull(RemoteGesturePolicy.fitCenterPoint(0f, 0f, 0, 100, 500, 1000))
        assertNull(RemoteGesturePolicy.fitCenterPoint(Float.NaN, 50f, 100, 100, 500, 1000))
    }
}
