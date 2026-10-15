package br.dev.monitorremoto.ui

import android.app.AlertDialog
import android.graphics.Color
import br.dev.monitorremoto.core.BatteryTemperaturePolicy
import android.content.Intent
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
    val deviceState = root.optJSONObject("deviceState")
    val percentage = if (!battery.has("levelPercent") || battery.isNull("levelPercent")) "—"
        else battery.optInt("levelPercent", -1).takeIf { it in 0..100 }?.let { "$it%" } ?: "—"
    val status = battery.optString("state").takeIf {
        it in setOf("Carregando", "Descarregando", "Cheia", "Não carregando")
    } ?: "Desconhecido"
    val source = battery.optString("powerSource").takeIf { it in setOf("USB", "Tomada", "Sem fio", "Base") }
    val statusLine = if (source == null) status else "$status · $source"
    val chargingIndicator = battery.optBoolean("chargingIndicator", false)
    val temperature = if (battery.isNull("temperatureC")) "—" else
        String.format(Locale.getDefault(), "%.1f °C", battery.optDouble("temperatureC"))
    val networkType = network?.optString("type")?.ifBlank { "Indisponível" } ?: "Indisponível"
    val ssid = network?.optString("wifiSsid")?.takeIf { it.isNotBlank() && it != "null" }
    val signal = network?.optInt("wifiRssiDbm", 0)?.takeIf { it in -110..-10 }?.let { "$it dBm" }
    val wifiNote = when {
        ssid != null -> listOfNotNull(ssid, signal).joinToString(" · ")
        network?.optBoolean("wifiPermission") == false -> "SSID: autorizar no monitorado"
        else -> "SSID indisponível"
    }
    val tempC = if (battery.has("temperatureC") && !battery.isNull("temperatureC")) battery.optDouble("temperatureC") else null
    val tempZone = BatteryTemperaturePolicy.zone(tempC)
    val items = listOf(
        compactMetricCard(if (chargingIndicator) R.drawable.ic_battery_charging else R.drawable.ic_battery, "Bateria",
            if (chargingIndicator) "$percentage ⚡" else percentage, statusLine),
        compactMetricCard(R.drawable.ic_thermometer, "Temperatura", temperature, BatteryTemperaturePolicy.label(tempZone), tempZone),
        compactMetricCard(R.drawable.ic_wifi, "Rede", networkType, wifiNote),
    )
    addMetricGrid(container, items)
    sectionGap(container, 8)
    if (deviceState != null) {
        val screen = deviceState.optString("screenState").takeIf {
            it in setOf("Ligada", "Desligada", "Ambiente")
        } ?: if (deviceState.optBoolean("screenInteractive")) "Interativa" else "Desconhecida"
        val locked = when {
            deviceState.isNull("deviceLocked") -> "Bloqueio desconhecido"
            deviceState.optBoolean("deviceLocked") -> "Bloqueado"
            else -> "Desbloqueado"
        }
        val cameraState = when {
            deviceState.isNull("cameraPossiblyBusy") -> "Câmera: desconhecida"
            deviceState.optBoolean("cameraPossiblyBusy") -> "Câmera: possivelmente em uso"
            else -> "Câmera: disponível"
        }
        val pkg = deviceState.optString("foregroundPackage").takeIf { it.isNotBlank() && it != "null" }
        val appState = when {
            !deviceState.optBoolean("usagePermission") -> "App: acesso ao uso não autorizado"
            pkg != null && screen in setOf("Ligada", "Interativa") -> "App: $pkg"
            else -> "App: não identificado"
        }
        container.addView(label("Tela: $screen · $locked   |   $cameraState", 12f, muted = true))
        container.addView(label(appState, 11.5f, muted = true))
    }
    sectionGap(container, 12)

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
        actionTile(R.drawable.ic_remote_camera, "Câmeras", "Ao vivo · início local", enabled = true) {
            startActivity(Intent(this, RemoteCameraActivity::class.java).apply {
                putExtra(RemoteCameraActivity.EXTRA_HOST, br.dev.monitorremoto.core.SettingsStore.getRemoteHost(this@displayResult))
                putExtra(RemoteCameraActivity.EXTRA_METHOD, remoteConnection.method)
            })
        },
        actionTile(R.drawable.ic_bell, "Tocar som", "Localizar · até 30 s", enabled = true) {
            showRemotePhysicalDialog(sound = true)
        },
        actionTile(R.drawable.ic_flashlight, "Lanterna", "Ligar e desligar", enabled = true) {
            showRemotePhysicalDialog(sound = false)
        },
        actionTile(R.drawable.ic_shield, "Bloquear", "Confirmação + PIN", enabled = true) {
            showRemoteLockDialog()
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

private fun MainActivity.compactMetricCard(icon: Int, title: String, value: String,
    subtitle: String? = null, temperatureZone: BatteryTemperaturePolicy.Zone? = null): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        val themed = when (temperatureZone) {
            BatteryTemperaturePolicy.Zone.COLD -> if (darkTheme()) Color.rgb(35, 61, 78) else Color.rgb(224, 241, 252)
            BatteryTemperaturePolicy.Zone.WARM -> if (darkTheme()) Color.rgb(91, 67, 35) else Color.rgb(255, 240, 214)
            BatteryTemperaturePolicy.Zone.HOT -> if (darkTheme()) Color.rgb(100, 42, 47) else Color.rgb(255, 226, 227)
            BatteryTemperaturePolicy.Zone.NORMAL -> if (darkTheme()) Color.rgb(39, 72, 54) else Color.rgb(231, 246, 235)
            else -> fieldColor
        }
        background = shape(themed, 14)
        setPadding(dp(4), dp(7), dp(4), dp(7))
        minimumHeight = dp(96)
        addView(vectorIcon(icon, 21), LinearLayout.LayoutParams(dp(21), dp(21)))
        addView(label(title, 11.5f, muted = true).apply {
            gravity = Gravity.CENTER
            maxLines = 1
            setPadding(0, dp(4), 0, dp(2))
        })
        addView(label(value, 14f, strong = true).apply {
            gravity = Gravity.CENTER
            maxLines = 2
            setPadding(0, 0, 0, 0)
        })
        if (!subtitle.isNullOrBlank()) addView(label(subtitle, 9.5f, muted = true).apply {
            gravity = Gravity.CENTER
            maxLines = 2
            setPadding(dp(2), dp(3), dp(2), 0)
        })
        contentDescription = listOfNotNull(title, value, subtitle).joinToString(". ")
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
        if (subtitle.isNotBlank()) addView(label(subtitle, 9.5f, muted = true).apply {
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
