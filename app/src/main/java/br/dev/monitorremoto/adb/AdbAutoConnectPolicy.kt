package br.dev.monitorremoto.adb

/** Associação conservadora: o QR/código determina o IP; mDNS só fornece a porta TLS desse IP. */
object AdbAutoConnectPolicy {
    fun connectionServices(pairedHost: String, discovered: List<AdbDiscovery.Endpoint>): List<AdbDiscovery.Endpoint> =
        discovered.filter {
            it.host == pairedHost && it.type.contains("_adb-tls-connect") &&
                it.port in 1..65535
        }.distinctBy { "${it.host}:${it.port}" }
}
