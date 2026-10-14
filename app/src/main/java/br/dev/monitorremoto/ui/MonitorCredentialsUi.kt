package br.dev.monitorremoto.ui

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PersistableBundle
import android.view.WindowManager
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import br.dev.monitorremoto.R
import br.dev.monitorremoto.core.SettingsStore
import br.dev.monitorremoto.security.CryptoBox
import br.dev.monitorremoto.security.SecretVault
import br.dev.monitorremoto.service.MonitorService

/** Sensitive monitor credentials remain hidden until explicitly requested. */

internal fun MainActivity.preferredLocalAddress(): String? {
    val ips = availableLocalIps()
    return (ips.firstOrNull { it.first.startsWith("wlan", ignoreCase = true) }
        ?: ips.firstOrNull())?.second
}

/** IP + segredo só são lidos após solicitação explícita, e não ficam na tela principal. */
internal fun MainActivity.showMyConnectionInfo() {
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
    val card = panel(15)
    card.addView(iconSectionHeader(R.drawable.ic_wifi, "Conexão local", "IP atual deste aparelho"))
    sectionGap(card, 7)
    card.addView(label(ip, 19f, strong = true))
    sectionGap(card, 12)
    card.addView(iconSectionHeader(R.drawable.ic_shield, "Chave de pareamento", "Protegida pelo Android Keystore"))
    sectionGap(card, 8)
    val keyLabel = label("••••••••••••••••", 14f).apply {
        setTextIsSelectable(false)
        maxLines = 3
        setTextColor(ink)
    }
    keyLabel.background = shape(fieldColor, 14, true)
    keyLabel.setPadding(dp(12), dp(12), dp(12), dp(12))
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
