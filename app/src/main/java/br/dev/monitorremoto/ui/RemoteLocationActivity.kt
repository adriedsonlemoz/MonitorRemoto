package br.dev.monitorremoto.ui

import android.app.Activity
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
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import br.dev.monitorremoto.core.LocationSharePolicy
import br.dev.monitorremoto.core.LocationUiPolicy
import br.dev.monitorremoto.core.RecentDevices
import br.dev.monitorremoto.core.SettingsStore
import br.dev.monitorremoto.transport.RemoteClient
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

/**
 * Mapa remoto de consulta autenticada.
 * Exibe uma leitura fresca quando possível, sem inventar posição.
 */
class RemoteLocationActivity : Activity() {
    companion object {
        const val EXTRA_HOST = "host"
        const val EXTRA_METHOD = "method"
        private val RATES = longArrayOf(5_000L, 10_000L, 15_000L, 30_000L, 60_000L)
        private val RATE_LABELS = arrayOf("5 s", "10 s", "15 s", "30 s", "60 s")
    }

    private val main = Handler(Looper.getMainLooper())
    private var generation = 0
    private var host = ""
    private var method = "Chave salva"
    private var sample: Pair<Double, Double>? = null
    private lateinit var status: TextView
    private lateinit var quality: TextView
    private lateinit var details: TextView
    private lateinit var titleLine: TextView
    private lateinit var openMap: Button
    private lateinit var copy: Button
    private lateinit var refresh: Button
    private lateinit var followButton: Button
    private lateinit var onlineButton: Button
    private lateinit var intervalButton: Button
    private lateinit var automaticButton: Button
    private lateinit var trackingButton: Button
    private lateinit var connectionChip: TextView
    private lateinit var fixChip: TextView
    private lateinit var responseMetric: TextView
    private lateinit var accuracyMetric: TextView
    private lateinit var ageMetric: TextView
    private lateinit var map: RemoteMapView
    private var visible = false
    private var inFlight = false
    private var automatic = true
    private var tracking = false
    private var lastResponseAtMs = 0L
    private var fixAgeAtResponseMs = -1L
    private var fixReceivedElapsedMs = 0L
    private var intervalMs = 10_000L
    private var mapOnline = false
    private var lastRoundTripMs = -1L
    
    private var pendingRetry = false
    private val prefs by lazy { getSharedPreferences("monitor_location_map_controller", Context.MODE_PRIVATE) }
    private val tileConsentKey get() = "tiles_online_allowed_${host.hashCode()}"

    private val ticker = object : Runnable {
        override fun run() {
            if (!visible) return
            val delay = LocationUiPolicy.refreshDelayMs(tracking, automatic, intervalMs) ?: return
            if (!inFlight) load(manual = false)
            main.postDelayed(this, delay)
        }
    }

    private val statusTicker = object : Runnable {
        override fun run() {
            if (!visible) return
            refreshAges()
            main.postDelayed(this, 1_000L)
        }
    }
    private val dark get() = when (SettingsStore.theme(this)) {
        "dark" -> true
        "system" -> resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        else -> false
    }
    private val ink get() = if (dark) Color.WHITE else Color.rgb(27, 39, 54)
    private val dim get() = if (dark) Color.rgb(178, 189, 203) else Color.rgb(104, 114, 129)
    private val subtle get() = if (dark) Color.rgb(95, 108, 125) else Color.rgb(123, 134, 149)
    private val surface get() = if (dark) Color.rgb(28, 39, 54) else Color.WHITE
    private val surfaceAlt get() = if (dark) Color.rgb(20, 30, 42) else Color.rgb(241, 245, 248)
    private val accent get() = Color.rgb(31, 172, 160)
    private val warn get() = Color.rgb(211, 125, 39)
    private val danger get() = Color.rgb(189, 71, 71)
    private val bg get() = if (dark) Color.rgb(14, 22, 32) else Color.rgb(247, 248, 251)
    private fun dp(n: Int) = (resources.displayMetrics.density * n).toInt()
    private fun rounded(color: Int, corner: Int = 16, strokeColor: Int? = null) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(color)
        cornerRadius = dp(corner).toFloat()
        strokeColor?.let { setStroke(dp(1), it) }
    }
    private fun text(value: String, size: Float = 14f, strong: Boolean = false, color: Int = if (strong) ink else dim): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (strong) setTypeface(null, Typeface.BOLD)
        }
    private fun iconChip(icon: String, title: String, accentColor: Int = accent) = TextView(this).apply {
        text = "$icon  $title"
        textSize = 12f
        setTextColor(ink)
        background = rounded(if (dark) Color.argb(80, 44, 61, 83) else Color.argb(26, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor)), 99)
        setPadding(dp(10), dp(7), dp(10), dp(7))
        gravity = Gravity.CENTER_VERTICAL
    }
    private fun setChip(chip: TextView, title: String, color: Int) {
        chip.text = title
        chip.setTextColor(if (dark) Color.WHITE else color)
        chip.background = rounded(Color.argb(if (dark) 60 else 24,
            Color.red(color), Color.green(color), Color.blue(color)), 99)
    }
    private fun button(title: String, click: () -> Unit) = Button(this).apply {
        text = title
        isAllCaps = false
        textSize = 12.5f
        minWidth = 0
        setTextColor(ink)
        background = rounded(surface, 14, if (dark) Color.rgb(51, 66, 84) else Color.rgb(225, 231, 236))
        elevation = 0f
        stateListAnimator = null
        setPadding(dp(8), 0, dp(8), 0)
        setOnClickListener { click() }
    }
    private fun actions(parent: LinearLayout, vararg buttons: Button) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        buttons.forEachIndexed { i, b ->
            row.addView(b, LinearLayout.LayoutParams(0, dp(46), 1f).apply { if (i > 0) leftMargin = dp(8) })
        }
        parent.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        host = intent.getStringExtra(EXTRA_HOST).orEmpty()
        method = intent.getStringExtra(EXTRA_METHOD).orEmpty().ifBlank { "Chave salva" }
        intervalMs = prefs.getLong("refresh_interval_ms", 10_000L).takeIf { it in RATES } ?: 10_000L
        mapOnline = prefs.getBoolean(tileConsentKey, false)

        window.statusBarColor = bg
        window.navigationBarColor = bg
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = if (dark) 0 else View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR

        val outer = ScrollView(this).apply { setBackgroundColor(bg); isFillViewport = true }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(22))
        }
        outer.addView(content)
        setContentView(outer)

        val title = RecentDevices.list(this).firstOrNull { it.host == host }?.name ?: "Aparelho remoto"

        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val back = text("‹", 26f, true, ink).apply {
            setPadding(0, dp(6), dp(12), dp(6))
            setOnClickListener { finish() }
            isClickable = true
            isFocusable = true
            contentDescription = "Voltar ao painel"
        }
        val titleCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titleCol.addView(text("Localização", 22f, true))
        titleLine = text("$title · consulta protegida", 12.5f, false, dim)
        titleCol.addView(titleLine)
        header.addView(back)
        header.addView(titleCol, LinearLayout.LayoutParams(0, -2, 1f))
        content.addView(header)

        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = rounded(surface, 20)
        }
        val heroTop = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val pin = TextView(this).apply {
            text = "⌖"
            textSize = 26f
            setTextColor(accent)
            gravity = Gravity.CENTER
            background = rounded(if (dark) Color.rgb(36, 54, 68) else Color.rgb(235, 244, 243), 99)
            setPadding(dp(10), dp(10), dp(10), dp(10))
        }
        heroTop.addView(pin)
        val heroText = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(10), 0, 0, 0) }
        status = text("Buscando posição", 17f, true)
        quality = text("Aguardando resposta do monitorado", 12.5f, false, dim)
        heroText.addView(status)
        heroText.addView(quality)
        heroTop.addView(heroText, LinearLayout.LayoutParams(0, -2, 1f))
        hero.addView(heroTop)

        details = text("Sem leitura recebida ainda.", 12f, false, subtle).apply {
            setPadding(0, dp(10), 0, 0)
            maxLines = 3
        }
        hero.addView(details)

        val chips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.START }
        connectionChip = iconChip("◌", "Aguardando", warn)
        fixChip = iconChip("⌖", "Sem posição", dim)
        chips.addView(connectionChip)
        chips.addView(fixChip, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(8) })
        hero.addView(chips, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(12) })

        val stats = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(10), dp(9), dp(10), dp(9))
            background = rounded(surfaceAlt, 13)
        }
        fun stat(caption: String): Pair<LinearLayout, TextView> {
            val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            col.addView(text(caption, 10f, false, dim))
            val number = text("—", 12.5f, true)
            col.addView(number)
            return col to number
        }
        val (responseCol, responseValue) = stat("RESPOSTA")
        val (precisionCol, precisionValue) = stat("PRECISÃO")
        val (ageCol, ageValue) = stat("IDADE DO GPS")
        responseMetric = responseValue
        accuracyMetric = precisionValue
        ageMetric = ageValue
        stats.addView(responseCol, LinearLayout.LayoutParams(0, -2, 1f))
        stats.addView(precisionCol, LinearLayout.LayoutParams(0, -2, 1f))
        stats.addView(ageCol, LinearLayout.LayoutParams(0, -2, 1f))
        hero.addView(stats, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        content.addView(hero, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })

        map = RemoteMapView(this).apply {
            enableOnline(mapOnline)
            background = rounded(if (dark) Color.rgb(34, 46, 62) else Color.rgb(238, 244, 247), 22)
            clipToOutline = true
            contentDescription = "Mapa com posição remota; arraste para explorar, use dois dedos para ampliar"
        }
        val deviceHeightDp = resources.displayMetrics.heightPixels / resources.displayMetrics.density
        content.addView(map, LinearLayout.LayoutParams(-1, dp((deviceHeightDp * .53f).toInt().coerceIn(320, 530))).apply { topMargin = dp(12) })

        refresh = button("↻  Atualizar") { load(manual = true) }
        trackingButton = button("◎  Acompanhar") {
            tracking = !tracking
            if (tracking) {
                automatic = true
                map.setFollow(true)
                updateFollowLabel()
                if (!inFlight) load(manual = true)
            }
            updateTrackingLabel()
            updateAutomaticLabel()
            schedule()
        }
        val center = button("⌖  Centralizar") { map.centerOnMarker() }
        actions(content, refresh, trackingButton, center)

        followButton = button("") {
            map.setFollow(!map.isFollowing())
            updateFollowLabel()
        }
        map.onFollowChanged = { updateFollowLabel() }
        onlineButton = button("") { changeTilesPermission() }
        intervalButton = button("") { chooseInterval() }
        actions(content, followButton, onlineButton, intervalButton)
        updateFollowLabel()
        updateOnlineLabel()
        updateIntervalLabel()
        updateTrackingLabel()

        automaticButton = button("") {
            automatic = !automatic
            if (!automatic) tracking = false
            updateAutomaticLabel()
            updateTrackingLabel()
            schedule()
        }
        openMap = button("↗  Mapa externo") { openExternalMap() }.apply { isEnabled = false }
        copy = button("⧉  Copiar GPS") { copyCoordinates() }.apply { isEnabled = false }
        actions(content, automaticButton, openMap, copy)
        updateAutomaticLabel()

        content.addView(text("© OpenStreetMap contributors · blocos online opcionais e cache local. A localização só é mostrada quando o monitorado a informar.", 10.5f, false, subtle).apply {
            setPadding(dp(2), dp(8), dp(2), 0)
            setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.openstreetmap.org/copyright"))) }
        })

        if (host.isBlank() || RecentDevices.key(this, host) == null) {
            status.text = "Aparelho não pareado"
            quality.text = "Escolha um aparelho com chave salva"
            details.text = "Sem pareamento não há como consultar a posição de forma segura."
            refresh.isEnabled = false
            automaticButton.isEnabled = false
            trackingButton.isEnabled = false
        } else {
            load(manual = true)
        }
    }

    private fun updateFollowLabel() {
        followButton.text = if (map.isFollowing()) "⌖  Seguir: sim" else "⌖  Seguir: não"
    }

    private fun updateOnlineLabel() {
        onlineButton.text = if (mapOnline) "◉  Mapa ligado" else "◯  Mapa desligado"
    }

    private fun updateIntervalLabel() {
        val idx = RATES.indexOf(intervalMs).coerceAtLeast(0)
        intervalButton.text = "⏱  ${RATE_LABELS[idx]}"
    }

    private fun updateAutomaticLabel() {
        automaticButton.text = if (automatic) "⟳  Auto ligado" else "⟳  Auto desligado"
    }

    private fun updateTrackingLabel() {
        trackingButton.text = if (tracking) "◉  Acompanhando" else "◎  Acompanhar"
        trackingButton.setTextColor(if (tracking) accent else ink)
    }

    private fun refreshAges() {
        if (lastResponseAtMs > 0L) {
            val elapsed = (System.currentTimeMillis() - lastResponseAtMs).coerceAtLeast(0L) / 1000L
            responseMetric.text = if (elapsed < 60L) "há ${elapsed}s" else "há ${elapsed / 60L}min"
        }
        if (fixAgeAtResponseMs >= 0L) {
            val elapsed = (SystemClock.elapsedRealtime() - fixReceivedElapsedMs).coerceAtLeast(0L)
            val ageSeconds = LocationUiPolicy.elapsedAgeMs(fixAgeAtResponseMs, elapsed) / 1000L
            ageMetric.text = if (ageSeconds < 60L) "${ageSeconds}s" else "${ageSeconds / 60L}min"
            if (ageSeconds > 30L) setChip(fixChip, "⚠ Posição antiga", warn)
        }
    }

    private fun changeTilesPermission() {
        if (mapOnline) {
            mapOnline = false
            prefs.edit().putBoolean(tileConsentKey, false).apply()
            map.enableOnline(false)
            updateOnlineLabel()
        } else {
            themedPopup(this)
                .setTitle("Ativar mapa online?")
                .setMessage("O app poderá baixar somente os blocos visíveis do OpenStreetMap e manter cache local. Você pode desligar quando quiser.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Ativar") { _, _ ->
                    mapOnline = true
                    prefs.edit().putBoolean(tileConsentKey, true).apply()
                    map.enableOnline(true)
                    updateOnlineLabel()
                }.show()
        }
    }

    private fun chooseInterval() {
        themedPopup(this).setTitle("Intervalo de atualização")
            .setSingleChoiceItems(RATE_LABELS, RATES.indexOf(intervalMs).coerceAtLeast(0)) { dialog, idx ->
                intervalMs = RATES[idx]
                prefs.edit().putLong("refresh_interval_ms", intervalMs).apply()
                updateIntervalLabel()
                dialog.dismiss()
                schedule()
                if (automatic) load(manual = false)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun schedule() {
        main.removeCallbacks(ticker)
        if (visible) LocationUiPolicy.refreshDelayMs(tracking, automatic, intervalMs)?.let {
            main.postDelayed(ticker, it)
        }
    }

    private fun load(manual: Boolean) {
        val secret = RecentDevices.key(this, host) ?: return
        if (inFlight) return
        val visit = ++generation
        val start = SystemClock.elapsedRealtime()
        inFlight = true
        refresh.isEnabled = false
        pendingRetry = false
        quality.text = if (manual) "Solicitando posição agora…" else "Atualizando em segundo plano…"
        thread(name = "MonitorRemoto-location", isDaemon = true) {
            val response = runCatching { RemoteClient.locationSnapshot(host, secret, applicationContext, method) }
            val elapsed = SystemClock.elapsedRealtime() - start
            main.post {
                if (isFinishing || isDestroyed || generation != visit) return@post
                inFlight = false
                refresh.isEnabled = true
                lastRoundTripMs = elapsed
                response.onSuccess {
                    lastResponseAtMs = System.currentTimeMillis()
                    setChip(connectionChip, "✓ Conectado · ${elapsed} ms", accent)
                    renderStatus(it.optJSONObject("location"), manual)
                    refreshAges()
                }
                    .onFailure {
                        setChip(connectionChip, "✕ Sem resposta", danger)
                        setChip(fixChip, if (sample == null) "⌖ Sem posição" else "⚠ Posição anterior", warn)
                        status.text = "Sem conexão com o aparelho"
                        quality.text = "Falha de consulta · ${elapsed} ms"
                        details.text = if (sample == null) {
                            "Não foi possível receber uma posição nesta tentativa. Toque em Atualizar para tentar de novo imediatamente."
                        } else {
                            "A conexão falhou. O marcador exibido é apenas a última posição recebida nesta sessão e não representa localização ao vivo."
                        }
                    }
            }
        }
    }

    private fun clearSample() {
        sample = null
        fixAgeAtResponseMs = -1L
        accuracyMetric.text = "—"
        ageMetric.text = "—"
        map.clearPosition()
        openMap.isEnabled = false
        copy.isEnabled = false
    }

    private fun renderStatus(value: JSONObject?, manual: Boolean) {
        // Não apagar um fix válido por uma falha temporária de rede/GNSS.
        // Quando preservado, ele é marcado explicitamente como posição anterior.
        setChip(fixChip, if (sample != null) "⚠ Posição anterior" else "⌖ Sem posição",
            if (sample != null) warn else dim)
        val latencyLabel = if (lastRoundTripMs >= 0) " · ${lastRoundTripMs} ms" else ""
        if (value == null) {
            status.text = "Sem dados de localização"
            quality.text = "Resposta sem posição$latencyLabel"
            details.text = "O monitorado respondeu, mas não trouxe uma leitura válida. Confira se a localização e a recuperação automática estão ativas."
            return
        }
        when (value.optString("state")) {
            "desativada" -> {
                clearSample()
                setChip(fixChip, "⌖ GPS parado", dim)
                val armed = value.optBoolean("recoveryArmed", false)
                status.text = if (armed) "Recuperação armada · GPS parado" else "GPS ainda não configurado"
                quality.text = "Sem leitura atual$latencyLabel"
                details.text = if (armed) {
                    "O modo de recuperação está armado, mas a sessão de localização não está ativa. Verifique permissão em segundo plano e restrições de bateria."
                } else {
                    "Ative uma vez no monitorado em Configurações → Localização → Recuperação automática. Depois a consulta pode ser feita daqui."
                }
            }
            "sem_permissao" -> {
                clearSample()
                setChip(fixChip, "⌖ Sem permissão", danger)
                status.text = "Sem permissão de localização"
                quality.text = "Android bloqueou a leitura$latencyLabel"
                details.text = "Conceda a permissão de localização no aparelho monitorado."
            }
            "gps_desligado" -> {
                clearSample()
                setChip(fixChip, "⌖ GPS desligado", dim)
                status.text = "Localização desativada"
                quality.text = "GPS desligado$latencyLabel"
                details.text = "Ative a localização no monitorado para receber posições."
            }
            "aguardando" -> {
                status.text = "Aguardando uma posição"
                quality.text = "Sessão ativa, sem coordenada ainda$latencyLabel"
                details.text = "O monitorado está tentando obter a posição. Se você acabou de solicitar, aguarde alguns segundos e atualize novamente."
                if (manual && !pendingRetry) scheduleQuickRetry()
            }
            "expirada" -> {
                status.text = "Leitura expirada"
                quality.text = "Posição antiga demais$latencyLabel"
                details.text = "A última leitura remota expirou. Uma nova consulta foi feita, mas ainda não chegou um ponto recente."
                if (manual && !pendingRetry) scheduleQuickRetry()
            }
            "disponivel", "antiga" -> {
                val lat = value.optDouble("latitude", Double.NaN)
                val lon = value.optDouble("longitude", Double.NaN)
                val age = value.optLong("ageMs", -1L)
                if (!LocationSharePolicy.coordinatesValid(lat, lon) || !LocationSharePolicy.usableAgeMs(age)) {
                    status.text = "Leitura inválida"
                    quality.text = "Posição inconsistente$latencyLabel"
                    details.text = "O aparelho informou uma localização inconsistente nesta consulta."
                    return
                }
                val accuracy = if (value.isNull("accuracyM")) null else value.optDouble("accuracyM").takeIf { it.isFinite() && it >= 0.0 }
                sample = lat to lon
                fixAgeAtResponseMs = age
                fixReceivedElapsedMs = SystemClock.elapsedRealtime()
                accuracyMetric.text = accuracy?.let { "±${it.toInt()}m" } ?: "—"
                map.setPosition(lat, lon, accuracy)
                openMap.isEnabled = true
                copy.isEnabled = true
                val state = value.optString("state")
                val ageSec = (age / 1000L).coerceAtLeast(0L)
                val time = value.optLong("timestampMs", 0L).takeIf { it > 0L }
                    ?.let { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(Date(it)) } ?: "não informada"
                val ageLabel = if (ageSec <= 15) "agora" else "há ${ageSec}s"
                val fresh = LocationUiPolicy.gpsIsRecent(state, age)
                setChip(fixChip, if (fresh) "✓ GPS recente" else "⚠ Posição antiga",
                    if (fresh) accent else warn)
                status.text = if (state == "antiga") "Última posição recebida" else "Posição recebida"
                quality.text = buildString {
                    append(if (state == "antiga") "Leitura antiga" else "Leitura atual")
                    append(" · ")
                    append(ageLabel)
                    accuracy?.let { append(" · ±${it.toInt()} m") }
                    append(latencyLabel)
                }
                details.text = buildString {
                    append(String.format(Locale.US, "%.6f, %.6f", lat, lon))
                    append("\n")
                    append(value.optString("provider", "desconhecida").replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() })
                    accuracy?.let { append(" · precisão ±${it.toInt()} m") }
                    append("\n")
                    append("Atualizado em $time · perfil ${LocationSharePolicy.label(value.optString("profile"))}")
                }
                titleLine.text = "${RecentDevices.list(this).firstOrNull { it.host == host }?.name ?: "Aparelho remoto"} · ${if (state == "antiga") "posição antiga" else "posição recebida"}"
                if (manual && state == "antiga" && ageSec > 15 && !pendingRetry) scheduleQuickRetry()
            }
            else -> {
                status.text = "Estado indisponível"
                quality.text = "Sem leitura válida$latencyLabel"
                details.text = "O monitorado não informou uma posição reconhecida."
            }
        }
    }

    private fun scheduleQuickRetry() {
        pendingRetry = true
        main.postDelayed({ if (visible && !inFlight) load(manual = false) }, 1800L)
    }

    private fun openExternalMap() {
        val (lat, lon) = sample ?: return
        val coords = String.format(Locale.US, "%.6f,%.6f", lat, lon)
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:$coords?q=$coords")))
        } catch (_: ActivityNotFoundException) {
            status.text = "Nenhum aplicativo de mapas instalado"
        } catch (_: SecurityException) {
            status.text = "Abertura bloqueada pelo Android"
        }
    }

    private fun copyCoordinates() {
        val (lat, lon) = sample ?: return
        val coords = String.format(Locale.US, "%.6f, %.6f", lat, lon)
        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText("Localização remota", coords))
        quality.text = "Coordenadas copiadas"
    }

    override fun onResume() {
        super.onResume()
        visible = true
        schedule()
        main.removeCallbacks(statusTicker)
        main.post(statusTicker)
        if (!inFlight && host.isNotBlank()) load(manual = true)
    }

    override fun onPause() {
        visible = false
        main.removeCallbacks(ticker)
        main.removeCallbacks(statusTicker)
        super.onPause()
    }

    override fun onDestroy() {
        generation++
        main.removeCallbacksAndMessages(null)
        map.dispose()
        super.onDestroy()
    }
}
