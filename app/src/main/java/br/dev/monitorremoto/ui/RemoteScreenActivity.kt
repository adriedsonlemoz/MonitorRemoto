package br.dev.monitorremoto.ui

import android.app.Activity
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import br.dev.monitorremoto.core.RecentDevices
import br.dev.monitorremoto.transport.RemoteClient
import java.util.concurrent.atomic.AtomicBoolean

/** Visualizador no controlador. A tela só existe enquanto a Activity estiver visível. */
class RemoteScreenActivity : Activity() {
    companion object {
        const val EXTRA_HOST = "host"
        const val EXTRA_METHOD = "method"
        private const val REFRESH_MS = 1_250L // 1 fps na origem, sem polling em segundo plano
    }
    private val main = Handler(Looper.getMainLooper())
    private var active = false
    private val fetching = AtomicBoolean(false)
    private lateinit var picture: ImageView
    private var displayedBitmap: android.graphics.Bitmap? = null
    private lateinit var status: TextView
    private var host = ""
    private var method = "Chave salva"
    @Volatile private var lastSequence = -1L
    private val poll = object : Runnable {
        override fun run() {
            if (!active || !fetching.compareAndSet(false, true)) return
            Thread({
                var nextStatus = ""
                var bitmap: android.graphics.Bitmap? = null
                var nextSequence = -1L
                var clearPicture = false
                try {
                    val key = RecentDevices.key(applicationContext, host)
                        ?: error("Chave de pareamento não encontrada. Conecte novamente.")
                    val frame = RemoteClient.screenFrame(host, key, applicationContext, method)
                    val encoded = frame.optString("jpeg")
                    if (!frame.optBoolean("sharing")) {
                        nextStatus = "Aguardando autorização no aparelho monitorado. Abra Monitorado → Compartilhar tela."
                        clearPicture = true
                    } else if (encoded.isBlank()) {
                        nextStatus = "Compartilhamento autorizado. Aguardando imagem recente..."
                        clearPicture = true
                    } else {
                        require(encoded.length <= 150_000) { "Quadro acima do limite seguro." }
                        nextSequence = frame.optLong("sequence", -1)
                        if (nextSequence != lastSequence) {
                            val bytes = Base64.decode(encoded, Base64.NO_WRAP)
                            require(bytes.size <= 110_000) { "Imagem acima do limite." }
                            bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                ?: error("Quadro JPEG inválido.")
                        }
                        nextStatus = "Tela ao vivo · atualização aproximada de 1 quadro/s · sem áudio"
                    }
                } catch (error: Exception) {
                    nextStatus = "Sem imagem: ${error.message?.take(140) ?: "conexão indisponível"}"
                    clearPicture = true
                }
                main.post {
                    fetching.set(false)
                    if (!active || isFinishing || isDestroyed) {
                        bitmap?.recycle()
                        return@post
                    }
                    status.text = nextStatus
                    if (bitmap != null) {
                        val previous = displayedBitmap
                        picture.setImageBitmap(bitmap)
                        displayedBitmap = bitmap
                        if (previous !== bitmap) previous?.recycle()
                        lastSequence = nextSequence
                    } else if (clearPicture) {
                        picture.setImageDrawable(null)
                        displayedBitmap?.recycle()
                        displayedBitmap = null
                        lastSequence = -1L
                    }
                    main.postDelayed(this, REFRESH_MS)
                }
            }, "MonitorRemoto-screen-view").start()
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
            setPadding(dp(14), dp(14), dp(14), dp(18))
        }
        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val back = Button(this).apply {
            text = "‹ Voltar"; isAllCaps = false
            setOnClickListener { finish() }
        }
        top.addView(back, LinearLayout.LayoutParams(dp(110), dp(48)))
        val heading = TextView(this).apply {
            text = "Ver tela"
            textSize = 20f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(heading, LinearLayout.LayoutParams(0, dp(48), 1f))
        root.addView(top)
        status = TextView(this).apply {
            text = "Conectando..."
            setTextColor(Color.rgb(190, 210, 220))
            textSize = 12f
            setPadding(dp(2), dp(10), dp(2), dp(15))
        }
        root.addView(status)
        picture = ImageView(this).apply {
            setBackgroundColor(Color.BLACK)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Imagem da tela remota consentida"
        }
        root.addView(picture, LinearLayout.LayoutParams(-1, 0, 1f))
        val note = TextView(this).apply {
            text = "A sessão precisa ser aprovada no outro aparelho. A captura para quando o usuário interrompe o compartilhamento."
            setTextColor(Color.rgb(165, 180, 194))
            textSize = 11.5f
            setPadding(0, dp(12), 0, 0)
        }
        root.addView(note)
        setContentView(root)
    }
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    override fun onResume() {
        super.onResume()
        active = host.isNotBlank() && ::picture.isInitialized
        main.removeCallbacks(poll)
        main.post(poll)
    }
    override fun onPause() {
        active = false
        main.removeCallbacks(poll)
        if (::picture.isInitialized) picture.setImageDrawable(null)
        displayedBitmap?.recycle()
        displayedBitmap = null
        lastSequence = -1L
        super.onPause()
    }
    override fun onDestroy() {
        main.removeCallbacks(poll)
        if (::picture.isInitialized) picture.setImageDrawable(null)
        displayedBitmap?.recycle()
        displayedBitmap = null
        super.onDestroy()
    }
}
