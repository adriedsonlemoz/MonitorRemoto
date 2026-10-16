package br.dev.monitorremoto.core

import org.junit.Assert.*
import org.junit.Test

class FixedDownloadPathPolicyTest {
    @Test fun rootFolder() {
        assertEquals("Download/Monitor Remoto/", FixedDownloadPathPolicy.folder(emptyList()))
        assertTrue(FixedDownloadPathPolicy.contained(FixedDownloadPathPolicy.ROOT))
    }
    @Test fun nestedFolder() {
        assertEquals("Download/Monitor Remoto/Videos/2026/",
            FixedDownloadPathPolicy.folder(listOf("Videos", "2026")))
        assertTrue(FixedDownloadPathPolicy.contained("Download/Monitor Remoto/Videos/2026/"))
    }
    @Test fun cannotEscapeFolder() {
        for (name in listOf("..", ".", "A/B", "A\\B", "\u0000secret")) {
            assertThrows(IllegalArgumentException::class.java) {
                FixedDownloadPathPolicy.folder(listOf(name))
            }
        }
        assertFalse(FixedDownloadPathPolicy.contained("Download/Monitor Remoto/../"))
        assertFalse(FixedDownloadPathPolicy.contained("Download/Outra/"))
        assertFalse(FixedDownloadPathPolicy.contained("Download/Monitor Remoto2/"))
        assertFalse(FixedDownloadPathPolicy.contained("Download/Monitor Remoto/Vi//"))
    }
    @Test fun depthLimit() {
        assertTrue(FixedDownloadPathPolicy.contained(
            FixedDownloadPathPolicy.folder(List(RemoteFilePolicy.MAX_DEPTH) { "a" })))
        assertThrows(IllegalArgumentException::class.java) {
            FixedDownloadPathPolicy.folder(List(RemoteFilePolicy.MAX_DEPTH + 1) { "a" })
        }
    }
}
