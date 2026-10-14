package br.dev.monitorremoto.ui

import android.app.AlertDialog
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.text.InputType
import android.view.View
import android.widget.LinearLayout
import br.dev.monitorremoto.core.RecentDevices
import br.dev.monitorremoto.core.SettingsStore
import br.dev.monitorremoto.security.CryptoBox

internal fun MainActivity.showSavedDeviceOptions(item: RecentDevices.Device) {
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


/** Etapa inicial compacta; somente a opção Manual abre os campos de IP e chave. */
/** QR/manual as two horizontal choices. No redundant QR text-entry action. */
internal fun MainActivity.showConnectionChooser() {
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

internal fun MainActivity.connectSavedDevice(item: RecentDevices.Device) {
    if (RecentDevices.key(this, item.host) == null) {
        message("A chave desse aparelho não está disponível. Faça um novo pareamento.")
        showConnectionChooser()
        return
    }
    SettingsStore.setRemoteHost(this, item.host)
    mode = 1
    remoteConnection.select("Chave salva")
    render(); refreshRemote(silent = false)
}

internal fun MainActivity.renameSavedDevice(item: RecentDevices.Device, returnToChooser: Boolean = true) {
    val input = field("Nome do aparelho", item.name)
    AlertDialog.Builder(this).setTitle("Renomear aparelho").setView(input)
        .setNegativeButton("Cancelar") { _, _ -> if (returnToChooser) showConnectionChooser() }
        .setPositiveButton("Salvar") { _, _ ->
            RecentDevices.rename(this, item.host, input.text.toString())
            render(); if (returnToChooser) showConnectionChooser()
        }.show()
}

internal fun MainActivity.forgetSavedDevice(item: RecentDevices.Device, returnToChooser: Boolean = true) {
    AlertDialog.Builder(this).setTitle("Esquecer aparelho?")
        .setMessage("Remover ${item.name} e apagar a chave de pareamento salva? Para conectar novamente será necessário parear.")
        .setNegativeButton("Cancelar") { _, _ -> if (returnToChooser) showConnectionChooser() }
        .setPositiveButton("Esquecer") { _, _ ->
            val wasSelected = SettingsStore.getRemoteHost(this).equals(item.host, ignoreCase = true)
            RecentDevices.forget(this, item.host)
            if (wasSelected) {
                remoteConnection.clear(resetMethod = false)
            }
            render(); if (returnToChooser) showConnectionChooser()
        }.show()
}

internal fun MainActivity.showManualConnectionDialog() {
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
                SettingsStore.setRemoteHost(this, address)
                remoteConnection.select("Manual")
                key.text.clear(); dialog.dismiss(); mode = 1; render(); refreshRemote(silent = false)
            } catch (ex: Exception) { message("Não foi possível conectar: ${ex.message}") }
        }
    }
    dialog.show()
}
