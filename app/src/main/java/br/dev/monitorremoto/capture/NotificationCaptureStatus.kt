package br.dev.monitorremoto.capture

/** Diagnóstico efêmero: apenas disponibilidade, nunca conteúdo ou pacote das notificações. */
object NotificationCaptureStatus {
    @Volatile var connected = false
        private set
    @Volatile var lastConnectedAt = 0L
        private set
    @Volatile var lastEventAt = 0L
        private set
    @Volatile var lastRecoveryCount = 0
        private set
    fun connected(activeNotifications: Int) {
        connected = true
        lastConnectedAt = System.currentTimeMillis()
        lastRecoveryCount = activeNotifications
    }
    fun disconnected() { connected = false }
    fun recorded() { lastEventAt = System.currentTimeMillis() }
    fun resetCaptureMetrics() { lastEventAt = 0L; lastRecoveryCount = 0 }
}
