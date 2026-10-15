package br.dev.monitorremoto.ui

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Base64
import android.view.Gravity
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import br.dev.monitorremoto.core.CameraStreamPolicy
import br.dev.monitorremoto.core.RecentDevices
import br.dev.monitorremoto.transport.RemoteClient
import java.util.concurrent.Executors

/** Somente visualiza captura já iniciada e aprovada no telefone remoto. */
class RemoteCameraActivity : Activity() {
    companion object {
        const val EXTRA_HOST = "host"
        const val EXTRA_METHOD = "method"
        private const val SAVE_DOCUMENT = 6211
    }
    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private var active = false
    private var generation = 0
    private var busy = false
    private var host = ""
    private var method = "Chave salva"
    private var lastSequence = 0L
    private var lastReceivedAt = 0L
    private var frame: ByteArray? = null
    private var pendingDocument: ByteArray? = null
    private var lastRttMs = -1L
    private var lastJpegBytes = 0
    private var observedPeriodMs = -1L
    private var cameraSession = 0L
    private var currentLens = ""
    private var switchAllowed = false
    private var pendingLens: String? = null
    private var switchRequestedAt = 0L
    private lateinit var switchCameraButton: Button
    private lateinit var picture: ImageView
    private lateinit var status: TextView
    private lateinit var save: Button
    private fun dp(x: Int) = (x * resources.displayMetrics.density).toInt()
    private val poll = object : Runnable { override fun run() { requestFrame() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        host = intent.getStringExtra(EXTRA_HOST).orEmpty()
        method = intent.getStringExtra(EXTRA_METHOD) ?: "Chave salva"
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            setBackgroundColor(Color.rgb(17, 25, 37))
        }
        body.addView(TextView(this).apply {
            text = "Câmera remota"
            textSize = 19f
            setTextColor(Color.WHITE)
        })
        status = TextView(this).apply {
            text = "Aguardando o monitorado abrir Câmera compartilhada…"
            textSize = 13f
            setTextColor(Color.LTGRAY)
            setPadding(0, dp(9), 0, dp(12))
        }
        body.addView(status)
        picture = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.BLACK)
            contentDescription = "Prévia JPEG da câmera do aparelho monitorado"
        }
        body.addView(picture, LinearLayout.LayoutParams(-1, 0, 1f))
        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        save = Button(this).apply { text = "Salvar foto"; isAllCaps = false; isEnabled = false; setOnClickListener { saveStill() } }
        buttons.addView(save, LinearLayout.LayoutParams(0, dp(54), 1f))
        switchCameraButton = Button(this).apply {
            text = "Trocar câmera"; isAllCaps = false; isEnabled = false
            setOnClickListener { requestSwitch() }
        }
        buttons.addView(switchCameraButton, LinearLayout.LayoutParams(0, dp(54), 1f))
        body.addView(buttons)
        body.addView(Button(this).apply {
            text = "Voltar"; isAllCaps = false; setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(-1, dp(48)))
        body.addView(TextView(this).apply {
            text = "JPEG sob demanda (~1 quadro/s). Sem áudio ou gravação. A troca remota só fica disponível se o monitorado marcar a autorização nesta sessão, mantendo a câmera aberta e visível."
            textSize = 12f
            setTextColor(Color.LTGRAY)
            setPadding(0, dp(9), 0, 0)
        })
        setContentView(body)
    }

    override fun onResume() {
        super.onResume()
        active = true
        generation++
        handler.post(poll)
    }

    override fun onPause() {
        active = false
        generation++
        handler.removeCallbacks(poll)
        clearPreview()
        cameraSession = 0L
        currentLens = ""
        switchAllowed = false
        pendingLens = null
        updateSwitchButton()
        super.onPause()
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun clearPreview() {
        frame = null
        lastReceivedAt = 0L
        lastSequence = 0L
        lastJpegBytes = 0
        observedPeriodMs = -1L
        picture.setImageDrawable(null)
        save.isEnabled = false
    }

    private fun updateSwitchButton() {
        if (!::switchCameraButton.isInitialized) return
        val target = if (currentLens == "Traseira") "Frontal" else "Traseira"
        switchCameraButton.text = if (pendingLens != null) "Trocando…" else "Usar $target"
        switchCameraButton.isEnabled = active && switchAllowed && cameraSession > 0L &&
            currentLens in setOf("Frontal", "Traseira") && pendingLens == null
    }

    private fun requestSwitch() {
        if (!active || !switchAllowed || pendingLens != null || cameraSession <= 0L) return
        val target = if (currentLens == "Traseira") "Frontal" else "Traseira"
        val capturedSession = cameraSession
        val callGeneration = generation
        pendingLens = target
        switchRequestedAt = SystemClock.elapsedRealtime()
        status.text = "Solicitando câmera $target…"
        updateSwitchButton()
        executor.execute {
            val result = runCatching {
                val key = RecentDevices.key(this, host) ?: error("Chave deste aparelho não está disponível.")
                RemoteClient.cameraSwitch(host, key, this, method, capturedSession, target)
            }
            handler.post {
                if (!active || callGeneration != generation || isFinishing || isDestroyed) return@post
                result.fold(onSuccess = {
                    if (pendingLens == target) status.text = "Solicitação recebida. Aguardando confirmação da câmera…"
                }, onFailure = {
                    if (pendingLens == target) pendingLens = null
                    status.text = "Troca não realizada: ${it.message ?: it.javaClass.simpleName}"
                })
                updateSwitchButton()
            }
        }
    }

    private fun requestFrame() {
        if (!active || busy) return
        val callGeneration = generation
        busy = true
        val knownSequence = lastSequence
        executor.execute {
            val startedAt = SystemClock.elapsedRealtime()
            val result = runCatching {
                val key = RecentDevices.key(this, host) ?: error("Chave deste aparelho não está disponível.")
                RemoteClient.cameraFrame(host, key, this, method, knownSequence)
            }
            val measuredRttMs = SystemClock.elapsedRealtime() - startedAt
            handler.post {
                busy = false
                if (!active || callGeneration != generation || isFinishing || isDestroyed) {
                    // Uma resposta antiga não deve impedir o polling ao voltar à Activity.
                    if (active && !isFinishing) handler.post(poll)
                    return@post
                }
                result.fold(onSuccess = { response ->
                    lastRttMs = measuredRttMs
                    val running = response.optBoolean("active", false)
                    val age = response.optLong("frameAgeMs", -1L)
                    val base64 = response.optString("jpeg", "")
                    val sequence = response.optLong("sequence", 0L)
                    val isFresh = running && age in 0..CameraStreamPolicy.MAX_FRAME_AGE_MS
                    val newSession = response.optLong("sessionToken", 0L)
                    val newLens = response.optString("lens", "")
                    if (cameraSession != 0L && newSession != cameraSession) clearPreview()
                    cameraSession = if (running) newSession else 0L
                    currentLens = if (running) newLens else ""
                    switchAllowed = running && response.optBoolean("remoteSwitchAllowed", false)
                    if (!running || !switchAllowed) pendingLens = null
                    if (pendingLens != null && currentLens == pendingLens && newSession > 0L) {
                        pendingLens = null // confirmada somente após o status Camera2 mudar
                    } else if (pendingLens != null &&
                        SystemClock.elapsedRealtime() - switchRequestedAt > 12_000L) pendingLens = null
                    if (isFresh && base64.isNotBlank() && sequence != lastSequence) {
                        val bytes = runCatching { Base64.decode(base64, Base64.NO_WRAP) }.getOrNull()
                        if (bytes != null && CameraStreamPolicy.validFrame(bytes)) {
                            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            if (bitmap != null) {
                                picture.setImageBitmap(bitmap)
                                frame = bytes
                                lastSequence = sequence
                                val receivedNow = SystemClock.elapsedRealtime()
                                if (lastReceivedAt > 0L && receivedNow > lastReceivedAt)
                                    observedPeriodMs = receivedNow - lastReceivedAt
                                lastReceivedAt = receivedNow
                                lastJpegBytes = bytes.size
                            }
                        }
                    }
                    if (!running || !isFresh || SystemClock.elapsedRealtime() - lastReceivedAt > CameraStreamPolicy.MAX_FRAME_AGE_MS) {
                        clearPreview()
                    }
                    save.isEnabled = frame != null
                    updateSwitchButton()
                    val network = if (lastRttMs >= 0L) " · resposta ${lastRttMs} ms" else ""
                    status.text = when {
                        pendingLens != null -> "Aguardando troca para $pendingLens…$network"
                        !running -> "Câmera fechada. Inicie no Modo monitorado do outro aparelho."
                        frame == null && age > CameraStreamPolicy.MAX_FRAME_AGE_MS ->
                            "Câmera ativa, mas o último quadro expirou$network"
                        frame == null -> "Câmera ativa, aguardando JPEG$network"
                        else -> {
                            val interval = if (observedPeriodMs > 0L) " · intervalo ${observedPeriodMs} ms" else ""
                            "● ${response.optString("lens", "Câmera")} · JPEG ${lastJpegBytes / 1024} KiB · quadro ${age} ms$network$interval"
                        }
                    }
                }, onFailure = {
                    clearPreview()
                    cameraSession = 0L
                    currentLens = ""
                    switchAllowed = false
                    pendingLens = null
                    updateSwitchButton()
                    status.text = "Sem confirmação da câmera: ${it.message ?: it.javaClass.simpleName}"
                })
                handler.postDelayed(poll, 1_200L)
            }
        }
    }

    private fun saveStill() {
        val bytes = frame?.copyOf() ?: return
        val age = SystemClock.elapsedRealtime() - lastReceivedAt
        if (age > CameraStreamPolicy.MAX_FRAME_AGE_MS || !CameraStreamPolicy.validFrame(bytes)) {
            clearPreview()
            Toast.makeText(this, "Imagem expirou. Aguarde um novo quadro.", Toast.LENGTH_SHORT).show()
            return
        }
        val fileName = RemoteImageSaver.fileName()
        if (Build.VERSION.SDK_INT >= 29) {
            save.isEnabled = false
            executor.execute {
                val result = runCatching { RemoteImageSaver.save(this, bytes, fileName) }
                handler.post {
                    if (!isFinishing) Toast.makeText(this, if (result.isSuccess) "Foto salva na galeria" else
                        "Erro ao salvar: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                    if (!isFinishing) save.isEnabled = frame != null
                }
            }
        } else {
            pendingDocument = bytes
            startActivityForResult(RemoteImageSaver.documentIntent(fileName), SAVE_DOCUMENT)
        }
    }

    @Deprecated("Compatibilidade com ACTION_CREATE_DOCUMENT no Android 8/9")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != SAVE_DOCUMENT) return
        val bytes = pendingDocument
        pendingDocument = null
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null || bytes == null) return
        executor.execute {
            val result = runCatching {
                contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("Destino indisponível")
            }
            handler.post {
                if (!isFinishing) Toast.makeText(this,
                    if (result.isSuccess) "Foto salva" else "Erro ao salvar: ${result.exceptionOrNull()?.message}",
                    Toast.LENGTH_LONG).show()
            }
        }
    }
}
