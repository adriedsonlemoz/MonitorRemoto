package br.dev.monitorremoto.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.ActivityNotFoundException
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import br.dev.monitorremoto.core.LocationSharePolicy
import br.dev.monitorremoto.core.RecentDevices
import br.dev.monitorremoto.core.SettingsStore
import br.dev.monitorremoto.transport.RemoteClient
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

/** Tela sob demanda. Nenhum GPS remoto pode ser ligado; só consulta sessão local já autorizada. */
class RemoteLocationActivity : Activity() {
    companion object {
        const val EXTRA_HOST = "host"
        const val EXTRA_METHOD = "method"
    }
    private val main = Handler(Looper.getMainLooper())
    private var generation = 0
    private var host = ""
    private var method = "Chave salva"
    private var sample: Pair<Double, Double>? = null
    private lateinit var status: TextView
    private lateinit var details: TextView
    private lateinit var openMap: Button
    private lateinit var copy: Button
    private lateinit var refresh: Button
    private val dark get() = when (SettingsStore.theme(this)) {
        "dark" -> true
        "system" -> resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        else -> false
    }
    private val ink get() = if (dark) Color.WHITE else Color.rgb(27, 39, 54)
    private val dim get() = if (dark) Color.rgb(175, 187, 202) else Color.rgb(100, 112, 129)
    private val surface get() = if (dark) Color.rgb(29, 40, 54) else Color.WHITE
    private val bg get() = if (dark) Color.rgb(14, 22, 32) else Color.rgb(247, 248, 251)
    private fun dp(n: Int) = (resources.displayMetrics.density * n).toInt()
    private fun rounded(color: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(16).toFloat() }
    private fun text(value: String, size: Float = 14f, strong: Boolean = false): TextView = TextView(this).apply {
        this.text = value; textSize = size; setTextColor(if (strong) ink else dim)
        if (strong) setTypeface(null, Typeface.BOLD)
        setPadding(0, dp(3), 0, dp(3))
    }
    private fun button(title: String, click: () -> Unit) = Button(this).apply {
        text = title; isAllCaps = false; textSize = 13f
        setTextColor(ink); background = rounded(surface)
        elevation = 0f; stateListAnimator = null
        setOnClickListener { click() }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        host = intent.getStringExtra(EXTRA_HOST).orEmpty()
        method = intent.getStringExtra(EXTRA_METHOD).orEmpty().ifBlank { "Chave salva" }
        window.statusBarColor = bg; window.navigationBarColor = bg
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = if (dark) 0 else
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        val outer = ScrollView(this).apply { setBackgroundColor(bg) }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(22), dp(18), dp(22))
        }
        outer.addView(content)
        setContentView(outer)
        val title = RecentDevices.list(this).firstOrNull { it.host == host }?.name ?: "Aparelho remoto"
        content.addView(text("‹ Voltar", 14f).apply { setOnClickListener { finish() }; isClickable = true })
        content.addView(text("Localização", 22f, true))
        content.addView(text(title, 13f))
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(16))
            background = rounded(surface)
        }
        val spacing = View(this)
        content.addView(spacing, LinearLayout.LayoutParams(1, dp(13)))
        content.addView(box)
        status = text("Aguardando consulta…", 16f, true)
        details = text("A posição só é compartilhada durante uma sessão ativada no celular monitorado.", 13f)
        box.addView(status); box.addView(details)
        content.addView(text("Sem internet, as coordenadas continuam disponíveis pela rede local. Os mapas offline dependem do aplicativo de mapas instalado.", 12f))
        refresh = button("Atualizar posição") { load() }
        openMap = button("Abrir em Mapas") { openMap() }.apply { isEnabled = false }
        copy = button("Copiar coordenadas") { copyCoordinates() }.apply { isEnabled = false }
        content.addView(refresh, LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(12) })
        content.addView(openMap, LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(8) })
        content.addView(copy, LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(8) })
        if (host.isBlank() || RecentDevices.key(this, host) == null) {
            status.text = "Aparelho não pareado"
            details.text = "Escolha um aparelho com chave salva e conexão de rede."
            refresh.isEnabled = false
        } else load()
    }
    private fun load() {
        val secret = RecentDevices.key(this, host) ?: return
        val visit = ++generation
        sample = null
        openMap.isEnabled = false; copy.isEnabled = false; refresh.isEnabled = false
        status.text = "Consultando…"
        details.text = "Aguardando resposta criptografada do monitorado."
        thread(name = "MonitorRemoto-location", isDaemon = true) {
            val response = runCatching { RemoteClient.snapshot(host, secret, applicationContext, method) }
            main.post {
                if (isFinishing || isDestroyed || generation != visit) return@post
                refresh.isEnabled = true
                response.onSuccess { renderStatus(it.optJSONObject("location")) }
                    .onFailure {
                        status.text = "Sem resposta"
                        details.text = "A posição não pôde ser consultada: ${it.message?.take(160) ?: "erro de rede"}"
                    }
            }
        }
    }
    private fun renderStatus(value: JSONObject?) {
        if (value == null) {
            status.text = "Sem dados de localização"
            details.text = "Atualize o Monitor Remoto nos dois celulares para a mesma versão."
            return
        }
        when (value.optString("state")) {
            "desativada" -> {
                status.text = "Compartilhamento desativado"
                details.text = "No celular monitorado: Configurações → Localização → Iniciar compartilhamento."
            }
            "sem_permissao" -> {
                status.text = "Sem autorização Android"
                details.text = "Conceda localização aproximada ou precisa no aparelho monitorado."
            }
            "gps_desligado" -> {
                status.text = "Localização do Android desligada"
                details.text = "Ative o serviço de localização no celular monitorado."
            }
            "aguardando" -> {
                status.text = "Aguardando GPS/rede"
                details.text = "Sessão ativa, mas ainda não chegou uma leitura válida. Tente perto de uma janela ou ao ar livre."
            }
            "expirada" -> {
                status.text = "Posição expirada"
                details.text = "A última leitura ultrapassou 30 minutos. Nenhuma coordenada antiga será apresentada como atual."
            }
            "disponivel", "antiga" -> {
                val lat = value.optDouble("latitude", Double.NaN)
                val lon = value.optDouble("longitude", Double.NaN)
                val age = value.optLong("ageMs", -1L)
                if (!LocationSharePolicy.coordinatesValid(lat, lon) || !LocationSharePolicy.usableAgeMs(age)) {
                    status.text = "Leitura inválida"; details.text = "O monitorado informou uma posição inconsistente."
                    return
                }
                sample = lat to lon
                openMap.isEnabled = true; copy.isEnabled = true
                val coordinates = String.format(Locale.US, "%.6f, %.6f", lat, lon)
                val accuracy = if (value.isNull("accuracyM")) "não informada"
                    else "±${value.optDouble("accuracyM").toInt()} m (estimada)"
                val time = value.optLong("timestampMs", 0L).takeIf { it > 0L }
                    ?.let { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(Date(it)) } ?: "não informada"
                status.text = if (value.optString("state") == "antiga") "Última posição (antiga)" else "Posição disponível"
                details.text = "Coordenadas: $coordinates\nPrecisão: $accuracy\nOrigem: ${value.optString("provider", "desconhecida")}\nIdade: ${age / 1000} s\nLeitura: $time\nPerfil: ${LocationSharePolicy.label(value.optString("profile"))}"
            }
            else -> { status.text = "Estado indisponível"; details.text = "O monitorado não informou uma localização válida." }
        }
    }
    private fun openMap() {
        val (lat, lon) = sample ?: return
        val coords = String.format(Locale.US, "%.6f,%.6f", lat, lon)
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:$coords?q=$coords")))
        } catch (_: ActivityNotFoundException) {
            status.text = "Nenhum aplicativo de mapas instalado"
        } catch (_: SecurityException) { status.text = "Abertura do mapa bloqueada pelo Android" }
    }
    private fun copyCoordinates() {
        val (lat, lon) = sample ?: return
        val coordinates = String.format(Locale.US, "%.6f, %.6f", lat, lon)
        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText("Localização remota", coordinates))
        status.text = "Coordenadas copiadas"
    }
    override fun onDestroy() {
        generation++
        main.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
