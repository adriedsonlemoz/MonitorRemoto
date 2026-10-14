package br.dev.monitorremoto.ui

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.os.SystemClock
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.graphics.Bitmap
import android.graphics.Color
import android.view.WindowManager
import android.widget.ImageView
import android.os.Build
import android.os.Bundle
import android.os.PersistableBundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import br.dev.monitorremoto.R
import br.dev.monitorremoto.capture.NotificationCaptureService
import br.dev.monitorremoto.core.RecentDevices
import br.dev.monitorremoto.core.Ipv4Suggestion
import br.dev.monitorremoto.core.SettingsStore
import br.dev.monitorremoto.core.SensitiveContent
import br.dev.monitorremoto.security.CryptoBox
import br.dev.monitorremoto.security.SecretVault
import br.dev.monitorremoto.service.MonitorService
import br.dev.monitorremoto.transport.AppVersion
import br.dev.monitorremoto.transport.AuthenticatedAccess
import br.dev.monitorremoto.transport.RemoteClient
import br.dev.monitorremoto.transport.PairingCode
import br.dev.monitorremoto.transport.PairingSession
import br.dev.monitorremoto.transport.WireProtocol
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import org.json.JSONObject
import java.net.Inet4Address
import java.net.NetworkInterface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

/** Interface Android Views sem dependências adicionais; ambos os modos no mesmo APK. */
class MainActivity : Activity() {
    private fun darkTheme(): Boolean = when (SettingsStore.theme(this)) {
        "dark" -> true
        "system" -> (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        else -> false
    }
    internal val bg get() = if (darkTheme()) Color.rgb(14, 22, 35) else Color.rgb(247, 249, 252)
    internal val surface get() = if (darkTheme()) Color.rgb(28, 41, 58) else Color.WHITE
    internal val ink get() = if (darkTheme()) Color.rgb(233, 239, 247) else Color.rgb(23, 34, 48)
    internal val dim get() = if (darkTheme()) Color.rgb(167, 184, 203) else Color.rgb(92, 109, 128)
    internal val highlight get() = if (darkTheme()) Color.rgb(90, 214, 188) else Color.rgb(24, 166, 146)
    internal val highlightText get() = if (darkTheme()) Color.rgb(14, 22, 35) else Color.WHITE
    internal val lineColor get() = if (darkTheme()) Color.rgb(47, 64, 83) else Color.rgb(224, 231, 239)
    internal val secondaryAccent get() = if (darkTheme()) Color.rgb(54, 116, 171) else Color.rgb(38, 113, 174)
    internal val fieldColor get() = if (darkTheme()) Color.rgb(22, 34, 50) else Color.rgb(242, 246, 250)
    internal lateinit var content: LinearLayout
    private val handler = Handler(Looper.getMainLooper())
    internal var mode = 2 // 2: início, 0: monitorado, 1: controle
    private val cameraPermissionRequest = 4317
    private val qrScanRequest = 4318
    private val notificationPermissionRequest = 101
    private var active = false
    private var fetching = false
    private var fetchingHost: String = ""
    internal var resultBox: LinearLayout? = null
    private var pairingInProgress = false
    internal var selectedConnectionMethod: String = "Chave salva"
    companion object { const val EXTRA_OPEN_MONITOR = "br.dev.monitorremoto.OPEN_MONITOR" }
    private var showingIncoming = false
    private var lastRecoveryAttemptMs = 0L
    private val incomingListener: () -> Unit = {
        handler.post {
            if (active && !isFinishing && !isDestroyed) {
                if (!showIncomingAccessIfNeeded() && mode == 0) render()
            }
        }
    }

    /** Interrompe até mesmo diálogos de Configurações: uma nova Activity ocupa a tela. */
    private fun showIncomingAccessIfNeeded(): Boolean {
        if (!active || showingIncoming || isFinishing || isDestroyed ||
            !SettingsStore.isMonitorEnabled(this) || !MonitorService.isRunning ||
            AuthenticatedAccess.recent().isEmpty()) return false
        showingIncoming = true
        startActivity(Intent(this, IncomingAccessActivity::class.java))
        // Fecha também os diálogos abertos na Activity anterior.
        finish()
        return true
    }

    private val monitorStatusPoll = object : Runnable {
        override fun run() {
            if (active && mode == 0) {
                render()
                handler.postDelayed(this, 5_000L) // Time out stale activity on screen.
            }
        }
    }
    private var lastResult: JSONObject? = null
    private var lastResultHost: String = ""
    internal var lastSuccessfulMs: Long = 0L
    internal var connectionState: String = "idle" // idle / loading / connected / offline
    private var connectionMessage: String? = null
    private var currentStatus: TextView? = null
    internal var currentDeviceTitle: TextView? = null
    private var currentUpdated: TextView? = null
    private var currentConnectButton: Button? = null
    private var systemBackCallback: android.window.OnBackInvokedCallback? = null
    private data class OptionItem(val title: String, val subtitle: String = "", val action: () -> Unit)

    private val poller = object : Runnable {
        override fun run() {
            if (active && mode == 1) {
                refreshRemote(silent = true)
                handler.postDelayed(this, 15_000L)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(if (darkTheme()) android.R.style.Theme_Material_NoActionBar else android.R.style.Theme_Material_Light_NoActionBar)
        super.onCreate(savedInstanceState)
        RecentDevices.migrateLegacy(this)
        mode = if (intent.getBooleanExtra(EXTRA_OPEN_MONITOR, false)) 0
            else savedInstanceState?.getInt("active_mode", 2)?.takeIf { it in 0..2 } ?: 2
        window.statusBarColor = bg
        window.navigationBarColor = bg
        window.decorView.systemUiVisibility = if (darkTheme()) 0 else
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        val scroll = ScrollView(this).apply { setBackgroundColor(bg); isFillViewport = true }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(24), dp(18), dp(32))
        }
        scroll.addView(content)
        setContentView(scroll)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val callback = android.window.OnBackInvokedCallback {
                if (mode == 2) finish() else navigateHome()
            }
            systemBackCallback = callback
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback
            )
        }
        render()
    }

    override fun onDestroy() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            systemBackCallback?.let { onBackInvokedDispatcher.unregisterOnBackInvokedCallback(it) }
            systemBackCallback = null
        }
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("active_mode", mode)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        active = true
        AuthenticatedAccess.subscribe(incomingListener)
        render()
        if (!showIncomingAccessIfNeeded()) recoverAuthorizedServiceIfStopped()
        handler.removeCallbacks(monitorStatusPoll)
        if (mode == 0) handler.postDelayed(monitorStatusPoll, 5_000L)
        handler.removeCallbacks(poller)
        handler.postDelayed(poller, 15_000L)
    }

    /** Recuperação conservadora somente com o app visível; nunca após parada explícita. */
    private fun recoverAuthorizedServiceIfStopped() {
        if (!SettingsStore.isMonitorEnabled(this) || MonitorService.isRunning) return
        val now = SystemClock.elapsedRealtime()
        if (lastRecoveryAttemptMs != 0L && now - lastRecoveryAttemptMs < 60_000L) return
        lastRecoveryAttemptMs = now
        handler.postDelayed({
            if (active && !isFinishing && !isDestroyed &&
                SettingsStore.isMonitorEnabled(this) && !MonitorService.isRunning) {
                runCatching { MonitorService.start(this) }
                handler.postDelayed({ if (active && mode == 0) render() }, 1_000L)
            }
        }, 800L)
    }

    override fun onPause() {
        active = false
        AuthenticatedAccess.unsubscribe(incomingListener)
        handler.removeCallbacks(monitorStatusPoll)
        handler.removeCallbacks(poller)
        // Uma resposta anterior não significa que ainda haja conexão ao voltar ao app.
        if (mode == 1 && connectionState == "connected") {
            connectionState = "idle"
            updateConnectionLabels()
        }
        super.onPause()
    }

    internal fun render() {
        content.removeAllViews()
        handler.removeCallbacks(monitorStatusPoll)
        if (active && mode == 0) handler.postDelayed(monitorStatusPoll, 5_000L)
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val title = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        title.addView(label("MONITOR REMOTO", 18f, strong = true).apply {
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        title.addView(label(when (mode) {
            1 -> "Painel de controle · privado e criptografado"
            0 -> "Modo monitorado · privado e criptografado"
            else -> "Início · conexão e aparelhos anteriores"
        }, 10.5f, muted = true).apply {
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        header.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(actionButton("⌂") { navigateHome() }.apply {
            contentDescription = "Home: voltar à tela inicial"
            textSize = 22f
        }, LinearLayout.LayoutParams(dp(45), dp(43)))
        header.addView(View(this), LinearLayout.LayoutParams(dp(7), 1))
        header.addView(actionButton("⚙") { showSettingsDialog() }.apply {
            contentDescription = "Configurações"
        }, LinearLayout.LayoutParams(dp(45), dp(43)))
        content.addView(header)

        val metaRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        metaRow.addView(infoChip("v${AppVersion.NAME}"))
        metaRow.addView(View(this), LinearLayout.LayoutParams(dp(8), 1))
        val monitorActive = SettingsStore.isMonitorEnabled(this) && MonitorService.isRunning
        metaRow.addView(infoChip(if (monitorActive) "Monitorado ativo" else "Monitorado inativo", accent = monitorActive))
        content.addView(metaRow)

        gap(16)
        when (mode) {
            0 -> renderMonitor()
            1 -> {
                renderController()
                content.post { if (active && mode == 1) refreshRemote(silent = true) }
            }
            else -> renderHome()
        }
        gap(20)
        addText("Somente em aparelhos próprios ou autorizados. Notificações permitidas são temporárias e filtradas.", 11f, muted = true)
    }

    /** Home retorna à escolha inicial, preservando serviço e chaves de pareamento. */
    private fun navigateHome() {
        if (mode != 2) { mode = 2; render() }
    }

    @Deprecated("Android Activity back callback")
    override fun onBackPressed() {
        if (mode != 2) navigateHome() else super.onBackPressed()
    }

    internal fun showAboutDialog() {
        val (wrapper, layout) = dialogWrapper()
        dialogIntro(layout, "Resumo rápido do foco atual do projeto.")
        val card = panel(14)
        card.addView(dialogMetricRow("Versão", AppVersion.NAME))
        card.addView(dialogMetricRow("Tela inicial", "Home com escolha de aparelhos"))
        card.addView(dialogMetricRow("Modo monitorado", "No mesmo APK"))
        card.addView(dialogMetricRow("Segurança", "Pareamento local e Android Keystore"))
        layout.addView(card)
        layout.addView(label("O aplicativo prioriza uso local, privacidade, baixo consumo e preparação para futura publicação na Google Play.", 12f, muted = true))
        AlertDialog.Builder(this)
            .setTitle("Sobre o Monitor Remoto")
            .setView(wrapper)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showSavedDeviceOptions(item: RecentDevices.Device) {
        val (wrapper, layout) = dialogWrapper()
        dialogIntro(layout, "${item.name} · ${item.host}")
        var popup: AlertDialog? = null
        layout.addView(dialogCardRow("Conectar", "Consultar este aparelho", accent = true) {
            popup?.dismiss()
            connectSavedDevice(item)
        })
        sectionGap(layout, 8)
        layout.addView(dialogCardRow("Renomear", "Alterar o nome exibido") {
            popup?.dismiss()
            renameSavedDevice(item)
        })
        sectionGap(layout, 8)
        layout.addView(dialogCardRow("Esquecer", "Apagar a credencial salva") {
            popup?.dismiss()
            forgetSavedDevice(item)
        })
        popup = AlertDialog.Builder(this).setTitle("Aparelho salvo")
            .setView(wrapper).setNegativeButton("Fechar", null).create()
        popup.show()
    }

    /** Prefere a interface Wi-Fi no instante de abertura, evitando IP antigo em cache. */
    internal fun preferredLocalAddress(): String? {
        val ips = availableLocalIps()
        return (ips.firstOrNull { it.first.startsWith("wlan", ignoreCase = true) }
            ?: ips.firstOrNull())?.second
    }

    /** IP + segredo só são lidos após solicitação explícita, e não ficam na tela principal. */
    internal fun showMyConnectionInfo() {
        if (!SettingsStore.isMonitorEnabled(this) || !MonitorService.isRunning) {
            AlertDialog.Builder(this)
                .setTitle("Monitoramento inativo")
                .setMessage("Ative o monitoramento para compartilhar os dados de conexão deste aparelho.")
                .setPositiveButton("Entendi", null).show()
            return
        }
        val ip = preferredLocalAddress()
        if (ip == null) {
            AlertDialog.Builder(this)
                .setTitle("Rede indisponível")
                .setMessage("Nenhum IP privado disponível. Conecte o aparelho a uma rede Wi-Fi ou VPN compatível e tente novamente.")
                .setPositiveButton("OK", null).show()
            return
        }
        val key = try { CryptoBox.encode(SecretVault.getOrCreateMonitorKey(this)) }
            catch (error: Exception) {
                message("Não foi possível acessar a chave protegida: ${error.message}")
                return
            }
        val (wrapper, layout) = dialogWrapper()
        dialogIntro(layout, "Use somente entre aparelhos próprios ou autorizados. A chave permite acesso aos dados monitorados.")
        val card = panel(14)
        card.addView(label("Endereço IP atual", 12f, muted = true))
        card.addView(label(ip, 17f, strong = true))
        sectionGap(card, 6)
        card.addView(label("Chave de pareamento", 12f, muted = true))
        val keyLabel = label("••••••••••••••••", 14f).apply {
            setTextIsSelectable(false)
            maxLines = 3
            setTextColor(ink)
        }
        card.addView(keyLabel)
        layout.addView(card)
        sectionGap(layout, 8)
        val payload = "Monitor Remoto\nIP: $ip\nChave: $key"
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        var shown = false
        lateinit var revealButton: Button
        revealButton = actionButton("Mostrar") {
            shown = !shown
            keyLabel.text = if (shown) key else "••••••••••••••••"
            revealButton.text = if (shown) "Ocultar" else "Mostrar"
        }
        fun addInfoAction(button: Button) {
            if (actions.childCount > 0) actions.addView(View(this), LinearLayout.LayoutParams(dp(5), 1))
            actions.addView(button.apply {
                textSize = 11f; minWidth = 0; setPadding(dp(2), 0, dp(2), 0)
            }, LinearLayout.LayoutParams(0, dp(43), 1f))
        }
        addInfoAction(revealButton)
        addInfoAction(actionButton("Copiar", emphasis = true) {
            val clip = ClipData.newPlainText("Monitor Remoto: IP e chave", payload)
            if (Build.VERSION.SDK_INT >= 33) {
                clip.description.extras = PersistableBundle().apply {
                    putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                }
            }
            (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
            message("IP e chave copiados. Limpe a área de transferência após o uso.")
        })
        addInfoAction(actionButton("Compartilhar") {
            AlertDialog.Builder(this)
                .setTitle("Compartilhar credencial?")
                .setMessage("A mensagem inclui IP e chave. Envie somente a aparelho ou pessoa autorizada.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Escolher app") { _, _ ->
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, payload)
                    }
                    try { startActivity(Intent.createChooser(intent, "Compartilhar conexão")) }
                    catch (_: Exception) { message("Nenhum aplicativo disponível.") }
                }.show()
        })
        layout.addView(actions)
        val dialog = AlertDialog.Builder(this)
            .setTitle("Minhas informações")
            .setView(wrapper)
            .setNegativeButton("Fechar", null)
            .create()
        dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        dialog.show()
    }

    internal fun selectNotificationApps() {
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
        val host = SettingsStore.getRemoteHost(this)
        val card = panel(18)
        val savedDevice = RecentDevices.list(this).firstOrNull { it.host == host }
        val lastDeviceName = lastResult?.optJSONObject("battery")?.optString("device")
            ?.takeIf { lastResultHost == host && it.isNotBlank() }
        val titleText = if (savedDevice?.customName == true) savedDevice.name
            else lastDeviceName ?: savedDevice?.name ?: "Nenhum aparelho selecionado"

        val heading = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.TOP }
        val infoColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        currentDeviceTitle = label(titleText, 19f, strong = true).apply {
            maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END
        }
        infoColumn.addView(currentDeviceTitle)
        val statusRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        currentStatus = label("", 13f, strong = true)
        statusRow.addView(currentStatus)
        statusRow.addView(View(this), LinearLayout.LayoutParams(dp(6), 1))
        statusRow.addView(infoChip("${networkIcon(host)} ${connectionKind(host)}"))
        infoColumn.addView(statusRow)
        currentUpdated = label("", 11.5f, muted = true).apply { setPadding(0, dp(1), 0, dp(2)) }
        infoColumn.addView(currentUpdated)

        val extraRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        extraRow.addView(infoChip("🌐 ${if (host.isBlank()) "Sem IP" else host}"))
        extraRow.addView(View(this), LinearLayout.LayoutParams(dp(6), 1))
        extraRow.addView(infoChip("🔐 $selectedConnectionMethod"))
        infoColumn.addView(extraRow)

        heading.addView(infoColumn, LinearLayout.LayoutParams(0, -2, 1f))
        heading.addView(View(this), LinearLayout.LayoutParams(dp(10), 1))
        val actionColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        currentConnectButton = actionButton("Conectar", emphasis = true) {
            val actualState = effectiveConnectionState()
            if (actualState == "connected" || actualState == "loading") disconnectCurrent()
            else showConnectionChooser()
        }.apply { textSize = 11.5f; minWidth = 0; setPadding(dp(4), 0, dp(4), 0) }
        actionColumn.addView(currentConnectButton, LinearLayout.LayoutParams(dp(116), dp(42)))
        actionColumn.addView(View(this), LinearLayout.LayoutParams(1, dp(8)))
        actionColumn.addView(actionButton("↻ Atualizar") { refreshRemote(silent = false) }.apply {
            background = shape(secondaryAccent, 12)
            setTextColor(Color.WHITE)
            textSize = 11.5f
            minWidth = 0
            setPadding(dp(4), 0, dp(4), 0)
        }, LinearLayout.LayoutParams(dp(116), dp(42)))
        heading.addView(actionColumn)
        card.addView(heading)
        sectionGap(card, 10)

        resultBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        card.addView(resultBox)
        content.addView(card)
        updateConnectionLabels()
        val cached = lastResult
        if (cached != null && lastResultHost == host) displayResult(cached, fromCache = true)
        else resultBox?.addView(label("Toque em Conectar para escolher QR Code, conexão manual ou aparelho salvo.", 13f, muted = true))
        gap(12)
        addText("Atualização automática a cada 15 segundos enquanto esta tela estiver aberta.", 11f, muted = true)
    }

    private fun effectiveConnectionState(): String {
        val host = SettingsStore.getRemoteHost(this)
        val hasKey = host.isNotBlank() && RecentDevices.key(this, host) != null
        return if (!hasKey) "idle" else connectionState
    }

    private fun disconnectCurrent() {
        val previousHost = SettingsStore.getRemoteHost(this)
        val previousKey = RecentDevices.key(this, previousHost)
        if (previousHost.isNotBlank() && previousKey != null) {
            thread(name = "MonitorRemoto-disconnect") {
                runCatching { RemoteClient.disconnect(previousHost, previousKey, applicationContext) }
            }
        }
        SettingsStore.setRemoteHost(this, "")
        fetching = false
        fetchingHost = ""
        lastResult = null
        lastResultHost = ""
        lastSuccessfulMs = 0L
        connectionState = "idle"
        connectionMessage = "Seleção removida."
        selectedConnectionMethod = "Chave salva"
        mode = 2
        render()
    }

    private fun updateConnectionLabels() {
        val host = SettingsStore.getRemoteHost(this)
        val hasKey = host.isNotBlank() && RecentDevices.key(this, host) != null
        val actualState = effectiveConnectionState()
        val text = when (actualState) {
            "connected" -> "● Conectado"
            "offline" -> "● Sem resposta"
            "loading" -> "◌ Atualizando…"
            else -> "○ Desconectado"
        }
        currentStatus?.text = text
        currentStatus?.setTextColor(if (actualState == "connected") highlight else
            if (actualState == "offline") Color.rgb(190, 88, 61) else dim)
        currentConnectButton?.text = when (actualState) {
            "connected", "loading" -> "Desconectar"
            "offline" -> "Reconectar"
            else -> "Conectar"
        }
        currentUpdated?.text = when {
            !hasKey -> "Selecione uma forma de conexão no botão Conectar"
            lastSuccessfulMs > 0 && lastResultHost == host ->
                "Atualizado: ${formatDate(lastSuccessfulMs)}" +
                    if (actualState == "offline") " · dados anteriores" else ""
            else -> connectionMessage ?: "Aguardando resposta de $host"
        }
    }

    private fun networkIcon(host: String): String = when {
        host.isBlank() -> "○"
        localWifiPrefix()?.let { host.startsWith(it) } == true -> "📶"
        PairingCode.isPrivateIpv4(host) -> "📡"
        else -> "🛡"
    }

    internal fun connectionKind(host: String): String = when {
        host.isBlank() -> "Sem aparelho"
        localWifiPrefix()?.let { host.startsWith(it) } == true -> "IP da rede local"
        PairingCode.isPrivateIpv4(host) -> "IP privado / VPN"
        else -> "VPN / nome de rede"
    }

    /** Apenas o IP da interface de rede Wi-Fi ativa; sem supor prefixos de outras redes. */
    private fun localWifiPrefix(): String? {
        return try {
            val manager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = manager.activeNetwork
            val capabilities = network?.let { manager.getNetworkCapabilities(it) }
            if (network == null || capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) != true) null
            else manager.getLinkProperties(network)?.linkAddresses?.firstNotNullOfOrNull { link ->
                val ip = link.address as? Inet4Address ?: return@firstNotNullOfOrNull null
                if (!ip.isSiteLocalAddress) return@firstNotNullOfOrNull null
                Ipv4Suggestion.prefix(ip.hostAddress.orEmpty(), link.prefixLength)
            }
        } catch (_: Exception) { null }
    }

    /** Etapa inicial compacta; somente a opção Manual abre os campos de IP e chave. */
    /** QR/manual as two horizontal choices. No redundant QR text-entry action. */
    internal fun showConnectionChooser() {
        RecentDevices.migrateLegacy(this)
        val (wrapper, layout) = dialogWrapper()
        dialogIntro(layout, "Escolha como conectar.")
        var chooser: AlertDialog? = null
        val choices = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        choices.addView(actionButton("▣ QR Code", emphasis = true) {
            chooser?.dismiss()
            startQrScanner()
        }, LinearLayout.LayoutParams(0, dp(52), 1f))
        choices.addView(View(this), LinearLayout.LayoutParams(dp(8), 1))
        choices.addView(actionButton("⌨ Manual") {
            chooser?.dismiss()
            showManualConnectionDialog()
        }, LinearLayout.LayoutParams(0, dp(52), 1f))
        layout.addView(choices)
        sectionGap(layout, 14)
        layout.addView(label("Aparelhos anteriores", 15f, strong = true))
        val devices = RecentDevices.list(this)
        if (devices.isEmpty()) {
            layout.addView(label("Nenhum aparelho salvo.", 12f, muted = true))
        }
        devices.forEach { item ->
            val card = panel(10)
            card.addView(label(item.name, 13.5f, strong = true).apply {
                maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END
            })
            val seen = if (item.lastSuccessMs == 0L) "Sem acesso confirmado" else formatDate(item.lastSuccessMs)
            card.addView(label("${item.host} · $seen", 10.5f, muted = true).apply {
                maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END
            })
            sectionGap(card, 6)
            val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            fun addAction(title: String, emphasized: Boolean = false, red: Boolean = false, onClick: () -> Unit) {
                if (buttons.childCount > 0) buttons.addView(View(this), LinearLayout.LayoutParams(dp(5), 1))
                buttons.addView(actionButton(title, emphasized, onClick).apply {
                    textSize = 10.5f
                    minWidth = 0
                    setPadding(dp(2), 0, dp(2), 0)
                    if (red) setTextColor(Color.rgb(186, 78, 65))
                }, LinearLayout.LayoutParams(0, dp(42), 1f))
            }
            addAction("Conectar", true) { chooser?.dismiss(); connectSavedDevice(item) }
            addAction("Renomear") { chooser?.dismiss(); renameSavedDevice(item) }
            addAction("Esquecer", red = true) { chooser?.dismiss(); forgetSavedDevice(item) }
            card.addView(buttons)
            layout.addView(card)
            sectionGap(layout, 7)
        }
        chooser = AlertDialog.Builder(this).setTitle("Conectar aparelho")
            .setView(wrapper).setNegativeButton("Fechar", null).create()
        chooser.show()
    }

    internal fun connectSavedDevice(item: RecentDevices.Device) {
        if (RecentDevices.key(this, item.host) == null) {
            message("A chave desse aparelho não está disponível. Faça um novo pareamento.")
            showConnectionChooser()
            return
        }
        SettingsStore.setRemoteHost(this, item.host)
        selectedConnectionMethod = "Chave salva"
        mode = 1
        lastResult = null; lastResultHost = ""; lastSuccessfulMs = 0L
        connectionState = "loading"; connectionMessage = null
        render(); refreshRemote(silent = false)
    }

    internal fun renameSavedDevice(item: RecentDevices.Device, returnToChooser: Boolean = true) {
        val input = field("Nome do aparelho", item.name)
        AlertDialog.Builder(this).setTitle("Renomear aparelho").setView(input)
            .setNegativeButton("Cancelar") { _, _ -> if (returnToChooser) showConnectionChooser() }
            .setPositiveButton("Salvar") { _, _ ->
                RecentDevices.rename(this, item.host, input.text.toString())
                render(); if (returnToChooser) showConnectionChooser()
            }.show()
    }

    internal fun forgetSavedDevice(item: RecentDevices.Device, returnToChooser: Boolean = true) {
        AlertDialog.Builder(this).setTitle("Esquecer aparelho?")
            .setMessage("Remover ${item.name} e apagar a chave de pareamento salva? Para conectar novamente será necessário parear.")
            .setNegativeButton("Cancelar") { _, _ -> if (returnToChooser) showConnectionChooser() }
            .setPositiveButton("Esquecer") { _, _ ->
                val wasSelected = SettingsStore.getRemoteHost(this).equals(item.host, ignoreCase = true)
                RecentDevices.forget(this, item.host)
                if (wasSelected) {
                    lastResult = null; lastResultHost = ""; lastSuccessfulMs = 0L
                    connectionState = "idle"; connectionMessage = null
                }
                render(); if (returnToChooser) showConnectionChooser()
            }.show()
    }

    private fun showManualConnectionDialog() {
        val (wrapper, layout) = dialogWrapper()
        val existing = SettingsStore.getRemoteHost(this)
        val initialHost = existing.ifBlank { localWifiPrefix().orEmpty() }
        val host = field("Ex.: 192.168.100.210", initialHost).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }
        val hasSaved = RecentDevices.key(this, existing) != null
        val key = field("Cole a chave do aparelho monitorado").apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            if (hasSaved) hint = "Chave já salva. Deixe vazio para manter"
        }
        val networkPanel = panel(14)
        networkPanel.addView(label("1. Endereço do aparelho", 15f, strong = true))
        networkPanel.addView(label("Digite o IP local completo ou o nome da VPN privada.", 12f, muted = true))
        networkPanel.addView(host)
        networkPanel.addView(label("Sugestão da rede Wi-Fi atual, editável.", 11.5f, muted = true))
        layout.addView(networkPanel)
        sectionGap(layout, 12)

        val keyPanel = panel(14)
        keyPanel.addView(label("2. Chave de pareamento", 15f, strong = true))
        keyPanel.addView(label("Cole a chave ou deixe vazio para usar a chave salva deste IP.", 12f, muted = true))
        keyPanel.addView(key)
        val keyActions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        keyActions.addView(actionButton("Colar", emphasis = true) {
            val clip = (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip
            val pasted = clip?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty().trim()
            if (pasted.isNotBlank()) key.setText(pasted) else message("Área de transferência vazia.")
        }, LinearLayout.LayoutParams(0, dp(46), 1f))
        keyActions.addView(View(this), LinearLayout.LayoutParams(dp(8), 1))
        keyActions.addView(actionButton("Limpar") { key.text.clear() }, LinearLayout.LayoutParams(0, dp(46), 1f))
        keyPanel.addView(keyActions)
        layout.addView(keyPanel)
        val dialog = AlertDialog.Builder(this).setTitle("Conexão manual")
            .setView(wrapper).setNegativeButton("Voltar") { _, _ -> showConnectionChooser() }
            .setPositiveButton("Conectar", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                try {
                    val address = host.text.toString().trim()
                    require(address.isNotEmpty() && address.length <= 253 &&
                        !address.contains('/') && !address.contains(':') && address.none { it.isWhitespace() } &&
                        !address.endsWith('.')) { "Complete o endereço IP ou hostname, sem porta." }
                    val typedKey = key.text.toString().trim()
                    val parsedKey = if (typedKey.isNotEmpty()) CryptoBox.decodeKey(typedKey)
                        else RecentDevices.key(this, address)
                    require(parsedKey != null) { "Informe a chave desse aparelho ou faça um novo pareamento." }
                    RecentDevices.save(this, address, parsedKey)
                    lastResult = null; lastResultHost = ""; lastSuccessfulMs = 0L
                    connectionState = "loading"; connectionMessage = null
                    selectedConnectionMethod = "Manual"
                    key.text.clear(); dialog.dismiss(); mode = 1; render(); refreshRemote(silent = false)
                } catch (ex: Exception) { message("Não foi possível conectar: ${ex.message}") }
            }
        }
        dialog.show()
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

    internal fun chooseQrAddress() {
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
            val matrix = QRCodeWriter().encode(code, BarcodeFormat.QR_CODE, 600, 600)
            val pixels = IntArray(600 * 600) { index ->
                if (matrix[index % 600, index / 600]) Color.BLACK else Color.WHITE
            }
            val bitmap = Bitmap.createBitmap(600, 600, Bitmap.Config.ARGB_8888)
            bitmap.setPixels(pixels, 0, 600, 0, 0, 600, 600)
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
        // O fluxo de autorização é visível antes de abrir a Activity de câmera.
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
            message("Este aparelho não possui câmera disponível para leitura. Use o pareamento manual.")
            return
        }
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), cameraPermissionRequest)
            return
        }
        launchQrCapture()
    }

    /** Resposta centralizada: cada permissão mantém o fluxo específico e independente. */
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
        when (requestCode) {
            cameraPermissionRequest -> {
                if (granted) launchQrCapture()
                else message("Câmera não autorizada. Permita o acesso para ler o QR ou use IP/chave manual.")
            }
            notificationPermissionRequest -> {
                if (granted) startServiceExplicitly()
                else message("Autorize notificações para exibir o monitoramento ativo.")
            }
        }
    }

    private fun launchQrCapture() {
        try {
            // Activity nativa: elimina a antiga Activity de câmera da biblioteca ZXing Embedded.
            startActivityForResult(Intent(this, QrScanActivity::class.java), qrScanRequest)
        } catch (e: Exception) {
            android.util.Log.e("MonitorRemotoQR", "Falha ao abrir câmera nativa", e)
            message("Falha ao abrir leitor (${e.javaClass.simpleName}). Use conexão manual por IP/chave ou consulte o diagnóstico da câmera.")
        }
    }

    @Deprecated("Compatibilidade com a Activity Android nativa")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != qrScanRequest || resultCode != Activity.RESULT_OK) return
        val scanned = data?.getStringExtra(QrScanActivity.EXTRA_QR_RESULT) ?: return
        confirmQrCode(scanned)
    }

    private fun confirmQrCode(text: String) {
        try {
            val code = PairingCode.decode(text)
            AlertDialog.Builder(this)
                .setTitle("Confirmar aparelho")
                .setMessage("Aparelho: ${RecentDevices.list(this).firstOrNull { it.host == code.host }?.name ?: "Não identificado (novo)"}\nEndereço: ${code.host}:${WireProtocol.PORT}\n\nConfira se é o celular monitorado. O nome só poderá ser verificado após autenticação.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Confirmar") { _, _ -> pairByQr(code) }
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
                        RecentDevices.save(this, code.host, key)
                        selectedConnectionMethod = "QR Code"
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
        if (mode != 1) return
        val host = SettingsStore.getRemoteHost(this)
        if (fetching && fetchingHost == host) return
        val key = if (host.isBlank()) null else RecentDevices.key(this, host)
        if (host.isBlank() || key == null) {
            connectionState = "idle"; connectionMessage = "Configure um aparelho em Conectar."
            updateConnectionLabels()
            return
        }
        fetching = true
        fetchingHost = host
        val method = selectedConnectionMethod
        connectionState = "loading"; connectionMessage = null
        updateConnectionLabels()
        thread(name = "MonitorRemoto-client") {
            val result = runCatching { RemoteClient.snapshot(host, key, applicationContext, method) }
            runOnUiThread {
                if (fetchingHost != host) return@runOnUiThread
                fetching = false
                if (!active || mode != 1 || SettingsStore.getRemoteHost(this) != host) return@runOnUiThread
                result.onSuccess {
                    lastSuccessfulMs = System.currentTimeMillis()
                    lastResult = it; lastResultHost = host
                    connectionState = "connected"
                    val name = it.optJSONObject("battery")?.optString("device").orEmpty()
                    RecentDevices.markSuccess(this, host, name, lastSuccessfulMs)
                    currentDeviceTitle?.text = RecentDevices.list(this).firstOrNull { d -> d.host == host }
                        ?.name ?: name.ifBlank { host }
                    displayResult(it)
                }.onFailure {
                    connectionState = "offline"
                    connectionMessage = it.message?.takeIf { txt -> txt.contains("Versões incompatíveis") }
                        ?: "Sem resposta: ${it.javaClass.simpleName}"
                    if (lastResultHost == host) {
                        lastResult?.let { cached -> displayResult(cached, fromCache = true) }
                    }
                    if (!silent && lastResultHost != host) {
                        resultBox?.removeAllViews()
                        resultBox?.addView(label(connectionMessage ?: "Não foi possível consultar o aparelho. Confira Wi-Fi/VPN, endereço e chave.", 13f, muted = true))
                    }
                }
                updateConnectionLabels()
            }
        }
    }

    internal fun requestStart() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), notificationPermissionRequest)
        } else startServiceExplicitly()
    }

    internal fun startServiceExplicitly() {
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


    internal fun hasListener(): Boolean = try {
        val setting = Settings.Secure.getString(contentResolver, "enabled_notification_listeners").orEmpty()
        setting.split(':').any {
            ComponentName.unflattenFromString(it) == ComponentName(this, NotificationCaptureService::class.java)
        }
    } catch (_: Exception) { false }

    internal fun localIps(): String = try {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { iface -> iface.inetAddresses.toList().filterIsInstance<Inet4Address>()
                .map { "${iface.name}: ${it.hostAddress}" } }
            .joinToString(" · ")
    } catch (_: Exception) { "" }

    internal fun formatDate(timestamp: Long): String = SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault()).format(Date(timestamp))

    internal fun message(message: String) {
        val (wrapper, layout) = dialogWrapper()
        layout.addView(label(message, 15f).apply { setPadding(0, dp(6), 0, dp(8)) })
        AlertDialog.Builder(this)
            .setView(wrapper)
            .setPositiveButton("OK", null)
            .show()
    }
}
