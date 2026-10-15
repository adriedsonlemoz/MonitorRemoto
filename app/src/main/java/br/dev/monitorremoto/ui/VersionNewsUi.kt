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
        "Localização remota" to "Compartilhamento iniciado somente no monitorado, com notificação permanente e perfis de bateria.",
        "Mapa e coordenadas" to "O controlador pode consultar, copiar e abrir coordenadas no app de mapas instalado.",
        "Privacidade" to "Posições ficam apenas na memória da sessão; parar monitoramento cancela o compartilhamento.",
        "Compatibilidade" to "Instale a alpha.75 em ambos aparelhos. Permissões de localização e notificação são solicitadas localmente."
    )
    entries.forEach { (title, details) ->
        layout.addView(dialogCardRow(title, details, ""))
        sectionGap(layout, 7)
    }
    layout.addView(label("A tela apagada não significa GPS ativo: verifique a notificação de compartilhamento. Intervalos podem sofrer atraso do Android.", 12f, muted = true))
    val dialog = AlertDialog.Builder(this).setView(wrapper)
        .setPositiveButton("Entendi") { _, _ -> SettingsStore.markNewsSeen(this, AppVersion.NAME) }
        .create().apply { setCancelable(false); setCanceledOnTouchOutside(false) }
    showManagedDialog(dialog)
}
