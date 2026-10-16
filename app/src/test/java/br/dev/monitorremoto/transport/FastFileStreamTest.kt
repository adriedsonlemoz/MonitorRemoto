package br.dev.monitorremoto.transport

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.security.SecureRandom

class FastFileStreamTest {
    private val key = ByteArray(32).also(SecureRandom()::nextBytes)
    private val challenge = FastFileStream.challengeBytes(FastFileStream.freshChallenge())

    @Test fun largeFramesAndFinalDigestSurviveContinuousWire() {
        val payload = ByteArray(FastFileStream.MAX_DATA_BYTES) { (it * 31).toByte() }
        val sha = java.security.MessageDigest.getInstance("SHA-256").digest(payload)
        val wire = ByteArrayOutputStream()
        FastFileStream.send(wire, key, challenge, FastFileStream.DATA, 0, payload)
        FastFileStream.send(wire, key, challenge, FastFileStream.DONE, payload.size.toLong(), sha)
        val source = ByteArrayInputStream(wire.toByteArray())
        val frame = FastFileStream.receive(source, key, challenge)
        assertEquals(FastFileStream.DATA, frame.type)
        assertEquals(0L, frame.offset)
        assertArrayEquals(payload, frame.payload)
        val finish = FastFileStream.receive(source, key, challenge)
        assertEquals(FastFileStream.DONE, finish.type)
        assertEquals(payload.size.toLong(), finish.offset)
        assertArrayEquals(sha, finish.payload)
        assertTrue("Binário deve evitar a expansão JSON+Base64", wire.size() < payload.size + 200)
    }

    @Test fun freshChallengesAreDifferentAndValid() {
        val first = FastFileStream.freshChallenge()
        val second = FastFileStream.freshChallenge()
        assertTrue(first != second)
        assertEquals(16, FastFileStream.challengeBytes(first).size)
    }

    @Test(expected = Exception::class) fun tamperingRejectsRecord() {
        val output = ByteArrayOutputStream()
        FastFileStream.send(output, key, challenge, FastFileStream.DATA, 0L, byteArrayOf(1, 2, 3))
        val bytes = output.toByteArray()
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        FastFileStream.receive(ByteArrayInputStream(bytes), key, challenge)
    }

    @Test(expected = Exception::class) fun replayFromDifferentSessionRejectsRecord() {
        val output = ByteArrayOutputStream()
        FastFileStream.send(output, key, challenge, FastFileStream.DATA, 0L, byteArrayOf(4))
        val otherChallenge = FastFileStream.challengeBytes(FastFileStream.freshChallenge())
        FastFileStream.receive(ByteArrayInputStream(output.toByteArray()), key, otherChallenge)
    }

    @Test(expected = EOFException::class) fun truncatedStreamRejectsFrame() {
        val output = ByteArrayOutputStream()
        FastFileStream.send(output, key, challenge, FastFileStream.DATA, 0L, byteArrayOf(7, 8, 9))
        val bytes = output.toByteArray()
        FastFileStream.receive(ByteArrayInputStream(bytes.copyOf(bytes.size - 3)), key, challenge)
    }

    @Test(expected = IllegalArgumentException::class) fun hugeFrameRejectedBeforeAllocation() {
        FastFileStream.receive(ByteArrayInputStream(byteArrayOf(127, -1, -1, -1)), key, challenge)
    }

    @Test fun ackDoesNotConsumeFollowingBinaryFrame() {
        val output = ByteArrayOutputStream()
        output.write("{\"ok\":true}\n".toByteArray())
        FastFileStream.send(output, key, challenge, FastFileStream.DATA, 14L, byteArrayOf(1, 2))
        val source = ByteArrayInputStream(output.toByteArray())
        assertEquals("{\"ok\":true}", String(FastFileStream.readAck(source)))
        val frame = FastFileStream.receive(source, key, challenge)
        assertEquals(14L, frame.offset)
        assertArrayEquals(byteArrayOf(1, 2), frame.payload)
    }
}
