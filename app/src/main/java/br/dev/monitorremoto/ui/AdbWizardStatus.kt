package br.dev.monitorremoto.ui

/** Estado honesto do wizard. Pareamento e credencial persistida não são conexão ADB. */
internal object AdbWizardStatus {
    enum class State { CONNECTED, PAIRED_NOT_CONNECTED, MONITORED_NOT_VERIFIABLE, DISCONNECTED }

    fun resolve(connected: Boolean, pairedNow: Boolean, monitoredRole: Boolean): State = when {
        connected -> State.CONNECTED
        pairedNow -> State.PAIRED_NOT_CONNECTED
        monitoredRole -> State.MONITORED_NOT_VERIFIABLE
        else -> State.DISCONNECTED
    }
}
