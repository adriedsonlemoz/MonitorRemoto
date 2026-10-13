package br.dev.monitorremoto.core

import android.content.Context
import br.dev.monitorremoto.security.SecretVault
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** Metadados locais e não sensíveis; cada chave fica criptografada no SecretVault/Keystore. */
object RecentDevices {
    private const val PREFS = "monitor_remoto_recents"
    private const val RECORDS = "recent_devices"
    private const val MIGRATION = "legacy_migrated"
    const val LIMIT = 5

    data class Device(val host: String, val name: String, val lastSuccessMs: Long,
        val customName: Boolean = false)

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun normalize(host: String) = host.trim().lowercase(Locale.ROOT)
    private fun validHost(host: String) = host.isNotBlank() && host.length <= 253 &&
        host.none { it == '/' || it == ':' || it.isWhitespace() }

    fun list(ctx: Context): List<Device> = try {
        val array = JSONArray(prefs(ctx).getString(RECORDS, "[]"))
        (0 until array.length()).mapNotNull { i ->
            val item = array.optJSONObject(i) ?: return@mapNotNull null
            val host = normalize(item.optString("host"))
            if (!validHost(host)) null else Device(
                host, item.optString("name").take(80).ifBlank { host },
                item.optLong("lastSuccessMs").coerceAtLeast(0),
                item.optBoolean("customName", false)
            )
        }.distinctBy { it.host }.take(LIMIT)
    } catch (_: Exception) { emptyList() }

    private fun write(ctx: Context, devices: List<Device>) {
        val data = JSONArray()
        devices.take(LIMIT).forEach { d ->
            data.put(JSONObject().put("host", d.host).put("name", d.name)
                .put("lastSuccessMs", d.lastSuccessMs).put("customName", d.customName))
        }
        prefs(ctx).edit().putString(RECORDS, data.toString()).apply()
    }

    /** Migra o pareamento singular da alpha.9 sem gerar nova credencial nem apagá-lo. */
    @Synchronized fun migrateLegacy(ctx: Context) {
        if (prefs(ctx).getBoolean(MIGRATION, false)) return
        val oldHost = normalize(SettingsStore.getRemoteHost(ctx))
        if (validHost(oldHost) && list(ctx).none { it.host == oldHost }) {
            val oldKey = SecretVault.load(ctx, SecretVault.CONTROLLER_KEY)
            if (oldKey != null) {
                SecretVault.save(ctx, SecretVault.profileKeyName(oldHost), oldKey)
                write(ctx, list(ctx) + Device(oldHost, oldHost, 0L))
            }
        }
        prefs(ctx).edit().putBoolean(MIGRATION, true).apply()
    }

    fun key(ctx: Context, host: String): ByteArray? {
        val normalized = normalize(host)
        return if (list(ctx).any { it.host == normalized })
            SecretVault.load(ctx, SecretVault.profileKeyName(normalized)) else null
    }

    @Synchronized fun save(ctx: Context, host: String, key: ByteArray, name: String = "") {
        val normalized = normalize(host)
        require(validHost(normalized)) { "Endereço inválido" }
        require(key.isNotEmpty())
        SecretVault.save(ctx, SecretVault.profileKeyName(normalized), key)
        val previous = list(ctx)
        val old = previous.firstOrNull { it.host == normalized }
        val specifiedName = name.trim().take(80)
        val newest = Device(normalized, specifiedName.ifBlank { old?.name ?: normalized },
            old?.lastSuccessMs ?: 0L, if (specifiedName.isNotBlank()) true else old?.customName ?: false)
        val combined = (listOf(newest) + previous.filterNot { it.host == normalized })
        val removed = combined.drop(LIMIT)
        write(ctx, combined)
        removed.forEach { SecretVault.delete(ctx, SecretVault.profileKeyName(it.host)) }
        SettingsStore.setRemoteHost(ctx, normalized)
    }

    @Synchronized fun markSuccess(ctx: Context, host: String, name: String, atMs: Long) {
        val normalized = normalize(host)
        val old = list(ctx)
        val entry = old.firstOrNull { it.host == normalized } ?: return
        val updated = entry.copy(
            name = if (entry.customName) entry.name else name.trim().take(80).ifBlank { entry.name },
            lastSuccessMs = atMs.coerceAtLeast(0))
        write(ctx, listOf(updated) + old.filterNot { it.host == normalized })
    }

    @Synchronized fun rename(ctx: Context, host: String, name: String) {
        val normalized = normalize(host)
        write(ctx, list(ctx).map { if (it.host == normalized)
            it.copy(name = name.trim().take(80).ifBlank { it.host }, customName = true) else it })
    }

    @Synchronized fun forget(ctx: Context, host: String) {
        val normalized = normalize(host)
        write(ctx, list(ctx).filterNot { it.host == normalized })
        SecretVault.delete(ctx, SecretVault.profileKeyName(normalized))
        if (SettingsStore.getRemoteHost(ctx).equals(normalized, ignoreCase = true)) {
            SettingsStore.setRemoteHost(ctx, "")
            // O slot legado da alpha.9 não pode reintroduzir esta credencial.
            SecretVault.delete(ctx, SecretVault.CONTROLLER_KEY)
        }
    }
}
