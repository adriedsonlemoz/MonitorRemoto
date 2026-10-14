package br.dev.monitorremoto.core

import org.junit.Assert.*
import org.junit.Test

class MessagePolicyTest {
    @Test fun smsAddressValidation() {
        assertTrue(MessagePolicy.validReplyAddress("+553199998888"))
        assertTrue(MessagePolicy.validReplyAddress("31999998888"))
        assertFalse(MessagePolicy.validReplyAddress("12345"))
        assertFalse(MessagePolicy.validReplyAddress("BANCO"))
        assertFalse(MessagePolicy.validReplyAddress("+55 31 99999"))
        assertFalse(MessagePolicy.validReplyAddress("+551199999999999999"))
    }

    @Test fun obviousAuthenticationSmsIsExcluded() {
        assertTrue(MessagePolicy.looksLikeAuthenticationSms("123456"))
        assertTrue(MessagePolicy.looksLikeAuthenticationSms("Código: 428173"))
        assertTrue(MessagePolicy.looksLikeAuthenticationSms("Senha de acesso 004521"))
        assertFalse(MessagePolicy.looksLikeAuthenticationSms("Chego às 14h"))
    }
}
