package br.dev.monitorremoto.core

import android.app.Activity
import org.json.JSONArray
import org.json.JSONObject
import java.util.LinkedHashMap
import java.util.UUID

/** Estados efêmeros de SMS enviados. Nenhum corpo de mensagem é guardado. */
object SmsDeliveryJournal {
    private data class Delivery(val id: String, val address: String, val whenMs: Long,
        val parts: Int, val sent: MutableSet<Int> = mutableSetOf(),
        val delivered: MutableSet<Int> = mutableSetOf(), var state: String = "solicitado")
    private val records = LinkedHashMap<String, Delivery>()
    @Synchronized fun begin(address: String, parts: Int): String {
        val id = UUID.randomUUID().toString()
        records[id] = Delivery(id, address.take(32), System.currentTimeMillis(), parts)
        while (records.size > 30) records.remove(records.keys.first())
        return id
    }
    @Synchronized fun update(id: String, stage: String, partIndex: Int, resultCode: Int) {
        val entry = records[id] ?: return
        if (partIndex !in 0 until entry.parts || entry.state == "falhou") return
        if (stage == "sent") {
            if (resultCode != Activity.RESULT_OK) entry.state = "falhou"
            else { entry.sent.add(partIndex); if (entry.sent.size == entry.parts && entry.state == "solicitado") entry.state = "enviado" }
        } else if (stage == "delivered") {
            if (resultCode != Activity.RESULT_OK) entry.state = "entrega não confirmada"
            else { entry.delivered.add(partIndex); if (entry.delivered.size == entry.parts) entry.state = "entregue" }
        }
    }
    @Synchronized fun failed(id: String) { records[id]?.state = "falhou" }
    @Synchronized fun clear() = records.clear()
    @Synchronized fun latest(): JSONArray = JSONArray().apply {
        records.values.toList().takeLast(20).forEach { put(JSONObject().put("address", it.address)
            .put("whenMs", it.whenMs).put("state", it.state)) }
    }
}
