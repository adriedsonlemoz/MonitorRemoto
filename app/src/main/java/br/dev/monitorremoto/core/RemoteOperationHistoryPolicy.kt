package br.dev.monitorremoto.core

/** Regras puras, compartilhadas com testes JVM locais. */
object RemoteOperationHistoryPolicy {
    val operations = setOf("download", "upload", "mkdir", "rename", "move", "copy", "trash", "delete")
    val outcomes = setOf("concluido", "parcial", "cancelado", "falhou")
    fun valid(operation: String, outcome: String, done: Int, total: Int, bytes: Long): Boolean =
        operation in operations && outcome in outcomes && total in 1..150 && done in 0..total &&
            bytes in 0L..(2L * 1024L * 1024L * 1024L)

    fun label(operation: String) = when (operation) {
        "download" -> "Download"; "upload" -> "Envio"; "mkdir" -> "Nova pasta"
        "rename" -> "Renomear"; "move" -> "Mover"; "copy" -> "Copiar"
        "trash" -> "Lixeira"; "delete" -> "Excluir"; else -> "Operação"
    }
    fun outcome(completed: Boolean, canceled: Boolean, done: Int): String = when {
        completed -> "concluido"
        canceled && done == 0 -> "cancelado"
        done > 0 -> "parcial"
        else -> "falhou"
    }
}
