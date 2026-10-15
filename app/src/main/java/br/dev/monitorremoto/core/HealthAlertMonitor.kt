package br.dev.monitorremoto.core

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import br.dev.monitorremoto.capture.ScreenShareService
import br.dev.monitorremoto.capture.CameraFrameStore
import br.dev.monitorremoto.ui.MainActivity
import org.json.JSONObject

/** Apenas telemetria local. Nenhuma tentativa de controlar apps de terceiros. */
object HealthAlertMonitor {
    private const val PREF = "monitor_health_alerts"
    private const val CHANNEL = "monitor_health_safety"
    private fun prefs(c: Context) = c.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    @Synchronized fun check(context: Context) {
        val data = BatteryReader.snapshot(context)
        val level = if (data.isNull("levelPercent")) null else data.optInt("levelPercent")
        val temp = if (data.isNull("temperatureC")) null else data.optDouble("temperatureC")
        val thermal = if (Build.VERSION.SDK_INT >= 29) runCatching {
            context.getSystemService(PowerManager::class.java).currentThermalStatus
        }.getOrNull() else null
        val settings = prefs(context)
        val old = runCatching { HealthAlertPolicy.Level.valueOf(settings.getString("level", "NORMAL") ?: "NORMAL") }
            .getOrDefault(HealthAlertPolicy.Level.NORMAL)
        val next = HealthAlertPolicy.classify(level, temp, thermal, old)
        val now = System.currentTimeMillis()
        val previousReason = settings.getString("reason", "")
        val changed = next.level != old || (next.level != HealthAlertPolicy.Level.NORMAL && next.reason != previousReason)
        val seq = settings.getLong("seq", 0L)
        val shouldRecord = changed && (next.level != HealthAlertPolicy.Level.NORMAL || old != HealthAlertPolicy.Level.NORMAL)
        val protectionNeeded = SettingsStore.thermalProtection(context) &&
            HealthAlertPolicy.shouldProtect(thermal, next.level, temp)
        settings.edit().putString("level", next.level.name).putString("reason", next.reason)
            .putBoolean("protectedSevere", protectionNeeded)
            .putInt("percent", level ?: -1).putFloat("temp", temp?.toFloat() ?: Float.NaN)
            .putInt("thermal", thermal ?: -1)
            .putLong("seq", if (shouldRecord) seq + 1L else seq)
            .putLong("occurredMs", if (shouldRecord) now else settings.getLong("occurredMs", 0L))
            .apply()
        if (shouldRecord && next.level != HealthAlertPolicy.Level.NORMAL && SettingsStore.localHealthAlerts(context))
            notifyLocal(context, next, level, temp)
        // Opt-in: captura própria pode ser suspensa somente em condição severa.
        if (protectionNeeded) {
            ScreenShareService.stop(context)
            CameraFrameStore.stop()
        }
    }

    fun protectionTriggered(context: Context): Boolean =
        SettingsStore.thermalProtection(context) && prefs(context).getBoolean("protectedSevere", false)

    fun snapshot(context: Context): JSONObject {
        val v = prefs(context)
        val temp = v.getFloat("temp", Float.NaN).toDouble()
        return JSONObject().apply {
            put("level", v.getString("level", "UNKNOWN"))
            put("reason", v.getString("reason", "Sem avaliação"))
            put("percent", v.getInt("percent", -1))
            put("temperatureC", if (temp.isFinite()) temp else JSONObject.NULL)
            put("thermalStatus", v.getInt("thermal", -1))
            put("eventSequence", v.getLong("seq", 0L))
            put("eventMs", v.getLong("occurredMs", 0L))
            put("autoProtectionEnabled", SettingsStore.thermalProtection(context))
        }
    }

    private fun notifyLocal(context: Context, status: HealthAlertPolicy.Status, percent: Int?, temp: Double?) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Saúde da bateria e temperatura",
            NotificationManager.IMPORTANCE_HIGH))
        val launch = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val details = listOfNotNull(percent?.let { "$it%" }, temp?.let { "%.1f °C".format(it) }).joinToString(" · ")
        val note = Notification.Builder(context, CHANNEL).setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(status.reason).setContentText(details)
            .setContentIntent(launch).setAutoCancel(true).setVisibility(Notification.VISIBILITY_PRIVATE).build()
        nm.notify(290, note)
    }
}
