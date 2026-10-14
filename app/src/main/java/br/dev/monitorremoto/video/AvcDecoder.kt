package br.dev.monitorremoto.video

import android.graphics.Bitmap
import android.graphics.Color
import android.media.Image
import android.media.MediaCodec
import android.media.MediaFormat
import java.nio.ByteBuffer

/** Decodificação AVC por codec do aparelho. Nenhum conteúdo em disco. */
class AvcDecoder(private val width: Int, private val height: Int,
    sps: ByteArray?, pps: ByteArray?) {
    private val codec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
    private val info = MediaCodec.BufferInfo()
    init {
        try {
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height)
            if (sps != null && sps.isNotEmpty()) format.setByteBuffer("csd-0", ByteBuffer.wrap(sps))
            if (pps != null && pps.isNotEmpty()) format.setByteBuffer("csd-1", ByteBuffer.wrap(pps))
            codec.configure(format, null, null, 0)
            codec.start()
        } catch (e: Exception) { codec.release(); throw e }
    }
    fun decode(payload: ByteArray, pts: Long): Bitmap? {
        val index = codec.dequeueInputBuffer(5_000L)
        if (index >= 0) {
            val buffer = codec.getInputBuffer(index) ?: error("Decoder sem buffer")
            require(buffer.capacity() >= payload.size)
            buffer.clear(); buffer.put(payload)
            codec.queueInputBuffer(index, 0, payload.size, pts, 0)
        }
        var result: Bitmap? = null
        repeat(6) {
            val output = codec.dequeueOutputBuffer(info, 2_000L)
            if (output >= 0) {
                try {
                    val img = codec.getOutputImage(output)
                    if (img != null) try { result = bitmap(img) } finally { img.close() }
                } finally { codec.releaseOutputBuffer(output, false) }
            } else if (output == MediaCodec.INFO_TRY_AGAIN_LATER) return result
        }
        return result
    }
    fun close() { runCatching { codec.stop() }; runCatching { codec.release() } }

    private fun bitmap(image: Image): Bitmap {
        val crop = image.cropRect
        val w = crop.width(); val h = crop.height()
        require(w in 1..1600 && h in 1..1600 && image.planes.size >= 3)
        val planes = image.planes
        val dest = IntArray(w*h)
        val yBuf = planes[0].buffer; val uBuf = planes[1].buffer; val vBuf = planes[2].buffer
        for (row in 0 until h) for (col in 0 until w) {
            val yy = row + crop.top; val xx = col + crop.left
            val Y = (yBuf.get(yy * planes[0].rowStride + xx * planes[0].pixelStride).toInt() and 255) - 16
            val U = (uBuf.get((yy/2)*planes[1].rowStride + (xx/2)*planes[1].pixelStride).toInt() and 255) - 128
            val V = (vBuf.get((yy/2)*planes[2].rowStride + (xx/2)*planes[2].pixelStride).toInt() and 255) - 128
            val cy = 298 * Y.coerceAtLeast(0)
            val r = (cy + 409*V + 128) shr 8
            val g = (cy - 100*U - 208*V + 128) shr 8
            val b = (cy + 516*U + 128) shr 8
            dest[row*w+col] = Color.rgb(r.coerceIn(0,255),g.coerceIn(0,255),b.coerceIn(0,255))
        }
        return Bitmap.createBitmap(dest, w, h, Bitmap.Config.ARGB_8888)
    }
}
