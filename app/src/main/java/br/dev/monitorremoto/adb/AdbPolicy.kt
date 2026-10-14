package br.dev.monitorremoto.adb

import java.security.SecureRandom

/** Numeric private IPv4 only: no DNS rebinding, internet or legacy port assumptions. */
object AdbPolicy {
    fun endpoint(host: String, port: String): Pair<String, Int> {
        val parts = host.split('.')
        require(parts.size == 4 && parts.all { it.isNotEmpty() && it.length <= 3 && it.all { c -> c in '0'..'9' } }) { "Informe um IPv4 privado da rede local." }
        val n = parts.map { it.toInt() }
        require(n.all { it in 0..255 } && (n[0] == 10 || n[0] == 192 && n[1] == 168 || n[0] == 172 && n[1] in 16..31)) { "Somente IPv4 privado da rede local é permitido." }
        val p = port.toIntOrNull()
        require(p != null && p in 1..65535) { "Porta inválida (1–65535)." }
        return n.joinToString(".") to p
    }
    fun code(value: String): String {
        require(value.length == 6 && value.all { it in '0'..'9' }) { "Informe os seis dígitos exibidos no monitorado." }
        return value
    }
    class QrSession(val service: String, val password: String, val expiresAt: Long) {
        fun payload() = "WIFI:T:ADB;S:$service;P:$password;;"
        fun valid(now: Long) = now < expiresAt
        override fun toString() = "QrSession(redacted)"
    }
    fun newQr(now: Long): QrSession {
        val random = SecureRandom()
        fun token() = ByteArray(16).also(random::nextBytes).joinToString("") { "%02x".format(it) }
        return QrSession("studio-" + token(), token(), now + 120_000)
    }
    enum class Command(val shell: String) {
        DIAGNOSTIC("getprop ro.product.model; getprop ro.build.version.release; getprop ro.build.version.sdk"),
        BACK("input keyevent 4"), HOME("input keyevent 3"), RECENTS("input keyevent 187")
    }
}
