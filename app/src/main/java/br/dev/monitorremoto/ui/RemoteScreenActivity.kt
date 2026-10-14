package br.dev.monitorremoto.ui

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Base64
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import br.dev.monitorremoto.R
import br.dev.monitorremoto.core.RecentDevices
import br.dev.monitorremoto.core.RemoteGesturePolicy
import br.dev.monitorremoto.core.SettingsStore
import br.dev.monitorremoto.core.ScreenCapturePolicy
import br.dev.monitorremoto.transport.RemoteClient
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

/** Visualizador remoto com captura local sob demanda. Não grava continuamente. */
class RemoteScreenActivity : Activity() {
    companion object {
        const val EXTRA_HOST = "host"
        const val EXTRA_METHOD = "method"
        private const val REFRESH_MS = 720L
        private const val DOCUMENT_SAVE_REQUEST = 9036
    }
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var active = false
    @Volatile private var audioWanted = false
    @Volatile private var audioReady = false
    @Volatile private var touchReady = false
    private var touchMode = false
    private var isFullscreen = false
    private var sharingReady = false
    private val fetching = AtomicBoolean(false)
    private val saving = AtomicBoolean(false)
    private lateinit var picture: ImageView
    private lateinit var status: TextView
    private lateinit var stage: FrameLayout
    private lateinit var placeholder: LinearLayout
    private lateinit var fullscreenExit: ImageButton
    private lateinit var cameraButton: ImageButton
    private lateinit var audioButton: ImageButton
    private lateinit var fullscreenButton: ImageButton
    private lateinit var quickCapture: LinearLayout
    private lateinit var touchAction: LinearLayout
    private lateinit var touchLabel: TextView
    private lateinit var touchIcon: ImageView
    private lateinit var highQualityAction: LinearLayout
    private lateinit var normal: LinearLayout
    private lateinit var normalScroll: ScrollView
    private lateinit var stageSlot: LinearLayout
    private lateinit var screenRoot: FrameLayout
    private var normalStageHeight = 0
    private var bitmap: Bitmap? = null
    private var lastPreviewJpeg: ByteArray? = null
    private var lastFrameAt = 0L
    private var pendingDocumentBytes: ByteArray? = null
    private var host = ""
    private var method = "Chave salva"
    private var lastSequence = -1L
    private var audioThread: Thread? = null
    @Volatile private var audioPlayer: AudioTrack? = null
    private var gestureStart: Pair<Float, Float>? = null
    private var gestureAt = 0L
    private var requestedThisVisit = false

    private val dark: Boolean get() = when (SettingsStore.theme(this)) {
        "dark" -> true
        "system" -> (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        else -> false
    }
    private val surface get() = if (dark) Color.rgb(26, 39, 56) else Color.WHITE
    private val backgroundColor get() = if (dark) Color.rgb(14, 22, 35) else Color.rgb(248, 250, 252)
    private val ink get() = if (dark) Color.rgb(236, 242, 248) else Color.rgb(28, 39, 53)
    private val muted get() = if (dark) Color.rgb(167, 185, 202) else Color.rgb(99, 113, 130)
    private val line get() = if (dark) Color.rgb(52, 67, 84) else Color.rgb(224, 230, 237)
    private val accent get() = if (dark) Color.rgb(88, 216, 190) else Color.rgb(25, 156, 140)

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun shape(color: Int, radius: Int = 14, stroke: Boolean = false) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
        if (stroke) setStroke(dp(1), line)
    }
    private fun text(value: String, size: Float, color: Int = ink, bold: Boolean = false) =
        TextView(this).apply {
            text = value; textSize = size; setTextColor(color)
            if (bold) setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER_VERTICAL
        }

    private fun icon(id: Int, description: String, bg: Int = Color.TRANSPARENT): ImageButton =
        ImageButton(this).apply {
            setImageResource(id)
            imageTintList = ColorStateList.valueOf(ink)
            contentDescription = description
            background = shape(bg, 12)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(11), dp(11), dp(11), dp(11))
        }

    private fun rowAction(image: Int, label: String, action: () -> Unit): Triple<LinearLayout, ImageView, TextView> {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            background = shape(surface, 13, true)
            setPadding(dp(4), dp(8), dp(4), dp(8))
            isClickable = true; isFocusable = true
            setOnClickListener { action() }
        }
        val imageView = ImageView(this).apply {
            setImageResource(image)
            imageTintList = ColorStateList.valueOf(ink)
            contentDescription = label
            setPadding(dp(2), dp(2), dp(2), dp(2))
        }
        container.addView(imageView, LinearLayout.LayoutParams(dp(26), dp(26)))
        val title = text(label, 11.5f, ink).apply { gravity = Gravity.CENTER; maxLines = 1 }
        container.addView(title)
        return Triple(container, imageView, title)
    }

    private fun remoteCommand(action: String, x: Float = 0.5f, y: Float = 0.5f,
        ex: Float = x, ey: Float = y, duration: Long = 240L) {
        if (!active || !touchReady || !touchMode) return
        Thread({
            val accepted = runCatching {
                val key = RecentDevices.key(applicationContext, host) ?: error("Pareamento ausente")
                RemoteClient.touch(host, key, applicationContext, method, action, x, y, ex, ey, duration)
            }.getOrDefault(false)
            if (!accepted) main.post {
                if (active) Toast.makeText(this, "Toque recusado ou acessibilidade desativada", Toast.LENGTH_SHORT).show()
            }
        }, "MonitorRemoto-remote-gesture").start()
    }

    private fun hitPoint(x: Float, y: Float): Pair<Float, Float>? {
        val image = bitmap ?: return null
        return RemoteGesturePolicy.fitCenterPoint(x, y, picture.width, picture.height,
            image.width, image.height)
    }

    private fun clearFrame() {
        picture.setImageDrawable(null)
        bitmap?.recycle(); bitmap = null
        lastPreviewJpeg = null
        lastSequence = -1L; lastFrameAt = 0L
    }

    private val poll = object : Runnable {
        override fun run() {
            if (!active || !fetching.compareAndSet(false, true)) return
            Thread({
                var nextStatus = ""
                var nextBitmap: Bitmap? = null
                var nextJpeg: ByteArray? = null
                var nextSequence = -1L
                var clear = false
                var canTouch = false
                var canAudio = false
                var sharing = false
                try {
                    val key = RecentDevices.key(applicationContext, host)
                        ?: error("Chave não encontrada. Conecte novamente.")
                    val result = RemoteClient.screenFrame(host, key, applicationContext, method)
                    sharing = result.optBoolean("sharing")
                    canTouch = result.optBoolean("touchReady")
                    canAudio = result.optBoolean("audioReady")
                    val encoded = result.optString("jpeg")
                    if (!sharing && !requestedThisVisit) {
                        requestedThisVisit = true
                        runCatching { RemoteClient.requestScreen(host, key, applicationContext, method) }
                    }
                    if (!sharing) {
                        nextStatus = "Aguardando autorização no monitorado"
                        clear = true
                    } else if (encoded.isBlank()) {
                        nextStatus = "Sessão autorizada · aguardando imagem"
                        clear = true
                    } else {
                        require(encoded.length <= 150_000)
                        nextSequence = result.optLong("sequence", -1L)
                        if (nextSequence != lastSequence) {
                            val bytes = Base64.decode(encoded, Base64.NO_WRAP)
                            require(bytes.size <= 110_000)
                            nextBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                ?: error("Imagem JPEG inválida")
                            if (RemoteImageSaver.validJpeg(bytes)) nextJpeg = bytes
                        }
                        nextStatus = "Tela ao vivo · ~1–2 quadros/s" +
                            (if (canTouch) " · toque disponível" else "") +
                            (if (canAudio) " · áudio disponível" else "")
                    }
                } catch (e: Exception) {
                    nextStatus = "Sem imagem: ${e.message?.take(90) ?: "rede indisponível"}"
                    clear = true
                }
                main.post {
                    fetching.set(false)
                    if (!active || isFinishing || isDestroyed) { nextBitmap?.recycle(); return@post }
                    status.text = nextStatus
                    sharingReady = sharing && !clear
                    touchReady = canTouch && !clear
                    audioReady = canAudio && !clear
                    if (!touchReady) touchMode = false
                    if (!audioReady && audioWanted) stopAudio()
                    if (nextBitmap != null) {
                        picture.setImageBitmap(nextBitmap)
                        bitmap?.recycle()
                        bitmap = nextBitmap
                        lastPreviewJpeg = nextJpeg
                        lastFrameAt = SystemClock.elapsedRealtime()
                        lastSequence = nextSequence
                    } else if (clear) clearFrame()
                    placeholder.visibility = if (bitmap == null) View.VISIBLE else View.GONE
                    updateControls()
                    main.postDelayed(this, REFRESH_MS)
                }
            }, "MonitorRemoto-screen-poll").start()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        host = intent.getStringExtra(EXTRA_HOST).orEmpty()
        method = intent.getStringExtra(EXTRA_METHOD)?.take(72) ?: method
        if (host.isBlank() || RecentDevices.key(this, host) == null) { finish(); return }
        window.statusBarColor = backgroundColor
        window.navigationBarColor = backgroundColor
        if (!dark && Build.VERSION.SDK_INT >= 26) {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
                View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
        screenRoot = FrameLayout(this).apply { setBackgroundColor(backgroundColor) }
        normal = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(14))
        }
        normalScroll = ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
        }
        normalScroll.addView(normal)
        screenRoot.addView(normalScroll, FrameLayout.LayoutParams(-1, -1))
        val toolbar = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val back = icon(R.drawable.ic_remote_back, "Voltar")
        back.setOnClickListener { finish() }
        toolbar.addView(back, LinearLayout.LayoutParams(dp(44), dp(46)))
        toolbar.addView(text("Ver tela", 21f, ink, true), LinearLayout.LayoutParams(0, dp(48), 1f))
        cameraButton = icon(R.drawable.ic_remote_camera, "Salvar print na galeria", surface)
        cameraButton.setOnClickListener { saveQuickFrame() }
        cameraButton.setOnLongClickListener { saveHighQuality(); true }
        toolbar.addView(cameraButton, LinearLayout.LayoutParams(dp(42), dp(42)))
        audioButton = icon(R.drawable.ic_remote_volume, "Ouvir áudio interno", surface)
        audioButton.setOnClickListener { if (audioWanted) stopAudio() else startAudio() }
        toolbar.addView(audioButton, LinearLayout.LayoutParams(dp(42), dp(42)))
        fullscreenButton = icon(R.drawable.ic_remote_fullscreen, "Abrir tela cheia", surface)
        fullscreenButton.setOnClickListener { toggleFullscreen() }
        toolbar.addView(fullscreenButton, LinearLayout.LayoutParams(dp(42), dp(42)))
        normal.addView(toolbar)

        status = text("Conectando ao monitorado...", 12.5f, muted).apply {
            maxLines = 2; setPadding(dp(3), dp(9), 0, dp(11))
        }
        normal.addView(status)

        stageSlot = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        stage = FrameLayout(this).apply {
            background = shape(Color.rgb(9, 16, 24), 22)
            clipToOutline = true
        }
        picture = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Visualização da tela do celular monitorado"
            setOnTouchListener { _, event ->
                if (!touchMode || !touchReady || bitmap == null) return@setOnTouchListener false
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        gestureStart = hitPoint(event.x, event.y)
                        gestureAt = SystemClock.elapsedRealtime()
                        gestureStart != null
                    }
                    MotionEvent.ACTION_UP -> {
                        val start = gestureStart
                        gestureStart = null
                        val end = hitPoint(event.x, event.y)
                        if (start != null && end != null) {
                            val duration = (SystemClock.elapsedRealtime() - gestureAt).coerceIn(60L, 1200L)
                            val move = abs(end.first - start.first) + abs(end.second - start.second)
                            if (move > 0.025f) remoteCommand("swipe", start.first, start.second,
                                end.first, end.second, duration)
                            else if (duration >= 550L) remoteCommand("longPress", start.first, start.second,
                                duration = duration)
                            else remoteCommand("tap", start.first, start.second)
                        }
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> { gestureStart = null; true }
                    else -> true
                }
            }
        }
        stage.addView(picture, FrameLayout.LayoutParams(-1, -1))
        placeholder = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            isClickable = false
        }
        placeholder.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_remote_phone)
            imageTintList = ColorStateList.valueOf(Color.rgb(158, 169, 179))
            contentDescription = null
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        placeholder.addView(text("Tela remota", 15f, Color.rgb(174, 183, 193)).apply {
            gravity = Gravity.CENTER; setPadding(0, dp(12), 0, 0)
        })
        stage.addView(placeholder, FrameLayout.LayoutParams(-1, -1))
        fullscreenExit = icon(R.drawable.ic_remote_exitfullscreen, "Sair da tela cheia", Color.rgb(243, 246, 248)).apply {
            visibility = View.GONE
            setOnClickListener { toggleFullscreen() }
        }
        stage.addView(fullscreenExit, FrameLayout.LayoutParams(dp(48), dp(48),
            Gravity.TOP or Gravity.END).apply { setMargins(0, dp(18), dp(18), 0) })
        normalStageHeight = minOf((resources.displayMetrics.heightPixels * 0.59f).toInt(),
            (resources.displayMetrics.widthPixels * 1.44f).toInt())
        stageSlot.addView(stage, LinearLayout.LayoutParams(-1, normalStageHeight))
        normal.addView(stageSlot)

        val actions = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val touch = rowAction(R.drawable.ic_remote_hand, "Toque", {
            if (!touchReady) return@rowAction
            touchMode = !touchMode
            updateControls()
        })
        touchAction = touch.first; touchIcon = touch.second; touchLabel = touch.third
        actions.addView(touchAction, LinearLayout.LayoutParams(0, dp(77), 1f).apply { rightMargin = dp(6) })
        val backAction = rowAction(R.drawable.ic_remote_back, "Voltar", { remoteCommand("back") })
        actions.addView(backAction.first, LinearLayout.LayoutParams(0, dp(77), 1f).apply { rightMargin = dp(6) })
        val homeAction = rowAction(R.drawable.ic_remote_home, "Início", { remoteCommand("home") })
        actions.addView(homeAction.first, LinearLayout.LayoutParams(0, dp(77), 1f).apply { rightMargin = dp(6) })
        val highQuality = rowAction(R.drawable.ic_remote_save, "Foto HD", { saveHighQuality() })
        highQualityAction = highQuality.first
        actions.addView(highQualityAction, LinearLayout.LayoutParams(0, dp(77), 1f))
        normal.addView(actions, LinearLayout.LayoutParams(-1, dp(77)).apply { topMargin = dp(12) })
        normal.addView(text(
            "Toque em 📷 para salvar a prévia. Use Alta qualidade para solicitar uma captura mais nítida. " +
                "A sessão deve estar autorizada no outro aparelho.", 11.5f, muted).apply {
                setPadding(dp(3), dp(12), dp(3), dp(3)); maxLines = 4
            })
        setContentView(screenRoot)
        updateControls()
    }

    private fun updateControls() {
        if (!::touchAction.isInitialized) return
        touchAction.isEnabled = touchReady
        touchAction.alpha = if (touchReady) 1f else 0.48f
        touchLabel.text = if (touchMode) "Toque ligado" else "Toque"
        touchIcon.imageTintList = ColorStateList.valueOf(if (touchMode) accent else ink)
        val fresh = lastPreviewJpeg != null && sharingReady &&
            ScreenCapturePolicy.freshPreview(SystemClock.elapsedRealtime() - lastFrameAt)
        cameraButton.isEnabled = fresh && !saving.get()
        cameraButton.alpha = if (cameraButton.isEnabled) 1f else 0.42f
        highQualityAction.isEnabled = sharingReady && !saving.get()
        highQualityAction.alpha = if (highQualityAction.isEnabled) 1f else 0.42f
        audioButton.isEnabled = audioReady
        audioButton.alpha = if (audioReady) 1f else 0.42f
        audioButton.setImageResource(if (audioWanted) R.drawable.ic_remote_mute else R.drawable.ic_remote_volume)
        audioButton.contentDescription = if (audioWanted) "Silenciar áudio remoto" else "Ouvir áudio remoto"
    }

    private fun toggleFullscreen() {
        isFullscreen = !isFullscreen
        if (isFullscreen) {
            stageSlot.removeView(stage)
            normalScroll.visibility = View.GONE
            stage.background = shape(Color.BLACK, 0)
            stage.clipToOutline = false
            screenRoot.addView(stage, FrameLayout.LayoutParams(-1, -1))
            fullscreenExit.visibility = View.VISIBLE
            if (Build.VERSION.SDK_INT >= 30) {
                window.insetsController?.apply {
                    systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    hide(WindowInsets.Type.systemBars())
                }
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            }
        } else {
            screenRoot.removeView(stage)
            stage.background = shape(Color.rgb(9, 16, 24), 22)
            stage.clipToOutline = true
            fullscreenExit.visibility = View.GONE
            stageSlot.addView(stage, LinearLayout.LayoutParams(-1, normalStageHeight))
            normalScroll.visibility = View.VISIBLE
            if (Build.VERSION.SDK_INT >= 30) window.insetsController?.show(WindowInsets.Type.systemBars())
            else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = if (dark) 0 else
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            }
        }
    }

    private fun saveQuickFrame() {
        val image = lastPreviewJpeg
        if (!active || !sharingReady || image == null ||
            !ScreenCapturePolicy.freshPreview(SystemClock.elapsedRealtime() - lastFrameAt)) {
            toast("Aguarde um quadro recente da tela remota")
            return
        }
        saveImage(image.copyOf())
    }

    private fun saveHighQuality() {
        if (!active || !sharingReady) { toast("Compartilhamento não está ativo"); return }
        if (!saving.compareAndSet(false, true)) return
        updateControls()
        Thread({
            val result = runCatching {
                val key = RecentDevices.key(applicationContext, host) ?: error("Pareamento indisponível")
                RemoteClient.screenCapture(host, key, applicationContext, method)
            }
            main.post {
                saving.set(false)
                if (!isDestroyed && !isFinishing) {
                    result.onSuccess { saveImage(it) }
                        .onFailure { toast("Captura indisponível: ${it.message?.take(85)}") }
                    updateControls()
                }
            }
        }, "MonitorRemoto-still-capture").start()
    }

    private fun saveImage(bytes: ByteArray) {
        if (!RemoteImageSaver.validJpeg(bytes)) { toast("Imagem inválida ou incompleta"); return }
        if (!saving.compareAndSet(false, true)) return
        updateControls()
        val name = RemoteImageSaver.fileName()
        if (Build.VERSION.SDK_INT < 29) {
            pendingDocumentBytes = bytes
            saving.set(false)
            updateControls()
            startActivityForResult(RemoteImageSaver.documentIntent(name), DOCUMENT_SAVE_REQUEST)
            return
        }
        Thread({
            val result = runCatching { RemoteImageSaver.save(applicationContext, bytes, name) }
            main.post {
                saving.set(false)
                if (!isFinishing && !isDestroyed) {
                    toast(if (result.isSuccess) "Imagem salva em Imagens/Monitor Remoto"
                        else "Falha ao salvar: ${result.exceptionOrNull()?.message?.take(70)}")
                    updateControls()
                }
            }
        }, "MonitorRemoto-save-image").start()
    }

    @Deprecated("Compatibilidade Android 8/9; MediaStore é usado no Android 10+.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != DOCUMENT_SAVE_REQUEST) return
        val bytes = pendingDocumentBytes
        pendingDocumentBytes = null
        if (resultCode != RESULT_OK || data?.data == null || bytes == null) return
        val result = runCatching {
            contentResolver.openOutputStream(data.data!!, "w")?.use { it.write(bytes) }
                ?: error("Destino indisponível")
        }
        toast(if (result.isSuccess) "Imagem salva" else "Falha ao salvar a imagem")
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    private fun startAudio() {
        if (!active || !audioReady || audioWanted) return
        audioWanted = true
        updateControls()
        audioThread = Thread({
            var track: AudioTrack? = null
            try {
                track = AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                    .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(16_000).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .setBufferSizeInBytes(maxOf(32_000, AudioTrack.getMinBufferSize(16_000,
                        AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT))).build()
                require(track.state == AudioTrack.STATE_INITIALIZED)
                audioPlayer = track
                track.play()
                var last = -1L
                while (active && audioWanted && audioReady) {
                    val key = RecentDevices.key(applicationContext, host) ?: break
                    val answer = RemoteClient.audioChunk(host, key, applicationContext, method)
                    if (!answer.optBoolean("enabled")) break
                    val seq = answer.optLong("sequence", -1L)
                    val payload = answer.optString("pcm")
                    if (seq != -1L && seq != last && payload.isNotBlank()) {
                        val bytes = Base64.decode(payload, Base64.NO_WRAP)
                        if (bytes.size in 2..16_000 && bytes.size % 2 == 0) {
                            track.write(bytes, 0, bytes.size, AudioTrack.WRITE_BLOCKING)
                            last = seq
                        }
                    }
                    Thread.sleep(25L)
                }
            } catch (_: Exception) {
                // A desconexão não reinicia a projeção no monitorado.
            } finally {
                runCatching { track?.pause(); track?.flush(); track?.release() }
                if (audioPlayer === track) audioPlayer = null
                main.post {
                    if (audioWanted) {
                        audioWanted = false
                        if (::audioButton.isInitialized) updateControls()
                    }
                }
            }
        }, "MonitorRemoto-audio-listener").apply { isDaemon = true; start() }
    }
    private fun stopAudio() {
        audioWanted = false
        if (::audioButton.isInitialized) updateControls()
        runCatching { audioPlayer?.pause(); audioPlayer?.flush() }
        audioThread?.interrupt()
        audioThread = null
    }

    override fun onResume() {
        super.onResume()
        active = host.isNotBlank() && ::picture.isInitialized
        if (isFullscreen && Build.VERSION.SDK_INT >= 30)
            window.insetsController?.hide(WindowInsets.Type.systemBars())
        main.removeCallbacks(poll); main.post(poll)
    }
    override fun onPause() {
        active = false; sharingReady = false; touchMode = false; touchReady = false; audioReady = false
        stopAudio()
        main.removeCallbacks(poll)
        if (::picture.isInitialized) clearFrame()
        if (::placeholder.isInitialized) placeholder.visibility = View.VISIBLE
        super.onPause()
    }
    override fun onDestroy() {
        main.removeCallbacks(poll); stopAudio()
        if (::picture.isInitialized) clearFrame()
        super.onDestroy()
    }
    @Deprecated("Usar retorno nativo enquanto atividade está em tela cheia.")
    override fun onBackPressed() {
        if (isFullscreen) toggleFullscreen() else super.onBackPressed()
    }
}
