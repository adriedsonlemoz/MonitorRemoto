package br.dev.monitorremoto.ui

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
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
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
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
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import br.dev.monitorremoto.capture.NotificationCaptureService
import br.dev.monitorremoto.core.BatteryReader
import br.dev.monitorremoto.core.RecentDevices
import br.dev.monitorremoto.core.Ipv4Suggestion
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
    private val bg get() = if (darkTheme()) Color.rgb(14, 22, 35) else Color.rgb(247, 249, 252)
    private val surface get() = if (darkTheme()) Color.rgb(28, 41, 58) else Color.WHITE
    private val ink get() = if (darkTheme()) Color.rgb(233, 239, 247) else Color.rgb(23, 34, 48)
    private val dim get() = if (darkTheme()) Color.rgb(167, 184, 203) else Color.rgb(92, 109, 128)
    private val highlight get() = if (darkTheme()) Color.rgb(90, 214, 188) else Color.rgb(24, 166, 146)
    private val highlightText get() = if (darkTheme()) Color.rgb(14, 22, 35) else Color.WHITE
    private val lineColor get() = if (darkTheme()) Color.rgb(47, 64, 83) else Color.rgb(224, 231, 239)
    private val secondaryAccent get() = if (darkTheme()) Color.rgb(54, 116, 171) else Color.rgb(38, 113, 174)
    private val fieldColor get() = if (darkTheme()) Color.rgb(22, 34, 50) else Color.rgb(242, 246, 250)
    private lateinit var content: LinearLayout
    private val handler = Handler(Looper.getMainLooper())
    private var mode = 0 // 0: monitorado, 1: controle
    private val cameraPermissionRequest = 4317
    private val qrScanRequest = 4318
    private val notificationPermissionRequest = 101
    private var active = false
    private var fetching = false
    private var fetchingHost: String = ""
    private var resultBox: LinearLayout? = null
    private var pairingInProgress = false
    private var lastResult: JSONObject? = null
    private var lastResultHost: String = ""
    private var lastSuccessfulMs: Long = 0L
    private var connectionState: String = "idle" // idle / loading / connected / offline
    private var connectionMessage: String? = null
    private var currentStatus: TextView? = null
    private var currentDeviceTitle: TextView? = null
    private var currentUpdated: TextView? = null
    private var currentConnectButton: Button? = null
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
        mode = savedInstanceState?.getInt("active_mode", 0) ?: 0
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
        render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("active_mode", mode)
        super.onSaveInstanceState(outState)
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
        // Uma resposta anterior não significa que ainda haja conexão ao voltar ao app.
        if (mode == 1 && connectionState == "connected") {
            connectionState = "idle"
            updateConnectionLabels()
        }
        super.onPause()
    }

    private fun dp(value: Int) = (resources.displayMetrics.density * value).toInt()
    private fun shape(color: Int, corner: Int = 13, stroke: Boolean = false) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(corner).toFloat()
        if (stroke) setStroke(dp(1), lineColor)
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
            setTextColor(if (emphasis) highlightText else ink)
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
        textSize = 16f
        setTextColor(ink)
        setHintTextColor(dim)
        background = shape(fieldColor, 14, true)
        setSingleLine(true)
        minHeight = dp(56)
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(16), dp(14), dp(16), dp(14))
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        val params = LinearLayout.LayoutParams(-1, dp(56))
        params.bottomMargin = dp(10)
        layoutParams = params
    }

    private fun actionButton(title: String, emphasis: Boolean = false, onClick: () -> Unit): Button =
        Button(this).apply {
            text = title; isAllCaps = false; textSize = 13f
            setTextColor(if (emphasis) highlightText else ink)
            background = shape(if (emphasis) highlight else surface, 12, !emphasis)
            setPadding(dp(9), 0, dp(9), 0)
            minHeight = dp(42)
            setOnClickListener { onClick() }
        }

    private fun panel(pad: Int = 16): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = shape(surface, 18, true)
        setPadding(dp(pad), dp(pad), dp(pad), dp(pad))
    }

    private fun sectionGap(parent: LinearLayout, pixels: Int = 12) {
        parent.addView(View(this), LinearLayout.LayoutParams(1, dp(pixels)))
    }

    private fun infoChip(value: String, accent: Boolean = false): TextView = TextView(this).apply {
        text = value
        textSize = 12f
        setTextColor(if (accent) highlightText else ink)
        background = shape(if (accent) highlight else fieldColor, 999, !accent)
        setPadding(dp(12), dp(8), dp(12), dp(8))
    }

    private fun dialogWrapper(): Pair<ScrollView, LinearLayout> {
        val wrapper = ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(10), dp(18), dp(10))
        }
        wrapper.addView(layout)
        return wrapper to layout
    }

    private fun dialogIntro(parent: LinearLayout, text: String) {
        if (text.isBlank()) return
        parent.addView(label(text, 12.5f, muted = true).apply { setPadding(0, 0, 0, dp(6)) })
    }

    private fun dialogCardRow(
        title: String,
        subtitle: String = "",
        trailing: String = "›",
        accent: Boolean = false,
        onClick: (() -> Unit)? = null
    ): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = shape(fieldColor, 16, true)
        setPadding(dp(14), dp(13), dp(14), dp(13))
        val textBlock = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
        textBlock.addView(label(title, 15f, strong = true).apply {
            setTextColor(if (accent) highlight else ink)
            setPadding(0, 0, 0, if (subtitle.isBlank()) 0 else dp(2))
        })
        if (subtitle.isNotBlank()) textBlock.addView(label(subtitle, 11.5f, muted = true).apply { setPadding(0, 0, 0, 0) })
        addView(textBlock, LinearLayout.LayoutParams(0, -2, 1f))
        if (trailing.isNotBlank()) addView(label(trailing, 18f, strong = accent, muted = !accent).apply { gravity = Gravity.CENTER_VERTICAL })
        if (onClick != null) {
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
    }

    private fun dialogMetricRow(name: String, value: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, dp(5), 0, dp(5))
        addView(label(name, 12f, muted = true), LinearLayout.LayoutParams(0, -2, 1f))
        addView(label(value, 14f, strong = true))
    }


    private fun render() {
        content.removeAllViews()
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val title = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        title.addView(label("MONITOR REMOTO", 18f, strong = true).apply {
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        title.addView(label(if (mode == 1) "Painel de controle · privado e criptografado" else
            "Modo monitorado · privado e criptografado", 10.5f, muted = true).apply {
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        header.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(actionButton("⌂") { navigateHome() }.apply {
            contentDescription = "Início: voltar ao modo monitorado"
            textSize = 22f
        }, LinearLayout.LayoutParams(dp(45), dp(43)))
        header.addView(View(this), LinearLayout.LayoutParams(dp(7), 1))
        header.addView(actionButton("⚙") { showSettingsDialog() }.apply {
            contentDescription = "Configurações"
        }, LinearLayout.LayoutParams(dp(45), dp(43)))
        content.addView(header)

        val metaRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        metaRow.addView(infoChip("v0.1.0-alpha.16"))
        metaRow.addView(View(this), LinearLayout.LayoutParams(dp(8), 1))
        val monitorActive = SettingsStore.isMonitorEnabled(this) && MonitorService.isRunning
        metaRow.addView(infoChip(if (monitorActive) "Monitorado ativo" else "Monitorado inativo", accent = monitorActive))
        content.addView(metaRow)

        gap(16)
        if (mode == 0) renderMonitor() else {
            renderController()
            content.post { if (active && mode == 1) refreshRemote(silent = true) }
        }
        gap(20)
        addText("Somente em aparelhos próprios ou autorizados. Notificações permitidas são temporárias e filtradas.", 11f, muted = true)
    }

    /** Home sempre retorna ao modo Monitorado, sem desligar o serviço ou revogar pareamentos. */
    private fun navigateHome() {
        if (mode != 0) { mode = 0; render() }
    }

    private fun showAboutDialog() {
        val (wrapper, layout) = dialogWrapper()
        dialogIntro(layout, "Resumo rápido do foco atual do projeto.")
        val card = panel(14)
        card.addView(dialogMetricRow("Versão", "0.1.0-alpha.16"))
        card.addView(dialogMetricRow("Tela inicial", "Modo monitorado"))
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

    private fun showSettingsDialog() {
        showOptionDialog(
            "Configurações",
            listOf(
                OptionItem("Aparência", "Tema claro, escuro ou automático") { showAppearanceDialog() },
                OptionItem("Permissões e notificações", "Acesso às notificações e apps autorizados") { showPermissionSettings() },
                OptionItem("Segurança e pareamento", "Mostrar chave ou revogar acessos") { showSecuritySettings() },
                OptionItem("Serviço e inicialização", "Reiniciar serviço e início automático") { showServiceSettings() },
                OptionItem("Diagnóstico", "IP, porta, estado do serviço e permissões") { showDiagnostics() },
                OptionItem("Sobre o aplicativo", "Versão, objetivos e segurança") { showAboutDialog() },
            )
        )
    }

    private fun showOptionDialog(title: String, items: List<OptionItem>) {
        val (wrapper, layout) = dialogWrapper()
        dialogIntro(layout, "Ajustes agrupados para reduzir a quantidade de informações na tela principal.")
        var popup: AlertDialog? = null
        items.forEachIndexed { index, item ->
            layout.addView(dialogCardRow(item.title, item.subtitle) {
                popup?.dismiss(); item.action()
            })
            if (index != items.lastIndex) sectionGap(layout, 8)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setView(wrapper)
            .setNegativeButton("Fechar", null)
            .create()
        popup = dialog
        dialog.show()
    }

    private fun showAppearanceDialog() {
        val options = listOf(
            Triple("light", "Claro (padrão)", "Visual mais leve para uso diário"),
            Triple("dark", "Escuro", "Mais confortável em ambientes pouco iluminados"),
            Triple("system", "Acompanhar o sistema", "Segue o tema configurado no Android")
        )
        val current = SettingsStore.theme(this)
        val (wrapper, layout) = dialogWrapper()
        dialogIntro(layout, "Escolha a aparência padrão do aplicativo.")
        var popup: AlertDialog? = null
        options.forEachIndexed { index, option ->
            val selected = option.first == current
            layout.addView(dialogCardRow(option.second, option.third, if (selected) "●" else "○", accent = selected) {
                if (option.first != SettingsStore.theme(this)) {
                    SettingsStore.setTheme(this, option.first)
                    popup?.dismiss()
                    recreate()
                } else popup?.dismiss()
            })
            if (index != options.lastIndex) sectionGap(layout, 8)
        }
        val dialog = AlertDialog.Builder(this).setTitle("Aparência").setView(wrapper).setNegativeButton("Fechar", null).create()
        popup = dialog
        dialog.show()
    }

    private fun showPermissionSettings() {
        val state = if (hasListener()) "Autorizado" else "Sem acesso"
        val count = SettingsStore.notificationAllowlist(this).size
        val (wrapper, layout) = dialogWrapper()
        dialogIntro(layout, "O monitoramento da bateria funciona sem esta permissão. As notificações só são enviadas para apps autorizados e não financeiros.")
        layout.addView(dialogCardRow("Acesso às notificações", state, "›") {
            AlertDialog.Builder(this).setTitle("Acesso a notificações (opcional)")
                .setMessage("O Android concede acesso geral às notificações, que podem conter informações sensíveis. Apenas aplicativos escolhidos são transmitidos, com filtros não infalíveis. Você pode revogar o acesso nas Configurações Android. O monitoramento da bateria não exige essa permissão.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Configurar no Android") { _, _ ->
                    startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }.show()
        })
        sectionGap(layout, 8)
        layout.addView(dialogCardRow("Aplicativos autorizados", "$count selecionado(s)", "›") { selectNotificationApps() })
        AlertDialog.Builder(this)
            .setTitle("Permissões e notificações")
            .setView(wrapper)
            .setNegativeButton("Fechar", null)
            .show()
    }

    private fun showSecuritySettings() {
        val (wrapper, layout) = dialogWrapper()
        dialogIntro(layout, "As credenciais são protegidas pelo Android Keystore. Compartilhe a chave somente entre seus próprios aparelhos.")
        layout.addView(dialogCardRow("Mostrar/copiar chave de pareamento", "Usar conexão manual com IP e chave", "›") { showMonitorKey() })
        sectionGap(layout, 8)
        layout.addView(dialogCardRow("Trocar chave e revogar acessos", "Todos os controladores precisarão parear novamente", "›") { confirmRotateMonitorKey() })
        AlertDialog.Builder(this)
            .setTitle("Segurança e pareamento")
            .setView(wrapper)
            .setNegativeButton("Fechar", null)
            .show()
    }

    private fun showMonitorKey() {
        try {
            val key = CryptoBox.encode(SecretVault.getOrCreateMonitorKey(this))
            val dialog = AlertDialog.Builder(this)
                .setTitle("Chave secreta de pareamento")
                .setMessage("Esta chave dá acesso aos dados remotos. Não compartilhe com terceiros.\n\n$key")
                .setPositiveButton("Copiar") { _, _ ->
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Chave Monitor Remoto", key))
                    message("Chave copiada. Limpe a área de transferência depois do pareamento.")
                }.setNegativeButton("Fechar", null).create()
            dialog.show()
            dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } catch (error: Exception) { message("Keystore indisponível: ${error.message}") }
    }

    private fun confirmRotateMonitorKey() {
        AlertDialog.Builder(this).setTitle("Revogar acessos?")
            .setMessage("Todos os controladores precisarão parear novamente. A chave anterior deixará de funcionar.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Trocar chave") { _, _ ->
                SecretVault.save(this, SecretVault.MONITOR_KEY, CryptoBox.newPairingKey())
                PairingSession.revoke()
                message("Chave substituída. Os acessos anteriores foram revogados.")
            }.show()
    }

    private fun showServiceSettings() {
        val autostart = SettingsStore.autoStartEnabled(this)
        val (wrapper, layout) = dialogWrapper()
        dialogIntro(layout, "Controle o comportamento do serviço monitorado neste aparelho.")
        layout.addView(dialogCardRow("Reiniciar serviço", "Reativa o monitoramento se ele já estiver autorizado", "›") {
            if (SettingsStore.isMonitorEnabled(this)) startServiceExplicitly()
            else message("Ative o monitoramento antes de iniciar o serviço.")
        })
        sectionGap(layout, 8)
        layout.addView(dialogCardRow(if (autostart) "Desativar início após reiniciar" else "Ativar início após reiniciar", "Depende das restrições do Android e do fabricante", "›") {
            SettingsStore.setAutoStart(this, !autostart)
            message("Preferência salva. A inicialização depende das restrições do Android e do fabricante.")
        })
        AlertDialog.Builder(this)
            .setTitle("Serviço e inicialização")
            .setView(wrapper)
            .setNegativeButton("Fechar", null)
            .show()
    }

    private fun showDiagnostics() {
        val running = MonitorService.isRunning
        val (wrapper, layout) = dialogWrapper()
        dialogIntro(layout, "Resumo técnico do serviço local e das permissões relacionadas.")
        val card = panel(14)
        card.addView(dialogMetricRow("Serviço", if (running) "Ativo" else "Parado"))
        card.addView(dialogMetricRow("Monitoramento autorizado", if (SettingsStore.isMonitorEnabled(this)) "Sim" else "Não"))
        card.addView(dialogMetricRow("Porta TCP", WireProtocol.PORT.toString()))
        card.addView(dialogMetricRow("IP local", localIps().ifBlank { "Indisponível" }))
        card.addView(dialogMetricRow("Notificações", if (hasListener()) "Autorizadas" else "Sem acesso"))
        card.addView(dialogMetricRow("Apps permitidos", SettingsStore.notificationAllowlist(this).size.toString()))
        layout.addView(card)
        AlertDialog.Builder(this)
            .setTitle("Diagnóstico")
            .setView(wrapper)
            .setNegativeButton("Fechar", null)
            .show()
    }

    private fun renderMonitor() {
        val enabled = SettingsStore.isMonitorEnabled(this)
        val running = MonitorService.isRunning
        val card = panel(18)
        card.addView(label("Este aparelho", 17f, strong = true))
        val state = when {
            !enabled -> "● Monitoramento inativo"
            running -> "● Monitoramento ativo"
            else -> "● Serviço sem execução"
        }
        card.addView(label(state, 15f, strong = true).apply {
            setTextColor(if (enabled && running) highlight else if (enabled) Color.rgb(190, 88, 61) else dim)
        })
        val preferred = preferredLocalAddress()
        card.addView(label("IP: ${preferred ?: "rede local indisponível"}", 14f))
        card.addView(label("Dados disponíveis somente para aparelhos autorizados.", 12f, muted = true))
        content.addView(card)
        gap(14)
        if (enabled && running) {
            content.addView(button("▣  Gerar QR Code", emphasis = true) { chooseQrAddress() })
            content.addView(button("⚿  Mostrar minhas informações") { showMyConnectionInfo() })
            content.addView(button("Interromper monitoramento") {
                SettingsStore.setMonitorEnabled(this, false)
                stopService(Intent(this, MonitorService::class.java))
                render()
            })
        } else {
            content.addView(button(if (enabled) "Reiniciar monitoramento" else "Ativar monitoramento", emphasis = true) {
                if (enabled) startServiceExplicitly() else requestStart()
            })
            content.addView(button("⚿  Mostrar minhas informações") { showMyConnectionInfo() })
        }
        gap(8)
        renderRecentDevicesHome()
        gap(10)
        addText("Permissões, chaves e detalhes técnicos: ⚙ Configurações.", 12f, muted = true)
    }

    /** Lista pequena no início; gerenciador detalhado permanece na escolha Conectar. */
    private fun renderRecentDevicesHome() {
        val recent = RecentDevices.list(this)
        val card = panel(14)
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(label("Conectados recentemente", 14f, strong = true).apply {
            maxLines = 2
        }, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(actionButton("Ver todos ›") { showConnectionChooser() }.apply {
            textSize = 12f
        }, LinearLayout.LayoutParams(dp(98), dp(40)))
        card.addView(row)
        if (recent.isEmpty()) {
            card.addView(label("Nenhum aparelho salvo ainda.", 12f, muted = true))
        } else {
            recent.take(2).forEach { device ->
                sectionGap(card, 7)
                card.addView(dialogCardRow(device.name, device.host, trailing = "›") {
                    showSavedDeviceOptions(device)
                })
            }
        }
        sectionGap(card, 8)
        card.addView(actionButton("Conectar outro aparelho", emphasis = true) {
            showConnectionChooser()
        }, LinearLayout.LayoutParams(-1, dp(44)))
        content.addView(card)
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
    private fun preferredLocalAddress(): String? {
        val ips = availableLocalIps()
        return (ips.firstOrNull { it.first.startsWith("wlan", ignoreCase = true) }
            ?: ips.firstOrNull())?.second
    }

    /** IP + segredo só são lidos após solicitação explícita, e não ficam na tela principal. */
    private fun showMyConnectionInfo() {
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
        var shown = false
        lateinit var revealButton: Button
        revealButton = actionButton("Mostrar chave") {
            shown = !shown
            keyLabel.text = if (shown) key else "••••••••••••••••"
            revealButton.text = if (shown) "Ocultar chave" else "Mostrar chave"
        }
        card.addView(revealButton, LinearLayout.LayoutParams(-1, dp(42)))
        layout.addView(card)
        sectionGap(layout, 12)
        val payload = "Monitor Remoto\nIP: $ip\nChave: $key"
        layout.addView(actionButton("Copiar informações", emphasis = true) {
            val clip = ClipData.newPlainText("Monitor Remoto: IP e chave", payload)
            if (Build.VERSION.SDK_INT >= 33) {
                clip.description.extras = PersistableBundle().apply {
                    putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                }
            }
            (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
            message("IP e chave copiados. Apague o conteúdo da área de transferência após o uso.")
        }, LinearLayout.LayoutParams(-1, dp(46)))
        sectionGap(layout, 8)
        layout.addView(actionButton("Compartilhar") {
            AlertDialog.Builder(this)
                .setTitle("Compartilhar credencial?")
                .setMessage("O compartilhamento incluirá IP e chave de pareamento. Envie somente para um aparelho ou pessoa autorizada. Não publique em grupos ou redes sociais.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Escolher aplicativo") { _, _ ->
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, payload)
                    }
                    try { startActivity(Intent.createChooser(intent, "Compartilhar informações de conexão")) }
                    catch (_: Exception) { message("Nenhum aplicativo de compartilhamento disponível.") }
                }.show()
        }, LinearLayout.LayoutParams(-1, dp(46)))
        val dialog = AlertDialog.Builder(this)
            .setTitle("Minhas informações")
            .setView(wrapper)
            .setNegativeButton("Fechar", null)
            .create()
        dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        dialog.show()
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
        val host = SettingsStore.getRemoteHost(this)
        val card = panel(18)
        val savedDevice = RecentDevices.list(this).firstOrNull { it.host == host }
        val lastDeviceName = lastResult?.optJSONObject("battery")?.optString("device")
            ?.takeIf { lastResultHost == host && it.isNotBlank() }

        val heading = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.TOP }
        currentDeviceTitle = label(
            if (savedDevice?.customName == true) savedDevice.name else lastDeviceName ?: savedDevice?.name ?: "Nenhum aparelho selecionado",
            18f,
            strong = true
        ).apply { maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END }
        heading.addView(currentDeviceTitle, LinearLayout.LayoutParams(0, -2, 1f))
        heading.addView(View(this), LinearLayout.LayoutParams(dp(8), 1))
        currentConnectButton = actionButton("Conectar", emphasis = true) {
            val actualState = effectiveConnectionState()
            if (actualState == "connected" || actualState == "loading") disconnectCurrent()
            else showConnectionChooser()
        }.apply { textSize = 12f; minWidth = 0; setPadding(dp(5), 0, dp(5), 0) }
        heading.addView(currentConnectButton, LinearLayout.LayoutParams(dp(100), dp(44)))
        card.addView(heading)
        currentStatus = label("", 13f, strong = true)
        card.addView(currentStatus)
        currentUpdated = label("", 12f, muted = true).apply { setPadding(0, dp(2), 0, dp(6)) }
        card.addView(currentUpdated)

        val refreshRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END }
        refreshRow.addView(actionButton("↻ Atualizar") { refreshRemote(silent = false) }.apply {
            background = shape(secondaryAccent, 12)
            setTextColor(Color.WHITE)
            textSize = 12f
        }, LinearLayout.LayoutParams(dp(108), dp(43)))
        card.addView(refreshRow)
        sectionGap(card, 12)

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
        SettingsStore.setRemoteHost(this, "")
        fetching = false
        fetchingHost = ""
        lastResult = null
        lastResultHost = ""
        lastSuccessfulMs = 0L
        connectionState = "idle"
        connectionMessage = "Seleção removida."
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
                "Última atualização: ${formatDate(lastSuccessfulMs)}" +
                    if (actualState == "offline") " · dados anteriores" else ""
            else -> connectionMessage ?: "Aguardando resposta de $host"
        }
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
    private fun showConnectionChooser() {
        RecentDevices.migrateLegacy(this)
        val (wrapper, layout) = dialogWrapper()
        dialogIntro(layout, "Escolha como conectar ao outro celular.")
        var chooser: AlertDialog? = null
        layout.addView(dialogCardRow("▣  QR Code", "Aponte a câmera para o código temporário do aparelho", "›", accent = true) {
            chooser?.dismiss()
            startQrScanner()
        })
        sectionGap(layout, 9)
        layout.addView(dialogCardRow("⌨  Manualmente", "Informar endereço IP e chave de pareamento", "›") {
            chooser?.dismiss()
            showManualConnectionDialog()
        })
        sectionGap(layout, 9)
        layout.addView(dialogCardRow("Inserir código QR", "Alternativa sem câmera: colar o conteúdo do QR", "›") {
            chooser?.dismiss()
            askQrText()
        })
        sectionGap(layout, 16)
        layout.addView(label("Aparelhos anteriores", 16f, strong = true))
        layout.addView(label("Use os botões de cada aparelho para conectar, renomear ou esquecer.", 12f, muted = true))
        sectionGap(layout, 7)
        val devices = RecentDevices.list(this)
        if (devices.isEmpty()) {
            layout.addView(label("Nenhum aparelho salvo. Use QR Code ou Manualmente.", 12f, muted = true))
        }
        devices.forEach { item ->
            val card = panel(12)
            card.addView(label(item.name, 14f, strong = true).apply { maxLines = 2 })
            val seen = if (item.lastSuccessMs == 0L) "Sem consulta confirmada" else "Última consulta: ${formatDate(item.lastSuccessMs)}"
            card.addView(label("${item.host} · $seen", 11f, muted = true))
            sectionGap(card, 6)
            card.addView(actionButton("Conectar", true) {
                chooser?.dismiss()
                connectSavedDevice(item)
            }, LinearLayout.LayoutParams(-1, dp(40)))
            sectionGap(card, 6)
            card.addView(actionButton("Renomear") {
                chooser?.dismiss()
                renameSavedDevice(item)
            }, LinearLayout.LayoutParams(-1, dp(40)))
            sectionGap(card, 6)
            card.addView(actionButton("Esquecer") {
                chooser?.dismiss()
                forgetSavedDevice(item)
            }.apply { setTextColor(Color.rgb(186, 78, 65)) }, LinearLayout.LayoutParams(-1, dp(40)))
            layout.addView(card)
            sectionGap(layout, 10)
        }
        chooser = AlertDialog.Builder(this).setTitle("Conectar aparelho")
            .setView(wrapper).setNegativeButton("Fechar", null).create()
        chooser.show()
    }

    private fun connectSavedDevice(item: RecentDevices.Device) {
        if (RecentDevices.key(this, item.host) == null) {
            message("A chave desse aparelho não está disponível. Faça um novo pareamento.")
            showConnectionChooser()
            return
        }
        SettingsStore.setRemoteHost(this, item.host)
        mode = 1
        lastResult = null; lastResultHost = ""; lastSuccessfulMs = 0L
        connectionState = "loading"; connectionMessage = null
        render(); refreshRemote(silent = false)
    }

    private fun renameSavedDevice(item: RecentDevices.Device) {
        val input = field("Nome do aparelho", item.name)
        AlertDialog.Builder(this).setTitle("Renomear aparelho").setView(input)
            .setNegativeButton("Cancelar") { _, _ -> showConnectionChooser() }
            .setPositiveButton("Salvar") { _, _ ->
                RecentDevices.rename(this, item.host, input.text.toString())
                render(); showConnectionChooser()
            }.show()
    }

    private fun forgetSavedDevice(item: RecentDevices.Device) {
        AlertDialog.Builder(this).setTitle("Esquecer aparelho?")
            .setMessage("Remover ${item.name} e apagar a chave de pareamento salva? Para conectar novamente será necessário parear.")
            .setNegativeButton("Cancelar") { _, _ -> showConnectionChooser() }
            .setPositiveButton("Esquecer") { _, _ ->
                val wasSelected = SettingsStore.getRemoteHost(this).equals(item.host, ignoreCase = true)
                RecentDevices.forget(this, item.host)
                if (wasSelected) {
                    lastResult = null; lastResultHost = ""; lastSuccessfulMs = 0L
                    connectionState = "idle"; connectionMessage = null
                }
                render(); showConnectionChooser()
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
            message("Falha ao abrir leitor (${e.javaClass.simpleName}). Use código QR manual ou consulte logcat.")
        }
    }

    @Deprecated("Compatibilidade com a Activity Android nativa")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != qrScanRequest || resultCode != Activity.RESULT_OK) return
        val scanned = data?.getStringExtra(QrScanActivity.EXTRA_QR_RESULT) ?: return
        confirmQrCode(scanned)
    }

    private fun askQrText() {
        val input = field("monitorremoto://pair?...", "").apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }
        AlertDialog.Builder(this)
            .setTitle("Código de pareamento temporário")
            .setMessage("Insira o conteúdo completo do QR apenas se for do seu aparelho monitorado. Não compartilhe este código.")
            .setView(input)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Validar") { _, _ -> confirmQrCode(input.text.toString().trim()) }
            .show()
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
        connectionState = "loading"; connectionMessage = null
        updateConnectionLabels()
        thread(name = "MonitorRemoto-client") {
            val result = runCatching { RemoteClient.snapshot(host, key) }
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
                    connectionMessage = "Sem resposta: ${it.javaClass.simpleName}"
                    if (!silent && lastResultHost != host) {
                        resultBox?.removeAllViews()
                        resultBox?.addView(label("Não foi possível consultar o aparelho. Confira Wi-Fi/VPN, endereço e chave.", 13f, muted = true))
                    }
                }
                updateConnectionLabels()
            }
        }
    }

    private fun displayResult(root: JSONObject, fromCache: Boolean = false) {
        val panel = resultBox ?: return
        panel.removeAllViews()
        val battery = root.optJSONObject("battery") ?: return
        val level = if (battery.isNull("levelPercent")) "—" else "${battery.optDouble("levelPercent").toInt()}%"
        val charging = if (battery.optBoolean("charging")) "Carregando" else "Descarregando"

        val topRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val batteryCard = panel(14).apply {
            addView(label("Bateria", 12f, muted = true))
            addView(label(level, 28f, strong = true))
            addView(label(charging, 12f, muted = true))
        }
        topRow.addView(batteryCard, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(8) })
        val tempCard = panel(14).apply {
            addView(label("Temperatura", 12f, muted = true))
            addView(label(if (battery.isNull("temperatureC")) "—" else
                String.format(Locale.getDefault(), "%.1f °C", battery.optDouble("temperatureC")), 22f, strong = true))
            addView(label("Estado térmico", 12f, muted = true))
        }
        topRow.addView(tempCard, LinearLayout.LayoutParams(0, -2, 1f))
        panel.addView(topRow)
        sectionGap(panel, 10)

        fun current(name: String): String = if (battery.isNull(name)) "—" else
            String.format(Locale.getDefault(), "%.0f mA", battery.optDouble(name))
        val voltage = if (battery.isNull("voltageMv")) "—" else "${battery.optInt("voltageMv")} mV"
        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        listOf(
            "Tensão" to voltage,
            "Corrente instantânea" to current("currentMa"),
            "Corrente média" to current("averageMa")
        ).forEach { (name, value) ->
            val metric = panel(13).apply {
                addView(label(name, 11.5f, muted = true))
                addView(label(value, 16f, strong = true))
            }
            grid.addView(metric, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        }
        panel.addView(grid)
        panel.addView(label("Correntes são as leituras disponíveis do Android e podem variar conforme o aparelho.", 11f, muted = true))
        if (fromCache && connectionState == "offline")
            panel.addView(label("Dados anteriores exibidos por falta de resposta atual.", 12f, muted = true))
        sectionGap(panel, 8)
        val events = root.optJSONArray("events")
        panel.addView(label("Últimos eventos (${events?.length() ?: 0})", 16f, true))
        if (events == null || events.length() == 0) {
            panel.addView(label("Nenhuma notificação autorizada capturada.", 12f, muted = true))
        } else {
            for (index in events.length() - 1 downTo 0) {
                val event = events.optJSONObject(index) ?: continue
                val item = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    background = shape(fieldColor, 12, true)
                    setPadding(dp(12), dp(8), dp(12), dp(8))
                }
                item.addView(label("${event.optString("type").uppercase(Locale.getDefault())} · ${event.optString("app")}", 11f, muted = true))
                item.addView(label(event.optString("title"), 14f, strong = true))
                item.addView(label(event.optString("preview"), 12f))
                item.addView(label(formatDate(event.optLong("whenMs")), 11f, muted = true))
                panel.addView(item, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
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
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), notificationPermissionRequest)
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
        val (wrapper, layout) = dialogWrapper()
        layout.addView(label(message, 15f).apply { setPadding(0, dp(6), 0, dp(8)) })
        AlertDialog.Builder(this)
            .setView(wrapper)
            .setPositiveButton("OK", null)
            .show()
    }
}
