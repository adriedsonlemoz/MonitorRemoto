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
import br.dev.monitorremoto.ui.MainActivity
import org.json.JSONObject
import java.security.MessageDigest

/** Deduplicação por host + evento do monitorado. Não armazena credenciais, conteúdo ou snapshots. */
object ControllerHealthAlerts {
    private const val PREF = "monitor_health_received"
    private const val CHANNEL = "monitor_health_remote"
    @Synchronized fun onSnapshot(context: Context, host: String, snapshot: JSONObject) {
        if (!SettingsStore.controllerHealthAlerts(context)) return
        val health = snapshot.optJSONObject("healthAlerts") ?: return
        val seq = health.optLong("eventSequence", 0L)
        val at = health.optLong("eventMs", 0L)
        val state = health.optString("level")
        if (seq <= 0 || at <= 0L || at > System.currentTimeMillis() + 60_000L ||
            state !in setOf("CRITICAL", "WARNING", "NORMAL")) return
        // Não notificar estado antigo demais (ex.: telefone ficou semanas desconectado).
        if (System.currentTimeMillis() - at > 24L * 60 * 60_000L) return
        val id = MessageDigest.getInstance("SHA-256").digest(host.toByteArray(Charsets.UTF_8))
            .take(10).joinToString("") { "%02x".format(it.toInt() and 255) }
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val lastSeq = prefs.getLong(id, 0L)
        val lastAt = prefs.getLong("event_at_$id", 0L)
        // Novo pareamento no mesmo IP pode reiniciar a sequência; data distingue eventos.
        if (at <= lastAt && seq <= lastSeq) return
        // Primeiro evento visto também é marcado; evitar repetir alertas após troca de Activity.
        prefs.edit().putLong(id, seq).putLong("event_at_$id", at).apply()
        if (state == "NORMAL") return
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val mgr = context.getSystemService(NotificationManager::class.java) ?: return
        mgr.createNotificationChannel(NotificationChannel(CHANNEL, "Alertas de aparelhos remotos", NotificationManager.IMPORTANCE_HIGH))
        val name = RecentDevices.list(context).firstOrNull { it.host == host }?.name ?: "Aparelho remoto"
        val title = if (state == "CRITICAL") "Atenção: $name" else "Aviso: $name"
        val reason = health.optString("reason", "Condição de bateria/temperatura")
        val ageMinutes = ((System.currentTimeMillis() - at) / 60_000L).coerceAtLeast(0)
        val detail = if (ageMinutes > 5) "$reason (registro há $ageMinutes min)" else reason
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        mgr.notify(291 + (id.hashCode() and 127), Notification.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_alert).setContentTitle(title)
            .setContentText(detail.take(120)).setContentIntent(open).setAutoCancel(true)
            .setVisibility(Notification.VISIBILITY_PRIVATE).build())
    }
}
