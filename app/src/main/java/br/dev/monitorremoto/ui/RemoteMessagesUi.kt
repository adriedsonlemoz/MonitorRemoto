package br.dev.monitorremoto.ui

import android.app.AlertDialog
import android.text.InputType
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import br.dev.monitorremoto.R
import br.dev.monitorremoto.core.SettingsStore
import org.json.JSONObject

/** Consulta somente quando Mensagens é solicitada, evitando leitura SMS a cada polling. */
internal fun MainActivity.loadRemoteMessages() {
    val host = SettingsStore.getRemoteHost(this)
    showAppNotice("Consultando mensagens", "Buscando somente os dados autorizados no aparelho monitorado.")
    remoteConnection.loadMessages(host) { result ->
        result.onSuccess { showRemoteMessages(it) }
            .onFailure { showAppNotice("Consulta indisponível", it.message ?: "Falha na comunicação.") }
    }
}

/** Caixa remota somente com snapshot fresco. Respostas exigem confirmação explícita. */
internal fun MainActivity.showRemoteMessages(root: JSONObject) {
    val (wrapper, layout) = dialogWrapper()
    val status = root.optJSONObject("messagingPermissions")
    val sms = root.optJSONArray("smsInbox")
    val events = root.optJSONArray("events")
    val smsEnabled = status?.optBoolean("smsRead") == true
    val canSmsReply = status?.optBoolean("smsReply") == true
    val canReplyNotifications = status?.optBoolean("notificationReply") == true
    dialogIntro(layout, "Somente dados presentes na última consulta autenticada. Mensagens não são armazenadas no controlador.")
    layout.addView(iconSectionHeader(R.drawable.ic_message, "SMS recebidos",
        if (smsEnabled) "Últimos recebidos, com filtros de segurança" else "Habilite READ_SMS nas configurações do monitorado"))
    sectionGap(layout, 8)
    val host = SettingsStore.getRemoteHost(this)
    val lastViewed = lastMessageViewedMs[host] ?: 0L
    var latestSeenMs = lastViewed
    val smsGroups = linkedMapOf<String, MutableList<JSONObject>>()
    if (smsEnabled && sms != null) for (index in 0 until sms.length()) {
        val smsItem = sms.optJSONObject(index) ?: continue
        smsGroups.getOrPut(smsItem.optString("address")) { mutableListOf() }.add(smsItem)
    }
    if (!smsEnabled || smsGroups.isEmpty()) {
        layout.addView(label(if (smsEnabled) "Nenhum SMS elegível disponível." else "Leitura de SMS não autorizada ou não disponível.", 12f, muted = true))
    } else smsGroups.forEach { (address, messages) ->
        val card = panel(12)
        val latest = messages.maxOf { it.optLong("whenMs") }
        latestSeenMs = maxOf(latestSeenMs, latest)
        card.addView(label("$address · ${messages.size} SMS", 14f, strong = true))
        if (latest > lastViewed) card.addView(label("● NOVO", 10f, muted = true))
        messages.take(3).forEach { smsItem ->
            card.addView(label(smsItem.optString("text"), 12f))
            card.addView(label(formatDate(smsItem.optLong("whenMs")), 11f, muted = true))
        }
        if (messages.size > 3) card.addView(label("+ ${messages.size - 3} mensagem(ns) anterior(es)", 11f, muted = true))
        if (canSmsReply && br.dev.monitorremoto.core.MessagePolicy.validReplyAddress(address)) {
            card.addView(actionButton("Responder SMS") {
                showRemoteMessageEditor("smsReply", address, "SMS para $address")
            })
        }
        layout.addView(card); sectionGap(layout, 7)
    }
    sectionGap(layout, 12)
    layout.addView(iconSectionHeader(R.drawable.ic_bell, "Mensagens em notificações",
        if (canReplyNotifications) "Resposta quando houver ação válida" else "Respostas desativadas no monitorado"))
    sectionGap(layout, 8)
    val notificationGroups = linkedMapOf<String, MutableList<JSONObject>>()
    if (events != null) for (index in events.length() - 1 downTo 0) {
        val event = events.optJSONObject(index) ?: continue
        if (event.optString("type") != "notificacao") continue
        val groupId = "${event.optString("app")}|${event.optString("title")}" // app + conversa
        notificationGroups.getOrPut(groupId) { mutableListOf() }.add(event)
    }
    if (notificationGroups.isEmpty()) layout.addView(label("Nenhuma nova notificação autorizada capturada.", 12f, muted = true))
    notificationGroups.forEach { (_, items) ->
        val recent = items.first()
        val latest = items.maxOf { it.optLong("whenMs") }
        latestSeenMs = maxOf(latestSeenMs, latest)
        val title = recent.optString("title")
        val pkg = recent.optString("app")
        val card = panel(12)
        card.addView(label("$title · ${items.size} notificação(ões)", 14f, strong = true))
        card.addView(label(pkg, 11f, muted = true))
        if (latest > lastViewed) card.addView(label("● NOVA", 10f, muted = true))
        items.take(3).forEach { item ->
            card.addView(label(item.optString("preview"), 12f))
            card.addView(label(formatDate(item.optLong("whenMs")), 11f, muted = true))
        }
        if (items.size > 3) card.addView(label("+ ${items.size - 3} notificação(ões) anterior(es)", 11f, muted = true))
        // Um token ativo só existe para a notificação permitida. Pode expirar entre tela e envio.
        val token = items.firstOrNull { it.optString("replyToken").isNotBlank() }?.optString("replyToken").orEmpty()
        if (canReplyNotifications && token.isNotBlank()) {
            card.addView(actionButton("Responder mensagem") {
                showRemoteMessageEditor("notificationReply", token, "Responder $title ($pkg)")
            })
        }
        layout.addView(card); sectionGap(layout, 7)
    }
    lastMessageViewedMs[host] = latestSeenMs
    val dialog = AlertDialog.Builder(this).setTitle("Mensagens remotas")
        .setView(wrapper).setPositiveButton("Fechar", null).create()
    showManagedDialog(dialog)
    dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
}

private fun MainActivity.showRemoteMessageEditor(operation: String, recipient: String, recipientLabel: String) {
    val host = SettingsStore.getRemoteHost(this)
    if (remoteConnection.state != "connected" || remoteConnection.cachedHost != host) {
        showAppNotice("Conexão não confirmada", "Atualize a conexão antes de responder mensagens.")
        return
    }
    val (wrapper, layout) = dialogWrapper()
    dialogIntro(layout, "$recipientLabel\n\nA confirmação de envio não garante que a mensagem chegou ao destinatário.")
    val input = EditText(this).apply {
        hint = "Digite a mensagem"
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        minLines = 3; maxLines = 6
        setTextColor(ink)
        setHintTextColor(dim)
        background = shape(fieldColor, 12, true)
        setPadding(dp(12), dp(10), dp(12), dp(10))
        filters = arrayOf(android.text.InputFilter.LengthFilter(500))
    }
    layout.addView(input, LinearLayout.LayoutParams(-1, -2))
    val dialog = AlertDialog.Builder(this).setTitle("Escrever resposta")
        .setView(wrapper).setNegativeButton("Cancelar", null)
        .setPositiveButton("Revisar", null).create()
    showManagedDialog(dialog)
    dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
        val text = input.text.toString().trim()
        if (text.isBlank()) { input.error = "Escreva uma mensagem."; return@setOnClickListener }
        if (br.dev.monitorremoto.core.SensitiveContent.isSensitive("", text)) {
            input.error = "Mensagem contém texto sensível bloqueado."; return@setOnClickListener
        }
        showAppConfirm("Confirmar envio remoto?", "$recipientLabel\n\n$text\n\nEsta ação será executada no aparelho monitorado.",
            R.drawable.ic_message, "Enviar") {
            remoteConnection.sendMessage(host, operation, recipient, text) { result ->
                result.onSuccess { showAppNotice("Solicitação realizada", it) }
                    .onFailure { showAppNotice("Não foi possível responder", it.message ?: "Falha na comunicação.") }
            }
        }
        activeUiDialog?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}
