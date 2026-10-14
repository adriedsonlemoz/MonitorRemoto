package br.dev.monitorremoto.capture

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import br.dev.monitorremoto.core.SmsDeliveryJournal

/** Callbacks explícitos de resultado do SmsManager; receptor não exportado. */
class SmsStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val id = intent?.getStringExtra("id") ?: return
        val stage = intent.getStringExtra("stage") ?: return
        if (stage != "sent" && stage != "delivered") return
        SmsDeliveryJournal.update(id, stage, intent.getIntExtra("part", -1), resultCode)
    }
}
