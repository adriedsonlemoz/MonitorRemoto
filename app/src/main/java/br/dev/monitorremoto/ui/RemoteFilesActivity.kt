package br.dev.monitorremoto.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.content.res.ColorStateList
import android.text.TextUtils
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.os.Build
import android.os.SystemClock
import android.content.ContentValues
import android.provider.OpenableColumns
import android.widget.ProgressBar
import android.widget.ImageView
import android.widget.CheckBox
import android.util.Base64
import java.security.MessageDigest
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import br.dev.monitorremoto.core.RecentDevices
import br.dev.monitorremoto.core.RemoteBatchPolicy
import br.dev.monitorremoto.core.RemoteFilePolicy
import br.dev.monitorremoto.core.RemoteManagePolicy
import br.dev.monitorremoto.core.SettingsStore
import br.dev.monitorremoto.core.RemoteOperationHistory
import br.dev.monitorremoto.core.RemoteOperationHistoryPolicy
import br.dev.monitorremoto.transport.RemoteClient
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/** Gerenciador SAF bidirecional: apenas a árvore autorizada e operações específicas autenticadas. */
class RemoteFilesActivity : Activity() {
    companion object {
        const val EXTRA_HOST = "host"
        const val EXTRA_METHOD = "method"
        private const val CREATE_DOCUMENT = 9045
        private const val CHOOSE_FOLDER = 9046
        private const val PICK_UPLOADS = 9047
        private const val MAX_BATCH_ITEMS = 20
        private const val MAX_BATCH_BYTES = 2L * 1024L * 1024L * 1024L
    }

    private val main = Handler(Looper.getMainLooper())
    private var host = ""
    private var method = "Chave salva"
    private var path = emptyList<String>()
    private var generation = 0
    private var copying = false
    private val cancel = AtomicBoolean(false)
    private val paused = AtomicBoolean(false)
    private var writeAllowed = false
    private var currentTransferName: TextView? = null
    private var currentTransferStage: TextView? = null
    private var currentTransferPercent: TextView? = null
    private var currentTransferAmount: TextView? = null
    private var currentTransferSpeed: TextView? = null
    private var currentTransferEta: TextView? = null
    private var currentTransferElapsed: TextView? = null
    private var currentTransferBar: ProgressBar? = null
    private var progressStartedAt = 0L
    private var lastProgressAt = 0L
    private var pausedSinceAt = 0L
    private var lastTransferItem = ""
    private var lastTransferBytes = 0L
    private val speedSamples = java.util.ArrayDeque<Pair<Long, Long>>()
    private var chooseDownloads = false
    private var key: ByteArray? = null
    private var entries = listOf<JSONObject>()
    private val selected = linkedSetOf<String>()
    private var selecting = false
    private var gridMode = false
    private var sortMode = 0
    private var queryText = ""
    private var pendingFile: JSONObject? = null
    private var pendingPath = emptyList<String>()
    private var batchFiles = emptyList<JSONObject>()
    private var batchPath = emptyList<String>()
    private var folderToCopy: String? = null
    private var folderParent = emptyList<String>()

    private lateinit var frame: LinearLayout
    private lateinit var content: LinearLayout
    private lateinit var listArea: LinearLayout
    private lateinit var selectionBar: LinearLayout
    private lateinit var status: TextView
    private lateinit var count: TextView
    private lateinit var search: EditText
    private var selectButton: Button? = null

    private val dark get() = when (SettingsStore.theme(this)) {
        "dark" -> true
        "system" -> resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        else -> false
    }
    private val foreground get() = if (dark) Color.WHITE else Color.rgb(24, 34, 46)
    private val muted get() = if (dark) Color.rgb(172, 188, 205) else Color.rgb(103, 116, 130)
    private val backgroundColor get() = if (dark) Color.rgb(15, 23, 34) else Color.rgb(247, 248, 251)
    private val panelColor get() = if (dark) Color.rgb(29, 40, 54) else Color.WHITE
    private val borderColor get() = if (dark) Color.rgb(66, 80, 99) else Color.rgb(224, 230, 237)
    private val accent get() = if (dark) Color.rgb(128, 190, 245) else Color.rgb(41, 103, 193)
    private val softColor get() = if (dark) Color.rgb(39, 52, 68) else Color.rgb(238, 242, 247)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun rounded(color: Int = panelColor, radius: Int = 16, border: Boolean = true) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
        if (border) setStroke(dp(1), borderColor)
    }
    private fun label(value: String, size: Float = 14f, bold: Boolean = false) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(this@RemoteFilesActivity.foreground)
        if (bold) typeface = Typeface.DEFAULT_BOLD
        setPadding(0, dp(3), 0, dp(3))
    }
    private fun button(title: String, primary: Boolean = false, action: () -> Unit) = Button(this).apply {
        text = title
        isAllCaps = false
        textSize = 13f
        minHeight = dp(46)
        elevation = 0f
        stateListAnimator = null
        setTextColor(if (primary) Color.WHITE else this@RemoteFilesActivity.foreground)
        background = rounded(if (primary) accent else panelColor, 14, !primary)
        setOnClickListener { action() }
    }
    private fun add(view: View, bottom: Int = 10) {
        content.addView(view, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(bottom) })
    }
    private fun horizontal(vararg views: View): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        views.forEachIndexed { idx, view ->
            addView(view, LinearLayout.LayoutParams(0, -2, 1f).apply {
                if (idx > 0) marginStart = dp(10)
            })
        }
    }
    private fun panel(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(14), dp(16), dp(14))
        background = rounded(panelColor, 19)
    }
    private fun goBack() {
        if (copying) { cancel.set(true); paused.set(false); status.text = "Cancelando transferência…"; return }
        if (selecting) { selecting = false; selected.clear(); renderItems(); return }
        if (path.isNotEmpty()) { path = path.dropLast(1); listFolder() } else finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        host = intent.getStringExtra(EXTRA_HOST).orEmpty()
        method = intent.getStringExtra(EXTRA_METHOD).orEmpty().ifBlank { "Chave salva" }
        key = RecentDevices.key(this, host)
        window.statusBarColor = backgroundColor
        window.navigationBarColor = backgroundColor
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = if (dark) 0 else
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        frame = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(backgroundColor)
            // Android 15/16 pode desenhar sob a barra de status (edge-to-edge).
            // Respeitar insets impede que o título encoste no relógio/notificações.
            setOnApplyWindowInsetsListener { view, insets ->
                @Suppress("DEPRECATION")
                view.setPadding(0, insets.systemWindowInsetTop, 0, insets.systemWindowInsetBottom)
                insets
            }
        }
        val scroll = ScrollView(this).apply { isFillViewport = true; clipToPadding = false }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(17), dp(16), dp(24))
        }
        scroll.addView(content)
        frame.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        selectionBar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            background = rounded(panelColor, 14)
            setPadding(dp(14), dp(8), dp(14), dp(12))
        }
        frame.addView(selectionBar, LinearLayout.LayoutParams(-1, -2).apply {
            leftMargin = dp(16); rightMargin = dp(16); bottomMargin = dp(10)
        })
        setContentView(frame)
        frame.requestApplyInsets()
        if (host.isBlank() || key == null) showProblem("Conecte primeiro um aparelho pareado para recuperar arquivos.")
        else listFolder()
    }

    private fun deviceTitle(): String = RecentDevices.list(this)
        .firstOrNull { it.host.equals(host, ignoreCase = true) }?.name
        ?.takeIf { it.isNotBlank() } ?: "Telefone remoto"

    private fun screenShell() {
        content.removeAllViews()
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(button("‹", action = { goBack() }).apply {
            textSize = 22f; contentDescription = "Voltar"
        }, LinearLayout.LayoutParams(dp(52), dp(52)))
        top.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(label("Recuperar dados", 22f, true))
            addView(label(deviceTitle(), 12f).apply { setTextColor(muted); maxLines = 1 })
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(10) })
        top.addView(button("?", action = { showHelp() }).apply {
            textSize = 19f; contentDescription = "Ajuda sobre arquivos compartilhados"
        }, LinearLayout.LayoutParams(dp(48), dp(52)))
        top.addView(button("↻", action = { if (!copying) listFolder() }).apply {
            textSize = 19f; contentDescription = "Atualizar lista"
        }, LinearLayout.LayoutParams(dp(48), dp(52)).apply { leftMargin = dp(8) })
        add(top, 8)
        add(horizontal(
            button("◷ Histórico") { showHistory() },
            button("ⓘ Ajuda") { showHelp() }
        ), 8)
        status = label("", 13f).apply { setTextColor(muted) }
        add(status, 9)
    }

    private fun logOperation(operation: String, success: Boolean, done: Int, total: Int, bytes: Long = 0L) {
        RemoteOperationHistory.record(applicationContext, host, operation,
            RemoteOperationHistoryPolicy.outcome(success, cancel.get(), done), done, total, bytes)
    }

    private fun showHistory() {
        val events = RemoteOperationHistory.read(this, host)
        val body = panel().apply {
            addView(label("Histórico neste controlador", 17f, true))
            addView(label("Apenas tipo, resultado, quantidade, data e tamanho. Nomes, caminhos, IPs e conteúdo não são guardados. Últimos 48 eventos, até 30 dias.", 12f).apply { setTextColor(muted) })
            if (events.isEmpty()) addView(label("Nenhuma operação finalizada registrada para este aparelho.", 13f))
            events.take(20).forEach { event ->
                val date = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(event.timestamp))
                addView(label("${RemoteOperationHistoryPolicy.label(event.operation)} · ${event.outcome} · ${event.done}/${event.total}", 13f, true))
                addView(label("$date${if (event.bytes > 0L) " · ${formatBytes(event.bytes)}" else ""}", 11f).apply { setTextColor(muted) })
            }
        }
        val scroll = ScrollView(this).apply { addView(body) }
        AlertDialog.Builder(this).setTitle("Atividades de arquivos").setView(scroll)
            .setNeutralButton("Limpar histórico") { _, _ ->
                AlertDialog.Builder(this).setTitle("Apagar histórico local?")
                    .setMessage("Apaga apenas os registros deste aparelho neste controlador. Não modifica arquivos remotos.")
                    .setNegativeButton("Cancelar", null)
                    .setPositiveButton("Apagar") { _, _ -> RemoteOperationHistory.clear(this, host); status.text = "Histórico local apagado." }
                    .show()
            }
            .setPositiveButton("Fechar", null).show()
    }

    private fun showHelp() {
        val body = panel().apply {
            addView(label("Arquivos compartilhados", 19f, true))
            addView(label("A pasta é escolhida no monitorado. Download requer leitura; envio, criação, movimentação e exclusão requerem autorização EXTRA de escrita, desligada por padrão.", 14f))
            addView(label("Para autorizar: no monitorado, abra Monitor Remoto → Configurações → Arquivos e recuperação → Escolher pasta.", 14f))
            addView(label("Até 2 GiB por arquivo/lote, 20 arquivos por lote, 150 itens por pasta. A transferência pausa apenas enquanto esta tela existir, e é cancelada ao fechá-la. Downloads pode ser usado como destino direto no Android 10+. Exclusão remota pode ser definitiva.", 12f).apply { setTextColor(muted) })
        }
        AlertDialog.Builder(this).setView(body).setPositiveButton("Entendi", null).show()
    }

    private fun showProblem(message: String) {
        screenShell()
        status.text = message
        add(button("Voltar ao aparelho") { finish() })
    }
    private fun showListingError(message: String) {
        screenShell()
        val box = panel().apply {
            addView(label("Pasta ainda indisponível", 18f, true))
            addView(label("O monitorado ainda não compartilhou uma pasta, ou a autorização não está mais disponível.", 13f).apply { setTextColor(muted) })
            addView(button("Como autorizar?", action = { showHelp() }))
        }
        add(box)
        status.text = "Detalhes: ${message.take(160)}"
        add(button("Tentar novamente", primary = true) { listFolder() })
        add(button("Voltar ao aparelho") { finish() })
    }

    private fun listFolder() {
        if (copying) return
        val token = ++generation
        selected.clear(); selecting = false
        screenShell()
        selectionBar.visibility = View.GONE
        status.text = "Atualizando lista…"
        val current = path.toList()
        val secret = key ?: return
        thread(name = "MonitorRemoto-data-list") {
            val result = runCatching { RemoteClient.dataList(host, secret, applicationContext, method, current) }
            main.post {
                if (isFinishing || isDestroyed || token != generation || copying) return@post
                result.onSuccess { response ->
                    val json = response.optJSONArray("entries")
                    entries = if (json == null) emptyList() else (0 until json.length()).mapNotNull { index ->
                        json.optJSONObject(index)?.takeIf { RemoteFilePolicy.validSegment(it.optString("name")) }
                    }
                    writeAllowed = response.optBoolean("writable", false)
                    showListing(response.optBoolean("truncated"))
                }.onFailure { showListingError(it.message ?: "Sem resposta do aparelho remoto.") }
            }
        }
    }

    private fun showListing(truncated: Boolean) {
        screenShell()
        val previous = RemoteOperationHistory.read(this, host).firstOrNull()
        val crumb = panel().apply {
            addView(label(if (path.isEmpty()) "Pasta compartilhada" else "Pasta /${path.joinToString("/")}", 14f, true))
            addView(label("${if (writeAllowed) "✓ Leitura e escrita autorizadas" else "◉ Somente leitura"} · ${entries.size} itens" +
                if (truncated) " · lista limitada" else "", 12f).apply { setTextColor(muted) })
            if (previous != null) addView(label("Última operação: ${RemoteOperationHistoryPolicy.label(previous.operation)} · ${previous.outcome}", 11f)
                .apply { setTextColor(muted) })
        }
        add(crumb, 7)
        if (writeAllowed) add(horizontal(
            button("⬆ Enviar") { pickUploadFiles() },
            button("＋ Nova pasta") { askNewFolder() }
        ), 7)
        selectButton = button(if (selecting) "✓ Cancelar" else "☑ Selecionar", action = {
            selecting = !selecting; selected.clear(); renderItems()
        })
        val controls = horizontal(
            button("⇅ Ordenar", action = { chooseSort() }),
            button(if (gridMode) "☰ Lista" else "▦ Grade", action = { gridMode = !gridMode; renderItems() }),
            selectButton!!
        )
        add(controls)
        search = EditText(this).apply {
            setSingleLine(true)
            hint = "Buscar nesta pasta"
            setTextColor(this@RemoteFilesActivity.foreground)
            setHintTextColor(muted)
            textSize = 14f
            setPadding(dp(15), dp(10), dp(15), dp(10))
            background = rounded(softColor, 14)
            setText(queryText)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, st: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    queryText = s?.toString().orEmpty(); renderItems()
                }
                override fun afterTextChanged(s: Editable?) {}
            })
        }
        add(search)
        count = label("", 12f).apply { setTextColor(muted) }
        add(count, 4)
        listArea = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        add(listArea)
        if (path.isNotEmpty()) add(button("‹ Pasta anterior") { goBack() })
        status.text = "Menu ⋮: copiar, informações e gerenciar. Downloads direto disponível por arquivo."
        renderItems()
    }

    private fun chooseSort() {
        val names = arrayOf("Nome", "Mais recentes", "Maiores primeiro", "Tipo")
        AlertDialog.Builder(this).setTitle("Ordenar arquivos por")
            .setSingleChoiceItems(names, sortMode) { dialog, value -> sortMode = value; dialog.dismiss(); renderItems() }
            .setNegativeButton("Cancelar", null).show()
    }

    private fun entrySize(item: JSONObject): String {
        val value = item.optLong("size", -1)
        return if (value < 0) "Tamanho indisponível" else when {
            value >= 1024L * 1024L -> String.format(Locale.getDefault(), "%.1f MiB", value / (1024.0 * 1024.0))
            value >= 1024 -> String.format(Locale.getDefault(), "%.1f KiB", value / 1024.0)
            else -> "$value B"
        }
    }
    private fun entryDate(item: JSONObject): String {
        val modified = item.optLong("modified", 0L)
        return if (modified <= 0) "Data indisponível" else DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(modified))
    }
    private fun entryIcon(item: JSONObject): String {
        if (item.optBoolean("folder")) return "📁"
        val mime = item.optString("mime")
        return when {
            mime.startsWith("image/") -> "🖼"
            mime.startsWith("video/") -> "🎬"
            mime.startsWith("audio/") -> "♫"
            mime == "application/pdf" -> "▤"
            mime.startsWith("text/") -> "☷"
            else -> "📄"
        }
    }
    private fun fileInfo(item: JSONObject) {
        val name = item.optString("name")
        val parent = if (path.isEmpty()) "/" else "/${path.joinToString("/")}/"
        val builder = AlertDialog.Builder(this).setTitle(name).setMessage(
            "Tipo: ${if (item.optBoolean("folder")) "Pasta" else item.optString("mime").ifBlank { "Arquivo" }}\n" +
                "Tamanho: ${entrySize(item)} (${item.optLong("size", -1)} bytes)\n" +
                "Modificado: ${entryDate(item)}\nLocal: $parent\n" +
                "Permissão: ${if (writeAllowed) "Leitura e gerenciamento" else "Somente leitura"}\n" +
                "Formato: ${item.optString("mime")}\n" +
                "Recursos do provedor: ${item.optInt("flags")} (flags Android).\n" +
                "Mover, copiar ou excluir depende do provedor de documentos."
        ).setPositiveButton("Fechar", null)
        if (!item.optBoolean("folder") && item.optLong("size", 0L) > 0) {
            builder.setNeutralButton("SHA-256") { _, _ -> showRemoteHash(item) }
        }
        builder.show()
    }
    private fun showRemoteHash(item: JSONObject) {
        val secret = key ?: return
        status.text = "Calculando SHA-256 no monitorado; arquivos grandes podem demorar…"
        val location = path + item.optString("name")
        thread(name = "MonitorRemoto-file-sha") {
            val result = runCatching { RemoteClient.dataHash(host, secret, applicationContext, method, location,
                item.optLong("size", -1), item.optLong("modified", -1)) }
            main.post { if (!isFinishing && !isDestroyed) result.onSuccess { digest ->
                status.text = "SHA-256 calculado"
                AlertDialog.Builder(this).setTitle("SHA-256 · ${item.optString("name")}")
                    .setMessage(digest).setPositiveButton("Fechar", null)
                    .setNeutralButton("Copiar") { _, _ ->
                        (getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                            .setPrimaryClip(android.content.ClipData.newPlainText("SHA-256", digest))
                    }.show()
            }.onFailure { status.text = "Não foi possível calcular SHA-256: ${it.message}" } }
        }
    }

    private fun renderItems() {
        if (!this::listArea.isInitialized || copying) return
        listArea.removeAllViews()
        selectButton?.text = if (selecting) "✓ Cancelar" else "☑ Selecionar"
        val filtered = entries.filter { it.optString("name").contains(queryText, ignoreCase = true) }
        val comparator: Comparator<JSONObject> = when (sortMode) {
            1 -> compareByDescending<JSONObject> { it.optLong("modified", 0L) }.thenBy { it.optString("name").lowercase(Locale.ROOT) }
            2 -> compareByDescending<JSONObject> { it.optLong("size", -1L) }.thenBy { it.optString("name").lowercase(Locale.ROOT) }
            3 -> compareBy<JSONObject> { it.optString("mime").lowercase(Locale.ROOT) }.thenBy { it.optString("name").lowercase(Locale.ROOT) }
            else -> compareBy<JSONObject> { it.optString("name").lowercase(Locale.ROOT) }
        }
        val ordered = filtered.sortedWith(compareBy<JSONObject> { !it.optBoolean("folder") }.then(comparator))
        if (this::count.isInitialized) count.text = "${ordered.size} itens" + if (selecting) " · SELEÇÃO ATIVA (${selected.size})" else ""
        if (ordered.isEmpty()) listArea.addView(label(if (entries.isEmpty()) "Pasta vazia." else "Nenhum resultado para a busca."))
        else if (!gridMode) ordered.forEach { item ->
            listArea.addView(entryCard(item, false), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        } else ordered.chunked(2).forEach { pair ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            pair.forEachIndexed { index, item ->
                row.addView(entryCard(item, true), LinearLayout.LayoutParams(0, -2, 1f).apply {
                    if (index > 0) marginStart = dp(9)
                })
            }
            if (pair.size == 1) row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f).apply { marginStart = dp(9) })
            listArea.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(9) })
        }
        updateSelectionBar()
    }

    private fun entryCard(item: JSONObject, grid: Boolean): View {
        val name = item.optString("name")
        val folder = item.optBoolean("folder")
        val duplicated = entries.count { it.optString("name") == name } > 1
        val chosen = selected.contains(name)
        val box = LinearLayout(this).apply {
            orientation = if (grid) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(13), dp(12), dp(13), dp(12))
            background = rounded(if (chosen) softColor else panelColor, 15)
            isClickable = true; isFocusable = true
        }
        if (selecting) box.addView(CheckBox(this).apply {
            isChecked = chosen
            contentDescription = "Selecionar $name"
            setOnClickListener { toggleSelection(item) }
        })
        val icon = label(entryIcon(item), if (grid) 29f else 25f).apply {
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 0)
        }
        box.addView(icon, if (grid) LinearLayout.LayoutParams(-1, dp(48)) else LinearLayout.LayoutParams(dp(44), dp(44)))
        val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        info.addView(label(name + if (duplicated) " ⚠ nome repetido" else "", 14f, true).apply { maxLines = 2 })
        info.addView(label(if (folder) "Pasta" else entrySize(item), 12f).apply { setTextColor(muted) })
        if (!folder) info.addView(label(entryDate(item), 11f).apply { setTextColor(muted); maxLines = 1 })
        box.addView(info, if (grid) LinearLayout.LayoutParams(-1, -2) else LinearLayout.LayoutParams(0, -2, 1f))
        if (!grid) box.addView(button(if (chosen) "✓" else "⋮") {
            if (selecting) toggleSelection(item) else openActions(item)
        }, LinearLayout.LayoutParams(dp(48), dp(46)))
        box.setOnClickListener {
            if (selecting) toggleSelection(item)
            else if (folder) openFolder(name)
            else openActions(item)
        }
        box.setOnLongClickListener {
            selecting = true; toggleSelection(item)
            true
        }
        return box
    }

    private fun openFolder(name: String) {
        if (entries.count { it.optString("name") == name } > 1) {
            status.text = "Nome de pasta repetido: acesso bloqueado para evitar pasta errada."; return
        }
        if (path.size >= RemoteFilePolicy.MAX_DEPTH) { status.text = "Limite de 12 níveis de pastas."; return }
        path = path + name
        queryText = ""
        listFolder()
    }
    /** Menu nativo do aplicativo, com ícones e ações contextualizadas. */
    private fun openActions(item: JSONObject) {
        val name = item.optString("name")
        if (entries.count { it.optString("name") == name } != 1) {
            status.text = "Nome repetido: renomeie diretamente no aparelho de origem."; return
        }
        val isFolder = item.optBoolean("folder")
        val holder = panel().apply { setPadding(dp(10), dp(8), dp(10), dp(8)) }
        holder.addView(label("${entryIcon(item)} $name", 15f, true).apply { maxLines = 3 })
        holder.addView(label(if (isFolder) "Pasta" else "${entrySize(item)} · ${entryDate(item)}", 12f).apply {
            setTextColor(muted)
        })
        var menu: AlertDialog? = null
        fun option(icon: Int, caption: String, action: () -> Unit) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(8), dp(12), dp(8))
                isClickable = true; isFocusable = true
                background = rounded(softColor, 12)
            }
            row.addView(ImageView(this).apply {
                setImageResource(icon)
                imageTintList = android.content.res.ColorStateList.valueOf(accent)
                contentDescription = null
            }, LinearLayout.LayoutParams(dp(28), dp(28)))
            row.addView(label(caption, 13f, true), LinearLayout.LayoutParams(-1, -2).apply {
                marginStart = dp(12)
            })
            row.setOnClickListener { menu?.dismiss(); action() }
            holder.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(7) })
        }
        if (isFolder) {
            option(android.R.drawable.ic_menu_view, "Abrir pasta") { openFolder(name) }
            option(android.R.drawable.ic_menu_save, "Baixar pasta e subpastas") { chooseFolderDestination(name) }
        } else {
            option(android.R.drawable.ic_menu_save, "Baixar neste celular") { chooseDestination(item, path) }
            option(android.R.drawable.ic_menu_save, "Salvar em Downloads") { saveDownloads(item) }
            option(android.R.drawable.ic_menu_view, "Baixar e visualizar") { saveDownloads(item, "view") }
            option(android.R.drawable.ic_menu_share, "Baixar e compartilhar") { saveDownloads(item, "share") }
        }
        option(android.R.drawable.ic_menu_info_details, "Informações") { fileInfo(item) }
        option(android.R.drawable.ic_menu_agenda, "Selecionar") { selecting = true; toggleSelection(item) }
        if (writeAllowed) {
            option(android.R.drawable.ic_menu_edit, "Renomear") { askRename(item) }
            option(android.R.drawable.ic_menu_directions, "Mover para subpasta") {
                chooseRemoteFolder("move", listOf(name))
            }
            option(android.R.drawable.ic_menu_add, "Copiar dentro da pasta remota") {
                chooseRemoteFolder("copy", listOf(name))
            }
            option(android.R.drawable.ic_menu_delete, "Mover para lixeira (se suportado)") {
                runBatchAction("trash", listOf(name), emptyList())
            }
            option(android.R.drawable.ic_menu_delete, "Excluir definitivamente") {
                confirmBatchDelete(listOf(item))
            }
        }
        menu = AlertDialog.Builder(this).setView(holder).setNegativeButton("Fechar", null).create()
        menu.show()
    }
    private fun toggleSelection(item: JSONObject) {
        val name = item.optString("name")
        if (entries.count { it.optString("name") == name } > 1) {
            status.text = "Arquivo ambíguo: renomeie o duplicado na origem."; return
        }
        if (!selected.remove(name)) {
            if (selected.size >= MAX_BATCH_ITEMS) { status.text = "Máximo de $MAX_BATCH_ITEMS arquivos por lote."; return }
            selected.add(name)
        }
        renderItems()
    }
    private fun updateSelectionBar() {
        selectionBar.removeAllViews()
        if (!selecting) { selectionBar.visibility = View.GONE; return }
        selectionBar.visibility = View.VISIBLE
        val chosen = entries.filter { selected.contains(it.optString("name")) }
        val total = chosen.sumOf { it.optLong("size", -1L).coerceAtLeast(0L) }
        selectionBar.addView(label("${chosen.size} selecionado(s) · ${formatBytes(total)}", 13f, true))
        selectionBar.addView(horizontal(
            button("Todos") { selected.clear(); entries.take(MAX_BATCH_ITEMS).forEach { selected.add(it.optString("name")) }; renderItems() },
            button("Limpar") { selected.clear(); renderItems() },
            button("Baixar", primary = true) { chooseBatchDestination() }
        ))
        if (Build.VERSION.SDK_INT >= 29) selectionBar.addView(button("↓ Salvar seleção em Downloads") {
            copyBatchDownloads(chosen)
        })
        if (writeAllowed) selectionBar.addView(horizontal(
            button("Mover") { chooseRemoteFolder("move", chosen.map { it.optString("name") }) },
            button("Copiar aqui") { chooseRemoteFolder("copy", chosen.map { it.optString("name") }) },
            button("Lixeira") { confirmBatchTrash(chosen) },
            button("Excluir") { confirmBatchDelete(chosen) }
        ))
    }
    private fun formatBytes(value: Long): String = if (value >= 1024L * 1024L)
        String.format(Locale.getDefault(), "%.1f MiB", value / (1024.0 * 1024.0)) else
        String.format(Locale.getDefault(), "%.1f KiB", value / 1024.0)

    private fun chooseFolderDestination(name: String) {
        if (copying || !RemoteFilePolicy.validSegment(name) ||
            entries.count { it.optString("name") == name } != 1) return
        folderToCopy = name
        folderParent = path.toList()
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }, CHOOSE_FOLDER)
        } catch (error: Exception) {
            folderToCopy = null
            status.text = "Não foi possível escolher destino: ${error.message}"
        }
    }

    private fun chooseBatchDestination() {
        if (copying) return
        val chosen = entries.filter { !it.optBoolean("folder") && it.optString("name") in selected }
        if (selected.size != chosen.size) { status.text = "Para baixar pastas, use o menu ⋮ da pasta individual."; return }
        val sizes = chosen.map { it.optLong("size", -1L) }
        if (!RemoteBatchPolicy.eligible(sizes) ||
            chosen.map { it.optString("name") }.distinct().size != chosen.size) {
            status.text = "Selecione até $MAX_BATCH_ITEMS arquivos, com tamanho conhecido e até 2 GiB no total. Pastas devem ser baixadas pelo menu individual."
            return
        }
        batchFiles = chosen.map { JSONObject(it.toString()) }
        batchPath = path.toList()
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }, CHOOSE_FOLDER)
        } catch (error: Exception) {
            batchFiles = emptyList(); status.text = "Não foi possível abrir o seletor de pasta: ${error.message}"
        }
    }

    private fun chooseDestination(file: JSONObject, parent: List<String>) {
        if (copying) return
        val name = file.optString("name")
        val size = file.optLong("size", -1L)
        if (!RemoteFilePolicy.validSegment(name) || size !in 1..RemoteFilePolicy.MAX_FILE_BYTES) {
            status.text = "Arquivo sem tamanho conhecido ou acima de 2 GiB."
            return
        }
        pendingFile = JSONObject(file.toString())
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
            pendingFile = null; status.text = "Seletor indisponível: ${error.message}"
        }
    }

    @Deprecated("Compatibilidade com seletores SAF")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            PICK_UPLOADS -> {
                if (resultCode != RESULT_OK || data == null) { status.text = "Envio cancelado."; return }
                val uris = mutableListOf<Uri>()
                data.data?.let { uris.add(it) }
                data.clipData?.let { clip -> for (i in 0 until clip.itemCount) {
                    val uri = clip.getItemAt(i).uri
                    if (uri !in uris) uris.add(uri)
                } }
                if (uris.size > MAX_BATCH_ITEMS) status.text = "Selecione até $MAX_BATCH_ITEMS arquivos."
                else uploadSelected(uris)
            }
            CREATE_DOCUMENT -> {
                val file = pendingFile
                pendingFile = null
                if (resultCode != RESULT_OK || data?.data == null || file == null) {
                    status.text = "Operação cancelada."; return
                }
                copyOne(data.data!!, file, pendingPath)
            }
            CHOOSE_FOLDER -> {
                val files = batchFiles
                val folder = folderToCopy
                folderToCopy = null
                batchFiles = emptyList()
                if (resultCode != RESULT_OK || data?.data == null || (files.isEmpty() && folder == null) ||
                    data.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION == 0) {
                    status.text = "Nenhuma pasta de destino autorizada."; return
                }
                if (folder != null) copyRemoteFolder(data.data!!, folderParent + folder, folder)
                else copyBatch(data.data!!, files, batchPath)
            }
        }
    }

    /** Cria nomes novos com sufixo, nunca sobrescreve arquivo preexistente do destino. */
    private fun safeNewDocumentName(tree: Uri, desired: String,
        parentId: String = DocumentsContract.getTreeDocumentId(tree)): String {
        val resolver = contentResolver
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        val used = mutableSetOf<String>()
        resolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) cursor.getString(0)?.let { used.add(it.lowercase(Locale.ROOT)) }
        } ?: error("Não foi possível conferir os arquivos existentes no destino.")
        return RemoteBatchPolicy.availableName(desired, used)
    }

    private data class RemoteFolderEntry(val relativePath: List<String>, val data: JSONObject?)

    /** Pasta inteira, até os mesmos limites de lote. Pré-validação antes de criar arquivos. */
    private fun collectRemoteFolder(path: List<String>): List<RemoteFolderEntry> {
        val secret = key ?: error("Conexão sem chave.")
        val result = ArrayList<RemoteFolderEntry>()
        var total = 0L
        var countFiles = 0
        fun visit(current: List<String>, relative: List<String>) {
            check(!cancel.get()) { "Cópia cancelada." }
            check(current.size <= RemoteFilePolicy.MAX_DEPTH) { "Pasta além do limite de 12 níveis." }
            check(SettingsStore.getRemoteHost(applicationContext) == host) { "O aparelho selecionado mudou." }
            val response = RemoteClient.dataList(host, secret, applicationContext, method, current)
            check(!response.optBoolean("truncated")) { "Pasta com mais de 150 itens: cópia completa não garantida." }
            val list = response.optJSONArray("entries") ?: error("Listagem ausente.")
            val unique = HashSet<String>()
            for (index in 0 until list.length()) {
                val item = list.optJSONObject(index) ?: error("Documento inválido.")
                val name = item.optString("name")
                check(RemoteFilePolicy.validSegment(name) && unique.add(name)) { "Nome repetido ou inválido: $name" }
                val branch = relative + name
                if (item.optBoolean("folder")) {
                    result.add(RemoteFolderEntry(branch, null))
                    check(result.size <= 200) { "Pasta com muitos diretórios: limite de 200 itens no resgate." }
                    visit(current + name, branch)
                } else {
                    val length = item.optLong("size", -1)
                    check(length in 1..RemoteFilePolicy.MAX_FILE_BYTES) { "Arquivo não permitido: $name" }
                    total += length
                    check(total <= RemoteBatchPolicy.MAX_TOTAL_BYTES) { "Pasta acima de 2 GiB." }
                    result.add(RemoteFolderEntry(branch, JSONObject(item.toString())))
                    countFiles++
                    check(countFiles <= RemoteBatchPolicy.MAX_FILES && result.size <= 200) {
                        "A pasta excede 20 arquivos ou 200 itens no total."
                    }
                }
            }
        }
        visit(path, emptyList())
        return result
    }

    private fun copyRemoteFolder(tree: Uri, sourceRoot: List<String>, title: String) {
        if (copying) return
        copying = true; cancel.set(false); paused.set(false); ++generation
        screenShell()
        showTransferPanel("Conferindo pasta remota")
        thread(name = "MonitorRemoto-data-folder") {
            var createdRoot: Uri? = null
            var finished = 0
            var totalFiles = 0
            val result = runCatching {
                val items = collectRemoteFolder(sourceRoot)
                totalFiles = items.count { it.data != null }
                check(totalFiles > 0) { "Pasta sem arquivos válidos para copiar." }
                check(!cancel.get()) { "Cópia cancelada." }
                val parentId = DocumentsContract.getTreeDocumentId(tree)
                val rootParent = DocumentsContract.buildDocumentUriUsingTree(tree, parentId)
                val targetName = safeNewDocumentName(tree, title)
                val newRoot = DocumentsContract.createDocument(contentResolver, rootParent,
                    DocumentsContract.Document.MIME_TYPE_DIR, targetName)
                    ?: error("Não foi possível criar pasta de destino.")
                createdRoot = newRoot
                val destinations = hashMapOf<List<String>, Uri>(emptyList<String>() to newRoot)
                for (entry in items) {
                    check(!cancel.get()) { "Cópia cancelada." }
                    val parent = entry.relativePath.dropLast(1)
                    val parentUri = destinations[parent] ?: error("Subpasta de destino ausente.")
                    val parentDocumentId = DocumentsContract.getDocumentId(parentUri)
                    val name = entry.relativePath.last()
                    val safeName = safeNewDocumentName(tree, name, parentDocumentId)
                    if (entry.data == null) {
                        val folderUri = DocumentsContract.createDocument(contentResolver, parentUri,
                            DocumentsContract.Document.MIME_TYPE_DIR, safeName)
                            ?: error("Não foi possível criar subpasta $name.")
                        destinations[entry.relativePath] = folderUri
                    } else {
                        val mime = entry.data.optString("mime").takeIf { '/' in it && it.length in 3..110 }
                            ?: "application/octet-stream"
                        val uri = DocumentsContract.createDocument(contentResolver, parentUri, mime, safeName)
                            ?: error("Não foi possível criar $name no destino.")
                        transferTo(uri, entry.data, sourceRoot + entry.relativePath) { written, size ->
                            main.post { if (!isDestroyed && !isFinishing)
                                updateProgress(written, size, "${finished + 1}/$totalFiles · $name") }
                        }
                        finished++
                    }
                }
            }
            logOperation("download", result.isSuccess, finished, totalFiles.coerceAtLeast(1))
            if (result.isFailure) createdRoot?.let { uri ->
                // Apenas diretório que acabamos de criar, nunca outros documentos do usuário.
                runCatching { DocumentsContract.deleteDocument(contentResolver, uri) }
            }
            main.post {
                if (isFinishing || isDestroyed) return@post
                copying = false
                screenShell()
                status.text = if (result.isSuccess) "Pasta copiada: $finished arquivo(s)." else
                    "Falha na cópia da pasta: ${result.exceptionOrNull()?.message}. " +
                        "Se o provedor não removeu os parciais, confira o destino."
                add(button("Voltar aos arquivos", primary = true) { listFolder() })
            }
        }
    }

    private fun copyBatch(tree: Uri, files: List<JSONObject>, parent: List<String>) {
        if (copying) return
        copying = true; cancel.set(false); paused.set(false)
        ++generation
        screenShell()
        showTransferPanel("Preparando ${files.size} arquivos")
        if (key == null) { copying = false; showProblem("Chave de conexão indisponível."); return }
        thread(name = "MonitorRemoto-data-batch") {
            var copied = 0
            var completedBytes = 0L
            val result = runCatching {
                val parentId = DocumentsContract.getTreeDocumentId(tree)
                val copiedNames = mutableSetOf<String>()
                files.forEachIndexed { index, file ->
                    check(!cancel.get()) { "Cópia cancelada." }
                    val name = file.optString("name")
                    check(RemoteFilePolicy.validSegment(name) && name !in copiedNames) { "Nome inválido ou duplicado." }
                    copiedNames.add(name)
                    val mime = file.optString("mime").takeIf { it.length in 3..110 && '/' in it } ?: "application/octet-stream"
                    val targetName = safeNewDocumentName(tree, name)
                    val uri = DocumentsContract.createDocument(contentResolver,
                        DocumentsContract.buildDocumentUriUsingTree(tree, parentId), mime, targetName)
                        ?: error("O Android não permitiu criar $name no destino.")
                    try {
                        transferTo(uri, file, parent + name) { written, total ->
                            main.post {
                                if (!isDestroyed && !isFinishing) updateProgress(written, total, "${index + 1}/${files.size} · $name")
                            }
                        }
                    } catch (error: Exception) {
                        runCatching { DocumentsContract.deleteDocument(contentResolver, uri) }
                        throw error
                    }
                    copied++
                    completedBytes += file.optLong("size", 0L)
                }
            }
            logOperation("download", result.isSuccess, copied, files.size, completedBytes)
            main.post {
                if (isFinishing || isDestroyed) return@post
                copying = false; selected.clear(); selecting = false
                screenShell()
                status.text = if (result.isSuccess) "Cópia concluída: $copied arquivos (${formatBytes(completedBytes)})." else
                    "Copiados: $copied de ${files.size}. ${result.exceptionOrNull()?.message ?: "Falha de transferência"}. " +
                        "Confira se existe algum arquivo parcial no destino."
                add(button("Voltar aos arquivos", primary = true) { listFolder() })
            }
        }
    }

    private fun copyOne(destination: Uri, file: JSONObject, source: List<String>, inDownloads: Boolean = false,
        nextAction: String = "") {
        if (copying) return
        copying = true; cancel.set(false); paused.set(false); ++generation
        screenShell()
        showTransferPanel("Copiando ${file.optString("name")}")
        thread(name = "MonitorRemoto-data-copy") {
            val result = runCatching { transferTo(destination, file, source) { done, total ->
                main.post { if (!isFinishing && !isDestroyed) updateProgress(done, total, file.optString("name")) }
            } }
            val published = if (result.isSuccess && inDownloads && Build.VERSION.SDK_INT >= 29)
                runCatching { contentResolver.update(destination, ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }, null, null) == 1 }.getOrDefault(false) else result.isSuccess
            if (!published) runCatching { contentResolver.delete(destination, null, null) }
            logOperation("download", published, if (published) 1 else 0, 1,
                if (published) file.optLong("size").coerceAtLeast(0L) else 0L)
            main.post {
                if (isFinishing || isDestroyed) return@post
                copying = false
                screenShell()
                status.text = if (published) "Arquivo copiado com sucesso." else
                    "Falha ou cancelamento: ${result.exceptionOrNull()?.message ?: "não foi possível publicar arquivo"}."
                add(button("Voltar aos arquivos", primary = true) { listFolder() })
                if (published && inDownloads && nextAction.isNotBlank()) {
                    val mime = file.optString("mime").takeIf { '/' in it } ?: "application/octet-stream"
                    val intent = if (nextAction == "share") Intent(Intent.ACTION_SEND).apply {
                        type = mime; putExtra(Intent.EXTRA_STREAM, destination)
                    } else Intent(Intent.ACTION_VIEW).apply { setDataAndType(destination, mime) }
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    runCatching {
                        startActivity(if (nextAction == "share") Intent.createChooser(intent, "Compartilhar arquivo") else intent)
                    }.onFailure { status.text = "Arquivo salvo em Downloads. Nenhum aplicativo aceitou abrir: ${it.message}" }
                }
            }
        }
    }

    private fun transferTo(destination: Uri, file: JSONObject, source: List<String>, onProgress: (Long, Long) -> Unit) {
        val secret = key ?: error("Chave de conexão indisponível.")
        val size = file.optLong("size", -1L)
        val modified = file.optLong("modified", -1L)
        require(size in 1..RemoteFilePolicy.MAX_FILE_BYTES && RemoteFilePolicy.validPath(source)) { "Arquivo não permitido." }
        val output = contentResolver.openOutputStream(destination, "wt") ?: error("Android não autorizou o destino.")
        output.use { stream ->
            var written = 0L
            var lastPostedAt = 0L
            onProgress(0L, size)
            RemoteClient.dataStream(host, secret, applicationContext, method, source, size, modified,
                beforeNext = {
                    check(!cancel.get()) { "Transferência cancelada." }
                    check(SettingsStore.getRemoteHost(applicationContext) == host) { "O aparelho selecionado mudou." }
                    while (paused.get()) {
                        check(!cancel.get()) { "Transferência cancelada." }
                        Thread.sleep(150L)
                    }
                }, onChunk = { bytes ->
                    stream.write(bytes)
                    written += bytes.size
                    val now = SystemClock.elapsedRealtime()
                    if (written == size || now - lastPostedAt >= 250L) {
                        lastPostedAt = now
                        onProgress(written, size)
                    }
                })
            stream.flush()
            check(written == size) { "Cópia incompleta." }
            check(!cancel.get()) { "Transferência cancelada." }
        }
    }

    /** Repetição curta para instabilidades transitórias, sem prolongar cancelamentos. */
    private fun fetchChunksWithRetry(secret: ByteArray, source: List<String>, size: Long,
        modified: Long, offset: Long): List<ByteArray> {
        var failure: Exception? = null
        repeat(3) { attempt ->
            check(!cancel.get()) { "Transferência cancelada." }
            try { return RemoteClient.dataChunks(host, secret, applicationContext, method, source, size, modified, offset) }
            catch (e: Exception) {
                failure = e
                if (attempt < 2) {
                    // Não tentar novamente para falhas de versão, permissão ou integridade.
                    val text = e.message.orEmpty().lowercase(Locale.ROOT)
                    if (listOf("versão", "permiss", "integridade", "mudou", "recusad", "inválid").any { it in text }) throw e
                    Thread.sleep((attempt + 1) * 400L)
                }
            }
        }
        throw failure ?: IllegalStateException("Sem resposta do monitorado.")
    }

    /** Painel de transferência com hierarquia legível mesmo em telas pequenas.
     * Métricas separadas, sem comprimir nome, velocidade e ETA numa linha de 12 sp.
     */
    private fun showTransferPanel(title: String) {
        status.text = "Transferência protegida • rede local"
        progressStartedAt = 0L
        lastProgressAt = 0L
        pausedSinceAt = 0L
        lastTransferItem = ""
        lastTransferBytes = 0L
        speedSamples.clear()

        val card = panel().apply { setPadding(dp(18), dp(18), dp(18), dp(18)) }
        val heading = label("PROGRESSO DA CÓPIA", 13f, true).apply { setTextColor(accent) }
        card.addView(heading)
        currentTransferStage = label("Preparando a transferência", 14f).apply {
            setTextColor(muted)
        }.also { card.addView(it) }
        currentTransferName = label(title, 18f, true).apply {
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, dp(12), 0, dp(16))
        }.also { card.addView(it) }

        val progressRow = LinearLayout(this).apply {
            gravity = Gravity.BOTTOM
            orientation = LinearLayout.HORIZONTAL
        }
        currentTransferPercent = label("0%", 34f, true).also {
            progressRow.addView(it, LinearLayout.LayoutParams(-2, -2))
        }
        currentTransferAmount = label("Aguardando dados", 14f).apply {
            gravity = Gravity.END or Gravity.BOTTOM
            setTextColor(muted)
        }.also { progressRow.addView(it, LinearLayout.LayoutParams(0, -2, 1f)) }
        card.addView(progressRow)
        currentTransferBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000
            progress = 0
            progressTintList = ColorStateList.valueOf(accent)
            progressBackgroundTintList = ColorStateList.valueOf(softColor)
        }.also {
            card.addView(it, LinearLayout.LayoutParams(-1, dp(14)).apply {
                topMargin = dp(12)
                bottomMargin = dp(14)
            })
        }

        fun metric(caption: String, value: String, setter: (TextView) -> Unit) =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(14), dp(10), dp(14))
                background = rounded(softColor, 14, false)
                addView(label(caption, 13f).apply { setTextColor(muted) })
                addView(label(value, 21f, true).also(setter))
            }
        val figures = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        figures.addView(metric("Velocidade", "Calculando…") { currentTransferSpeed = it },
            LinearLayout.LayoutParams(0, -2, 1f))
        figures.addView(metric("Tempo restante", "Calculando…") { currentTransferEta = it },
            LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(10) })
        card.addView(figures)
        currentTransferElapsed = label("Tempo decorrido: 0s", 14f).apply {
            setTextColor(muted)
            setPadding(0, dp(15), 0, 0)
        }.also { card.addView(it) }
        add(card, 16)

        val pause = button("Pausar") { }.apply { textSize = 16f; minHeight = dp(58) }
        pause.setOnClickListener {
            val now = SystemClock.elapsedRealtime()
            paused.set(!paused.get())
            pause.text = if (paused.get()) "Retomar" else "Pausar"
            if (paused.get()) {
                pausedSinceAt = now
                currentTransferStage?.text = "Pausada pelo usuário"
                currentTransferSpeed?.text = "Pausada"
                currentTransferEta?.text = "Pausada"
            } else {
                pausedSinceAt = 0L
                speedSamples.clear() // não misturar tempo pausado ao cálculo de vazão
                speedSamples.addLast(now to lastTransferBytes)
                currentTransferStage?.text = "Retomando cópia…"
            }
        }
        val stop = button("Cancelar") {
            cancel.set(true); paused.set(false)
            status.text = "Cancelando transferência…"
            currentTransferStage?.text = "Cancelamento solicitado"
        }.apply { textSize = 16f; minHeight = dp(58) }
        add(horizontal(pause, stop), 12)
        add(label("A cópia só é validada após conferir a integridade do arquivo.", 14f).apply {
            setTextColor(muted)
        }, 8)
    }

    private fun formatDuration(seconds: Long): String = when {
        seconds >= 3600L -> "%dh%02dmin".format(seconds / 3600L, (seconds % 3600L) / 60L)
        seconds >= 60L -> "%dmin%02ds".format(seconds / 60L, seconds % 60L)
        else -> "${seconds}s"
    }

    private fun updateProgress(done: Long, total: Long, title: String) {
        if (total <= 0L || !copying || cancel.get()) return
        val now = SystemClock.elapsedRealtime()
        if (lastTransferItem != title || done < lastTransferBytes) {
            progressStartedAt = now
            lastProgressAt = 0L
            lastTransferItem = title
            speedSamples.clear()
            speedSamples.addLast(now to done)
        }
        lastTransferBytes = done
        if (now - lastProgressAt < 400L && done != total) return
        lastProgressAt = now
        val fileNumber = Regex("^(\\d+)/(\\d+) · (.*)$").matchEntire(title)
        val name = fileNumber?.groupValues?.get(3) ?: title
        currentTransferStage?.text = when {
            paused.get() -> "Pausada pelo usuário"
            fileNumber != null -> "Arquivo ${fileNumber.groupValues[1]} de ${fileNumber.groupValues[2]}"
            else -> "Recebendo arquivo do aparelho remoto"
        }
        currentTransferName?.text = name
        currentTransferBar?.progress = (done * 1000L / total).toInt()
        currentTransferPercent?.text = "${done * 100L / total}%"
        currentTransferAmount?.text = "${formatBytes(done)} / ${formatBytes(total)}"
        val elapsed = ((now - progressStartedAt).coerceAtLeast(0L) / 1000L)
        currentTransferElapsed?.text = "Tempo decorrido: ${formatDuration(elapsed)}"
        if (paused.get()) {
            currentTransferSpeed?.text = "Pausada"
            currentTransferEta?.text = "Pausada"
            return
        }
        speedSamples.addLast(now to done)
        while (speedSamples.size > 2 && now - speedSamples.first().first > 5000L)
            speedSamples.removeFirst()
        val first = speedSamples.first()
        val spanMs = now - first.first
        val bytesPerSecond = if (spanMs >= 500L && done > first.second)
            (done - first.second) * 1000L / spanMs else 0L
        val remain = if (bytesPerSecond > 0L) (total - done) / bytesPerSecond else 0L
        currentTransferSpeed?.text = if (bytesPerSecond > 0L) "${formatBytes(bytesPerSecond)}/s" else "Calculando…"
        currentTransferEta?.text = when {
            done >= total -> "Verificando…"
            bytesPerSecond > 0L -> "~${formatDuration(remain)}"
            else -> "Calculando…"
        }
    }

    private fun fetchLargeChunkWithRetry(secret: ByteArray, source: List<String>, size: Long,
        modified: Long, offset: Long): ByteArray {
        var failure: Exception? = null
        repeat(3) { attempt ->
            check(!cancel.get()) { "Transferência cancelada." }
            try { return RemoteClient.dataRead(host, secret, applicationContext, method, source, size, modified, offset) }
            catch (e: Exception) {
                failure = e
                val msg = e.message.orEmpty().lowercase(Locale.ROOT)
                if (listOf("versão", "permiss", "integridade", "mudou", "inválid", "recusad").any { it in msg }) throw e
                if (attempt < 2) Thread.sleep(400L * (attempt + 1))
            }
        }
        throw failure ?: IllegalStateException("Leitura interrompida.")
    }

    private fun askNewFolder() {
        if (!writeAllowed) return
        val input = EditText(this).apply { hint = "Nome da pasta"; setSingleLine(true) }
        AlertDialog.Builder(this).setTitle("＋ Nova pasta").setView(input)
            .setNegativeButton("Cancelar", null).setPositiveButton("Criar") { _, _ ->
                val name = input.text.toString().trim()
                if (!RemoteFilePolicy.validSegment(name)) { status.text = "Nome de pasta inválido."; return@setPositiveButton }
                executeRemoteAction("mkdir", path, name = name)
            }.show()
    }

    private fun askRename(item: JSONObject) {
        val oldName = item.optString("name")
        val edit = EditText(this).apply { setSingleLine(true); setText(oldName); selectAll() }
        AlertDialog.Builder(this).setTitle("Renomear arquivo/pasta").setView(edit)
            .setNegativeButton("Cancelar", null).setPositiveButton("Renomear") { _, _ ->
                val name = edit.text.toString().trim()
                if (RemoteFilePolicy.validSegment(name) && name != oldName)
                    executeRemoteAction("rename", path + oldName, name = name)
            }.show()
    }

    private fun confirmBatchDelete(items: List<JSONObject>) {
        if (!writeAllowed || items.isEmpty()) return
        val names = items.map { it.optString("name") }
        AlertDialog.Builder(this).setTitle("Excluir ${names.size} item(ns) da origem?")
            .setMessage("A exclusão é REMOTA e pode ser DEFINITIVA. Ela não apaga apenas uma cópia local.\n" +
                names.take(5).joinToString("\n") + if (names.size > 5) "\n…" else "")
            .setNegativeButton("Cancelar", null).setPositiveButton("EXCLUIR") { _, _ ->
                runBatchAction("delete", names, emptyList())
            }.show()
    }

    private fun confirmBatchTrash(items: List<JSONObject>) {
        if (!writeAllowed || items.isEmpty()) return
        AlertDialog.Builder(this).setTitle("Mover ${items.size} item(ns) à lixeira?")
            .setMessage("A lixeira é criada dentro da pasta autorizada. A operação depende do suporte do Android a mover documentos; o arquivo não será apagado se falhar.")
            .setNegativeButton("Cancelar", null).setPositiveButton("Mover") { _, _ ->
                runBatchAction("trash", items.map { it.optString("name") }, emptyList())
            }.show()
    }

    private fun executeRemoteAction(action: String, source: List<String>, destination: List<String> = emptyList(),
        name: String = "") {
        runBatchAction(action, listOf(source.lastOrNull() ?: name), destination, source, name)
    }

    private fun runBatchAction(action: String, names: List<String>, destination: List<String>,
        explicitSource: List<String>? = null, requestedName: String = "") {
        if (copying || !writeAllowed || names.isEmpty()) return
        val secret = key ?: return
        copying = true; cancel.set(false); paused.set(false); ++generation
        screenShell(); showTransferPanel("${action.uppercase(Locale.ROOT)} · ${names.size} item(ns)")
        thread(name = "MonitorRemoto-data-manage") {
            var completed = 0
            val outcome = runCatching {
                names.forEach { name ->
                    check(!cancel.get())
                    check(SettingsStore.getRemoteHost(applicationContext) == host) { "O aparelho selecionado mudou." }
                    while (paused.get()) { check(!cancel.get()); Thread.sleep(150L) }
                    val source = explicitSource ?: if (action == "mkdir") path else path + name
                    val newName = if (action in setOf("mkdir", "rename")) requestedName else ""
                    check(RemoteManagePolicy.allowed(action, source, destination, newName)) { "Operação ou destino inválidos." }
                    RemoteClient.dataManage(host, secret, applicationContext, method, action, source, destination, newName)
                    completed++
                    val step = completed
                    main.post { if (!isDestroyed && !isFinishing) {
                        currentTransferBar?.progress = step * 1000 / names.size
                        currentTransferStage?.text = "Operação $step de ${names.size}"
                        currentTransferName?.text = name
                        currentTransferPercent?.text = "${step * 100 / names.size}%"
                        currentTransferAmount?.text = "$step / ${names.size} itens"
                        currentTransferSpeed?.text = "Concluindo…"
                        currentTransferEta?.text = "Por etapas"
                    } }
                }
            }
            logOperation(action, outcome.isSuccess, completed, names.size)
            main.post { if (!isFinishing && !isDestroyed) {
                copying = false; selected.clear(); selecting = false
                screenShell(); status.text = if (outcome.isSuccess) "Operação concluída em $completed item(ns)." else
                    "Concluídos $completed/${names.size}. ${outcome.exceptionOrNull()?.message}"
                add(button("Atualizar arquivos", true) { listFolder() })
            } }
        }
    }

    /** Navegador remoto para selecionar pasta interna; o caminho nunca sai da árvore SAF. */
    private fun chooseRemoteFolder(action: String, names: List<String>, at: List<String> = emptyList()) {
        if (!writeAllowed || names.isEmpty()) return
        val secret = key ?: return
        val current = at.toList()
        thread(name = "MonitorRemoto-destinations") {
            val fetched = runCatching { RemoteClient.dataList(host, secret, applicationContext, method, current) }
            main.post { if (!isFinishing && !isDestroyed) fetched.onSuccess { data ->
                val array = data.optJSONArray("entries")
                val folders = (0 until (array?.length() ?: 0)).mapNotNull { array?.optJSONObject(it) }
                    .filter { it.optBoolean("folder") }.map { it.optString("name") }
                val labels = (listOf("✓ Usar esta pasta") +
                    (if (current.isEmpty()) emptyList() else listOf("↑ Pasta anterior")) +
                    folders.map { "📁 $it" }).toTypedArray()
                AlertDialog.Builder(this).setTitle("Destino: /${current.joinToString("/")}")
                    .setItems(labels) { _, index -> when {
                        index == 0 -> if (action == "copy" || action == "move") runBatchAction(action, names, current)
                        index == 1 && current.isNotEmpty() -> chooseRemoteFolder(action, names, current.dropLast(1))
                        else -> {
                            val pos = index - if (current.isEmpty()) 1 else 2
                            folders.getOrNull(pos)?.let { chooseRemoteFolder(action, names, current + it) }
                        }
                    } }.setNegativeButton("Cancelar", null).show()
            }.onFailure { status.text = "Destino indisponível: ${it.message}" } }
        }
    }

    private fun pickUploadFiles() {
        if (!writeAllowed || copying) return
        try { @Suppress("DEPRECATION")
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE); type = "*/*"
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, PICK_UPLOADS)
        } catch (e: Exception) { status.text = "Seletor de envio indisponível: ${e.message}" }
    }

    private data class LocalUpload(val uri: Uri, val name: String, val mime: String, val size: Long)
    private fun uploadSelected(files: List<Uri>) {
        if (files.isEmpty() || !writeAllowed || copying) return
        val secret = key ?: return
        copying = true; cancel.set(false); paused.set(false); ++generation
        screenShell(); showTransferPanel("Enviando ${files.size} arquivo(s) ao monitorado")
        thread(name = "MonitorRemoto-upload") {
            var sent = 0
            var bytesSent = 0L
            val outcome = runCatching {
                val items = files.map { source ->
                    var name = ""; var size = -1L
                    contentResolver.query(source, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) { name = cursor.getString(0).orEmpty(); if (!cursor.isNull(1)) size = cursor.getLong(1) }
                    }
                    val mime = contentResolver.getType(source) ?: "application/octet-stream"
                    check(RemoteFilePolicy.validSegment(name) && RemoteFilePolicy.validUploadSize(size) &&
                        mime.length in 3..110 && '/' in mime) { "Arquivo sem nome/tamanho válido: $name" }
                    LocalUpload(source, name, mime, size)
                }
                check(items.size <= MAX_BATCH_ITEMS && items.sumOf { it.size } <= MAX_BATCH_BYTES) { "Lote acima de 20 arquivos ou 2 GiB." }
                items.forEachIndexed { index, file ->
                    check(!cancel.get())
                    check(SettingsStore.getRemoteHost(applicationContext) == host) { "O aparelho selecionado mudou." }
                    val open = RemoteClient.dataUpload(host, secret, applicationContext, method, "dataUploadStart", path,
                        JSONObject().put("name", file.name).put("size", file.size).put("mime", file.mime))
                    val session = open.getJSONObject("result").getString("session")
                    try {
                        contentResolver.openInputStream(file.uri)?.use { input ->
                            val buffer = ByteArray(RemoteFilePolicy.UPLOAD_CHUNK_BYTES)
                            val hash = MessageDigest.getInstance("SHA-256")
                            var offset = 0L
                            while (offset < file.size) {
                                check(!cancel.get()); while (paused.get()) { check(!cancel.get()); Thread.sleep(150L) }
                                val needed = minOf(buffer.size.toLong(), file.size - offset).toInt()
                                var read = 0
                                while (read < needed) {
                                    val n = input.read(buffer, read, needed - read)
                                    check(n > 0) { "Arquivo local mudou durante o envio." }; read += n
                                }
                                val bytes = buffer.copyOf(read)
                                val payload = JSONObject().put("session", session)
                                    .put("offset", offset).put("bytes", Base64.encodeToString(bytes, Base64.NO_WRAP))
                                val accepted = runCatching {
                                    RemoteClient.dataUpload(host, secret, applicationContext, method,
                                        "dataUploadChunk", emptyList(), payload).getJSONObject("result").optLong("offset", -1)
                                }.getOrElse { failure ->
                                    // Se a resposta se perdeu depois da gravação, a sessão informa o deslocamento.
                                    val serverOffset = runCatching { RemoteClient.dataUpload(host, secret,
                                        applicationContext, method, "dataUploadStatus", emptyList(),
                                        JSONObject().put("session", session)).getJSONObject("result").optLong("offset", -1) }
                                        .getOrElse { throw failure }
                                    when (serverOffset) {
                                        offset + read -> serverOffset
                                        offset -> RemoteClient.dataUpload(host, secret, applicationContext, method,
                                            "dataUploadChunk", emptyList(), payload).getJSONObject("result").optLong("offset", -1)
                                        else -> error("Deslocamento remoto inesperado; envio interrompido.")
                                    }
                                }
                                hash.update(bytes)
                                offset += read
                                check(accepted == offset) { "Envio não confirmado pelo monitorado." }
                                val done = offset
                                main.post { if (!isFinishing && !isDestroyed)
                                    updateProgress(done, file.size.coerceAtLeast(1), "${index + 1}/${items.size} · ${file.name}") }
                            }
                            check(input.read() == -1) { "Arquivo local cresceu durante o envio." }
                            val digest = hash.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
                            RemoteClient.dataUpload(host, secret, applicationContext, method, "dataUploadFinish", emptyList(),
                                JSONObject().put("session", session).put("sha256", digest))
                        } ?: error("Não foi possível abrir arquivo local.")
                        sent++
                        bytesSent += file.size
                    } catch (error: Exception) {
                        runCatching { RemoteClient.dataUpload(host, secret, applicationContext, method,
                            "dataUploadAbort", emptyList(), JSONObject().put("session", session)) }
                        throw error
                    }
                }
            }
            logOperation("upload", outcome.isSuccess, sent, files.size, bytesSent)
            main.post { if (!isDestroyed && !isFinishing) {
                copying = false; screenShell()
                status.text = if (outcome.isSuccess) "$sent arquivo(s) enviados e conferidos." else
                    "$sent/${files.size} enviados. ${outcome.exceptionOrNull()?.message}"
                add(button("Voltar aos arquivos", true) { listFolder() })
            } }
        }
    }

    private fun copyBatchDownloads(items: List<JSONObject>) {
        if (Build.VERSION.SDK_INT < 29 || copying || items.isEmpty() ||
            items.any { it.optBoolean("folder") } ||
            !RemoteBatchPolicy.eligible(items.map { it.optLong("size", -1) })) {
            status.text = "Escolha até 20 arquivos com tamanho conhecido e total de até 2 GiB."; return
        }
        copying = true; cancel.set(false); paused.set(false); ++generation
        screenShell(); showTransferPanel("Baixando ${items.size} para Downloads")
        thread(name = "MonitorRemoto-downloads-batch") {
            var finished = 0
            val outcome = runCatching {
                items.forEachIndexed { index, item ->
                    check(!cancel.get())
                    val name = item.optString("name")
                    val mime = item.optString("mime").takeIf { '/' in it } ?: "application/octet-stream"
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                        put(MediaStore.MediaColumns.MIME_TYPE, mime)
                        put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/Monitor Remoto")
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                    val target = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                        ?: error("Não foi possível criar arquivo em Downloads.")
                    try {
                        transferTo(target, item, path + name) { done, total ->
                            main.post { if (!isFinishing && !isDestroyed)
                                updateProgress(done, total, "${index + 1}/${items.size} · $name") }
                        }
                        val published = contentResolver.update(target, ContentValues().apply {
                            put(MediaStore.MediaColumns.IS_PENDING, 0)
                        }, null, null)
                        check(published == 1) { "Não foi possível publicar $name em Downloads." }
                        finished++
                    } catch (e: Exception) { runCatching { contentResolver.delete(target, null, null) }; throw e }
                }
            }
            logOperation("download", outcome.isSuccess, finished, items.size)
            main.post { if (!isDestroyed && !isFinishing) {
                copying = false; screenShell()
                status.text = if (outcome.isSuccess) "$finished arquivo(s) salvos em Downloads/Monitor Remoto." else
                    "Salvos $finished/${items.size}. ${outcome.exceptionOrNull()?.message}"
                add(button("Voltar aos arquivos", true) { listFolder() })
            } }
        }
    }

    private fun saveDownloads(item: JSONObject, nextAction: String = "") {
        if (Build.VERSION.SDK_INT < 29) { chooseDestination(item, path); return }
        val name = item.optString("name")
        val mime = item.optString("mime").takeIf { '/' in it } ?: "application/octet-stream"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/Monitor Remoto")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val target = runCatching { contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) }.getOrNull()
        if (target == null) { status.text = "Downloads indisponível. Escolha outro destino."; return }
        copyOne(target, item, path + name, inDownloads = true, nextAction = nextAction)
    }

    @Deprecated("Navegação por Activity")
    override fun onBackPressed() { goBack() }
    override fun onDestroy() {
        cancel.set(true); generation++
        super.onDestroy()
    }
}
