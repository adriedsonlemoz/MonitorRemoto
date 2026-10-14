package br.dev.monitorremoto.ui

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import org.json.JSONObject
import java.util.Locale
import br.dev.monitorremoto.R

/** Painel deliberadamente enxuto. Informações técnicas detalhadas ficam em RemoteDetailsActivity. */
internal fun MainActivity.displayResult(root: JSONObject, fromCache: Boolean = false) {
    val container = resultBox ?: return
    container.removeAllViews()
    val battery = root.optJSONObject("battery") ?: return
    val network = root.optJSONObject("network")
    val percentage = if (battery.isNull("levelPercent")) "—" else "${battery.optDouble("levelPercent").toInt()}%"
    val temperature = if (battery.isNull("temperatureC")) "—" else
        String.format(Locale.getDefault(), "%.1f °C", battery.optDouble("temperatureC"))
    val networkType = network?.optString("type")?.ifBlank { "Indisponível" } ?: "Indisponível"
    val items = listOf(
        compactMetricCard(R.drawable.ic_battery, "Bateria", percentage),
        compactMetricCard(R.drawable.ic_thermometer, "Temperatura", temperature),
        compactMetricCard(R.drawable.ic_wifi, "Rede", networkType),
    )
    addMetricGrid(container, items)
    sectionGap(container, 19)

    container.addView(label("Funções remotas", 17f, strong = true).apply { setPadding(0, 0, 0, dp(12)) })
    val eventCount = root.optJSONArray("events")?.length() ?: 0
    val cards = listOf(
        actionTile(R.drawable.ic_folder_download, "Recuperar dados", "Em breve") {
            showFeatureSoon("Recuperar dados", "O resgate de arquivos exigirá autorização das pastas no celular monitorado e transferência criptografada. Ainda não está disponível.")
        },
        actionTile(R.drawable.ic_phone, "Detalhes", "", enabled = true) { showRemoteDetailsDialog(root) },
        actionTile(R.drawable.ic_bell, "Notificações", if (eventCount > 0) "$eventCount evento(s)" else "", enabled = true) {
            showRemoteNotifications(root)
        },
        actionTile(R.drawable.ic_message, "Mensagens", "Em breve") {
            showFeatureSoon("Mensagens", "Não existe leitura direta de mensagens nesta versão.")
        },
        actionTile(R.drawable.ic_monitor, "Ver tela", "Em breve") {
            showFeatureSoon("Ver tela", "A transmissão exigirá permissão de captura concedida no aparelho monitorado.")
        },
        actionTile(R.drawable.ic_pin, "Localização", "Em breve") {
            showFeatureSoon("Localização", "A localização remota dependerá de permissão explícita no aparelho monitorado.")
        },
    )
    addActionGrid(container, cards)
    if (fromCache && remoteConnection.state != "connected") {
        sectionGap(container, 12)
        container.addView(label("Exibindo a última leitura armazenada. Aguarde nova consulta para confirmar os dados.", 11f, muted = true))
    }
}

private fun MainActivity.compactMetricCard(icon: Int, title: String, value: String): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        background = shape(fieldColor, 18)
        setPadding(dp(3), dp(13), dp(3), dp(12))
        minimumHeight = dp(115)
        addView(vectorIcon(icon, 28), LinearLayout.LayoutParams(dp(28), dp(28)))
        addView(label(title, 11.5f, muted = true).apply {
            gravity = Gravity.CENTER
            maxLines = 1
            setPadding(0, dp(10), 0, dp(3))
        })
        addView(label(value, 16f, strong = true).apply {
            gravity = Gravity.CENTER
            maxLines = 2
            setPadding(0, 0, 0, 0)
        })
    }

private fun MainActivity.addMetricGrid(parent: LinearLayout, cards: List<LinearLayout>) {
    val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
    cards.forEachIndexed { index, card ->
        row.addView(card, LinearLayout.LayoutParams(0, -2, 1f).apply {
            if (index < cards.lastIndex) rightMargin = dp(8)
        })
    }
    parent.addView(row)
}

private fun MainActivity.actionTile(icon: Int, title: String, subtitle: String, enabled: Boolean = false, onClick: () -> Unit): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        background = shape(surface, 18, true)
        setPadding(dp(6), dp(14), dp(6), dp(10))
        minimumHeight = dp(105)
        isClickable = true
        isFocusable = true
        contentDescription = "$title${if (!enabled) ", em desenvolvimento" else ""}"
        setOnClickListener { onClick() }
        addView(vectorIcon(icon, 28), LinearLayout.LayoutParams(dp(28), dp(28)))
        addView(label(title, 12f, strong = enabled).apply {
            gravity = Gravity.CENTER
            maxLines = 2
            setPadding(0, dp(10), 0, 0)
        })
        if (subtitle.isNotBlank()) addView(label(subtitle, 10f, muted = true).apply {
            gravity = Gravity.CENTER
            maxLines = 1
            setPadding(0, dp(2), 0, 0)
        })
    }

private fun MainActivity.addActionGrid(parent: LinearLayout, tiles: List<LinearLayout>) {
    tiles.chunked(3).forEachIndexed { index, segment ->
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        segment.forEachIndexed { column, tile ->
            row.addView(tile, LinearLayout.LayoutParams(0, -2, 1f).apply {
                if (column != segment.lastIndex) rightMargin = dp(8)
            })
        }
        parent.addView(row)
        if (index == 0) sectionGap(parent, 9)
    }
}

private fun MainActivity.showFeatureSoon(name: String, description: String) {
    AlertDialog.Builder(this).setTitle(name)
        .setMessage("$description\n\nEste recurso ainda não está implementado.")
        .setPositiveButton("Entendi", null).show()
}

private fun MainActivity.showRemoteNotifications(root: JSONObject) {
    val events = root.optJSONArray("events")
    val (wrapper, layout) = dialogWrapper()
    dialogIntro(layout, "Notificações novas e autorizadas no outro aparelho.")
    if (events == null || events.length() == 0) {
        layout.addView(label("Nenhum evento autorizado registrado.", 13f, muted = true))
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
    AlertDialog.Builder(this).setTitle("Notificações")
        .setView(wrapper).setPositiveButton("Fechar", null).show()
}

private fun MainActivity.showRemoteDetailsDialog(root: JSONObject) {
    val name = currentDeviceTitle?.text?.toString().orEmpty()
    val selectedHost = br.dev.monitorremoto.core.SettingsStore.getRemoteHost(this)
    startActivity(Intent(this, RemoteDetailsActivity::class.java).apply {
        putExtra(RemoteDetailsActivity.EXTRA_SNAPSHOT, root.toString())
        putExtra(RemoteDetailsActivity.EXTRA_DEVICE, name)
        putExtra(RemoteDetailsActivity.EXTRA_HOST, selectedHost)
        putExtra(RemoteDetailsActivity.EXTRA_METHOD, remoteConnection.method)
        putExtra(RemoteDetailsActivity.EXTRA_UPDATED, remoteConnection.lastSuccessMs)
    })
}
