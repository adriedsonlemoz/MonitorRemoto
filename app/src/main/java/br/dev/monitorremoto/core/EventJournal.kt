package br.dev.monitorremoto.core

import org.json.JSONArray
import org.json.JSONObject
import java.util.ArrayDeque
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

    /** Remove imediatamente eventos quando o acesso do usuário é interrompido. */
    @Synchronized fun clear() {
        records.clear()
    }

    /** Remove eventos de aplicativos não mais autorizados, inclusive do buffer antigo. */
    @Synchronized fun retainAllowed(allowedPackages: Set<String>) {
        records.removeAll { it.app !in allowedPackages }
    }

    /** Revalida a lista positiva ao ENVIAR, inclusive contra notificações em trânsito.
     * Separar o filtro da serialização permite testá-lo na JVM sem android.jar mockável.
     */
    @Synchronized fun latest(allowedPackages: Set<String>): List<RecordedEvent> =
        records.filter { it.app in allowedPackages }.takeLast(30)

    fun latestJson(allowedPackages: Set<String>): JSONArray = JSONArray().apply {
        latest(allowedPackages).forEach { put(it.toJson()) }
    }

    /** Defesa parcial. O filtro não substitui cuidados com dados sensíveis. */
    internal fun sanitized(body: String): String {
        val words = Regex("(?i)(c[oó]digo|verification|verifica[cç][aã]o|otp|2fa|token|senha tempor[aá]ria)")
        val digits = Regex("(?<!\\d)\\d{4,8}(?!\\d)")
        return if (words.containsMatchIn(body) && digits.containsMatchIn(body))
            "[Possível código de verificação ocultado]" else body
    }
}
