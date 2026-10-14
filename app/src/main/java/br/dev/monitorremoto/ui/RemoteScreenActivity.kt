package br.dev.monitorremoto.ui

import android.app.Activity
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.Gravity
import android.view.MotionEvent
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import br.dev.monitorremoto.core.RecentDevices
import br.dev.monitorremoto.core.RemoteGesturePolicy
import br.dev.monitorremoto.transport.RemoteClient
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

/** Controle visível: preview, gestos consentidos e playback de áudio interno compatível. */
class RemoteScreenActivity : Activity() {
    companion object {
        const val EXTRA_HOST = "host"
        const val EXTRA_METHOD = "method"
        private const val REFRESH_MS = 720L
    }
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var active = false
    @Volatile private var audioWanted = false
    @Volatile private var audioReady = false
    @Volatile private var touchReady = false
    private var touchMode = false
    private val fetching = AtomicBoolean(false)
    private lateinit var picture: ImageView
    private lateinit var status: TextView
    private lateinit var touchButton: Button
    private lateinit var audioButton: Button
    private var bitmap: android.graphics.Bitmap? = null
    private var host = ""
    private var method = "Chave salva"
    private var lastSequence = -1L
    private var audioThread: Thread? = null
    @Volatile private var audioPlayer: AudioTrack? = null
    private var gestureStart: Pair<Float, Float>? = null
    private var gestureAt = 0L
    private var requestedThisVisit = false

    private fun remoteCommand(action: String, x: Float = 0.5f, y: Float = 0.5f,
        ex: Float = x, ey: Float = y, duration: Long = 240L) {
        if (!active || !touchReady || !touchMode) return
        Thread({
            val accepted = runCatching {
                val key = RecentDevices.key(applicationContext, host) ?: error("Pareamento ausente")
                RemoteClient.touch(host, key, applicationContext, method, action, x, y, ex, ey, duration)
            }.getOrDefault(false)
            if (!accepted) main.post {
                if (active) Toast.makeText(this, "Toque recusado ou serviço de acessibilidade desativado", Toast.LENGTH_SHORT).show()
            }
        }, "MonitorRemoto-remote-gesture").start()
    }

    private fun hitPoint(x: Float, y: Float): Pair<Float, Float>? {
        val image = bitmap ?: return null
        return RemoteGesturePolicy.fitCenterPoint(x, y, picture.width, picture.height,
            image.width, image.height)
    }

    private val poll = object : Runnable {
        override fun run() {
            if (!active || !fetching.compareAndSet(false, true)) return
            Thread({
                var nextStatus = ""
                var nextBitmap: android.graphics.Bitmap? = null
                var nextSequence = -1L
                var clear = false
                var canTouch = false
                var canAudio = false
                try {
                    val key = RecentDevices.key(applicationContext, host)
                        ?: error("Chave não encontrada. Conecte novamente.")
                    val result = RemoteClient.screenFrame(host, key, applicationContext, method)
                    canTouch = result.optBoolean("touchReady")
                    canAudio = result.optBoolean("audioReady")
                    val encoded = result.optString("jpeg")
                    if (!result.optBoolean("sharing") && !requestedThisVisit) {
                        requestedThisVisit = true
                        // Notificação visível no monitorado, sem iniciar captura remotamente.
                        runCatching { RemoteClient.requestScreen(host, key, applicationContext, method) }
                    }
                    if (!result.optBoolean("sharing")) {
                        nextStatus = "Aguardando autorização no monitorado: Compartilhar tela."
                        clear = true
                    } else if (encoded.isBlank()) {
                        nextStatus = "Sessão autorizada. Aguardando quadro recente..."
                        clear = true
                    } else {
                        require(encoded.length <= 150_000)
                        nextSequence = result.optLong("sequence", -1)
                        if (nextSequence != lastSequence) {
                            val bytes = Base64.decode(encoded, Base64.NO_WRAP)
                            require(bytes.size <= 110_000)
                            nextBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                ?: error("Imagem JPEG inválida")
                        }
                        nextStatus = "Tela ao vivo · ~1-2 quadros/s" +
                            (if (canTouch) " · toque permitido" else " · toque indisponível") +
                            (if (canAudio) " · áudio disponível" else "")
                    }
                } catch (e: Exception) {
                    nextStatus = "Sem imagem: ${e.message?.take(115) ?: "rede indisponível"}"
                    clear = true
                }
                main.post {
                    fetching.set(false)
                    if (!active || isFinishing || isDestroyed) { nextBitmap?.recycle(); return@post }
                    status.text = nextStatus
                    touchReady = canTouch && !clear
                    audioReady = canAudio && !clear
                    if (!touchReady) touchMode = false
                    touchButton.isEnabled = touchReady
                    touchButton.text = if (touchMode) "Toque: ligado" else "Toque: desligado"
                    audioButton.isEnabled = audioReady
                    if (!audioReady && audioWanted) stopAudio()
                    if (nextBitmap != null) {
                        picture.setImageBitmap(nextBitmap)
                        bitmap?.recycle()
                        bitmap = nextBitmap
                        lastSequence = nextSequence
                    } else if (clear) {
                        picture.setImageDrawable(null)
                        bitmap?.recycle(); bitmap = null; lastSequence = -1L
                    }
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
        window.statusBarColor = Color.rgb(16, 22, 31)
        window.navigationBarColor = Color.rgb(16, 22, 31)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(16, 22, 31))
            setPadding(dp(12), dp(8), dp(12), dp(12))
        }
        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val back = Button(this).apply { text = "‹ Voltar"; isAllCaps = false; setOnClickListener { finish() } }
        top.addView(back, LinearLayout.LayoutParams(dp(96), dp(46)))
        top.addView(TextView(this).apply {
            text = "Ver tela"; textSize = 20f; setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD); gravity = Gravity.CENTER_VERTICAL
        }, LinearLayout.LayoutParams(0, dp(46), 1f))
        root.addView(top)
        status = TextView(this).apply {
            text = "Conectando..."; textSize = 12f; setTextColor(Color.rgb(190, 210, 220))
            setPadding(0, dp(8), 0, dp(10))
        }
        root.addView(status)
        picture = ImageView(this).apply {
            setBackgroundColor(Color.BLACK)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Visualização da tela remota autorizada"
            setOnTouchListener { _, event ->
                if (!touchMode || !touchReady || bitmap == null) return@setOnTouchListener false
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        gestureStart = hitPoint(event.x, event.y)
                        gestureAt = android.os.SystemClock.elapsedRealtime()
                        gestureStart != null
                    }
                    MotionEvent.ACTION_UP -> {
                        val start = gestureStart
                        gestureStart = null
                        val end = hitPoint(event.x, event.y)
                        if (start != null && end != null) {
                            val duration = (android.os.SystemClock.elapsedRealtime() - gestureAt).coerceIn(60L, 1200L)
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
        root.addView(picture, LinearLayout.LayoutParams(-1, 0, 1f))
        val controls = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        touchButton = Button(this).apply {
            text = "Toque: desligado"; isAllCaps = false; isEnabled = false
            setOnClickListener {
                touchMode = !touchMode
                text = if (touchMode) "Toque: ligado" else "Toque: desligado"
            }
        }
        controls.addView(touchButton, LinearLayout.LayoutParams(0, dp(49), 1f))
        audioButton = Button(this).apply {
            text = "Ouvir áudio"; isAllCaps = false; isEnabled = false
            setOnClickListener { if (audioWanted) stopAudio() else startAudio() }
        }
        controls.addView(audioButton, LinearLayout.LayoutParams(0, dp(49), 1f))
        root.addView(controls)
        val nav = LinearLayout(this).apply { gravity = Gravity.CENTER_HORIZONTAL }
        for ((label, action) in listOf("Voltar remoto" to "back", "Início remoto" to "home")) {
            nav.addView(Button(this).apply {
                text = label; isAllCaps = false
                setOnClickListener { remoteCommand(action) }
            }, LinearLayout.LayoutParams(0, dp(48), 1f))
        }
        root.addView(nav)
        root.addView(TextView(this).apply {
            text = "A captura deve estar autorizada no outro aparelho. Toque e áudio exigem habilitações locais independentes. Sem gravação."
            textSize = 11f; setTextColor(Color.rgb(165, 180, 194))
            setPadding(0, dp(9), 0, 0)
        })
        setContentView(root)
    }

    private fun startAudio() {
        if (!active || !audioReady || audioWanted) return
        audioWanted = true
        audioButton.text = "Silenciar"
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
                // Desconexão ou encerramento da sessão. Nunca reiniciar a captura do monitorado.
            } finally {
                runCatching { track?.pause(); track?.flush(); track?.release() }
                if (audioPlayer === track) audioPlayer = null
                main.post { if (audioWanted) { audioWanted = false; if (::audioButton.isInitialized) audioButton.text = "Ouvir áudio" } }
            }
        }, "MonitorRemoto-audio-listener").apply { isDaemon = true; start() }
    }
    private fun stopAudio() {
        audioWanted = false
        if (::audioButton.isInitialized) audioButton.text = "Ouvir áudio"
        runCatching { audioPlayer?.pause(); audioPlayer?.flush() }
        audioThread?.interrupt()
        audioThread = null
    }
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    override fun onResume() {
        super.onResume()
        active = host.isNotBlank() && ::picture.isInitialized
        main.removeCallbacks(poll); main.post(poll)
    }
    override fun onPause() {
        active = false; touchMode = false; touchReady = false; audioReady = false
        stopAudio()
        main.removeCallbacks(poll)
        if (::picture.isInitialized) picture.setImageDrawable(null)
        bitmap?.recycle(); bitmap = null; lastSequence = -1L
        super.onPause()
    }
    override fun onDestroy() {
        main.removeCallbacks(poll); stopAudio()
        if (::picture.isInitialized) picture.setImageDrawable(null)
        bitmap?.recycle(); bitmap = null
        super.onDestroy()
    }
}
