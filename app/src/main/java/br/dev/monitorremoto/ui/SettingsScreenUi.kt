package br.dev.monitorremoto.ui

import android.app.AlertDialog
import android.content.res.ColorStateList
import android.content.Intent
import android.widget.ImageView
import android.provider.Settings
import android.view.Gravity
import android.widget.LinearLayout
import br.dev.monitorremoto.R
import br.dev.monitorremoto.core.SettingsStore
import br.dev.monitorremoto.security.CryptoBox
import br.dev.monitorremoto.security.SecretVault
import br.dev.monitorremoto.service.MonitorService
import br.dev.monitorremoto.transport.AppVersion
import br.dev.monitorremoto.transport.AuthenticatedAccess
import br.dev.monitorremoto.transport.PairingSession
import br.dev.monitorremoto.transport.WireProtocol

/** Alpha.22: módulo de interface extraído de MainActivity. Sem estado de conexão próprio. */

/** Painel de opções moderno, sem os antigos itens de diálogo em listas longas. */
internal fun MainActivity.showSettingsDialog() {
    val (wrapper, layout) = dialogWrapper()
    val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    val headText = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    headText.addView(label("Personalize e proteja", 17f, strong = true))
    headText.addView(label("Controles do aparelho e do serviço", 11.5f, muted = true))
    header.addView(headText, LinearLayout.LayoutParams(0, -2, 1f))
    header.addView(settingsIcon(R.drawable.ic_settings, highlight, 26))
    layout.addView(header)
    sectionGap(layout, 12)

    val options = listOf(
        Triple("Aparência", "Claro · Escuro · Sistema", R.drawable.ic_palette),
        Triple("Notificações", "Acesso e apps permitidos", R.drawable.ic_bell),
        Triple("Segurança", "Chaves e pareamento", R.drawable.ic_shield),
        Triple("Serviço", "Ativar e inicialização", R.drawable.ic_power),
        Triple("Diagnóstico", "Rede e permissões", R.drawable.ic_activity),
        Triple("Sobre", "Versão e privacidade", R.drawable.ic_info)
    )
    val actions: List<() -> Unit> = listOf(
        { showAppearanceDialog() }, { showPermissionSettings() },
        { showSecuritySettings() }, { showServiceSettings() },
        { showDiagnostics() }, { showAboutDialog() }
    )
    var popup: AlertDialog? = null
    options.chunked(2).forEachIndexed { rowIndex, rowOptions ->
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        rowOptions.forEachIndexed { index, option ->
            val itemIndex = rowIndex * 2 + index
            val tile = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = shape(fieldColor, 17, true)
                setPadding(dp(13), dp(14), dp(9), dp(12))
                minimumHeight = dp(112)
                isClickable = true
                isFocusable = true
                contentDescription = "${option.first}: ${option.second}"
                setOnClickListener { popup?.dismiss(); actions[itemIndex]() }
            }
            tile.addView(settingsIcon(option.third, if (itemIndex == 2) highlight else secondaryAccent, 26))
            tile.addView(label(option.first, 13f, strong = true).apply {
                setPadding(0, dp(8), 0, 0)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
            tile.addView(label(option.second, 10.5f, muted = true).apply {
                setPadding(0, dp(3), 0, 0)
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
            row.addView(tile, LinearLayout.LayoutParams(0, -2, 1f).apply {
                if (index == 0) rightMargin = dp(8)
            })
        }
        layout.addView(row)
        if (rowIndex < 2) sectionGap(layout, 8)
    }
    sectionGap(layout, 10)
    layout.addView(label("Monitor Remoto  ·  ${AppVersion.NAME}", 11f, muted = true))
    popup = AlertDialog.Builder(this).setTitle("Configurações")
        .setView(wrapper).setNegativeButton("Fechar", null).create()
    popup.show()
}

private fun MainActivity.settingsIcon(drawable: Int, tint: Int, size: Int) = ImageView(this).apply {
    setImageResource(drawable)
    imageTintList = ColorStateList.valueOf(tint)
    layoutParams = LinearLayout.LayoutParams(dp(size), dp(size))
}

private fun MainActivity.showAppearanceDialog() {
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

private fun MainActivity.showPermissionSettings() {
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

private fun MainActivity.showSecuritySettings() {
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

private fun MainActivity.showMonitorKey() {
    // Mesma janela protegida e chave oculta inicialmente usada no modo Monitorado.
    showMyConnectionInfo()
}

internal fun MainActivity.confirmRotateMonitorKey() {
    AlertDialog.Builder(this).setTitle("Revogar acessos?")
        .setMessage("Todos os controladores precisarão parear novamente. A chave anterior deixará de funcionar.")
        .setNegativeButton("Cancelar", null)
        .setPositiveButton("Trocar chave") { _, _ ->
            SecretVault.save(this, SecretVault.MONITOR_KEY, CryptoBox.newPairingKey())
            PairingSession.revoke()
            AuthenticatedAccess.clear()
            message("Chave substituída. Os acessos anteriores foram revogados.")
        }.show()
}

private fun MainActivity.showServiceSettings() {
    val autostart = SettingsStore.autoStartEnabled(this)
    val (wrapper, layout) = dialogWrapper()
    dialogIntro(layout, "Controle o comportamento do serviço monitorado neste aparelho.")
    layout.addView(dialogCardRow("Retomar serviço", "Recupera o monitoramento se estiver autorizado", "›") {
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

private fun MainActivity.showDiagnostics() {
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

