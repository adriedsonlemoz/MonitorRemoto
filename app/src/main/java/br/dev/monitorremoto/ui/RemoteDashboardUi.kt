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
    val eventCount = root.optJSONArray("events")?.let { events ->
        (0 until events.length()).count { events.optJSONObject(it)?.optString("type") == "notificacao" }
    } ?: 0
    val cards = listOf(
        actionTile(R.drawable.ic_folder_download, "Recuperar dados", "Pasta autorizada", enabled = true) {
            startActivity(Intent(this, RemoteFilesActivity::class.java).apply {
                putExtra(RemoteFilesActivity.EXTRA_HOST, br.dev.monitorremoto.core.SettingsStore.getRemoteHost(this@displayResult))
                putExtra(RemoteFilesActivity.EXTRA_METHOD, remoteConnection.method)
            })
        },
        actionTile(R.drawable.ic_phone, "Detalhes", "", enabled = true) { showRemoteDetailsDialog(root) },
        actionTile(R.drawable.ic_bell, "Notificações", if (eventCount > 0) "$eventCount evento(s)" else "", enabled = true) {
            startActivity(Intent(this, RemoteCatalogActivity::class.java).apply {
                putExtra(RemoteCatalogActivity.EXTRA_HOST, br.dev.monitorremoto.core.SettingsStore.getRemoteHost(this@displayResult))
                putExtra(RemoteCatalogActivity.EXTRA_METHOD, remoteConnection.method)
                putExtra(RemoteCatalogActivity.EXTRA_TYPE, "notifications")
            })
        },
        actionTile(R.drawable.ic_phone, "Aplicativos", "Instalados · autorizados", enabled = true) {
            startActivity(Intent(this, RemoteCatalogActivity::class.java).apply {
                putExtra(RemoteCatalogActivity.EXTRA_HOST, br.dev.monitorremoto.core.SettingsStore.getRemoteHost(this@displayResult))
                putExtra(RemoteCatalogActivity.EXTRA_METHOD, remoteConnection.method)
                putExtra(RemoteCatalogActivity.EXTRA_TYPE, "apps")
            })
        },
        actionTile(R.drawable.ic_message, "Mensagens", "SMS e respostas", enabled = true) {
            openRemoteMessagesScreen()
        },
        actionTile(R.drawable.ic_monitor, "Ver tela", "Ao vivo · autorizado", enabled = true) {
            startActivity(Intent(this, RemoteScreenActivity::class.java).apply {
                putExtra(RemoteScreenActivity.EXTRA_HOST, br.dev.monitorremoto.core.SettingsStore.getRemoteHost(this@displayResult))
                putExtra(RemoteScreenActivity.EXTRA_METHOD, remoteConnection.method)
            })
        },
        actionTile(R.drawable.ic_settings, "Manutenção", "Reiniciar via ADB", enabled = true) {
            showRemoteMaintenance()
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

/** A conexão normal AES-GCM não confere privilégio de reinicialização. */
private fun MainActivity.showRemoteMaintenance() {
    val selectedHost = br.dev.monitorremoto.core.SettingsStore.getRemoteHost(this)
    val name = currentDeviceTitle?.text?.toString().orEmpty().ifBlank { "Aparelho remoto" }
    val (wrapper, layout) = dialogWrapper()
    layout.addView(iconSectionHeader(R.drawable.ic_settings, "Manutenção", "Ações autorizadas no aparelho remoto"))
    sectionGap(layout, 14)
    layout.addView(panel(14).apply {
        background = shape(fieldColor, 17)
        addView(label(name, 16f, strong = true))
        addView(label(selectedHost, 12f, muted = true))
        addView(infoChip("Requer ADB conectado"))
    })
    sectionGap(layout, 12)
    layout.addView(label("Disponível", 12f, muted = true))
    layout.addView(dialogCardRow(
        title = "Reiniciar normalmente",
        subtitle = "Reinicia o Android, sem desligar",
        trailing = "›",
        accent = true
    ) {
        activeUiDialog?.dismiss()
        startActivity(Intent(this, AdbActivity::class.java).apply {
            putExtra(AdbActivity.EXTRA_MAINTENANCE_HOST, selectedHost)
        })
    })
    sectionGap(layout, 12)
    layout.addView(panel(14).apply {
        background = shape(fieldColor, 16)
        addView(iconSectionHeader(R.drawable.ic_info, "Antes de reiniciar"))
        addView(label("Se o celular não voltar a iniciar, ele poderá ficar inacessível sem o botão Power. A Depuração sem fios pode não voltar sozinha.", 12.5f, muted = true))
        addView(label("Nenhum comando é enviado ao abrir este menu. A confirmação final é feita no ADB.", 12f, muted = true))
    })
    showManagedDialog(AlertDialog.Builder(this)
        .setView(wrapper)
        .setNegativeButton("Cancelar", null))
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
    showAppNotice(name, "$description\n\nEste recurso ainda não está implementado.", R.drawable.ic_info)
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
