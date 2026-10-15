package br.dev.monitorremoto.core

import org.junit.Assert.*
import org.junit.Test

class CameraNv21RotationTest {
    private val nv21 = byteArrayOf(
        0, 1, 2, 3, 4, 5, 6, 7, // Y: 4x2
        10, 11, 20, 21           // VU: 2x1
    )

    @Test fun rotacaoZeroConservaPixels() {
        val frame = CameraNv21Rotation.rotate(nv21, 4, 2, 0)
        assertEquals(4, frame.width)
        assertEquals(2, frame.height)
        assertArrayEquals(nv21, frame.bytes)
    }

    @Test fun rotacao90AlteraDimensoesENaoTrocaUV() {
        val frame = CameraNv21Rotation.rotate(nv21, 4, 2, 90)
        assertEquals(2, frame.width)
        assertEquals(4, frame.height)
        assertArrayEquals(byteArrayOf(4,0,5,1,6,2,7,3,10,11,20,21), frame.bytes)
    }

    @Test fun rotacao180PreservaDimensoesERevertePares() {
        val frame = CameraNv21Rotation.rotate(nv21, 4, 2, 180)
        assertEquals(4, frame.width)
        assertEquals(2, frame.height)
        assertArrayEquals(byteArrayOf(7,6,5,4,3,2,1,0,20,21,10,11), frame.bytes)
    }

    @Test fun rotacao270TambemPreservaVU() {
        val frame = CameraNv21Rotation.rotate(nv21, 4, 2, 270)
        assertEquals(2, frame.width)
        assertEquals(4, frame.height)
        assertArrayEquals(byteArrayOf(3,7,2,6,1,5,0,4,20,21,10,11), frame.bytes)
    }

    @Test fun sensorFrontalETraseiroUsamSinaisDistintos() {
        assertEquals(90, CameraNv21Rotation.degrees(90, 0, false))
        assertEquals(0, CameraNv21Rotation.degrees(90, 1, false))
        assertEquals(270, CameraNv21Rotation.degrees(90, 2, false))
        assertEquals(270, CameraNv21Rotation.degrees(270, 0, true))
        assertEquals(0, CameraNv21Rotation.degrees(270, 1, true))
        assertEquals(90, CameraNv21Rotation.degrees(270, 2, true))
    }

    @Test fun bloqueiaDimensoesEOrientacaoInesperadas() {
        assertThrows(IllegalArgumentException::class.java) { CameraNv21Rotation.rotate(nv21, 3, 2, 0) }
        assertThrows(IllegalArgumentException::class.java) { CameraNv21Rotation.rotate(nv21, 4, 2, 45) }
        assertThrows(IllegalArgumentException::class.java) { CameraNv21Rotation.rotate(byteArrayOf(1), 4, 2, 90) }
        assertThrows(IllegalArgumentException::class.java) { CameraNv21Rotation.degrees(45, 0, false) }
        assertThrows(IllegalArgumentException::class.java) { CameraNv21Rotation.degrees(90, 4, false) }
    }
}
