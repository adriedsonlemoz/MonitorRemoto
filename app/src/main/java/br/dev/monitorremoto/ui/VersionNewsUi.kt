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
        "Mapa mais limpo" to "Estados de conexão e GPS em chips, precisão e idade da posição bem destacados.",
        "Última resposta" to "Mostra tempo desde a consulta autenticada, separado da idade da coordenada.",
        "Acompanhar" to "Atualiza a consulta GPS a cada 3 segundos enquanto esta tela permanece aberta.",
        "Consulta enxuta" to "Nova operação cifrada de localização, sem carregar SMS, rede e telemetria completa.",
        "Privacidade" to "Não força GPS novo nem ultrapassa autorização do monitorado; leituras antigas não aparecem como ao vivo."
    )
    entries.forEach { (title, details) ->
        layout.addView(dialogCardRow(title, details, ""))
        sectionGap(layout, 7)
    }
    layout.addView(label("A transferência pode ser limitada pelo Wi-Fi ou armazenamento. Atualize os dois aparelhos para a mesma versão antes de copiar.", 12f, muted = true))
    val dialog = themedPopup(this).setView(wrapper)
        .setPositiveButton("Entendi") { _, _ -> SettingsStore.markNewsSeen(this, AppVersion.NAME) }
        .create().apply { setCancelable(false); setCanceledOnTouchOutside(false) }
    showManagedDialog(dialog)
}
