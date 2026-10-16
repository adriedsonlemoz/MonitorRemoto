package br.dev.monitorremoto.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PhotoPreviewPolicyTest {
    @Test fun smallPhotosKeepFullResolution() {
        assertEquals(1, PhotoPreviewPolicy.sampleSize(640, 480))
        assertEquals(1, PhotoPreviewPolicy.sampleSize(1600, 900))
    }
    @Test fun largePhotosUsePowerOfTwoSamples() {
        assertEquals(2, PhotoPreviewPolicy.sampleSize(2560, 1440))
        assertEquals(4, PhotoPreviewPolicy.sampleSize(6000, 4000))
        assertEquals(8, PhotoPreviewPolicy.sampleSize(12000, 8000))
    }
    @Test fun invalidDimensionsRefused() {
        assertThrows(IllegalArgumentException::class.java) { PhotoPreviewPolicy.sampleSize(0, 10) }
    }
}
