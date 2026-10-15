package br.dev.monitorremoto.core

import org.junit.Assert.*
import org.junit.Test

class RemoteBatchPolicyTest {
    @Test fun acceptsValidBatchAndBlocksUnsupportedSizes() {
        assertTrue(RemoteBatchPolicy.eligible(listOf(1L, 1024L)))
        assertTrue(RemoteBatchPolicy.eligible(List(4) { 32L * 1024L * 1024L }))
        assertFalse(RemoteBatchPolicy.eligible(emptyList()))
        assertFalse(RemoteBatchPolicy.eligible(List(21) { 1L }))
        assertFalse(RemoteBatchPolicy.eligible(listOf(0L)))
        assertFalse(RemoteBatchPolicy.eligible(listOf(-1L)))
        assertTrue(RemoteBatchPolicy.eligible(listOf(32L * 1024L * 1024L + 1)))
        assertTrue(RemoteBatchPolicy.eligible(List(5) { 32L * 1024L * 1024L }))
        assertFalse(RemoteBatchPolicy.eligible(listOf(RemoteBatchPolicy.MAX_TOTAL_BYTES + 1)))
    }

    @Test fun nonDestructiveSuffixIsCaseInsensitive() {
        val used = setOf("FOTO.JPG", "foto (2).jpg")
        assertEquals("foto (3).jpg", RemoteBatchPolicy.availableName("foto.jpg", used))
        assertEquals("texto.txt", RemoteBatchPolicy.availableName("texto.txt", used))
        assertEquals("arquivo (2)", RemoteBatchPolicy.availableName("arquivo", setOf("arquivo")))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsTraversalName() { RemoteBatchPolicy.availableName("../outra-pasta", emptySet()) }
}
