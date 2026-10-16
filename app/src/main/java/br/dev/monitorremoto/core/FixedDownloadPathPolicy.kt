package br.dev.monitorremoto.core

/** Política pura (sem Android) para limitar operações à pasta relativa fixa. */
object FixedDownloadPathPolicy {
    const val ROOT = "Download/Monitor Remoto/"

    fun folder(parts: List<String>): String {
        require(RemoteFilePolicy.validPath(parts) && parts.size <= RemoteFilePolicy.MAX_DEPTH) {
            "Pasta fora do limite permitido."
        }
        return ROOT + if (parts.isEmpty()) "" else parts.joinToString("/", postfix = "/")
    }

    fun contained(relative: String): Boolean {
        if (!relative.startsWith(ROOT)) return false
        val suffix = relative.removePrefix(ROOT)
        if (suffix.isEmpty()) return true
        if (!suffix.endsWith('/')) return false
        val segments = suffix.trimEnd('/').split('/')
        return RemoteFilePolicy.validPath(segments) && segments.size <= RemoteFilePolicy.MAX_DEPTH &&
            folder(segments) == relative
    }
}
