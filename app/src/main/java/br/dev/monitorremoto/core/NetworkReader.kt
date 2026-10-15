package br.dev.monitorremoto.core

import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import android.location.LocationManager
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
            val allowed = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val locationActive = runCatching {
                if (Build.VERSION.SDK_INT >= 28)
                    (context.getSystemService(Context.LOCATION_SERVICE) as LocationManager).isLocationEnabled
                else (Settings.Secure.getInt(context.contentResolver,
                    Settings.Secure.LOCATION_MODE, Settings.Secure.LOCATION_MODE_OFF) != Settings.Secure.LOCATION_MODE_OFF)
            }.getOrDefault(false)
            result.put("wifiPermission", allowed)
            result.put("wifiLocationActive", locationActive)
            // Wi-Fi pode não ser a rede padrão quando há VPN ou dados móveis.
            val wifiNetwork = cm.allNetworks.firstOrNull { network ->
                cm.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            }
            val info = if (allowed && locationActive && wifiNetwork != null) runCatching {
                val capabilityInfo = if (Build.VERSION.SDK_INT >= 31)
                    cm.getNetworkCapabilities(wifiNetwork)?.transportInfo as? WifiInfo else null
                if (capabilityInfo != null && capabilityInfo.ssid != WifiManager.UNKNOWN_SSID) capabilityInfo
                else (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).connectionInfo
            }.getOrNull() else null
            val ssid = info?.ssid?.trim('"')?.takeIf { it.isNotBlank() && it != WifiManager.UNKNOWN_SSID }
            val rssi = info?.rssi?.takeIf { it in -110..-10 }
            result.put("wifiSsid", ssid ?: JSONObject.NULL)
            result.put("wifiRssiDbm", rssi ?: JSONObject.NULL)
        } catch (_: Exception) {
            result.put("type", "Indisponível")
            result.put("internetValidated", JSONObject.NULL)
            result.put("metered", JSONObject.NULL)
            result.put("wifiDetected", JSONObject.NULL)
            result.put("cellularDetected", JSONObject.NULL)
            result.put("localIpv4", JSONObject.NULL)
            result.put("wifiPermission", false)
            result.put("wifiLocationActive", false)
            result.put("wifiSsid", JSONObject.NULL)
            result.put("wifiRssiDbm", JSONObject.NULL)
        }
        return result
    }
}
