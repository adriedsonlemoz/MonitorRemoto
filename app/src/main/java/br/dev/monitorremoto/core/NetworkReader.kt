package br.dev.monitorremoto.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.Inet4Address
import org.json.JSONObject

/** Dados efetivamente observáveis do Android; não presume SSID, qualidade de sinal ou velocidade real. */
object NetworkReader {
    fun snapshot(context: Context): JSONObject {
        val result = JSONObject()
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val active = cm.activeNetwork
            val caps = active?.let { cm.getNetworkCapabilities(it) }
            val type = when {
                caps == null -> "Sem rede"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Dados móveis"
                else -> "Outra rede"
            }
            result.put("type", type)
            result.put("internetValidated", caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) ?: false)
            result.put("metered", runCatching { cm.isActiveNetworkMetered }.getOrDefault(false))
            // Uma VPN pode mascarar o transporte subjacente; não declarar Wi-Fi desligado nesse caso.
            val networks = cm.allNetworks.mapNotNull { cm.getNetworkCapabilities(it) }
            result.put("wifiDetected", networks.any { it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) })
            result.put("cellularDetected", networks.any { it.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) })
            val ipv4 = active?.let { cm.getLinkProperties(it) }?.linkAddresses
                ?.firstOrNull { it.address is Inet4Address && !it.address.isLoopbackAddress }
                ?.address?.hostAddress
            result.put("localIpv4", ipv4 ?: JSONObject.NULL)
        } catch (_: Exception) {
            result.put("type", "Indisponível")
            result.put("internetValidated", JSONObject.NULL)
            result.put("metered", JSONObject.NULL)
            result.put("wifiDetected", JSONObject.NULL)
            result.put("cellularDetected", JSONObject.NULL)
            result.put("localIpv4", JSONObject.NULL)
        }
        return result
    }
}
