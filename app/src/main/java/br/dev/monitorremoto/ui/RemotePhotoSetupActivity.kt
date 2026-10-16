package br.dev.monitorremoto.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import br.dev.monitorremoto.core.SettingsStore
import br.dev.monitorremoto.service.MonitorService
import br.dev.monitorremoto.service.PhotoReadyService

/** O armamento da câmera exige gesto no aparelho monitorado, em Activity visível. */
class RemotePhotoSetupActivity : Activity() {
    private val permissionCode = 6280
    private val main = Handler(Looper.getMainLooper())
    private var visible = false
    private lateinit var status: TextView
    private lateinit var arm: Button
    private lateinit var stop: Button
    private var latestMessage: String? = null
    private val updater: Runnable = object : Runnable {
        override fun run() {
            if (!visible) return
            updateStatus()
            main.postDelayed(this, 1_000L)
        }
    }
    private fun dp(v: Int) = (resources.displayMetrics.density * v).toInt()
    private val dark get() = when (SettingsStore.theme(this)) {
        "dark" -> true
        "system" -> resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        else -> false
    }
    private val bg get() = if (dark) Color.rgb(14, 23, 36) else Color.rgb(247, 249, 252)
    private val surface get() = if (dark) Color.rgb(28, 40, 55) else Color.WHITE
    private val ink get() = if (dark) Color.WHITE else Color.rgb(29, 42, 60)
    private val muted get() = if (dark) Color.rgb(175, 187, 202) else Color.rgb(102, 115, 130)
    private fun rounded(color: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(15).toFloat() }
    private fun text(value: String, size: Float = 14f, title: Boolean = false): TextView = TextView(this).apply {
        text = value; textSize = size; setTextColor(if (title) ink else muted)
        if (title) setTypeface(null, Typeface.BOLD)
    }
    private fun section(content: LinearLayout, heading: String, explanation: String) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(15), dp(14), dp(15), dp(14))
            background = rounded(surface)
        }
        card.addView(text(heading, 15f, true))
        card.addView(text(explanation, 13f).apply { setPadding(0, dp(7), 0, 0) })
        content.addView(card, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
    }
    private fun action(label: String, highlighted: Boolean, click: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; textSize = 13f; minHeight = dp(52)
        setTextColor(if (highlighted) Color.WHITE else ink)
        background = rounded(if (highlighted) Color.rgb(36, 110, 197) else surface)
        elevation = 0f; stateListAnimator = null
        setOnClickListener { click() }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg; window.navigationBarColor = bg
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = if (dark) 0 else
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        val scroller = ScrollView(this).apply { setBackgroundColor(bg); isFillViewport = true; fitsSystemWindows = true }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(24))
        }
        scroller.addView(content)
        content.addView(action("‹ Voltar", false) { finish() }, LinearLayout.LayoutParams(dp(98), dp(52)))
        content.addView(text("Fotografia remota", 22f, true).apply { setPadding(0, dp(14), 0, dp(5)) })
        content.addView(text("Preparação no aparelho monitorado", 13f))
        val current = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(surface)
            setPadding(dp(15), dp(14), dp(15), dp(14))
        }
        current.addView(text("Estado da câmera", 14f, true))
        status = text("Verificando…", 13f).apply { setPadding(0, dp(7), 0, 0) }
        current.addView(status)
        content.addView(current, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(13) })
        arm = action("Armar foto remota", true) { authorize() }
        stop = action("Parar câmera remota", false) {
            PhotoReadyService.stop(this)
            latestMessage = "Solicitação de parada enviada."
            updateStatus()
        }
        content.addView(arm, LinearLayout.LayoutParams(-1, dp(54)).apply { topMargin = dp(12) })
        content.addView(stop, LinearLayout.LayoutParams(-1, dp(54)).apply { topMargin = dp(8) })
        section(content, "Como usar", "1. Ative o Modo monitorado neste celular.\n" +
            "2. Toque em Armar foto remota e conceda CAMERA.\n" +
            "3. Enquanto a sessão permanecer ativa, fotografe pelo celular controlador sem iniciar vídeo.")
        section(content, "Privacidade e controle", "A sessão é sinalizada por uma notificação permanente com ação Parar. " +
            "Somente aparelhos que possuam a chave de pareamento podem solicitar fotografias, enviadas por AES-GCM. " +
            "Nenhuma foto é gravada automaticamente neste celular.")
        section(content, "Limites do Android", "Não é possível rearmar a câmera automaticamente depois de reiniciar, " +
            "forçar a parada ou se o Android encerrar o serviço. O monitorado precisa continuar conectado pela LAN " +
            "ou por uma rede privada previamente configurada.")
        setContentView(scroller)
        updateStatus()
    }

    override fun onResume() {
        super.onResume()
        visible = true
        main.removeCallbacks(updater)
        main.post(updater)
    }
    override fun onPause() { visible = false; main.removeCallbacks(updater); super.onPause() }

    private fun updateStatus(message: String? = null) {
        if (message != null) latestMessage = message
        val armed = PhotoReadyService.armed
        status.text = if (armed) "● Ativa · Pronta para fotografias autenticadas" else
            latestMessage ?: "○ Desativada · Armar exige ação local."
        arm.isEnabled = !armed
        stop.isEnabled = armed
        if (armed) latestMessage = null
    }

    private fun authorize() {
        if (!SettingsStore.isMonitorEnabled(this) || !MonitorService.isRunning) {
            updateStatus("Ative primeiro o Modo monitorado e aguarde a rede ficar disponível.")
            return
        }
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), permissionCode)
            return
        }
        begin()
    }

    private fun begin() {
        try {
            val request = Intent(this, PhotoReadyService::class.java).setAction(PhotoReadyService.ACTION_START)
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(request) else startService(request)
            updateStatus("Preparando sessão… Confira a notificação do Android.")
        } catch (ex: Exception) {
            updateStatus("Android não autorizou armar a câmera: ${ex.message ?: "falha"}")
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == permissionCode) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) begin()
            else updateStatus("Permissão CAMERA recusada. A sessão não foi iniciada.")
        }
    }
}
