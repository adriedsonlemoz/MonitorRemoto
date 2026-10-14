package br.dev.monitorremoto.video

import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import java.nio.ByteBuffer

/** AVC por MediaCodec, YUV_420_888 em buffers. Mantém a única VirtualDisplay no ImageReader. */
class AvcEncoder(private val width: Int, private val height: Int) {
    private val codec: MediaCodec
    private val info = MediaCodec.BufferInfo()
    private var sps: ByteArray? = null
    private var pps: ByteArray? = null
    private var requestedAt = 0L
    init {
        require(width in 64..1280 && height in 64..1280 && width % 2 == 0 && height % 2 == 0)
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            setInteger(MediaFormat.KEY_BIT_RATE, 1_000_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, 10)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
        } catch (e: Exception) { codec.release(); throw e }
    }

    fun encode(bitmap: Bitmap) {
        require(bitmap.width == width && bitmap.height == height)
        if (VideoFrameStore.syncNeeded() || SystemClock.elapsedRealtime() - requestedAt > 2_000L) {
            requestedAt = SystemClock.elapsedRealtime()
            runCatching { codec.setParameters(Bundle().apply { putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0) }) }
        }
        val index = codec.dequeueInputBuffer(1_000L)
        if (index >= 0) {
            try {
                val image = codec.getInputImage(index)
                    ?: throw IllegalStateException("Codec não fornece plano YUV flexível")
                try { writeYuv(bitmap, image) } finally { image.close() }
                codec.queueInputBuffer(index, 0, width * height * 3 / 2,
                    SystemClock.elapsedRealtimeNanos() / 1000L, 0)
            } catch (e: Exception) {
                // Input buffer precisa ser devolvido antes de abandonar o codec.
                runCatching { codec.queueInputBuffer(index, 0, 0, 0L, 0) }
                throw e
            }
        }
        drain()
    }

    private fun drain() {
        repeat(10) {
            val index = codec.dequeueOutputBuffer(info, 0L)
            when (index) {
                MediaCodec.INFO_TRY_AGAIN_LATER -> return
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val format = codec.outputFormat
                    sps = format.getByteBuffer("csd-0")?.let(::bytes)
                    pps = format.getByteBuffer("csd-1")?.let(::bytes)
                }
                else -> if (index >= 0) {
                    try {
                        val output = codec.getOutputBuffer(index)
                        if (output != null && info.size > 0) {
                            output.position(info.offset)
                            output.limit(info.offset + info.size)
                            val payload = ByteArray(info.size)
                            output.get(payload)
                            if ((info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                                // Alguns codecs fornecem apenas uma amostra CSD no lugar de csd-0/1.
                                if (sps == null) sps = payload
                            } else {
                                VideoFrameStore.publish(width, height, info.presentationTimeUs,
                                    (info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0,
                                    payload, sps, pps)
                            }
                        }
                    } finally { codec.releaseOutputBuffer(index, false) }
                }
            }
        }
    }
    fun close() { runCatching { codec.stop() }; runCatching { codec.release() } }

    private fun bytes(buffer: ByteBuffer): ByteArray {
        val copy = buffer.duplicate()
        val result = ByteArray(copy.remaining())
        copy.get(result)
        return result
    }

    private fun writeYuv(bitmap: Bitmap, image: android.media.Image) {
        val planes = image.planes
        require(planes.size >= 3) { "Sem três planos YUV" }
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val y = planes[0].buffer
        val u = planes[1].buffer
        val v = planes[2].buffer
        for (row in 0 until height) for (col in 0 until width) {
            val rgb = pixels[row * width + col]
            val r = (rgb shr 16) and 255; val g = (rgb shr 8) and 255; val b = rgb and 255
            val luma = ((66*r + 129*g + 25*b + 128) shr 8) + 16
            y.put(row * planes[0].rowStride + col * planes[0].pixelStride,
                luma.coerceIn(0,255).toByte())
        }
        for (row in 0 until height step 2) for (col in 0 until width step 2) {
            var rr = 0; var gg = 0; var bb = 0
            for (dy in 0..1) for (dx in 0..1) {
                val c = pixels[(row+dy)*width + col+dx]
                rr += (c shr 16) and 255; gg += (c shr 8) and 255; bb += c and 255
            }
            val r = rr / 4; val g = gg / 4; val b = bb / 4
            val chromaU = (((-38*r - 74*g + 112*b + 128) shr 8) + 128).coerceIn(0,255)
            val chromaV = (((112*r - 94*g - 18*b + 128) shr 8) + 128).coerceIn(0,255)
            val cy = row / 2; val cx = col / 2
            u.put(cy * planes[1].rowStride + cx * planes[1].pixelStride, chromaU.toByte())
            v.put(cy * planes[2].rowStride + cx * planes[2].pixelStride, chromaV.toByte())
        }
    }
}
