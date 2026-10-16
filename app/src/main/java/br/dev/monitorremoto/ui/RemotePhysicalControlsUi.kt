package br.dev.monitorremoto.ui

import android.app.AlertDialog
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import br.dev.monitorremoto.R
import br.dev.monitorremoto.core.SettingsStore
import org.json.JSONObject

/** Comandos exclusivos da conexão atual. Estado de lanterna vem de TorchCallback no monitorado. */
internal fun MainActivity.showRemotePhysicalDialog(sound: Boolean) {
    val host = SettingsStore.getRemoteHost(this)
    val (wrapper, content) = dialogWrapper()
    content.addView(iconSectionHeader(if (sound) R.drawable.ic_bell else R.drawable.ic_flashlight,
        if (sound) "Tocar som remoto" else "Lanterna remota",
        "${currentDeviceTitle?.text?.toString().orEmpty().ifBlank { "Aparelho conectado" }}"))
    sectionGap(content, 8)
    content.addView(label(if (sound)
        "Alerta temporário. Respeita o volume e o Não Perturbe do monitorado."
        else "Flash traseiro, quando o Android permitir. Estado confirmado pela câmera.", 12f, muted = true))
    sectionGap(content, 8)
    val statusView = label("Consultando estado real…", 13f, strong = true)
    val messageView = label("", 12f, muted = true)
    content.addView(panel(11).apply {
        background = shape(fieldColor, 14)
        addView(statusView)
        addView(messageView)
    })
    sectionGap(content, 10)

    var duration = 10
    val durationButtons = mutableListOf<Button>()
    if (sound) {
        content.addView(label("Duração do alerta", 12f, muted = true))
        val durations = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf(5, 10, 20, 30).forEachIndexed { index, seconds ->
            val button = actionButton("${seconds}s", seconds == duration) {
                duration = seconds
                durationButtons.forEachIndexed { i, item ->
                    val selected = i == index
                    item.background = shape(if (selected) highlight else surface, 12, !selected)
                    item.setTextColor(if (selected) highlightText else ink)
                }
            }
            durationButtons.add(button)
            durations.addView(button, LinearLayout.LayoutParams(0, dp(43), 1f).apply {
                if (index < 3) rightMargin = dp(5)
            })
        }
        content.addView(durations)
        sectionGap(content, 12)
    }
    val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    val turnOn = actionButton(if (sound) "Iniciar som" else "Ligar flash", emphasis = true) { }
    val turnOff = actionButton(if (sound) "Interromper" else "Desligar") { }
    controls.addView(turnOn, LinearLayout.LayoutParams(0, dp(48), 1f).apply { rightMargin = dp(8) })
    controls.addView(turnOff, LinearLayout.LayoutParams(0, dp(48), 1f))
    content.addView(controls)
    sectionGap(content, 8)
    val refresh = actionButton("Atualizar estado") { }
    content.addView(refresh)
    val dialog = themedPopup(this).setView(wrapper).setNegativeButton("Fechar", null).create()
    var busy = false
    var authorized = false
    fun setEnabled() {
        turnOn.isEnabled = !busy && authorized
        turnOff.isEnabled = !busy && (authorized || sound)
        refresh.isEnabled = !busy
        durationButtons.forEach { it.isEnabled = !busy && authorized }
        for (button in listOf(turnOn, turnOff, refresh)) button.alpha = if (button.isEnabled) 1f else 0.5f
    }
    fun readStatus(state: JSONObject) {
        authorized = state.optBoolean(if (sound) "soundAllowed" else "torchAllowed", false)
        val line = if (sound) {
            if (state.optBoolean("soundActive", false))
                "Som solicitado · ${state.optInt("soundRemainingSeconds")}s restantes"
            else "Alerta inativo"
        } else when (state.optString("torchState", "desconhecida")) {
            "ligada" -> "Lanterna ligada (confirmada)"
            "desligada" -> "Lanterna desligada (confirmada)"
            "indisponível" -> "Flash indisponível ou ocupado"
            else -> "Estado da lanterna ainda não confirmado"
        }
        statusView.text = line
        messageView.text = if (authorized) "Autorizado no monitorado" else
            "Ative a permissão em Configurações › Som e lanterna no monitorado."
    }
    fun send(action: String, seconds: Int = 0) {
        if (busy || !dialog.isShowing) return
        busy = true
        setEnabled()
        remoteConnection.deviceControl(host, action, seconds) { result ->
            if (!dialog.isShowing) return@deviceControl
            busy = false
            result.fold(onSuccess = { reply ->
                reply.optJSONObject("status")?.let(::readStatus)
                val msg = reply.optString("message")
                if (!reply.optBoolean("ok", false)) {
                    messageView.text = msg.ifBlank { "Comando recusado pelo monitorado." }
                } else if (action != "status") {
                    messageView.text = msg
                    // A troca da lanterna é assíncrona; consultar o callback após breve intervalo.
                    if (!sound && (action == "torchOn" || action == "torchOff")) {
                        handler.postDelayed({ if (dialog.isShowing && !busy) send("status") }, 800L)
                    }
                }
            }, onFailure = {
                statusView.text = "Sem confirmação do monitorado"
                messageView.text = it.message ?: "Falha de comunicação"
            })
            setEnabled()
        }
    }
    turnOn.setOnClickListener { send(if (sound) "soundStart" else "torchOn", if (sound) duration else 0) }
    turnOff.setOnClickListener { send(if (sound) "soundStop" else "torchOff") }
    refresh.setOnClickListener { send("status") }
    setEnabled()
    showManagedDialog(dialog)
    send("status")
}
