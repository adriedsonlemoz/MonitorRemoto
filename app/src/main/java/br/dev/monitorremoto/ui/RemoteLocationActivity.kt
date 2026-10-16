package br.dev.monitorremoto.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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

/** Mapa interno de consulta. Nenhum comando remoto inicia GNSS nem histórico oculto. */
class RemoteLocationActivity : Activity() {
    companion object {
        const val EXTRA_HOST = "host"
        const val EXTRA_METHOD = "method"
        private val RATES = longArrayOf(15_000L, 30_000L, 60_000L, 300_000L)
        private val RATE_LABELS = arrayOf("15 segundos", "30 segundos", "60 segundos", "5 minutos")
    }
    private val main = Handler(Looper.getMainLooper())
    private var generation = 0
    private var host = ""
    private var method = "Chave salva"
    private var sample: Pair<Double, Double>? = null
    private lateinit var status: TextView
    private lateinit var details: TextView
    private lateinit var quality: TextView
    private lateinit var openMap: Button
    private lateinit var copy: Button
    private lateinit var refresh: Button
    private lateinit var followButton: Button
    private lateinit var onlineButton: Button
    private lateinit var intervalButton: Button
    private lateinit var automaticButton: Button
    private lateinit var map: RemoteMapView
    private var visible = false
    private var inFlight = false
    private var automatic = false
    private var intervalMs = 60_000L
    private var mapOnline = false
    private val prefs by lazy { getSharedPreferences("monitor_location_map_controller", Context.MODE_PRIVATE) }
    // A permissão de acesso aos blocos online é separada para cada aparelho pareado.
    private val tileConsentKey get() = "tiles_online_allowed_${host.hashCode()}"
    private val ticker = object : Runnable {
        override fun run() {
            if (!visible || !automatic) return
            if (!inFlight) load()
            main.postDelayed(this, intervalMs)
        }
    }
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
    private fun rounded(color: Int, corner: Int = 16) = GradientDrawable().apply { setColor(color); cornerRadius = dp(corner).toFloat() }
    private fun text(value: String, size: Float = 14f, strong: Boolean = false): TextView = TextView(this).apply {
        this.text = value; textSize = size; setTextColor(if (strong) ink else dim)
        if (strong) setTypeface(null, Typeface.BOLD)
        setPadding(0, dp(3), 0, dp(3))
    }
    private fun button(title: String, click: () -> Unit) = Button(this).apply {
        text = title; isAllCaps = false; textSize = 12.5f; minWidth = 0
        setTextColor(ink); background = rounded(surface, 12)
        elevation = 0f; stateListAnimator = null
        setPadding(dp(5), 0, dp(5), 0)
        setOnClickListener { click() }
    }
    private fun actions(parent: LinearLayout, vararg buttons: Button) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        buttons.forEachIndexed { i, b ->
            row.addView(b, LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                if (i > 0) leftMargin = dp(7)
            })
        }
        parent.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        host = intent.getStringExtra(EXTRA_HOST).orEmpty()
        method = intent.getStringExtra(EXTRA_METHOD).orEmpty().ifBlank { "Chave salva" }
        intervalMs = prefs.getLong("refresh_interval_ms", 60_000L).takeIf { it in RATES } ?: 60_000L
        mapOnline = prefs.getBoolean(tileConsentKey, false)
        window.statusBarColor = bg; window.navigationBarColor = bg
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = if (dark) 0 else
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        val outer = ScrollView(this).apply { setBackgroundColor(bg); isFillViewport = true }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(14), dp(14), dp(22))
        }
        outer.addView(content)
        setContentView(outer)
        val title = RecentDevices.list(this).firstOrNull { it.host == host }?.name ?: "Aparelho remoto"
        content.addView(text("‹ Voltar", 13f).apply {
            setOnClickListener { finish() }; isClickable = true; isFocusable = true
            contentDescription = "Voltar ao painel"
        })
        content.addView(text("Localização · mapa", 21f, true))
        content.addView(text(title, 12f))
        content.addView(text("Mapa online opcional · posição somente após autorização no monitorado", 11f))

        map = RemoteMapView(this).apply {
            enableOnline(mapOnline)
            background = rounded(if (dark) Color.rgb(34, 46, 62) else Color.rgb(238, 244, 247))
            clipToOutline = true
            contentDescription = "Mapa com posição remota; arraste para explorar, use dois dedos para ampliar"
        }
        content.addView(map, LinearLayout.LayoutParams(-1, dp(300)).apply { topMargin = dp(11) })
        actions(content, button("−") { map.zoomBy(-1) }, button("+  Zoom") { map.zoomBy(1) },
            button("Centralizar") { map.centerOnMarker() })
        followButton = button("Seguir: ligado") {
            map.setFollow(!map.isFollowing())
            updateFollowLabel()
        }
        map.onFollowChanged = { updateFollowLabel() }
        onlineButton = button("") { changeTilesPermission() }
        actions(content, followButton, onlineButton)
        updateOnlineLabel()
        content.addView(text("© OpenStreetMap contributors · somente blocos vistos, cache local. Sem rede, o GPS funciona, mas o mapa mostra apenas os blocos já consultados.", 10.5f).apply {
            setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.openstreetmap.org/copyright"))) }
        })

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(13), dp(12), dp(13), dp(12))
            background = rounded(surface)
        }
        content.addView(box, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        status = text("Aguardando consulta…", 16f, true)
        details = text("A posição depende de uma sessão iniciada pelo monitorado.", 12.5f)
        box.addView(status)
        quality = text("Sem medição de precisão ou idade de posição.", 12f)
        quality.setPadding(0, dp(5), 0, dp(7))
        box.addView(quality)
        box.addView(details)
        refresh = button("Atualizar agora") { load() }
        openMap = button("Mapa externo") { openExternalMap() }.apply { isEnabled = false }
        copy = button("Copiar GPS") { copyCoordinates() }.apply { isEnabled = false }
        actions(content, refresh, openMap, copy)
        content.addView(text("Uma consulta sem resposta limpa o marcador; o mapa não mostra uma posição antiga como atual. A localização do monitorado precisa continuar ativa e autorizada.", 11f))
        automaticButton = button("Automático: desligado") {
            automatic = !automatic
            automaticButton.text = if (automatic) "Automático: ligado" else "Automático: desligado"
            schedule()
        }
        intervalButton = button("Consultar: ${RATE_LABELS[RATES.indexOf(intervalMs)]}") { chooseInterval() }
        actions(content, automaticButton, intervalButton)
        content.addView(text("O intervalo acima controla apenas as consultas feitas por esta tela. O perfil GPS é escolhido no aparelho monitorado e não pode ser alterado remotamente. Atualizações e precisão dependem do Android.", 11f))
        if (host.isBlank() || RecentDevices.key(this, host) == null) {
            status.text = "Aparelho não pareado"
            details.text = "Escolha um aparelho com chave salva e conexão de rede."
            refresh.isEnabled = false
            automaticButton.isEnabled = false
        } else load()
    }
    private fun updateFollowLabel() {
        followButton.text = if (map.isFollowing()) "Seguir: ligado" else "Seguir: desligado"
    }
    private fun updateOnlineLabel() {
        onlineButton.text = if (mapOnline) "Mapa online: ligado" else "Ativar mapa online"
    }
    private fun changeTilesPermission() {
        if (mapOnline) {
            mapOnline = false
            prefs.edit().putBoolean(tileConsentKey, false).apply()
            map.enableOnline(false)
            updateOnlineLabel()
        } else {
            AlertDialog.Builder(this)
                .setTitle("Carregar mapa online?")
                .setMessage("Para buscar blocos do OpenStreetMap, a região visualizada e seu endereço de rede são comunicados ao serviço de mapas. Só serão carregados blocos da área visível, com cache local. Você pode desligar a qualquer momento.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Permitir") { _, _ ->
                    mapOnline = true
                    prefs.edit().putBoolean(tileConsentKey, true).apply()
                    map.enableOnline(true)
                    updateOnlineLabel()
                }.show()
        }
    }
    private fun chooseInterval() {
        AlertDialog.Builder(this).setTitle("Frequência de consulta do controlador")
            .setSingleChoiceItems(RATE_LABELS, RATES.indexOf(intervalMs)) { dialog, idx ->
                intervalMs = RATES[idx]
                prefs.edit().putLong("refresh_interval_ms", intervalMs).apply()
                intervalButton.text = "Consultar: ${RATE_LABELS[idx]}"
                dialog.dismiss(); schedule()
            }.setNegativeButton("Cancelar", null).show()
    }
    private fun schedule() {
        main.removeCallbacks(ticker)
        if (visible && automatic) main.postDelayed(ticker, intervalMs)
    }
    private fun load() {
        val secret = RecentDevices.key(this, host) ?: return
        if (inFlight) return
        val visit = ++generation
        inFlight = true
        refresh.isEnabled = false
        status.text = "Consultando localização…"
        quality.text = "Aguardando resposta autenticada"
        thread(name = "MonitorRemoto-location", isDaemon = true) {
            val response = runCatching { RemoteClient.snapshot(host, secret, applicationContext, method) }
            main.post {
                if (isFinishing || isDestroyed || generation != visit) return@post
                inFlight = false
                refresh.isEnabled = true
                response.onSuccess { renderStatus(it.optJSONObject("location")) }
                    .onFailure {
                        // Não apresentar leitura anterior como posição atual após perda de conexão.
                        clearSample()
                        status.text = "Sem resposta"
                        quality.text = "Sem posição atual · confira a rede e a sessão no monitorado"
                        details.text = "Última consulta falhou: ${it.message?.take(150) ?: "erro de rede"}"
                    }
            }
        }
    }
    private fun clearSample() {
        sample = null
        map.clearPosition()
        quality.text = "Sem leitura válida nesta consulta"
        openMap.isEnabled = false; copy.isEnabled = false
    }
    private fun renderStatus(value: JSONObject?) {
        clearSample()
        if (value == null) {
            status.text = "Sem dados de localização"
            details.text = "Instale a mesma versão do Monitor Remoto nos dois celulares."
            return
        }
        when (value.optString("state")) {
            "desativada" -> { status.text = "Compartilhamento desativado"; details.text = "No monitorado: Configurações → Localização → Iniciar compartilhamento." }
            "sem_permissao" -> { status.text = "Sem autorização Android"; details.text = "Conceda localização aproximada ou precisa no monitorado." }
            "gps_desligado" -> { status.text = "Localização Android desligada"; details.text = "Ative a localização no aparelho monitorado." }
            "aguardando" -> { status.text = "Aguardando GPS/rede"; details.text = "Sessão ativa, mas sem leitura válida. Tente ao ar livre." }
            "expirada" -> { status.text = "Posição expirada"; details.text = "Última leitura ultrapassou 30 minutos. Posição antiga não é exibida como atual." }
            "disponivel", "antiga" -> {
                val lat = value.optDouble("latitude", Double.NaN)
                val lon = value.optDouble("longitude", Double.NaN)
                val age = value.optLong("ageMs", -1L)
                if (!LocationSharePolicy.coordinatesValid(lat, lon) || !LocationSharePolicy.usableAgeMs(age)) {
                    status.text = "Leitura inválida"; details.text = "O aparelho informou uma posição inconsistente."
                    return
                }
                val accuracy = if (value.isNull("accuracyM")) null else value.optDouble("accuracyM").takeIf { it.isFinite() && it >= 0.0 }
                sample = lat to lon
                map.setPosition(lat, lon, accuracy)
                updateFollowLabel()
                openMap.isEnabled = true; copy.isEnabled = true
                val time = value.optLong("timestampMs", 0L).takeIf { it > 0L }
                    ?.let { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(Date(it)) } ?: "não informada"
                status.text = if (value.optString("state") == "antiga") "Última posição (antiga)" else "Posição disponível"
                quality.text = "${if (value.optString("state") == "antiga") "⚠ Leitura antiga" else "✓ Leitura recebida"}  ·  " +
                    "idade ${age / 1000}s  ·  " +
                    (accuracy?.let { "precisão estimada ±${it.toInt()}m" } ?: "precisão não informada")
                details.text = "${String.format(Locale.US, "%.6f, %.6f", lat, lon)}\n" +
                    "Precisão: ${accuracy?.let { "±${it.toInt()} m (estimada)" } ?: "não informada"} · Fonte: ${value.optString("provider", "desconhecida")}\n" +
                    "Idade: ${age / 1000} s · Atualização: $time\n" +
                    "Perfil GPS do monitorado: ${LocationSharePolicy.label(value.optString("profile"))}"
            }
            else -> { status.text = "Estado indisponível"; details.text = "O monitorado não informou uma localização válida." }
        }
    }
    private fun openExternalMap() {
        val (lat, lon) = sample ?: return
        val coords = String.format(Locale.US, "%.6f,%.6f", lat, lon)
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:$coords?q=$coords"))) }
        catch (_: ActivityNotFoundException) { status.text = "Nenhum aplicativo de mapas instalado" }
        catch (_: SecurityException) { status.text = "Abertura bloqueada pelo Android" }
    }
    private fun copyCoordinates() {
        val (lat, lon) = sample ?: return
        val coords = String.format(Locale.US, "%.6f, %.6f", lat, lon)
        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText("Localização remota", coords))
        status.text = "Coordenadas copiadas"
    }
    override fun onResume() { super.onResume(); visible = true; schedule() }
    override fun onPause() { visible = false; main.removeCallbacks(ticker); super.onPause() }
    override fun onDestroy() {
        generation++
        main.removeCallbacksAndMessages(null)
        map.dispose()
        super.onDestroy()
    }
}
