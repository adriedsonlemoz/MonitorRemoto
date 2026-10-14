package br.dev.monitorremoto.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.text.InputType
import android.text.format.DateFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import br.dev.monitorremoto.adb.AdbClient
import br.dev.monitorremoto.adb.AdbAutoConnectPolicy
import br.dev.monitorremoto.adb.AdbDiscovery
import br.dev.monitorremoto.adb.AdbIdentity
import br.dev.monitorremoto.adb.AdbKnownDevice
import br.dev.monitorremoto.adb.AdbKnownDevices
import br.dev.monitorremoto.adb.AdbPolicy
import br.dev.monitorremoto.core.SettingsStore
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import java.util.Calendar
import java.util.concurrent.Executors

/** ADB consentido, sempre visível; QR -> pareamento -> descoberta -> conexão TLS. */
class AdbActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private var client: AdbClient? = null
    private var discovery: AdbDiscovery? = null
    private var qr: AdbPolicy.QrSession? = null
    private var dialog: AlertDialog? = null
    private var qrImage: ImageView? = null
    private var modalStatus: TextView? = null
    private var generation = 0
    private var discoveryGeneration = 0
    private var busy = false
    private var visible = false
    private var pairingHost: String? = null
    private var reportedPhone = ""
    private var advancedVisible = false
    private var discoveryHost: String? = null
    private var reconnectTarget: AdbKnownDevice? = null
    private var discoveryPending = false
    private val endpoints = linkedMapOf<String, AdbDiscovery.Endpoint>()
    private lateinit var root: LinearLayout
    private lateinit var chip: TextView
    private lateinit var summary: TextView
    private lateinit var detail: TextView
    private lateinit var feedback: TextView
    private lateinit var savedContainer: LinearLayout
    private lateinit var advancedContainer: LinearLayout
    private lateinit var advancedToggle: Button

    private val dark get() = when (SettingsStore.theme(this)) {
        "dark" -> true
        "system" -> resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        else -> false
    }
    private val fg get() = if (dark) Color.WHITE else Color.rgb(24, 31, 40)
    private val muted get() = if (dark) Color.rgb(183, 193, 206) else Color.rgb(101, 112, 125)
    private val bg get() = if (dark) Color.rgb(15, 22, 32) else Color.rgb(249, 250, 252)
    private val surface get() = if (dark) Color.rgb(30, 40, 54) else Color.WHITE
    private val soft get() = if (dark) Color.rgb(42, 52, 65) else Color.rgb(243, 245, 248)
    private val stroke get() = if (dark) Color.rgb(67, 81, 98) else Color.rgb(226, 230, 236)
    private val success get() = if (dark) Color.rgb(135, 230, 180) else Color.rgb(21, 135, 75)
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun shape(color: Int, radius: Int = 20, line: Boolean = false) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
        if (line) setStroke(dp(1), stroke)
    }
    private fun text(value: String, sp: Float = 15f, secondary: Boolean = false, bold: Boolean = false) =
        TextView(this).apply {
            text = value
            textSize = sp
            setTextColor(if (secondary) muted else fg)
            if (bold) typeface = Typeface.DEFAULT_BOLD
            setLineSpacing(dp(2).toFloat(), 1.02f)
            setPadding(0, dp(3), 0, dp(3))
        }
    private fun button(label: String, primary: Boolean = false, outline: Boolean = false, act: () -> Unit) =
        Button(this).apply {
            text = label
            isAllCaps = false
            minHeight = dp(50)
            elevation = 0f
            background = shape(if (primary) Color.rgb(19, 21, 25) else if (outline) surface else soft, 17, outline)
            setTextColor(if (primary) Color.WHITE else fg)
            setOnClickListener { if (!busy) act() }
        }
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun card(container: LinearLayout, fill: Int = surface) = column().apply {
        setPadding(dp(18), dp(18), dp(18), dp(18))
        background = shape(fill, 22, fill == surface)
        container.addView(this, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
    }
    private fun formField(hintValue: String, numeric: Boolean = false, pin: Boolean = false) =
        EditText(this).apply {
            hint = hintValue
            setSingleLine()
            isSaveEnabled = false
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
            inputType = if (pin) InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD else
                if (numeric) InputType.TYPE_CLASS_NUMBER else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setTextColor(fg)
            setHintTextColor(muted)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = shape(soft, 13, true)
            layoutParams = LinearLayout.LayoutParams(-1, dp(52)).apply { bottomMargin = dp(9) }
        }
    private fun lastSeen(ms: Long): String {
        if (ms <= 0) return "não informado"
        val now = Calendar.getInstance()
        val then = Calendar.getInstance().apply { timeInMillis = ms }
        val date = DateFormat.format("HH:mm", ms).toString()
        return when {
            now.get(Calendar.YEAR) == then.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR) -> "hoje, $date"
            now.get(Calendar.YEAR) == then.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) - then.get(Calendar.DAY_OF_YEAR) == 1 -> "ontem, $date"
            else -> DateFormat.format("dd/MM, HH:mm", ms).toString()
        }
    }

    override fun onCreate(state: Bundle?) {
        setTheme(if (dark) android.R.style.Theme_Material_NoActionBar else android.R.style.Theme_Material_Light_NoActionBar)
        super.onCreate(state)
        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(bg)
            setOnApplyWindowInsetsListener { v, ins ->
                v.setPadding(ins.systemWindowInsetLeft, ins.systemWindowInsetTop, ins.systemWindowInsetRight, ins.systemWindowInsetBottom)
                ins
            }
        }
        root = column().apply { setPadding(dp(16), dp(12), dp(16), dp(24)) }
        scroll.addView(root)
        setContentView(scroll)
        scroll.requestApplyInsets()
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            val flags = android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            window.insetsController?.setSystemBarsAppearance(if (dark) 0 else flags, flags)
        } else window.decorView.systemUiVisibility = if (dark) 0 else
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        root.addView(button("‹ Configurações", outline = true) { finish() })
        root.addView(text("Monitor Remoto", 28f, bold = true))
        root.addView(text("Dispositivos ADB", 14f, secondary = true))

        val summaryCard = card(root)
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val label = text("Estado da conexão", 15f, bold = true)
        header.addView(label, LinearLayout.LayoutParams(0, -2, 1f))
        chip = text("Desconectado", 13f, bold = true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(7), dp(12), dp(7))
        }
        header.addView(chip)
        summaryCard.addView(header)
        summary = text("Nenhum aparelho conectado", 19f, bold = true)
        detail = text("Adicione um aparelho ou reconecte um salvo.", 13f, secondary = true)
        summaryCard.addView(summary)
        summaryCard.addView(detail)

        root.addView(text("Conectados anteriormente", 20f, bold = true))
        savedContainer = column()
        root.addView(savedContainer)
        root.addView(button("+ Adicionar outro celular", outline = true) { chooseRole() })
        advancedToggle = button("Configurações avançadas  ▾") { toggleAdvanced() }
        root.addView(advancedToggle)
        advancedContainer = column().apply { visibility = View.GONE }
        val advCard = card(advancedContainer)
        advCard.addView(button("Conectar por IP e porta") { manualConnect() })
        advCard.addView(button("Controles ADB") { showControls() })
        advCard.addView(button("Apagar credencial ADB local", outline = true) { forgetIdentity() })
        root.addView(advancedContainer)
        feedback = text("A conexão ADB é experimental. O espelhamento de tela usa o modo normal do aplicativo.", 12f, secondary = true)
        root.addView(feedback)
        render()
    }

    private fun setFeedback(message: String) {
        if (this::feedback.isInitialized) feedback.text = message
        modalStatus?.text = message
    }
    private fun render() {
        if (!this::savedContainer.isInitialized) return
        val connected = client?.connected() == true
        chip.text = if (connected) "Conectado" else "Desconectado"
        chip.setTextColor(if (connected) success else muted)
        chip.background = shape(if (connected) {
            if (dark) Color.rgb(36, 75, 56) else Color.rgb(227, 247, 235)
        } else soft, 30)
        summary.text = if (connected) reportedPhone.ifBlank { "Aparelho conectado" } else "Pronto para conectar"
        detail.text = if (connected) "Conexão ADB ativa • ${client?.endpoint.orEmpty()}" else "Escolha um dispositivo salvo para reconectar."
        savedContainer.removeAllViews()
        val known = AdbKnownDevices.load(this)
        if (known.isEmpty()) {
            val empty = card(savedContainer, soft)
            empty.addView(text("Nenhum celular salvo", 17f, bold = true))
            empty.addView(text("Após a primeira conexão ADB confirmada, o aparelho aparecerá aqui.", 13f, secondary = true))
        }
        known.forEach { device ->
            val tile = card(savedContainer, soft)
            tile.addView(text(device.name, 21f, bold = true))
            tile.addView(text("Última conexão: ${lastSeen(device.lastConnectedAt)}", 13f, secondary = true))
            val active = connected && client?.endpoint == "${device.host}:${device.port}"
            tile.addView(text(if (active) "● Conectado agora" else "⌁ Aguardando reconexão", 13f, secondary = true))
            tile.addView(button(if (active) "Desconectar" else "Reconectar", primary = true) {
                if (active) disconnect() else reconnect(device)
            })
            tile.addView(button("Opções do aparelho") { deviceOptions(device) })
        }
    }
    private fun toggleAdvanced() {
        advancedVisible = !advancedVisible
        advancedContainer.visibility = if (advancedVisible) View.VISIBLE else View.GONE
        advancedToggle.text = if (advancedVisible) "Ocultar configurações avançadas  ▴" else "Configurações avançadas  ▾"
    }

    private fun modal(title: String, body: LinearLayout, cancel: Boolean = true): AlertDialog {
        closeDialog()
        val content = ScrollView(this).apply { addView(body); isFillViewport = false }
        val result = AlertDialog.Builder(this).setTitle(title).setView(content)
            .apply {
                if (cancel) setNegativeButton("Fechar") { _, _ ->
                    clearQr(); stopDiscovery(); if (busy) cancelWork()
                }
            }.create()
        dialog = result
        result.setOnDismissListener {
            if (dialog === result) {
                dialog = null
                modalStatus = null
                if (qr != null) { clearQr(); stopDiscovery() }
            }
        }
        result.show()
        result.setCanceledOnTouchOutside(false)
        result.window?.setLayout((resources.displayMetrics.widthPixels * 0.92f).toInt(), -2)
        return result
    }
    private fun modalBody() = column().apply { setPadding(dp(20), dp(8), dp(20), dp(16)) }
    private fun closeDialog() { dialog?.dismiss(); dialog = null; modalStatus = null }
    private fun chooseRole() {
        val body = modalBody()
        body.addView(text("Qual será a função deste celular?", 16f, bold = true))
        body.addView(button("Vou controlar outro celular", primary = true) { chooseMethod() })
        body.addView(button("Vou permitir controle deste celular") { monitoredGuide(0) })
        modal("Novo dispositivo", body)
    }
    private fun chooseMethod() {
        val body = modalBody()
        body.addView(text("Os dois aparelhos precisam estar no mesmo Wi-Fi. No outro, ative a Depuração sem fios.", 14f, secondary = true))
        body.addView(button("QR Code  •  Recomendado", primary = true) { qrDialog() })
        body.addView(button("Código de seis dígitos") { codeDialog() })
        modal("Como deseja parear?", body)
    }
    private fun monitoredGuide(step: Int) {
        val titles = listOf("Abrir Opções do desenvolvedor", "Ativar Depuração sem fios", "Fazer o pareamento", "Conferir a conexão")
        val desc = listOf(
            "Abra as configurações deste celular e encontre Opções do desenvolvedor. Se não aparecer, procure Sobre o telefone e toque sete vezes no número da versão.",
            "Ative Depuração sem fios neste celular. Confirme que os dois aparelhos estão na mesma rede Wi-Fi.",
            "No outro celular, abra Monitor Remoto → Adicionar outro celular → Vou controlar. Escolha QR ou código. Use ESTE celular para escanear o QR exibido no controlador.",
            "Depois do pareamento, confirme no controlador que apareceu Conectado. Neste celular não é possível garantir por esta tela que um cliente ADB remoto está ativo."
        )
        val body = modalBody()
        body.addView(text("PASSO ${step + 1} DE ${titles.size}", 12f, secondary = true, bold = true))
        body.addView(text(titles[step], 19f, bold = true))
        body.addView(text(desc[step], 14f, secondary = true))
        if (step == 0) body.addView(button("Abrir configurações do Android", primary = true) { openSettings(false) })
        if (step == 0 || step == 1) body.addView(button("Abrir Opções do desenvolvedor") { openSettings(true) })
        if (step > 0) body.addView(button("‹ Voltar") { monitoredGuide(step - 1) })
        if (step < titles.lastIndex) body.addView(button("Continuar ›", primary = true) { monitoredGuide(step + 1) })
        else body.addView(button("Concluir", primary = true) { closeDialog() })
        modal("Permitir controle", body)
    }
    private fun openSettings(developer: Boolean) {
        val action = if (developer) Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS else Settings.ACTION_SETTINGS
        try { startActivity(Intent(action)) }
        catch (_: Exception) {
            runCatching { startActivity(Intent(Settings.ACTION_SETTINGS)) }
                .onFailure { setFeedback("Não foi possível abrir as Configurações.") }
        }
    }
    private fun qrDialog() {
        clearQr()
        val session = AdbPolicy.newQr(SystemClock.elapsedRealtime())
        qr = session
        val body = modalBody()
        body.addView(text("No telefone monitorado: Depuração sem fios → Emparelhar com QR. Aponte a câmera DELE para este código.", 14f, secondary = true))
        val img = ImageView(this).apply { contentDescription = "QR ADB temporário de pareamento" }
        qrImage = img
        try {
            val matrix = MultiFormatWriter().encode(session.payload(), BarcodeFormat.QR_CODE, 512, 512)
            val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
            bitmap.setPixels(IntArray(512 * 512) { i -> if (matrix[i % 512, i / 512]) Color.BLACK else Color.WHITE }, 0, 512, 0, 0, 512, 512)
            img.setImageBitmap(bitmap)
        } catch (_: Exception) {
            clearQr(); setFeedback("Não foi possível gerar QR. Tente por código."); chooseMethod(); return
        }
        body.addView(img, LinearLayout.LayoutParams(-1, dp(258)))
        val info = text("Aguardando leitura do QR…", 14f, secondary = true)
        body.addView(info)
        modal("Parear com QR Code", body)
        qr = session // modal fecha diálogo anterior antes da exibição
        modalStatus = info
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        beginDiscovery("qr")
        handler.postDelayed({
            if (qr === session && session.valid(SystemClock.elapsedRealtime()).not()) {
                clearQr(); stopDiscovery(); info.text = "QR expirado. Feche e gere outro código."
            }
        }, 120_100)
    }
    private fun codeDialog() {
        clearQr(); stopDiscovery()
        val body = modalBody()
        body.addView(text("No monitorado, abra Depuração sem fios → Emparelhar dispositivo com código. Copie IP, PORTA DE PAREAMENTO e os seis dígitos.", 14f, secondary = true))
        val host = formField("IP do monitorado")
        val port = formField("Porta de pareamento", numeric = true)
        val code = formField("Código de 6 dígitos", pin = true)
        body.addView(host); body.addView(port); body.addView(code)
        val info = text("A conexão depois do código será localizada automaticamente quando possível.", 13f, secondary = true)
        body.addView(info)
        body.addView(button("Parear", primary = true) {
            try {
                val endpoint = AdbPolicy.endpoint(host.text.toString().trim(), port.text.toString().trim())
                val pass = AdbPolicy.code(code.text.toString().trim())
                code.text.clear()
                runOperation("Pareando por código…", { it.pair(endpoint.first, endpoint.second, pass); "Pareado" }, {
                    afterPair(endpoint.first)
                })
            } catch (e: IllegalArgumentException) { info.text = e.message ?: "Dados inválidos" }
        })
        modal("Parear por código", body)
        modalStatus = info
    }
    private fun afterPair(host: String) {
        pairingHost = host
        clearQr()
        val body = modalBody()
        val info = text("Pareado! Procurando a porta de conexão ADB no mesmo celular…", 15f)
        body.addView(info)
        body.addView(button("Informar IP/porta manualmente") { manualConnect(host) })
        modal("Conectando automaticamente", body)
        modalStatus = info
        val cached = connectCandidates(host)
        if (cached.size == 1) connectEndpoint(cached[0].host, cached[0].port, null, true)
        else if (cached.size > 1) manualFallback("Há mais de uma porta ADB nesse endereço. Confira a porta no Android.", host)
        else beginDiscovery("connect", host)
    }
    private fun connectCandidates(host: String) = AdbAutoConnectPolicy.connectionServices(host, endpoints.values.toList())
    private fun beginDiscovery(mode: String, host: String? = null) {
        stopDiscovery()
        endpoints.clear()
        discoveryHost = host
        discoveryPending = mode == "connect" || mode == "reconnect"
        val sequence = discoveryGeneration
        discovery = AdbDiscovery(this, found@{ endpoint ->
            if (!visible || sequence != discoveryGeneration) return@found
            endpoints["${endpoint.type}/${endpoint.name}/${endpoint.host}:${endpoint.port}"] = endpoint
            val currentQr = qr
            if (mode == "qr" && currentQr != null && currentQr.valid(SystemClock.elapsedRealtime()) &&
                endpoint.type.contains("_adb-tls-pairing") && endpoint.name == currentQr.service && !busy) {
                // O host foi obtido do serviço temporário cujo nome está no QR; não é um IP escolhido ao acaso.
                runOperation("Pareando pelo QR…", { it.pair(endpoint.host, endpoint.port, currentQr.password); "Pareado" }, {
                    afterPair(endpoint.host)
                })
            }
            if (discoveryPending && (mode == "connect" || mode == "reconnect") &&
                endpoint.type.contains("_adb-tls-connect") && endpoint.host == host && !busy) {
                // Aguarde outros anúncios do mesmo host antes de escolher uma porta.
                handler.postDelayed({
                    if (visible && sequence == discoveryGeneration && discoveryPending && !busy) {
                        val possible = connectCandidates(host)
                        if (possible.size == 1) {
                            discoveryPending = false
                            connectEndpoint(possible[0].host, possible[0].port,
                                if (mode == "reconnect") reconnectTarget else null, true)
                        } else if (possible.size > 1) {
                            discoveryPending = false
                            manualFallback("Mais de uma porta ADB encontrada no mesmo IP. Confira no aparelho.", host)
                        }
                    }
                }, 900)
            }
        }, { problem -> if (visible) setFeedback(problem) }).also { it.start() }
        handler.postDelayed({
            if (sequence == discoveryGeneration && visible && discoveryPending) {
                discoveryPending = false
                manualFallback("Não foi possível localizar automaticamente a porta ADB. Confira a Depuração sem fios no monitorado.", host)
            }
        }, 16000)
    }
    private fun stopDiscovery() {
        discoveryGeneration++
        discoveryPending = false
        discovery?.stop()
        discovery = null
    }
    private fun manualFallback(message: String, host: String?) {
        if (!visible) return
        setFeedback(message)
        val body = modalBody()
        body.addView(text(message, 14f, secondary = true))
        body.addView(button("Informar porta manualmente", primary = true) { manualConnect(host) })
        body.addView(button("Tentar buscar novamente") { beginDiscovery("connect", host) })
        modal("Conexão não localizada", body)
    }
    private fun manualConnect(prefill: String? = null) {
        stopDiscovery(); clearQr()
        val body = modalBody()
        body.addView(text("Use o IP e a porta de CONEXÃO mostrados na tela principal de Depuração sem fios, não a porta de pareamento.", 14f, secondary = true))
        val host = formField("IP do monitorado")
        if (prefill != null) host.setText(prefill)
        val port = formField("Porta de conexão ADB", numeric = true)
        body.addView(host); body.addView(port)
        val info = text("A conexão só será confirmada após resposta ADB real.", 13f, secondary = true)
        body.addView(info)
        body.addView(button("Conectar", primary = true) {
            try {
                val endpoint = AdbPolicy.endpoint(host.text.toString().trim(), port.text.toString().trim())
                connectEndpoint(endpoint.first, endpoint.second)
            } catch (e: IllegalArgumentException) { info.text = e.message ?: "Dados inválidos" }
        })
        modal("Conectar manualmente", body)
        modalStatus = info
    }
    private fun reconnect(device: AdbKnownDevice) {
        val body = modalBody()
        val info = text("Tentando conectar a ${device.name}…", 15f)
        body.addView(info)
        body.addView(button("Conectar manualmente") { manualConnect(device.host) })
        modal("Reconectar", body)
        modalStatus = info
        connectEndpoint(device.host, device.port, device, false)
    }
    private fun connectEndpoint(host: String, port: Int, expected: AdbKnownDevice? = null, discovered: Boolean = false) {
        if (busy) return
        runOperation("Conectando a $host:$port…", { adb ->
            adb.connect(host, port)
            runCatching { adb.execute(AdbPolicy.Command.DIAGNOSTIC) }.getOrDefault("")
        }, { response ->
            if (client?.connected() != true) { setFeedback("ADB desconectou antes da confirmação."); return@runOperation }
            val lines = response.lines().map { it.trim().take(80) }
            val model = lines.getOrNull(0).orEmpty().ifBlank { "Aparelho em $host" }
            val commercial = listOf(lines.getOrNull(2), lines.getOrNull(3))
                .firstOrNull { !it.isNullOrBlank() }?.trim().orEmpty()
            val displayModel = commercial.ifBlank { model }
            val android = lines.getOrNull(1).orEmpty()
            val expectedModel = expected?.model?.ifBlank { expected.name }.orEmpty()
            if (expected != null && expectedModel.isNotBlank() &&
                !expectedModel.equals(model, ignoreCase = true) &&
                !expectedModel.equals(displayModel, ignoreCase = true) &&
                !expectedModel.startsWith("Aparelho em ")) {
                AlertDialog.Builder(this).setTitle("Confirmar aparelho")
                    .setMessage("O aparelho respondeu como $displayModel, mas o salvo é ${expected.name}. Confirme no telefone antes de substituir os dados salvos.")
                    .setNegativeButton("Desconectar") { _, _ -> disconnect() }
                    .setPositiveButton("É o mesmo aparelho") { _, _ -> finishConnected(displayModel, android, host, port, expected) }
                    .show()
            } else finishConnected(displayModel, android, host, port, expected)
        }, { failure ->
            if (expected != null && !discovered) {
                setFeedback("A porta salva não respondeu. Procurando a porta atual em ${expected.host}…")
                reconnectTarget = expected
                beginDiscovery("reconnect", expected.host)
            } else manualFallback("Falha ao conectar: $failure. Confira a porta de conexão atual.", host)
        })
    }
    private fun finishConnected(model: String, android: String, host: String, port: Int, expected: AdbKnownDevice? = null) {
        if (client?.connected() != true) return
        reportedPhone = model
        val label = if (expected != null && expected.name.isNotBlank() &&
            expected.model.isNotBlank() && expected.name != expected.model) expected.name else model
        if (expected != null && (expected.host != host || expected.port != port)) {
            AdbKnownDevices.forget(this, expected)
        }
        AdbKnownDevices.remember(this, AdbKnownDevice(label, host, port, System.currentTimeMillis(), android, model))
        clearQr(); stopDiscovery(); closeDialog()
        render()
        setFeedback("Conectado a $model. A identificação foi informada pelo Android do aparelho.")
    }
    private fun showControls() {
        if (client?.connected() != true) { setFeedback("Conecte um aparelho antes de usar os comandos ADB."); return }
        val body = modalBody()
        body.addView(text("${reportedPhone.ifBlank { "Aparelho conectado" }} • ${client?.endpoint.orEmpty()}", 14f, secondary = true))
        listOf("Voltar" to AdbPolicy.Command.BACK, "Início" to AdbPolicy.Command.HOME,
            "Recentes" to AdbPolicy.Command.RECENTS).forEach { (label, command) ->
            body.addView(button(label) {
                runOperation("Enviando $label…", { it.execute(command); "Comando enviado. Confira o resultado no aparelho." })
            })
        }
        body.addView(button("Desconectar", outline = true) { disconnect(); closeDialog() })
        modal("Controles ADB", body)
    }
    private fun deviceOptions(device: AdbKnownDevice) {
        val body = modalBody()
        body.addView(text("${device.host}:${device.port} • última conexão ${lastSeen(device.lastConnectedAt)}", 13f, secondary = true))
        body.addView(button("Renomear") {
            val field = formField("Nome do aparelho").apply { setText(device.name) }
            AlertDialog.Builder(this).setTitle("Renomear aparelho").setView(field)
                .setNegativeButton("Cancelar", null).setPositiveButton("Salvar") { _, _ ->
                    val name = field.text.toString().trim().take(60)
                    if (name.isNotBlank()) {
                        AdbKnownDevices.forget(this, device)
                        AdbKnownDevices.remember(this, device.copy(name = name))
                        render()
                    }
                }.show()
        })
        body.addView(button("Esquecer da lista", outline = true) {
            AlertDialog.Builder(this).setTitle("Esquecer ${device.name}?")
                .setMessage("Isso remove o histórico local, mas não revoga a autorização ADB no outro celular.")
                .setNegativeButton("Cancelar", null).setPositiveButton("Esquecer") { _, _ ->
                    AdbKnownDevices.forget(this, device); closeDialog(); render()
                }.show()
        })
        modal("Opções do aparelho", body)
    }
    private fun forgetIdentity() {
        AlertDialog.Builder(this).setTitle("Apagar credencial ADB?")
            .setMessage("Os aparelhos terão de ser pareados novamente. A revogação definitiva deve ser feita no Android do celular monitorado.")
            .setNegativeButton("Cancelar", null).setPositiveButton("Apagar") { _, _ ->
                disconnect()
                worker.execute { AdbIdentity.forget(applicationContext) }
                setFeedback("A identidade ADB local foi marcada para exclusão.")
            }.show()
    }
    private fun clearQr() {
        qr = null
        qrImage?.setImageDrawable(null)
        qrImage = null
        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
    private fun runOperation(
        label: String,
        operation: (AdbClient) -> String,
        onSuccess: ((String) -> Unit)? = null,
        onFailure: ((String) -> Unit)? = null
    ) {
        if (busy || !visible) return
        busy = true
        setFeedback(label)
        val token = ++generation
        val active = client ?: AdbClient(applicationContext).also { client = it }
        val timeout = Runnable {
            if (visible && token == generation && busy) {
                cancelWork()
                val message = "Tempo esgotado. Confira a rede Wi-Fi e a autorização."
                setFeedback(message)
                onFailure?.invoke(message)
            }
        }
        handler.postDelayed(timeout, 35000)
        worker.execute {
            var message = ""
            var succeeded = false
            try { message = operation(active); succeeded = true }
            catch (e: Exception) { message = "${e.javaClass.simpleName}: ${e.message?.take(100).orEmpty()}" }
            catch (e: LinkageError) { message = "Biblioteca ADB indisponível: ${e.javaClass.simpleName}" }
            handler.post {
                handler.removeCallbacks(timeout)
                if (visible && token == generation) {
                    busy = false
                    setFeedback(message)
                    // Feche primeiro o cliente de pareamento antes de iniciar a próxima sessão.
                    if (!active.connected() && client === active) { active.close(); client = null }
                    if (succeeded) onSuccess?.invoke(message) else onFailure?.invoke(message)
                    render()
                } else active.close()
            }
        }
    }
    private fun cancelWork() {
        generation++
        busy = false
        val old = client
        client = null
        old?.requestCancel()
        if (old != null) Thread { old.close() }.start()
    }
    private fun disconnect() {
        clearQr(); stopDiscovery(); cancelWork()
        reportedPhone = ""
        setFeedback("ADB desconectado. A autorização de pareamento pode continuar no outro celular.")
        render()
    }
    private val watcher = object : Runnable {
        override fun run() {
            if (!visible) return
            val active = client
            if (active != null && !busy && !active.connected()) {
                client = null
                Thread { active.close() }.start()
                reportedPhone = ""
                setFeedback("A conexão ADB foi encerrada. Use Reconectar para tentar novamente.")
            }
            render()
            handler.postDelayed(this, 2000)
        }
    }
    override fun onStart() {
        super.onStart()
        visible = true
        handler.removeCallbacks(watcher)
        handler.post(watcher)
    }
    override fun onStop() {
        visible = false
        handler.removeCallbacks(watcher)
        clearQr(); stopDiscovery(); cancelWork()
        super.onStop()
    }
    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        worker.shutdownNow()
        super.onDestroy()
    }
}
