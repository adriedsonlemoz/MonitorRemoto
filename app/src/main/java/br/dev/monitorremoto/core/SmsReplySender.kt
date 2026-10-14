package br.dev.monitorremoto.core

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import br.dev.monitorremoto.capture.SmsStatusReceiver
import java.util.concurrent.atomic.AtomicInteger

/** Resposta autorizada, com callbacks locais de envio e entrega, nunca em lote. */
object SmsReplySender {
    private var lastAttemptMs = 0L
    private val requestIds = AtomicInteger(1000)

    @Synchronized fun send(context: Context, destination: String, body: String, subscriptionId: Int = -1): String {
        check(SettingsStore.isMonitorEnabled(context) && SettingsStore.allowSmsReplies(context)) {
            "Respostas SMS não autorizadas no aparelho monitorado."
        }
        check(context.checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
            "Permissão SEND_SMS ausente."
        }
        check(SmsInboxReader.mayReply(context, destination)) { "Número não autorizado ou fora das mensagens recebidas." }
        check(body.isNotBlank() && body.length <= 500 && !SensitiveContent.isSensitive("", body)) {
            "Texto vazio, sensível ou longo demais."
        }
        val now = System.currentTimeMillis()
        check(now - lastAttemptMs >= 20_000L) { "Aguarde 20 segundos entre respostas SMS." }
        val manager = SmsSubscriptions.sender(context, subscriptionId)
        val parts = manager.divideMessage(body)
        val id = SmsDeliveryJournal.begin(destination, parts.size)
        fun callback(stage: String, part: Int): PendingIntent {
            val intent = Intent(context, SmsStatusReceiver::class.java).apply {
                action = "br.dev.monitorremoto.SMS_${stage.uppercase()}"
                putExtra("id", id)
                putExtra("stage", stage)
                putExtra("part", part)
            }
            return PendingIntent.getBroadcast(context, requestIds.incrementAndGet(), intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT)
        }
        // Conta limite mesmo que o subsistema lance exceção durante o envio.
        lastAttemptMs = now
        try {
            if (parts.size == 1) manager.sendTextMessage(destination, null, body,
                callback("sent", 0), callback("delivered", 0))
            else manager.sendMultipartTextMessage(destination, null, parts,
                ArrayList(parts.indices.map { callback("sent", it) }),
                ArrayList(parts.indices.map { callback("delivered", it) }))
        } catch (error: Exception) {
            SmsDeliveryJournal.failed(id)
            throw error
        }
        return "Solicitação SMS enviada ao Android. Consulte o status para verificar envio/entrega."
    }
}
