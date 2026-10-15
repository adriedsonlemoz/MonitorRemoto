package br.dev.monitorremoto.ui

import android.app.Activity
import android.app.AlertDialog
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
import br.dev.monitorremoto.core.SettingsStore
import br.dev.monitorremoto.transport.RemoteClient
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/** Gerenciador remoto SOMENTE LEITURA de uma árvore SAF autorizada no telefone monitorado. */
class RemoteFilesActivity : Activity() {
    companion object {
        const val EXTRA_HOST = "host"
        const val EXTRA_METHOD = "method"
        private const val CREATE_DOCUMENT = 9045
        private const val CHOOSE_FOLDER = 9046
        private const val MAX_BATCH_ITEMS = 20
        private const val MAX_BATCH_BYTES = 128L * 1024L * 1024L
    }

    private val main = Handler(Looper.getMainLooper())
    private var host = ""
    private var method = "Chave salva"
    private var path = emptyList<String>()
    private var generation = 0
    private var copying = false
    private val cancel = AtomicBoolean(false)
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

    private lateinit var frame: LinearLayout
    private lateinit var content: LinearLayout
    private lateinit var listArea: LinearLayout
    private lateinit var selectionBar: LinearLayout
    private lateinit var status: TextView
    private lateinit var count: TextView
    private lateinit var search: EditText

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
        if (copying) { cancel.set(true); status.text = "Cancelando transferência…"; return }
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
        frame = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(backgroundColor) }
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
        if (host.isBlank() || key == null) showProblem("Conecte primeiro um aparelho pareado para recuperar arquivos.")
        else listFolder()
    }

    private fun deviceTitle(): String = RecentDevices.list(this)
        .firstOrNull { it.host.equals(host, ignoreCase = true) }?.name
        ?.takeIf { it.isNotBlank() } ?: "Telefone remoto"

    private fun screenShell() {
        content.removeAllViews()
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(button("‹", action = { goBack() }), LinearLayout.LayoutParams(dp(52), dp(48)))
        top.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(label("Recuperar dados", 22f, true))
            addView(label(deviceTitle(), 12f).apply { setTextColor(muted); maxLines = 1 })
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(10) })
        top.addView(button("?", action = { showHelp() }).apply { contentDescription = "Ajuda sobre arquivos compartilhados" }, LinearLayout.LayoutParams(dp(48), dp(48)))
        top.addView(button("↻", action = { if (!copying) listFolder() }).apply { contentDescription = "Atualizar lista" }, LinearLayout.LayoutParams(dp(48), dp(48)).apply { leftMargin = dp(8) })
        add(top, 15)
        status = label("", 13f).apply { setTextColor(muted) }
        add(status, 9)
    }

    private fun showHelp() {
        val body = panel().apply {
            addView(label("Arquivos compartilhados", 19f, true))
            addView(label("Somente a pasta escolhida e autorizada no telefone monitorado. O acesso é de leitura: copiar não altera a origem.", 14f))
            addView(label("Para autorizar: no monitorado, abra Monitor Remoto → Configurações → Arquivos e recuperação → Escolher pasta.", 14f))
            addView(label("Limites: 32 MiB por arquivo, 20 arquivos por lote, 128 MiB por lote. Não recupera arquivos apagados nem lê dados privados de outros apps.", 12f).apply { setTextColor(muted) })
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
                    showListing(response.optBoolean("truncated"))
                }.onFailure { showListingError(it.message ?: "Sem resposta do aparelho remoto.") }
            }
        }
    }

    private fun showListing(truncated: Boolean) {
        screenShell()
        val crumb = panel().apply {
            addView(label(if (path.isEmpty()) "Pasta compartilhada" else "Pasta /${path.joinToString("/")}", 14f, true))
            addView(label("Somente leitura · ${entries.size} itens" + if (truncated) " · limite 150" else "", 12f).apply { setTextColor(muted) })
        }
        add(crumb)
        val controls = horizontal(
            button("Ordenar", action = { chooseSort() }),
            button(if (gridMode) "☰ Lista" else "▦ Grade", action = { gridMode = !gridMode; renderItems() }),
            button(if (selecting) "Cancelar" else "Selecionar", action = {
                selecting = !selecting; selected.clear(); renderItems()
            })
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
        status.text = "Toque em uma pasta para abrir. Selecione arquivos para copiar em lote."
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
        AlertDialog.Builder(this).setTitle(name).setMessage(
            "Tipo: ${if (item.optBoolean("folder")) "Pasta" else item.optString("mime").ifBlank { "Arquivo" }}\n" +
                "Tamanho: ${entrySize(item)}\nModificado: ${entryDate(item)}\nLocal: $parent\n\nOrigem somente leitura."
        ).setPositiveButton("Fechar", null).show()
    }
    private fun renderItems() {
        if (!this::listArea.isInitialized || copying) return
        listArea.removeAllViews()
        val filtered = entries.filter { it.optString("name").contains(queryText, ignoreCase = true) }
        val comparator: Comparator<JSONObject> = when (sortMode) {
            1 -> compareByDescending<JSONObject> { it.optLong("modified", 0L) }.thenBy { it.optString("name").lowercase(Locale.ROOT) }
            2 -> compareByDescending<JSONObject> { it.optLong("size", -1L) }.thenBy { it.optString("name").lowercase(Locale.ROOT) }
            3 -> compareBy<JSONObject> { it.optString("mime").lowercase(Locale.ROOT) }.thenBy { it.optString("name").lowercase(Locale.ROOT) }
            else -> compareBy<JSONObject> { it.optString("name").lowercase(Locale.ROOT) }
        }
        val ordered = filtered.sortedWith(compareBy<JSONObject> { !it.optBoolean("folder") }.then(comparator))
        if (this::count.isInitialized) count.text = "${ordered.size} itens" + if (selected.isNotEmpty()) " · ${selected.size} selecionado(s)" else ""
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
        val chosen = selected.contains(name)
        val box = LinearLayout(this).apply {
            orientation = if (grid) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(13), dp(12), dp(13), dp(12))
            background = rounded(if (chosen) softColor else panelColor, 15)
            isClickable = true; isFocusable = true
        }
        val icon = label(entryIcon(item), if (grid) 29f else 25f).apply {
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 0)
        }
        box.addView(icon, if (grid) LinearLayout.LayoutParams(-1, dp(48)) else LinearLayout.LayoutParams(dp(44), dp(44)))
        val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        info.addView(label(name, 14f, true).apply { maxLines = 2 })
        info.addView(label(if (folder) "Pasta" else entrySize(item), 12f).apply { setTextColor(muted) })
        if (!folder) info.addView(label(entryDate(item), 11f).apply { setTextColor(muted); maxLines = 1 })
        box.addView(info, if (grid) LinearLayout.LayoutParams(-1, -2) else LinearLayout.LayoutParams(0, -2, 1f))
        if (!grid) box.addView(button(if (chosen) "✓" else "⋮") {
            if (selecting && !folder) toggleSelection(item) else openActions(item)
        }, LinearLayout.LayoutParams(dp(48), dp(46)))
        box.setOnClickListener {
            if (selecting && !folder) toggleSelection(item)
            else if (folder) openFolder(name)
            else openActions(item)
        }
        box.setOnLongClickListener {
            if (!folder) { selecting = true; toggleSelection(item) } else fileInfo(item)
            true
        }
        return box
    }

    private fun openFolder(name: String) {
        if (path.size >= RemoteFilePolicy.MAX_DEPTH) { status.text = "Limite de 12 níveis de pastas."; return }
        path = path + name
        queryText = ""
        listFolder()
    }
    private fun openActions(item: JSONObject) {
        val isFolder = item.optBoolean("folder")
        val choices = if (isFolder) arrayOf("Abrir pasta", "Informações") else arrayOf("Copiar para este celular", "Informações", "Selecionar")
        AlertDialog.Builder(this).setTitle(item.optString("name"))
            .setItems(choices) { _, which ->
                if (isFolder) {
                    if (which == 0) openFolder(item.optString("name")) else fileInfo(item)
                } else when (which) {
                    0 -> chooseDestination(item, path)
                    1 -> fileInfo(item)
                    2 -> { selecting = true; toggleSelection(item) }
                }
            }.setNegativeButton("Fechar", null).show()
    }
    private fun toggleSelection(item: JSONObject) {
        val name = item.optString("name")
        if (item.optBoolean("folder")) return
        if (!selected.remove(name)) {
            if (selected.size >= MAX_BATCH_ITEMS) { status.text = "Máximo de $MAX_BATCH_ITEMS arquivos por lote."; return }
            selected.add(name)
        }
        renderItems()
    }
    private fun updateSelectionBar() {
        selectionBar.removeAllViews()
        if (!selecting || selected.isEmpty()) { selectionBar.visibility = View.GONE; return }
        selectionBar.visibility = View.VISIBLE
        val chosen = entries.filter { !it.optBoolean("folder") && selected.contains(it.optString("name")) }
        val total = chosen.sumOf { it.optLong("size", -1L).coerceAtLeast(0L) }
        selectionBar.addView(label("${chosen.size} arquivos · ${formatBytes(total)}", 13f, true))
        selectionBar.addView(horizontal(
            button("Cancelar") { selected.clear(); selecting = false; renderItems() },
            button("Copiar selecionados", primary = true) { chooseBatchDestination() }
        ))
    }
    private fun formatBytes(value: Long) = if (value >= 1024 * 1024) "${value / (1024 * 1024)} MiB" else "${value / 1024} KiB"

    private fun chooseBatchDestination() {
        if (copying) return
        val chosen = entries.filter { !it.optBoolean("folder") && it.optString("name") in selected }
        val sizes = chosen.map { it.optLong("size", -1L) }
        if (!RemoteBatchPolicy.eligible(sizes) ||
            chosen.map { it.optString("name") }.distinct().size != chosen.size) {
            status.text = "Selecione até $MAX_BATCH_ITEMS arquivos distintos, com tamanho conhecido entre 1 B e 32 MiB cada (até 128 MiB no lote)."
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
            status.text = "Esta versão copia arquivos individuais de 1 B a 32 MiB."
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
                batchFiles = emptyList()
                if (resultCode != RESULT_OK || data?.data == null || files.isEmpty() ||
                    data.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION == 0) {
                    status.text = "Nenhuma pasta de destino autorizada."; return
                }
                copyBatch(data.data!!, files, batchPath)
            }
        }
    }

    /** Cria nomes novos com sufixo, nunca sobrescreve arquivo preexistente do destino. */
    private fun safeNewDocumentName(tree: Uri, desired: String): String {
        val resolver = contentResolver
        val parentId = DocumentsContract.getTreeDocumentId(tree)
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        val used = mutableSetOf<String>()
        resolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) cursor.getString(0)?.let { used.add(it.lowercase(Locale.ROOT)) }
        } ?: error("Não foi possível conferir os arquivos existentes no destino.")
        return RemoteBatchPolicy.availableName(desired, used)
    }

    private fun copyBatch(tree: Uri, files: List<JSONObject>, parent: List<String>) {
        if (copying) return
        copying = true; cancel.set(false)
        ++generation
        screenShell()
        status.text = "Preparando ${files.size} arquivos…"
        add(button("Cancelar cópia", primary = true) { cancel.set(true); status.text = "Cancelando após o bloco atual…" })
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
                                if (!isDestroyed && !isFinishing) status.text =
                                    "${index + 1}/${files.size} · $name · ${written * 100 / total}%"
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

    private fun copyOne(destination: Uri, file: JSONObject, source: List<String>) {
        if (copying) return
        copying = true; cancel.set(false); ++generation
        screenShell()
        status.text = "Copiando ${file.optString("name")}…"
        add(button("Cancelar cópia", primary = true) { cancel.set(true) })
        thread(name = "MonitorRemoto-data-copy") {
            val result = runCatching { transferTo(destination, file, source) { done, total ->
                main.post { if (!isFinishing && !isDestroyed) status.text = "Copiando: ${done * 100 / total}%" }
            } }
            if (result.isFailure) runCatching { DocumentsContract.deleteDocument(contentResolver, destination) }
            main.post {
                if (isFinishing || isDestroyed) return@post
                copying = false
                screenShell()
                status.text = if (result.isSuccess) "Arquivo copiado com sucesso." else
                    "Falha ou cancelamento: ${result.exceptionOrNull()?.message}. Verifique se restou arquivo parcial."
                add(button("Voltar aos arquivos", primary = true) { listFolder() })
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
            var lastBucket = -1L
            while (written < size) {
                check(!cancel.get()) { "Transferência cancelada." }
                check(SettingsStore.getRemoteHost(applicationContext) == host) { "O aparelho selecionado mudou." }
                val bytes = RemoteClient.dataChunk(host, secret, applicationContext, method, source, size, modified, written)
                check(bytes.isNotEmpty() && bytes.size <= RemoteFilePolicy.CHUNK_BYTES) { "Bloco inválido." }
                check(!cancel.get()) { "Transferência cancelada." }
                stream.write(bytes)
                written += bytes.size
                val bucket = (written * 100 / size) / 5
                if (bucket != lastBucket || written == size) {
                    lastBucket = bucket
                    onProgress(written, size)
                }
            }
            stream.flush()
            check(written == size) { "Cópia incompleta." }
        }
    }

    @Deprecated("Navegação por Activity")
    override fun onBackPressed() { goBack() }
    override fun onDestroy() {
        cancel.set(true); generation++
        super.onDestroy()
    }
}
