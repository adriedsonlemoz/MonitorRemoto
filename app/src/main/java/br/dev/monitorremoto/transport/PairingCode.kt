package br.dev.monitorremoto.transport

import br.dev.monitorremoto.security.CryptoBox
import java.net.URI

/** Conteúdo de QR temporário. Somente endereços IPv4 privados na alpha.4. */
object PairingCode {
    private const val SCHEME = "monitorremoto"
    private const val URI_HOST = "pair"
    private val sessionPattern = Regex("[A-Za-z0-9_-]{22}")

    data class Offer(val host: String, val session: String, val ephemeralKey: ByteArray)

    fun encode(offer: Offer): String {
        require(isPrivateIpv4(offer.host)) { "O QR exige um IPv4 privado local." }
        require(sessionPattern.matches(offer.session)) { "Identificador de sessão inválido." }
        require(offer.ephemeralKey.size == 32)
        return "$SCHEME://$URI_HOST?v=1&app=${AppVersion.NAME}&host=${offer.host}&port=${WireProtocol.PORT}" +
            "&sid=${offer.session}&secret=${CryptoBox.encode(offer.ephemeralKey)}"
    }

    fun decode(text: String): Offer {
        require(text.length in 90..500) { "Tamanho de QR inesperado." }
        val uri = URI(text)
        require(uri.scheme == SCHEME && uri.host == URI_HOST && uri.port == -1 &&
            uri.rawFragment == null && uri.rawUserInfo == null && uri.path.isNullOrEmpty()) {
            "Este QR não pertence ao Monitor Remoto."
        }
        val tokens = uri.rawQuery?.split('&')?.map { part ->
            require(part.count { it == '=' } == 1) { "Campo de QR inválido." }
            part.substringBefore('=') to part.substringAfter('=')
        } ?: error("QR sem parâmetros")
        require(tokens.map { it.first }.toSet().size == tokens.size) { "Campos repetidos." }
        val params = tokens.toMap()
        require(params.containsKey("app")) { AppVersion.incompatibility(null) }
        require(params.keys == setOf("v", "app", "host", "port", "sid", "secret")) { "Campos de QR inesperados." }
        require(params["v"] == "1" && params["port"] == WireProtocol.PORT.toString()) {
            "Protocolo ou porta não suportado."
        }
        require(AppVersion.compatible(params["app"])) { AppVersion.incompatibility(params["app"]) }
        val host = params.getValue("host")
        require(isPrivateIpv4(host)) { "QR não contém IPv4 privado local." }
        val session = params.getValue("sid")
        require(sessionPattern.matches(session)) { "Sessão inválida." }
        val key = CryptoBox.decodeKey(params.getValue("secret"))
        return Offer(host, session, key)
    }

    fun isPrivateIpv4(host: String): Boolean {
        if (host.length !in 7..15) return false
        val sections = host.split('.')
        if (sections.size != 4) return false
        val octets = sections.map { s ->
            if (s.isEmpty() || s.length > 3 || (s.length > 1 && s[0] == '0') || !s.all(Char::isDigit))
                return false
            s.toIntOrNull()?.takeIf { it in 0..255 } ?: return false
        }
        return octets[0] == 10 || (octets[0] == 172 && octets[1] in 16..31) ||
            (octets[0] == 192 && octets[1] == 168)
    }
}
