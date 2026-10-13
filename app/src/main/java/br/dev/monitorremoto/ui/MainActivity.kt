package br.dev.monitorremoto.ui

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.WindowManager
import android.widget.ImageView
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import br.dev.monitorremoto.capture.NotificationCaptureService
import br.dev.monitorremoto.core.BatteryReader
import br.dev.monitorremoto.core.SettingsStore
import br.dev.monitorremoto.core.SensitiveContent
import br.dev.monitorremoto.security.CryptoBox
import br.dev.monitorremoto.security.SecretVault
import br.dev.monitorremoto.service.MonitorService
import br.dev.monitorremoto.transport.RemoteClient
import br.dev.monitorremoto.transport.PairingCode
import br.dev.monitorremoto.transport.PairingSession
import br.dev.monitorremoto.transport.WireProtocol
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.google.zxing.integration.android.IntentIntegrator
import org.json.JSONObject
import java.net.Inet4Address
import java.net.NetworkInterface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

/** Interface Android Views sem dependências adicionais; ambos os modos no mesmo APK. */
class MainActivity : Activity() {
    private val bg = Color.rgb(14, 22, 35)
    private val surface = Color.rgb(28, 41, 58)
    private val ink = Color.rgb(233, 239, 247)
    private val dim = Color.rgb(167, 184, 203)
    private val highlight = Color.rgb(90, 214, 188)
    private lateinit var content: LinearLayout
    private val handler = Handler(Looper.getMainLooper())
    private var mode = 0 // 0: monitorado, 1: controle
    private var active = false
    private var fetching = false
    private var resultBox: LinearLayout? = null
    private var errorBox: TextView? = null
    private var pairingInProgress = false
    private val poller = object : Runnable {
        override fun run() {
            if (active && mode == 1) {
                refreshRemote(silent = true)
                handler.postDelayed(this, 15_000L)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        window.decorView.systemUiVisibility = 0
        val scroll = ScrollView(this).apply { setBackgroundColor(bg); isFillViewport = true }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(24), dp(18), dp(32))
        }
        scroll.addView(content)
        setContentView(scroll)
        render()
    }

    override fun onResume() {
        super.onResume()
        active = true
        render()
        handler.removeCallbacks(poller)
        handler.postDelayed(poller, 15_000L)
    }

    override fun onPause() {
        active = false
        handler.removeCallbacks(poller)
        super.onPause()
    }

    private fun dp(value: Int) = (resources.displayMetrics.density * value).toInt()
    private fun shape(color: Int, corner: Int = 13) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(corner).toFloat()
    }

    private fun label(value: String, size: Float = 15f, strong: Boolean = false, muted: Boolean = false): TextView =
        TextView(this).apply {
            setText(value)
            textSize = size
            setTextColor(if (muted) dim else ink)
            if (strong) setTypeface(null, Typeface.BOLD)
            setPadding(0, dp(5), 0, dp(5))
        }

    private fun addText(value: String, size: Float = 15f, strong: Boolean = false, muted: Boolean = false): TextView =
        label(value, size, strong, muted).also { content.addView(it) }

    private fun gap(px: Int = 10) { content.addView(View(this), LinearLayout.LayoutParams(1, dp(px))) }

    private fun button(title: String, emphasis: Boolean = false, onClick: () -> Unit): Button =
        Button(this).apply {
            text = title
            isAllCaps = false
            textSize = 14f
            setTextColor(if (emphasis) bg else ink)
            background = shape(if (emphasis) highlight else surface)
            setPadding(dp(8), dp(8), dp(8), dp(8))
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52))
            params.bottomMargin = dp(8)
            layoutParams = params
            setOnClickListener { onClick() }
        }

    private fun field(hintText: String, initial: String = ""): EditText = EditText(this).apply {
        hint = hintText
        setText(initial)
        textSize = 15f
        setTextColor(ink)
        setHintTextColor(dim)
        background = shape(surface)
        setSingleLine(true)
        setPadding(dp(14), 0, dp(14), 0)
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        val params = LinearLayout.LayoutParams(-1, dp(52))
        params.bottomMargin = dp(12)
        layoutParams = params
    }

    private fun render() {
        content.removeAllViews()
        addText("MONITOR REMOTO", 22f, strong = true)
        addText("v0.1.0-alpha.6  ·  Privado e criptografado", 12f, muted = true)
        gap(14)
        val tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun addTab(title: String, which: Int) {
            val tab = button(title, emphasis = mode == which) {
                mode = which; render()
            }
            val params = LinearLayout.LayoutParams(0, dp(48), 1f)
            if (which == 0) params.rightMargin = dp(8)
            tabs.addView(tab, params)
        }
        addTab("MONITORADO", 0)
        addTab("CONTROLE", 1)
        content.addView(tabs)
        gap(14)
        if (mode == 0) renderMonitor() else {
            renderController()
            content.post { if (active && mode == 1) refreshRemote(silent = true) }
        }
        gap(20)
        addText("Apenas em aparelhos próprios ou com autorização expressa. As mensagens são mantidas somente na memória enquanto o processo estiver ativo.", 12f, muted = true)
    }

    private fun renderMonitor() {
        addText("Este celular disponibiliza os dados", 17f, strong = true)
        addText("A tela pode ficar bloqueada depois da configuração inicial.", 13f, muted = true)
        gap()
        val battery = BatteryReader.snapshot(this)
        addText(batterySummary(battery), 15f)
        gap()
        addText("Servidor: ${if (MonitorService.isRunning) "ativo" else "parado"} · porta 37841", 15f, strong = true)
        addText("Endereços disponíveis: ${localIps().ifBlank { "verifique Wi-Fi/VPN" }}", 12f, muted = true)
        gap()
        if (!SettingsStore.isMonitorEnabled(this)) {
            content.addView(button("Ativar monitoramento", emphasis = true) { requestStart() })
        } else {
            content.addView(button("Interromper monitoramento") {
                SettingsStore.setMonitorEnabled(this, false)
                stopService(Intent(this, MonitorService::class.java))
                render()
            })
            if (!MonitorService.isRunning) {
                content.addView(button("Reiniciar serviço") { startServiceExplicitly() })
            }
        }
        content.addView(button("Gerar QR Code para parear", emphasis = true) { chooseQrAddress() })
        addText("QR temporário: válido por 2 minutos, uma conexão, sem expor a chave permanente.", 12f, muted = true)
        content.addView(button("Mostrar/copiar chave de pareamento") {
            try {
                val key = CryptoBox.encode(SecretVault.getOrCreateMonitorKey(this))
                AlertDialog.Builder(this)
                    .setTitle("Chave secreta de pareamento")
                    .setMessage("Esta chave dá acesso aos dados remotos. Não a envie a terceiros.\n\n$key")
                    .setPositiveButton("Copiar") { _, _ ->
                        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Chave Monitor Remoto", key))
                        message("Chave copiada. Apague-a da área de transferência após parear.")
                    }
                    .setNegativeButton("Fechar", null)
                    .show()
            } catch (error: Exception) { message("Keystore indisponível: ${error.message}") }
        })
        content.addView(button("Trocar chave e revogar acesso anterior") {
            AlertDialog.Builder(this)
                .setTitle("Revogar acessos?")
                .setMessage("A chave anterior deixará de funcionar. Será necessário parear novamente todos os controladores.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Trocar chave") { _, _ ->
                    SecretVault.save(this, SecretVault.MONITOR_KEY, CryptoBox.newPairingKey())
                    PairingSession.revoke()
                    message("Nova chave criada. Abra 'Mostrar/copiar chave'.")
                }.show()
        })
        gap(12)
        addText("Permissões independentes", 17f, strong = true)
        addText("Notificações: ${if (hasListener()) "autorizadas" else "sem acesso"}", 14f)
        content.addView(button("Configurar acesso às notificações") {
            AlertDialog.Builder(this)
                .setTitle("Acesso a notificações (opcional)")
                .setMessage(
                    "O Android permite que este serviço leia notificações de outros aplicativos, inclusive dados sensíveis. " +
                    "O Monitor Remoto só registra e transmite notificações dos aplicativos que você selecionar, " +
                    "com filtros adicionais para informações financeiras e de autenticação. " +
                    "Esses filtros não são infalíveis. As notificações selecionadas podem ser transmitidas " +
                    "para seu outro celular pela rede local em uma conexão criptografada, mesmo com esta tela bloqueada. " +
                    "Você pode revogar essa permissão nas Configurações do Android a qualquer momento. " +
                    "Não habilite em um aparelho com banco se o banco não aceitar essa permissão."
                )
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Entendi, configurar") { _, _ ->
                    startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }
                .show()
        })
        addText("Notificações permitidas: ${SettingsStore.notificationAllowlist(this).size}", 14f)
        content.addView(button("Selecionar aplicativos autorizados") { selectNotificationApps() })
        addText("Sem apps selecionados, nenhuma notificação é registrada. SMS direto não é coletado: novas mensagens podem aparecer apenas na notificação do app de SMS, se você o autorizar.", 12f, muted = true)
        gap(10)
        content.addView(button(if (SettingsStore.autoStartEnabled(this))
            "Desativar tentativa de iniciar após reiniciar" else "Tentar iniciar após reiniciar o celular") {
            SettingsStore.setAutoStart(this, !SettingsStore.autoStartEnabled(this)); render()
        })
        addText("A reinicialização automática depende das restrições do Android e do fabricante. O serviço não é invisível.", 12f, muted = true)
    }

    private fun selectNotificationApps() {
        val apps = packageManager.getInstalledApplications(0)
            .filter {
                it.packageName != packageName &&
                    packageManager.getLaunchIntentForPackage(it.packageName) != null &&
                    !SensitiveContent.isBlockedFinancialApp(
                        it.packageName, packageManager.getApplicationLabel(it).toString()
                    )
            }
            .sortedBy { packageManager.getApplicationLabel(it).toString().lowercase(Locale.getDefault()) }
        val names = apps.map { "${packageManager.getApplicationLabel(it)} (${it.packageName})" }.toTypedArray()
        val selected = SettingsStore.notificationAllowlist(this).toMutableSet()
        val checked = apps.map { it.packageName in selected }.toBooleanArray()
        AlertDialog.Builder(this).setTitle("Autorizar apps não financeiros")
            .setMessage("Somente os apps marcados podem ter notificações transmitidas. " +
                "Aplicativos bancários conhecidos são ocultados. O filtro não cobre todos os bancos. " +
                "SMS, quando disponível, pode aparecer pela notificação do aplicativo de mensagens, não pela caixa SMS.")
            .setMultiChoiceItems(names, checked) { _, index, enabled ->
                if (enabled) selected.add(apps[index].packageName) else selected.remove(apps[index].packageName)
            }
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Salvar") { _, _ ->
                SettingsStore.setNotificationAllowlist(this, selected)
                render()
            }.show()
    }

    private fun renderController() {
        addText("Consultar outro celular", 17f, strong = true)
        addText("Use o IP Wi-Fi do monitorado ou o IPv4/nome da VPN privada. Não informe http:// nem porta.", 13f, muted = true)
        gap()
        val host = field("Ex.: 192.168.1.50 ou celular.tailnet.ts.net", SettingsStore.getRemoteHost(this))
        val key = field(if (SecretVault.load(this, SecretVault.CONTROLLER_KEY) == null)
            "Cole a chave de pareamento (43 caracteres)" else "Chave já salva. Deixe vazio para mantê-la")
        content.addView(host)
        content.addView(key)
        content.addView(button("Salvar acesso", emphasis = true) {
            try {
                val address = host.text.toString().trim()
                require(address.isNotEmpty() && address.length <= 253 &&
                    !address.contains('/') && !address.contains(':') && !address.contains(' ')) {
                    "Informe um IP IPv4 ou hostname válido."
                }
                val typedKey = key.text.toString().trim()
                if (typedKey.isNotEmpty()) {
                    SecretVault.save(this, SecretVault.CONTROLLER_KEY, CryptoBox.decodeKey(typedKey))
                    key.text.clear()
                }
                require(SecretVault.load(this, SecretVault.CONTROLLER_KEY) != null) { "Cole primeiro a chave do outro celular." }
                SettingsStore.setRemoteHost(this, address)
                message("Acesso salvo com chave protegida pelo Android Keystore.")
                refreshRemote(silent = false)
            } catch (error: Exception) { message("Não foi possível salvar: ${error.message}") }
        })
        content.addView(button("Ler QR Code do outro celular", emphasis = true) { startQrScanner() })
        addText("Aponte a câmera para o QR exibido no Monitorado. Permissão de câmera somente durante a leitura.", 12f, muted = true)
        content.addView(button("Atualizar agora") { refreshRemote(silent = false) })
        addText("Atualização automática a cada 15 segundos somente enquanto esta tela estiver aberta.", 12f, muted = true)
        gap(14)
        errorBox = label("Aguardando primeira consulta…", muted = true).also { content.addView(it) }
        resultBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(resultBox)
    }

    private fun availableLocalIps(): List<Pair<String, String>> = try {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { iface -> iface.inetAddresses.toList().filterIsInstance<Inet4Address>()
                .mapNotNull { ip ->
                    val address = ip.hostAddress.orEmpty()
                    if (PairingCode.isPrivateIpv4(address)) iface.name to address else null
                }
            }.distinctBy { it.second }
    } catch (_: Exception) { emptyList() }

    private fun chooseQrAddress() {
        if (!MonitorService.isRunning) {
            message("Ative o monitoramento antes de gerar o QR Code.")
            return
        }
        val addresses = availableLocalIps()
        if (addresses.isEmpty()) {
            message("Não encontrei IP privado. Conecte ambos os celulares ao mesmo Wi-Fi ou ponto de acesso e tente novamente.")
            return
        }
        if (addresses.size == 1) showPairingQr(addresses.first().second)
        else AlertDialog.Builder(this)
            .setTitle("Escolha a rede do outro celular")
            .setItems(addresses.map { "${it.first}: ${it.second}" }.toTypedArray()) { _, index ->
                showPairingQr(addresses[index].second)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showPairingQr(ip: String) {
        val offer = PairingSession.issue()
        try {
            val code = PairingCode.encode(offer.copy(host = ip))
            val bitmap = BarcodeEncoder().encodeBitmap(code, BarcodeFormat.QR_CODE, 600, 600)
            val panel = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(8), dp(16), dp(8))
            }
            panel.addView(TextView(this).apply {
                text = "No aparelho CONTROLADOR, toque em 'Ler QR Code'.\nRede: $ip:${WireProtocol.PORT}"
                textSize = 14f
            })
            val size = minOf(resources.displayMetrics.widthPixels - dp(96), dp(320))
            panel.addView(ImageView(this).apply {
                setImageBitmap(bitmap)
                contentDescription = "QR Code de pareamento temporário. Não compartilhe."
            }, LinearLayout.LayoutParams(size, size).apply { gravity = Gravity.CENTER_HORIZONTAL })
            panel.addView(TextView(this).apply {
                text = "Acesso temporário. Expira em 2 minutos ou após o primeiro pareamento. Não compartilhe imagens deste código."
                textSize = 12f
            })
            val dialog = AlertDialog.Builder(this)
                .setTitle("Parear pela rede local")
                .setView(panel)
                .setNegativeButton("Fechar", null)
                .create()
            dialog.setOnDismissListener { PairingSession.revoke(offer.session) }
            dialog.show()
            dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            val monitorQr = object : Runnable {
                override fun run() {
                    if (!dialog.isShowing) return
                    if (PairingSession.active()?.first != offer.session) dialog.dismiss()
                    else handler.postDelayed(this, 1_000L)
                }
            }
            handler.postDelayed(monitorQr, 1_000L)
            handler.postDelayed({ if (dialog.isShowing) dialog.dismiss() }, PairingSession.EXPIRES_MS)
        } catch (e: Exception) {
            PairingSession.revoke(offer.session)
            message("Não foi possível exibir o QR: ${e.message}")
        }
    }

    private fun startQrScanner() {
        try {
            // ZXing Android Embedded 4.3.0 oferece QR_CODE (String), não QR_CODE_TYPES.
            // Chamada explícita sem encadeamento evita inferência incorreta de receptor.
            val scanner = IntentIntegrator(this)
            scanner.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
            scanner.setPrompt("Aponte para o QR do celular monitorado")
            scanner.setBeepEnabled(false)
            scanner.setBarcodeImageEnabled(false)
            scanner.initiateScan()
        } catch (e: Exception) {
            message("Não foi possível abrir a câmera: ${e.message}")
        }
    }

    @Deprecated("API Activity legada para compatibilidade com a Activity Android nativa")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val scan = IntentIntegrator.parseActivityResult(requestCode, resultCode, data) ?: return
        val text = scan.contents ?: return // Scanner cancelado.
        try {
            val code = PairingCode.decode(text)
            AlertDialog.Builder(this)
                .setTitle("Confirmar conexão local")
                .setMessage("Parear com o aparelho no IP ${code.host}:${WireProtocol.PORT}?\n\nConfirme somente se o QR está na tela do seu outro celular.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Conectar") { _, _ -> pairByQr(code) }
                .show()
        } catch (e: Exception) {
            message("QR inválido ou incompatível: ${e.message}")
        }
    }

    private fun pairByQr(code: PairingCode.Offer) {
        if (pairingInProgress) return
        pairingInProgress = true
        thread(name = "MonitorRemoto-qr-pair") {
            val result = runCatching { RemoteClient.pair(code) }
            runOnUiThread {
                pairingInProgress = false
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { key ->
                    try {
                        SecretVault.save(this, SecretVault.CONTROLLER_KEY, key)
                        SettingsStore.setRemoteHost(this, code.host)
                        mode = 1
                        render()
                        message("Pareamento concluído. IP e chave salvos com proteção do Android Keystore.")
                        refreshRemote(silent = false)
                    } catch (e: Exception) { message("Erro ao salvar pareamento: ${e.message}") }
                }.onFailure { e ->
                    message("Não foi possível parear: ${e.message}. Verifique o Wi-Fi, serviço ativo, tempo do QR e isolamento de clientes no roteador. Gere novo QR.")
                }
            }
        }
    }

    private fun refreshRemote(silent: Boolean) {
        if (fetching || mode != 1) return
        val host = SettingsStore.getRemoteHost(this)
        val key = SecretVault.load(this, SecretVault.CONTROLLER_KEY)
        if (host.isBlank() || key == null) {
            if (!silent) errorBox?.text = "Salve o IP/nome e a chave primeiro."
            return
        }
        fetching = true
        if (!silent) errorBox?.text = "Conectando…"
        thread(name = "MonitorRemoto-client") {
            val result = runCatching { RemoteClient.snapshot(host, key) }
            runOnUiThread {
                fetching = false
                if (!active || mode != 1) return@runOnUiThread
                result.onSuccess { displayResult(it) }
                    .onFailure { errorBox?.text = "Sem conexão: ${it.message ?: it.javaClass.simpleName}. Verifique serviço, rede/VPN e chave." }
            }
        }
    }

    private fun displayResult(root: JSONObject) {
        val panel = resultBox ?: return
        panel.removeAllViews()
        val battery = root.getJSONObject("battery")
        errorBox?.text = "Conectado · ${formatDate(root.optLong("serverMs"))}"
        val header = label(battery.optString("device", "Android"), 16f, true)
        panel.addView(header)
        panel.addView(label(batterySummary(battery), 15f))
        panel.addView(label("Leituras de corrente são estimativas do que o Android fornece: negativas = descarga; positivas = entrada líquida.", 12f, muted = true))
        val events = root.optJSONArray("events")
        panel.addView(label("Últimos eventos (${events?.length() ?: 0})", 17f, true))
        if (events == null || events.length() == 0) {
            panel.addView(label("Nenhuma notificação foi capturada desde o início do processo.", 13f, muted = true))
        } else {
            for (index in events.length() - 1 downTo 0) {
                val event = events.optJSONObject(index) ?: continue
                val item = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    background = shape(surface)
                    setPadding(dp(13), dp(10), dp(13), dp(10))
                }
                item.addView(label("${event.optString("type").uppercase(Locale.getDefault())} · ${event.optString("app")}", 11f, muted = true))
                item.addView(label(event.optString("title"), 15f, strong = true))
                item.addView(label(event.optString("preview"), 13f))
                item.addView(label(formatDate(event.optLong("whenMs")), 11f, muted = true))
                panel.addView(item, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(9) })
            }
        }
    }

    private fun batterySummary(battery: JSONObject): String {
        fun number(name: String, suffix: String, digits: Int = 1): String = if (battery.isNull(name)) "indisponível" else
            String.format(Locale.getDefault(), "% .${digits}f", battery.optDouble(name)).trim() + suffix
        val charge = if (battery.optBoolean("charging")) "carregando" else "descarregando"
        return "Bateria: ${number("levelPercent", "%", 0)} ($charge)\n" +
            "Temperatura: ${number("temperatureC", " °C")}\n" +
            "Tensão: ${if (battery.isNull("voltageMv")) "indisponível" else "${battery.optInt("voltageMv")} mV"}\n" +
            "Corrente instantânea: ${number("currentMa", " mA", 0)}\n" +
            "Corrente média: ${number("averageMa", " mA", 0)}"
    }

    private fun requestStart() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
        } else startServiceExplicitly()
    }

    private fun startServiceExplicitly() {
        try {
            SecretVault.getOrCreateMonitorKey(this)
            SettingsStore.setMonitorEnabled(this, true)
            MonitorService.start(this)
            render()
        } catch (error: Exception) {
            SettingsStore.setMonitorEnabled(this, false)
            message("Não foi possível iniciar o serviço: ${error.message}")
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        val granted = results.isNotEmpty() && results[0] == PackageManager.PERMISSION_GRANTED
        when (requestCode) {
            101 -> if (granted) startServiceExplicitly() else message("Autorize notificações para exibir o monitoramento ativo.")
        }
    }

    private fun hasListener(): Boolean = try {
        val setting = Settings.Secure.getString(contentResolver, "enabled_notification_listeners").orEmpty()
        setting.split(':').any {
            ComponentName.unflattenFromString(it) == ComponentName(this, NotificationCaptureService::class.java)
        }
    } catch (_: Exception) { false }

    private fun localIps(): String = try {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { iface -> iface.inetAddresses.toList().filterIsInstance<Inet4Address>()
                .map { "${iface.name}: ${it.hostAddress}" } }
            .joinToString(" · ")
    } catch (_: Exception) { "" }

    private fun formatDate(timestamp: Long): String = SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault()).format(Date(timestamp))

    private fun message(message: String) {
        AlertDialog.Builder(this).setMessage(message).setPositiveButton("OK", null).show()
    }
}
