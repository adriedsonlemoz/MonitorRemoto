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
    popup?.let { showManagedDialog(it) }
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
    showManagedDialog(dialog)
}

private fun MainActivity.showPermissionSettings() {
    val state = if (hasListener()) "Autorizado" else "Sem acesso"
    val count = SettingsStore.notificationAllowlist(this).size
    val (wrapper, layout) = dialogWrapper()
    dialogIntro(layout, "O monitoramento da bateria funciona sem esta permissão. As notificações só são enviadas para apps autorizados e não financeiros.")
    layout.addView(iconMenuRow(R.drawable.ic_bell, "Acesso às notificações", state) {
        showAppConfirm(
            "Acesso às notificações",
            "O Android poderá mostrar notificações sensíveis. Somente aplicativos escolhidos serão transmitidos, com filtros que podem falhar. O acesso pode ser revogado nas Configurações do Android.",
            R.drawable.ic_bell, "Configurar no Android"
        ) { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
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
            showAppConfirm(
                "Compartilhar número do chip?",
                "Opcional. Quando disponível, o número será enviado somente a aparelhos pareados e autenticados. Não é necessário para bateria, redes ou armazenamento. Você poderá desativar esta opção a qualquer momento.",
                R.drawable.ic_sim, "Autorizar"
            ) {
                if (checkSelfPermission(Manifest.permission.READ_PHONE_NUMBERS) == PackageManager.PERMISSION_GRANTED) {
                    SettingsStore.setSharePhoneNumber(this, true)
                    message("Compartilhamento ativado. Algumas operadoras não informam o número ao Android.")
                } else requestPermissions(arrayOf(Manifest.permission.READ_PHONE_NUMBERS), phoneNumberPermissionRequest)
            }
        }
    })
    AlertDialog.Builder(this)
        .setTitle("Permissões e notificações")
        .setView(wrapper)
        .setNegativeButton("Fechar", null)
        .create().let { showManagedDialog(it) }
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
        .create().let { showManagedDialog(it) }
}

private fun MainActivity.showMonitorKey() {
    // Mesma janela protegida e chave oculta inicialmente usada no modo Monitorado.
    showMyConnectionInfo()
}

internal fun MainActivity.confirmRotateMonitorKey() {
    showAppConfirm(
        "Revogar acessos?",
        "Todos os controladores perderão a chave atual e precisarão parear novamente. Essa operação não pode ser desfeita.",
        R.drawable.ic_shield, "Trocar chave"
    ) {
        SecretVault.save(this, SecretVault.MONITOR_KEY, CryptoBox.newPairingKey())
        PairingSession.revoke()
        AuthenticatedAccess.clear()
        message("Chave substituída. Os acessos anteriores foram revogados.")
    }
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
        .create().let { showManagedDialog(it) }
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
        .create().let { showManagedDialog(it) }
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
        .create().let { showManagedDialog(it) }
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
    val selected = SettingsStore.notificationAllowlist(this).toMutableSet()
    val (wrapper, layout) = dialogWrapper()
    layout.addView(iconSectionHeader(R.drawable.ic_bell, "Aplicativos autorizados", "Permita apenas o que deseja compartilhar"))
    sectionGap(layout, 8)
    dialogIntro(layout, "As notificações dos aplicativos selecionados poderão ser consultadas por aparelhos pareados. Os filtros de conteúdo sensível não são infalíveis.")
    val search = field("Buscar aplicativo por nome").apply {
        inputType = android.text.InputType.TYPE_CLASS_TEXT
    }
    layout.addView(search)
    sectionGap(layout, 8)
    val count = label("", 11.5f, muted = true)
    layout.addView(count)
    val appList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    val listScroll = android.widget.ScrollView(this).apply {
        isFillViewport = false
        addView(appList)
    }
    layout.addView(listScroll, LinearLayout.LayoutParams(-1, dp(300)))
    sectionGap(layout, 8)
    layout.addView(panel(11).apply {
        background = shape(fieldColor, 15)
        addView(label("Aplicativos bancários conhecidos são excluídos da lista. Não há acesso à caixa de SMS: apenas notificações dos aplicativos autorizados.", 11f, muted = true))
    })

    fun refreshList(filter: String) {
        appList.removeAllViews()
        val visible = apps.filter { packageManager.getApplicationLabel(it).toString()
            .contains(filter.trim(), ignoreCase = true) }
        count.text = "${selected.size} selecionado(s) · ${visible.size} aplicativo(s) exibido(s)"
        if (visible.isEmpty()) {
            appList.addView(label(if (apps.isEmpty()) "Nenhum aplicativo elegível foi encontrado neste aparelho."
                else "Nenhum aplicativo corresponde à pesquisa.", 12.5f, muted = true))
            return
        }
        visible.forEach { app ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = shape(fieldColor, 15, true)
                setPadding(dp(10), dp(8), dp(10), dp(8))
            }
            val symbol = ImageView(this).apply {
                setImageDrawable(runCatching { packageManager.getApplicationIcon(app.packageName) }.getOrNull())
                contentDescription = null
            }
            row.addView(symbol, LinearLayout.LayoutParams(dp(30), dp(30)))
            row.addView(android.view.View(this), LinearLayout.LayoutParams(dp(10), 1))
            val name = packageManager.getApplicationLabel(app).toString()
            val title = label(name, 13f, strong = true).apply {
                maxLines = 2; setPadding(0, 0, 0, 0)
            }
            row.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
            val check = android.widget.CheckBox(this).apply {
                isChecked = app.packageName in selected
                contentDescription = "Permitir notificações de $name"
                setOnCheckedChangeListener { _, checked ->
                    if (checked) selected.add(app.packageName) else selected.remove(app.packageName)
                    count.text = "${selected.size} selecionado(s) · ${visible.size} aplicativo(s) exibido(s)"
                }
            }
            row.addView(check)
            row.setOnClickListener { check.isChecked = !check.isChecked }
            appList.addView(row)
            sectionGap(appList, 7)
        }
    }
    search.addTextChangedListener(object : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            refreshList(s?.toString().orEmpty())
        }
        override fun afterTextChanged(s: android.text.Editable?) = Unit
    })
    refreshList("")
    showManagedDialog(AlertDialog.Builder(this)
        .setView(wrapper)
        .setNegativeButton("Cancelar", null)
        .setPositiveButton("Salvar") { _, _ ->
            SettingsStore.setNotificationAllowlist(this, selected)
            render()
        })
}
