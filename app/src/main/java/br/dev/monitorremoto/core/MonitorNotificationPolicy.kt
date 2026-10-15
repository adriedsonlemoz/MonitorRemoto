package br.dev.monitorremoto.core

/** Síntese sem dependências Android, para não confundir acesso autenticado com conexão TCP permanente. */
internal object MonitorNotificationPolicy {
    data class State(
        val serviceActive: Boolean,
        val serverReady: Boolean,
        val recentAccessCount: Int,
        val locationActive: Boolean,
        val screenActive: Boolean,
        val cameraActive: Boolean,
        val alertsEnabled: Boolean,
    )

    fun headline(state: State): String = when {
        !state.serviceActive -> "Monitoramento parado"
        !state.serverReady -> "Serviço ativo · reconectando"
        state.recentAccessCount == 1 -> "1 acesso recente"
        state.recentAccessCount > 1 -> "${state.recentAccessCount} acessos recentes"
        else -> "Aguardando controlador"
    }

    fun modules(state: State): String = buildList {
        if (state.locationActive) add("Localização")
        if (state.screenActive) add("Tela")
        if (state.cameraActive) add("Câmera")
        if (state.alertsEnabled) add("Alertas")
    }.joinToString(" · ").ifBlank { "Nenhum módulo adicional ativo" }
}
