package br.dev.monitorremoto.ui

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.PersistableBundle
import android.content.ClipDescription
import android.text.InputType
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.Toast
import br.dev.monitorremoto.R
import br.dev.monitorremoto.core.MessagePolicy
import br.dev.monitorremoto.core.SettingsStore
import org.json.JSONArray
import org.json.JSONObject

/** Mensagens são consultadas somente sob demanda, sem polling da caixa SMS no painel. */
internal fun MainActivity.loadRemoteMessages() {
    val host = SettingsStore.getRemoteHost(this)
    remoteConnection.loadMessages(host) { result ->
        result.onSuccess { showRemoteMessages(it) }
            .onFailure { showAppNotice("Consulta indisponível", it.message ?: "Falha na comunicação.") }
    }
}

/** Cópia intencional para a área de transferência local; não persistimos no aplicativo. */
internal fun MainActivity.copyRemoteMessage(text: String) {
    val clip = ClipData.newPlainText("Mensagem remota", text)
    if (android.os.Build.VERSION.SDK_INT >= 33) {
        clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
    }
    (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
    Toast.makeText(this, "Mensagem copiada", Toast.LENGTH_SHORT).show()
}

/** SMS, MMS (metadados) e notificações selecionadas, sem FLAG_SECURE nesta tela. */
internal fun MainActivity.showRemoteMessages(root: JSONObject) {
    val (wrapper, layout) = dialogWrapper()
    val state = root.optJSONObject("messagingPermissions")
    val sms = root.optJSONArray("smsInbox")
    val live = root.optJSONArray("smsLive")
    val events = root.optJSONArray("events")
    val subscriptions = root.optJSONArray("smsSubscriptions")
    val smsEnabled = state?.optBoolean("smsRead") == true
    val canSmsReply = state?.optBoolean("smsReply") == true
    val canReplyNotification = state?.optBoolean("notificationReply") == true
    val host = SettingsStore.getRemoteHost(this)
    val lastViewed = lastMessageViewedMs[host] ?: 0L
    var latestSeen = lastViewed

    dialogIntro(layout, "Você pode capturar esta tela e copiar mensagens. Somente dados autorizados pelo monitorado; códigos de autenticação são filtrados.")
    layout.addView(actionButton("Atualizar mensagens") { loadRemoteMessages() })
    sectionGap(layout, 12)
    layout.addView(iconSectionHeader(R.drawable.ic_message, "SMS recebidos e enviados",
        if (smsEnabled) "Até 60 SMS consultados sob demanda" else "Autorize READ_SMS no monitorado"))
    sectionGap(layout, 7)
    if (state?.optBoolean("smsRealtime") == true) {
        layout.addView(label("Recepção SMS ativa no monitorado. Atualize para ver novas mensagens.", 11.5f, muted = true))
        sectionGap(layout, 7)
    }

    val groups = linkedMapOf<String, MutableList<JSONObject>>()
    if (smsEnabled && sms != null) for (i in 0 until sms.length()) {
        val item = sms.optJSONObject(i) ?: continue
        groups.getOrPut(item.optString("address")) { mutableListOf() }.add(item)
    }
    // A notificação broadcast pode chegar antes de o provedor de SMS ser atualizado.
    if (state?.optBoolean("smsRealtime") == true && live != null) for (i in 0 until live.length()) {
        val item = live.optJSONObject(i) ?: continue
        val address = item.optString("address")
        val known = groups[address].orEmpty().any { existing ->
            existing.optString("text") == item.optString("text") &&
                kotlin.math.abs(existing.optLong("whenMs") - item.optLong("whenMs")) < 30_000L
        }
        if (!known) groups.getOrPut(address) { mutableListOf() }.add(item)
    }
    if (groups.isEmpty()) {
        layout.addView(label(if (smsEnabled) "Nenhum SMS elegível encontrado."
            else if (state?.optBoolean("smsRealtime") == true) "Nenhum novo SMS detectado em tempo real."
            else "SMS sem autorização ou permissão do Android.", 12f, muted = true))
    }
    groups.forEach { (address, messages) ->
        val card = panel(12)
        val latest = messages.maxOf { it.optLong("whenMs") }
        latestSeen = maxOf(latestSeen, latest)
        card.addView(label("$address · ${messages.size} SMS", 14f, strong = true))
        if (latest > lastViewed) card.addView(label("● NOVO", 10.5f, muted = true))
        messages.sortedByDescending { it.optLong("whenMs") }.take(5).forEach { item ->
            sectionGap(card, 7)
            val direction = if (item.optString("direction") == "enviado") "Enviado" else "Recebido"
            val content = item.optString("text")
            card.addView(label("$direction · ${formatDate(item.optLong("whenMs"))}", 10.5f, muted = true))
            card.addView(label(content, 12f))
            card.addView(actionButton("Copiar") { copyRemoteMessage(content) })
        }
        if (messages.size > 5) card.addView(label("+ ${messages.size - 5} mensagens anteriores", 10.5f, muted = true))
        if (canSmsReply && MessagePolicy.validReplyAddress(address)) {
            sectionGap(card, 8)
            card.addView(actionButton("Responder SMS") {
                showRemoteMessageEditor("smsReply", address, "SMS para $address", subscriptions)
            })
        }
        layout.addView(card)
        sectionGap(layout, 7)
    }

    val smsStatus = root.optJSONArray("smsStatus")
    if (smsStatus != null && smsStatus.length() > 0) {
        sectionGap(layout, 8)
        layout.addView(iconSectionHeader(R.drawable.ic_activity, "Status de SMS enviados", "Resultados informados pelo Android/operadora"))
        sectionGap(layout, 7)
        for (i in smsStatus.length() - 1 downTo 0) {
            val event = smsStatus.optJSONObject(i) ?: continue
            layout.addView(label("${event.optString("address")} · ${event.optString("state")} · ${formatDate(event.optLong("whenMs"))}", 11.5f))
            sectionGap(layout, 4)
        }
    }
    val mms = root.optJSONArray("mmsMetadata")
    if (mms != null && mms.length() > 0) {
        sectionGap(layout, 12)
        layout.addView(iconSectionHeader(R.drawable.ic_message, "MMS", "Somente assuntos e datas, sem anexos"))
        for (i in 0 until mms.length()) {
            val item = mms.optJSONObject(i) ?: continue
            layout.addView(label("${item.optString("subject")} · ${formatDate(item.optLong("whenMs"))}", 11.5f))
        }
    }
    sectionGap(layout, 14)
    layout.addView(iconSectionHeader(R.drawable.ic_bell, "Notificações de aplicativos",
        if (state?.optBoolean("allNotifications") == true) "Todos os aplicativos elegíveis" else "Aplicativos escolhidos no monitorado"))
    sectionGap(layout, 7)
    val notificationGroups = linkedMapOf<String, MutableList<JSONObject>>()
    if (events != null) for (i in events.length() - 1 downTo 0) {
        val item = events.optJSONObject(i) ?: continue
        if (item.optString("type") != "notificacao") continue
        notificationGroups.getOrPut("${item.optString("app")}|${item.optString("title")}") { mutableListOf() }.add(item)
    }
    if (notificationGroups.isEmpty()) layout.addView(label("Nenhuma nova notificação elegível encontrada.", 12f, muted = true))
    notificationGroups.forEach { (_, items) ->
        val mostRecent = items.first()
        val title = mostRecent.optString("title")
        val pkg = mostRecent.optString("app")
        val latest = items.maxOf { it.optLong("whenMs") }
        latestSeen = maxOf(latestSeen, latest)
        val card = panel(12)
        card.addView(label("$title · ${items.size} notificação(ões)", 13.5f, strong = true))
        card.addView(label(pkg, 11f, muted = true))
        if (latest > lastViewed) card.addView(label("● NOVA", 10.5f, muted = true))
        items.take(5).forEach { item ->
            val message = item.optString("preview")
            sectionGap(card, 6)
            card.addView(label(message, 12f))
            card.addView(label(formatDate(item.optLong("whenMs")), 10.5f, muted = true))
            card.addView(actionButton("Copiar") { copyRemoteMessage(message) })
        }
        val token = items.firstOrNull { it.optString("replyToken").isNotBlank() }?.optString("replyToken").orEmpty()
        if (canReplyNotification && token.isNotBlank()) {
            sectionGap(card, 7)
            card.addView(actionButton("Responder") {
                showRemoteMessageEditor("notificationReply", token, "Responder $title ($pkg)")
            })
        } else {
            card.addView(label("Resposta indisponível nesta notificação.", 10.5f, muted = true))
        }
        layout.addView(card)
        sectionGap(layout, 7)
    }
    lastMessageViewedMs[host] = latestSeen
    val dialog = AlertDialog.Builder(this).setTitle("Mensagens remotas")
        .setView(wrapper).setPositiveButton("Fechar", null).create()
    showManagedDialog(dialog)
}

internal fun MainActivity.showRemoteMessageEditor(operation: String, recipient: String,
    recipientLabel: String, subscriptions: JSONArray? = null) {
    val host = SettingsStore.getRemoteHost(this)
    if (remoteConnection.state != "connected" || remoteConnection.cachedHost != host) {
        showAppNotice("Conexão não confirmada", "Atualize a conexão antes de responder mensagens.")
        return
    }
    val (wrapper, layout) = dialogWrapper()
    dialogIntro(layout, "$recipientLabel\n\nUma solicitação de envio não garante entrega ao destinatário.")
    val options = mutableListOf(-1 to "Chip padrão")
    if (operation == "smsReply" && subscriptions != null) {
        for (i in 0 until subscriptions.length()) {
            val entry = subscriptions.optJSONObject(i) ?: continue
            val id = entry.optInt("id", -1)
            if (id != -1 && options.none { it.first == id }) options.add(id to entry.optString("label"))
        }
    }
    val selector = if (operation == "smsReply" && options.size > 1) Spinner(this).apply {
        adapter = ArrayAdapter(this@showRemoteMessageEditor, android.R.layout.simple_spinner_dropdown_item,
            options.map { it.second })
        layout.addView(label("Chip que enviará o SMS", 11.5f, muted = true))
        layout.addView(this)
        sectionGap(layout, 9)
    } else null
    val input = EditText(this).apply {
        hint = "Digite a resposta"
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        minLines = 3; maxLines = 6
        setTextColor(ink); setHintTextColor(dim)
        background = shape(fieldColor, 12, true)
        setPadding(dp(12), dp(10), dp(12), dp(10))
        filters = arrayOf(android.text.InputFilter.LengthFilter(500))
    }
    layout.addView(input, LinearLayout.LayoutParams(-1, -2))
    val dialog = AlertDialog.Builder(this).setTitle("Responder mensagem")
        .setView(wrapper).setNegativeButton("Cancelar", null).setPositiveButton("Revisar", null).create()
    showManagedDialog(dialog)
    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
        val text = input.text.toString().trim()
        if (text.isBlank()) { input.error = "Escreva uma mensagem."; return@setOnClickListener }
        if (br.dev.monitorremoto.core.SensitiveContent.isSensitive("", text)) {
            input.error = "Conteúdo sensível bloqueado."; return@setOnClickListener
        }
        val selected = options.getOrElse(selector?.selectedItemPosition ?: 0) { options.first() }
        showAppConfirm("Confirmar envio remoto?", "$recipientLabel\n${if (operation == "smsReply") selected.second + "\n" else ""}\n$text\n\nAção executada no aparelho monitorado.",
            R.drawable.ic_message, "Enviar") {
            remoteConnection.sendMessage(host, operation, recipient, text, { result ->
                result.onSuccess { showAppNotice("Solicitação realizada", it) }
                    .onFailure { showAppNotice("Falha ao responder", it.message ?: "Falha na comunicação.") }
            }, selected.first)
        }
    }
}
