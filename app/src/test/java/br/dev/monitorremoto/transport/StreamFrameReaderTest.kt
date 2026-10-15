package br.dev.monitorremoto.transport

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.EOFException

class StreamFrameReaderTest {
    @Test fun severalFramesInsideOneRead() {
        val data = "one\ntwo\nthree\n".toByteArray()
        val reader = StreamFrameReader(ByteArrayInputStream(data))
        assertEquals("one", String(reader.receive(100)))
        assertEquals("two", String(reader.receive(100)))
        assertEquals("three", String(reader.receive(100)))
    }
    @Test fun largeEncryptedFramesPreserveBoundary() {
        val first = ByteArray(340_000) { 'X'.code.toByte() }
        val all = first + byteArrayOf(10) + "end\n".toByteArray()
        val reader = StreamFrameReader(ByteArrayInputStream(all))
        assertArrayEquals(first, reader.receive(520 * 1024))
        assertEquals("end", String(reader.receive(520 * 1024)))
    }
    @Test(expected = EOFException::class) fun incompleteFrameIsRejected() {
        StreamFrameReader(ByteArrayInputStream("incomplete".toByteArray())).receive(20)
    }
    @Test(expected = IllegalStateException::class) fun oversizedFrameIsRejected() {
        StreamFrameReader(ByteArrayInputStream("0123456789\n".toByteArray())).receive(10)
    }
}
