package br.dev.monitorremoto.adb

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Histórico leve de aparelhos ADB já conectados com sucesso.
 * Não prova identidade do telefone; serve apenas para reconexão conveniente.
 */
data class AdbKnownDevice(
    val name: String,
    val host: String,
    val port: Int,
    val lastConnectedAt: Long,
    val androidVersion: String = "",
    val model: String = ""
)

object AdbKnownDevices {
    private const val PREFS = "adb_known_devices"
    private const val KEY = "entries"
    private const val LIMIT = 5

    fun load(context: Context): List<AdbKnownDevice> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null).orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val name = item.optString("name").trim()
                    val host = item.optString("host").trim()
                    val port = item.optInt("port", -1)
                    if (name.isBlank() || host.isBlank() || port !in 1..65535) continue
                    add(
                        AdbKnownDevice(
                            name = name,
                            host = host,
                            port = port,
                            lastConnectedAt = item.optLong("lastConnectedAt", 0L),
                            androidVersion = item.optString("androidVersion").trim(),
                            model = item.optString("model").trim()
                        )
                    )
                }
            }.sortedByDescending { it.lastConnectedAt }
        }.getOrDefault(emptyList())
    }

    fun remember(context: Context, device: AdbKnownDevice) {
        val cleaned = device.copy(
            name = device.name.trim().ifBlank { "Aparelho em ${device.host}" },
            host = device.host.trim(),
            androidVersion = device.androidVersion.trim(),
            model = device.model.trim()
        )
        val current = load(context).toMutableList()
        // Dois telefones podem ter o mesmo modelo. Nunca mesclar apenas por nome/modelo.
        val match = current.indexOfFirst { it.host == cleaned.host && it.port == cleaned.port }
        if (match >= 0) {
            current[match] = cleaned
        } else {
            current.add(0, cleaned)
        }
        persist(context, current.sortedByDescending { it.lastConnectedAt }.take(LIMIT))
    }

    fun forget(context: Context, device: AdbKnownDevice) {
        val remaining = load(context).filterNot {
            it.name.equals(device.name, ignoreCase = true) && it.host == device.host && it.port == device.port
        }
        persist(context, remaining)
    }

    private fun persist(context: Context, devices: List<AdbKnownDevice>) {
        val array = JSONArray()
        devices.forEach { device ->
            array.put(
                JSONObject().apply {
                    put("name", device.name)
                    put("host", device.host)
                    put("port", device.port)
                    put("lastConnectedAt", device.lastConnectedAt)
                    put("androidVersion", device.androidVersion)
                    put("model", device.model)
                }
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, array.toString()).apply()
    }
}
