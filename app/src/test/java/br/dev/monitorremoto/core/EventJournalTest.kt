package br.dev.monitorremoto.core

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class EventJournalTest {
    @After fun reset() = EventJournal.clear()

    @Test fun stoppingMonitoringClearsAllEvents() {
        EventJournal.add("notificacao", "com.exemplo.chat", "Teste", "texto normal")
        assertEquals(1, EventJournal.latest(setOf("com.exemplo.chat")).size)
        EventJournal.clear()
        assertEquals(0, EventJournal.latest(setOf("com.exemplo.chat")).size)
    }

    @Test fun revokedAppIsPrunedFromVolatileHistory() {
        EventJournal.add("notificacao", "com.exemplo.chat", "Chat", "texto permitido")
        EventJournal.add("notificacao", "com.exemplo.sms", "SMS", "texto permitido")
        EventJournal.retainAllowed(setOf("com.exemplo.chat"))
        val visible = EventJournal.latest(setOf("com.exemplo.chat", "com.exemplo.sms"))
        assertEquals(1, visible.size)
        assertEquals("com.exemplo.chat", visible[0].app)
    }

    @Test fun transmissionAppliesCurrentAllowlistEvenIfEventAlreadyQueued() {
        EventJournal.add("notificacao", "com.exemplo.chat", "Chat", "texto")
        assertEquals(0, EventJournal.latest(emptySet()).size)
        assertEquals(1, EventJournal.latest(setOf("com.exemplo.chat")).size)
    }
}
