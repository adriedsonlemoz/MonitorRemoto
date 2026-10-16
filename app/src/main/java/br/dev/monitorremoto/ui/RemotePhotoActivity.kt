package br.dev.monitorremoto.ui

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.Gravity
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import br.dev.monitorremoto.core.RecentDevices
import br.dev.monitorremoto.transport.RemoteClient
import java.util.concurrent.Executors

/** Foto sob demanda; nenhum polling de vídeo e nenhuma câmera local aberta pelo controlador. */
class RemotePhotoActivity : Activity() {
    companion object {
        const val EXTRA_HOST = "host"
        const val EXTRA_METHOD = "method"
        private const val SAVE_DOCUMENT = 6281
    }
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var busy = false
    private var jpeg: ByteArray? = null
    private var pendingDocument: ByteArray? = null
    private lateinit var photo: ImageView
    private lateinit var status: TextView
    private lateinit var trigger: Button
    private lateinit var save: Button
    private lateinit var lens: Button
    private var front = false
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            setBackgroundColor(Color.rgb(17, 25, 37))
        }
        body.addView(TextView(this).apply {
            text = "Fotografar aparelho remoto"; textSize = 19f; setTextColor(Color.WHITE)
        })
        status = TextView(this).apply {
            text = "Toque em Tirar foto. É necessária uma sessão de foto previamente armada no monitorado."
            textSize = 12f; setTextColor(Color.LTGRAY); setPadding(0, dp(10), 0, dp(10))
        }
        body.addView(status)
        photo = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.BLACK)
            contentDescription = "Fotografia recebida do aparelho monitorado"
        }
        body.addView(photo, LinearLayout.LayoutParams(-1, 0, 1f))
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER; orientation = LinearLayout.HORIZONTAL }
        trigger = Button(this).apply { text = "Tirar foto"; isAllCaps = false; setOnClickListener { takePhoto() } }
        row.addView(trigger, LinearLayout.LayoutParams(0, dp(52), 1f))
        save = Button(this).apply { text = "Salvar"; isAllCaps = false; isEnabled = false; setOnClickListener { savePhoto() } }
        row.addView(save, LinearLayout.LayoutParams(0, dp(52), 1f))
        body.addView(row)
        lens = Button(this).apply {
            text = "Câmera: traseira"; isAllCaps = false
            setOnClickListener { front = !front; text = if (front) "Câmera: frontal" else "Câmera: traseira" }
        }
        body.addView(lens, LinearLayout.LayoutParams(-1, dp(48)))
        body.addView(Button(this).apply { text = "Voltar"; isAllCaps = false; setOnClickListener { finish() } },
            LinearLayout.LayoutParams(-1, dp(48)))
        setContentView(body)
    }
    private fun takePhoto() {
        if (busy) return
        val host = intent.getStringExtra(EXTRA_HOST).orEmpty()
        val method = intent.getStringExtra(EXTRA_METHOD) ?: "Chave salva"
        val selectedLens = if (front) "Frontal" else "Traseira"
        busy = true
        trigger.isEnabled = false
        lens.isEnabled = false
        save.isEnabled = false
        jpeg = null
        photo.setImageDrawable(null)
        status.text = "Solicitando foto $selectedLens…"
        executor.execute {
            val result = runCatching {
                val key = RecentDevices.key(this, host) ?: error("Chave de pareamento não encontrada.")
                RemoteClient.takePhoto(host, key, this, method, selectedLens)
            }
            main.post {
                if (isDestroyed || isFinishing) return@post
                busy = false
                trigger.isEnabled = true
                lens.isEnabled = true
                result.fold({ bytes ->
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bitmap == null) status.text = "JPEG recebido, mas a imagem não pôde ser aberta."
                    else {
                        jpeg = bytes
                        photo.setImageBitmap(bitmap)
                        status.text = "Foto recebida: ${bitmap.width} × ${bitmap.height} · ${bytes.size / 1024} KiB"
                        save.isEnabled = true
                    }
                }, { status.text = "Falha ao fotografar: ${it.message ?: "erro desconhecido"}" })
            }
        }
    }
    private fun savePhoto() {
        val bytes = jpeg?.copyOf() ?: return
        val fileName = RemoteImageSaver.fileName()
        if (Build.VERSION.SDK_INT >= 29) {
            save.isEnabled = false
            executor.execute {
                val result = runCatching { RemoteImageSaver.save(this, bytes, fileName) }
                main.post {
                    if (isFinishing || isDestroyed) return@post
                    Toast.makeText(this, if (result.isSuccess) "Foto salva na galeria" else
                        "Erro ao salvar: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                    save.isEnabled = jpeg != null
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
            main.post { if (!isFinishing && !isDestroyed) Toast.makeText(this,
                if (result.isSuccess) "Foto salva" else "Falha ao salvar foto", Toast.LENGTH_LONG).show() }
        }
    }
    override fun onDestroy() {
        jpeg = null
        pendingDocument = null
        executor.shutdownNow()
        super.onDestroy()
    }
}
