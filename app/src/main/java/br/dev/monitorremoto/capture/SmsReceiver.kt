package br.dev.monitorremoto.capture

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Telephony
import br.dev.monitorremoto.core.EventJournal
import br.dev.monitorremoto.core.SettingsStore

/** Apenas SMS NOVOS, nunca lê a caixa de entrada nem envia mensagens. */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION ||
            !SettingsStore.isMonitorEnabled(context) || !SettingsStore.isSmsEnabled(context) ||
            context.checkSelfPermission(Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED
        ) return

        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (parts.isEmpty()) return
        val origin = parts.firstOrNull()?.displayOriginatingAddress ?: "Número desconhecido"
        val text = parts.joinToString("") { it.displayMessageBody.orEmpty() }
        EventJournal.add("sms", "SMS", origin, text)
    }
}
