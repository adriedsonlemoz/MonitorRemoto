package br.dev.monitorremoto.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobParameters
import android.app.job.JobService
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import br.dev.monitorremoto.core.SettingsStore
import br.dev.monitorremoto.ui.MainActivity

/** Verificação de contingência, não watchdog de 15 s. O Android decide quando executar.
 *  Um JobService NÃO é isenção para iniciar um FGS em segundo plano.
 */
class RecoveryJobService : JobService() {
    companion object {
        private const val NOTICE_CHANNEL = "monitor_remoto_recuperacao"
        private const val NOTICE_ID = 98
    }

    override fun onStartJob(params: JobParameters?): Boolean {
        if (!RecoveryDecision.shouldAttempt(SettingsStore.isMonitorEnabled(this),
                SettingsStore.recoveryJobsEnabled(this), MonitorService.isServiceActive)) return false
        try {
            // Pode ser negado em Android 12+; nunca contornar restrições ou iniciar UI sozinho.
            MonitorService.start(this)
            SettingsStore.recordRecoveryEvent(this, "Recuperação solicitada pelo agendador")
        } catch (error: Exception) {
            Log.w("MonitorRecovery", "Android bloqueou retomada agendada", error)
            SettingsStore.recordRecoveryEvent(this, "Retomada automática restrita pelo Android")
            showManualResumeNotification()
        }
        return false // operação curta; sem processo em execução contínua no JobService
    }

    override fun onStopJob(params: JobParameters?): Boolean = false

    private fun showManualResumeNotification() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(NotificationChannel(NOTICE_CHANNEL,
            "Recuperação do monitoramento", NotificationManager.IMPORTANCE_DEFAULT))
        val intent = Intent(this, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_MONITOR, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val tap = PendingIntent.getActivity(this, NOTICE_ID, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.notify(NOTICE_ID, Notification.Builder(this, NOTICE_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Monitor Remoto precisa ser retomado")
            .setContentText("O Android impediu a retomada automática. Toque para reativar.")
            .setContentIntent(tap)
            .setAutoCancel(true)
            .build())
    }
}
