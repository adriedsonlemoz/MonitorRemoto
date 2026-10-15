package br.dev.monitorremoto.capture

import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import br.dev.monitorremoto.core.CameraStreamPolicy
import java.io.ByteArrayOutputStream

/** YUV_420_888 para JPEG, respeitando rowStride e pixelStride de cada plano. */
internal object CameraImageCodec {
    fun encode(image: Image): ByteArray? {
        if (image.format != ImageFormat.YUV_420_888) return null
        val width = image.width
        val height = image.height
        if (width !in 2..800 || height !in 2..800 || width % 2 != 0 || height % 2 != 0) return null
        if (image.planes.size != 3) return null
        val nv21 = ByteArray(width * height * 3 / 2)
        if (!copyPlane(image.planes[0], width, height, nv21, 0, 1)) return null
        // NV21 intercala V,U; não assumir layout NV21 nativo ou padding igual em fabricantes.
        if (!copyPlane(image.planes[2], width / 2, height / 2, nv21, width * height, 2)) return null
        if (!copyPlane(image.planes[1], width / 2, height / 2, nv21, width * height + 1, 2)) return null
        val out = ByteArrayOutputStream(60_000)
        YuvImage(nv21, ImageFormat.NV21, width, height, null)
            .compressToJpeg(Rect(0, 0, width, height), 46, out)
        val encoded = out.toByteArray()
        return encoded.takeIf { CameraStreamPolicy.validFrame(it) }
    }

    private fun copyPlane(plane: Image.Plane, width: Int, height: Int,
                          dst: ByteArray, offset: Int, step: Int): Boolean {
        val buffer = plane.buffer
        val start = buffer.position()
        val limit = buffer.limit()
        for (row in 0 until height) for (col in 0 until width) {
            val pos = start + row * plane.rowStride + col * plane.pixelStride
            val target = offset + (row * width + col) * step
            if (pos < start || pos >= limit || target >= dst.size) return false
            dst[target] = buffer.get(pos)
        }
        return true
    }
}
