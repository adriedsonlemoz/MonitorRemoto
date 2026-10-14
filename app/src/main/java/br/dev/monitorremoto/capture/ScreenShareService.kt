package br.dev.monitorremoto.capture

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioPlaybackCaptureConfiguration
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import android.view.WindowManager
import br.dev.monitorremoto.core.SettingsStore
import br.dev.monitorremoto.ui.MainActivity
import java.io.ByteArrayOutputStream

/** Projeção autorizada: a mesma sessão continua ativa durante reconexões TCP; sem reutilizar token. */
class ScreenShareService : Service() {
    companion object {
        const val ACTION_START = "br.dev.monitorremoto.SCREEN_START"
        const val ACTION_STOP = "br.dev.monitorremoto.SCREEN_STOP"
        const val ACTION_AUDIO_STOP = "br.dev.monitorremoto.SCREEN_AUDIO_STOP"
        const val EXTRA_RESULT_CODE = "projection_result_code"
        const val EXTRA_RESULT_DATA = "projection_result_data"
        private const val CHANNEL = "monitor_remoto_tela"
        private const val NOTIFICATION_ID = 58
        private const val TAG = "MonitorRemotoScreen"
        private const val FRAME_INTERVAL_MS = 700L // ~1-2 fps sob demanda; sem codificar quando ninguém assiste
        fun stop(context: Context) { context.stopService(Intent(context, ScreenShareService::class.java)) }
    }

    private var recording: AudioRecord? = null
    private var audioThread: Thread? = null
    @Volatile private var audioRunning = false
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var workerThread: HandlerThread? = null
    private var worker: Handler? = null
    private var lastFrameAt = 0L
    private var stopped = false
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Compartilhamento visível de tela", NotificationManager.IMPORTANCE_LOW)
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_AUDIO_STOP) {
            stopInternalAudio()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_STOP || intent?.action != ACTION_START ||
            !SettingsStore.isMonitorEnabled(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        // Consentimento novo é obrigatório para cada sessão; não reutilizar Intent nem MediaProjection.
        if (projection != null) return START_NOT_STICKY
        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
        @Suppress("DEPRECATION")
        val grant = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
        if (resultCode != Activity.RESULT_OK || grant == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_MONITOR, true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, ScreenShareService::class.java)
            .setAction(ACTION_STOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("Tela sendo compartilhada")
            .setContentText("Sessão ativa até tocar em Parar; reconexões não exigem nova captura.")
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Parar tela", stop).build())
            .build()
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            } else startForeground(NOTIFICATION_ID, notification)
            val manager = getSystemService(MediaProjectionManager::class.java)
            val acquired = manager.getMediaProjection(resultCode, grant) ?: error("Autorização de captura inválida")
            projection = acquired
            workerThread = HandlerThread("MonitorRemoto-screen-frame").also { it.start() }
            worker = Handler(workerThread!!.looper)
            acquired.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() { stopSelf() }
                override fun onCapturedContentResize(width: Int, height: Int) {
                    worker?.post { resizeSurface(width, height) }
                }
            }, worker)
            val metrics = resources.displayMetrics
            val wm = getSystemService(WindowManager::class.java)
            val width = if (Build.VERSION.SDK_INT >= 30) wm.maximumWindowMetrics.bounds.width() else metrics.widthPixels
            val height = if (Build.VERSION.SDK_INT >= 30) wm.maximumWindowMetrics.bounds.height() else metrics.heightPixels
            val density = metrics.densityDpi
            check(width > 0 && height > 0)
            reader = makeReader(width, height)
            ScreenFrameStore.begin()
            display = acquired.createVirtualDisplay("Monitor Remoto - tela autorizada", width, height,
                density, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader!!.surface, null, worker)
            startInternalAudio(acquired)
        } catch (error: Exception) {
            Log.e(TAG, "Não foi possível iniciar compartilhamento consentido", error)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    /** Áudio de reprodução de apps que permitem captura (Android 10+). Não usa microfone. */
    private fun startInternalAudio(grantedProjection: MediaProjection) {
        if (Build.VERSION.SDK_INT < 29 || !SettingsStore.captureInternalAudio(this) ||
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return
        try {
            val config = AudioPlaybackCaptureConfiguration.Builder(grantedProjection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .build()
            val minimum = AudioRecord.getMinBufferSize(AudioChunkStore.RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            require(minimum > 0) { "AudioRecord buffer inválido" }
            val record = AudioRecord.Builder()
                .setAudioFormat(AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(AudioChunkStore.RATE)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO).build())
                .setBufferSizeInBytes(maxOf(minimum * 2, AudioChunkStore.MAX_CHUNK * 2))
                .setAudioPlaybackCaptureConfig(config).build()
            require(record.state == AudioRecord.STATE_INITIALIZED) { "AudioRecord não iniciado" }
            record.startRecording()
            recording = record
            audioRunning = true
            AudioChunkStore.start()
            audioThread = Thread({
                val buffer = ByteArray(AudioChunkStore.MAX_CHUNK)
                while (audioRunning) {
                    val count = runCatching { record.read(buffer, 0, buffer.size,
                        AudioRecord.READ_BLOCKING) }.getOrDefault(-1)
                    if (count <= 0) break
                    AudioChunkStore.publish(buffer.copyOf(count))
                }
                AudioChunkStore.stop()
            }, "MonitorRemoto-audio-playback").apply { isDaemon = true; start() }
        } catch (error: Exception) {
            Log.w(TAG, "Áudio interno indisponível; tela continua sem áudio", error)
            stopInternalAudio()
        }
    }

    private fun stopInternalAudio() {
        audioRunning = false
        AudioChunkStore.stop()
        val old = recording
        recording = null
        runCatching { old?.stop() }
        runCatching { old?.release() }
        audioThread?.interrupt()
        audioThread = null
    }

    private fun makeReader(width: Int, height: Int): ImageReader =
        ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2).also { newReader ->
            newReader.setOnImageAvailableListener({ source ->
                val image = runCatching { source.acquireLatestImage() }.getOrNull() ?: return@setOnImageAvailableListener
                try {
                    if (!ScreenFrameStore.sharing || !ScreenFrameStore.viewerActive()) return@setOnImageAvailableListener
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastFrameAt < FRAME_INTERVAL_MS) return@setOnImageAvailableListener
                    lastFrameAt = now
                    val plane = image.planes.firstOrNull() ?: return@setOnImageAvailableListener
                    val w = image.width
                    val h = image.height
                    val pixels = plane.pixelStride
                    if (w < 1 || h < 1 || pixels < 4) return@setOnImageAvailableListener
                    val stride = plane.rowStride
                    val padded = w + (stride - pixels * w) / pixels
                    val full = Bitmap.createBitmap(padded, h, Bitmap.Config.ARGB_8888)
                    try {
                        plane.buffer.rewind()
                        val src = plane.buffer
                        src.rewind()
                        val bytesNeeded = full.byteCount
                        if (src.remaining() < bytesNeeded) {
                            val paddedBuffer = java.nio.ByteBuffer.allocateDirect(bytesNeeded)
                            paddedBuffer.put(src)
                            paddedBuffer.rewind()
                            full.copyPixelsFromBuffer(paddedBuffer)
                        } else full.copyPixelsFromBuffer(src)
                        val cropped = Bitmap.createBitmap(full, 0, 0, w, h)
                        try {
                            val targetW = w.coerceAtMost(540)
                            val targetH = (h.toDouble() * targetW / w).toInt().coerceAtLeast(1)
                            val small = Bitmap.createScaledBitmap(cropped, targetW, targetH, true)
                            try {
                                val out = ByteArrayOutputStream()
                                for (quality in listOf(55, 38, 24)) {
                                    out.reset()
                                    small.compress(Bitmap.CompressFormat.JPEG, quality, out)
                                    if (out.size() <= ScreenFrameStore.MAX_JPEG_BYTES) break
                                }
                                ScreenFrameStore.publish(targetW, targetH, out.toByteArray())
                            } finally { if (small !== cropped) small.recycle() }
                        } finally { if (cropped !== full) cropped.recycle() }
                    } finally { full.recycle() }
                } catch (e: Exception) {
                    Log.w(TAG, "Falha em frame da projeção", e)
                } finally { image.close() }
            }, worker)
        }

    private fun resizeSurface(width: Int, height: Int) {
        if (width < 1 || height < 1 || stopped || display == null) return
        try {
            val next = makeReader(width, height)
            display?.resize(width, height, resources.displayMetrics.densityDpi)
            display?.surface = next.surface
            val previous = reader
            reader = next
            previous?.setOnImageAvailableListener(null, null)
            previous?.close()
            lastFrameAt = 0L
        } catch (e: Exception) { Log.w(TAG, "Não foi possível redimensionar a projeção", e) }
    }

    override fun onDestroy() {
        stopped = true
        stopInternalAudio()
        ScreenFrameStore.stop()
        runCatching { display?.release() }
        display = null
        runCatching { reader?.setOnImageAvailableListener(null, null); reader?.close() }
        reader = null
        val oldProjection = projection
        projection = null
        runCatching { oldProjection?.stop() }
        workerThread?.quitSafely()
        workerThread = null
        worker = null
        super.onDestroy()
    }
}
