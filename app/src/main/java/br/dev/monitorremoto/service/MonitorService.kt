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
import br.dev.monitorremoto.core.MonitorNotificationPolicy
import br.dev.monitorremoto.core.LocationShareStore
import br.dev.monitorremoto.core.EventJournal
import br.dev.monitorremoto.core.SettingsStore
import br.dev.monitorremoto.security.SecretVault
import br.dev.monitorremoto.transport.AuthenticatedAccess
import br.dev.monitorremoto.transport.MonitorServer
import br.dev.monitorremoto.transport.PairingSession
import br.dev.monitorremoto.ui.MainActivity
import br.dev.monitorremoto.ui.IncomingAccessActivity
import br.dev.monitorremoto.ui.MonitorStatusActivity
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
            refreshMainNotification()
            mainHandler.postDelayed(this, 15_000L)
        }
    }
    private val healthWatchdog = object : Runnable {
        override fun run() {
            if (!serviceActive || !SettingsStore.isMonitorEnabled(this@MonitorService)) return
            runCatching { br.dev.monitorremoto.core.HealthAlertMonitor.check(this@MonitorService) }
                .onFailure { Log.w("MonitorRemotoHealth", "Leitura de saúde indisponível", it) }
            refreshMainNotification()
            mainHandler.postDelayed(this, 30_000L)
        }
    }
    private val accessAlerts = mutableSetOf<String>()
    private var foregroundStarted = false
    private var lastNotificationSignature: String? = null
    private var lastControllerSeenMs = 0L
    private val mainNotificationId = 42
    private val mainHandler = Handler(Looper.getMainLooper())
    private val accessListener: () -> Unit = {
        mainHandler.post {
            refreshAccessAlert()
            refreshMainNotification()
        }
    }
    private val alertCleanup = object : Runnable {
        override fun run() {
            refreshAccessAlert()
            refreshMainNotification()
            if (serviceActive) mainHandler.postDelayed(this, 10_000L)
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
        if (br.dev.monitorremoto.core.FixedDownloadsFolder.supported()) {
            kotlin.concurrent.thread(name = "MonitorRemoto-prepare-downloads") {
                runCatching {
                    // Revoga apenas o grant legado SAF, não apaga arquivos do usuário.
                    if (SettingsStore.sharedDataTree(applicationContext).isNotBlank())
                        br.dev.monitorremoto.core.SharedDataFolder.revoke(applicationContext)
                    br.dev.monitorremoto.core.FixedDownloadsFolder.ensure(applicationContext)
                }
                    .onFailure { Log.w("MonitorRemotoFiles", "Pasta padrão indisponível", it) }
            }
        }
        br.dev.monitorremoto.core.RemotePhysicalControls.start(applicationContext)
        br.dev.monitorremoto.core.DeviceStateReader.start(applicationContext)
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
        val notification = buildMainNotification(MonitorNotificationPolicy.State(
            serviceActive = true, serverReady = false, recentAccessCount = 0,
            locationActive = false, screenActive = false, cameraActive = false,
            alertsEnabled = SettingsStore.localHealthAlerts(this) && notificationsAllowed()
        ), "")
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(mainNotificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else startForeground(mainNotificationId, notification)
        foregroundStarted = true
        lastNotificationSignature = null
        serviceActive = true
        isServiceActive = true
        stoppedExplicitly = false
        RecoveryScheduler.refresh(this)
        SettingsStore.recordRecoveryEvent(this, "Serviço iniciado ou restaurado")
        getSystemService(NotificationManager::class.java)?.cancel(98)
        registerConnectivityWatch()
        // Serviço ativo não significa porta disponível; o supervisor valida ambos.
        ensureServerReady()
        // Persistent recovery location is allowed only with owner opt-in + Android background grant.
        LocationRecoveryStarter.tryStart(this)
        refreshMainNotification()
        mainHandler.removeCallbacks(serverWatchdog)
        mainHandler.postDelayed(serverWatchdog, 15_000L)
        mainHandler.removeCallbacks(healthWatchdog)
        mainHandler.post(healthWatchdog)
        AuthenticatedAccess.unsubscribe(accessListener)
        AuthenticatedAccess.subscribe(accessListener)
        mainHandler.removeCallbacks(alertCleanup)
        mainHandler.postDelayed(alertCleanup, 10_000L)
        return START_STICKY
    }

    private fun notificationsAllowed(): Boolean = Build.VERSION.SDK_INT < 33 ||
        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun buildMainNotification(state: MonitorNotificationPolicy.State, accessLine: String): Notification {
        val open = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_MONITOR, true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val details = PendingIntent.getActivity(this, 76,
            Intent(this, MonitorStatusActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1,
            Intent(this, MonitorService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val summary = MonitorNotificationPolicy.headline(state)
        val modules = MonitorNotificationPolicy.modules(state)
        val expanded = listOf(summary, accessLine, modules).filter { it.isNotBlank() }.joinToString("\n")
        val privateNotification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(br.dev.monitorremoto.R.drawable.ic_stat_shield)
            .setContentTitle("Monitoramento protegido")
            .setContentText(summary + if (state.locationActive) " · Localização ativa" else "")
            .setStyle(Notification.BigTextStyle().bigText(expanded))
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .addAction(Notification.Action.Builder(null, "Parar", stop).build())
            .addAction(Notification.Action.Builder(null, "Detalhes", details).build())
        // Tela de bloqueio recebe texto genérico, sem nome/IP do controlador ou módulos.
        val publicNotification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(br.dev.monitorremoto.R.drawable.ic_stat_shield)
            .setContentTitle("Monitor Remoto ativo")
            .setContentText("Serviço de monitoramento")
            .build()
        return privateNotification.setPublicVersion(publicNotification).build()
    }

    private fun refreshMainNotification() {
        if (!serviceActive || !foregroundStarted || !SettingsStore.isMonitorEnabled(this)) return
        val peers = AuthenticatedAccess.recent()
        val recent = peers.firstOrNull()
        if (recent != null && recent.lastSeenMs >= lastControllerSeenMs) {
            lastControllerSeenMs = recent.lastSeenMs
        }
        val ready = isRunning && server?.isHealthy() == true
        val state = MonitorNotificationPolicy.State(
            serviceActive = true, serverReady = ready, recentAccessCount = peers.size,
            locationActive = LocationShareService.running && LocationShareStore.active(),
            screenActive = br.dev.monitorremoto.capture.ScreenFrameStore.sharing,
            cameraActive = br.dev.monitorremoto.capture.CameraFrameStore.isActive(),
            alertsEnabled = SettingsStore.localHealthAlerts(this) && notificationsAllowed()
        )
        val accessLine = if (recent != null) {
            "Nome informado: ${recent.name.ifBlank { "Não informado" }.take(48)} · Última consulta: " +
                android.text.format.DateFormat.format("HH:mm", recent.lastSeenMs)
        } else if (lastControllerSeenMs > 0L) {
            "Último acesso: " + android.text.format.DateFormat.format("HH:mm", lastControllerSeenMs)
        } else ""
        val key = listOf(state, accessLine).joinToString("|")
        if (key == lastNotificationSignature) return
        lastNotificationSignature = key
        getSystemService(NotificationManager::class.java)
            ?.notify(mainNotificationId, buildMainNotification(state, accessLine))
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
            .setSmallIcon(br.dev.monitorremoto.R.drawable.ic_stat_shield)
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
        // Localização nunca permanece ativa após perda do serviço principal.
        LocationShareService.stop(this)
        PhotoReadyService.stop(this)
        br.dev.monitorremoto.core.DeviceStateReader.stop()
        foregroundStarted = false
        lastNotificationSignature = null
        lastControllerSeenMs = 0L
        serviceActive = false
        isServiceActive = false
        if (!stoppedExplicitly && SettingsStore.isMonitorEnabled(this)) {
            SettingsStore.recordRecoveryEvent(this, "Serviço Android encerrado; aguardando retomada")
            RecoveryScheduler.refresh(this)
        }
        br.dev.monitorremoto.capture.ScreenShareService.stop(this)
        br.dev.monitorremoto.capture.CameraFrameStore.stop()
        br.dev.monitorremoto.core.RemotePhysicalControls.shutdown()
        mainHandler.removeCallbacks(serverWatchdog)
        mainHandler.removeCallbacks(healthWatchdog)
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
