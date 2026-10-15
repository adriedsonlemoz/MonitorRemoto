package br.dev.monitorremoto.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.Manifest
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import br.dev.monitorremoto.core.EventJournal
import br.dev.monitorremoto.core.SettingsStore
import br.dev.monitorremoto.security.SecretVault
import br.dev.monitorremoto.transport.AuthenticatedAccess
import br.dev.monitorremoto.transport.MonitorServer
import br.dev.monitorremoto.transport.PairingSession
import br.dev.monitorremoto.ui.MainActivity
import br.dev.monitorremoto.ui.IncomingAccessActivity
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkRequest
import android.util.Log

class MonitorService : Service() {
    companion object {
        const val CHANNEL = "monitor_remoto_ativo"
        const val ACCESS_CHANNEL = "monitor_remoto_acessos"
        const val ACCESS_NOTIFICATION_ID = 43
        const val ACTION_STOP = "br.dev.monitorremoto.STOP"
        @Volatile var isRunning = false
            private set
        /** Não confundir listener TCP fora do ar com serviço Android encerrado. */
        @Volatile var isServiceActive = false
            private set

        fun start(context: Context) {
            val intent = Intent(context, MonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }
    }

    private var server: MonitorServer? = null
    private var stoppedExplicitly = false
    @Volatile private var serviceActive = false
    private var consecutiveFailures = 0
    private var nextRecoveryMs = 0L
    private var networkCallbackRegistered = false
    private val networkManager by lazy { getSystemService(ConnectivityManager::class.java) }
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            mainHandler.post {
                if (serviceActive && SettingsStore.isMonitorEnabled(this@MonitorService)) ensureServerReady()
            }
        }
    }
    private val serverWatchdog = object : Runnable {
        override fun run() {
            if (!serviceActive || !SettingsStore.isMonitorEnabled(this@MonitorService)) return
            ensureServerReady()
            mainHandler.postDelayed(this, 15_000L)
        }
    }
    private val accessAlerts = mutableSetOf<String>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val accessListener: () -> Unit = {
        mainHandler.post { refreshAccessAlert() }
    }
    private val alertCleanup = object : Runnable {
        override fun run() {
            refreshAccessAlert()
            if (isRunning) mainHandler.postDelayed(this, 10_000L)
        }
    }
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(
            CHANNEL, "Monitoramento ativo", NotificationManager.IMPORTANCE_LOW
        ))
        manager.createNotificationChannel(NotificationChannel(
            ACCESS_CHANNEL, "Acesso remoto detectado", NotificationManager.IMPORTANCE_DEFAULT
        ))
        br.dev.monitorremoto.core.RemotePhysicalControls.start(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP || !SettingsStore.isMonitorEnabled(this)) {
            stoppedExplicitly = true
            serviceActive = false
            isServiceActive = false
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
        serviceActive = true
        isServiceActive = true
        stoppedExplicitly = false
        RecoveryScheduler.refresh(this)
        SettingsStore.recordRecoveryEvent(this, "Serviço iniciado ou restaurado")
        getSystemService(NotificationManager::class.java)?.cancel(98)
        registerConnectivityWatch()
        // Serviço ativo não significa porta disponível; o supervisor valida ambos.
        ensureServerReady()
        mainHandler.removeCallbacks(serverWatchdog)
        mainHandler.postDelayed(serverWatchdog, 15_000L)
        AuthenticatedAccess.unsubscribe(accessListener)
        AuthenticatedAccess.subscribe(accessListener)
        mainHandler.removeCallbacks(alertCleanup)
        mainHandler.postDelayed(alertCleanup, 10_000L)
        return START_STICKY
    }

    /** Reinicia somente o listener TCP; não tenta abrir Activities nem revogar consentimento. */
    private fun ensureServerReady() {
        if (!serviceActive || !SettingsStore.isMonitorEnabled(this)) return
        if (server?.isHealthy() == true) {
            isRunning = true
            consecutiveFailures = 0
            nextRecoveryMs = 0L
            SettingsStore.recordServerHealth(this, "ativo", 0)
            return
        }
        isRunning = false
        val now = SystemClock.elapsedRealtime()
        if (!RecoveryPolicy.canRetry(now, nextRecoveryMs)) return
        server?.stop()
        server = null
        try {
            // Acesso local ao Keystore: falha não deve apagar a autorização do usuário.
            SecretVault.getOrCreateMonitorKey(this)
            val candidate = MonitorServer(applicationContext)
            candidate.start()
            server = candidate
            // isHealthy() será confirmado no próximo ciclo se a thread ainda estiver iniciando.
            isRunning = true
            consecutiveFailures = 0
            nextRecoveryMs = 0L
            SettingsStore.recordServerHealth(this, "recuperando", 0)
        } catch (error: Exception) {
            consecutiveFailures = (consecutiveFailures + 1).coerceAtMost(20)
            val delayMs = RecoveryPolicy.nextDelayMs(consecutiveFailures)
            nextRecoveryMs = now + delayMs
            SettingsStore.recordServerHealth(this, "aguardando", consecutiveFailures)
            Log.w("MonitorRemotoService", "Servidor indisponível; nova tentativa em ${delayMs}ms", error)
        }
    }

    private fun registerConnectivityWatch() {
        if (networkCallbackRegistered) return
        try {
            networkManager.registerNetworkCallback(NetworkRequest.Builder().build(), networkCallback)
            networkCallbackRegistered = true
        } catch (error: Exception) {
            Log.w("MonitorRemotoService", "Sem callback de rede; supervisor periódico continua", error)
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Remover a tarefa visual não equivale ao usuário tocar em Parar.
        // START_STICKY mantém a possibilidade de recriação pelo sistema.
        SettingsStore.recordRecoveryEvent(this, "Janela do app removida; serviço deve continuar")
        super.onTaskRemoved(rootIntent)
    }

    private fun refreshAccessAlert() {
        val current = AuthenticatedAccess.recent()
        val fresh = current.filter { accessAlerts.add("${it.key}:${it.sinceMs}") }
        if (current.isEmpty()) {
            (getSystemService(NotificationManager::class.java))?.cancel(ACCESS_NOTIFICATION_ID)
            return
        }
        if (fresh.isEmpty()) return // no alert spam from 15-second polling
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val launch = PendingIntent.getActivity(this, 44,
            Intent(this, IncomingAccessActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            ), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val note = Notification.Builder(this, ACCESS_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("Acesso remoto autorizado")
            .setContentText("Outro aparelho consultou os dados. Toque para ver detalhes.")
            .setContentIntent(launch)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .build()
        getSystemService(NotificationManager::class.java)?.notify(ACCESS_NOTIFICATION_ID, note)
    }

    override fun onDestroy() {
        serviceActive = false
        isServiceActive = false
        if (!stoppedExplicitly && SettingsStore.isMonitorEnabled(this)) {
            SettingsStore.recordRecoveryEvent(this, "Serviço Android encerrado; aguardando retomada")
            RecoveryScheduler.refresh(this)
        }
        br.dev.monitorremoto.capture.ScreenShareService.stop(this)
        br.dev.monitorremoto.core.RemotePhysicalControls.shutdown()
        mainHandler.removeCallbacks(serverWatchdog)
        if (networkCallbackRegistered) {
            runCatching { networkManager.unregisterNetworkCallback(networkCallback) }
            networkCallbackRegistered = false
        }
        mainHandler.removeCallbacks(alertCleanup)
        AuthenticatedAccess.unsubscribe(accessListener)
        getSystemService(NotificationManager::class.java)?.cancel(ACCESS_NOTIFICATION_ID)
        accessAlerts.clear()
        // O processo pode continuar vivo após a interrupção do serviço.
        EventJournal.clear()
        PairingSession.revoke()
        AuthenticatedAccess.clear()
        server?.stop()
        server = null
        isRunning = false
        SettingsStore.recordServerHealth(this, "parado", 0)
        super.onDestroy()
    }
}
