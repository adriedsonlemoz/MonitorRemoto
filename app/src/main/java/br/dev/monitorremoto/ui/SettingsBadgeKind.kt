package br.dev.monitorremoto.ui

/** Estado real fornecido pela tela, nunca inferido de rótulos ou subtítulos. */
internal enum class SettingsBadgeKind(val label: String) {
    ACTIVE("ATIVADO"),
    INACTIVE("DESATIVADO"),
    ACTION("ABRIR"),
    WARNING("REVOGAR"),
    DANGER("REVOGAR"),
    NEUTRAL("VOLTAR");

    companion object {
        fun forEnabled(enabled: Boolean): SettingsBadgeKind = if (enabled) ACTIVE else INACTIVE
    }
}
