package br.dev.monitorremoto.capture

import android.app.Notification
import android.content.ComponentName
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import br.dev.monitorremoto.core.EventJournal
import br.dev.monitorremoto.core.SettingsStore
import br.dev.monitorremoto.core.SensitiveContent

class NotificationCaptureService : NotificationListenerService() {
    companion object {
        @Volatile private var listener: NotificationCaptureService? = null
        /** Chamar ao ativar monitoramento: listener pode estar conectado desde antes da autorização local. */
        fun recoverActiveAfterLocalStart() { listener?.recoverActive() }
    }
    private val observed = LinkedHashMap<String, String>()
    override fun onListenerConnected() {
        super.onListenerConnected()
        listener = this
        recoverActive()
    }
    private fun recoverActive() {
        // Só lista notificações ainda visíveis. A API não permite recuperar as já descartadas.
        val current = runCatching { activeNotifications?.sortedByDescending { it.postTime }?.take(30).orEmpty() }
            .getOrDefault(emptyList())
        NotificationCaptureStatus.connected(current.size)
        if (SettingsStore.isMonitorEnabled(this)) current.asReversed().forEach { onNotificationPosted(it) }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn?.key?.let {
            observed.remove(it)
            NotificationReplyActions.forget(it)
        }
    }

    override fun onListenerDisconnected() {
        if (listener === this) listener = null
        observed.clear()
        NotificationCaptureStatus.disconnected()
        NotificationReplyActions.clear()
        if (Build.VERSION.SDK_INT >= 24 && SettingsStore.isMonitorEnabled(this)) {
            runCatching { NotificationListenerService.requestRebind(ComponentName(this, NotificationCaptureService::class.java)) }
        }
    }
    override fun onDestroy() {
        if (listener === this) listener = null
        observed.clear()
        NotificationCaptureStatus.disconnected()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null || !SettingsStore.isMonitorEnabled(this)) return
        NotificationReplyActions.forget(sbn.key)
        if (sbn.packageName == packageName) return
        if (!SettingsStore.allNotifications(this) &&
            sbn.packageName !in SettingsStore.notificationAllowlist(this)) return
        if (SensitiveContent.isBlockedFinancialApp(sbn.packageName, sbn.packageName)) return
        val notification = sbn.notification
        if (notification.visibility == Notification.VISIBILITY_SECRET) return
        if ((notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
        if ((notification.flags and Notification.FLAG_ONGOING_EVENT) != 0) return

        val extras = notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.toString()
            ?: extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.joinToString(" · ")
            ?: ""
        if (SensitiveContent.isSensitive(title, text)) return
        // Evita duplicar avisos inalterados se o sistema religar o listener.
        val fingerprint = "${sbn.postTime}:$title:$text"
        if (observed[sbn.key] == fingerprint) return
        observed[sbn.key] = fingerprint
        while (observed.size > 120) observed.remove(observed.keys.first())
        val replyToken = if (SettingsStore.allowNotificationReplies(this)) {
            NotificationReplyActions.remember(sbn.key, sbn.packageName, notification)
        } else null
        EventJournal.add("notificacao", sbn.packageName, title, text, replyToken)
        NotificationCaptureStatus.recorded()
    }
}
