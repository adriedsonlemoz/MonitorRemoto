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
        "Painel em 3 colunas" to "12 funções com botões compactos e Detalhes no cabeçalho do aparelho.",
        "Configurações mais simples" to "Seis grupos e busca local instantânea. Autorizações continuam no aparelho monitorado.",
        "Avisos com novo visual" to "Diálogos principais mais compactos, mantendo confirmação para ações sensíveis.",
        "Diagnóstico de tempo" to "Exibe duração real da última solicitação autenticada, sem confundir com a idade dos dados.",
        "Recursos preservados" to "Arquivos, mapa e fotografia remota continuam com as autorizações já existentes."
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
