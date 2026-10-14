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
    @Test fun globalNavigationDoesNotRequireImageTouchMode() {
        for (action in listOf("back", "home", "recents", "notifications")) {
            assertTrue(RemoteGesturePolicy.isGlobalAction(action))
            assertTrue(RemoteGesturePolicy.valid(action, .5f, .5f, .5f, .5f, 240L))
        }
        assertFalse(RemoteGesturePolicy.isGlobalAction("tap"))
        assertFalse(RemoteGesturePolicy.isGlobalAction("longPress"))
        assertFalse(RemoteGesturePolicy.valid("unlock", .5f, .5f, .5f, .5f, 240L))
    }
    @Test fun mapsCropModeWhenImageIsLargerThanViewport() {
        // Retrato 500x1000 na janela quadrada 1000x1000: recorte vertical do frame.
        val center = RemoteGesturePolicy.centerCropPoint(500f, 500f, 1000, 1000, 500, 1000)
        assertEquals(.5f, center!!.first, .001f)
        assertEquals(.5f, center.second, .001f)
        val top = RemoteGesturePolicy.centerCropPoint(500f, 0f, 1000, 1000, 500, 1000)
        assertEquals(.25f, top!!.second, .001f)
        val bottom = RemoteGesturePolicy.centerCropPoint(500f, 1000f, 1000, 1000, 500, 1000)
        assertEquals(.75f, bottom!!.second, .001f)
        assertNull(RemoteGesturePolicy.centerCropPoint(-5f, 200f, 1000, 1000, 500, 1000))
        assertNull(RemoteGesturePolicy.centerCropPoint(Float.NaN, 200f, 1000, 1000, 500, 1000))
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
