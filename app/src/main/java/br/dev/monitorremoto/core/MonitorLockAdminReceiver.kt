package br.dev.monitorremoto.core

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

/** Identificador estável do administrador de dispositivo. Só a política force-lock é solicitada. */
class MonitorLockAdminReceiver : DeviceAdminReceiver() {
    override fun onDisabled(context: Context, intent: Intent) {
        SettingsStore.setAllowRemoteLock(context, false)
    }
}
