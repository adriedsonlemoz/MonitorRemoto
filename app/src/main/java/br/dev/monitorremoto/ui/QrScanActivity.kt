package br.dev.monitorremoto.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.Image
import android.media.ImageReader
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.Surface
import android.view.TextureView
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/** Leitor nativo/offline com Camera2 + ZXing Core, sem Activity de captura de terceiros. */
class QrScanActivity : Activity(), TextureView.SurfaceTextureListener {
    companion object {
        const val EXTRA_QR_RESULT = "br.dev.monitorremoto.QR_RESULT"
        private const val TAG = "MonitorRemotoQR"
        private const val MIN_DECODE_INTERVAL_MS = 400L
    }

    private lateinit var texture: TextureView
    private lateinit var status: TextView
    private var cameraThread: HandlerThread? = null
    private var cameraHandler: Handler? = null
    private var camera: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var previewSurface: Surface? = null
    @Volatile private var resumed = false
    private var opening = false
    @Volatile private var completed = false
    private var lastDecodeMs = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        texture = TextureView(this).apply { surfaceTextureListener = this@QrScanActivity }
        root.addView(texture, FrameLayout.LayoutParams(-1, -1))
        val overlay = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            setBackgroundColor(0x99000000.toInt())
        }
        status = TextView(this).apply {
            text = "Aguardando câmera…"
            setTextColor(Color.WHITE)
            textSize = 17f
        }
        overlay.addView(status, LinearLayout.LayoutParams(-1, -2))
        overlay.addView(Button(this).apply {
            text = "Cancelar leitura"
            isAllCaps = false
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(-1, -2))
        root.addView(overlay, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        startWorker()
        if (texture.isAvailable) openCamera()
    }

    override fun onPause() {
        resumed = false
        closeCamera()
        stopWorker()
        super.onPause()
    }

    private fun startWorker() {
        if (cameraThread != null) return
        cameraThread = HandlerThread("MonitorRemoto-Camera2").also { it.start() }
        cameraHandler = Handler(requireNotNull(cameraThread).looper)
    }

    private fun stopWorker() {
        cameraHandler = null
        cameraThread?.quitSafely()
        cameraThread = null
    }

    private fun updateStatus(message: String) {
        runOnUiThread {
            if (!isFinishing && !isDestroyed) status.text = message
        }
    }

    private fun showCameraError(stage: String, error: Throwable) {
        Log.e(TAG, "Falha de câmera: $stage", error) // Nunca registrar conteúdo do QR.
        updateStatus("Não foi possível usar a câmera ($stage: ${error.javaClass.simpleName}). " +
            "Volte e use 'Inserir código QR manualmente'.")
    }

    private fun openCamera() {
        if (!resumed || opening || camera != null || !texture.isAvailable) return
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            updateStatus("A câmera não está autorizada. Volte e conceda permissão ou use código manual.")
            return
        }
        try {
            val manager = getSystemService(CAMERA_SERVICE) as CameraManager
            val selected = manager.cameraIdList.firstOrNull { id ->
                manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) ==
                    CameraCharacteristics.LENS_FACING_BACK
            } ?: manager.cameraIdList.firstOrNull()
            if (selected == null) {
                updateStatus("Nenhuma câmera disponível. Use o código QR manualmente.")
                return
            }
            val configs = manager.getCameraCharacteristics(selected)
                .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val sizes = configs?.getOutputSizes(ImageFormat.YUV_420_888).orEmpty()
            val size = sizes.filter { it.width >= 320 && it.height >= 240 }
                .minByOrNull { kotlin.math.abs(it.width * it.height - 640 * 480) }
                ?: sizes.firstOrNull()
            if (size == null) {
                updateStatus("Câmera não oferece captura YUV. Use o código QR manualmente.")
                return
            }
            imageReader = ImageReader.newInstance(size.width, size.height, ImageFormat.YUV_420_888, 2)
            imageReader?.setOnImageAvailableListener({ reader -> onFrame(reader) }, cameraHandler)
            opening = true
            manager.openCamera(selected, object : CameraDevice.StateCallback() {
                override fun onOpened(device: CameraDevice) {
                    opening = false
                    if (!resumed || completed) { device.close(); return }
                    camera = device
                    configureSession(device)
                }
                override fun onDisconnected(device: CameraDevice) {
                    opening = false
                    device.close()
                    if (camera === device) camera = null
                    updateStatus("Câmera desconectada. Volte e tente novamente.")
                }
                override fun onError(device: CameraDevice, error: Int) {
                    opening = false
                    device.close()
                    if (camera === device) camera = null
                    updateStatus("Erro Android Camera2 ($error). Volte e use o código QR manualmente.")
                    Log.e(TAG, "CameraDevice erro código=$error")
                }
            }, cameraHandler)
        } catch (e: Exception) {
            opening = false
            showCameraError("abertura", e)
        }
    }

    private fun configureSession(device: CameraDevice) {
        try {
            val surfaceTexture = texture.surfaceTexture ?: run {
                updateStatus("Pré-visualização indisponível. Volte e tente novamente.")
                return
            }
            val reader = imageReader ?: return
            surfaceTexture.setDefaultBufferSize(reader.width, reader.height)
            val surface = Surface(surfaceTexture)
            previewSurface = surface
            device.createCaptureSession(listOf(surface, reader.surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(configured: CameraCaptureSession) {
                    if (!resumed || completed || camera !== device) {
                        configured.close()
                        return
                    }
                    session = configured
                    try {
                        val request = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                            addTarget(surface)
                            addTarget(reader.surface)
                            set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                        }
                        configured.setRepeatingRequest(request.build(), null, cameraHandler)
                        updateStatus("Aponte a câmera para o QR do celular monitorado")
                    } catch (e: Exception) {
                        showCameraError("pré-visualização", e)
                    }
                }
                override fun onConfigureFailed(configured: CameraCaptureSession) {
                    configured.close()
                    updateStatus("Câmera não iniciou a pré-visualização. Use o código QR manualmente.")
                }
            }, cameraHandler)
        } catch (e: Exception) {
            showCameraError("sessão", e)
        }
    }

    private fun onFrame(reader: ImageReader) {
        var image: Image? = null
        try {
            image = reader.acquireLatestImage() ?: return
            val now = SystemClock.elapsedRealtime()
            if (completed || !resumed || now - lastDecodeMs < MIN_DECODE_INTERVAL_MS) return
            lastDecodeMs = now
            val data = grayPlane(image) ?: return
            val decoded = QrFrameDecoder.decode(data, image.width, image.height) ?: return
            completed = true
            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    setResult(RESULT_OK, Intent().putExtra(EXTRA_QR_RESULT, decoded))
                    finish()
                }
            }
        } catch (e: Exception) {
            // Um frame inválido não encerra o app; permite tentar outro frame.
            Log.w(TAG, "Frame de câmera ignorado: ${e.javaClass.simpleName}")
        } finally {
            try { image?.close() } catch (_: Exception) { }
        }
    }

    /** Copia apenas a luminância Y, respeitando padding/stride do fabricante. */
    private fun grayPlane(image: Image): ByteArray? {
        val plane = image.planes.firstOrNull() ?: return null
        val buffer = plane.buffer
        val stride = plane.rowStride
        val pixelStride = plane.pixelStride
        val width = image.width
        val height = image.height
        if (width <= 0 || height <= 0 || width.toLong() * height > 4_000_000L) return null
        val result = ByteArray(width * height)
        val start = buffer.position()
        val limit = buffer.limit()
        for (row in 0 until height) {
            for (col in 0 until width) {
                val offset = start + row * stride + col * pixelStride
                if (offset < start || offset >= limit) return null
                result[row * width + col] = buffer.get(offset)
            }
        }
        return result
    }

    private fun closeCamera() {
        opening = false
        try { session?.close() } catch (_: Exception) { }
        session = null
        try { camera?.close() } catch (_: Exception) { }
        camera = null
        try { imageReader?.close() } catch (_: Exception) { }
        imageReader = null
        try { previewSurface?.release() } catch (_: Exception) { }
        previewSurface = null
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        if (resumed) openCamera()
    }
    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) = Unit
    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        closeCamera()
        return true
    }
}
