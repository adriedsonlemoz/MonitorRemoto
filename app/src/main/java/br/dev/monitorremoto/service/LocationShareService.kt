package br.dev.monitorremoto.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import br.dev.monitorremoto.core.LocationSharePolicy
import br.dev.monitorremoto.core.LocationShareStore
import br.dev.monitorremoto.core.SettingsStore
import br.dev.monitorremoto.ui.MainActivity

/** GPS persistente apenas após autorização presencial; restauro exige permissão background. */
class LocationShareService : Service(), LocationListener {
    companion object {
        const val ACTION_START = "br.dev.monitorremoto.LOCATION_START"
        const val ACTION_STOP = "br.dev.monitorremoto.LOCATION_STOP"
        const val ACTION_RECOVER = "br.dev.monitorremoto.LOCATION_RECOVER"
        private const val CHANNEL = "monitor_remoto_localizacao"
        private const val ID = 89
        @Volatile var running = false
            private set
        fun stop(context: android.content.Context) {
            SettingsStore.setLocationRecoveryEnabled(context, false)
            LocationShareStore.stop()
            runCatching { context.stopService(Intent(context, LocationShareService::class.java)) }
        }
    }
    private val locationManager by lazy { getSystemService(LocationManager::class.java) }
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Localização compartilhada", NotificationManager.IMPORTANCE_LOW)
        )
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            SettingsStore.setLocationRecoveryEnabled(this, false)
            stopSelf()
            return START_NOT_STICKY
        }
        val recovery = intent?.action == ACTION_RECOVER || (intent == null && SettingsStore.locationRecoveryEnabled(this))
        val allowed = if (recovery) LocationRecoveryStarter.allowed(this)
            else intent?.action == ACTION_START && MonitorService.isRunning &&
                LocationShareStore.hasPermission(this) && LocationShareStore.locationEnabled(this)
        if (!allowed || !SettingsStore.isMonitorEnabled(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (running) return if (SettingsStore.locationRecoveryEnabled(this)) START_STICKY else START_NOT_STICKY
        val mode = SettingsStore.locationProfile(this)
        val open = PendingIntent.getActivity(this, 89, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 89,
            Intent(this, LocationShareService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val note = Notification.Builder(this, CHANNEL)
            .setSmallIcon(br.dev.monitorremoto.R.drawable.ic_stat_shield)
            .setContentTitle("Localização compartilhada")
            .setContentText("${LocationSharePolicy.label(mode)} · toque para gerenciar")
            .setContentIntent(open).setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Parar localização", stop).build())
            .build()
        try {
            if (Build.VERSION.SDK_INT >= 34) startForeground(ID, note, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            else startForeground(ID, note)
            // Só depois do FGS ativo a sessão pode publicar coordenadas.
            LocationShareStore.begin(mode)
            val fine = LocationShareStore.hasFinePermission(this)
            val requested = if (fine) listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
                else listOf(LocationManager.NETWORK_PROVIDER)
            var subscribed = false
            for (provider in requested) {
                // Não deixar a ausência de um provedor anular outro já registrado.
                val registered = runCatching {
                    if (!locationManager.isProviderEnabled(provider)) false else {
                        locationManager.requestLocationUpdates(provider, LocationSharePolicy.intervalMs(mode),
                            LocationSharePolicy.minDistanceM(mode), this, Looper.getMainLooper())
                        true
                    }
                }.getOrDefault(false)
                subscribed = subscribed || registered
            }
            if (!subscribed) { stopSelf(); return START_NOT_STICKY }
            running = true
        } catch (_: SecurityException) { stopSelf(); return START_NOT_STICKY }
        catch (_: Exception) { stopSelf(); return START_NOT_STICKY }
        return if (SettingsStore.locationRecoveryEnabled(this)) START_STICKY else START_NOT_STICKY
    }
    override fun onLocationChanged(location: Location) { LocationShareStore.update(location) }
    override fun onProviderDisabled(provider: String) { /* O snapshot informa se a localização foi desligada. */ }
    override fun onDestroy() {
        runCatching { locationManager.removeUpdates(this) }
        LocationShareStore.stop()
        running = false
        super.onDestroy()
    }
}
