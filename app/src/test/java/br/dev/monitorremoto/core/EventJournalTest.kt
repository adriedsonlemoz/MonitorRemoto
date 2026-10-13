package br.dev.monitorremoto.core

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class EventJournalTest {
    @After fun reset() = EventJournal.clear()

    @Test fun stoppingMonitoringClearsAllEvents() {
        EventJournal.add("notificacao", "com.exemplo.chat", "Teste", "texto normal")
        assertEquals(1, EventJournal.latestJson(setOf("com.exemplo.chat")).length())
        EventJournal.clear()
        assertEquals(0, EventJournal.latestJson(setOf("com.exemplo.chat")).length())
    }

    @Test fun revokedAppIsPrunedFromVolatileHistory() {
        EventJournal.add("notificacao", "com.exemplo.chat", "Chat", "texto permitido")
        EventJournal.add("notificacao", "com.exemplo.sms", "SMS", "texto permitido")
        EventJournal.retainAllowed(setOf("com.exemplo.chat"))
        val visible = EventJournal.latestJson(setOf("com.exemplo.chat", "com.exemplo.sms"))
        assertEquals(1, visible.length())
        assertEquals("com.exemplo.chat", visible.getJSONObject(0).getString("app"))
    }

    @Test fun transmissionAppliesCurrentAllowlistEvenIfEventAlreadyQueued() {
        EventJournal.add("notificacao", "com.exemplo.chat", "Chat", "texto")
        assertEquals(0, EventJournal.latestJson(emptySet()).length())
        assertEquals(1, EventJournal.latestJson(setOf("com.exemplo.chat")).length())
    }
}
