package br.dev.monitorremoto.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SensitiveContentTest {
    @Test fun blocksAuthenticationAndFinancialText() {
        assertTrue(SensitiveContent.isSensitive("Código de verificação", "123456"))
        assertTrue(SensitiveContent.isSensitive("Notificação", "PIX realizado"))
        assertFalse(SensitiveContent.isSensitive("Amigo", "Bom dia, tudo bem?"))
    }

    @Test fun blocksKnownFinancialAppsWithoutBlockingChatAndSmsNotifications() {
        assertTrue(SensitiveContent.isBlockedFinancialApp("com.nu.production", "Nubank"))
        assertTrue(SensitiveContent.isBlockedFinancialApp("x.y", "Banco Exemplo"))
        assertFalse(SensitiveContent.isBlockedFinancialApp("com.whatsapp", "WhatsApp"))
        assertFalse(SensitiveContent.isBlockedFinancialApp("com.google.android.apps.messaging", "Mensagens"))
    }
}
