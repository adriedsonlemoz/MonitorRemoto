package br.dev.monitorremoto.adb
import org.junit.Assert.*
import org.junit.Test
class AdbPolicyTest {
 @Test fun privateNetworks() { for (ip in listOf("10.0.0.2","172.16.0.1","172.31.255.254","192.168.0.4")) assertEquals(ip to 37219,AdbPolicy.endpoint(ip,"37219")) }
 @Test fun rejectInternetLoopbackAndNames() { for (ip in listOf("8.8.8.8","127.0.0.1","0.0.0.0","172.32.0.1","172.15.1.1","192.169.1.1","169.254.2.3","example.com","::1","10.0.0.256","10.0.0.-1","10.0.0.1;id")) assertTrue(ip,runCatching { AdbPolicy.endpoint(ip,"1234") }.isFailure) }
 @Test fun dynamicPortsAndBounds() { for(p in listOf("0","65536","-1","abc","")) assertTrue(runCatching { AdbPolicy.endpoint("192.168.1.2",p) }.isFailure); assertEquals(65535,AdbPolicy.endpoint("192.168.1.2","65535").second) }
 @Test fun numericCodePreservesLeadingZero() { assertEquals("001234",AdbPolicy.code("001234")); for(c in listOf("12345","1234567","abcdef","１２３４５６")) assertTrue(runCatching { AdbPolicy.code(c) }.isFailure) }
 @Test fun qrFormatEntropyAndExpiration() { val q=AdbPolicy.newQr(100); val other=AdbPolicy.newQr(100); assertTrue(q.payload().matches(Regex("WIFI:T:ADB;S:studio-[0-9a-f]{32};P:[0-9a-f]{32};;"))); assertNotEquals(q.service,other.service); assertNotEquals(q.password,other.password); assertTrue(q.valid(120099)); assertFalse(q.valid(120100)); assertFalse(q.toString().contains(q.password)) }
 @Test fun commandAllowlist() { assertEquals(4,AdbPolicy.Command.entries.size); assertEquals("input keyevent 4",AdbPolicy.Command.BACK.shell); assertFalse(AdbPolicy.Command.entries.any { it.shell.contains("tcpip") || it.shell.contains("screencap") }) }
}
