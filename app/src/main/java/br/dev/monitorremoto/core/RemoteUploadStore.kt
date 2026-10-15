package br.dev.monitorremoto.core

import android.content.Context
import android.util.Base64
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID

/** Estágios privados no cache até a conferência SHA-256; nunca escreve arquivo remoto parcialmente. */
object RemoteUploadStore {
    private const val TTL = 30 * 60 * 1000L
    private data class Pending(val file: File, val owner: String, val parent: List<String>,
        val name: String, val mime: String, val total: Long, var written: Long,
        var touched: Long, val digest: MessageDigest)
    private val sessions = HashMap<String, Pending>()

    @Synchronized private fun clean(now: Long) {
        val expired = sessions.filterValues { now - it.touched > TTL }.keys.toList()
        expired.forEach { id -> sessions.remove(id)?.file?.delete() }
    }

    @Synchronized fun start(context: Context, owner: String, parent: List<String>, name: String,
        mime: String, size: Long): JSONObject {
        require(owner.isNotBlank() && owner.length <= 120 && RemoteFilePolicy.validPath(parent) &&
            parent.size <= RemoteFilePolicy.MAX_DEPTH && RemoteFilePolicy.validSegment(name) &&
            mime.length in 3..110 && '/' in mime && RemoteFilePolicy.validUploadSize(size)) { "Envio inválido." }
        clean(System.currentTimeMillis())
        val directory = File(context.cacheDir, "remote-uploads").apply { mkdirs() }
        directory.listFiles()?.filter { it.name.endsWith(".part") &&
            System.currentTimeMillis() - it.lastModified() > TTL }?.forEach { it.delete() }
        check(sessions.size < 1) { "Outro envio está ativo neste monitorado." }
        val id = UUID.randomUUID().toString()
        val staged = File.createTempFile("staged-", ".part", directory)
        sessions[id] = Pending(staged, owner, parent.toList(), name, mime, size, 0L,
            System.currentTimeMillis(), MessageDigest.getInstance("SHA-256"))
        return JSONObject().put("session", id).put("offset", 0L)
    }

    @Synchronized fun status(owner: String, id: String): JSONObject {
        clean(System.currentTimeMillis())
        val session = sessions[id] ?: error("Envio expirado.")
        check(session.owner == owner) { "Controlador não autorizado para esta sessão." }
        return JSONObject().put("offset", session.written).put("size", session.total)
    }

    @Synchronized fun append(owner: String, id: String, offset: Long, data: String): JSONObject {
        clean(System.currentTimeMillis())
        val current = sessions[id] ?: error("Envio expirado. Inicie novamente.")
        check(current.owner == owner) { "Sessão de outro controlador." }
        val bytes = Base64.decode(data, Base64.NO_WRAP)
        require(bytes.isNotEmpty() && bytes.size <= RemoteFilePolicy.UPLOAD_CHUNK_BYTES &&
            offset == current.written && current.written + bytes.size <= current.total) { "Bloco de envio inválido ou fora de ordem." }
        FileOutputStream(current.file, true).use { it.write(bytes) }
        current.digest.update(bytes)
        current.written += bytes.size
        current.touched = System.currentTimeMillis()
        return JSONObject().put("offset", current.written)
    }

    @Synchronized fun finish(owner: String, id: String, expectedHash: String,
        writer: (List<String>, String, String, File) -> Unit): JSONObject {
        clean(System.currentTimeMillis())
        val pending = sessions[id] ?: error("Envio não encontrado.")
        check(pending.owner == owner && pending.written == pending.total) { "Envio incompleto ou sem autorização." }
        require(expectedHash.length == 64 && expectedHash.all { it in '0'..'9' || it in 'a'..'f' })
        check(pending.digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) } == expectedHash) {
            sessions.remove(id); pending.file.delete(); "Hash de envio divergente."
        }
        try { writer(pending.parent, pending.name, pending.mime, pending.file) }
        finally { sessions.remove(id); pending.file.delete() }
        return JSONObject().put("bytes", pending.written)
    }

    @Synchronized fun abort(owner: String, id: String) {
        val pending = sessions[id] ?: return
        if (pending.owner == owner) { sessions.remove(id); pending.file.delete() }
    }
    @Synchronized fun clear() { sessions.values.forEach { it.file.delete() }; sessions.clear() }
}
