package br.dev.monitorremoto.core

import android.content.Context
import java.util.UUID

object SettingsStore {
    private const val FILE = "monitor_remoto_settings"
    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun isMonitorEnabled(context: Context) = prefs(context).getBoolean("monitor_enabled", false)
    fun setMonitorEnabled(context: Context, enabled: Boolean) {
        val preferences = prefs(context)
        val wasEnabled = preferences.getBoolean("monitor_enabled", false)
        preferences.edit().putBoolean("monitor_enabled", enabled).apply()
        // Nunca reutilizar eventos da sessão anterior, mesmo com uma captura em trânsito.
        if (!enabled || !wasEnabled) EventJournal.clear()
    }

    fun notificationAllowlist(context: Context): Set<String> =
        prefs(context).getStringSet("notification_allowlist", emptySet())?.toSet() ?: emptySet()
    fun setNotificationAllowlist(context: Context, packages: Set<String>) {
        // Retirar a autorização também apaga os eventos já capturados desses apps.
        prefs(context).edit().putStringSet("notification_allowlist", packages.toSet()).apply()
        EventJournal.retainAllowed(packages)
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
