package br.dev.monitorremoto.service

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import br.dev.monitorremoto.core.SettingsStore

/** Job periódico de melhor esforço. O Android pode adiar execuções por Doze/bateria. */
object RemoteHealthScheduler {
    const val JOB_ID = 2753
    fun refresh(context: Context) {
        val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
        if (!SettingsStore.controllerHealthAlerts(context)) {
            scheduler.cancel(JOB_ID)
            return
        }
        // Evita reiniciar o relógio de 15 min toda vez que a Activity é aberta.
        if (scheduler.getPendingJob(JOB_ID) != null) return
        val job = JobInfo.Builder(JOB_ID, ComponentName(context, RemoteHealthJobService::class.java))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setPeriodic(15L * 60_000L)
            .setPersisted(false)
            .build()
        scheduler.schedule(job)
    }
}
