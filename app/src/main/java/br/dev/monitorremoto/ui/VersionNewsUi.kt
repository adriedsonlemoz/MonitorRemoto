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
        "Correção da compilação" to "Menu de arquivos corrigido: lista rolável própria e diálogo com referência não nula.",
        "Downloads sem escolher pasta" to "Pasta fixa Downloads/Monitor Remoto criada automaticamente em Android 10+.",
        "Envio e recuperação" to "Enviar seleciona os arquivos; baixar usa Downloads/Monitor Remoto, mantendo integridade e retomada.",
        "Arquivos do próprio app" to "Somente arquivos criados pelo Monitor Remoto ficam disponíveis, sem ler downloads de terceiros.",
        "Menus redesenhados" to "Ações de arquivo em menu inferior e diálogos com estilo coerente nos temas.",
        "Segurança preservada" to "Chave de pareamento e confirmação de exclusão continuam obrigatórias."
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
