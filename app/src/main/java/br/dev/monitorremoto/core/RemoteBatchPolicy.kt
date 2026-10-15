package br.dev.monitorremoto.core

import java.util.Locale

/** Limites e geração de nomes não destrutivos para copiar arquivos SAF ao controlador. */
object RemoteBatchPolicy {
    const val MAX_FILES = 20
    const val MAX_TOTAL_BYTES = 2L * 1024L * 1024L * 1024L

    fun eligible(sizes: List<Long>): Boolean = sizes.isNotEmpty() && sizes.size <= MAX_FILES &&
        sizes.all { it in 1..RemoteFilePolicy.MAX_FILE_BYTES } && sizes.fold(0L) { a, b -> a + b } <= MAX_TOTAL_BYTES

    fun availableName(desired: String, existing: Set<String>): String {
        require(RemoteFilePolicy.validSegment(desired)) { "Nome do arquivo inválido." }
        val lower = existing.mapTo(HashSet()) { it.lowercase(Locale.ROOT) }
        if (desired.lowercase(Locale.ROOT) !in lower) return desired
        val dot = desired.lastIndexOf('.')
        val stem = if (dot > 0) desired.substring(0, dot) else desired
        val extension = if (dot > 0) desired.substring(dot) else ""
        for (index in 2..999) {
            val candidate = "$stem ($index)$extension"
            if (RemoteFilePolicy.validSegment(candidate) && candidate.lowercase(Locale.ROOT) !in lower) return candidate
        }
        error("Não foi possível encontrar um nome livre no destino.")
    }
}
