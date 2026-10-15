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
        "Aplicativos com ícones" to "Miniaturas reais do aparelho remoto, página por página, sem aumentar demais o tráfego.",
        "Abrir aplicativo" to "Tenta a API Android com autorização local; quando bloqueada, oferece ADB separado.",
        "Parar pelo ADB" to "Somente em sessão ADB TLS autorizada, com confirmação digitada e proteção de apps essenciais.",
        "Favoritos e filtros" to "Pesquisa, favoritos por aparelho e filtro do último aplicativo observado.",
        "Energia e Manutenção" to "Bloqueio, tela e reinicialização numa central, sem opção de desligamento total."
    )
    entries.forEach { (title, details) ->
        layout.addView(dialogCardRow(title, details, ""))
        sectionGap(layout, 7)
    }
    layout.addView(label("Abertura e parada são pedidos, não provas de execução. A notificação inteligente, a localização e a proteção térmica continuam disponíveis.", 12f, muted = true))
    val dialog = AlertDialog.Builder(this).setView(wrapper)
        .setPositiveButton("Entendi") { _, _ -> SettingsStore.markNewsSeen(this, AppVersion.NAME) }
        .create().apply { setCancelable(false); setCanceledOnTouchOutside(false) }
    showManagedDialog(dialog)
}
