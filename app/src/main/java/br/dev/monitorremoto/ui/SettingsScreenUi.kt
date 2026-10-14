package br.dev.monitorremoto.ui

import android.app.AlertDialog
import android.Manifest
import android.content.pm.PackageManager
import br.dev.monitorremoto.core.SensitiveContent
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
import java.util.Locale

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
        layout.addView(iconMenuRow(R.drawable.ic_palette, option.second,
            if (selected) "✓ Selecionado · ${option.third}" else option.third) {
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
    layout.addView(iconMenuRow(R.drawable.ic_bell, "Acesso às notificações", state) {
        AlertDialog.Builder(this).setTitle("Acesso a notificações (opcional)")
            .setMessage("O Android concede acesso geral às notificações, que podem conter informações sensíveis. Apenas aplicativos escolhidos são transmitidos, com filtros não infalíveis. Você pode revogar o acesso nas Configurações Android. O monitoramento da bateria não exige essa permissão.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Configurar no Android") { _, _ ->
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }.show()
    })
    sectionGap(layout, 8)
    layout.addView(iconMenuRow(R.drawable.ic_phone, "Aplicativos autorizados", "$count selecionado(s)") { selectNotificationApps() })
    sectionGap(layout, 8)
    val numberEnabled = SettingsStore.sharePhoneNumber(this) &&
        checkSelfPermission(Manifest.permission.READ_PHONE_NUMBERS) == PackageManager.PERMISSION_GRANTED
    layout.addView(iconMenuRow(R.drawable.ic_sim, "Número do chip", if (numberEnabled) "Compartilhamento ativado" else "Desativado (padrão)") {
        if (numberEnabled) {
            SettingsStore.setSharePhoneNumber(this, false)
            message("Compartilhamento do número desativado. O Android pode manter a permissão concedida, mas o app não transmitirá o número.")
        } else {
            AlertDialog.Builder(this)
                .setTitle("Compartilhar número do chip?")
                .setMessage("Opcional. O número da linha, quando disponível, será enviado somente a aparelhos pareados e autenticados. Não é necessário para bateria, redes ou armazenamento. Você poderá desativar em Permissões.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Autorizar") { _, _ ->
                    if (checkSelfPermission(Manifest.permission.READ_PHONE_NUMBERS) == PackageManager.PERMISSION_GRANTED) {
                        SettingsStore.setSharePhoneNumber(this, true)
                        message("Compartilhamento ativado. Algumas operadoras não informam o número ao Android.")
                    } else requestPermissions(arrayOf(Manifest.permission.READ_PHONE_NUMBERS), phoneNumberPermissionRequest)
                }.show()
        }
    })
    AlertDialog.Builder(this)
        .setTitle("Permissões e notificações")
        .setView(wrapper)
        .setNegativeButton("Fechar", null)
        .show()
}

private fun MainActivity.showSecuritySettings() {
    val (wrapper, layout) = dialogWrapper()
    dialogIntro(layout, "As credenciais são protegidas pelo Android Keystore. Compartilhe a chave somente entre seus próprios aparelhos.")
    layout.addView(iconMenuRow(R.drawable.ic_shield, "Mostrar/copiar chave", "IP e chave de pareamento protegidos") { showMonitorKey() })
    sectionGap(layout, 8)
    layout.addView(iconMenuRow(R.drawable.ic_logout, "Trocar chave e revogar acessos", "Revoga o acesso de todos os controladores", dangerous = true) { confirmRotateMonitorKey() })
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
    layout.addView(iconMenuRow(R.drawable.ic_power, "Retomar serviço", "Recupera o monitoramento se estiver autorizado") {
        if (SettingsStore.isMonitorEnabled(this)) startServiceExplicitly()
        else message("Ative o monitoramento antes de iniciar o serviço.")
    })
    sectionGap(layout, 8)
    layout.addView(iconMenuRow(R.drawable.ic_clock, if (autostart) "Desativar início após reiniciar" else "Ativar início após reiniciar", "Depende das restrições do Android e do fabricante") {
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
    card.addView(iconSectionHeader(R.drawable.ic_activity, "Serviço e rede", "Estado atual deste aparelho"))
    sectionGap(card, 12)
    val firstRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
    firstRow.addView(iconMetric("Serviço", if (running) "Ativo" else "Parado", R.drawable.ic_power),
        LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(9) })
    firstRow.addView(iconMetric("Monitoramento", if (SettingsStore.isMonitorEnabled(this)) "Autorizado" else "Desativado", R.drawable.ic_shield),
        LinearLayout.LayoutParams(0, -2, 1f))
    card.addView(firstRow)
    sectionGap(card, 9)
    val secondRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
    secondRow.addView(iconMetric("Porta TCP", WireProtocol.PORT.toString(), R.drawable.ic_wifi),
        LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(9) })
    secondRow.addView(iconMetric("Notificações", if (hasListener()) "Autorizadas" else "Sem acesso", R.drawable.ic_bell),
        LinearLayout.LayoutParams(0, -2, 1f))
    card.addView(secondRow)
    sectionGap(card, 10)
    card.addView(dialogMetricRow("IP local", localIps().ifBlank { "Indisponível" }))
    card.addView(dialogMetricRow("Apps permitidos", SettingsStore.notificationAllowlist(this).size.toString()))
    layout.addView(card)
    AlertDialog.Builder(this)
        .setTitle("Diagnóstico")
        .setView(wrapper)
        .setNegativeButton("Fechar", null)
        .show()
}


/** Settings-owned dialogs formerly hosted by MainActivity. */
internal fun MainActivity.showAboutDialog() {
    val (wrapper, layout) = dialogWrapper()
    dialogIntro(layout, "Resumo rápido do foco atual do projeto.")
    val card = panel(14)
    card.addView(iconSectionHeader(R.drawable.ic_info, "Monitor Remoto", "Uso particular e conexão privada"))
    sectionGap(card, 9)
    card.addView(dialogMetricRow("Versão", AppVersion.NAME))
    card.addView(dialogMetricRow("Tela inicial", "Home com escolha de aparelhos"))
    card.addView(dialogMetricRow("Modo monitorado", "No mesmo APK"))
    card.addView(dialogMetricRow("Segurança", "Pareamento e Android Keystore"))
    layout.addView(card)
    layout.addView(label("Privacidade, baixo consumo e permissões explícitas continuam como prioridades.", 12f, muted = true))
    AlertDialog.Builder(this)
        .setTitle("Sobre o Monitor Remoto")
        .setView(wrapper)
        .setPositiveButton("OK", null)
        .show()
}

/** Prefere a interface Wi-Fi no instante de abertura, evitando IP antigo em cache. */

internal fun MainActivity.selectNotificationApps() {
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
