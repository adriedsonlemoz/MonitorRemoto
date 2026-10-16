package br.dev.monitorremoto.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemotePhotoPolicyTest {
    @Test fun lensesAreOnlyFrontOrBack() {
        assertTrue(RemotePhotoPolicy.validLens("Traseira"))
        assertTrue(RemotePhotoPolicy.validLens("Frontal"))
        assertFalse(RemotePhotoPolicy.validLens("interna"))
        assertFalse(RemotePhotoPolicy.validLens(""))
    }
    @Test fun captureRateLimits() {
        assertTrue(RemotePhotoPolicy.canRequest(1_000, 0))
        assertFalse(RemotePhotoPolicy.canRequest(2_999, 1_000))
        assertTrue(RemotePhotoPolicy.canRequest(3_000, 1_000))
        assertFalse(RemotePhotoPolicy.canRequest(0, 0))
    }
    @Test fun jpegEnvelopeValidated() {
        val valid = ByteArray(512) { 0 }
        valid[0] = 0xff.toByte(); valid[1] = 0xd8.toByte()
        valid[valid.lastIndex - 1] = 0xff.toByte(); valid[valid.lastIndex] = 0xd9.toByte()
        assertTrue(RemotePhotoPolicy.validJpeg(valid))
        assertFalse(RemotePhotoPolicy.validJpeg(ByteArray(511)))
        assertFalse(RemotePhotoPolicy.validJpeg(ByteArray(RemotePhotoPolicy.MAX_JPEG_BYTES + 1)))
        valid[0] = 0
        assertFalse(RemotePhotoPolicy.validJpeg(valid))
    }
}
