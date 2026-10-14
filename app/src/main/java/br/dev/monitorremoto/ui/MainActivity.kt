package br.dev.monitorremoto.ui

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.os.SystemClock
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import br.dev.monitorremoto.R
import br.dev.monitorremoto.capture.NotificationCaptureService
import br.dev.monitorremoto.core.RecentDevices
import br.dev.monitorremoto.core.SettingsStore
import br.dev.monitorremoto.security.SecretVault
import br.dev.monitorremoto.service.MonitorService
import br.dev.monitorremoto.transport.AppVersion
import br.dev.monitorremoto.transport.AuthenticatedAccess
import java.net.Inet4Address
import java.net.NetworkInterface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Interface Android Views sem dependências adicionais; ambos os modos no mesmo APK. */
class MainActivity : Activity() {
    internal var activeUiDialog: AlertDialog? = null

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
    internal val handler = Handler(Looper.getMainLooper())
    internal val remoteConnection by lazy { RemoteConnectionManager(applicationContext) }
    internal val pairingController = PairingController()
    internal var mode = 2 // 2: início, 0: monitorado, 1: controle
    internal val cameraPermissionRequest = 4317
    internal val qrScanRequest = 4318
    private val notificationPermissionRequest = 101
    internal val phoneNumberPermissionRequest = 103
    internal var active = false
    internal var resultBox: LinearLayout? = null
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
    internal var currentStatus: TextView? = null
    internal var currentDeviceTitle: TextView? = null
    internal var currentUpdated: TextView? = null
    internal var currentConnectButton: Button? = null
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
        activeUiDialog?.dismiss()
        activeUiDialog = null
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
        if (mode == 1 && remoteConnection.state == "connected") {
            remoteConnection.pause()
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
        header.addView(headerIconButton(R.drawable.ic_phone, "Home: voltar à tela inicial") { navigateHome() },
            LinearLayout.LayoutParams(dp(45), dp(45)))
        header.addView(View(this), LinearLayout.LayoutParams(dp(8), 1))
        header.addView(headerIconButton(R.drawable.ic_settings, "Configurações") { showSettingsDialog() },
            LinearLayout.LayoutParams(dp(45), dp(45)))
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
            phoneNumberPermissionRequest -> {
                SettingsStore.setSharePhoneNumber(this, granted)
                message(if (granted) "Número da linha autorizado para consultas remotas autenticadas. Pode desativar em Permissões."
                    else "Número da linha não será compartilhado.")
            }
        }
    }

    @Deprecated("Compatibilidade com a Activity Android nativa")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != qrScanRequest || resultCode != Activity.RESULT_OK) return
        val scanned = data?.getStringExtra(QrScanActivity.EXTRA_QR_RESULT) ?: return
        confirmQrCode(scanned)
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
        showAppNotice("Monitor Remoto", message)
    }
}
