package br.dev.monitorremoto.core

import android.content.Context

object SettingsStore {
    private const val FILE = "monitor_remoto_settings"
    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun isMonitorEnabled(context: Context) = prefs(context).getBoolean("monitor_enabled", false)
    fun setMonitorEnabled(context: Context, enabled: Boolean) = prefs(context).edit().putBoolean("monitor_enabled", enabled).apply()

    fun notificationAllowlist(context: Context): Set<String> =
        prefs(context).getStringSet("notification_allowlist", emptySet())?.toSet() ?: emptySet()
    fun setNotificationAllowlist(context: Context, packages: Set<String>) =
        prefs(context).edit().putStringSet("notification_allowlist", packages).apply()

    fun autoStartEnabled(context: Context) = prefs(context).getBoolean("autostart", false)
    fun setAutoStart(context: Context, enabled: Boolean) = prefs(context).edit().putBoolean("autostart", enabled).apply()

    fun getRemoteHost(context: Context) = prefs(context).getString("remote_host", "").orEmpty()
    fun setRemoteHost(context: Context, host: String) = prefs(context).edit().putString("remote_host", host).apply()
}
