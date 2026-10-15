package br.dev.monitorremoto.core

import org.junit.Assert.*
import org.junit.Test

class CameraRemoteSwitchPolicyTest {
    private fun allowed(consent: Boolean = true, current: String? = "Traseira", target: String = "Frontal",
        session: Long = 9L, expected: Long = 9L, pending: Boolean = false,
        viewer: Boolean = true, last: Long = 0L, now: Long = 10_000L) =
        CameraRemoteSwitchPolicy.accept(consent, current, target, session, expected, pending, viewer, last, now)

    @Test fun recusaSemConsentimentoExplicito() { assertFalse(allowed(consent = false)) }
    @Test fun recusaSemCameraAtiva() { assertFalse(allowed(current = null)) }
    @Test fun recusaMesmoAlvo() { assertFalse(allowed(target = "Traseira")) }
    @Test fun recusaComandoArbitrario() { assertFalse(allowed(target = "INICIAR")); assertFalse(allowed(target = "")) }
    @Test fun recusaSessaoObsoleta() { assertFalse(allowed(expected = 8L)); assertFalse(allowed(session = 0L, expected = 0L)) }
    @Test fun recusaComRequisicaoPendente() { assertFalse(allowed(pending = true)) }
    @Test fun recusaSemVisualizadorRecente() { assertFalse(allowed(viewer = false)) }
    @Test fun limitaFrequencia() {
        assertFalse(allowed(last = 9_999L)); assertFalse(allowed(last = 8_001L))
        assertTrue(allowed(last = 8_000L)); assertFalse(allowed(last = 11_000L))
    }
    @Test fun permiteTrocaReversaValida() {
        assertTrue(allowed()); assertTrue(allowed(current = "Frontal", target = "Traseira"))
    }
}
