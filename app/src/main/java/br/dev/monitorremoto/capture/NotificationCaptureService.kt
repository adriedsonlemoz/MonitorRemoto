package br.dev.monitorremoto.capture

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import br.dev.monitorremoto.core.EventJournal
import br.dev.monitorremoto.core.SettingsStore
import br.dev.monitorremoto.core.SensitiveContent

class NotificationCaptureService : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null || !SettingsStore.isMonitorEnabled(this)) return
        if (sbn.packageName == packageName) return
        if (sbn.packageName !in SettingsStore.notificationAllowlist(this)) return
        if (SensitiveContent.isBlockedFinancialApp(sbn.packageName, sbn.packageName)) return
        val notification = sbn.notification
        if (notification.visibility == Notification.VISIBILITY_SECRET) return
        if ((notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
        if ((notification.flags and Notification.FLAG_ONGOING_EVENT) != 0) return

        val extras = notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        if (SensitiveContent.isSensitive(title, text)) return
        EventJournal.add("notificacao", sbn.packageName, title, text)
    }
}
