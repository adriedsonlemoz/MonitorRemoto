package br.dev.monitorremoto.capture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import br.dev.monitorremoto.core.SettingsStore
import br.dev.monitorremoto.ui.MainActivity

/** Um controlador pareado pode solicitar uma notificação; só o monitorado inicia o consentimento. */
object ScreenShareRequest {
    private const val CHANNEL = "monitor_remoto_pedidos_tela"
    @Volatile private var last = 0L
    @Synchronized fun notifyLocal(context: Context): Boolean {
        if (!SettingsStore.isMonitorEnabled(context) || ScreenFrameStore.sharing) return false
        val now = SystemClock.elapsedRealtime()
        if (last != 0L && now - last < 60_000L) return false
        last = now
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL,
            "Solicitações de visualização", NotificationManager.IMPORTANCE_HIGH))
        val target = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_MONITOR, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val open = PendingIntent.getActivity(context, 55, target,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.notify(59, Notification.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("Solicitação para ver a tela")
            .setContentText("Abra o Monitor Remoto e toque em Compartilhar tela para autorizar no Android.")
            .setContentIntent(open).setAutoCancel(true).build())
        return true
    }
}
