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
        "Histórico local de arquivos" to "Mostra operações concluídas, parciais e falhas, sem nomes de arquivos, com opção de apagar.",
        "Painel mais legível" to "Funções em duas colunas e aviso quando a telemetria é apenas uma leitura guardada.",
        "Localização mais clara" to "GPS com idade e precisão separadas, destacando leitura antiga e sem resposta.",
        "Privacidade mantida" to "Sem novas permissões; arquivos, câmera, SMS e localização seguem dependentes de autorização.",
        "Transferências" to "Cópia continua contínua e cifrada; retomada após fechar a tela ainda não está disponível."
    )
    entries.forEach { (title, details) ->
        layout.addView(dialogCardRow(title, details, ""))
        sectionGap(layout, 7)
    }
    layout.addView(label("A transferência pode ser limitada pelo Wi-Fi ou armazenamento. Atualize os dois aparelhos para a mesma versão antes de copiar.", 12f, muted = true))
    val dialog = AlertDialog.Builder(this).setView(wrapper)
        .setPositiveButton("Entendi") { _, _ -> SettingsStore.markNewsSeen(this, AppVersion.NAME) }
        .create().apply { setCancelable(false); setCanceledOnTouchOutside(false) }
    showManagedDialog(dialog)
}
