package br.dev.monitorremoto.core

/** Política de leitura SAF: somente caminhos relativos à pasta escolhida localmente. */
object RemoteFilePolicy {
    const val MAX_DEPTH = 12
    const val MAX_FILE_PATH_PARTS = MAX_DEPTH + 1
    const val MAX_ENTRIES = 150
    const val MAX_FILE_BYTES = 2L * 1024L * 1024L * 1024L
    const val CHUNK_BYTES = 48 * 1024
    const val FAST_CHUNK_BYTES = 192 * 1024
    const val UPLOAD_CHUNK_BYTES = 96 * 1024

    fun validSegment(value: String): Boolean = value.isNotBlank() && value.length <= 200 &&
        value != "." && value != ".." && '/' !in value && '\\' !in value &&
        value.none { it.code < 32 || it.code == 127 }

    fun validPath(parts: List<String>): Boolean =
        parts.size <= MAX_FILE_PATH_PARTS && parts.all(::validSegment)

    fun validChunk(size: Long, offset: Long): Boolean =
        size in 1..MAX_FILE_BYTES && offset >= 0 && offset < size && offset % CHUNK_BYTES == 0L

    fun validFastChunk(size: Long, offset: Long): Boolean =
        size in 0..MAX_FILE_BYTES && offset in 0 until size && offset % FAST_CHUNK_BYTES == 0L

    fun nextFastLength(size: Long, offset: Long): Int =
        if (!validFastChunk(size, offset)) 0 else minOf(FAST_CHUNK_BYTES.toLong(), size - offset).toInt()

    fun validUploadSize(size: Long) = size in 0..MAX_FILE_BYTES

    fun nextChunkLength(size: Long, offset: Long): Int =
        if (!validChunk(size, offset)) 0 else minOf(CHUNK_BYTES.toLong(), size - offset).toInt()
}
