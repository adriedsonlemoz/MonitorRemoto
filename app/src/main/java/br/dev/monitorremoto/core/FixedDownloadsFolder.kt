package br.dev.monitorremoto.core

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** Área fixa no armazenamento compartilhado: apenas arquivos pertencentes a este app no MediaStore.
 * NÃO concede acesso a Downloads de terceiros nem ao restante do armazenamento.
 */
object FixedDownloadsFolder {
    private val BASE = FixedDownloadPathPolicy.ROOT
    private const val README = "LEIA-ME-MONITOR-REMOTO.txt"
    private const val MAX = 150
    private const val FOLDER_FLAGS = DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE
    private const val FILE_FLAGS = DocumentsContract.Document.FLAG_SUPPORTS_DELETE or
        DocumentsContract.Document.FLAG_SUPPORTS_RENAME or DocumentsContract.Document.FLAG_SUPPORTS_MOVE or
        DocumentsContract.Document.FLAG_SUPPORTS_COPY
    private val collection get() = MediaStore.Downloads.EXTERNAL_CONTENT_URI
    private val cols = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME,
        MediaStore.MediaColumns.MIME_TYPE, MediaStore.MediaColumns.SIZE,
        MediaStore.MediaColumns.DATE_MODIFIED, MediaStore.MediaColumns.RELATIVE_PATH,
        MediaStore.MediaColumns.OWNER_PACKAGE_NAME)
    private data class Stored(val uri: Uri, val name: String, val mime: String, val size: Long,
        val modified: Long, val relative: String)

    fun supported() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    fun available(context: Context): Boolean = supported() && SettingsStore.isMonitorEnabled(context)
    private fun valid(parts: List<String>) = RemoteFilePolicy.validPath(parts) &&
        parts.size <= RemoteFilePolicy.MAX_DEPTH
    private fun relative(parts: List<String>): String = FixedDownloadPathPolicy.folder(parts)

    private fun parts(request: JSONObject): List<String> {
        val array = request.optJSONArray("path") ?: error("Caminho remoto ausente.")
        require(array.length() <= RemoteFilePolicy.MAX_FILE_PATH_PARTS) { "Caminho profundo demais." }
        return (0 until array.length()).map { array.getString(it) }.also { require(valid(it)) }
    }
    /** As pastas vazias são índices locais. Arquivos sobrevivem à limpeza do cache e são
     * redescobertos por RELATIVE_PATH. Não incluir diretórios de outros aplicativos. */
    private fun folderKey(context: Context) = context.getSharedPreferences("fixed_transfer_folders", Context.MODE_PRIVATE)
    private fun known(context: Context): MutableSet<String> = folderKey(context).getStringSet("folders", emptySet())!!.toMutableSet()
    private fun persist(context: Context, folders: Set<String>) {
        folderKey(context).edit().putStringSet("folders", folders).apply()
    }
    private fun folderExists(context: Context, path: List<String>): Boolean {
        if (path.isEmpty()) return true
        val key = path.joinToString("/")
        return key in known(context) || all(context).any { it.relative.startsWith(relative(path)) }
    }
    private fun checkFolder(context: Context, path: List<String>) {
        require(folderExists(context, path)) { "Pasta não encontrada no Monitor Remoto." }
    }
    private fun all(context: Context): List<Stored> {
        val result = ArrayList<Stored>()
        val resolver = context.contentResolver
        resolver.query(collection, cols,
            "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?", arrayOf("$BASE%"), null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val owner = cursor.getString(6)
                if (owner != context.packageName) continue
                val rel = cursor.getString(5) ?: continue
                if (!FixedDownloadPathPolicy.contained(rel)) continue
                val sub = rel.removePrefix(BASE).trimEnd('/')
                if (sub.isNotEmpty() && !valid(sub.split('/'))) continue
                val name = cursor.getString(1) ?: continue
                if (!RemoteFilePolicy.validSegment(name)) continue
                result.add(Stored(ContentUris.withAppendedId(collection, cursor.getLong(0)), name,
                    cursor.getString(2) ?: "application/octet-stream", cursor.getLong(3),
                    cursor.getLong(4) * 1000L, rel))
            }
        }
        return result
    }
    private fun file(context: Context, path: List<String>): Stored {
        require(path.isNotEmpty() && RemoteFilePolicy.validPath(path))
        val matches = all(context).filter { it.relative == relative(path.dropLast(1)) && it.name == path.last() }
        check(matches.size == 1) { "Arquivo inexistente ou nome ambíguo na pasta fixa." }
        return matches.single()
    }
    private fun exists(context: Context, parent: List<String>, name: String): Boolean =
        all(context).any { it.relative == relative(parent) && it.name.equals(name, true) } ||
            folderExists(context, parent + name)

    /** Materializa a pasta no gerenciador de arquivos. MediaStore não oferece API para pasta vazia;
     * o pequeno arquivo de instruções cria a pasta real automaticamente. */
    @Synchronized fun ensure(context: Context) {
        if (!supported()) return
        if (all(context).any { it.relative == BASE && it.name == README }) return
        val info = "Monitor Remoto: arquivos transferidos para este aparelho ficam aqui.\n" +
            "Apenas arquivos criados pelo Monitor Remoto ficam disponíveis para acesso remoto sem selecionar uma pasta.\n" +
            "Arquivos pessoais de outros aplicativos continuam protegidos pelo Android.\n"
        create(context, emptyList(), README, "text/plain") { uri ->
            context.contentResolver.openOutputStream(uri, "w")?.use { it.write(info.toByteArray(Charsets.UTF_8)) }
                ?: error("Não foi possível preparar pasta de transferência.")
        }
    }
    private fun create(context: Context, parent: List<String>, name: String, mime: String,
        write: (Uri) -> Unit): Uri {
        checkFolder(context, parent)
        require(RemoteFilePolicy.validSegment(name) && mime.length in 3..110 && '/' in mime)
        check(!exists(context, parent, name)) { "Nome já existente: $name" }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relative(parent))
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = context.contentResolver.insert(collection, values) ?: error("Não foi possível criar arquivo em Downloads.")
        try {
            write(uri)
            check(context.contentResolver.update(uri, ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }, null, null) == 1) { "Android não publicou o arquivo." }
        } catch (e: Exception) { runCatching { context.contentResolver.delete(uri, null, null) }; throw e }
        return uri
    }
    private fun listing(context: Context, parent: List<String>, response: JSONObject): JSONObject {
        checkFolder(context, parent)
        val rel = relative(parent)
        val files = all(context).filter { it.relative == rel }
        val discovered = all(context).mapNotNull {
            if (it.relative.startsWith(rel) && it.relative != rel)
                it.relative.removePrefix(rel).substringBefore('/').takeIf(String::isNotBlank)
            else null
        }
        val saved = known(context).mapNotNull { path ->
            val prefix = if (parent.isEmpty()) "" else parent.joinToString("/", postfix = "/")
            if (path.startsWith(prefix) && path.length > prefix.length) path.removePrefix(prefix).substringBefore('/')
            else null
        }
        val folders = (discovered + saved).filter(RemoteFilePolicy::validSegment).distinct()
        val sorted = JSONArray()
        val items = folders.sorted().map { name -> JSONObject().put("name", name).put("folder", true)
            .put("mime", DocumentsContract.Document.MIME_TYPE_DIR).put("size", -1L).put("modified", 0L)
            .put("flags", FOLDER_FLAGS) } + files.sortedBy { it.name.lowercase() }.map { item ->
            JSONObject().put("name", item.name).put("folder", false).put("mime", item.mime)
                .put("size", item.size).put("modified", item.modified).put("flags", FILE_FLAGS)
        }
        items.take(MAX).forEach { sorted.put(it) }
        return response.put("ok", true).put("entries", sorted)
            .put("truncated", items.size > MAX).put("writable", true)
    }
    private fun metadata(request: JSONObject, item: Stored) {
        check(item.size in 1..RemoteFilePolicy.MAX_FILE_BYTES) { "Arquivo vazio ou maior que 2 GiB." }
        check(request.optLong("size", -1L) == item.size && request.optLong("modified", -1L) == item.modified) {
            "O arquivo mudou desde a listagem." }
    }
    fun handle(context: Context, request: JSONObject): JSONObject {
        val response = JSONObject().put("v", 1).put("op", request.optString("op"))
            .put("appVersion", br.dev.monitorremoto.transport.AppVersion.NAME)
        if (!available(context)) return response.put("ok", false).put("message", "Monitoramento desativado ou Android anterior ao 10.")
        return runCatching {
            ensure(context)
            val path = parts(request)
            val op = request.optString("op")
            if (op.startsWith("dataUpload") || op == "dataManage")
                return@runCatching manage(context, request, path, response)
            if (op == "dataList") return@runCatching listing(context, path, response)
            val item = file(context, path)
            metadata(request, item)
            val isHash = op == "dataHash"
            val isMulti = op == "dataChunks"
            val isFast = op == "dataRead"
            val offset = request.optLong("offset", -1L)
            require(isHash || if (isFast) RemoteFilePolicy.validFastChunk(item.size, offset)
                else RemoteFilePolicy.validChunk(item.size, offset)) { "Bloco remoto inválido." }
            if (isHash) {
                val digest = MessageDigest.getInstance("SHA-256")
                var counted = 0L
                context.contentResolver.openInputStream(item.uri)?.use { input ->
                    val data = ByteArray(64 * 1024)
                    while (true) { val n = input.read(data); if (n < 0) break
                        if (n > 0) { digest.update(data, 0, n); counted += n } }
                } ?: error("Sem leitura do arquivo.")
                check(counted == item.size) { "Arquivo mudou." }
                response.put("ok", true).put("size", item.size).put("modified", item.modified)
                    .put("sha256", digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) })
            } else {
                val blocks = JSONArray()
                repeat(if (isMulti) 2 else 1) { index ->
                    val at = offset + index * RemoteFilePolicy.CHUNK_BYTES.toLong()
                    val size = if (isFast) RemoteFilePolicy.nextFastLength(item.size, at)
                        else RemoteFilePolicy.nextChunkLength(item.size, at)
                    if (size > 0) {
                        val buffer = ByteArray(size)
                        context.contentResolver.openInputStream(item.uri)?.use { input ->
                            var toSkip = at
                            while (toSkip > 0) { val skip = input.skip(toSkip); check(skip > 0); toSkip -= skip }
                            var n = 0
                            while (n < size) { val got = input.read(buffer, n, size - n); check(got > 0); n += got }
                        } ?: error("Sem leitura do arquivo.")
                        val data = JSONObject().put("offset", at).put("bytes", Base64.encodeToString(buffer, Base64.NO_WRAP))
                            .put("sha256", MessageDigest.getInstance("SHA-256").digest(buffer)
                                .joinToString("") { "%02x".format(it.toInt() and 255) })
                        if (isMulti) blocks.put(data) else response.put("offset", at).put("bytes", data.getString("bytes"))
                            .put("sha256", data.getString("sha256"))
                    }
                }
                response.put("ok", true).put("size", item.size).put("modified", item.modified)
                if (isMulti) response.put("offset", offset).put("chunks", blocks)
            }
            response
        }.getOrElse { response.put("ok", false).put("message", (it.message ?: "Falha ao consultar Downloads.").take(170)) }
    }
    fun stream(context: Context, request: JSONObject, onPrefixProgress: (Long) -> Unit = {},
        emit: (ByteArray, Int, Long) -> Unit): ByteArray {
        check(available(context)) { "Monitoramento desativado." }
        val source = file(context, parts(request))
        metadata(request, source)
        val offset = request.optLong("offset", 0L)
        require(offset in 0..source.size && offset % RemoteFilePolicy.FAST_CHUNK_BYTES == 0L)
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(RemoteFilePolicy.FAST_CHUNK_BYTES)
        var readTotal = 0L
        var lastReport = 0L
        context.contentResolver.openInputStream(source.uri)?.use { raw ->
            raw.buffered(128 * 1024).use { stream ->
                while (readTotal < source.size) {
                    check(available(context) && !Thread.currentThread().isInterrupted) { "Transferência interrompida." }
                    val expected = minOf(buffer.size.toLong(), source.size - readTotal).toInt()
                    var got = 0
                    while (got < expected) { val n = stream.read(buffer, got, expected - got)
                        check(n > 0) { "Arquivo terminou prematuramente." }; got += n }
                    digest.update(buffer, 0, got)
                    if (readTotal >= offset) emit(buffer, got, readTotal)
                    else if (readTotal + got - lastReport >= 3L * 1024 * 1024) {
                        lastReport = readTotal + got; onPrefixProgress(lastReport)
                    }
                    readTotal += got
                }
                check(stream.read() == -1) { "Origem cresceu durante a transferência." }
            }
        } ?: error("Android negou a leitura do arquivo.")
        check(readTotal == source.size)
        return digest.digest()
    }
    private fun manage(context: Context, request: JSONObject, parts: List<String>, response: JSONObject): JSONObject {
        val op = request.optString("op")
        if (op.startsWith("dataUpload")) {
            val owner = request.optString("clientId")
            val result = when (op) {
                "dataUploadStart" -> {
                    checkFolder(context, parts)
                    val name = request.optString("name")
                    check(RemoteFilePolicy.validSegment(name) && !exists(context, parts, name)) { "Arquivo de destino já existe." }
                    RemoteUploadStore.start(context, owner, parts, name, request.optString("mime"), request.optLong("size", -1))
                }
                "dataUploadStatus" -> RemoteUploadStore.status(owner, request.optString("session"))
                "dataUploadChunk" -> RemoteUploadStore.append(owner, request.optString("session"), request.optLong("offset", -1), request.getString("bytes"))
                "dataUploadAbort" -> { RemoteUploadStore.abort(owner, request.optString("session")); JSONObject() }
                "dataUploadFinish" -> RemoteUploadStore.finish(owner, request.optString("session"), request.optString("sha256")) { parent, name, mime, stage ->
                    checkFolder(context, parent)
                    create(context, parent, name, mime) { uri ->
                        context.contentResolver.openOutputStream(uri, "w")?.use { output ->
                            stage.inputStream().use { it.copyTo(output, 64 * 1024) }
                        } ?: error("Android recusou gravar o arquivo enviado.")
                    }
                }
                else -> error("Operação de upload inválida.")
            }
            return response.put("ok", true).put("result", result)
        }
        val destJson = request.optJSONArray("destination") ?: JSONArray()
        val destination = (0 until destJson.length()).map { destJson.getString(it) }
        val name = request.optString("name")
        val action = request.optString("action")
        require(RemoteManagePolicy.allowed(action, parts, destination, name)) { "Ação não permitida." }
        when (action) {
            "mkdir" -> {
                checkFolder(context, parts)
                check(!exists(context, parts, name)) { "Pasta já existe." }
                val set = known(context); set.add((parts + name).joinToString("/")); persist(context, set)
            }
            "delete", "trash", "rename", "copy", "move" -> {
                val isFolder = folderExists(context, parts) && all(context).none {
                    it.relative == relative(parts.dropLast(1)) && it.name == parts.last() }
                if (isFolder) {
                    check(action == "delete") { "Para esta pasta, somente excluir é suportado." }
                    check(all(context).none { it.relative.startsWith(relative(parts)) }) { "A pasta não está vazia." }
                    val set = known(context)
                    val prefix = parts.joinToString("/")
                    check(set.none { it.startsWith("$prefix/") }) { "A pasta contém subpastas." }
                    set.remove(prefix); persist(context, set)
                } else {
                    val item = file(context, parts)
                    val resolver = context.contentResolver
                    when (action) {
                        "delete" -> check(resolver.delete(item.uri, null, null) == 1) { "Não foi possível excluir." }
                        "rename" -> {
                            check(!exists(context, parts.dropLast(1), name)) { "Nome duplicado." }
                            check(resolver.update(item.uri, ContentValues().apply {
                                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                            }, null, null) == 1) { "Não foi possível renomear." }
                        }
                        "move", "trash" -> {
                            val target = if (action == "trash") listOf("Lixeira") else destination
                            if (action == "trash" && !folderExists(context, target)) {
                                val set = known(context); set.add("Lixeira"); persist(context, set)
                            }
                            checkFolder(context, target)
                            check(!exists(context, target, item.name)) { "O destino já possui este nome." }
                            check(resolver.update(item.uri, ContentValues().apply {
                                put(MediaStore.MediaColumns.RELATIVE_PATH, relative(target))
                            }, null, null) == 1) { "Não foi possível mover." }
                        }
                        "copy" -> {
                            checkFolder(context, destination)
                            create(context, destination, item.name, item.mime) { uri ->
                                resolver.openInputStream(item.uri)?.use { source ->
                                    resolver.openOutputStream(uri, "w")?.use { source.copyTo(it, 64 * 1024) }
                                        ?: error("Sem permissão para criar cópia.")
                                } ?: error("Sem leitura para copiar.")
                            }
                        }
                    }
                }
            }
            else -> error("Ação inválida.")
        }
        return response.put("ok", true).put("documentChanged", true)
    }
}
