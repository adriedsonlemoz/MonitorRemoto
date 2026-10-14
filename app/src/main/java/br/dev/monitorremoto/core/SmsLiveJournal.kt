package br.dev.monitorremoto.core

import org.json.JSONArray
import org.json.JSONObject
import java.util.ArrayDeque

/** SMS recebido em tempo real: somente na RAM e depois de autorização local. */
object SmsLiveJournal {
    private data class Entry(val address: String, val text: String, val whenMs: Long)
    private val entries = ArrayDeque<Entry>()
    @Synchronized fun add(address: String, body: String, whenMs: Long) {
        if (SensitiveContent.isSensitive(address, body) || MessagePolicy.looksLikeAuthenticationSms(body)) return
        if (entries.any { it.address == address && it.text == body && kotlin.math.abs(it.whenMs - whenMs) < 30_000L }) return
        entries.addLast(Entry(address.take(40), body.take(400), whenMs))
        while (entries.size > 20) entries.removeFirst()
    }
    @Synchronized fun clear() = entries.clear()
    @Synchronized fun snapshot(): JSONArray = JSONArray().apply {
        entries.forEach { put(JSONObject().put("address", it.address).put("text", it.text)
            .put("whenMs", it.whenMs).put("direction", "recebido").put("live", true)) }
    }
}
