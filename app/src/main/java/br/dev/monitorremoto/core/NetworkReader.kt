package br.dev.monitorremoto.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import org.json.JSONObject

/** Indica o transporte da rede ativa do aparelho MONITORADO, sem adivinhar SSID. */
object NetworkReader {
    fun snapshot(context: Context): JSONObject {
        val type = try {
            val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val caps = manager.activeNetwork?.let(manager::getNetworkCapabilities)
            when {
                caps == null -> "Sem rede"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Dados móveis"
                else -> "Outra rede"
            }
        } catch (_: Exception) { "Indisponível" }
        return JSONObject().put("type", type)
    }
}
