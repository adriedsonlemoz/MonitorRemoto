package br.dev.monitorremoto.ui

import android.app.AlertDialog
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import br.dev.monitorremoto.core.RecentDevices
import br.dev.monitorremoto.core.SettingsStore
import br.dev.monitorremoto.service.MonitorService
import br.dev.monitorremoto.transport.AuthenticatedAccess
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Alpha.22: módulo de interface extraído de MainActivity. Sem estado de conexão próprio. */

/** Home é sempre navegável mesmo quando o monitor está parado ou sem rede. */
internal fun MainActivity.renderHome() {
    val remoteAccess = AuthenticatedAccess.recent()
    if (remoteAccess.isNotEmpty() && SettingsStore.isMonitorEnabled(this) && MonitorService.isRunning) {
        val banner = panel(12)
        banner.addView(label("● Acesso remoto em andamento", 14f, strong = true).apply {
            setTextColor(highlight)
        })
        banner.addView(label("${remoteAccess.size} aparelho(s) consultaram este celular recentemente.", 11f, muted = true))
        banner.addView(actionButton("Ver acessos") { mode = 0; render() },
            LinearLayout.LayoutParams(-1, dp(40)))
        content.addView(banner)
        gap(9)
    }
    content.addView(label("O que você deseja fazer?", 20f, strong = true))
    content.addView(label("Conecte um aparelho ou disponibilize os dados deste celular.", 12f, muted = true))
    gap(12)
    content.addView(button("＋  Conectar novo aparelho", emphasis = true) { showConnectionChooser() })
    gap(12)

    val recent = RecentDevices.list(this)
    val listPanel = panel(14)
    listPanel.addView(label("Aparelhos anteriores", 16f, strong = true))
    listPanel.addView(label("Conecte rapidamente ou use a engrenagem para gerenciar cada aparelho.", 12f, muted = true))
    sectionGap(listPanel, 8)
    if (recent.isEmpty()) {
        listPanel.addView(label("Nenhum aparelho salvo. Escolha Conectar novo aparelho para começar.", 12f, muted = true))
    } else {
        recent.forEachIndexed { index, item ->
            if (index != 0) sectionGap(listPanel, 9)
            val devicePanel = panel(11)
            val actions = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            actions.addView(label(item.name, 14f, strong = true).apply {
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
            }, LinearLayout.LayoutParams(0, -2, 1f))
            actions.addView(View(this), LinearLayout.LayoutParams(dp(6), 1))
            actions.addView(actionButton("Conectar", emphasis = true) {
                connectSavedDevice(item)
            }.apply {
                minWidth = 0
                textSize = 11.5f
                setPadding(dp(4), 0, dp(4), 0)
            }, LinearLayout.LayoutParams(dp(84), dp(44)))
            actions.addView(View(this), LinearLayout.LayoutParams(dp(6), 1))
            actions.addView(actionButton("⚙") {
                showHomeSavedDeviceOptions(item)
            }.apply {
                textSize = 18f
                contentDescription = "Gerenciar ${item.name}: renomear ou esquecer"
                minWidth = 0
                setPadding(0, 0, 0, 0)
            }, LinearLayout.LayoutParams(dp(44), dp(44)))
            devicePanel.addView(actions)
            devicePanel.addView(label("IP: ${item.host}", 12f, muted = true))
            val lastUsed = if (item.lastSuccessMs > 0L)
                SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(item.lastSuccessMs))
                else "ainda sem conexão confirmada"
            devicePanel.addView(label("Última conexão: $lastUsed", 11f, muted = true))
            listPanel.addView(devicePanel)
        }
    }
    content.addView(listPanel)
    gap(14)

    val monitorCard = panel(14)
    monitorCard.addView(label("Monitorar este aparelho", 16f, strong = true))
    monitorCard.addView(label("Permite que outro celular consulte os dados deste aparelho.", 12f, muted = true))
    sectionGap(monitorCard, 9)
    monitorCard.addView(actionButton("Abrir tela do aparelho ›") {
        mode = 0
        render()
    }, LinearLayout.LayoutParams(-1, dp(48)))
    content.addView(monitorCard)
}

/** O menu da engrenagem de cada aparelho contém apenas ações de gerenciamento. */
private fun MainActivity.showHomeSavedDeviceOptions(item: RecentDevices.Device) {
    val (wrapper, layout) = dialogWrapper()
    dialogIntro(layout, "${item.name} · ${item.host}")
    var popup: AlertDialog? = null
    layout.addView(dialogCardRow("Renomear", "Alterar o nome do aparelho salvo") {
        popup?.dismiss()
        renameSavedDevice(item, returnToChooser = false)
    })
    sectionGap(layout, 8)
    layout.addView(dialogCardRow("Esquecer aparelho", "Excluir a chave e remover este aparelho da lista") {
        popup?.dismiss()
        forgetSavedDevice(item, returnToChooser = false)
    })
    popup = AlertDialog.Builder(this)
        .setTitle("Gerenciar aparelho")
        .setView(wrapper)
        .setNegativeButton("Fechar", null)
        .create()
    popup.show()
}

