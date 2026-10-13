package br.dev.monitorremoto.core

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Buffer VOLÁTIL e limitado: nenhuma mensagem é persistida no armazenamento. */
object EventJournal {
    private const val CAPACITY = 100
    private val records = ArrayDeque<RecordedEvent>()
    private var lastId = 0L
    val epoch: String = UUID.randomUUID().toString()

    data class RecordedEvent(
        val id: Long, val whenMs: Long, val type: String,
        val app: String, val title: String, val preview: String
    ) {
        fun toJson(): JSONObject = JSONObject().apply {
            put("id", id); put("whenMs", whenMs); put("type", type)
            put("app", app); put("title", title); put("preview", preview)
        }
    }

    @Synchronized fun add(type: String, app: String, title: String, preview: String) {
        if (title.isBlank() && preview.isBlank()) return
        lastId++
        records.addLast(RecordedEvent(
            lastId, System.currentTimeMillis(), type,
            app.take(100), title.take(120), sanitized(preview).take(400)
        ))
        while (records.size > CAPACITY) records.removeFirst()
    }

    @Synchronized fun latestJson(): JSONArray = JSONArray().apply {
        records.takeLast(30).forEach { put(it.toJson()) }
    }

    /** Defesa parcial. O filtro não substitui cuidados com dados sensíveis. */
    internal fun sanitized(body: String): String {
        val words = Regex("(?i)(c[oó]digo|verification|verifica[cç][aã]o|otp|2fa|token|senha tempor[aá]ria)")
        val digits = Regex("(?<!\\d)\\d{4,8}(?!\\d)")
        return if (words.containsMatchIn(body) && digits.containsMatchIn(body))
            "[Possível código de verificação ocultado]" else body
    }
}
