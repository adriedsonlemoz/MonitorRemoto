package br.dev.monitorremoto.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.content.res.Configuration
import android.content.pm.PackageManager
import android.Manifest
import android.os.Build
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import br.dev.monitorremoto.core.LocationShareStore
import br.dev.monitorremoto.core.MonitorNotificationPolicy
import br.dev.monitorremoto.core.SettingsStore
import br.dev.monitorremoto.service.LocationShareService
import br.dev.monitorremoto.service.MonitorService
import br.dev.monitorremoto.transport.AuthenticatedAccess
import br.dev.monitorremoto.capture.ScreenFrameStore
import br.dev.monitorremoto.capture.CameraFrameStore

/** Detalhes locais do serviço, abertos somente após toque na notificação. */
class MonitorStatusActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var container: LinearLayout
    private var visible = false
    private val refresh = object : Runnable {
        override fun run() {
            if (!visible) return
            render()
            handler.postDelayed(this, 5_000L)
        }
    }
    private fun dp(n: Int): Int = (n * resources.displayMetrics.density).toInt()
    private val dark: Boolean get() = when (SettingsStore.theme(this)) {
        "dark" -> true
        "system" -> (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        else -> false
    }
    private val backgroundColor get() = if (dark) Color.rgb(13, 24, 31) else Color.rgb(245, 248, 250)
    private val panelColor get() = if (dark) Color.rgb(30, 47, 58) else Color.WHITE
    private val primary get() = if (dark) Color.rgb(239, 247, 248) else Color.rgb(20, 44, 54)
    private val secondary get() = if (dark) Color.rgb(165, 187, 196) else Color.rgb(99, 117, 126)
    private val accent = Color.rgb(23, 149, 138)
    private fun round(fill: Int): GradientDrawable = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(16).toFloat()
    }
    private fun line(text: String, size: Float = 14f, bold: Boolean = false, muted: Boolean = false) =
        TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(if (muted) secondary else primary)
            if (bold) setTypeface(null, Typeface.BOLD)
            setPadding(0, dp(4), 0, dp(4))
        }
    private fun gap(px: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(px)) }
    private fun panel(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = round(panelColor)
        setPadding(dp(15), dp(12), dp(15), dp(12))
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = backgroundColor
        window.navigationBarColor = backgroundColor
        window.decorView.systemUiVisibility = if (dark) 0 else
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        val scroll = ScrollView(this).apply { setBackgroundColor(backgroundColor) }
        container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(24))
        }
        scroll.addView(container)
        setContentView(scroll)
    }
    override fun onResume() {
        super.onResume()
        visible = true
        handler.removeCallbacks(refresh)
        refresh.run()
    }
    override fun onPause() {
        visible = false
        handler.removeCallbacks(refresh)
        super.onPause()
    }
    private fun render() {
        container.removeAllViews()
        val heading = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val shield = android.widget.ImageView(this).apply {
            setImageResource(br.dev.monitorremoto.R.drawable.ic_shield)
            imageTintList = android.content.res.ColorStateList.valueOf(accent)
            contentDescription = null
        }
        heading.addView(shield, LinearLayout.LayoutParams(dp(26), dp(26)))
        heading.addView(View(this), LinearLayout.LayoutParams(dp(8), 1))
        heading.addView(line("Estado do Monitor Remoto", 20f, bold = true))
        container.addView(heading)
        container.addView(line("Informações locais do aparelho monitorado", 12f, muted = true))
        container.addView(gap(12))
        val peers = AuthenticatedAccess.recent()
        val active = MonitorService.isServiceActive && SettingsStore.isMonitorEnabled(this)
        val state = MonitorNotificationPolicy.State(
            serviceActive = active,
            serverReady = MonitorService.isRunning,
            recentAccessCount = peers.size,
            locationActive = LocationShareService.running && LocationShareStore.active(),
            screenActive = ScreenFrameStore.sharing,
            cameraActive = CameraFrameStore.isActive(),
            alertsEnabled = SettingsStore.localHealthAlerts(this) &&
                (Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
        )
        container.addView(panel().apply {
            addView(line(MonitorNotificationPolicy.headline(state), 16f, bold = true))
            addView(line("Recursos: ${MonitorNotificationPolicy.modules(state)}", 12.5f, muted = true))
        })
        container.addView(gap(10))
        container.addView(panel().apply {
            addView(line("Acessos autenticados recentes (${peers.size})", 15f, bold = true))
            if (peers.isEmpty()) addView(line("Nenhum acesso nos últimos 45 segundos.", 12.5f, muted = true))
            peers.forEach { peer ->
                val name = peer.name.ifBlank { "Não informado" }.take(48)
                addView(line("Nome informado: $name", 13.5f))
                addView(line("IP observado: ${peer.address.take(64)}", 12f, muted = true))
                addView(line("Método informado: ${peer.method.take(32)}", 12f, muted = true))
                addView(line("Última consulta: " + android.text.format.DateFormat.format("HH:mm:ss", peer.lastSeenMs),
                    12f, muted = true))
            }
            addView(gap(3))
            addView(line("O nome é declarado pelo controlador, não uma identidade verificada. " +
                "A comunicação ocorre por consultas criptografadas, sem conexão permanente.", 11.5f, muted = true))
        })
        container.addView(gap(10))
        val actions = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val open = line("ABRIR MONITORADO", 13f, bold = true).apply {
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = round(accent)
            setPadding(dp(14), dp(13), dp(14), dp(13))
            isClickable = true
            isFocusable = true
            contentDescription = "Abrir painel Monitorado"
            setOnClickListener {
                startActivity(Intent(this@MonitorStatusActivity, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_OPEN_MONITOR, true)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
                finish()
            }
        }
        actions.addView(open, LinearLayout.LayoutParams(0, -2, 1f))
        container.addView(actions)
    }
}
