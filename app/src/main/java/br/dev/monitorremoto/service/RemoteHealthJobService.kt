package br.dev.monitorremoto.service

import android.app.job.JobParameters
import android.app.job.JobService
import br.dev.monitorremoto.core.RecentDevices
import br.dev.monitorremoto.core.SettingsStore
import br.dev.monitorremoto.core.ControllerHealthAlerts
import br.dev.monitorremoto.transport.RemoteClient
import kotlin.concurrent.thread

/** Poll somente de aparelho salvo escolhido, nunca tenta reiniciar monitoramento sem autorização. */
class RemoteHealthJobService : JobService() {
    @Volatile private var stopped = false
    override fun onStartJob(params: JobParameters): Boolean {
        if (!SettingsStore.controllerHealthAlerts(this)) return false
        stopped = false
        thread(name = "MonitorRemoto-health-job") {
            try {
                val host = SettingsStore.getRemoteHost(applicationContext)
                val key = RecentDevices.key(applicationContext, host)
                if (!stopped && host.isNotBlank() && key != null) {
                    runCatching { RemoteClient.snapshot(host, key, applicationContext) }
                        .onSuccess { snap -> if (!stopped) ControllerHealthAlerts.onSnapshot(applicationContext, host, snap) }
                }
            } finally { if (!stopped) jobFinished(params, false) }
        }
        return true
    }
    override fun onStopJob(params: JobParameters): Boolean {
        stopped = true
        return false
    }
}
