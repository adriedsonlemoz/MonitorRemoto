package br.dev.monitorremoto.core

import android.content.Context

object SettingsStore {
    private const val FILE = "monitor_remoto_settings"
    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun isMonitorEnabled(context: Context) = prefs(context).getBoolean("monitor_enabled", false)
    fun setMonitorEnabled(context: Context, enabled: Boolean) = prefs(context).edit().putBoolean("monitor_enabled", enabled).apply()

    fun isSmsEnabled(context: Context) = prefs(context).getBoolean("sms_enabled", false)
    fun setSmsEnabled(context: Context, enabled: Boolean) = prefs(context).edit().putBoolean("sms_enabled", enabled).apply()

    fun autoStartEnabled(context: Context) = prefs(context).getBoolean("autostart", false)
    fun setAutoStart(context: Context, enabled: Boolean) = prefs(context).edit().putBoolean("autostart", enabled).apply()

    fun getRemoteHost(context: Context) = prefs(context).getString("remote_host", "").orEmpty()
    fun setRemoteHost(context: Context, host: String) = prefs(context).edit().putString("remote_host", host).apply()
}
