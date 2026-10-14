package br.dev.monitorremoto.ui

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.PersistableBundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import br.dev.monitorremoto.core.SettingsStore
import org.json.JSONObject
import java.util.Locale

/** Alpha.22: módulo de interface extraído de MainActivity. Sem estado de conexão próprio. */

internal fun MainActivity.displayResult(root: JSONObject, fromCache: Boolean = false) {
    val panel = resultBox ?: return
    panel.removeAllViews()
    val battery = root.optJSONObject("battery") ?: return
    val events = root.optJSONArray("events")
    val host = SettingsStore.getRemoteHost(this)
    val level = if (battery.isNull("levelPercent")) "—" else "${battery.optDouble("levelPercent").toInt()}%"
    val charging = when {
        battery.optBoolean("full") -> "Cheia"
        battery.optBoolean("charging") -> "Carregando"
        else -> "Descarregando"
    }
    fun current(name: String): String = if (battery.isNull(name)) "—" else
        String.format(Locale.getDefault(), "%.0f mA", battery.optDouble(name))
    val temp = if (battery.isNull("temperatureC")) "—" else String.format(Locale.getDefault(), "%.1f °C", battery.optDouble("temperatureC"))
    val voltage = if (battery.isNull("voltageMv")) "—" else "${battery.optInt("voltageMv")} mV"
    val eventCount = events?.length() ?: 0
    val metricCards = listOf(
        compactMetricCard("🔋", "Bateria", level, charging, accent = true),
        compactMetricCard("🌡", "Temperatura", temp, "Térmica"),
        compactMetricCard("⚡", "Corrente", current("currentMa"), "Agora"),
        compactMetricCard("∿", "Média", current("averageMa"), "Consumo"),
        compactMetricCard("🔌", "Tensão", voltage, "Bateria"),
        compactMetricCard("🔔", "Eventos", eventCount.toString(), if (eventCount == 1) "1 evento" else "$eventCount eventos")
    )
    addMetricGrid(panel, metricCards)
    sectionGap(panel, 10)

    val remoteNetwork = root.optJSONObject("network")?.optString("type").orEmpty()
    val quickStates = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    quickStates.addView(infoChip("🔋 $charging").apply {
        maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
    }, LinearLayout.LayoutParams(0, -2, 1f))
    if (remoteNetwork.isNotBlank()) {
        quickStates.addView(View(this), LinearLayout.LayoutParams(dp(8), 1))
        quickStates.addView(infoChip("📶 Rede: $remoteNetwork").apply {
            maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
        }, LinearLayout.LayoutParams(0, -2, 1f))
    }
    panel.addView(quickStates)
    sectionGap(panel, 10)

    panel.addView(label("Funções remotas", 15f, strong = true).apply { setPadding(0, 0, 0, dp(2)) })
    panel.addView(label("Toque para abrir dados atuais ou funções futuras.", 11f, muted = true).apply { setPadding(0, 0, 0, dp(4)) })
    val tiles = listOf(
        actionTile("🔔", "Notificações", if (eventCount == 0) "Sem alertas" else "$eventCount capturadas", emphasis = true) { showRemoteNotifications(root) },
        actionTile("📋", "Copiar", "Resumo atual") { copyRemoteSnapshot(root) },
        actionTile("ℹ", "Detalhes", "Bateria e rede") { showRemoteDetailsDialog(root) },
        actionTile("💬", "Mensagens", "Em breve") { showFeatureSoon("Mensagens", "A visualização direta de mensagens ainda depende de um fluxo separado e de permissões específicas.") },
        actionTile("🖥", "Ver tela", "Em breve") { showFeatureSoon("Ver tela", "A visualização da tela remota exigirá autorização explícita no aparelho monitorado.") },
        actionTile("📍", "Localização", "Em breve") { showFeatureSoon("Localização", "A localização remota será exibida quando houver permissão e coleta dedicada desse dado.") }
    )
    addActionGrid(panel, tiles)

    if (fromCache && connectionState != "connected") {
        sectionGap(panel, 8)
        panel.addView(label("Leitura anterior: aguarde confirmação de uma nova consulta antes de considerar estes dados atuais.", 11.5f, muted = true))
    }

}

private fun MainActivity.compactMetricCard(icon: String, title: String, value: String, subtitle: String = "", accent: Boolean = false): LinearLayout =
    panel(8).apply {
        background = shape(if (accent) highlight else fieldColor, 15, !accent)
        val caption = LinearLayout(this@compactMetricCard).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        caption.addView(label(icon, 13f).apply { setPadding(0, 0, dp(4), 0) })
        caption.addView(label(title, 10f, muted = !accent).apply {
            setTextColor(if (accent) highlightText else dim)
            maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(0, 0, 0, 0)
        })
        addView(caption)
        addView(label(value, 16f, strong = true).apply {
            setTextColor(if (accent) highlightText else ink)
            setPadding(0, dp(4), 0, 0)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
    }

private fun MainActivity.addMetricGrid(parent: LinearLayout, cards: List<LinearLayout>) {
    val rows = cards.chunked(3)
    rows.forEachIndexed { rowIndex, rowCards ->
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        rowCards.forEachIndexed { index, card ->
            row.addView(card, LinearLayout.LayoutParams(0, -2, 1f).apply {
                if (index < rowCards.lastIndex) rightMargin = dp(8)
            })
        }
        while (row.childCount < 3) {
            row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f).apply {
                if (row.childCount < 3) rightMargin = dp(8)
            })
        }
        parent.addView(row)
        if (rowIndex < rows.lastIndex) sectionGap(parent, 8)
    }
}

private fun MainActivity.actionTile(icon: String, title: String, subtitle: String, emphasis: Boolean = false, onClick: () -> Unit): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        background = shape(if (emphasis) highlight else fieldColor, 16, !emphasis)
        setPadding(dp(7), dp(13), dp(7), dp(13))
        minimumHeight = dp(98)
        isClickable = true
        isFocusable = true
        contentDescription = "$title: $subtitle"
        setOnClickListener { onClick() }
        addView(label(icon, 22f).apply {
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(4))
        })
        addView(label(title, 12f, strong = true).apply {
            gravity = Gravity.CENTER
            setTextColor(if (emphasis) highlightText else ink)
            setPadding(0, 0, 0, 0)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        addView(label(subtitle, 10f, muted = !emphasis).apply {
            gravity = Gravity.CENTER
            setTextColor(if (emphasis) Color.argb(216, 255, 255, 255) else dim)
            setPadding(0, dp(3), 0, 0)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
    }

private fun MainActivity.addActionGrid(parent: LinearLayout, tiles: List<LinearLayout>) {
    val rows = tiles.chunked(3)
    rows.forEachIndexed { rowIndex, rowTiles ->
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        rowTiles.forEachIndexed { index, tile ->
            row.addView(tile, LinearLayout.LayoutParams(0, -2, 1f).apply {
                if (index < rowTiles.lastIndex) rightMargin = dp(8)
            })
        }
        while (row.childCount < 3) {
            row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f).apply {
                if (row.childCount < 3) rightMargin = dp(8)
            })
        }
        parent.addView(row)
        if (rowIndex < rows.lastIndex) sectionGap(parent, 8)
    }
}

private fun MainActivity.showFeatureSoon(name: String, description: String) {
    AlertDialog.Builder(this)
        .setTitle(name)
        .setMessage("$description\n\nEsse atalho já está previsto no painel, mas ainda será implementado em uma versão futura.")
        .setPositiveButton("OK", null)
        .show()
}

private fun MainActivity.showRemoteNotifications(root: JSONObject) {
    val events = root.optJSONArray("events")
    val (wrapper, layout) = dialogWrapper()
    dialogIntro(layout, "Eventos autorizados recebidos do aparelho remoto.")
    if (events == null || events.length() == 0) {
        layout.addView(label("Nenhuma notificação autorizada capturada.", 12.5f, muted = true))
    } else {
        for (index in events.length() - 1 downTo 0) {
            val event = events.optJSONObject(index) ?: continue
            val item = panel(12)
            item.addView(label("${event.optString("type").uppercase(Locale.getDefault())} · ${event.optString("app")}", 11f, muted = true))
            item.addView(label(event.optString("title"), 14f, strong = true))
            if (event.optString("preview").isNotBlank()) item.addView(label(event.optString("preview"), 12f))
            item.addView(label(formatDate(event.optLong("whenMs")), 11f, muted = true))
            layout.addView(item)
            sectionGap(layout, 7)
        }
    }
    AlertDialog.Builder(this)
        .setTitle("Notificações")
        .setView(wrapper)
        .setPositiveButton("Fechar", null)
        .show()
}

private fun MainActivity.copyRemoteSnapshot(root: JSONObject) {
    val battery = root.optJSONObject("battery") ?: return
    val host = SettingsStore.getRemoteHost(this)
    val summary = buildString {
        append(currentDeviceTitle?.text ?: "Monitor Remoto")
        append("\nIP: ").append(host)
        append("\nAcesso: ").append(connectionKind(host))
        append("\nRede remota: ").append(root.optJSONObject("network")?.optString("type") ?: "Não informada")
        append("\nMétodo: ").append(selectedConnectionMethod)
        append("\nAtualizado: ").append(if (lastSuccessfulMs > 0L) formatDate(lastSuccessfulMs) else "agora")
        append("\n\n").append(batterySummary(battery))
    }
    val clip = ClipData.newPlainText("Monitor Remoto: resumo do aparelho", summary)
    if (Build.VERSION.SDK_INT >= 33) {
        clip.description.extras = PersistableBundle().apply {
            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
        }
    }
    (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
    message("Resumo atual copiado para a área de transferência.")
}

private fun MainActivity.showRemoteDetailsDialog(root: JSONObject) {
    val battery = root.optJSONObject("battery") ?: return
    val host = SettingsStore.getRemoteHost(this)
    val (wrapper, layout) = dialogWrapper()
    dialogIntro(layout, "Resumo técnico do aparelho remoto.")
    val card = panel(14)
    card.addView(dialogMetricRow("Aparelho", currentDeviceTitle?.text?.toString() ?: host))
    card.addView(dialogMetricRow("Acesso", connectionKind(host)))
    card.addView(dialogMetricRow("Rede do celular", root.optJSONObject("network")?.optString("type") ?: "Não informada"))
    card.addView(dialogMetricRow("IP", host.ifBlank { "—" }))
    card.addView(dialogMetricRow("Método", selectedConnectionMethod))
    card.addView(dialogMetricRow("Atualização", if (lastSuccessfulMs > 0L) formatDate(lastSuccessfulMs) else "Sem leitura recente"))
    card.addView(dialogMetricRow("Bateria", if (battery.isNull("levelPercent")) "—" else "${battery.optDouble("levelPercent").toInt()}%"))
    card.addView(dialogMetricRow("Estado", if (battery.optBoolean("charging")) "Carregando" else "Descarregando"))
    card.addView(dialogMetricRow("Temperatura", if (battery.isNull("temperatureC")) "—" else String.format(Locale.getDefault(), "%.1f °C", battery.optDouble("temperatureC"))))
    card.addView(dialogMetricRow("Tensão", if (battery.isNull("voltageMv")) "—" else "${battery.optInt("voltageMv")} mV"))
    card.addView(dialogMetricRow("Corrente atual", if (battery.isNull("currentMa")) "—" else String.format(Locale.getDefault(), "%.0f mA", battery.optDouble("currentMa"))))
    card.addView(dialogMetricRow("Corrente média", if (battery.isNull("averageMa")) "—" else String.format(Locale.getDefault(), "%.0f mA", battery.optDouble("averageMa"))))
    card.addView(dialogMetricRow("Eventos", "${root.optJSONArray("events")?.length() ?: 0}"))
    layout.addView(card)
    layout.addView(label("Correntes são leituras fornecidas pelo Android e podem variar conforme o aparelho.", 11f, muted = true))
    AlertDialog.Builder(this)
        .setTitle("Detalhes do aparelho")
        .setView(wrapper)
        .setPositiveButton("Fechar", null)
        .show()
}

private fun MainActivity.batterySummary(battery: JSONObject): String {
    fun number(name: String, suffix: String, digits: Int = 1): String = if (battery.isNull(name)) "indisponível" else
        String.format(Locale.getDefault(), "% .${digits}f", battery.optDouble(name)).trim() + suffix
    val charge = if (battery.optBoolean("charging")) "carregando" else "descarregando"
    return "Bateria: ${number("levelPercent", "%", 0)} ($charge)\n" +
        "Temperatura: ${number("temperatureC", " °C")}\n" +
        "Tensão: ${if (battery.isNull("voltageMv")) "indisponível" else "${battery.optInt("voltageMv")} mV"}\n" +
        "Corrente instantânea: ${number("currentMa", " mA", 0)}\n" +
        "Corrente média: ${number("averageMa", " mA", 0)}"
}

