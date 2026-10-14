package br.dev.monitorremoto.service

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.util.Log
import br.dev.monitorremoto.core.SettingsStore

/** Rede é opcional: mesmo sem Wi-Fi o listener local pode continuar saudável.
 *  A execução é inexata e pode atrasar sob Doze/restrições do fabricante.
 */
object RecoveryScheduler {
    private const val JOB_ID = 0x4d52 // único no aplicativo
    private const val INTERVAL_MS = 15 * 60 * 1000L

    fun refresh(context: Context) {
        if (SettingsStore.isMonitorEnabled(context) && SettingsStore.recoveryJobsEnabled(context)) schedule(context)
        else cancel(context)
    }

    fun schedule(context: Context) {
        if (!SettingsStore.isMonitorEnabled(context) || !SettingsStore.recoveryJobsEnabled(context)) return
        try {
            val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
            if (scheduler.allPendingJobs.any { it.id == JOB_ID }) return
            val info = JobInfo.Builder(JOB_ID, ComponentName(context, RecoveryJobService::class.java))
                .setPeriodic(INTERVAL_MS)
                // A opção de boot é independente. Não persistir o Job além do reboot.
                .setPersisted(false)
                .build()
            if (scheduler.schedule(info) != JobScheduler.RESULT_SUCCESS) {
                SettingsStore.recordRecoveryEvent(context, "Agendamento recusado pelo Android")
            }
        } catch (error: Exception) {
            Log.w("MonitorRecovery", "Não foi possível agendar verificação", error)
            SettingsStore.recordRecoveryEvent(context, "Falha ao agendar verificação")
        }
    }

    fun cancel(context: Context) {
        runCatching { context.getSystemService(JobScheduler::class.java)?.cancel(JOB_ID) }
    }
}
