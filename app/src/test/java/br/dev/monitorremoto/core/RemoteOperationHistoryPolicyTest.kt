package br.dev.monitorremoto.core

import org.junit.Assert.*
import org.junit.Test

class RemoteOperationHistoryPolicyTest {
    @Test fun validEntries() {
        assertTrue(RemoteOperationHistoryPolicy.valid("download", "concluido", 1, 1, 24))
        assertTrue(RemoteOperationHistoryPolicy.valid("delete", "parcial", 2, 3, 0))
        assertFalse(RemoteOperationHistoryPolicy.valid("sms", "concluido", 1, 1, 0))
        assertFalse(RemoteOperationHistoryPolicy.valid("download", "desconhecido", 1, 1, 0))
        assertFalse(RemoteOperationHistoryPolicy.valid("download", "falhou", -1, 3, 0))
        assertFalse(RemoteOperationHistoryPolicy.valid("download", "falhou", 4, 3, 0))
        assertFalse(RemoteOperationHistoryPolicy.valid("upload", "falhou", 0, 1, -1))
        assertFalse(RemoteOperationHistoryPolicy.valid("upload", "falhou", 0, 1, 2L * 1024L * 1024L * 1024L + 1))
    }
    @Test fun stateIsNotInvented() {
        assertEquals("concluido", RemoteOperationHistoryPolicy.outcome(true, false, 3))
        assertEquals("cancelado", RemoteOperationHistoryPolicy.outcome(false, true, 0))
        assertEquals("parcial", RemoteOperationHistoryPolicy.outcome(false, true, 1))
        assertEquals("falhou", RemoteOperationHistoryPolicy.outcome(false, false, 0))
        assertEquals("parcial", RemoteOperationHistoryPolicy.outcome(false, false, 1))
    }
}
