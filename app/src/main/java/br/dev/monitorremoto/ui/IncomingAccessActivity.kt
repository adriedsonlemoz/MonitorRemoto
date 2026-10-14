package br.dev.monitorremoto.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.res.Configuration
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import br.dev.monitorremoto.R
import br.dev.monitorremoto.core.SettingsStore
import br.dev.monitorremoto.service.MonitorService
import br.dev.monitorremoto.transport.AuthenticatedAccess
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Tela dedicada, visível somente quando o usuário está dentro do aplicativo.
 * Quando estiver em segundo plano, o serviço usa uma notificação normal do Android.
 * Não significa controle remoto da tela nem conexão TCP persistente.
 */
class IncomingAccessActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private var foreground = false
    private var leaving = false
    private val onChanged: () -> Unit = { handler.post { if (foreground) refreshAccess() } }
    private val tick = object : Runnable {
        override fun run() {
            if (!foreground) return
            refreshAccess()
            handler.postDelayed(this, 2_000L)
        }
    }

    internal fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private val dark: Boolean get() = when (SettingsStore.theme(this)) {
        "dark" -> true
        "system" -> (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        else -> false
    }
    internal val background get() = if (dark) Color.rgb(12, 25, 35) else Color.rgb(244, 248, 250)
    internal val surface get() = if (dark) Color.rgb(27, 44, 56) else Color.WHITE
    internal val ink get() = if (dark) Color.rgb(243, 248, 251) else Color.rgb(21, 39, 48)
    internal val secondary get() = if (dark) Color.rgb(177, 194, 203) else Color.rgb(89, 108, 118)
    internal val accent get() = if (dark) Color.rgb(92, 218, 191) else Color.rgb(20, 158, 139)
    internal val divider get() = if (dark) Color.rgb(60, 79, 89) else Color.rgb(218, 229, 235)
    internal lateinit var root: LinearLayout

    internal fun shape(color: Int, radius: Int = 20, border: Boolean = false) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
        if (border) setStroke(dp(1), divider)
    }
    internal fun text(s: String, size: Float, bold: Boolean = false, subtle: Boolean = false) = TextView(this).apply {
        text = s; textSize = size
        setTextColor(if (subtle) secondary else ink)
        if (bold) setTypeface(null, Typeface.BOLD)
        includeFontPadding = false
    }
    internal fun icon(id: Int, tint: Int, size: Int) = ImageView(this).apply {
        setImageResource(id)
        imageTintList = ColorStateList.valueOf(tint)
        layoutParams = LinearLayout.LayoutParams(dp(size), dp(size))
        contentDescription = null
    }
    internal fun gap(v: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(v)) }
    internal fun action(title: String, filled: Boolean, onClick: () -> Unit) = Button(this).apply {
        text = title
        textSize = 13.5f
        isAllCaps = false
        setTextColor(if (filled) Color.WHITE else ink)
        background = shape(if (filled) Color.rgb(186, 67, 58) else surface, 15, !filled)
        minHeight = dp(54)
        setOnClickListener { onClick() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(if (dark) android.R.style.Theme_Material_NoActionBar else android.R.style.Theme_Material_Light_NoActionBar)
        super.onCreate(savedInstanceState)
        window.statusBarColor = background
        window.navigationBarColor = background
        window.decorView.systemUiVisibility = if (dark) 0 else
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        val wrapper = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(this@IncomingAccessActivity.background)
        }
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(26))
        }
        wrapper.addView(root)
        setContentView(wrapper)
    }

    override fun onResume() {
        super.onResume()
        foreground = true
        AuthenticatedAccess.subscribe(onChanged)
        handler.removeCallbacks(tick)
        refreshAccess()
        handler.postDelayed(tick, 2_000L)
    }

    override fun onPause() {
        foreground = false
        AuthenticatedAccess.unsubscribe(onChanged)
        handler.removeCallbacks(tick)
        super.onPause()
    }

    internal fun fmt(ms: Long) = SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault()).format(Date(ms))

    private fun refreshAccess() {
        if (!foreground || leaving) return
        val peers = AuthenticatedAccess.recent()
        if (peers.isEmpty() || !MonitorService.isRunning || !SettingsStore.isMonitorEnabled(this)) {
            returnToMonitor()
            return
        }
        renderIncomingAccessScreen(peers) { confirmStop() }
    }

    private fun confirmStop() {
        val confirmation = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(10))
            background = shape(surface, 20, true)
            addView(icon(R.drawable.ic_shield, accent, 30))
            addView(gap(12))
            addView(text("Interromper todos os acessos?", 19f, bold = true))
            addView(gap(8))
            addView(text("O monitoramento será desligado para todos os controladores. Ele só voltará a compartilhar dados após uma nova ativação explícita.", 13.5f, subtle = true))
        }
        AlertDialog.Builder(this)
            .setView(confirmation)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Parar monitoramento") { _, _ ->
                SettingsStore.setMonitorEnabled(this, false)
                stopService(Intent(this, MonitorService::class.java))
                returnToMonitor()
            }.show()
    }

    private fun returnToMonitor() {
        if (leaving) return
        leaving = true
        if (foreground && !isFinishing) {
            startActivity(Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_MONITOR, true)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
        }
        finish()
    }
}
