package br.dev.monitorremoto.ui

import android.app.AlertDialog
import android.graphics.Color
import br.dev.monitorremoto.core.BatteryTemperaturePolicy
import br.dev.monitorremoto.core.RemoteOperationHistory
import br.dev.monitorremoto.core.RemoteOperationHistoryPolicy
import android.content.Intent
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import org.json.JSONObject
import java.util.Locale
import br.dev.monitorremoto.R
import br.dev.monitorremoto.core.SettingsStore

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
            if (chargingIndicator) "$percentage ⚡" else percentage, statusLine, onClick = {
                showRemoteThermalDialog(battery, fromCache)
            }),
        compactMetricCard(R.drawable.ic_thermometer, "Temperatura", temperature, BatteryTemperaturePolicy.label(tempZone), tempZone, onClick = {
            showRemoteThermalDialog(battery, fromCache)
        }),
        compactMetricCard(R.drawable.ic_wifi, "Rede", networkType, wifiNote, onClick = {
            showAppNotice("Rede do monitorado", if (ssid == null && network?.optBoolean("wifiPermission") == false)
                "No celular monitorado, abra Monitor Remoto → Configurações → Diagnóstico → Nome e sinal do Wi-Fi. Autorize Localização precisa e ative a localização do Android. Não é possível liberar essa permissão remotamente."
                else "Tipo: $networkType\n${ssid ?: "SSID indisponível"}\n${signal ?: "RSSI indisponível"}")
        }),
    )
    // Resultado de polling autenticado não significa canal persistente.
    val liveReading = !fromCache && remoteConnection.state == "connected"
    container.addView(dialogCardRow(
        if (liveReading) "✓ Resposta autenticada" else "◷ Dados guardados no controlador",
        if (liveReading) "Resposta ${formatDate(remoteConnection.lastSuccessMs)} · ${remoteConnection.lastRoundTripMs?.let { "${it} ms" } ?: "tempo indisponível"}"
        else "Esta leitura não confirma conexão atual. Última resposta: ${if (remoteConnection.lastSuccessMs > 0L) formatDate(remoteConnection.lastSuccessMs) else "não registrada"}",
        "ⓘ") {
        showAppNotice("Estado da conexão", "O Monitor Remoto consulta cada dado mediante requisição autenticada; não mantém um canal conectado permanentemente.\n" +
            "Estado: ${if (liveReading) "última consulta respondida" else "leitura armazenada"}\n" +
            "Método: ${remoteConnection.method}\n" +
            "Duração da requisição: ${remoteConnection.lastRoundTripMs?.let { "${it} ms" } ?: "não medida"}\n" +
            "Última resposta: ${if (remoteConnection.lastSuccessMs > 0L) formatDate(remoteConnection.lastSuccessMs) else "não disponível"}")
    })
    sectionGap(container, 9)
    addMetricGrid(container, items)
    sectionGap(container, 8)
    if (deviceState != null) {
        val screen = deviceState.optString("screenState").takeIf { it in setOf("Ligada", "Desligada", "Ambiente") } ?: "Desconhecida"
        val locked = when { deviceState.isNull("deviceLocked") -> "? Bloqueio"; deviceState.optBoolean("deviceLocked") -> "Bloqueado"; else -> "Desbloqueado" }
        val camera = when { deviceState.isNull("cameraPossiblyBusy") -> "Câmera ?"; deviceState.optBoolean("cameraPossiblyBusy") -> "Em uso?"; else -> "Disponível" }
        val stateRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val statuses = listOf(
            Triple(R.drawable.ic_monitor, "Tela $screen", screen == "Desligada"),
            Triple(R.drawable.ic_shield, locked, locked == "Bloqueado"),
            Triple(R.drawable.ic_remote_camera, camera, false)
        )
        statuses.forEachIndexed { i, status ->
            val (icon, caption, accent) = status
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                background = shape(fieldColor, 11)
                setPadding(dp(4), dp(4), dp(4), dp(4))
                addView(vectorIcon(icon, 16, if (accent) highlight else ink))
                addView(label(caption, 9.5f, muted = true).apply { maxLines = 1; setPadding(dp(3), 0, 0, 0) })
                contentDescription = caption
                isClickable = true; isFocusable = true
                setOnClickListener {
                    showAppNotice("Diagnóstico remoto", "Tela: $screen\n$locked\nCâmera: $camera. Ocupação não comprova gravação do Minha Câmera.")
                }
            }
            stateRow.addView(cell, LinearLayout.LayoutParams(0, -2, 1f).apply { if (i < 2) rightMargin = dp(5) })
        }
        container.addView(stateRow)
        sectionGap(container, 8)
        val current = deviceState.optString("foregroundPackage").takeIf { it.isNotBlank() && it != "null" }
        val last = deviceState.optString("lastForegroundPackage").takeIf { it.isNotBlank() && it != "null" }
        val lastAt = if (!deviceState.isNull("lastForegroundAt")) deviceState.optLong("lastForegroundAt") else 0L
        val permitted = deviceState.optBoolean("usagePermission")
        val appCaption = when {
            !permitted -> "Acesso ao uso pendente no monitorado"
            current != null -> "Em primeiro plano: $current"
            last != null -> "Último visto: $last" + if (lastAt > 0L) " · ${formatDate(lastAt)}" else ""
            else -> "Aplicativo não identificado"
        }
        container.addView(dialogCardRow("Aplicativo observado", appCaption, "›") {
            showAppNotice("Aplicativo observado", if (!permitted)
                "No aparelho monitorado: Monitor Remoto → Configurações → Diagnóstico → Aplicativo visível → Acesso ao uso. Não é possível conceder essa permissão pelo controlador."
                else "${current?.let { "Agora: $it" } ?: "Nenhum aplicativo atual identificado"}\n${last?.let { "Último visto: $it" } ?: "Sem histórico observado"}\nIsso não comprova que o Minha Câmera esteja gravando.")
        })
    }
    root.optJSONObject("healthAlerts")?.let { health ->
        val level = health.optString("level")
        if (level == "WARNING" || level == "CRITICAL") {
            sectionGap(container, 7)
            container.addView(dialogCardRow("Alerta de saúde", health.optString("reason", "Condição desconhecida"), "!", accent = true) {
                showAppNotice("Saúde do monitorado", "${health.optString("reason")}\nNotificações remotas: Configurações → Alertas e proteção. Interrupção automática do Minha Câmera não está disponível.")
            })
        }
    }
    sectionGap(container, 12)

    container.addView(label("Funções remotas", 17f, strong = true).apply { setPadding(0, 0, 0, dp(5)) })
    container.addView(label("Toque para abrir · 12 funções · 3 colunas", 10.5f, muted = true).apply {
        setPadding(0, 0, 0, dp(8))
    })
    val eventCount = root.optJSONArray("events")?.let { events ->
        (0 until events.length()).count { events.optJSONObject(it)?.optString("type") == "notificacao" }
    } ?: 0
    val cards = listOf(
        actionTile(R.drawable.ic_folder_download, "Arquivos", "Downloads fixos", enabled = true) {
            startActivity(Intent(this, RemoteFilesActivity::class.java).apply {
                putExtra(RemoteFilesActivity.EXTRA_HOST, br.dev.monitorremoto.core.SettingsStore.getRemoteHost(this@displayResult))
                putExtra(RemoteFilesActivity.EXTRA_METHOD, remoteConnection.method)
            })
        },
        actionTile(R.drawable.ic_bell, "Alertas", if (eventCount > 0) "$eventCount evento(s)" else "", enabled = true) {
            startActivity(Intent(this, RemoteCatalogActivity::class.java).apply {
                putExtra(RemoteCatalogActivity.EXTRA_HOST, br.dev.monitorremoto.core.SettingsStore.getRemoteHost(this@displayResult))
                putExtra(RemoteCatalogActivity.EXTRA_METHOD, remoteConnection.method)
                putExtra(RemoteCatalogActivity.EXTRA_TYPE, "notifications")
            })
        },
        actionTile(R.drawable.ic_phone, "Apps", "Instalados · autorizados", enabled = true) {
            startActivity(Intent(this, RemoteCatalogActivity::class.java).apply {
                putExtra(RemoteCatalogActivity.EXTRA_HOST, br.dev.monitorremoto.core.SettingsStore.getRemoteHost(this@displayResult))
                putExtra(RemoteCatalogActivity.EXTRA_METHOD, remoteConnection.method)
                putExtra(RemoteCatalogActivity.EXTRA_TYPE, "apps")
            })
        },
        actionTile(R.drawable.ic_message, "Mensagens", "SMS e respostas", enabled = true) {
            openRemoteMessagesScreen()
        },
        actionTile(R.drawable.ic_monitor, "Tela", "Ao vivo · autorizado", enabled = true) {
            startActivity(Intent(this, RemoteScreenActivity::class.java).apply {
                putExtra(RemoteScreenActivity.EXTRA_HOST, br.dev.monitorremoto.core.SettingsStore.getRemoteHost(this@displayResult))
                putExtra(RemoteScreenActivity.EXTRA_METHOD, remoteConnection.method)
            })
        },
        actionTile(R.drawable.ic_remote_camera, "Foto", "JPEG único · sem vídeo", enabled = true) {
            startActivity(Intent(this, RemotePhotoActivity::class.java).apply {
                putExtra(RemotePhotoActivity.EXTRA_HOST, br.dev.monitorremoto.core.SettingsStore.getRemoteHost(this@displayResult))
                putExtra(RemotePhotoActivity.EXTRA_METHOD, remoteConnection.method)
            })
        },
        actionTile(R.drawable.ic_remote_camera, "Câmeras", "Ao vivo · início local", enabled = true) {
            startActivity(Intent(this, RemoteCameraActivity::class.java).apply {
                putExtra(RemoteCameraActivity.EXTRA_HOST, br.dev.monitorremoto.core.SettingsStore.getRemoteHost(this@displayResult))
                putExtra(RemoteCameraActivity.EXTRA_METHOD, remoteConnection.method)
            })
        },
        actionTile(R.drawable.ic_bell, "Som", "Localizar · até 30 s", enabled = true) {
            showRemotePhysicalDialog(sound = true)
        },
        actionTile(R.drawable.ic_flashlight, "Lanterna", "Ligar e desligar", enabled = true) {
            showRemotePhysicalDialog(sound = false)
        },
        actionTile(R.drawable.ic_shield, "Bloquear", "Confirmação + PIN", enabled = true) {
            showRemoteLockDialog()
        },
        actionTile(R.drawable.ic_settings, "Energia", "Bloqueio e manutenção", enabled = true) {
            showRemoteMaintenance()
        },
        actionTile(R.drawable.ic_pin, "Localização", "Consulta autorizada") {
            startActivity(Intent(this, RemoteLocationActivity::class.java)
                .putExtra(RemoteLocationActivity.EXTRA_HOST, br.dev.monitorremoto.core.SettingsStore.getRemoteHost(this))
                .putExtra(RemoteLocationActivity.EXTRA_METHOD, "Chave salva"))
        },
    )
    addActionGrid(container, cards)
    val selectedHost = SettingsStore.getRemoteHost(this)
    val recent = RemoteOperationHistory.read(this, selectedHost).firstOrNull()
    if (recent != null) {
        sectionGap(container, 11)
        container.addView(dialogCardRow("Atividade recente de arquivos",
            "${RemoteOperationHistoryPolicy.label(recent.operation)} · ${recent.outcome} · ${recent.done}/${recent.total} item(ns)", "›") {
            startActivity(Intent(this, RemoteFilesActivity::class.java).apply {
                putExtra(RemoteFilesActivity.EXTRA_HOST, selectedHost)
                putExtra(RemoteFilesActivity.EXTRA_METHOD, remoteConnection.method)
            })
        })
    }
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
    layout.addView(iconSectionHeader(R.drawable.ic_settings, "Energia e Manutenção", "Ações autorizadas no aparelho remoto"))
    sectionGap(layout, 14)
    layout.addView(panel(14).apply {
        background = shape(fieldColor, 17)
        addView(label(name, 16f, strong = true))
        addView(label(selectedHost, 12f, muted = true))
        addView(infoChip("Requer ADB conectado"))
    })
    sectionGap(layout, 12)
    layout.addView(label("Controles disponíveis", 12f, muted = true))
    layout.addView(dialogCardRow(
        title = "Bloquear tela",
        subtitle = "Requer administrador e autorização no monitorado",
        trailing = "›"
    ) {
        activeUiDialog?.dismiss()
        showRemoteLockDialog()
    })
    sectionGap(layout, 8)
    layout.addView(dialogCardRow(
        title = "Acender tela",
        subtitle = "Permissão local obrigatória; não desbloqueia",
        trailing = "›"
    ) {
        activeUiDialog?.dismiss()
        showAppNotice("Acender a tela", "A função está disponível em Ver tela, mediante autorização prévia no celular monitorado. O Monitor Remoto não remove PIN nem desbloqueia o Android.")
    })
    sectionGap(layout, 8)
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
        addView(label("Nenhum comando é enviado ao abrir este menu. O desligamento completo não é oferecido por segurança. A confirmação de reinicialização é feita no ADB.", 12f, muted = true))
    })
    showManagedDialog(themedPopup(this)
        .setView(wrapper)
        .setNegativeButton("Cancelar", null))
}

private fun MainActivity.compactMetricCard(icon: Int, title: String, value: String,
    subtitle: String? = null, temperatureZone: BatteryTemperaturePolicy.Zone? = null,
    onClick: (() -> Unit)? = null): LinearLayout =
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
        if (!subtitle.isNullOrBlank()) addView(label(subtitle, 10.5f, muted = true).apply {
            gravity = Gravity.CENTER
            maxLines = 2
            setPadding(dp(2), dp(3), dp(2), 0)
        })
        contentDescription = listOfNotNull(title, value, subtitle).joinToString(". ")
        if (onClick != null) {
            isClickable = true; isFocusable = true
            setOnClickListener { onClick() }
        }
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

/** Grade compacta de três colunas em retrato, sem esconder ações por falta de largura. */
private fun MainActivity.actionTile(icon: Int, title: String, subtitle: String, enabled: Boolean = false, onClick: () -> Unit): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        background = shape(surface, 16, true)
        setPadding(dp(3), dp(10), dp(3), dp(9))
        minimumHeight = dp(86)
        isClickable = true
        isFocusable = true
        contentDescription = listOf(title, subtitle).filter { it.isNotBlank() }.joinToString(". ")
        setOnClickListener { onClick() }
        addView(vectorIcon(icon, 23, if (enabled) ink else dim),
            LinearLayout.LayoutParams(dp(23), dp(23)).apply { gravity = Gravity.CENTER_HORIZONTAL })
        addView(label(title, 11.5f, strong = enabled).apply {
            gravity = Gravity.CENTER
            maxLines = 2
            setPadding(dp(1), dp(7), dp(1), 0)
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        // Subtítulos permanecem no contentDescription para TalkBack; não ocupam linhas.
    }

private fun MainActivity.addActionGrid(parent: LinearLayout, tiles: List<LinearLayout>) {
    tiles.chunked(3).forEachIndexed { rowIndex, segment ->
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        segment.forEachIndexed { column, tile ->
            row.addView(tile, LinearLayout.LayoutParams(0, dp(88), 1f).apply {
                if (column < 2) rightMargin = dp(7)
            })
        }
        repeat(3 - segment.size) { row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f)) }
        parent.addView(row)
        if (rowIndex < (tiles.size + 2) / 3 - 1) sectionGap(parent, 7)
    }
}

private fun MainActivity.showFeatureSoon(name: String, description: String) {
    showAppNotice(name, "$description\n\nEste recurso ainda não está implementado.", R.drawable.ic_info)
}

internal fun MainActivity.showRemoteDetailsDialog(root: JSONObject) {
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
