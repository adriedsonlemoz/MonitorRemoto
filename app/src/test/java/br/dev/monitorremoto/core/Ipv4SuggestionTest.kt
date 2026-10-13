package br.dev.monitorremoto.core

import org.junit.Assert.assertEquals
import org.junit.Test

class Ipv4SuggestionTest {
    @Test fun subnet24() = assertEquals("192.168.100.", Ipv4Suggestion.prefix("192.168.100.35", 24))
    @Test fun subnet16() = assertEquals("172.16.", Ipv4Suggestion.prefix("172.16.22.1", 16))
    @Test fun subnet23DoesNotAssumeThirdOctet() = assertEquals("192.168.", Ipv4Suggestion.prefix("192.168.100.35", 23))
    @Test fun incompleteOrInvalidIpv4ReturnsNull() {
        assertEquals(null, Ipv4Suggestion.prefix("192.168.100", 24))
        assertEquals(null, Ipv4Suggestion.prefix("999.168.100.2", 24))
        assertEquals(null, Ipv4Suggestion.prefix("1.2.3.4", 32))
    }
}
