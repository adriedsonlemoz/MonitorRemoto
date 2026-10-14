package br.dev.monitorremoto.capture

import android.app.Notification
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import java.util.UUID
import java.util.LinkedHashMap

/** Apenas ações de resposta existentes em notificações ativas; nunca abre banco do WhatsApp. */
object NotificationReplyActions {
    private const val EXPIRY_MS = 10 * 60_000L
    private const val CAPACITY = 60
    private data class Entry(
        val notificationKey: String, val app: String, val action: Notification.Action,
        val input: RemoteInput, val createdMs: Long
    )
    private val entries = LinkedHashMap<String, Entry>()

    @Synchronized fun remember(notificationKey: String, app: String, notification: Notification): String? {
        forget(notificationKey)
        val action = notification.actions?.firstOrNull { item ->
            item.actionIntent != null && item.remoteInputs?.any { it.allowFreeFormInput } == true
        } ?: return null
        val input = action.remoteInputs?.firstOrNull { it.allowFreeFormInput } ?: return null
        val token = UUID.randomUUID().toString()
        entries[token] = Entry(notificationKey, app, action, input, System.currentTimeMillis())
        cleanup()
        return token
    }

    @Synchronized fun forget(notificationKey: String) {
        entries.entries.removeAll { it.value.notificationKey == notificationKey }
    }

    @Synchronized fun retainAllowed(packages: Set<String>) {
        entries.entries.removeAll { it.value.app !in packages }
    }

    @Synchronized fun clear() { entries.clear() }

    private fun cleanup() {
        val now = System.currentTimeMillis()
        entries.entries.removeAll { now - it.value.createdMs > EXPIRY_MS }
        while (entries.size > CAPACITY) entries.remove(entries.keys.first())
    }

    /** Executar somente após autenticação, autorização de resposta e validação do corpo. */
    @Synchronized fun reply(context: Context, token: String, text: String, packages: Set<String>): Boolean {
        cleanup()
        val entry = entries.remove(token) ?: return false // Token de uso único, sem replay.
        if (entry.app !in packages || System.currentTimeMillis() - entry.createdMs > EXPIRY_MS) return false
        val inputIntent = Intent()
        val bundle = android.os.Bundle().apply { putCharSequence(entry.input.resultKey, text) }
        RemoteInput.addResultsToIntent(arrayOf(entry.input), inputIntent, bundle)
        return try {
            entry.action.actionIntent.send(context, 0, inputIntent)
            true // Entregue à ação do app, NÃO confirma entrega ao destinatário.
        } catch (_: Exception) { false }
    }
}
