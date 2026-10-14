package br.dev.monitorremoto.ui

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import br.dev.monitorremoto.core.RecentDevices
import br.dev.monitorremoto.core.RemoteFilePolicy
import br.dev.monitorremoto.core.SettingsStore
import br.dev.monitorremoto.transport.RemoteClient
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/** Cópia SAF sob demanda. Nenhum arquivo é recebido antes do usuário escolher onde salvá-lo. */
class RemoteFilesActivity : Activity() {
    companion object {
        const val EXTRA_HOST = "host"
        const val EXTRA_METHOD = "method"
        private const val CREATE_DOCUMENT = 9045
    }
    private val main = Handler(Looper.getMainLooper())
    private var host = ""
    private var method = "Chave salva"
    private var path = emptyList<String>()
    private var generation = 0
    private val cancel = AtomicBoolean(false)
    private var copying = false
    private var pending: JSONObject? = null
    private lateinit var content: LinearLayout
    private lateinit var status: TextView
    private var key: ByteArray? = null

    private val dark get() = when (SettingsStore.theme(this)) {
        "dark" -> true
        "system" -> (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        else -> false
    }
    private val foreground get() = if (dark) Color.WHITE else Color.rgb(21, 38, 55)
    private val muted get() = if (dark) Color.rgb(170, 193, 210) else Color.rgb(95, 110, 125)
    private val panelColor get() = if (dark) Color.rgb(28, 42, 60) else Color.WHITE
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun label(value: String, size: Float = 14f, bold: Boolean = false) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(this@RemoteFilesActivity.foreground)
        if (bold) setTypeface(null, Typeface.BOLD)
        setPadding(0, dp(4), 0, dp(6))
    }
    private fun button(title: String, action: () -> Unit) = Button(this).apply {
        text = title
        isAllCaps = false
        setTextColor(this@RemoteFilesActivity.foreground)
        setOnClickListener { action() }
        minHeight = dp(48)
    }
    private fun card(title: String, subtitle: String, action: () -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        val bg = GradientDrawable().apply { setColor(panelColor); cornerRadius = dp(14).toFloat() }
        background = bg
        setPadding(dp(15), dp(11), dp(15), dp(11))
        addView(label(title, 14f, true))
        addView(label(subtitle, 12f).apply { setTextColor(muted) })
        setOnClickListener { action() }
        isClickable = true
        isFocusable = true
    }
    private fun addCard(view: View) {
        content.addView(view, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(9) })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        host = intent.getStringExtra(EXTRA_HOST).orEmpty()
        method = intent.getStringExtra(EXTRA_METHOD).orEmpty().ifBlank { "Chave salva" }
        key = RecentDevices.key(this, host)
        window.statusBarColor = if (dark) Color.rgb(14, 22, 35) else Color.rgb(247, 249, 252)
        window.navigationBarColor = window.statusBarColor
        val scroll = ScrollView(this).apply { setBackgroundColor(window.statusBarColor); isFillViewport = true }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(20), dp(18), dp(35))
        }
        scroll.addView(content)
        setContentView(scroll)
        if (host.isBlank() || key == null) showProblem("Conecte um telefone pareado antes de recuperar arquivos.")
        else listFolder()
    }

    private fun header() {
        content.removeAllViews()
        addCard(label("Recuperar dados", 21f, true))
        val deviceName = RecentDevices.list(this).firstOrNull { it.host.equals(host, true) }?.name.orEmpty()
        addCard(label("Telefone: ${deviceName.ifBlank { "Não identificado" }} · $host", 12f)
            .apply { setTextColor(muted) })
        addCard(label("Somente arquivos da pasta escolhida e autorizada no telefone monitorado. " +
            "Nada é recuperado de arquivos apagados ou de outros aplicativos.", 12f))
        status = label("", 12f)
        addCard(status)
    }
    private fun showProblem(text: String) {
        header()
        status.text = text
        addCard(button("Voltar") { finish() })
    }

    private fun listFolder() {
        if (copying) return
        val token = ++generation
        header()
        val current = path.toList()
        addCard(label("Pasta: /" + current.joinToString("/"), 14f, true))
        if (current.isNotEmpty()) addCard(button("‹ Pasta anterior") {
            path = path.dropLast(1)
            listFolder()
        })
        status.text = "Consultando arquivos com conexão criptografada…"
        val secret = key ?: return
        thread(name = "MonitorRemoto-data-list") {
            val answer = runCatching { RemoteClient.dataList(host, secret, applicationContext, method, current) }
            main.post {
                if (isFinishing || isDestroyed || token != generation || copying) return@post
                answer.onSuccess { data ->
                    header()
                    addCard(label("Pasta: /" + current.joinToString("/"), 14f, true))
                    if (current.isNotEmpty()) addCard(button("‹ Pasta anterior") {
                        path = current.dropLast(1); listFolder()
                    })
                    status.text = if (data.optBoolean("truncated"))
                        "Lista limitada a 150 itens. Escolha uma pasta menor no monitorado." else "Toque em uma pasta ou arquivo."
                    val entries = data.optJSONArray("entries")
                    if (entries == null || entries.length() == 0) addCard(label("Pasta vazia ou sem arquivos acessíveis."))
                    else for (index in 0 until entries.length()) {
                        val item = entries.optJSONObject(index) ?: continue
                        val name = item.optString("name")
                        if (!RemoteFilePolicy.validSegment(name)) continue
                        val folder = item.optBoolean("folder")
                        val size = item.optLong("size", -1L)
                        val subtitle = if (folder) "Pasta · abrir" else
                            "Arquivo · ${if (size >= 0) "${size / 1024} KiB" else "tamanho indisponível"} · salvar cópia"
                        addCard(card("${if (folder) "📁" else "📄"} $name", subtitle) {
                            if (folder) {
                                if (current.size < RemoteFilePolicy.MAX_DEPTH) {
                                    path = current + name; listFolder()
                                } else status.text = "Limite de 12 pastas aninhadas."
                            } else chooseDestination(item, current)
                        })
                    }
                    addCard(button("Atualizar lista") { listFolder() })
                }.onFailure { status.text = "Não foi possível listar arquivos: ${it.message ?: "falha de rede"}" }
            }
        }
    }

    private var pendingPath = emptyList<String>()
    private fun chooseDestination(file: JSONObject, parent: List<String>) {
        if (copying) return
        val name = file.optString("name")
        val size = file.optLong("size", -1L)
        if (!RemoteFilePolicy.validSegment(name) || size !in 1..RemoteFilePolicy.MAX_FILE_BYTES) {
            status.text = "Esta versão copia arquivos individuais de 1 byte a 32 MiB."
            return
        }
        pending = file
        pendingPath = parent + name
        val mime = file.optString("mime").takeIf { it.length in 3..110 && '/' in it } ?: "application/octet-stream"
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = mime
                putExtra(Intent.EXTRA_TITLE, name)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }, CREATE_DOCUMENT)
        } catch (error: Exception) {
            pending = null
            status.text = "O Android não conseguiu abrir o seletor: ${error.message}"
        }
    }

    @Deprecated("Compatibilidade com seletor de arquivos SAF")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != CREATE_DOCUMENT) return
        val destination = data?.data
        val file = pending
        pending = null
        if (resultCode != RESULT_OK || destination == null || file == null) {
            status.text = "Operação cancelada: nenhum arquivo foi copiado."
            return
        }
        copyTo(destination, file, pendingPath)
    }

    private fun copyTo(destination: Uri, file: JSONObject, remotePath: List<String>) {
        if (copying) return
        val secret = key ?: return
        val size = file.optLong("size", -1L)
        val modified = file.optLong("modified", -1L)
        if (size !in 1..RemoteFilePolicy.MAX_FILE_BYTES) return
        copying = true
        cancel.set(false)
        val sourceName = file.optString("name")
        header()
        status.text = "Copiando $sourceName: 0%"
        addCard(button("Cancelar transferência") { cancel.set(true); status.text = "Cancelando…" })
        thread(name = "MonitorRemoto-data-copy") {
            var written = 0L
            var lastPercent = -1L
            val result = runCatching {
                val out = contentResolver.openOutputStream(destination, "wt")
                    ?: error("Android recusou escrever no destino.")
                out.use { stream ->
                    while (written < size) {
                        check(SettingsStore.getRemoteHost(applicationContext) == host) {
                            "O aparelho selecionado mudou durante a transferência."
                        }
                        check(!cancel.get()) { "Transferência cancelada." }
                        val bytes = RemoteClient.dataChunk(host, secret, applicationContext,
                            method, remotePath, size, modified, written)
                        check(!cancel.get()) { "Transferência cancelada." }
                        stream.write(bytes)
                        written += bytes.size
                        val percent = written * 100 / size
                        if (percent / 5 > lastPercent / 5 || written == size) {
                            lastPercent = percent
                            main.post { if (!isDestroyed && !isFinishing) status.text =
                                "Copiando $sourceName: $percent% (${written / 1024} KiB)" }
                        }
                    }
                    stream.flush()
                }
                check(written == size) { "Cópia incompleta." }
            }
            if (result.isFailure) {
                // Se o provider aceitar, descarta o documento parcial criado por ACTION_CREATE_DOCUMENT.
                runCatching { DocumentsContract.deleteDocument(contentResolver, destination) }
            }
            main.post {
                if (isFinishing || isDestroyed) return@post
                copying = false
                header()
                status.text = if (result.isSuccess) "Cópia concluída: $sourceName ($written bytes)." else
                    "Falha ou cancelamento: ${result.exceptionOrNull()?.message ?: "erro de escrita"}. " +
                        "Se o arquivo parcial permanecer no destino, exclua-o manualmente."
                addCard(button("Voltar aos arquivos") { listFolder() })
            }
        }
    }

    @Deprecated("Navegação simples")
    override fun onBackPressed() {
        if (copying) { cancel.set(true); return }
        if (path.isNotEmpty()) { path = path.dropLast(1); listFolder() }
        else super.onBackPressed()
    }

    override fun onDestroy() {
        cancel.set(true)
        generation++
        super.onDestroy()
    }
}
