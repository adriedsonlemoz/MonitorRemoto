package br.dev.monitorremoto.core

import org.junit.Assert.*
import org.junit.Test

class RemoteFilePolicyTest {
    @Test fun pathsRestrictedToRelativeSegments() {
        assertTrue(RemoteFilePolicy.validPath(emptyList()))
        assertTrue(RemoteFilePolicy.validPath(listOf("Fotos", "2026 viagem", "áudio.mp3")))
        for (invalid in listOf("", ".", "..", "../../etc", "/DCIM", "a/b", "a\\b", "\u0000secret", "line\nnext", "a".repeat(201))) {
            assertFalse("Deveria rejeitar: $invalid", RemoteFilePolicy.validSegment(invalid))
        }
        assertFalse(RemoteFilePolicy.validPath(List(13) { "sub" }))
        assertTrue(RemoteFilePolicy.validPath(List(12) { "sub" }))
    }
    @Test fun boundedChunks() {
        assertTrue(RemoteFilePolicy.validChunk(1, 0))
        assertEquals(1, RemoteFilePolicy.nextChunkLength(1, 0))
        assertEquals(48 * 1024, RemoteFilePolicy.nextChunkLength(150_000, 0))
    }
    @Test fun rejectsMisalignedAndOversized() {
        val chunk = RemoteFilePolicy.CHUNK_BYTES.toLong()
        assertEquals(0, RemoteFilePolicy.nextChunkLength(150_000, 48_000))
        assertEquals(48 * 1024, RemoteFilePolicy.nextChunkLength(150_000, chunk))
        assertEquals(chunk, RemoteFilePolicy.nextChunkLength(150_000, 2 * chunk).toLong())
        assertEquals(150_000 - 3 * chunk, RemoteFilePolicy.nextChunkLength(150_000, 3 * chunk).toLong())
        assertFalse(RemoteFilePolicy.validChunk(0, 0))
        assertFalse(RemoteFilePolicy.validChunk(RemoteFilePolicy.MAX_FILE_BYTES + 1, 0))
        assertFalse(RemoteFilePolicy.validChunk(100, -1))
        assertFalse(RemoteFilePolicy.validChunk(100, 100))
    }
}
