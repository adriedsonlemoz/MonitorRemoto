package br.dev.monitorremoto.core

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SmsManager

/** Resposta SMS solicitada pelo usuário, nunca envio automático ou silencioso de listas. */
object SmsReplySender {
    private var lastAttemptMs = 0L
    @Synchronized fun send(context: Context, destination: String, body: String): String {
        check(SettingsStore.isMonitorEnabled(context) && SettingsStore.allowSmsReplies(context)) { "Respostas SMS não autorizadas no aparelho monitorado." }
        check(context.checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) { "Permissão SEND_SMS ausente." }
        check(SmsInboxReader.mayReply(context, destination)) { "Número não autorizado ou fora das mensagens recebidas." }
        check(body.isNotBlank() && body.length <= 500 && !SensitiveContent.isSensitive("", body)) { "Texto vazio, sensível ou longo demais." }
        val now = System.currentTimeMillis()
        check(now - lastAttemptMs >= 20_000L) { "Aguarde 20 segundos entre respostas SMS." }
        lastAttemptMs = now
        val manager = SmsManager.getDefault()
        val parts = manager.divideMessage(body)
        if (parts.size <= 1) manager.sendTextMessage(destination, null, body, null, null)
        else manager.sendMultipartTextMessage(destination, null, parts, null, null)
        // A chamada só indica aceitação pelo subsistema, NÃO confirma envio/entrega pela operadora.
        return "Solicitação SMS aceita pelo Android. A entrega não foi confirmada."
    }
}
