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
        "Foto remota redesenhada" to "Tema claro/escuro, progresso do disparo, identificação do aparelho e dados da foto recebida.",
        "Ampliar fotografia" to "Use dois dedos, arraste ou toque duas vezes; botão Ajustar imagem restaura o zoom.",
        "Salvar e compartilhar" to "Salve a foto na galeria ou compartilhe; nenhuma foto é salva automaticamente.",
        "Preparação mais clara" to "Estado da sessão no monitorado, notificação visível e botão Parar.",
        "Recursos preservados" to "Mapa, recuperação de arquivos, transferência binária e câmera ao vivo continuam disponíveis."
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
