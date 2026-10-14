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
import android.view.Gravity
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
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private val dark: Boolean get() = when (SettingsStore.theme(this)) {
        "dark" -> true
        "system" -> (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        else -> false
    }
    private val background get() = if (dark) Color.rgb(12, 25, 35) else Color.rgb(244, 248, 250)
    private val surface get() = if (dark) Color.rgb(27, 44, 56) else Color.WHITE
    private val ink get() = if (dark) Color.rgb(243, 248, 251) else Color.rgb(21, 39, 48)
    private val secondary get() = if (dark) Color.rgb(177, 194, 203) else Color.rgb(89, 108, 118)
    private val accent get() = if (dark) Color.rgb(92, 218, 191) else Color.rgb(20, 158, 139)
    private val divider get() = if (dark) Color.rgb(60, 79, 89) else Color.rgb(218, 229, 235)
    private lateinit var root: LinearLayout

    private fun shape(color: Int, radius: Int = 20, border: Boolean = false) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
        if (border) setStroke(dp(1), divider)
    }
    private fun text(s: String, size: Float, bold: Boolean = false, subtle: Boolean = false) = TextView(this).apply {
        text = s; textSize = size
        setTextColor(if (subtle) secondary else ink)
        if (bold) setTypeface(null, Typeface.BOLD)
        includeFontPadding = false
    }
    private fun icon(id: Int, tint: Int, size: Int) = ImageView(this).apply {
        setImageResource(id)
        imageTintList = ColorStateList.valueOf(tint)
        layoutParams = LinearLayout.LayoutParams(dp(size), dp(size))
        contentDescription = null
    }
    private fun gap(v: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(v)) }
    private fun action(title: String, filled: Boolean, onClick: () -> Unit) = Button(this).apply {
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
        val wrapper = ScrollView(this).apply { isFillViewport = true; setBackgroundColor(background) }
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
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
    private fun fmt(ms: Long) = SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault()).format(Date(ms))

    private fun refreshAccess() {
        if (!foreground || leaving) return
        val peers = AuthenticatedAccess.recent()
        if (peers.isEmpty() || !MonitorService.isRunning || !SettingsStore.isMonitorEnabled(this)) {
            returnToMonitor()
            return
        }
        val main = peers.first()
        root.removeAllViews()
        root.addView(text("MONITOR REMOTO", 13.5f, bold = true).apply { setTextColor(accent) })
        root.addView(gap(22))
        val halo = LinearLayout(this).apply {
            background = shape(if (dark) Color.rgb(32, 77, 73) else Color.rgb(220, 247, 238), 28)
            setPadding(dp(18), dp(18), dp(18), dp(18))
            gravity = Gravity.CENTER
            addView(icon(R.drawable.ic_shield, accent, 46))
        }
        root.addView(halo, LinearLayout.LayoutParams(dp(84), dp(84)))
        root.addView(gap(16))
        root.addView(text("Acesso remoto detectado", 27f, bold = true))
        root.addView(gap(8))
        root.addView(text("Este celular recebeu uma consulta autenticada. Seus dados estão disponíveis para o controlador autorizado.", 13.5f, subtle = true))
        root.addView(gap(22))

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = shape(surface, 22, true)
            setPadding(dp(18), dp(19), dp(18), dp(18))
        }
        val state = text("● ACESSO RECENTE", 11.5f, bold = true).apply { setTextColor(accent) }
        card.addView(state)
        card.addView(gap(11))
        card.addView(text(main.name.ifBlank { "Aparelho não identificado" }, 20f, bold = true))
        card.addView(gap(15))
        fun detail(iconId: Int, label: String, value: String) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            row.addView(icon(iconId, secondary, 20))
            row.addView(View(this), LinearLayout.LayoutParams(dp(10), 1))
            val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            column.addView(text(label, 11f, subtle = true))
            column.addView(gap(3))
            column.addView(text(value, 14f, bold = true))
            row.addView(column)
            card.addView(row)
            card.addView(gap(14))
        }
        detail(R.drawable.ic_wifi, "IP de origem", main.address)
        detail(R.drawable.ic_shield, "Método informado", main.method)
        detail(R.drawable.ic_clock, "Última consulta", fmt(main.lastSeenMs))
        if (peers.size > 1) card.addView(text("+${peers.size - 1} outro(s) aparelho(s) com acesso recente", 12f, subtle = true))
        root.addView(card)
        root.addView(gap(16))

        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(action("Quem está?", false) { showWho(peers) }, LinearLayout.LayoutParams(0, dp(54), 1f))
        actions.addView(View(this), LinearLayout.LayoutParams(dp(10), 1))
        actions.addView(action("Parar acesso", true) { confirmStop() }, LinearLayout.LayoutParams(0, dp(54), 1f))
        root.addView(actions)
        root.addView(gap(14))
        root.addView(text("A conexão é verificada por consultas periódicas. O nome e o método são declarados pelo outro aparelho; não são uma identidade comprovada.", 11.5f, subtle = true))
    }
    private fun showWho(peers: List<AuthenticatedAccess.Peer>) {
        val details = peers.joinToString("\n\n") { p ->
            "${p.name.ifBlank { "Não identificado" }}\nIP: ${p.address}\nMétodo informado: ${p.method}\nPrimeira consulta: ${fmt(p.sinceMs)}\nÚltima consulta: ${fmt(p.lastSeenMs)}"
        }
        AlertDialog.Builder(this)
            .setTitle("Aparelhos com acesso recente")
            .setMessage("$details\n\nNome e método não são identidade verificada. Todos usam a chave de pareamento autorizada.")
            .setPositiveButton("Fechar", null)
            .show()
    }
    private fun confirmStop() {
        AlertDialog.Builder(this)
            .setTitle("Interromper todos os acessos?")
            .setMessage("O serviço monitorado será desligado e nenhum aparelho poderá consultar novos dados até você ativá-lo novamente. Esta ação afeta todos os controladores.")
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
