package br.dev.monitorremoto.transport

import org.junit.Assert.*
import org.junit.Test

class AppVersionTest {
    @Test fun exactlyMatchingVersionIsAccepted() {
        assertTrue(AppVersion.compatible(AppVersion.NAME))
        assertFalse(AppVersion.compatible("0.1.0-alpha.19"))
        assertFalse(AppVersion.compatible("0.1.0-alpha.20"))
        assertFalse(AppVersion.compatible("0.1.0-alpha.21"))
        assertFalse(AppVersion.compatible("0.1.0-alpha.22"))
        assertFalse(AppVersion.compatible("0.1.0-alpha.23"))
        assertFalse(AppVersion.compatible(null))
        assertTrue(AppVersion.incompatibility("0.1.0-alpha.23").contains("Versões incompatíveis"))
    }
}
