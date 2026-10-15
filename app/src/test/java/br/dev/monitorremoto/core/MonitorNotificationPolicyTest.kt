package br.dev.monitorremoto.core

import org.junit.Assert.assertEquals
import org.junit.Test

class MonitorNotificationPolicyTest {
    private fun state(active: Boolean = true, ready: Boolean = true, peers: Int = 0,
                      gps: Boolean = false, screen: Boolean = false,
                      camera: Boolean = false, alerts: Boolean = false) =
        MonitorNotificationPolicy.State(active, ready, peers, gps, screen, camera, alerts)

    @Test fun noAccessMeansWaitingNotConnected() {
        assertEquals("Aguardando controlador", MonitorNotificationPolicy.headline(state()))
    }
    @Test fun failedServerMeansReconnecting() {
        assertEquals("Serviço ativo · reconectando", MonitorNotificationPolicy.headline(state(ready = false)))
    }
    @Test fun serviceStoppedIsExplicit() {
        assertEquals("Monitoramento parado", MonitorNotificationPolicy.headline(state(active = false, peers = 2)))
    }
    @Test fun peersAreOnlyRecentAccesses() {
        assertEquals("2 acessos recentes", MonitorNotificationPolicy.headline(state(peers = 2)))
    }
    @Test fun singularAccess() {
        assertEquals("1 acesso recente", MonitorNotificationPolicy.headline(state(peers = 1)))
    }
    @Test fun modulesNeverClaimInactiveCamera() {
        assertEquals("Localização · Alertas", MonitorNotificationPolicy.modules(state(gps = true, alerts = true)))
    }
    @Test fun emptyModulesAreClear() {
        assertEquals("Nenhum módulo adicional ativo", MonitorNotificationPolicy.modules(state()))
    }
}
