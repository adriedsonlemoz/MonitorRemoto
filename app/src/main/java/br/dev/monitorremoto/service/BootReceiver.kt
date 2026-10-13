package br.dev.monitorremoto.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import br.dev.monitorremoto.core.SettingsStore

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!SettingsStore.autoStartEnabled(context) || !SettingsStore.isMonitorEnabled(context)) return
        try {
            MonitorService.start(context)
        } catch (_: Exception) {
            // Restrições de fabricante/SO: iniciar manualmente se necessário.
        }
    }
}
