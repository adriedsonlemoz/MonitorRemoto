package br.dev.monitorremoto.ui

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import br.dev.monitorremoto.core.PhotoPreviewPolicy
import br.dev.monitorremoto.core.RecentDevices
import br.dev.monitorremoto.core.SettingsStore
import br.dev.monitorremoto.transport.RemoteClient
import java.util.Locale
import java.util.concurrent.Executors

/** Foto única sob demanda. Sem polling de vídeo, sem captura automática ou armazenamento oculto. */
class RemotePhotoActivity : Activity() {
    companion object {
        const val EXTRA_HOST = "host"
        const val EXTRA_METHOD = "method"
        private const val SAVE_DOCUMENT = 6281
    }
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var taking = false
    private var saving = false
    private var jpeg: ByteArray? = null
    private var savedUri: Uri? = null
    private var pendingDocument: ByteArray? = null
    private var shareAfterDocument = false
    private var front = false
    private lateinit var photo: PhotoZoomView
    private lateinit var empty: TextView
    private lateinit var status: TextView
    private lateinit var detail: TextView
    private lateinit var progress: ProgressBar
    private lateinit var trigger: Button
    private lateinit var save: Button
    private lateinit var share: Button
    private lateinit var lens: Button
    private lateinit var reset: Button
    private val isDark get() = when (SettingsStore.theme(this)) {
        "dark" -> true
        "system" -> resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        else -> false
    }
    private val bg get() = if (isDark) Color.rgb(14, 23, 36) else Color.rgb(247, 249, 252)
    private val surface get() = if (isDark) Color.rgb(28, 40, 55) else Color.WHITE
    private val ink get() = if (isDark) Color.WHITE else Color.rgb(29, 42, 60)
    private val muted get() = if (isDark) Color.rgb(171, 184, 199) else Color.rgb(98, 114, 130)
    private val accent = Color.rgb(36, 110, 197)
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun shape(color: Int, radius: Int = 15) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
    }
    private fun label(value: String, size: Float, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size; setTextColor(if (bold) ink else muted)
        if (bold) setTypeface(null, Typeface.BOLD)
    }
    private fun action(value: String, highlighted: Boolean = false, click: () -> Unit) = Button(this).apply {
        text = value; isAllCaps = false; textSize = 13f; minHeight = dp(50)
        setTextColor(if (highlighted) Color.WHITE else ink)
        background = shape(if (highlighted) accent else surface, 13)
        elevation = 0f; stateListAnimator = null
        setOnClickListener { click() }
    }
    private fun row(parent: LinearLayout, vararg buttons: Button) {
        val container = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        buttons.forEachIndexed { i, button ->
            container.addView(button, LinearLayout.LayoutParams(0, dp(50), 1f).apply {
                if (i > 0) leftMargin = dp(8)
            })
        }
        parent.addView(container, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
    }
    private fun refreshActions() {
        trigger.isEnabled = !taking && !saving
        lens.isEnabled = !taking && !saving
        save.isEnabled = jpeg != null && !taking && !saving
        share.isEnabled = jpeg != null && !taking && !saving
        reset.isEnabled = photo.hasPhoto() && !taking
        progress.visibility = if (taking || saving) View.VISIBLE else View.GONE
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg; window.navigationBarColor = bg
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = if (isDark) 0 else
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(dp(14), dp(10), dp(14), dp(12))
            fitsSystemWindows = true
        }
        val heading = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val back = action("‹ Voltar") { finish() }
        heading.addView(back, LinearLayout.LayoutParams(dp(91), dp(50)))
        val title = label("Foto remota", 21f, true).apply { setPadding(dp(14), 0, 0, 0) }
        heading.addView(title)
        body.addView(heading)
        val host = intent.getStringExtra(EXTRA_HOST).orEmpty()
        val alias = RecentDevices.list(this).firstOrNull { it.host == host }?.name ?: "Aparelho monitorado"
        body.addView(label(alias, 12f).apply { setPadding(dp(3), dp(4), 0, dp(9)) })
        val indicator = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = shape(surface)
            setPadding(dp(12), dp(11), dp(12), dp(11))
        }
        status = label("Pronto para solicitar uma fotografia", 14f, true)
        detail = label("A sessão de câmera deve estar armada no monitorado.", 12f)
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true; visibility = View.GONE; indeterminateTintList = android.content.res.ColorStateList.valueOf(accent)
        }
        indicator.addView(status)
        indicator.addView(detail, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
        indicator.addView(progress, LinearLayout.LayoutParams(-1, dp(3)).apply { topMargin = dp(9) })
        body.addView(indicator)

        val previewCard = FrameLayout(this).apply { background = shape(Color.rgb(8, 13, 23)); clipToOutline = true }
        photo = PhotoZoomView(this).apply {
            contentDescription = "Prévia da última fotografia recebida. Aproxime com dois dedos, arraste e toque duas vezes para alternar zoom."
        }
        previewCard.addView(photo, FrameLayout.LayoutParams(-1, -1))
        empty = TextView(this).apply {
            text = "📷\n\nNenhuma foto recebida\nToque em Tirar foto para capturar uma imagem"
            textSize = 14f; setTextColor(Color.LTGRAY); gravity = Gravity.CENTER
            setPadding(dp(16), 0, dp(16), 0)
        }
        previewCard.addView(empty, FrameLayout.LayoutParams(-1, -1))
        body.addView(previewCard, LinearLayout.LayoutParams(-1, 0, 1f).apply {
            topMargin = dp(10); bottomMargin = dp(6)
        })
        lens = action("Câmera: traseira") {
            front = !front
            lens.text = if (front) "Câmera: frontal" else "Câmera: traseira"
        }
        reset = action("Ajustar imagem") { photo.resetZoom() }
        row(body, lens, reset)
        trigger = action("📷 Tirar foto", highlighted = true) { takePhoto() }
        body.addView(trigger, LinearLayout.LayoutParams(-1, dp(56)).apply { topMargin = dp(9) })
        save = action("Salvar na galeria") { savePhoto(false) }
        share = action("Compartilhar") { savePhoto(true) }
        row(body, save, share)
        body.addView(label("A foto não é salva automaticamente. Zoom e visualização funcionam localmente após recebê-la.", 11f)
            .apply { setPadding(dp(2), dp(10), dp(2), 0) })
        setContentView(body)
        refreshActions()
        if (host.isBlank() || RecentDevices.key(this, host) == null) {
            status.text = "Aparelho não pareado"
            detail.text = "Conecte-se a um aparelho com chave salva para solicitar uma foto."
            trigger.isEnabled = false
        }
    }

    private fun decodePreview(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val sample = PhotoPreviewPolicy.sampleSize(bounds.outWidth, bounds.outHeight)
        val options = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.RGB_565 }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    private fun takePhoto() {
        if (taking || saving) return
        val host = intent.getStringExtra(EXTRA_HOST).orEmpty()
        val method = intent.getStringExtra(EXTRA_METHOD) ?: "Chave salva"
        val selectedLens = if (front) "Frontal" else "Traseira"
        taking = true
        status.text = "Fotografando pela câmera ${selectedLens.lowercase(Locale.getDefault())}…"
        detail.text = "Aguardando um JPEG único do aparelho monitorado."
        refreshActions()
        val started = SystemClock.elapsedRealtime()
        executor.execute {
            val result = runCatching {
                val key = RecentDevices.key(this, host) ?: error("Chave de pareamento não encontrada.")
                val data = RemoteClient.takePhoto(host, key, this, method, selectedLens)
                val preview = decodePreview(data) ?: error("JPEG recebido, mas não pôde ser exibido.")
                Pair(data, preview)
            }
            main.post {
                if (isDestroyed || isFinishing) {
                    result.getOrNull()?.second?.recycle()
                    return@post
                }
                taking = false
                result.fold({ (bytes, bitmap) ->
                    jpeg = bytes
                    savedUri = null
                    photo.showPhoto(bitmap)
                    empty.visibility = View.GONE
                    status.text = "Foto recebida · $selectedLens"
                    val seconds = (SystemClock.elapsedRealtime() - started) / 1000f
                    detail.text = "${bitmap.width} × ${bitmap.height} · ${bytes.size / 1024} KiB · ${"%.1f".format(Locale.getDefault(), seconds)} s · gesto de pinça para ampliar"
                }, {
                    status.text = "Não foi possível tirar a foto"
                    detail.text = (it.message ?: "Falha na conexão ou na câmera.").take(210) +
                        if (jpeg != null) " A imagem exibida é da captura anterior." else ""
                })
                refreshActions()
            }
        }
    }

    private fun savePhoto(shareAfter: Boolean) {
        val bytes = jpeg?.copyOf() ?: return
        if (saving || taking) return
        if (savedUri != null) {
            if (shareAfter) shareUri(savedUri!!) else Toast.makeText(this, "Esta foto já foi salva", Toast.LENGTH_SHORT).show()
            return
        }
        val fileName = RemoteImageSaver.fileName()
        if (Build.VERSION.SDK_INT >= 29) {
            saving = true
            status.text = if (shareAfter) "Salvando para compartilhar…" else "Salvando na galeria…"
            refreshActions()
            executor.execute {
                val result = runCatching { RemoteImageSaver.save(this, bytes, fileName) }
                main.post {
                    if (isDestroyed || isFinishing) return@post
                    saving = false
                    result.fold({ uri ->
                        savedUri = uri
                        status.text = "Foto salva em Imagens / Monitor Remoto"
                        if (shareAfter) shareUri(uri) else Toast.makeText(this, "Foto salva na galeria", Toast.LENGTH_SHORT).show()
                    }, { status.text = "Falha ao salvar: ${it.message ?: "armazenamento indisponível"}" })
                    refreshActions()
                }
            }
        } else {
            pendingDocument = bytes
            shareAfterDocument = shareAfter
            startActivityForResult(RemoteImageSaver.documentIntent(fileName), SAVE_DOCUMENT)
        }
    }

    private fun shareUri(uri: Uri) {
        runCatching {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newUri(contentResolver, "Foto do Monitor Remoto", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(send, "Compartilhar fotografia"))
        }.onFailure { Toast.makeText(this, "Não foi possível abrir o compartilhamento", Toast.LENGTH_LONG).show() }
    }

    @Deprecated("Compatibilidade com ACTION_CREATE_DOCUMENT no Android 8/9")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != SAVE_DOCUMENT) return
        val bytes = pendingDocument
        pendingDocument = null
        val share = shareAfterDocument
        shareAfterDocument = false
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null || bytes == null) return
        saving = true; refreshActions()
        executor.execute {
            val result = runCatching {
                contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("Destino indisponível")
                uri
            }
            main.post {
                if (isDestroyed || isFinishing) return@post
                saving = false
                result.fold({ saved ->
                    savedUri = saved
                    status.text = "Foto salva no destino escolhido"
                    if (share) shareUri(saved)
                }, { status.text = "Falha ao salvar: ${it.message ?: "destino indisponível"}" })
                refreshActions()
            }
        }
    }

    override fun onDestroy() {
        jpeg = null; pendingDocument = null
        if (::photo.isInitialized) photo.showPhoto(null)
        executor.shutdownNow()
        super.onDestroy()
    }
}
