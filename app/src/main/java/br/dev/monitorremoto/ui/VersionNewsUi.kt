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
        "Correção de compilação" to "Ajuste no encerramento do serviço após o erro detectado no workflow #72.",
        "Ícone de escudo" to "Identidade visual própria no launcher e nas notificações do Android.",
        "Notificação inteligente" to "Estado do serviço, acessos recentes e módulos ativos com atualizações discretas.",
        "Detalhes do serviço" to "Tela dedicada com horários das consultas; nomes de controladores são autodeclarados.",
        "Privacidade" to "Tela bloqueada sem nomes ou detalhes de acesso. Instale alpha.77 nos dois aparelhos."
    )
    entries.forEach { (title, details) ->
        layout.addView(dialogCardRow(title, details, ""))
        sectionGap(layout, 7)
    }
    layout.addView(label("Acesso recente não equivale a conexão permanente. O compartilhamento de localização continua exigindo ativação local.", 12f, muted = true))
    val dialog = AlertDialog.Builder(this).setView(wrapper)
        .setPositiveButton("Entendi") { _, _ -> SettingsStore.markNewsSeen(this, AppVersion.NAME) }
        .create().apply { setCancelable(false); setCanceledOnTouchOutside(false) }
    showManagedDialog(dialog)
}
