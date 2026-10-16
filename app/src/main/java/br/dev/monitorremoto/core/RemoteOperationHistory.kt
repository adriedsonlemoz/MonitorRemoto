package br.dev.monitorremoto.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom

/** Histórico LOCAL e deliberadamente sem nomes/caminhos de arquivos, IPs ou conteúdo.
 * Apenas eventos concluídos são gravados; esta classe não autoriza ações remotas.
 */
object RemoteOperationHistory {
    private const val STORE = "remote_operation_history_v1"
    private const val MAX_ITEMS = 48
    private const val MAX_AGE_MS = 30L * 24L * 60L * 60L * 1000L
    private val lock = Any()

    data class Entry(val timestamp: Long, val operation: String, val outcome: String,
                     val done: Int, val total: Int, val bytes: Long)

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(STORE, Context.MODE_PRIVATE)

    /** Sal criado uma vez; o identificador do host não pode ser revertido diretamente da lista. */
    private fun hostId(context: Context, host: String): String {
        val store = prefs(context)
        var salt = store.getString("salt", null)
        if (salt == null) {
            val value = ByteArray(16).also { SecureRandom().nextBytes(it) }
            salt = android.util.Base64.encodeToString(value, android.util.Base64.NO_WRAP)
            store.edit().putString("salt", salt).commit()
        }
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest((salt + "\u0000" + host).toByteArray(Charsets.UTF_8))
        return bytes.take(12).joinToString("") { "%02x".format(it.toInt() and 255) }
    }

    fun record(context: Context, host: String, operation: String, outcome: String,
               done: Int, total: Int, bytes: Long = 0L) {
        if (host.isBlank() || !RemoteOperationHistoryPolicy.valid(operation, outcome, done, total, bytes)) return
        synchronized(lock) {
            val store = prefs(context)
            val now = System.currentTimeMillis()
            val id = hostId(context, host)
            val previous = runCatching { JSONArray(store.getString("entries", "[]")) }.getOrDefault(JSONArray())
            val next = JSONArray()
            next.put(JSONObject().put("host", id).put("time", now).put("op", operation)
                .put("state", outcome).put("done", done).put("total", total).put("bytes", bytes))
            for (i in 0 until previous.length()) {
                val item = previous.optJSONObject(i) ?: continue
                val at = item.optLong("time")
                if (next.length() < MAX_ITEMS && at > 0L && at <= now && now - at <= MAX_AGE_MS)
                    next.put(item)
            }
            store.edit().putString("entries", next.toString()).apply()
        }
    }

    fun read(context: Context, host: String): List<Entry> {
        if (host.isBlank()) return emptyList()
        synchronized(lock) {
            val store = prefs(context)
            val id = hostId(context, host)
            val now = System.currentTimeMillis()
            val raw = runCatching { JSONArray(store.getString("entries", "[]")) }.getOrDefault(JSONArray())
            return (0 until raw.length()).mapNotNull { index ->
                val item = raw.optJSONObject(index) ?: return@mapNotNull null
                val timestamp = item.optLong("time")
                val operation = item.optString("op")
                val outcome = item.optString("state")
                val done = item.optInt("done", -1)
                val total = item.optInt("total", -1)
                val bytes = item.optLong("bytes", -1)
                if (item.optString("host") != id || timestamp <= 0L || timestamp > now ||
                    now - timestamp > MAX_AGE_MS ||
                    !RemoteOperationHistoryPolicy.valid(operation, outcome, done, total, bytes)) null
                else Entry(timestamp, operation, outcome, done, total, bytes)
            }
        }
    }

    /** Apaga SOMENTE os eventos do host selecionado; preserva os demais aparelhos. */
    fun clear(context: Context, host: String) {
        if (host.isBlank()) return
        synchronized(lock) {
            val store = prefs(context)
            val id = hostId(context, host)
            val raw = runCatching { JSONArray(store.getString("entries", "[]")) }.getOrDefault(JSONArray())
            val left = JSONArray()
            for (i in 0 until raw.length()) raw.optJSONObject(i)?.let {
                if (it.optString("host") != id) left.put(it)
            }
            store.edit().putString("entries", left.toString()).apply()
        }
    }
}
