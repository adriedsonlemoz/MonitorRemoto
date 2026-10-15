package br.dev.monitorremoto.ui

import android.Manifest
import android.app.Activity
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
import android.view.Gravity
import android.view.Surface
import android.view.TextureView
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import br.dev.monitorremoto.capture.CameraFrameStore
import br.dev.monitorremoto.capture.CameraImageCodec
import br.dev.monitorremoto.core.CameraStreamPolicy
import br.dev.monitorremoto.core.CameraNv21Rotation
import br.dev.monitorremoto.core.SettingsStore
import br.dev.monitorremoto.service.MonitorService

/** Captura remota só existe enquanto esta Activity estiver realmente visível no monitorado. */
class CameraShareActivity : Activity(), TextureView.SurfaceTextureListener {
    private val permissionRequest = 6210
    private var resumed = false
    private var opening = false
    private var front = false
    private var worker: HandlerThread? = null
    private var workerHandler: Handler? = null
    private var camera: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    @Volatile private var imageReader: ImageReader? = null
    private var previewSurface: Surface? = null
    private var lastEncodedMs = 0L
    @Volatile private var cameraGeneration = 0
    @Volatile private var frameSessionToken = -1L
    @Volatile private var frameRotation = 0
    private lateinit var preview: TextureView
    private lateinit var status: TextView
    private lateinit var switchButton: Button

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun button(text: String, action: () -> Unit) = Button(this).apply {
        this.text = text
        isAllCaps = false
        setOnClickListener { action() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(16, 23, 34))
            setPadding(dp(14), dp(15), dp(14), dp(14))
        }
        val title = TextView(this).apply {
            text = "Câmera compartilhada · autorização local"
            textSize = 18f
            setTextColor(Color.WHITE)
        }
        body.addView(title)
        status = TextView(this).apply {
            text = "Iniciando câmera…"
            textSize = 13f
            setTextColor(Color.LTGRAY)
            setPadding(0, dp(7), 0, dp(12))
        }
        body.addView(status)
        preview = TextureView(this).apply { surfaceTextureListener = this }
        body.addView(preview, LinearLayout.LayoutParams(-1, 0, 1f))
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        switchButton = button("Trocar câmera") {
            front = !front
            closeCamera()
            openCamera()
        }
        actions.addView(switchButton, LinearLayout.LayoutParams(0, dp(52), 1f))
        actions.addView(button("Parar e voltar") { finish() }, LinearLayout.LayoutParams(0, dp(52), 1f))
        body.addView(actions)
        body.addView(TextView(this).apply {
            text = "A câmera só transmite enquanto esta tela está aberta. Quadros JPEG em memória, sem microfone ou gravação. Voltar ou bloquear a tela encerra a captura."
            textSize = 12f
            setTextColor(Color.LTGRAY)
            setPadding(0, dp(6), 0, 0)
        })
        setContentView(body)
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        worker = HandlerThread("MR-CameraShare").also { it.start() }
        workerHandler = Handler(requireNotNull(worker).looper)
        if (preview.isAvailable) ensurePermissionAndOpen()
    }

    override fun onPause() {
        resumed = false
        closeCamera()
        worker?.quitSafely()
        worker = null
        workerHandler = null
        super.onPause()
    }

    // onPause já encerra a sessão. Não limpar novamente em onDestroy: uma Activity
    // antiga destruída depois de recriação não pode apagar a nova câmera.

    private fun report(message: String) = runOnUiThread {
        if (!isFinishing && !isDestroyed) status.text = message
    }

    private fun authorized(): Boolean = resumed && !isFinishing &&
        SettingsStore.isMonitorEnabled(this) && MonitorService.isRunning

    private fun ensurePermissionAndOpen() {
        if (!authorized()) { report("Ative o monitoramento primeiro."); finish(); return }
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), permissionRequest)
        } else openCamera()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == permissionRequest) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) openCamera()
            else { report("Permissão de câmera recusada pelo Android."); finish() }
        }
    }

    private fun openCamera() {
        if (!authorized() || opening || camera != null || !preview.isAvailable) return
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return
        try {
            val manager = getSystemService(CameraManager::class.java)
            val direction = if (front) CameraCharacteristics.LENS_FACING_FRONT else CameraCharacteristics.LENS_FACING_BACK
            val cameraId = manager.cameraIdList.firstOrNull {
                manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == direction
            }
            if (cameraId == null) { report("Câmera ${if (front) "frontal" else "traseira"} não disponível."); return }
            val characteristics = manager.getCameraCharacteristics(cameraId)
            val sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)
            if (sensorOrientation == null) {
                report("Orientação do sensor não informada pelo Android.")
                return
            }
            frameRotation = CameraNv21Rotation.degrees(
                sensorOrientation, display?.rotation ?: Surface.ROTATION_0, front
            )
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val sizes = map?.getOutputSizes(ImageFormat.YUV_420_888).orEmpty()
            val selected = sizes.filter { it.width <= 800 && it.height <= 800 && it.width % 2 == 0 && it.height % 2 == 0 }
                .minByOrNull { kotlin.math.abs(it.width.toLong() * it.height - 640L * 480L) }
            if (selected == null) { report("Esta câmera não fornece quadros YUV compactos."); return }
            val generation = cameraGeneration
            imageReader = ImageReader.newInstance(selected.width, selected.height, ImageFormat.YUV_420_888, 2)
            imageReader?.setOnImageAvailableListener({ reader -> onFrame(reader, generation) }, workerHandler)
            opening = true
            manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(device: CameraDevice) {
                    if (generation != cameraGeneration) { device.close(); return }
                    opening = false
                    if (!authorized()) { device.close(); return }
                    camera = device
                    configure(device)
                }
                override fun onDisconnected(device: CameraDevice) {
                    device.close()
                    if (generation != cameraGeneration) return
                    if (camera === device) camera = null
                    opening = false
                    CameraFrameStore.stop()
                    runOnUiThread {
                        if (generation == cameraGeneration) {
                            closeCamera()
                            report("Câmera desconectada.")
                        }
                    }
                }
                override fun onError(device: CameraDevice, error: Int) {
                    device.close()
                    if (generation != cameraGeneration) return
                    if (camera === device) camera = null
                    opening = false
                    CameraFrameStore.stop()
                    runOnUiThread {
                        if (generation == cameraGeneration) {
                            closeCamera()
                            report("Android não liberou a câmera (erro $error).")
                        }
                    }
                }
            }, workerHandler)
        } catch (e: Exception) {
            closeCamera() // inclusive ImageReader alocado antes de openCamera falhar
            report("Falha na câmera: ${e.javaClass.simpleName}")
        }
    }

    private fun configure(device: CameraDevice) {
        val generation = cameraGeneration
        try {
            val texture = preview.surfaceTexture ?: return
            val reader = imageReader ?: return
            texture.setDefaultBufferSize(reader.width, reader.height)
            val surface = Surface(texture)
            previewSurface = surface
            device.createCaptureSession(listOf(surface, reader.surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(configured: CameraCaptureSession) {
                    if (generation != cameraGeneration || !authorized() || camera !== device) {
                        configured.close(); return
                    }
                    session = configured
                    try {
                        val request = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                            addTarget(surface)
                            addTarget(reader.surface)
                            set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                        }
                        frameSessionToken = CameraFrameStore.begin(if (front) "Frontal" else "Traseira")
                        lastEncodedMs = 0L
                        configured.setRepeatingRequest(request.build(), null, workerHandler)
                        report("● Câmera ${if (front) "frontal" else "traseira"} ativa · envia JPEG somente com visualizador aberto")
                    } catch (e: Exception) {
                        CameraFrameStore.stop()
                        runOnUiThread {
                            if (generation == cameraGeneration) {
                                closeCamera()
                                report("Falha ao iniciar captura: ${e.javaClass.simpleName}")
                            }
                        }
                    }
                }
                override fun onConfigureFailed(configured: CameraCaptureSession) {
                    configured.close()
                    if (generation != cameraGeneration) return
                    CameraFrameStore.stop()
                    runOnUiThread {
                        if (generation == cameraGeneration) {
                            closeCamera()
                            report("Não foi possível configurar a câmera.")
                        }
                    }
                }
            }, workerHandler)
        } catch (e: Exception) {
            CameraFrameStore.stop()
            runOnUiThread {
                if (generation == cameraGeneration) {
                    closeCamera()
                    report("Erro na prévia: ${e.javaClass.simpleName}")
                }
            }
        }
    }

    private fun onFrame(reader: ImageReader, generation: Int) {
        var image: Image? = null
        try {
            image = reader.acquireLatestImage() ?: return
            if (generation != cameraGeneration || reader !== imageReader) return
            if (!authorized()) { runOnUiThread { if (!isFinishing) finish() }; return }
            val token = frameSessionToken
            val now = SystemClock.elapsedRealtime()
            if (!CameraStreamPolicy.shouldEncode(CameraFrameStore.isActive() && CameraFrameStore.hasRecentViewer() && token > 0L,
                    now - lastEncodedMs)) return
            lastEncodedMs = now
            val rotation = frameRotation
            val encoded = CameraImageCodec.encode(image, rotation) ?: return
            if (generation == cameraGeneration && reader === imageReader && authorized()) {
                CameraFrameStore.publish(token, encoded)
            }
        } catch (_: Exception) {
            // Quadros inválidos são descartados; nunca compartilhar bytes parciais.
        } finally {
            try { image?.close() } catch (_: Exception) { }
        }
    }

    private fun closeCamera() {
        cameraGeneration++
        frameSessionToken = -1L
        CameraFrameStore.stop()
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
        if (resumed) ensurePermissionAndOpen()
    }
    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) = Unit
    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean { closeCamera(); return true }
    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
}
