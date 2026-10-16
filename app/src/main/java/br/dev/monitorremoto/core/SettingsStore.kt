package br.dev.monitorremoto.core

import android.content.Context
import java.util.UUID
import br.dev.monitorremoto.capture.NotificationReplyActions

object SettingsStore {
    private const val FILE = "monitor_remoto_settings"
    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun seenNewsVersion(context: Context) = prefs(context).getString("seen_news_version", "").orEmpty()
    fun markNewsSeen(context: Context, version: String) = prefs(context).edit().putString("seen_news_version", version).apply()
    fun localHealthAlerts(context: Context) = prefs(context).getBoolean("health_alerts_local", true)
    fun setLocalHealthAlerts(context: Context, enabled: Boolean) = prefs(context).edit().putBoolean("health_alerts_local", enabled).apply()
    fun thermalProtection(context: Context) = prefs(context).getBoolean("thermal_protection", false)
    fun setThermalProtection(context: Context, enabled: Boolean) = prefs(context).edit().putBoolean("thermal_protection", enabled).apply()
    fun controllerHealthAlerts(context: Context) = prefs(context).getBoolean("health_alerts_controller", false)
    fun setControllerHealthAlerts(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean("health_alerts_controller", enabled).apply()
        br.dev.monitorremoto.service.RemoteHealthScheduler.refresh(context)
    }

    /** Perfil da sessão de localização, nunca inicia o GPS por si só. */
    fun locationProfile(context: Context): String = prefs(context).getString("location_profile", "economico") ?: "economico"
    fun setLocationProfile(context: Context, profile: String) {
        require(profile in setOf("economico", "equilibrado", "intermediario", "preciso"))
        prefs(context).edit().putString("location_profile", profile).apply()
    }

    fun isMonitorEnabled(context: Context) = prefs(context).getBoolean("monitor_enabled", false)
    fun setMonitorEnabled(context: Context, enabled: Boolean) {
        val preferences = prefs(context)
        val wasEnabled = preferences.getBoolean("monitor_enabled", false)
        preferences.edit().putBoolean("monitor_enabled", enabled).apply()
        // Um comando Parar cancela inclusive as futuras verificações agendadas.
        br.dev.monitorremoto.service.RecoveryScheduler.refresh(context)
        if (!enabled) recordRecoveryEvent(context, "Monitoramento interrompido localmente")
        // Nunca reutilizar eventos da sessão anterior, mesmo com uma captura em trânsito.
        if (!enabled) {
            br.dev.monitorremoto.service.LocationShareService.stop(context)
            br.dev.monitorremoto.capture.CameraFrameStore.stop()
            RemotePhysicalControls.stopSound()
            RemotePhysicalControls.stopOwnedTorch()
        }
        if (!enabled || !wasEnabled) {
            EventJournal.clear()
            NotificationReplyActions.clear()
            SmsLiveJournal.clear()
            SmsDeliveryJournal.clear()
            br.dev.monitorremoto.capture.NotificationCaptureStatus.resetCaptureMetrics()
        }
        if (enabled && !wasEnabled) {
            br.dev.monitorremoto.capture.NotificationCaptureService.recoverActiveAfterLocalStart()
        }
    }

    fun notificationAllowlist(context: Context): Set<String> =
        prefs(context).getStringSet("notification_allowlist", emptySet())?.toSet() ?: emptySet()
    fun allNotifications(context: Context): Boolean = prefs(context).getBoolean("all_notifications", false)
    fun setAllNotifications(context: Context, value: Boolean) {
        if (value == allNotifications(context)) return
        prefs(context).edit().putBoolean("all_notifications", value).apply()
        EventJournal.clear()
        NotificationReplyActions.clear()
    }

    /** Leitura sob demanda da lista de aplicativos com ícone de launcher; desativada por padrão. */
    fun allowRemoteAppLaunch(context: Context) = prefs(context).getBoolean("remote_app_launch", false)
    fun setAllowRemoteAppLaunch(context: Context, enabled: Boolean) =
        prefs(context).edit().putBoolean("remote_app_launch", enabled).apply()

    fun shareInstalledApps(context: Context) = prefs(context).getBoolean("share_installed_apps", false)
    fun setShareInstalledApps(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean("share_installed_apps", enabled)
            .apply { if (!enabled) putBoolean("remote_app_launch", false) }.apply()
    }

    fun receiveSmsEvents(context: Context): Boolean = prefs(context).getBoolean("receive_sms_events", false)
    fun setReceiveSmsEvents(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean("receive_sms_events", value).apply()
        if (!value) SmsLiveJournal.clear()
    }

    fun setNotificationAllowlist(context: Context, packages: Set<String>) {
        // Retirar a autorização também apaga os eventos já capturados desses apps.
        prefs(context).edit().putStringSet("notification_allowlist", packages.toSet()).apply()
        if (!allNotifications(context)) {
            EventJournal.retainAllowed(packages)
            NotificationReplyActions.retainAllowed(packages)
        }
    }

    /** Separadamente consentidas no aparelho monitorado; OFF por padrão. */
    fun shareSmsInbox(context: Context) = prefs(context).getBoolean("share_sms_inbox", false)
    fun setShareSmsInbox(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean("share_sms_inbox", value).apply()

    fun allowSmsReplies(context: Context) = prefs(context).getBoolean("allow_sms_replies", false)
    fun setAllowSmsReplies(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean("allow_sms_replies", value).apply()

    fun allowNotificationReplies(context: Context) = prefs(context).getBoolean("allow_notification_replies", false)
    fun setAllowNotificationReplies(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean("allow_notification_replies", value).apply()
        if (!value) NotificationReplyActions.clear()
    }

    /** Opções locais para sessão de tela: OFF por padrão e independentes do pareamento. */
    fun allowRemoteTouch(context: Context) = prefs(context).getBoolean("remote_touch", false)
    fun setAllowRemoteTouch(context: Context, enabled: Boolean) =
        prefs(context).edit().putBoolean("remote_touch", enabled).apply()

    /** Autoriza SOMENTE acender a tela; não remove bloqueio seguro. Desligado por padrão. */
    fun allowRemoteWake(context: Context) = prefs(context).getBoolean("remote_wake", false)
    fun setAllowRemoteWake(context: Context, enabled: Boolean) =
        prefs(context).edit().putBoolean("remote_wake", enabled).apply()

    /** Ações físicas são autorizadas separadamente no telefone monitorado; OFF por padrão. */
    fun allowRemoteSound(context: Context) = prefs(context).getBoolean("remote_sound", false)
    fun setAllowRemoteSound(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean("remote_sound", enabled).apply()
        if (!enabled) RemotePhysicalControls.stopSound()
    }
    fun allowRemoteTorch(context: Context) = prefs(context).getBoolean("remote_torch", false)
    fun setAllowRemoteTorch(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean("remote_torch", enabled).apply()
        if (!enabled) RemotePhysicalControls.stopOwnedTorch()
    }

    /** O administrador Android e este opt-in são requisitos independentes; OFF por padrão. */
    fun allowRemoteLock(context: Context) = prefs(context).getBoolean("remote_lock", false)
    fun setAllowRemoteLock(context: Context, enabled: Boolean) =
        prefs(context).edit().putBoolean("remote_lock", enabled).apply()

    /** No controlador: solicitar acordar uma vez ao abrir Ver tela. OFF por padrão. */
    fun autoWakeOnView(context: Context) = prefs(context).getBoolean("auto_wake_view", false)
    fun setAutoWakeOnView(context: Context, enabled: Boolean) =
        prefs(context).edit().putBoolean("auto_wake_view", enabled).apply()

    fun captureInternalAudio(context: Context) = prefs(context).getBoolean("internal_audio", false)
    fun setCaptureInternalAudio(context: Context, enabled: Boolean) =
        prefs(context).edit().putBoolean("internal_audio", enabled).apply()

    /** Ativação explícita, separada da permissão de sistema para acesso ao número da linha. */
    fun sharePhoneNumber(context: Context) = prefs(context).getBoolean("share_phone_number", false)
    fun setSharePhoneNumber(context: Context, enabled: Boolean) =
        prefs(context).edit().putBoolean("share_phone_number", enabled).apply()

    /** Supervisor state, metadata only; no IP, pairing secret, or messages. */
    fun recordServerHealth(context: Context, state: String, failures: Int) {
        require(state in setOf("ativo", "aguardando", "recuperando", "parado"))
        val previous = prefs(context)
        if (previous.getString("server_health_state", "") == state &&
            previous.getInt("server_failure_count", 0) == failures &&
            System.currentTimeMillis() - previous.getLong("server_last_check_ms", 0L) < 60_000L) return
        previous.edit()
            .putString("server_health_state", state)
            .putInt("server_failure_count", failures)
            .putLong("server_last_check_ms", System.currentTimeMillis())
            .apply()
    }
    fun serverHealthState(context: Context) = prefs(context).getString("server_health_state", "não verificado").orEmpty()
    fun serverFailureCount(context: Context) = prefs(context).getInt("server_failure_count", 0)
    fun serverLastCheck(context: Context) = prefs(context).getLong("server_last_check_ms", 0L)

    /** Opt-in adicional, sem tentar burlar force-stop ou reiniciar projeção de tela. */
    fun recoveryJobsEnabled(context: Context) = prefs(context).getBoolean("recovery_jobs", false)
    fun setRecoveryJobs(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean("recovery_jobs", enabled).apply()
        br.dev.monitorremoto.service.RecoveryScheduler.refresh(context)
        recordRecoveryEvent(context, if (enabled) "Verificação periódica autorizada" else "Verificação periódica desativada")
    }
    fun recordRecoveryEvent(context: Context, description: String) {
        prefs(context).edit()
            .putString("recovery_last_event", description.take(110))
            .putLong("recovery_last_event_ms", System.currentTimeMillis())
            .apply()
    }
    fun recoveryLastEvent(context: Context) = prefs(context).getString("recovery_last_event", "Nenhum evento registrado").orEmpty()
    fun recoveryLastEventMs(context: Context) = prefs(context).getLong("recovery_last_event_ms", 0L)

    fun autoStartEnabled(context: Context) = prefs(context).getBoolean("autostart", false)
    fun setAutoStart(context: Context, enabled: Boolean) = prefs(context).edit().putBoolean("autostart", enabled).apply()

    /** Aparência: claro por padrão; nunca depende de tema escolhido no sistema. */
    fun theme(context: Context) = prefs(context).getString("theme", "light") ?: "light"
    fun setTheme(context: Context, value: String) {
        require(value in setOf("light", "dark", "system"))
        prefs(context).edit().putString("theme", value).apply()
    }

    /** Pseudonymous client identifier is not a secret or proof of device identity. */
    @Synchronized fun controllerId(context: Context): String {
        val preferences = prefs(context)
        val known = preferences.getString("controller_id", null)
        if (!known.isNullOrBlank()) return known
        val generated = UUID.randomUUID().toString()
        preferences.edit().putString("controller_id", generated).commit()
        return generated
    }

    /** Somente a árvore SAF explicitamente concedida no monitorado; vazia = desativado. */
    fun sharedDataTree(context: Context): String =
        prefs(context).getString("shared_data_tree", "").orEmpty()
    fun setSharedDataTree(context: Context, uri: String) =
        prefs(context).edit().putString("shared_data_tree", uri).apply()

    /** Apagamento/upload/renomeação somente após consentimento local independente da leitura. */
    fun allowRemoteFileWrite(context: Context): Boolean =
        prefs(context).getBoolean("remote_file_write", false)
    fun setAllowRemoteFileWrite(context: Context, allowed: Boolean) =
        prefs(context).edit().putBoolean("remote_file_write", allowed).apply()

    fun getRemoteHost(context: Context) = prefs(context).getString("remote_host", "").orEmpty()
    fun setRemoteHost(context: Context, host: String) = prefs(context).edit().putString("remote_host", host).apply()
}
