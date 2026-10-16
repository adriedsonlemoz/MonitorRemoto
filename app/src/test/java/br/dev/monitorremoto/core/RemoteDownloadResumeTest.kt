package br.dev.monitorremoto.core

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class RemoteDownloadResumeTest {
    @Test fun identityBindsHostPathAndMetadata() {
        val a = RemoteDownloadResume.id("192.168.1.2", listOf("Camera", "a.mp4"), 1000, 123)
        assertEquals(a, RemoteDownloadResume.id("192.168.1.2", listOf("Camera", "a.mp4"), 1000, 123))
        assertNotEquals(a, RemoteDownloadResume.id("192.168.1.3", listOf("Camera", "a.mp4"), 1000, 123))
        assertNotEquals(a, RemoteDownloadResume.id("192.168.1.2", listOf("Camera", "b.mp4"), 1000, 123))
        assertNotEquals(a, RemoteDownloadResume.id("192.168.1.2", listOf("Camera", "a.mp4"), 1000, 124))
    }
    @Test fun onlyCompleteBlocksSurviveRestart() {
        val root=Files.createTempDirectory("monitor-checkpoint").toFile()
        try {
            val dir=RemoteDownloadResume.folder(root)
            val id=RemoteDownloadResume.id("host", listOf("videos", "x.mp4"), 2048, 42)
            val first=RemoteDownloadResume.prepare(dir,id,"host","x.mp4",2048,42,256)
            assertEquals(0,first.resumeAt)
            first.file.outputStream().use { it.write(ByteArray(773) { 3 }) }
            val next=RemoteDownloadResume.prepare(dir,id,"host","x.mp4",2048,42,256)
            assertEquals(768,next.resumeAt)
            assertEquals(768,next.file.length())
            assertEquals(1,RemoteDownloadResume.list(dir,"host").size)
            assertTrue(RemoteDownloadResume.list(dir,"other").isEmpty())
            RemoteDownloadResume.discard(dir,id)
            assertTrue(RemoteDownloadResume.list(dir,"host").isEmpty())
        } finally { root.deleteRecursively() }
    }
    @Test fun changedOriginIsNeverReused() {
        val root=Files.createTempDirectory("monitor-checkpoint").toFile()
        try {
            val dir=RemoteDownloadResume.folder(root)
            val id=RemoteDownloadResume.id("host",listOf("x"),1024,99)
            RemoteDownloadResume.prepare(dir,id,"host","x",1024,99,256).file.writeBytes(ByteArray(512))
            val wrong=RemoteDownloadResume.prepare(dir,id,"host","x",1024,100,256)
            assertEquals(0,wrong.resumeAt)
            assertEquals(0,wrong.file.length())
        } finally { root.deleteRecursively() }
    }
    @Test fun cleanupOnlyPrivateRecords() {
        val root=Files.createTempDirectory("monitor-checkpoint").toFile()
        try {
            val dir=RemoteDownloadResume.folder(root)
            val id=RemoteDownloadResume.id("host",listOf("x"),1000,99)
            RemoteDownloadResume.prepare(dir,id,"host","x",1000,99,256)
            File(dir,"$id.meta").setLastModified(1L)
            val unrelated=File(dir,"other-user.txt").also { it.writeText("keep") }
            RemoteDownloadResume.cleanup(dir,10L*24*60*60*1000)
            assertTrue(unrelated.exists())
            assertFalse(File(dir,"$id.part").exists())
        } finally { root.deleteRecursively() }
    }
    @Test fun alignmentNeverExceedsSource() {
        assertEquals(0,RemoteDownloadResume.alignedLength(255,1000,256))
        assertEquals(768,RemoteDownloadResume.alignedLength(900,1000,256))
        assertEquals(768,RemoteDownloadResume.alignedLength(2000,1000,256))
        assertEquals(0,RemoteDownloadResume.alignedLength(0,1000,256))
    }
}
