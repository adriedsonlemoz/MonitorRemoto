package br.dev.monitorremoto.ui

import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import br.dev.monitorremoto.R
import br.dev.monitorremoto.adb.AdbClient
import br.dev.monitorremoto.adb.AdbKnownDevices
import br.dev.monitorremoto.adb.AdbPolicy
import br.dev.monitorremoto.adb.ThermalAdbParser
import br.dev.monitorremoto.core.RecentDevices
import br.dev.monitorremoto.core.SettingsStore
import br.dev.monitorremoto.transport.RemoteClient
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

/** Diálogo central: usa leituras oficiais e acrescenta zonas ADB se já houve pareamento TLS. */
internal fun MainActivity.showRemoteThermalDialog(batteryFromPanel: JSONObject, fromCache: Boolean) {
    val host = SettingsStore.getRemoteHost(this)
    val key = RecentDevices.key(this, host)
    val (scroll, layout) = dialogWrapper()
    layout.background = shape(surface, 22, true)
    layout.setPadding(dp(18), dp(18), dp(18), dp(15))
    layout.addView(iconSectionHeader(R.drawable.ic_thermometer, "Temperaturas do aparelho",
        "Leituras fornecidas pelo Android e ADB, quando disponível"))
    sectionGap(layout, 6)
    val state = label("Consultando sensores…", 12.5f, muted = true)
    layout.addView(state)
    sectionGap(layout, 8)
    val nativeList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    val adbList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    layout.addView(nativeList)
    sectionGap(layout, 12)
    layout.addView(iconSectionHeader(R.drawable.ic_info, "Sensores via ADB",
        "Somente em aparelho já pareado por Depuração sem fios"))
    sectionGap(layout, 4)
    val adbStatus = label("Verificando ADB conhecido…", 12f, muted = true)
    layout.addView(adbStatus)
    layout.addView(adbList)
    sectionGap(layout, 12)
    layout.addView(label("As temperaturas da CPU/GPU podem não ser expostas. Estado térmico não é temperatura em °C. Nenhum valor é estimado.",
        11.5f, muted = true))
    sectionGap(layout, 9)
    lateinit var dialog: android.app.AlertDialog
    var sequence = 0
    fun renderNative(data: JSONObject, source: String) {
        nativeList.removeAllViews()
        val battery = data.optJSONObject("battery") ?: batteryFromPanel
        val level = if (!battery.isNull("levelPercent")) "${battery.optInt("levelPercent")}%" else "indisponível"
        nativeList.addView(dialogCardRow("Bateria", "Carga: $level · ${battery.optString("state", "Estado indisponível")}", ""))
        val readings = data.optJSONArray("readings")
        if (readings == null || readings.length() == 0) {
            val value = battery.optDouble("temperatureC", Double.NaN)
            if (value.isFinite() && value in -40.0..120.0) {
                addThermalReading(nativeList, "Bateria · BatteryManager", value, "API Android")
            } else nativeList.addView(label("Nenhuma temperatura pública disponível.", 12.5f, muted = true))
        } else for (i in 0 until readings.length()) {
            val item = readings.optJSONObject(i) ?: continue
            val c = item.optDouble("celsius", Double.NaN)
            if (c.isFinite() && c in -40.0..180.0)
                addThermalReading(nativeList, item.optString("name", "Sensor"), c,
                    item.optString("source", "Android"))
        }
        val status = data.optInt("thermalStatus", -1)
        nativeList.addView(dialogCardRow("Estado térmico do sistema",
            thermalStatusLabel(status), ""))
        val headroom = data.optDouble("headroom", Double.NaN)
        nativeList.addView(dialogCardRow("Margem térmica (não é °C)",
            if (headroom.isFinite() && headroom >= 0) String.format(Locale.getDefault(), "%.2f", headroom)
            else "Não informada pelo Android", ""))
        val timestamp = data.optLong("sampledAt", 0L)
        val whenRead = if (timestamp > 0) DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date(timestamp)) else "não informado"
        state.text = "$source · $whenRead"
    }
    val fallback = JSONObject().put("battery", batteryFromPanel).put("thermalStatus", JSONObject.NULL)
    renderNative(fallback, if (fromCache) "Leitura guardada" else "Última leitura recebida")
    fun refresh() {
        val token = ++sequence
        state.text = "Atualizando dados do monitorado…"
        adbList.removeAllViews()
        val known = AdbKnownDevices.load(this).filter { it.host == host }
            .maxByOrNull { it.lastConnectedAt }
        adbStatus.text = when {
            known == null -> "ADB não pareado para este mesmo IP. Leitura avançada indisponível."
            else -> "Conectando ao ADB já pareado…"
        }
        if (key == null || host.isBlank()) {
            state.text = "Conecte um aparelho autorizado para obter novas leituras."
            return
        }
        val context = applicationContext
        val method = remoteConnection.method
        thread(name = "MonitorRemoto-thermal-rpc") {
            val result = runCatching { RemoteClient.thermalDiagnostics(host, key, context, method) }
            handler.post {
                if (!dialog.isShowing || token != sequence || SettingsStore.getRemoteHost(this) != host) return@post
                result.onSuccess { answer ->
                    val diagnostics = answer.optJSONObject("diagnostics")
                    if (diagnostics != null) renderNative(diagnostics, "Leitura remota autenticada")
                    else state.text = "O aparelho não retornou sensores."
                }.onFailure { state.text = "Falha na consulta: ${it.message?.take(110) ?: "sem resposta"}" }
            }
        }
        if (known != null) thread(name = "MonitorRemoto-thermal-adb") {
            val result = runCatching {
                val (ip, port) = AdbPolicy.endpoint(known.host, known.port.toString())
                check(ip == host) { "O ADB salvo pertence a outro aparelho." }
                val client = AdbClient(context)
                try {
                    client.connect(ip, port)
                    ThermalAdbParser.parse(client.readThermalService())
                } finally { client.close() }
            }
            handler.post {
                if (!dialog.isShowing || token != sequence || SettingsStore.getRemoteHost(this) != host) return@post
                result.onSuccess { readings ->
                    adbList.removeAllViews()
                    adbStatus.text = if (readings.isEmpty())
                        "ADB conectado, mas o fabricante não publicou zonas térmicas." else
                        "${readings.size} zona(s) térmica(s) informadas pelo ADB"
                    readings.forEach { reading ->
                        addThermalReading(adbList, thermalDisplayName(reading), reading.celsius,
                            "ADB · ${reading.name}")
                    }
                }.onFailure {
                    adbStatus.text = "ADB indisponível agora. Ative a Depuração sem fios e conecte em ADB."
                }
            }
        }
    }
    val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
    controls.addView(actionButton("Atualizar", emphasis = true) { refresh() },
        LinearLayout.LayoutParams(0, dp(44), 1f).apply { rightMargin = dp(8) })
    controls.addView(actionButton("Fechar") { dialog.dismiss() },
        LinearLayout.LayoutParams(0, dp(44), 1f))
    layout.addView(controls)
    dialog = themedPopup(this).setView(scroll).create()
    showManagedDialog(dialog)
    dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
    dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.93f).toInt(),
        (resources.displayMetrics.heightPixels * 0.85f).toInt())
    dialog.window?.setGravity(Gravity.CENTER)
    refresh()
}

private fun MainActivity.addThermalReading(parent: LinearLayout, name: String, value: Double, origin: String) {
    val entry = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(11), dp(9), dp(11), dp(9))
        background = shape(fieldColor, 12, true)
    }
    val names = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    names.addView(label(name.take(76), 13f, strong = true))
    names.addView(label(origin.take(92), 10.5f, muted = true))
    entry.addView(names, LinearLayout.LayoutParams(0, -2, 1f))
    entry.addView(label(String.format(Locale.getDefault(), "%.1f °C", value), 16f, strong = true))
    parent.addView(entry, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })
}

private fun thermalStatusLabel(code: Int): String = when (code) {
    0 -> "Normal · sem limitação reportada"
    1 -> "Leve"; 2 -> "Moderado"; 3 -> "Severo"; 4 -> "Crítico"
    5 -> "Emergência"; 6 -> "Desligamento iminente"
    else -> "Não informado pelo Android"
}

private fun thermalDisplayName(reading: ThermalAdbParser.Reading): String = when (reading.type) {
    "cpu" -> "CPU · ${reading.name}"
    "gpu" -> "GPU · ${reading.name}"
    "battery" -> "Bateria · ${reading.name}"
    "skin" -> "Superfície · ${reading.name}"
    "usb" -> "USB · ${reading.name}"
    "modem" -> "Modem · ${reading.name}"
    "power" -> "Amplificador · ${reading.name}"
    "npu" -> "NPU · ${reading.name}"
    else -> "Sensor · ${reading.name}"
}
