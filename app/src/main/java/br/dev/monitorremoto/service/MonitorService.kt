package br.dev.monitorremoto.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import br.dev.monitorremoto.core.SettingsStore
import br.dev.monitorremoto.security.SecretVault
import br.dev.monitorremoto.transport.MonitorServer
import br.dev.monitorremoto.transport.PairingSession
import br.dev.monitorremoto.ui.MainActivity

class MonitorService : Service() {
    companion object {
        const val CHANNEL = "monitor_remoto_ativo"
        const val ACTION_STOP = "br.dev.monitorremoto.STOP"
        @Volatile var isRunning = false
            private set

        fun start(context: Context) {
            val intent = Intent(context, MonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }
    }

    private var server: MonitorServer? = null
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(
            CHANNEL, "Monitoramento ativo", NotificationManager.IMPORTANCE_LOW
        ))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP || !SettingsStore.isMonitorEnabled(this)) {
            SettingsStore.setMonitorEnabled(this, false)
            stopSelf()
            return START_NOT_STICKY
        }
        val openIntent = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(this, 1,
            Intent(this, MonitorService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle("Monitor Remoto ativo")
            .setContentText("Dados acessíveis somente com sua chave de pareamento")
            .setContentIntent(openIntent)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Parar", stopIntent).build())
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(42, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else startForeground(42, notification)
        if (server == null) {
            try {
                SecretVault.getOrCreateMonitorKey(this)
                server = MonitorServer(applicationContext).also { it.start() }
            } catch (_: Exception) {
                SettingsStore.setMonitorEnabled(this, false)
                stopSelf()
                return START_NOT_STICKY
            }
        }
        isRunning = true
        return START_STICKY
    }

    override fun onDestroy() {
        PairingSession.revoke()
        server?.stop()
        server = null
        isRunning = false
        super.onDestroy()
    }
}
