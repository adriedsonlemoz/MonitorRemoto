package br.dev.monitorremoto.capture

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Telephony
import br.dev.monitorremoto.core.SettingsStore
import br.dev.monitorremoto.core.SmsLiveJournal

/** Não salva SMS nem transmite nada em background. Somente buffers locais enquanto ativo. */
class SmsReceivedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION ||
            !SettingsStore.isMonitorEnabled(context) || !SettingsStore.shareSmsInbox(context) ||
            !SettingsStore.receiveSmsEvents(context) ||
            context.checkSelfPermission(Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (parts.isEmpty()) return
        // Combina as partes recebidas no mesmo broadcast, sem armazená-las no disco.
        val address = parts.first().displayOriginatingAddress.orEmpty()
        val body = parts.joinToString("") { it.displayMessageBody.orEmpty() }
        val whenMs = parts.first().timestampMillis.takeIf { it > 0L } ?: System.currentTimeMillis()
        SmsLiveJournal.add(address, body, whenMs)
    }
}
