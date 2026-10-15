package br.dev.monitorremoto.ui

import android.app.AlertDialog
import br.dev.monitorremoto.R
import br.dev.monitorremoto.core.SettingsStore
import br.dev.monitorremoto.transport.AppVersion

/** Exibido somente após a primeira abertura de cada versão instalada. */
internal fun MainActivity.showVersionNewsIfNeeded() {
    if (SettingsStore.seenNewsVersion(this) == AppVersion.NAME) return
    showVersionNews()
}
internal fun MainActivity.showVersionNews() {
    val (wrapper, layout) = dialogWrapper()
    layout.addView(iconSectionHeader(R.drawable.ic_info, "Novidades · ${AppVersion.NAME}", "Bem-vindo à atualização"))
    sectionGap(layout, 10)
    val entries = listOf(
        "Painel renovado" to "Nome editável, ações compactas, ícones para tela, bloqueio e câmera e cartão de aplicativo observado.",
        "Permissões" to "Toque nos cartões para ver detalhes e abrir as autorizações localmente no monitorado.",
        "Alertas de saúde" to "Avisos de bateria e temperatura, consulta remota autenticada e verificação opcional em segundo plano.",
        "Proteção térmica" to "Se ativada localmente, suspende capturas do próprio Monitor Remoto em aquecimento severo. Não controla o Minha Câmera."
    )
    entries.forEach { (title, details) ->
        layout.addView(dialogCardRow(title, details, ""))
        sectionGap(layout, 7)
    }
    layout.addView(label("As notificações precisam da autorização do Android. O intervalo em segundo plano depende do sistema; alertas podem atrasar.", 12f, muted = true))
    val dialog = AlertDialog.Builder(this).setView(wrapper)
        .setPositiveButton("Entendi") { _, _ -> SettingsStore.markNewsSeen(this, AppVersion.NAME) }
        .create().apply { setCancelable(false); setCanceledOnTouchOutside(false) }
    showManagedDialog(dialog)
}
