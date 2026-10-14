package br.dev.monitorremoto.core

import android.content.Context
import java.util.UUID
import br.dev.monitorremoto.capture.NotificationReplyActions

object SettingsStore {
    private const val FILE = "monitor_remoto_settings"
    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun isMonitorEnabled(context: Context) = prefs(context).getBoolean("monitor_enabled", false)
    fun setMonitorEnabled(context: Context, enabled: Boolean) {
        val preferences = prefs(context)
        val wasEnabled = preferences.getBoolean("monitor_enabled", false)
        preferences.edit().putBoolean("monitor_enabled", enabled).apply()
        // Nunca reutilizar eventos da sessão anterior, mesmo com uma captura em trânsito.
        if (!enabled || !wasEnabled) {
            EventJournal.clear()
            NotificationReplyActions.clear()
            SmsLiveJournal.clear()
            SmsDeliveryJournal.clear()
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

    /** Ativação explícita, separada da permissão de sistema para acesso ao número da linha. */
    fun sharePhoneNumber(context: Context) = prefs(context).getBoolean("share_phone_number", false)
    fun setSharePhoneNumber(context: Context, enabled: Boolean) =
        prefs(context).edit().putBoolean("share_phone_number", enabled).apply()

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

    fun getRemoteHost(context: Context) = prefs(context).getString("remote_host", "").orEmpty()
    fun setRemoteHost(context: Context, host: String) = prefs(context).edit().putString("remote_host", host).apply()
}
