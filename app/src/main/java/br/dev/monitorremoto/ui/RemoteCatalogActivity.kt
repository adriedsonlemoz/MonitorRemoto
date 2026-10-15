package br.dev.monitorremoto.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ClipDescription
import android.os.PersistableBundle
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.text.TextUtils
import android.text.format.DateFormat
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import br.dev.monitorremoto.R
import br.dev.monitorremoto.core.RecentDevices
import br.dev.monitorremoto.core.SensitiveContent
import br.dev.monitorremoto.core.SettingsStore
import br.dev.monitorremoto.transport.RemoteClient
import org.json.JSONObject
import java.util.concurrent.Executors

/** Catálogo remoto apenas para leitura, desenhado como Detalhes, com dados sempre consultados sob demanda. */
class RemoteCatalogActivity : Activity() {
    companion object {
        const val EXTRA_HOST = "remote_host"
        const val EXTRA_METHOD = "remote_method"
        const val EXTRA_TYPE = "catalog_type"
    }

    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var host = ""
    private var method = "Chave salva"
    private var type = "notifications"
    private var generation = 0
    private var loading = false
    private var payload: JSONObject? = null
    private var error: String? = null
    private var search = ""
    private var expandedInfo = false
    private lateinit var content: LinearLayout
    private lateinit var items: LinearLayout
    private lateinit var summary: TextView
    private lateinit var action: TextView
    private val dark get() = when (SettingsStore.theme(this)) {
        "dark" -> true
        "system" -> resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        else -> false
    }
    private val bg get() = if (dark) Color.rgb(14, 22, 35) else Color.rgb(247, 249, 252)
    private val surface get() = if (dark) Color.rgb(28, 41, 58) else Color.WHITE
    private val tile get() = if (dark) Color.rgb(22, 34, 50) else Color.rgb(243, 246, 249)
    private val ink get() = if (dark) Color.rgb(233, 239, 247) else Color.rgb(23, 34, 48)
    private val muted get() = if (dark) Color.rgb(167, 184, 203) else Color.rgb(92, 109, 128)
    private val border get() = if (dark) Color.rgb(47, 64, 83) else Color.rgb(224, 231, 239)
    private val accent get() = if (dark) Color.rgb(90, 214, 188) else Color.rgb(24, 166, 146)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun shape(color: Int = surface, radius: Int = 17) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat(); setStroke(dp(1), border)
    }
    private fun label(text: String, size: Float = 14f, bold: Boolean = false, secondary: Boolean = false): TextView =
        TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(if (secondary) muted else ink)
            if (bold) setTypeface(null, Typeface.BOLD)
            includeFontPadding = false
            setLineSpacing(dp(2).toFloat(), 1.08f)
        }
    private fun gap(size: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(size)) }
    private fun icon(res: Int) = ImageView(this).apply {
        setImageResource(res)
        imageTintList = ColorStateList.valueOf(accent)
        scaleType = ImageView.ScaleType.FIT_CENTER
    }
    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = shape()
        setPadding(dp(16), dp(16), dp(16), dp(16))
    }
    private fun addCard(view: LinearLayout) {
        content.addView(view, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
    }
    private fun action(label: String, onClick: () -> Unit) = TextView(this).apply {
        text = label
        textSize = 13.5f
        setTextColor(accent)
        setTypeface(null, Typeface.BOLD)
        gravity = Gravity.CENTER
        background = shape(tile, 13)
        setPadding(dp(12), dp(10), dp(12), dp(10))
        minHeight = dp(44)
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(if (dark) android.R.style.Theme_Material_NoActionBar else android.R.style.Theme_Material_Light_NoActionBar)
        super.onCreate(savedInstanceState)
        host = intent.getStringExtra(EXTRA_HOST).orEmpty()
        method = intent.getStringExtra(EXTRA_METHOD).orEmpty().ifBlank { "Chave salva" }
        type = if (intent.getStringExtra(EXTRA_TYPE) == "apps") "apps" else "notifications"
        window.statusBarColor = bg
        window.navigationBarColor = bg
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = if (dark) 0 else View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        val scroll = ScrollView(this).apply { setBackgroundColor(bg); isFillViewport = true }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(30))
        }
        scroll.addView(content)
        setContentView(scroll)
        buildShell()
        refresh()
    }

    private fun notificationAppName(pkg: String): String = when (pkg) {
        "com.openai.chatgpt" -> "ChatGPT"
        "com.whatsapp", "com.whatsapp.w4b" -> "WhatsApp"
        "org.telegram.messenger" -> "Telegram"
        "com.instagram.android" -> "Instagram"
        "com.google.android.apps.messaging" -> "Mensagens"
        "com.google.android.gm" -> "Gmail"
        else -> pkg.substringAfterLast('.').ifBlank { "Aplicativo" }
            .replaceFirstChar { it.uppercase() }.take(45)
    }

    private fun deviceName(): String = RecentDevices.list(this).firstOrNull { it.host == host }?.name
        ?.takeIf { it.isNotBlank() } ?: "Aparelho remoto"

    private fun buildShell() {
        content.removeAllViews()
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(action("‹", { finish() }), LinearLayout.LayoutParams(dp(48), dp(46)))
        header.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(label(if (type == "apps") "Aplicativos instalados" else "Notificações", 21f, true))
            addView(gap(4))
            addView(label(deviceName(), 11.5f, secondary = true))
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(12) })
        action = action("Atualizar", { refresh() })
        header.addView(action)
        content.addView(header)
        content.addView(gap(16))
        val info = card()
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(icon(if (type == "apps") R.drawable.ic_phone else R.drawable.ic_bell),
            LinearLayout.LayoutParams(dp(28), dp(28)).apply { rightMargin = dp(12) })
        val title = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        title.addView(label(if (type == "apps") "Lista autorizada" else "Eventos recentes", 16f, true))
        summary = label("Consultando o aparelho…", 12f, secondary = true)
        title.addView(summary)
        top.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        info.addView(top)
        info.addView(gap(10))
        info.addView(action("Como funciona?  ⌄") {
            expandedInfo = !expandedInfo
            renderRecords()
        })
        addCard(info)
        if (type == "apps") {
            val field = EditText(this).apply {
                hint = "Buscar aplicativo"
                setSingleLine()
                textSize = 14f
                setTextColor(ink)
                setHintTextColor(muted)
                background = shape(tile, 14)
                setPadding(dp(13), dp(8), dp(13), dp(8))
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                        search = s?.toString().orEmpty(); renderRecords()
                    }
                    override fun afterTextChanged(s: Editable?) {}
                })
            }
            content.addView(field, LinearLayout.LayoutParams(-1, dp(52)).apply { bottomMargin = dp(12) })
        }
        items = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(items)
    }

    private fun refresh() {
        if (loading) return
        if (host.isBlank()) { error = "Nenhum aparelho selecionado."; renderRecords(); return }
        val key = RecentDevices.key(this, host)
        if (key == null) { error = "Chave do aparelho indisponível. Pareie novamente."; renderRecords(); return }
        loading = true
        error = null
        summary.text = "Atualizando…"
        action.isEnabled = false
        action.alpha = 0.5f
        val request = ++generation
        executor.execute {
            val result = runCatching {
                if (type == "apps") RemoteClient.installedApps(host, key, applicationContext, method)
                else RemoteClient.snapshot(host, key, applicationContext, method)
            }
            main.post {
                if (request == generation && !isFinishing && !isDestroyed) {
                    loading = false
                    action.isEnabled = true
                    action.alpha = 1f
                    result.onSuccess { payload = it; error = null }
                        .onFailure { error = it.message ?: "Consulta indisponível" }
                    renderRecords()
                }
            }
        }
    }

    private fun renderRecords() {
        if (!this::items.isInitialized) return
        items.removeAllViews()
        error?.let { problem ->
            summary.text = "Sem resposta"
            val panel = card()
            panel.addView(label("Não foi possível atualizar", 15f, true))
            panel.addView(gap(6))
            panel.addView(label(problem.take(180), 13f, secondary = true))
            items.addView(panel)
            return
        }
        val data = payload ?: return
        val isApps = type == "apps"
        val status = data.optJSONObject("notificationStatus")
        val records = data.optJSONArray(if (isApps) "items" else "events")
        val all = mutableListOf<JSONObject>()
        for (i in 0 until (records?.length() ?: 0)) {
            val obj = records?.optJSONObject(i) ?: continue
            if (!isApps && obj.optString("type") != "notificacao") continue
            if (!isApps && (SensitiveContent.isSensitive(obj.optString("title"), obj.optString("preview")) ||
                    SensitiveContent.isBlockedFinancialApp(obj.optString("app"), obj.optString("app")))) continue
            if (isApps && !"${obj.optString("name")} ${obj.optString("package")}".contains(search, ignoreCase = true)) continue
            all.add(obj)
        }
        if (!isApps) all.sortByDescending { it.optLong("whenMs") }
        summary.text = if (isApps) "${all.size} aplicativos visíveis no monitorado" else "${all.size} notificações elegíveis na última consulta"
        if (expandedInfo) {
            val help = card()
            help.addView(label(if (isApps) "Apenas aplicativos com ícone no lançador, sem dados internos. É necessária a autorização em Configurações > Aplicativos instalados no monitorado." else "O acesso requer: monitoramento ativo, permissão de notificação do Android e aplicativos autorizados. São exibidos somente até 30 eventos recentes mantidos em memória. Eventos excluídos pelo filtro de segurança não aparecem.", 12f, secondary = true))
            items.addView(help, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        }
        if (!isApps && status != null) {
            val diagnosis = card()
            diagnosis.addView(label("Estado da captura", 14f, true))
            diagnosis.addView(gap(8))
            val cause = when {
                !status.optBoolean("monitorEnabled") -> "Monitoramento interrompido no aparelho remoto"
                !status.optBoolean("listenerGranted") -> "Permissão de acesso às notificações não concedida no Android"
                !status.optBoolean("listenerConnected") -> "Permissão concedida, mas serviço de notificações desconectado"
                !status.optBoolean("allApps") && status.optInt("allowedCount") == 0 -> "Nenhum aplicativo autorizado para notificações"
                else -> "Captura habilitada (nem todos os apps ou eventos são elegíveis)"
            }
            diagnosis.addView(label(cause, 12.5f, secondary = true))
            diagnosis.addView(gap(5))
            val last = status.optLong("lastEventAt")
            diagnosis.addView(label("Último evento capturado: " + if (last > 0) DateFormat.format("dd/MM HH:mm", last) else "ainda não registrado", 11.5f, secondary = true))
            items.addView(diagnosis, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        }
        if (all.isEmpty()) {
            val panel = card()
            panel.addView(label(if (isApps) "Nenhum aplicativo disponível" else "Nenhuma notificação disponível", 15f, true))
            panel.addView(gap(7))
            panel.addView(label(if (isApps) "Ative a consulta no monitorado e toque em Atualizar. Alguns apps podem estar ocultos pelo Android." else "Confira o estado da captura acima, as permissões e os filtros no aparelho monitorado. Notificações antigas removidas pelo sistema não podem ser recuperadas.", 12.5f, secondary = true))
            items.addView(panel)
            return
        }
        all.take(120).forEach { item ->
            val panel = card()
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.TOP }
            row.addView(icon(if (isApps) R.drawable.ic_phone else R.drawable.ic_bell),
                LinearLayout.LayoutParams(dp(24), dp(24)).apply { rightMargin = dp(11) })
            val textColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            textColumn.addView(label(if (isApps) item.optString("name") else item.optString("title").ifBlank { "Notificação" }, 14.5f, true))
            textColumn.addView(gap(5))
            textColumn.addView(label(if (isApps) item.optString("package") else notificationAppName(item.optString("app")), 11.5f, secondary = true))
            if (!isApps) {
                textColumn.addView(gap(8))
                textColumn.addView(label(item.optString("preview"), 12.5f).apply {
                    maxLines = 5
                    ellipsize = TextUtils.TruncateAt.END
                    setOnClickListener {
                        maxLines = if (maxLines == 5) 100 else 5
                        ellipsize = if (maxLines == 5) TextUtils.TruncateAt.END else null
                    }
                })
                textColumn.addView(gap(9))
                textColumn.addView(label(DateFormat.format("dd/MM HH:mm:ss", item.optLong("whenMs")).toString(), 11f, secondary = true))
                textColumn.addView(gap(8))
                textColumn.addView(action("Copiar texto") {
                    val body = item.optString("preview")
                    if (body.isNotBlank() && !SensitiveContent.isSensitive(item.optString("title"), body)) {
                        val clip = ClipData.newPlainText("Notificação remota", body)
                        if (android.os.Build.VERSION.SDK_INT >= 33) {
                            clip.description.extras = PersistableBundle().apply {
                                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                            }
                        }
                        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                            .setPrimaryClip(clip)
                    }
                })
            }
            row.addView(textColumn, LinearLayout.LayoutParams(0, -2, 1f))
            panel.addView(row)
            items.addView(panel, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        }
    }

    override fun onDestroy() {
        generation++
        executor.shutdownNow()
        payload = null
        super.onDestroy()
    }
}
