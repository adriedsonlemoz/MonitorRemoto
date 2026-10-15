package br.dev.monitorremoto.core

/** Formatos fixos. Nenhum comando arbitrário de arquivo ou caminho absoluto chega ao servidor. */
object RemoteManagePolicy {
    val actions = setOf("mkdir", "rename", "move", "copy", "delete", "trash")
    fun allowed(action: String, path: List<String>, destination: List<String> = emptyList(),
        name: String = ""): Boolean {
        if (action !in actions || !RemoteFilePolicy.validPath(path) || !RemoteFilePolicy.validPath(destination)) return false
        return when (action) {
            "mkdir" -> path.size <= RemoteFilePolicy.MAX_DEPTH && RemoteFilePolicy.validSegment(name)
            "rename" -> path.isNotEmpty() && RemoteFilePolicy.validSegment(name)
            "move", "copy" -> path.isNotEmpty() && destination.size <= RemoteFilePolicy.MAX_DEPTH &&
                !(destination.size >= path.size && destination.take(path.size) == path)
            "delete", "trash" -> path.isNotEmpty()
            else -> false
        }
    }
}
