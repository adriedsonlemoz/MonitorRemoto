package br.dev.monitorremoto.ui

import br.dev.monitorremoto.core.RecentDevices
import br.dev.monitorremoto.core.SettingsStore

internal fun MainActivity.disconnectCurrent() {
    val host = SettingsStore.getRemoteHost(this)
    remoteConnection.disconnect(host)
    SettingsStore.setRemoteHost(this, "")
    remoteConnection.clear()
    mode = 2
    render()
}

/** Keep Activity visibility/lifecycle checks here, and network/state in RemoteConnectionManager. */
internal fun MainActivity.refreshRemote(silent: Boolean) {
    if (mode != 1) return
    val host = SettingsStore.getRemoteHost(this)
    remoteConnection.fetch(
        host = host,
        stillVisible = { active && mode == 1 && !isFinishing && !isDestroyed && SettingsStore.getRemoteHost(this) == host },
    ) { finished, fresh ->
        if (finished) {
            if (fresh != null) {
                val name = fresh.optJSONObject("battery")?.optString("device").orEmpty()
                currentDeviceTitle?.text = RecentDevices.list(this).firstOrNull { it.host == host }
                    ?.name ?: name.ifBlank { host }
                displayResult(fresh)
            } else if (remoteConnection.cachedHost == host) {
                remoteConnection.cached?.let { displayResult(it, fromCache = true) }
            } else if (!silent && remoteConnection.state == "offline") {
                resultBox?.removeAllViews()
                resultBox?.addView(label(remoteConnection.message ?: "Não foi possível consultar o aparelho. Confira Wi-Fi/VPN, endereço e chave.", 13f, muted = true))
            }
        }
        updateConnectionLabels()
    }
}
