package br.dev.monitorremoto.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsBadgeKindTest {
    @Test fun textosSaoFixosEIndependentesDasDescricoes() {
        assertEquals("ATIVADO", SettingsBadgeKind.ACTIVE.label)
        assertEquals("DESATIVADO", SettingsBadgeKind.INACTIVE.label)
        assertEquals("ABRIR", SettingsBadgeKind.ACTION.label)
        assertEquals("REVOGAR", SettingsBadgeKind.WARNING.label)
        assertEquals("REVOGAR", SettingsBadgeKind.DANGER.label)
        assertEquals("VOLTAR", SettingsBadgeKind.NEUTRAL.label)
    }

    @Test fun somenteBooleanoControlaEstado() {
        assertEquals(SettingsBadgeKind.ACTIVE, SettingsBadgeKind.forEnabled(true))
        assertEquals(SettingsBadgeKind.INACTIVE, SettingsBadgeKind.forEnabled(false))
    }
}
