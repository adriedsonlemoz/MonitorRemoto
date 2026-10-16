package br.dev.monitorremoto.core

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Properties

/** Checkpoint local de downloads destinados a Downloads, em noBackupFilesDir.
 * Nunca contém IP, chave de pareamento ou caminho remoto em texto puro.
 * Apenas arquivos criados por este módulo são eliminados na limpeza.
 */
object RemoteDownloadResume {
    private const val AGE_LIMIT_MS = 7L * 24 * 60 * 60 * 1000
    private const val MAX_RECORDS = 20
    data class Partial(val id: String, val name: String, val bytes: Long, val total: Long, val lastTouched: Long)
    data class Checkpoint(val file: File, val id: String, val resumeAt: Long)

    private fun hash(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) }
    fun id(host: String, path: List<String>, size: Long, modified: Long): String =
        hash(listOf(host, path.size.toString(), path.joinToString("\u0000"), size.toString(), modified.toString())
            .joinToString("\u0001"))
    fun hostId(host: String) = hash(host)
    fun folder(noBackup: File) = File(noBackup, "monitor-remoto-downloads").also {
        check(it.isDirectory || it.mkdirs()) { "Não foi possível preparar cópia parcial." }
    }
    private fun metadata(dir: File, id: String) = File(dir, "$id.meta")
    private fun data(dir: File, id: String) = File(dir, "$id.part")
    private fun props(f: File): Properties = Properties().also { FileInputStream(f).use(it::load) }

    fun prepare(dir: File, id: String, host: String, name: String, total: Long, modified: Long, chunk: Int): Checkpoint {
        require(id.length == 64 && id.all { it in '0'..'9' || it in 'a'..'f' })
        require(RemoteFilePolicy.validSegment(name) && total in 1..RemoteFilePolicy.MAX_FILE_BYTES)
        val meta = metadata(dir, id)
        val part = data(dir, id)
        val fresh = !meta.exists() || !part.exists() || runCatching {
            val saved = props(meta)
            saved.getProperty("id") != id || saved.getProperty("host") != hostId(host) ||
                saved.getProperty("size") != total.toString() || saved.getProperty("modified") != modified.toString()
        }.getOrDefault(true)
        if (fresh) { meta.delete(); part.delete() }
        if (!part.exists()) check(part.createNewFile()) { "Não foi possível criar parcial." }
        val length = part.length()
        val aligned = alignedLength(length, total, chunk)
        if (length != aligned) java.io.RandomAccessFile(part, "rw").use { it.setLength(aligned) }
        Properties().apply {
            setProperty("id", id); setProperty("host", hostId(host)); setProperty("name", name)
            setProperty("size", total.toString()); setProperty("modified", modified.toString())
        }.also { saved -> FileOutputStream(meta).use { saved.store(it, "Copia parcial Monitor Remoto") } }
        meta.setLastModified(System.currentTimeMillis())
        return Checkpoint(part, id, aligned)
    }

    fun alignedLength(length: Long, total: Long, chunk: Int): Long {
        require(total >= 0 && chunk > 0)
        return minOf(length.coerceAtLeast(0L), total) / chunk * chunk
    }
    fun discard(dir: File, id: String) {
        if (id.length != 64 || id.any { it !in '0'..'9' && it !in 'a'..'f' }) return
        metadata(dir, id).delete(); data(dir, id).delete()
    }
    fun list(dir: File, host: String): List<Partial> = dir.listFiles { f -> f.isFile && f.name.endsWith(".meta") }
        ?.asSequence()?.take(128)?.mapNotNull { file ->
            runCatching {
                val properties = props(file)
                val id = properties.getProperty("id") ?: return@runCatching null
                if (properties.getProperty("host") != hostId(host) || id.length != 64 ||
                    id.any { it !in '0'..'9' && it !in 'a'..'f' }) return@runCatching null
                val part = data(dir, id)
                val size = properties.getProperty("size").toLong()
                val name = properties.getProperty("name")
                if (!part.exists() || size !in 1..RemoteFilePolicy.MAX_FILE_BYTES ||
                    !RemoteFilePolicy.validSegment(name)) return@runCatching null
                Partial(id, name, part.length().coerceAtMost(size), size, file.lastModified())
            }.getOrNull()
        }?.sortedByDescending { it.lastTouched }?.take(MAX_RECORDS)?.toList() ?: emptyList()

    fun cleanup(dir: File, now: Long = System.currentTimeMillis()) {
        dir.listFiles { f -> f.isFile && f.name.endsWith(".meta") }?.take(256)?.forEach { meta ->
            val id = meta.name.removeSuffix(".meta")
            if (now - meta.lastModified() > AGE_LIMIT_MS) discard(dir, id)
        }
        // Nunca apagar fora do diretório privado ou arquivos que não pertençam a este esquema.
    }
}
