package br.dev.monitorremoto.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.SystemClock
import br.dev.monitorremoto.R
import br.dev.monitorremoto.core.SettingsStore
import br.dev.monitorremoto.core.RemotePhotoPolicy
import br.dev.monitorremoto.ui.RemotePhotoSetupActivity
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Uma sessão de câmera em primeiro plano iniciada pelo usuário no monitorado.
 * Não transmite vídeo, não captura sem pedido remoto e não reinicia por boot/recovery.
 * Requer CAMERA + atividade visível no instante de armar, mesmo com permissão já concedida.
 */
class PhotoReadyService : Service() {
    data class PhotoResult(val jpeg: ByteArray? = null, val error: String = "")

    companion object {
        const val ACTION_START = "br.dev.monitorremoto.photo.ARM"
        const val ACTION_STOP = "br.dev.monitorremoto.photo.STOP"
        private const val CHANNEL = "monitor_remoto_foto_armada"
        private const val NOTIFICATION_ID = 94
        @Volatile private var current: PhotoReadyService? = null
        @Volatile var armed: Boolean = false
            private set

        fun stop(context: android.content.Context) {
            armed = false
            current = null
            runCatching { context.stopService(Intent(context, PhotoReadyService::class.java)) }
        }
        fun take(lens: String): PhotoResult {
            val service = current ?: return PhotoResult(error = "Foto remota não armada. Abra a configuração no monitorado enquanto ele estiver disponível.")
            return service.capture(lens)
        }
    }

    private val busy = AtomicBoolean(false)
    private var lastShotAt = 0L
    private lateinit var thread: HandlerThread
    private lateinit var cameraHandler: Handler

    override fun onCreate() {
        super.onCreate()
        thread = HandlerThread("MonitorRemoto-FotoUnica").also { it.start() }
        cameraHandler = Handler(thread.looper)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Câmera pronta para foto remota", NotificationManager.IMPORTANCE_LOW)
        )
    }
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != ACTION_START || !SettingsStore.isMonitorEnabled(this) ||
            !MonitorService.isRunning ||
            checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            stopSelf()
            return START_NOT_STICKY
        }
        val open = PendingIntent.getActivity(this, 94, Intent(this, RemotePhotoSetupActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 95,
            Intent(this, PhotoReadyService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val note = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_shield)
            .setContentTitle("Foto remota autorizada")
            .setContentText("Câmera pronta para fotos sob demanda. Toque para parar.")
            .setContentIntent(open)
            .setOngoing(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(Notification.Action.Builder(null, "Parar câmera remota", stop).build())
            .build()
        return try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIFICATION_ID, note, ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)
            } else startForeground(NOTIFICATION_ID, note)
            current = this
            armed = true
            START_NOT_STICKY
        } catch (_: Exception) {
            stopSelf()
            START_NOT_STICKY
        }
    }

    @Synchronized private fun capture(lens: String): PhotoResult {
        if (!armed || current !== this || !SettingsStore.isMonitorEnabled(this) || !MonitorService.isRunning) {
            return PhotoResult(error = "Sessão fotográfica indisponível.")
        }
        if (!RemotePhotoPolicy.validLens(lens)) return PhotoResult(error = "Lente inválida.")
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return PhotoResult(error = "Permissão de câmera revogada.")
        }
        if (!busy.compareAndSet(false, true)) return PhotoResult(error = "Fotografia anterior ainda em andamento.")
        try {
            val now = SystemClock.elapsedRealtime()
            if (!RemotePhotoPolicy.canRequest(now, lastShotAt)) return PhotoResult(error = "Aguarde dois segundos antes de outra foto.")
            lastShotAt = now
            if (br.dev.monitorremoto.core.HealthAlertMonitor.protectionTriggered(this)) {
                return PhotoResult(error = "Captura bloqueada pela proteção térmica.")
            }
            return captureStillCamera2(lens)
        } finally { busy.set(false) }
    }

    @Suppress("MissingPermission")
    private fun captureStillCamera2(lens: String): PhotoResult {
        val manager = getSystemService(CameraManager::class.java)
        val desired = if (lens == "Frontal") CameraCharacteristics.LENS_FACING_FRONT else CameraCharacteristics.LENS_FACING_BACK
        val cameraId = manager.cameraIdList.firstOrNull {
            manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == desired
        } ?: return PhotoResult(error = "Câmera $lens não encontrada.")
        val properties = manager.getCameraCharacteristics(cameraId)
        val sizes = properties.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            ?.getOutputSizes(ImageFormat.JPEG).orEmpty()
        val size = sizes.filter { it.width >= 640 && it.height >= 480 &&
            it.width <= 2560 && it.height <= 2560 }
            .maxByOrNull { it.width.toLong() * it.height }
            ?: sizes.minByOrNull { it.width.toLong() * it.height }
            ?: return PhotoResult(error = "Câmera não oferece fotografia JPEG.")

        val latch = CountDownLatch(1)
        val cancelled = AtomicBoolean(false)
        val data = AtomicReference<ByteArray?>(null)
        val issue = AtomicReference("A captura não foi concluída.")
        var reader: ImageReader? = null
        var device: CameraDevice? = null
        var session: CameraCaptureSession? = null
        try {
            reader = ImageReader.newInstance(size.width, size.height, ImageFormat.JPEG, 2)
            val output = reader
            output.setOnImageAvailableListener({ source ->
                try {
                    if (cancelled.get()) return@setOnImageAvailableListener
                    val image = source.acquireLatestImage()
                    if (image != null) {
                        try {
                            val bytes = image.planes[0].buffer
                            val original = ByteArray(bytes.remaining())
                            bytes.get(original)
                            data.set(compactJpeg(original))
                            issue.set("")
                        } finally { image.close() }
                    }
                } catch (_: Exception) { issue.set("Não foi possível preparar o JPEG.") }
                finally { latch.countDown() }
            }, cameraHandler)
            manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(opened: CameraDevice) {
                    device = opened
                    if (cancelled.get() || !armed || current !== this@PhotoReadyService) {
                        issue.set("Sessão cancelada."); latch.countDown(); opened.close(); return
                    }
                    try {
                        opened.createCaptureSession(listOf(output.surface), object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(ready: CameraCaptureSession) {
                                session = ready
                                if (cancelled.get() || !armed || current !== this@PhotoReadyService) {
                                    ready.close(); latch.countDown(); return
                                }
                                try {
                                    val request = opened.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
                                    request.addTarget(output.surface)
                                    request.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                                    properties.get(CameraCharacteristics.SENSOR_ORIENTATION)?.let {
                                        request.set(CaptureRequest.JPEG_ORIENTATION, it)
                                    }
                                    ready.capture(request.build(), object : CameraCaptureSession.CaptureCallback() {
                                        override fun onCaptureFailed(s: CameraCaptureSession, r: CaptureRequest,
                                            failure: android.hardware.camera2.CaptureFailure) {
                                            issue.set("Falha de captura Camera2."); latch.countDown()
                                        }
                                    }, cameraHandler)
                                } catch (_: Exception) { issue.set("Disparo Camera2 recusado."); latch.countDown() }
                            }
                            override fun onConfigureFailed(failed: CameraCaptureSession) {
                                issue.set("Câmera não preparou a sessão."); latch.countDown()
                            }
                        }, cameraHandler)
                    } catch (_: Exception) { issue.set("Não foi possível abrir a sessão fotográfica."); latch.countDown() }
                }
                override fun onDisconnected(disconnected: CameraDevice) {
                    issue.set("Câmera desconectada."); latch.countDown(); disconnected.close()
                }
                override fun onError(broken: CameraDevice, error: Int) {
                    issue.set("Câmera indisponível ($error). Pode estar em uso por outro aplicativo.")
                    latch.countDown(); broken.close()
                }
            }, cameraHandler)
            if (!latch.await(12, TimeUnit.SECONDS)) return PhotoResult(error = "Tempo esgotado ao tirar a foto. O Android pode ter bloqueado a câmera.")
            val jpeg = data.get() ?: return PhotoResult(error = issue.get())
            if (!armed || current !== this || !SettingsStore.isMonitorEnabled(this))
                return PhotoResult(error = "Sessão encerrada durante a fotografia.")
            return PhotoResult(jpeg = jpeg)
        } catch (ex: SecurityException) {
            return PhotoResult(error = "Android bloqueou a câmera nesta sessão.")
        } catch (ex: Exception) {
            return PhotoResult(error = ex.message?.take(120) ?: "Câmera indisponível.")
        } finally {
            cancelled.set(true)
            runCatching { reader?.setOnImageAvailableListener(null, null) }
            runCatching { session?.close() }
            runCatching { device?.close() }
            runCatching { reader?.close() }
        }
    }

    /** JPEG único, nunca vídeo. Mantém o envelope AES-GCM limitado para uso na LAN. */
    private fun compactJpeg(original: ByteArray): ByteArray {
        val bitmap = BitmapFactory.decodeByteArray(original, 0, original.size)
            ?: throw IllegalArgumentException("JPEG inválido")
        var scaled = bitmap
        try {
            val longest = maxOf(bitmap.width, bitmap.height)
            if (longest > 1600) {
                val ratio = 1600f / longest
                scaled = Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt().coerceAtLeast(1),
                    (bitmap.height * ratio).toInt().coerceAtLeast(1), true)
            }
            for (quality in listOf(85, 75, 65, 50)) {
                val output = ByteArrayOutputStream()
                if (scaled.compress(Bitmap.CompressFormat.JPEG, quality, output) && output.size() <= RemotePhotoPolicy.MAX_JPEG_BYTES)
                    return output.toByteArray()
            }
            throw IllegalArgumentException("Foto excedeu o tamanho permitido.")
        } finally {
            if (scaled !== bitmap) scaled.recycle()
            bitmap.recycle()
        }
    }

    override fun onDestroy() {
        if (current === this) current = null
        armed = false
        runCatching { thread.quitSafely() }
        super.onDestroy()
    }
}
