package br.dev.monitorremoto.ui

import java.io.EOFException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.*
import org.junit.Test

class ConnectionIssueTest {
    @Test fun exactVersionIsExtractedOnlyWhenConfirmed() {
        val issue = ConnectionIssue.from(IllegalStateException(
            "Versões incompatíveis. Ambos os celulares precisam executar 0.1.0-alpha.29. Versão recebida: 0.1.0-alpha.27."
        ))
        assertEquals(ConnectionIssue.Code.VERSION, issue.code)
        assertEquals("0.1.0-alpha.27", issue.remoteVersion)
    }

    @Test fun eofDoesNotInventARemoteVersion() {
        val issue = ConnectionIssue.from(EOFException("Conexão interrompida"))
        assertEquals(ConnectionIssue.Code.CLOSED, issue.code)
        assertNull(issue.remoteVersion)
    }

    @Test fun commonErrorsHaveActionableCategories() {
        assertEquals(ConnectionIssue.Code.TIMEOUT, ConnectionIssue.from(SocketTimeoutException()).code)
        assertEquals(ConnectionIssue.Code.REFUSED, ConnectionIssue.from(ConnectException()).code)
        assertEquals(ConnectionIssue.Code.HOST, ConnectionIssue.from(UnknownHostException()).code)
    }
}
