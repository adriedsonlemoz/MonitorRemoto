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
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import br.dev.monitorremoto.R
import br.dev.monitorremoto.adb.AdbScreenBridge
import br.dev.monitorremoto.adb.AdbScreenRouting
import br.dev.monitorremoto.core.DisplayedFrameRate
import br.dev.monitorremoto.core.RecentDevices
import br.dev.monitorremoto.core.RemoteGesturePolicy
import br.dev.monitorremoto.core.SettingsStore
import br.dev.monitorremoto.core.ScreenCapturePolicy
import br.dev.monitorremoto.transport.RemoteClient
import br.dev.monitorremoto.video.AvcDecoder
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

/** Visualizador remoto com captura local sob demanda. Não grava continuamente. */
class RemoteScreenActivity : Activity() {
    companion object {
        const val EXTRA_HOST = "host"
        const val EXTRA_METHOD = "method"
        const val EXTRA_ADB_HANDOFF = "adb_viewer_handoff"
        private const val REFRESH_MS = 85L // solicita próximo frame após resposta; sem filas de quadros
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
    // Reutiliza uma única thread de consulta; não cresce com o FPS solicitado.
    private val pollExecutor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "MonitorRemoto-screen-poll").apply { isDaemon = true }
    }
    private var visitGeneration = 0L
    private val saving = AtomicBoolean(false)
    private lateinit var picture: ImageView
    private lateinit var status: TextView
    private lateinit var channelBadge: TextView
    private var fromAdbHandoff = false
    @Volatile private var screenChannelAuthenticated = false
    private lateinit var stage: FrameLayout
    private lateinit var placeholder: LinearLayout
    private lateinit var fullscreenExit: ImageButton
    private lateinit var fullscreenFit: TextView
    private lateinit var fullscreenTopBar: LinearLayout
    private lateinit var fullscreenBottomBar: LinearLayout
    private lateinit var fullscreenStatus: TextView
    private lateinit var fullscreenAudio: ImageButton
    private lateinit var fullscreenTouch: ImageButton
    private lateinit var fullscreenPrint: ImageButton
    private val fullscreenNavigation = mutableListOf<ImageButton>()
    private var fillFullscreen = false
    private lateinit var cameraButton: ImageButton
    private lateinit var audioButton: ImageButton
    private lateinit var fullscreenButton: ImageButton
    private lateinit var quickCapture: LinearLayout
    private lateinit var touchAction: LinearLayout
    private lateinit var touchLabel: TextView
    private lateinit var touchIcon: ImageView
    private lateinit var backAction: LinearLayout
    private lateinit var homeAction: LinearLayout
    private lateinit var recentsAction: LinearLayout
    private lateinit var notificationsAction: LinearLayout
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
    private val videoLock = Any()
    private var videoSeq = 0L
    private var videoDecoder: AvcDecoder? = null
    private var videoSize: Pair<Int, Int>? = null
    private var videoFailUntil = 0L
    @Volatile private var preferJpeg = true
    private lateinit var jpegModeButton: Button
    private lateinit var h264ModeButton: Button
    @Volatile private var remoteDeviceLocked = false
    @Volatile private var remoteScreenInteractive = true
    private var videoMode = false
    private var videoWaitingSince = 0L
    private var videoFirstPictureAt = 0L
    private var wakeRequestedOnOpen = false
    private var audioThread: Thread? = null
    @Volatile private var audioPlayer: AudioTrack? = null
    private var gestureStart: Pair<Float, Float>? = null
    private var gestureAt = 0L
    private var requestedThisVisit = false
    private var previousStatus = ""
    // Taxa medida somente em quadros efetivamente apresentados, não na frequência pretendida.
    private val frameRate = DisplayedFrameRate()
    private var measuredFps = 0f

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

    /** Botão acessível com contraste suficiente sobre vídeo, sem fundo opaco. */
    private fun floatingButton(iconId: Int, label: String, action: () -> Unit): ImageButton =
        ImageButton(this).apply {
            setImageResource(iconId)
            imageTintList = ColorStateList.valueOf(Color.WHITE)
            contentDescription = label
            background = shape(Color.TRANSPARENT, 12)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(10), dp(10), dp(10), dp(10))
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
            setOnLongClickListener { toast(label); true }
        }

    /** Transparência aplicada apenas à superfície; os ícones permanecem legíveis. */
    private fun floatingBar(): LinearLayout = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        orientation = LinearLayout.HORIZONTAL
        background = shape(Color.argb(112, 12, 21, 32), 19)
        elevation = dp(6).toFloat()
        setPadding(dp(3), dp(2), dp(3), dp(2))
        visibility = View.GONE
    }

    private fun addFloatingButton(bar: LinearLayout, button: ImageButton, width: Int = 42) {
        bar.addView(button, LinearLayout.LayoutParams(dp(width), dp(42)))
    }

    private fun toggleRemoteTouch() {
        if (!touchReady) {
            toast(touchUnavailableExplanation())
            return
        }
        touchMode = !touchMode
        updateControls()
    }

    private fun touchUnavailableExplanation(): String = when {
        !screenChannelAuthenticated -> "Não foi possível verificar a tela remota. Confira a conexão e tente novamente."
        remoteDeviceLocked -> "Tela remota bloqueada. Desbloqueie no aparelho; o ADB não remove PIN ou biometria."
        !remoteScreenInteractive -> "Tela remota apagada. Toque em Acender tela; talvez seja necessária nova autorização de captura."
        !sharingReady -> "Compartilhamento encerrado. Autorize novamente a captura no telefone monitorado."
        else -> "Para tocar, habilite Acessibilidade no monitorado e autorize o controle remoto."
    }

    private fun selectStreamMode(jpeg: Boolean) {
        if (preferJpeg == jpeg) return
        preferJpeg = jpeg
        videoFailUntil = 0L
        videoWaitingSince = 0L
        closeVideo()
        clearFrame()
        updateStreamButtons()
        status.text = if (jpeg) "JPEG selecionado · aguardando quadro novo" else
            "H.264 experimental selecionado · pode aumentar o atraso"
    }

    private fun updateStreamButtons() {
        if (!::jpegModeButton.isInitialized || !::h264ModeButton.isInitialized) return
        listOf(jpegModeButton to true, h264ModeButton to false).forEach { (view, jpeg) ->
            val selected = preferJpeg == jpeg
            val selectedFill = if (dark) Color.rgb(24, 110, 98) else Color.rgb(7, 112, 101)
            view.background = shape(if (selected) selectedFill else surface, 14, !selected)
            view.setTextColor(if (selected) Color.WHITE else ink)
            view.alpha = if (selected) 1f else 0.9f
            view.contentDescription = (if (jpeg) "JPEG: menor atraso" else "H.264 experimental") +
                if (selected) ", selecionado" else ", selecionar"
        }
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

    private fun adbAvailable() = fromAdbHandoff && AdbScreenBridge.connectedFor(host)

    private fun updateChannelBadge() {
        if (!::channelBadge.isInitialized) return
        val enabled = adbAvailable()
        val label = AdbScreenRouting.label(enabled)
        if (channelBadge.text.toString() != label) channelBadge.text = label
        channelBadge.setTextColor(if (enabled) accent else muted)
    }

    private fun remoteCommand(action: String, x: Float = 0.5f, y: Float = 0.5f,
        ex: Float = x, ey: Float = y, duration: Long = 240L) {
        if (!active) { toast("Controle remoto desconectado"); return }
        // A imagem segue autenticada por AES-GCM/MediaProjection; o ADB só assume navegação
        // quando ambos os canais respondem para o mesmo endereço autorizado.
        val adbNav = AdbScreenRouting.navigation(action, adbAvailable(), screenChannelAuthenticated)
        if (adbNav != null) {
            AdbScreenBridge.execute(host, adbNav) { success, message ->
                if (active) {
                    if (!success) toast(message)
                    updateChannelBadge()
                    updateControls()
                }
            }
            return
        }
        if (adbAvailable() && !screenChannelAuthenticated &&
            (action == "back" || action == "home" || action == "recents")) {
            toast("Aguardando autenticação do canal de imagem antes de usar comandos ADB.")
            return
        }
        val global = RemoteGesturePolicy.isGlobalAction(action)
        if (!touchReady) { toast(touchUnavailableExplanation()); return }
        if (!global && !touchMode) { toast("Ative Toque para interagir com a imagem"); return }
        Thread({
            val result = runCatching {
                val key = RecentDevices.key(applicationContext, host) ?: error("Pareamento ausente")
                RemoteClient.touch(host, key, applicationContext, method, action, x, y, ex, ey, duration)
            }
            if (result.getOrNull() != true) main.post {
                if (active) toast("Ação não executada: ${result.exceptionOrNull()?.message?.take(80) ?: "Acessibilidade ou sessão indisponível"}")
            }
        }, "MonitorRemoto-remote-gesture").start()
    }

    private fun hitPoint(x: Float, y: Float): Pair<Float, Float>? {
        val image = bitmap ?: return null
        return if (picture.scaleType == ImageView.ScaleType.CENTER_CROP)
            RemoteGesturePolicy.centerCropPoint(x, y, picture.width, picture.height, image.width, image.height)
        else RemoteGesturePolicy.fitCenterPoint(x, y, picture.width, picture.height,
            image.width, image.height)
    }

    private fun closeVideo() = synchronized(videoLock) {
        runCatching { videoDecoder?.close() }
        videoDecoder = null; videoSize = null; videoSeq = 0L; videoMode = false
        videoWaitingSince = 0L; videoFirstPictureAt = 0L
        lastSequence = -1L
    }

    private fun wakeRemoteScreen() {
        if (!active) { toast("Conexão indisponível"); return }
        Thread({
            val result = runCatching {
                val key = RecentDevices.key(applicationContext, host) ?: error("Pareamento ausente")
                RemoteClient.wakeScreen(host, key, applicationContext, method)
            }
            main.post { if (active) toast(result.getOrNull() ?: result.exceptionOrNull()?.message.orEmpty()) }
        }, "MonitorRemoto-screen-wake").start()
    }

    private fun clearFrame() {
        picture.setImageDrawable(null)
        bitmap?.recycle(); bitmap = null
        lastPreviewJpeg = null
        lastSequence = -1L; lastFrameAt = 0L
        frameRate.reset(); measuredFps = 0f
    }

    private val poll = object : Runnable {
        override fun run() {
            if (!active || !fetching.compareAndSet(false, true)) return
            val visit = visitGeneration
            pollExecutor.execute {
                var nextStatus = ""
                var nextBitmap: Bitmap? = null
                var nextJpeg: ByteArray? = null
                var nextSequence = -1L
                var clear = false
                var deviceLocked = false
                var screenInteractive = true
                var canTouch = false
                var canAudio = false
                var sharing = false
                var codecAvailable = false
                var retryDelay = REFRESH_MS
                var authenticatedThisPoll = false
                try {
                    val key = RecentDevices.key(applicationContext, host)
                        ?: error("Chave não encontrada. Conecte novamente.")
                    val queryStart = SystemClock.elapsedRealtime()
                    val tryVideo = !preferJpeg && SystemClock.elapsedRealtime() >= videoFailUntil
                    val result = if (tryVideo) RemoteClient.videoFrame(host, key, applicationContext,
                        method, videoSeq) else RemoteClient.screenFrame(host, key, applicationContext, method)
                    val networkRoundTrip = SystemClock.elapsedRealtime() - queryStart
                    authenticatedThisPoll = true
                    val locked = result.optBoolean("deviceLocked")
                    val interactive = result.optBoolean("screenInteractive", true)
                    deviceLocked = locked
                    screenInteractive = interactive
                    val frameAge = result.optLong("frameAgeMs", -1L)
                    sharing = result.optBoolean("sharing")
                    canTouch = result.optBoolean("touchReady")
                    canAudio = result.optBoolean("audioReady")
                    codecAvailable = tryVideo && result.optBoolean("supported")
                    if (!sharing && !requestedThisVisit) {
                        requestedThisVisit = true
                        runCatching { RemoteClient.requestScreen(host, key, applicationContext, method) }
                    }
                    if (!sharing) {
                        nextStatus = when {
                            locked -> "Tela bloqueada · acenda a tela; uma nova captura precisa de autorização local"
                            !interactive -> "Tela apagada · use Acender tela; captura pode ter sido encerrada"
                            else -> "Aguardando autorização do monitorado para compartilhar a tela"
                        }
                        closeVideo()
                        clear = true
                        retryDelay = 450L
                    } else if (codecAvailable) {
                        val encoded = result.optString("avc")
                        if (encoded.isBlank()) {
                            if (videoWaitingSince == 0L) videoWaitingSince = SystemClock.elapsedRealtime()
                            if (SystemClock.elapsedRealtime() - videoWaitingSince > 4_000L) {
                                throw IllegalStateException("Codec sem quadros; alternando para JPEG")
                            }
                        } else videoWaitingSince = 0L
                        if (encoded.isNotBlank()) {
                            require(encoded.length <= 175_000) { "Quadro AVC acima do limite" }
                            val nextSeq = result.optLong("sequence", -1L)
                            val keyFrame = result.optBoolean("keyFrame")
                            val w = result.optInt("width"); val h = result.optInt("height")
                            if (!keyFrame && (videoDecoder == null || nextSeq != videoSeq + 1L)) {
                                closeVideo() // GOP incompleto: solicitar novo IDR
                            } else {
                                if (keyFrame && (videoDecoder == null || videoSize != w to h ||
                                        nextSeq != videoSeq + 1L)) {
                                    closeVideo()
                                    val sps = result.optString("sps").takeIf { it.isNotEmpty() }
                                        ?.let { Base64.decode(it, Base64.NO_WRAP) }
                                    val pps = result.optString("pps").takeIf { it.isNotEmpty() }
                                        ?.let { Base64.decode(it, Base64.NO_WRAP) }
                                    videoDecoder = AvcDecoder(w, h, sps, pps)
                                    videoSize = w to h
                                }
                                val data = Base64.decode(encoded, Base64.NO_WRAP)
                                require(data.size <= 128_000)
                                nextBitmap = synchronized(videoLock) {
                                    videoDecoder?.decode(data, result.optLong("pts", 0L))
                                }
                                videoSeq = nextSeq
                                videoMode = true
                                if (nextBitmap != null) videoFirstPictureAt = 0L
                                else {
                                    if (videoFirstPictureAt == 0L) videoFirstPictureAt = SystemClock.elapsedRealtime()
                                    if (SystemClock.elapsedRealtime() - videoFirstPictureAt > 4_000L)
                                        throw IllegalStateException("Decoder sem imagem; alternando para JPEG")
                                }
                            }
                        }
                        nextStatus = when {
                            locked -> "Tela bloqueada · controle protegido"
                            !interactive -> "Tela apagada · a projeção pode terminar"
                            else -> "H.264 · rede ${networkRoundTrip} ms" +
                                (if (frameAge >= 0) " · quadro ${frameAge} ms" else "") +
                                (if (!canTouch) " · habilite toque no monitorado" else "")
                        }
                        retryDelay = if (encoded.isNotBlank()) 35L else 95L
                    } else {
                        closeVideo()
                        val encoded = result.optString("jpeg")
                        if (encoded.isBlank()) {
                            nextStatus = "Sessão autorizada · aguardando imagem JPEG"
                            retryDelay = 230L
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
                            nextStatus = "JPEG (compatibilidade) · rede ${networkRoundTrip} ms" +
                                (if (frameAge >= 0) " · imagem ${frameAge} ms" else "")
                            retryDelay = 300L
                        }
                    }
                } catch (e: Exception) {
                    authenticatedThisPoll = false
                    nextStatus = "Visualização: ${e.message?.take(80) ?: "rede indisponível"}"
                    // Decodificador falhou: recuperar com JPEG por 30 s.
                    videoFailUntil = SystemClock.elapsedRealtime() + 30_000L
                    closeVideo()
                    retryDelay = 500L
                }
                main.post {
                    fetching.set(false)
                    if (!active || isFinishing || isDestroyed || visit != visitGeneration) {
                        nextBitmap?.recycle()
                        if (active && visit != visitGeneration && !isFinishing && !isDestroyed) main.post(poll)
                        return@post
                    }
                    screenChannelAuthenticated = authenticatedThisPoll
                    sharingReady = sharing
                    remoteDeviceLocked = deviceLocked
                    remoteScreenInteractive = screenInteractive
                    touchReady = canTouch && sharing && !deviceLocked && screenInteractive
                    audioReady = canAudio && sharing
                    if (!touchReady) touchMode = false
                    if (!audioReady && audioWanted) stopAudio()
                    if (nextBitmap != null) {
                        picture.setImageBitmap(nextBitmap)
                        bitmap?.recycle()
                        bitmap = nextBitmap
                        lastPreviewJpeg = nextJpeg
                        lastFrameAt = SystemClock.elapsedRealtime()
                        measuredFps = frameRate.record(lastFrameAt)
                        if (nextSequence >= 0) lastSequence = nextSequence
                    } else if (clear) clearFrame()
                    measuredFps = frameRate.current(SystemClock.elapsedRealtime())
                    val realStatus = if (sharing && measuredFps > 0f)
                        "$nextStatus · %.1f FPS reais".format(java.util.Locale.US, measuredFps)
                    else nextStatus
                    val transportStatus = if (adbAvailable()) "Navegação ADB conectada · $realStatus" else realStatus
                    if (transportStatus != previousStatus) {
                        status.text = realStatus
                        fullscreenStatus.text = transportStatus
                        previousStatus = transportStatus
                    }
                    updateChannelBadge()
                    placeholder.visibility = if (bitmap == null) View.VISIBLE else View.GONE
                    updateControls()
                    main.postDelayed(this, retryDelay)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        host = intent.getStringExtra(EXTRA_HOST).orEmpty()
        method = intent.getStringExtra(EXTRA_METHOD)?.take(72) ?: method
        fromAdbHandoff = intent.getBooleanExtra(EXTRA_ADB_HANDOFF, false)
        if (host.isBlank() || RecentDevices.key(this, host) == null) {
            if (fromAdbHandoff) AdbScreenBridge.close(host)
            finish()
            return
        }
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
        channelBadge = text(AdbScreenRouting.label(false), 12f, muted, true).apply {
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = shape(surface, 12, true)
            maxLines = 2
        }
        normal.addView(channelBadge, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6); bottomMargin = dp(6) })
        updateChannelBadge()
        normal.addView(text("TRANSMISSÃO DA TELA", 12f, muted, true).apply {
            setPadding(dp(4), dp(7), 0, dp(8))
        })
        val streamRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        jpegModeButton = Button(this).apply {
            text = "JPEG · rápido"
            isAllCaps = false
            minHeight = dp(46)
            elevation = 0f
            textSize = 13f
            setOnClickListener { selectStreamMode(true) }
        }
        h264ModeButton = Button(this).apply {
            text = "H.264 · teste"
            isAllCaps = false
            minHeight = dp(46)
            elevation = 0f
            textSize = 13f
            setOnClickListener { selectStreamMode(false) }
        }
        streamRow.addView(jpegModeButton, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(5) })
        streamRow.addView(h264ModeButton, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginStart = dp(5) })
        normal.addView(streamRow, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        updateStreamButtons()

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
        // Os dois painéis abaixo só são exibidos em tela cheia. A captura continua
        // preenchendo todo o FrameLayout e recebe os gestos fora da área dos botões.
        fullscreenTopBar = floatingBar()
        fullscreenExit = floatingButton(R.drawable.ic_remote_exitfullscreen, "Sair da tela cheia") {
            toggleFullscreen()
        }
        addFloatingButton(fullscreenTopBar, fullscreenExit)
        fullscreenFit = text("Preencher", 12f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            visibility = View.GONE
            isClickable = true
            isFocusable = true
            contentDescription = "Alternar entre ajustar imagem e preencher tela"
            setPadding(dp(4), 0, dp(4), 0)
            setOnClickListener {
                fillFullscreen = !fillFullscreen
                picture.scaleType = if (fillFullscreen) ImageView.ScaleType.CENTER_CROP
                    else ImageView.ScaleType.FIT_CENTER
                text = if (fillFullscreen) "Ajustar" else "Preencher"
            }
        }
        fullscreenTopBar.addView(fullscreenFit, LinearLayout.LayoutParams(dp(75), dp(42)))
        fullscreenPrint = floatingButton(R.drawable.ic_remote_camera, "Salvar print da tela remota") {
            saveQuickFrame()
        }
        fullscreenPrint.setOnLongClickListener { saveHighQuality(); true }
        addFloatingButton(fullscreenTopBar, fullscreenPrint)
        fullscreenAudio = floatingButton(R.drawable.ic_remote_volume, "Ouvir áudio remoto") {
            if (audioWanted) stopAudio() else startAudio()
        }
        addFloatingButton(fullscreenTopBar, fullscreenAudio)
        val hd = floatingButton(R.drawable.ic_remote_save, "Salvar Foto HD") { saveHighQuality() }
        addFloatingButton(fullscreenTopBar, hd)
        stage.addView(fullscreenTopBar, FrameLayout.LayoutParams(-2, -2,
            Gravity.TOP or Gravity.END).apply { setMargins(dp(7), dp(12), dp(7), 0) })

        fullscreenBottomBar = floatingBar()
        fun navOverlay(image: Int, label: String, action: String) {
            val item = floatingButton(image, label) { remoteCommand(action) }
            addFloatingButton(fullscreenBottomBar, item)
            fullscreenNavigation.add(item)
        }
        navOverlay(R.drawable.ic_remote_back, "Voltar no celular remoto", "back")
        navOverlay(R.drawable.ic_remote_home, "Início no celular remoto", "home")
        navOverlay(android.R.drawable.ic_menu_recent_history, "Recentes no celular remoto", "recents")
        navOverlay(R.drawable.ic_bell, "Notificações no celular remoto", "notifications")
        fullscreenTouch = floatingButton(R.drawable.ic_remote_hand, "Ligar/desligar toque remoto") {
            toggleRemoteTouch()
        }
        addFloatingButton(fullscreenBottomBar, fullscreenTouch)
        val fullscreenWake = floatingButton(R.drawable.ic_remote_wake, "Acender tela remota") {
            wakeRemoteScreen()
        }
        addFloatingButton(fullscreenBottomBar, fullscreenWake)
        stage.addView(fullscreenBottomBar, FrameLayout.LayoutParams(-2, -2,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { setMargins(dp(6), 0, dp(6), dp(14)) })

        fullscreenStatus = text("Conectando ao monitorado...", 10f, Color.WHITE).apply {
            maxLines = 1
            maxWidth = dp(240)
            ellipsize = android.text.TextUtils.TruncateAt.END
            background = shape(Color.argb(134, 12, 21, 32), 10)
            setPadding(dp(9), dp(5), dp(9), dp(5))
            visibility = View.GONE
        }
        stage.addView(fullscreenStatus, FrameLayout.LayoutParams(-2, -2,
            Gravity.BOTTOM or Gravity.START).apply { setMargins(dp(8), 0, dp(8), dp(66)) })
        normalStageHeight = minOf((resources.displayMetrics.heightPixels * 0.59f).toInt(),
            (resources.displayMetrics.widthPixels * 1.44f).toInt())
        stageSlot.addView(stage, LinearLayout.LayoutParams(-1, normalStageHeight))
        normal.addView(stageSlot)

        val nav = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        fun navItem(drawable: Int, title: String, gesture: String): LinearLayout =
            rowAction(drawable, title) { remoteCommand(gesture) }.first.also {
                nav.addView(it, LinearLayout.LayoutParams(0, dp(66), 1f).apply { rightMargin = dp(5) })
            }
        backAction = navItem(R.drawable.ic_remote_back, "Voltar", "back")
        homeAction = navItem(R.drawable.ic_remote_home, "Início", "home")
        recentsAction = navItem(android.R.drawable.ic_menu_recent_history, "Recentes", "recents")
        notificationsAction = navItem(R.drawable.ic_bell, "Notificações", "notifications")
        normal.addView(nav, LinearLayout.LayoutParams(-1, dp(66)).apply { topMargin = dp(12) })

        val tools = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val touch = rowAction(R.drawable.ic_remote_hand, "Toque na imagem", {
            toggleRemoteTouch()
        })
        touchAction = touch.first; touchIcon = touch.second; touchLabel = touch.third
        tools.addView(touchAction, LinearLayout.LayoutParams(0, dp(64), 1f).apply { rightMargin = dp(7) })
        val highQuality = rowAction(R.drawable.ic_remote_save, "Foto HD", { saveHighQuality() })
        highQualityAction = highQuality.first
        tools.addView(highQualityAction, LinearLayout.LayoutParams(0, dp(64), 1f).apply { rightMargin = dp(7) })
        val wake = rowAction(R.drawable.ic_remote_wake, "Acender tela", { wakeRemoteScreen() })
        tools.addView(wake.first, LinearLayout.LayoutParams(0, dp(64), 1f))
        normal.addView(tools, LinearLayout.LayoutParams(-1, dp(64)).apply { topMargin = dp(8) })
        normal.addView(text(
            "Toque em 📷 para salvar a prévia. Use Alta qualidade para solicitar uma captura mais nítida. " +
                "A sessão deve estar autorizada no outro aparelho.", 11.5f, muted).apply {
                setPadding(dp(3), dp(12), dp(3), dp(3)); maxLines = 4
            })
        setContentView(screenRoot)
        // Android 15/16 impõe edge-to-edge: não deixar toolbar ou rodapé sob as barras.
        if (Build.VERSION.SDK_INT >= 30) {
            screenRoot.setOnApplyWindowInsetsListener { _, insets ->
                val safe = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                normalScroll.setPadding(0, if (isFullscreen) 0 else safe.top,
                    0, if (isFullscreen) 0 else safe.bottom)
                // No modo imersivo as barras somem, mas recortes de câmera ainda
                // devem ser respeitados. Posicionar painéis sem cobrir a área segura.
                val cutout = insets.getInsets(WindowInsets.Type.displayCutout())
                if (::fullscreenTopBar.isInitialized) {
                    (fullscreenTopBar.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
                        params.topMargin = dp(12) + if (isFullscreen) cutout.top else safe.top
                        params.rightMargin = dp(7) + cutout.right
                        fullscreenTopBar.layoutParams = params
                    }
                }
                if (::fullscreenBottomBar.isInitialized) {
                    (fullscreenBottomBar.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
                        params.bottomMargin = dp(14) + if (isFullscreen) cutout.bottom else safe.bottom
                        fullscreenBottomBar.layoutParams = params
                    }
                }
                insets
            }
            screenRoot.requestApplyInsets()
        }
        updateControls()
        if (savedInstanceState?.getBoolean("remote_viewer_fullscreen", false) == true) {
            toggleFullscreen()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("remote_viewer_fullscreen", isFullscreen)
        super.onSaveInstanceState(outState)
    }

    private fun updateControls() {
        if (!::touchAction.isInitialized) return
        // Os botões continuam clicáveis para explicar quando falta permissão;
        // desabilitá-los deixava o usuário sem qualquer resposta.
        touchAction.isEnabled = true
        touchAction.alpha = if (touchReady) 1f else 0.55f
        val adbNavReady = adbAvailable() && screenChannelAuthenticated
        for (action in listOf(backAction, homeAction, recentsAction, notificationsAction)) {
            action.isEnabled = true
            action.alpha = if (touchReady || (adbNavReady && action !== notificationsAction)) 1f else 0.55f
        }
        touchLabel.text = if (touchMode) "Toque ligado" else "Toque"
        touchIcon.imageTintList = ColorStateList.valueOf(if (touchMode) accent else ink)
        val fresh = lastPreviewJpeg != null && sharingReady &&
            ScreenCapturePolicy.freshPreview(SystemClock.elapsedRealtime() - lastFrameAt)
        cameraButton.isEnabled = (fresh || (videoMode && sharingReady)) && !saving.get()
        cameraButton.alpha = if (cameraButton.isEnabled) 1f else 0.42f
        highQualityAction.isEnabled = sharingReady && !saving.get()
        highQualityAction.alpha = if (highQualityAction.isEnabled) 1f else 0.42f
        audioButton.isEnabled = audioReady
        audioButton.alpha = if (audioReady) 1f else 0.42f
        audioButton.setImageResource(if (audioWanted) R.drawable.ic_remote_mute else R.drawable.ic_remote_volume)
        audioButton.contentDescription = if (audioWanted) "Silenciar áudio remoto" else "Ouvir áudio remoto"
        if (::fullscreenAudio.isInitialized) {
            fullscreenAudio.isEnabled = audioReady
            fullscreenAudio.alpha = if (audioReady) 1f else 0.52f
            fullscreenAudio.setImageResource(if (audioWanted) R.drawable.ic_remote_mute
                else R.drawable.ic_remote_volume)
            fullscreenAudio.contentDescription = audioButton.contentDescription
        }
        if (::fullscreenPrint.isInitialized) {
            // No AVC, o Print solicita a foto HD quando não há JPEG da prévia.
            fullscreenPrint.isEnabled = cameraButton.isEnabled
            fullscreenPrint.alpha = cameraButton.alpha
        }
        if (::fullscreenTouch.isInitialized) {
            fullscreenTouch.imageTintList = ColorStateList.valueOf(
                if (touchMode && touchReady) accent else Color.WHITE)
            fullscreenTouch.alpha = if (touchReady) 1f else 0.62f
            fullscreenTouch.contentDescription = if (touchMode) "Desligar toque remoto"
                else "Ativar toque remoto"
        }
        for ((index, item) in fullscreenNavigation.withIndex()) {
            item.alpha = if (touchReady || (adbNavReady && index < 3)) 1f else 0.62f
        }
    }

    private fun toggleFullscreen() {
        isFullscreen = !isFullscreen
        if (isFullscreen) {
            stageSlot.removeView(stage)
            normalScroll.visibility = View.GONE
            stage.background = shape(Color.BLACK, 0)
            stage.clipToOutline = false
            screenRoot.addView(stage, FrameLayout.LayoutParams(-1, -1))
            fullscreenTopBar.visibility = View.VISIBLE
            fullscreenBottomBar.visibility = View.VISIBLE
            fullscreenStatus.visibility = View.VISIBLE
            fullscreenExit.visibility = View.VISIBLE
            fullscreenFit.visibility = View.VISIBLE
            applyImmersiveMode(true)

        } else {
            screenRoot.removeView(stage)
            stage.background = shape(Color.rgb(9, 16, 24), 22)
            stage.clipToOutline = true
            fullscreenTopBar.visibility = View.GONE
            fullscreenBottomBar.visibility = View.GONE
            fullscreenStatus.visibility = View.GONE
            fullscreenExit.visibility = View.GONE
            fullscreenFit.visibility = View.GONE
            picture.scaleType = ImageView.ScaleType.FIT_CENTER
            fillFullscreen = false
            fullscreenFit.text = "Preencher"
            applyImmersiveMode(false)
            stageSlot.addView(stage, LinearLayout.LayoutParams(-1, normalStageHeight))
            normalScroll.visibility = View.VISIBLE

        }
    }

    /** Persistir o modo em mudanças de foco (ex.: painel do Android recolhido). */
    private fun applyImmersiveMode(enabled: Boolean) {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = if (enabled) (
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        ) else if (dark) 0 else (View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
            View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR)
        if (Build.VERSION.SDK_INT >= 30) window.insetsController?.let { controller ->
            if (enabled) {
                controller.systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsets.Type.systemBars())
            } else controller.show(WindowInsets.Type.systemBars())
        }
        if (::screenRoot.isInitialized && Build.VERSION.SDK_INT >= 30) screenRoot.requestApplyInsets()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && isFullscreen) applyImmersiveMode(true)
    }

    private fun saveQuickFrame() {
        val image = lastPreviewJpeg
        if (active && sharingReady && videoMode && image == null) { saveHighQuality(); return }
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
        visitGeneration++
        active = host.isNotBlank() && ::picture.isInitialized
        updateChannelBadge()
        if (isFullscreen) applyImmersiveMode(true)
        main.removeCallbacks(poll); main.post(poll)
        if (!wakeRequestedOnOpen && SettingsStore.autoWakeOnView(this)) {
            wakeRequestedOnOpen = true
            wakeRemoteScreen()
        }
    }
    override fun onPause() {
        visitGeneration++
        active = false; sharingReady = false; touchMode = false; touchReady = false; audioReady = false
        remoteDeviceLocked = false; remoteScreenInteractive = true
        screenChannelAuthenticated = false
        if (fromAdbHandoff) AdbScreenBridge.close(host)
        stopAudio()
        main.removeCallbacks(poll)
        closeVideo()
        if (::picture.isInitialized) clearFrame()
        if (::placeholder.isInitialized) placeholder.visibility = View.VISIBLE
        super.onPause()
    }
    override fun onDestroy() {
        main.removeCallbacks(poll); pollExecutor.shutdownNow(); stopAudio(); closeVideo()
        if (::picture.isInitialized) clearFrame()
        super.onDestroy()
    }
    @Deprecated("Usar retorno nativo enquanto atividade está em tela cheia.")
    override fun onBackPressed() {
        if (isFullscreen) toggleFullscreen() else super.onBackPressed()
    }
}
