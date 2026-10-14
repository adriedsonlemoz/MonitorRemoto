package br.dev.monitorremoto.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.PersistableBundle
import android.text.format.Formatter
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import br.dev.monitorremoto.R
import br.dev.monitorremoto.core.SettingsStore
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Informações recebidas somente após consulta autenticada; nada é consultado diretamente no controlador. */
class RemoteDetailsActivity : Activity() {
    companion object {
        const val EXTRA_SNAPSHOT = "br.dev.monitorremoto.DETAILS_SNAPSHOT"
        const val EXTRA_DEVICE = "br.dev.monitorremoto.DETAILS_DEVICE"
        const val EXTRA_HOST = "br.dev.monitorremoto.DETAILS_HOST"
        const val EXTRA_METHOD = "br.dev.monitorremoto.DETAILS_METHOD"
        const val EXTRA_UPDATED = "br.dev.monitorremoto.DETAILS_UPDATED"
    }
    private val dark: Boolean get() = when (SettingsStore.theme(this)) {
        "dark" -> true
        "system" -> (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        else -> false
    }
    private val pageBg: Int get() = if (dark) Color.rgb(14, 22, 35) else Color.rgb(247, 249, 252)
    private val cardBg: Int get() = if (dark) Color.rgb(28, 41, 58) else Color.WHITE
    private val tileBg: Int get() = if (dark) Color.rgb(22, 34, 50) else Color.rgb(243, 246, 249)
    private val fg: Int get() = if (dark) Color.rgb(233, 239, 247) else Color.rgb(23, 34, 48)
    private val subdued: Int get() = if (dark) Color.rgb(167, 184, 203) else Color.rgb(92, 109, 128)
    private val border: Int get() = if (dark) Color.rgb(47, 64, 83) else Color.rgb(224, 231, 239)
    private val teal: Int get() = if (dark) Color.rgb(90, 214, 188) else Color.rgb(24, 166, 146)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun drawable(color: Int, radius: Int, outline: Boolean = false): GradientDrawable = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
        if (outline) setStroke(dp(1), border)
    }
    private fun text(value: String, size: Float, bold: Boolean = false, muted: Boolean = false) = TextView(this).apply {
        this.text = value
        textSize = size
        setTextColor(if (muted) subdued else fg)
        if (bold) setTypeface(null, Typeface.BOLD)
        includeFontPadding = false
    }
    private fun icon(id: Int, size: Int, tint: Int = fg) = ImageView(this).apply {
        setImageResource(id)
        imageTintList = ColorStateList.valueOf(tint)
        scaleType = ImageView.ScaleType.FIT_CENTER
        contentDescription = null
        layoutParams = LinearLayout.LayoutParams(dp(size), dp(size))
    }
    private fun gap(height: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(height)) }
    private fun labelValue(data: JSONObject?, key: String): String = data?.optString(key)
        ?.takeIf { it.isNotBlank() && it != "null" } ?: "Não disponível"
    private fun yesNo(data: JSONObject?, key: String): String = when {
        data == null || !data.has(key) || data.isNull(key) -> "Não disponível"
        data.optBoolean(key) -> "Sim"
        else -> "Não"
    }
    private fun bytes(data: JSONObject?, key: String): String = if (data == null || !data.has(key) || data.isNull(key)) {
        "Não disponível"
    } else Formatter.formatFileSize(this, data.optLong(key))
    private fun time(value: Long) = if (value > 0L) {
        SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault()).format(Date(value))
    } else "Não disponível"

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(if (dark) android.R.style.Theme_Material_NoActionBar else android.R.style.Theme_Material_Light_NoActionBar)
        super.onCreate(savedInstanceState)
        val payload = runCatching { JSONObject(intent.getStringExtra(EXTRA_SNAPSHOT).orEmpty()) }.getOrNull()
        if (payload == null) { finish(); return }
        val battery = payload.optJSONObject("battery")
        val network = payload.optJSONObject("network")
        val system = payload.optJSONObject("deviceInfo")
        val sim = payload.optJSONObject("telephony")
        val device = intent.getStringExtra(EXTRA_DEVICE).orEmpty().ifBlank {
            labelValue(battery, "device")
        }
        val host = intent.getStringExtra(EXTRA_HOST).orEmpty()
        val method = intent.getStringExtra(EXTRA_METHOD).orEmpty()
        val received = intent.getLongExtra(EXTRA_UPDATED, 0L)

        // Se houver número compartilhado, impedir que outras apps façam screenshot da tela.
        if (sim?.optString("phoneNumber")?.takeIf { it.isNotBlank() && it != "null" } != null) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        window.statusBarColor = pageBg
        window.navigationBarColor = pageBg
        window.decorView.systemUiVisibility = if (dark) 0 else
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR

        val wrapper = ScrollView(this).apply { isFillViewport = true; setBackgroundColor(pageBg) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(22), dp(20), dp(34))
        }
        wrapper.addView(root)
        setContentView(wrapper)

        val toolbar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val back = icon(R.drawable.ic_back, 24).apply {
            background = drawable(cardBg, 13, true)
            setPadding(dp(11), dp(11), dp(11), dp(11))
            contentDescription = "Voltar ao painel de controle"
            isFocusable = true; isClickable = true
            setOnClickListener { finish() }
        }
        toolbar.addView(back, LinearLayout.LayoutParams(dp(48), dp(48)))
        toolbar.addView(View(this), LinearLayout.LayoutParams(dp(12), 1))
        val heading = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        heading.addView(text("Detalhes do aparelho", 21f, bold = true))
        heading.addView(gap(4))
        heading.addView(text(device, 12f, muted = true))
        toolbar.addView(heading, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(toolbar)
        root.addView(gap(14))
        root.addView(text("Última consulta: ${time(received)}", 11.5f, muted = true))
        root.addView(gap(16))

        fun section(iconId: Int, title: String, compose: (LinearLayout) -> Unit) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = drawable(cardBg, 22, true)
                setPadding(dp(16), dp(17), dp(16), dp(17))
            }
            val titleRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            titleRow.addView(icon(iconId, 22, teal))
            titleRow.addView(View(this), LinearLayout.LayoutParams(dp(10), 1))
            titleRow.addView(text(title, 16f, bold = true))
            card.addView(titleRow)
            card.addView(gap(16))
            compose(card)
            root.addView(card)
            root.addView(gap(13))
        }
        fun cell(label: String, value: String): LinearLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = drawable(tileBg, 14)
            setPadding(dp(12), dp(11), dp(8), dp(12))
            addView(text(label, 10.5f, muted = true))
            addView(gap(7))
            addView(text(value, 13f, bold = true).apply { maxLines = 3 })
        }
        fun pair(parent: LinearLayout, first: Pair<String, String>, second: Pair<String, String>) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(cell(first.first, first.second), LinearLayout.LayoutParams(0, -2, 1f).apply {
                rightMargin = dp(8)
            })
            row.addView(cell(second.first, second.second), LinearLayout.LayoutParams(0, -2, 1f))
            parent.addView(row)
            parent.addView(gap(8))
        }
        fun row(parent: LinearLayout, key: String, value: String) {
            val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            line.addView(text(key, 12f, muted = true), LinearLayout.LayoutParams(0, -2, 1f))
            line.addView(text(value, 12f, bold = true).apply { gravity = Gravity.END; maxLines = 3 },
                LinearLayout.LayoutParams(0, -2, 1.1f))
            parent.addView(line)
            parent.addView(gap(12))
        }
        fun decimal(data: JSONObject?, key: String, unit: String, digits: Int = 1): String =
            if (data == null || !data.has(key) || data.isNull(key)) "Não disponível"
            else String.format(Locale.getDefault(), "%.${digits}f %s", data.optDouble(key), unit)

        section(R.drawable.ic_battery, "Bateria") { card ->
            val state = when {
                battery?.optBoolean("full") == true -> "Cheia"
                battery?.optBoolean("charging") == true -> "Carregando"
                else -> "Descarregando"
            }
            pair(card, "Carga" to decimal(battery, "levelPercent", "%", 0),
                "Temperatura" to decimal(battery, "temperatureC", "°C"))
            pair(card, "Estado" to state,
                "Tensão" to if (battery?.has("voltageMv") == true && !battery.isNull("voltageMv"))
                    "${battery.optInt("voltageMv")} mV" else "Não disponível")
            pair(card, "Corrente atual" to decimal(battery, "currentMa", "mA", 0),
                "Corrente média" to decimal(battery, "averageMa", "mA", 0))
            card.addView(text("Corrente e tensão são leituras fornecidas pelo Android; podem variar conforme o hardware.", 10.5f, muted = true))
        }
        section(R.drawable.ic_wifi, "Wi-Fi e conexão") { card ->
            pair(card, "Rede ativa" to labelValue(network, "type"),
                "IP de acesso" to host.ifBlank { "Não disponível" })
            pair(card, "Wi-Fi detectado" to yesNo(network, "wifiDetected"),
                "Dados móveis detectados" to yesNo(network, "cellularDetected"))
            row(card, "Internet validada pelo Android", yesNo(network, "internetValidated"))
            row(card, "Rede tarifada", yesNo(network, "metered"))
            row(card, "IP informado pelo aparelho", labelValue(network, "localIpv4"))
            row(card, "Método de pareamento", method.ifBlank { "Não disponível" })
        }
        section(R.drawable.ic_cellular, "Rede móvel e chip") { card ->
            pair(card, "Chip" to labelValue(sim, "simState"),
                "Operadora do chip" to labelValue(sim, "operator"))
            row(card, "Operadora da rede", labelValue(sim, "networkOperator"))
            val shareNumber = sim?.optBoolean("numberAuthorized") == true
            row(card, "Número da linha", if (shareNumber) labelValue(sim, "phoneNumber") else "Não compartilhado")
            card.addView(text("O número depende da operadora e de uma autorização opcional no celular monitorado.", 10.5f, muted = true))
        }
        section(R.drawable.ic_storage, "Armazenamento") { card ->
            val total = system?.optLong("storageTotalBytes") ?: 0L
            val available = system?.optLong("storageAvailableBytes") ?: 0L
            val valid = total > 0L && available in 0L..total
            if (valid) {
                val progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                    max = 1000
                    this.progress = (((total - available).toDouble() / total) * 1000).toInt()
                    progressTintList = ColorStateList.valueOf(teal)
                    progressBackgroundTintList = ColorStateList.valueOf(border)
                }
                card.addView(progress, LinearLayout.LayoutParams(-1, dp(9)))
                card.addView(gap(11))
            }
            pair(card, "Capacidade" to bytes(system, "storageTotalBytes"),
                "Disponível" to bytes(system, "storageAvailableBytes"))
            val used = if (valid) Formatter.formatFileSize(this, total - available) else "Não disponível"
            row(card, "Ocupado (partição de dados)", used)
            card.addView(text("Mostra o armazenamento de dados do aparelho, não o tamanho de contas na nuvem.", 10.5f, muted = true))
        }
        section(R.drawable.ic_cpu, "Sistema e hardware") { card ->
            pair(card, "Fabricante" to labelValue(system, "manufacturer"),
                "Modelo" to labelValue(system, "model"))
            pair(card, "Android" to labelValue(system, "androidVersion"),
                "API do Android" to labelValue(system, "sdk"))
            pair(card, "RAM total" to bytes(system, "ramTotalBytes"),
                "RAM disponível" to bytes(system, "ramAvailableBytes"))
            row(card, "Arquitetura", labelValue(system, "architecture"))
            row(card, "Patch de segurança", labelValue(system, "securityPatch"))
            val uptime = system?.optLong("uptimeMs") ?: 0L
            row(card, "Tempo ligado", if (uptime > 0L) "${uptime / 3_600_000} h ${(uptime / 60_000) % 60} min" else "Não disponível")
        }

        val copyButton = Button(this).apply {
            text = "Copiar resumo técnico"
            textSize = 13.5f
            isAllCaps = false
            setTextColor(if (dark) Color.rgb(14, 22, 35) else Color.WHITE)
            background = drawable(teal, 16)
            setOnClickListener {
                val summary = buildString {
                    append(device).append("\nAtualização: ").append(time(received))
                    append("\nBateria: ").append(decimal(battery, "levelPercent", "%", 0))
                    append("\nTemperatura: ").append(decimal(battery, "temperatureC", "°C"))
                    append("\nRede: ").append(labelValue(network, "type"))
                    append("\nArmazenamento: ").append(bytes(system, "storageTotalBytes"))
                    append("\nAndroid: ").append(labelValue(system, "androidVersion"))
                }
                val clip = ClipData.newPlainText("Monitor Remoto: resumo", summary)
                if (Build.VERSION.SDK_INT >= 33) {
                    clip.description.extras = PersistableBundle().apply {
                        putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                    }
                }
                (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
                android.widget.Toast.makeText(this@RemoteDetailsActivity, "Resumo copiado", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        root.addView(copyButton, LinearLayout.LayoutParams(-1, dp(51)))
        root.addView(gap(10))
        root.addView(text("Dados disponíveis apenas enquanto o aparelho monitorado responder. Informações não acessíveis são indicadas explicitamente.", 11f, muted = true))
    }
}
