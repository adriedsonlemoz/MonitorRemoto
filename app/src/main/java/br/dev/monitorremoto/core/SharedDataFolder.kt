package br.dev.monitorremoto.core

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.FileInputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/** Somente a árvore SAF escolhida pelo proprietário do telefone monitorado. Sem acesso amplo ao armazenamento. */
object SharedDataFolder {
    private const val DIRECTORY = DocumentsContract.Document.MIME_TYPE_DIR
    private val columns = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_SIZE,
        DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        DocumentsContract.Document.COLUMN_FLAGS
    )
    private data class Item(val id: String, val name: String, val mime: String, val size: Long, val modified: Long, val flags: Int) {
        val folder get() = mime == DIRECTORY
        fun json() = JSONObject().put("name", name).put("folder", folder)
            .put("mime", mime).put("size", size).put("modified", modified).put("flags", flags)
    }

    fun available(context: Context): Boolean {
        val uri = tree(context) ?: return false
        return SettingsStore.isMonitorEnabled(context) && hasPermission(context, uri)
    }

    private fun hasPermission(context: Context, uri: Uri): Boolean =
        context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }

    fun writable(context: Context): Boolean = available(context) && SettingsStore.allowRemoteFileWrite(context) &&
        tree(context)?.let { uri -> context.contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isReadPermission && it.isWritePermission } } == true

    private fun tree(context: Context): Uri? = SettingsStore.sharedDataTree(context)
        .takeIf { it.isNotBlank() }?.let { runCatching { Uri.parse(it) }.getOrNull() }

    /** Chamado unicamente após ACTION_OPEN_DOCUMENT_TREE no próprio monitorado. */
    fun authorize(context: Context, uri: Uri, flags: Int) {
        require(DocumentsContract.isTreeUri(uri)) { "Escolha uma pasta pelo seletor do Android." }
        require(flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0) { "Acesso de leitura não concedido." }
        val granted = Intent.FLAG_GRANT_READ_URI_PERMISSION or
            (flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        context.contentResolver.takePersistableUriPermission(uri, granted)
        check(hasPermission(context, uri)) { "Android não persistiu a permissão da pasta." }
        val old = tree(context)
        SettingsStore.setAllowRemoteFileWrite(context, false)
        RemoteUploadStore.clear()
        SettingsStore.setSharedDataTree(context, uri.toString())
        if (old != null && old != uri) runCatching {
            context.contentResolver.releasePersistableUriPermission(old, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
    }

    fun revoke(context: Context) {
        val old = tree(context)
        SettingsStore.setSharedDataTree(context, "")
        SettingsStore.setAllowRemoteFileWrite(context, false)
        RemoteUploadStore.clear()
        if (old != null) runCatching {
            context.contentResolver.releasePersistableUriPermission(old, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
    }

    private fun cursorItems(cursor: Cursor, max: Int): Pair<List<Item>, Boolean> {
        val found = ArrayList<Item>()
        while (cursor.moveToNext() && found.size <= max) {
            val id = cursor.getString(0) ?: continue
            val name = cursor.getString(1) ?: continue
            if (!RemoteFilePolicy.validSegment(name)) continue
            val mime = cursor.getString(2) ?: "application/octet-stream"
            val size = if (cursor.isNull(3)) -1L else cursor.getLong(3)
            val modified = if (cursor.isNull(4)) 0L else cursor.getLong(4)
            found.add(Item(id, name, mime, size, modified, if (cursor.isNull(5)) 0 else cursor.getInt(5)))
        }
        return found.take(max) to (found.size > max)
    }

    private fun children(context: Context, tree: Uri, parent: String, limit: Int): Pair<List<Item>, Boolean> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parent)
        context.contentResolver.query(uri, columns, null, null, null)?.use { cursor ->
            return cursorItems(cursor, limit)
        }
        error("A pasta não pôde ser consultada no Android.")
    }

    private fun metadata(context: Context, tree: Uri, documentId: String): Item {
        val uri = DocumentsContract.buildDocumentUriUsingTree(tree, documentId)
        context.contentResolver.query(uri, columns, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) return Item(cursor.getString(0) ?: error("Documento inválido."),
                cursor.getString(1) ?: "Pasta autorizada", cursor.getString(2) ?: "", 
                if (cursor.isNull(3)) -1L else cursor.getLong(3),
                if (cursor.isNull(4)) 0L else cursor.getLong(4),
                if (cursor.isNull(5)) 0 else cursor.getInt(5))
        }
        error("Arquivo ou pasta não encontrado.")
    }

    /** Resolve cada componente por enumeração da pasta anterior, sem aceitar IDs enviados pela rede. */
    private fun resolve(context: Context, tree: Uri, path: List<String>): Item {
        require(RemoteFilePolicy.validPath(path)) { "Caminho inválido." }
        var current = metadata(context, tree, DocumentsContract.getTreeDocumentId(tree))
        for (name in path) {
            check(current.folder) { "Caminho não corresponde a uma pasta." }
            // Não aceitar pasta com mais itens que o limite, inclusive durante resolução.
            val (children, truncated) = children(context, tree, current.id, RemoteFilePolicy.MAX_ENTRIES)
            check(!truncated) { "Pasta tem mais de 150 itens; não é seguro identificar o arquivo." }
            val matches = children.filter { it.name == name }
            check(matches.size == 1) { if (matches.isEmpty()) "Arquivo não disponível nessa pasta." else "Existem nomes repetidos nesta pasta; cópia bloqueada para evitar o arquivo errado." }
            current = matches.single()
        }
        return current
    }

    private fun path(request: JSONObject): List<String> {
        val array = request.optJSONArray("path") ?: error("Caminho ausente.")
        require(array.length() <= RemoteFilePolicy.MAX_FILE_PATH_PARTS) { "Caminho muito profundo." }
        return (0 until array.length()).map { array.getString(it) }.also {
            require(RemoteFilePolicy.validPath(it)) { "Nome de pasta não permitido." }
        }
    }

    fun handle(context: Context, request: JSONObject): JSONObject {
        val response = JSONObject().put("v", 1).put("op", request.optString("op"))
            .put("appVersion", br.dev.monitorremoto.transport.AppVersion.NAME)
        if (!available(context)) return response.put("ok", false).put("message",
            "No monitorado, abra Configurações > Arquivos e recuperação e escolha uma pasta para compartilhar.")
        return runCatching {
            val tree = tree(context) ?: error("Nenhuma pasta selecionada.")
            val parts = path(request)
            val operation = request.optString("op")
            if (operation in setOf("dataManage", "dataUploadStart", "dataUploadChunk", "dataUploadFinish", "dataUploadAbort", "dataUploadStatus")) {
                check(writable(context)) { "No monitorado, autorize Gerenciamento remoto (escrita) nesta pasta." }
                return@runCatching manage(context, tree, request, parts, response)
            }
            val node = resolve(context, tree, parts)
            if (operation == "dataList") {
                check(node.folder && parts.size <= RemoteFilePolicy.MAX_DEPTH) { "Limite de 12 níveis de pasta." }
                val (files, truncated) = children(context, tree, node.id, RemoteFilePolicy.MAX_ENTRIES)
                val entries = JSONArray()
                files.sortedWith(compareBy<Item> { !it.folder }.thenBy { it.name.lowercase() })
                    .forEach { entries.put(it.json()) }
                response.put("ok", true).put("entries", entries).put("truncated", truncated)
                    .put("writable", writable(context))
            } else {
                check(!node.folder) { "Não é possível copiar uma pasta inteira nesta versão." }
                check(node.size in 1..RemoteFilePolicy.MAX_FILE_BYTES) {
                    "Arquivo vazio, tamanho desconhecido ou acima do limite de 2 GiB."
                }
                val offset = request.optLong("offset", -1L)
                val isHash = request.optString("op") == "dataHash"
                val isMulti = operation == "dataChunks"
                val isFast = operation == "dataRead"
                require(isHash || (if (isFast) RemoteFilePolicy.validFastChunk(node.size, offset)
                    else RemoteFilePolicy.validChunk(node.size, offset))) { "Bloco inválido." }
                // Metadados iniciais informados pelo controlador, evita cópias inconsistentes por alteração da origem.
                require(request.optLong("size", -1L) == node.size &&
                    request.optLong("modified", -1L) == node.modified) { "Arquivo mudou desde a listagem." }
                val document = DocumentsContract.buildDocumentUriUsingTree(tree, node.id)
                if (isHash) {
                    val digest = MessageDigest.getInstance("SHA-256")
                    var received = 0L
                    context.contentResolver.openInputStream(document)?.use { input ->
                        val buffer = ByteArray(16 * 1024)
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            if (n > 0) { received += n; check(received <= node.size) { "Origem mudou." }; digest.update(buffer, 0, n) }
                        }
                    } ?: error("Android não permitiu conferir a integridade da origem.")
                    check(received == node.size) { "Tamanho da origem mudou." }
                    response.put("ok", true).put("size", node.size).put("modified", node.modified)
                        .put("sha256", digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) })
                } else if (isMulti) {
                    val blocks = JSONArray()
                    for (index in 0..1) {
                        val at = offset + index * RemoteFilePolicy.CHUNK_BYTES.toLong()
                        val count = RemoteFilePolicy.nextChunkLength(node.size, at)
                        if (count == 0) break
                        val bytes = readSlice(context, document, at, count)
                        check(bytes.size == count) { "Bloco de origem incompleto." }
                        blocks.put(JSONObject().put("offset", at)
                            .put("bytes", Base64.encodeToString(bytes, Base64.NO_WRAP))
                            .put("sha256", MessageDigest.getInstance("SHA-256").digest(bytes)
                                .joinToString("") { "%02x".format(it.toInt() and 255) }))
                    }
                    response.put("ok", true).put("offset", offset)
                        .put("size", node.size).put("modified", node.modified).put("chunks", blocks)
                } else {
                    val count = if (isFast) RemoteFilePolicy.nextFastLength(node.size, offset)
                        else RemoteFilePolicy.nextChunkLength(node.size, offset)
                    val bytes = readSlice(context, document, offset, count)
                    check(bytes.size == count) { "Leitura incompleta do arquivo." }
                    response.put("ok", true).put("offset", offset).put("size", node.size)
                        .put("modified", node.modified)
                        .put("bytes", Base64.encodeToString(bytes, Base64.NO_WRAP))
                        .put("sha256", MessageDigest.getInstance("SHA-256").digest(bytes)
                            .joinToString("") { "%02x".format(it.toInt() and 255) })
                }
            }
            response
        }.getOrElse { error -> response.put("ok", false).put("message",
            (error.message ?: "Falha de leitura no Android.").take(170)) }
    }

    /** Mutação SAF restrita a caminhos relativos e somente com permissão explícita de escrita. */
    private fun manage(context: Context, tree: Uri, request: JSONObject, parts: List<String>,
        response: JSONObject): JSONObject {
        val operation = request.optString("op")
        val owner = request.optString("clientId")
        if (operation.startsWith("dataUpload")) {
            val result = when (operation) {
                "dataUploadStart" -> {
                    val destinationFolder = resolve(context, tree, parts)
                    check(destinationFolder.folder) { "Selecione uma pasta de destino." }
                    check(destinationFolder.flags and DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE != 0) {
                        "O provedor não permite criar arquivos nessa pasta." }
                    val name = request.optString("name")
                    check(RemoteFilePolicy.validSegment(name) && parts.size <= RemoteFilePolicy.MAX_DEPTH)
                    check(!exists(context, tree, resolve(context, tree, parts), name)) { "Arquivo com esse nome já existe." }
                    RemoteUploadStore.start(context, owner, parts, name,
                        request.optString("mime"), request.optLong("size", -1L))
                }
                "dataUploadStatus" -> RemoteUploadStore.status(owner, request.optString("session"))
                "dataUploadChunk" -> RemoteUploadStore.append(owner, request.optString("session"),
                    request.optLong("offset", -1L), request.getString("bytes"))
                "dataUploadFinish" -> RemoteUploadStore.finish(owner, request.optString("session"),
                    request.optString("sha256")) { parent, name, mime, staged ->
                    val target = resolve(context, tree, parent)
                    check(target.folder && target.flags and DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE != 0 &&
                        !exists(context, tree, target, name)) { "Destino alterado, sem escrita ou nome existente." }
                    val parentUri = uri(tree, target.id)
                    val created = DocumentsContract.createDocument(context.contentResolver, parentUri, mime, name)
                        ?: error("Provedor recusou criar o arquivo.")
                    try {
                        context.contentResolver.openOutputStream(created, "wt")?.use { output ->
                            staged.inputStream().use { input -> input.copyTo(output, 64 * 1024) }
                        } ?: error("Provedor recusou gravar o arquivo.")
                    } catch (e: Exception) {
                        runCatching { DocumentsContract.deleteDocument(context.contentResolver, created) }
                        throw e
                    }
                }
                "dataUploadAbort" -> { RemoteUploadStore.abort(owner, request.optString("session")); JSONObject() }
                else -> error("Operação de envio inválida.")
            }
            return response.put("ok", true).put("result", result)
        }
        val action = request.optString("action")
        val destJson = request.optJSONArray("destination") ?: JSONArray()
        val destination = (0 until destJson.length()).map { destJson.getString(it) }
        val newName = request.optString("name")
        require(RemoteManagePolicy.allowed(action, parts, destination, newName)) { "Ação ou caminho inválido." }
        val resolver = context.contentResolver
        val node = resolve(context, tree, parts)
        val source = uri(tree, node.id)
        val updated = when (action) {
            "mkdir" -> {
                check(node.folder && node.flags and DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE != 0 &&
                    !exists(context, tree, node, newName)) { "Pasta sem permissão de criação ou nome existente." }
                DocumentsContract.createDocument(resolver, source, DIRECTORY, newName)
                    ?: error("Não foi possível criar pasta.")
            }
            "rename" -> {
                check(node.flags and DocumentsContract.Document.FLAG_SUPPORTS_RENAME != 0) {
                    "Provedor não suporta renomear este documento." }
                check(!exists(context, tree, resolve(context, tree, parts.dropLast(1)), newName)) { "Nome já existe." }
                DocumentsContract.renameDocument(resolver, source, newName)
                    ?: error("Provedor não permite renomear.")
            }
            "delete" -> {
                check(node.flags and DocumentsContract.Document.FLAG_SUPPORTS_DELETE != 0) {
                    "Provedor não permite excluir este documento." }
                check(DocumentsContract.deleteDocument(resolver, source)) { "Provedor recusou a exclusão." }
                source
            }
            "trash" -> {
                check(node.flags and DocumentsContract.Document.FLAG_SUPPORTS_MOVE != 0) {
                    "Provedor não suporta mover para lixeira." }
                check(parts.first() != ".MonitorRemoto-Lixeira") { "Arquivo já está na lixeira." }
                val root = resolve(context, tree, emptyList())
                val (existing, truncated) = children(context, tree, root.id, RemoteFilePolicy.MAX_ENTRIES)
                check(!truncated) { "Pasta raiz com muitos itens." }
                val matches = existing.filter { it.name == ".MonitorRemoto-Lixeira" }
                check(matches.size <= 1) { "Lixeira duplicada." }
                val destinationFolder = matches.singleOrNull()?.let { check(it.folder); uri(tree, it.id) }
                    ?: (DocumentsContract.createDocument(resolver, uri(tree, root.id), DIRECTORY,
                        ".MonitorRemoto-Lixeira") ?: error("Não foi possível criar lixeira."))
                val id = DocumentsContract.getDocumentId(destinationFolder)
                check(!exists(context, tree, metadata(context, tree, id), node.name)) {
                    "Já existe arquivo com este nome na lixeira." }
                val previous = uri(tree, resolve(context, tree, parts.dropLast(1)).id)
                DocumentsContract.moveDocument(resolver, source, previous, destinationFolder)
                    ?: error("Provedor não permite mover para a lixeira. Arquivo original preservado.")
            }
            "move", "copy" -> {
                val target = resolve(context, tree, destination)
                check(target.folder && !exists(context, tree, target, node.name)) { "Destino inválido ou nome já existente." }
                if (action == "move") check(node.flags and DocumentsContract.Document.FLAG_SUPPORTS_MOVE != 0) {
                    "Provedor não permite mover este documento." }
                if (action == "copy" && node.folder)
                    check(node.flags and DocumentsContract.Document.FLAG_SUPPORTS_COPY != 0) {
                        "Provedor não permite copiar pastas." }
                val folder = uri(tree, target.id)
                val oldParent = uri(tree, resolve(context, tree, parts.dropLast(1)).id)
                if (action == "move") {
                    DocumentsContract.moveDocument(resolver, source, oldParent, folder)
                        ?: error("Provedor não permite mover este documento.")
                } else {
                    val copied = runCatching { DocumentsContract.copyDocument(resolver, source, folder) }.getOrNull()
                    copied ?: if (!node.folder) copyFallback(context, source, folder, node)
                        else error("Provedor não permite copiar pastas diretamente.")
                }
            }
            else -> error("Operação não reconhecida.")
        }
        return response.put("ok", true).put("name", newName.ifBlank { node.name })
            .put("documentChanged", updated != null)
    }

    private fun uri(tree: Uri, id: String): Uri = DocumentsContract.buildDocumentUriUsingTree(tree, id)

    private fun exists(context: Context, tree: Uri, parent: Item, name: String): Boolean {
        val (all, truncated) = children(context, tree, parent.id, RemoteFilePolicy.MAX_ENTRIES)
        check(!truncated) { "Pasta grande demais para evitar sobrescritas." }
        return all.any { it.name.equals(name, ignoreCase = true) }
    }

    private fun copyFallback(context: Context, source: Uri, parent: Uri, item: Item): Uri {
        val resolver = context.contentResolver
        val created = DocumentsContract.createDocument(resolver, parent, item.mime, item.name)
            ?: error("Não foi possível criar cópia.")
        try {
            val input = resolver.openInputStream(source) ?: error("Não foi possível ler arquivo.")
            val output = resolver.openOutputStream(created, "wt") ?: error("Não foi possível gravar arquivo.")
            val total = input.use { src -> output.use { dst -> src.copyTo(dst, 64 * 1024) } }
            if (item.size >= 0L) check(total == item.size) { "Fonte alterada durante a cópia." }
        } catch (e: Exception) {
            runCatching { DocumentsContract.deleteDocument(resolver, created) }
            throw e
        }
        return created
    }

    private fun readCount(stream: java.io.InputStream, count: Int): ByteArray {
        val buffer = ByteArray(count)
        var read = 0
        while (read < count) {
            val n = stream.read(buffer, read, count - read)
            if (n < 0) break
            if (n == 0) continue
            read += n
        }
        return if (read == count) buffer else buffer.copyOf(read)
    }

    private fun readSlice(context: Context, document: Uri, offset: Long, count: Int): ByteArray {
        // Preferir FD com deslocamento direto: evita reler a origem inteira a cada bloco.
        val resolver = context.contentResolver
        resolver.openFileDescriptor(document, "r")?.use { fd ->
            FileInputStream(fd.fileDescriptor).use { input ->
                try {
                    input.channel.position(offset)
                    return readCount(input, count)
                } catch (_: java.io.IOException) { /* Provider pode devolver pipe sem seek. */ }
            }
        }
        resolver.openInputStream(document)?.use { input ->
            var skipped = 0L
            while (skipped < offset) {
                val n = input.skip(offset - skipped)
                if (n <= 0) {
                    if (input.read() == -1) error("Fim de arquivo prematuro.")
                    skipped++
                } else skipped += n
            }
            return readCount(input, count)
        }
        error("Android não autorizou abrir o arquivo.")
    }
}
