package br.dev.monitorremoto.core

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import org.json.JSONObject

/** Dados públicos da rede SIM. Número do telefone: opção desligada por padrão e permissão explícita. */
object TelephonyReader {
    fun snapshot(context: Context): JSONObject = JSONObject().apply {
        val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val state = runCatching { telephony?.simState }.getOrNull()
        put("simState", when (state) {
            TelephonyManager.SIM_STATE_READY -> "Pronto"
            TelephonyManager.SIM_STATE_ABSENT -> "Sem chip"
            TelephonyManager.SIM_STATE_PIN_REQUIRED -> "PIN necessário"
            TelephonyManager.SIM_STATE_PUK_REQUIRED -> "PUK necessário"
            TelephonyManager.SIM_STATE_NETWORK_LOCKED -> "Bloqueado pela rede"
            else -> "Indisponível"
        })
        put("operator", runCatching { telephony?.simOperatorName.orEmpty() }.getOrDefault(""))
        put("networkOperator", runCatching { telephony?.networkOperatorName.orEmpty() }.getOrDefault(""))
        val optedIn = SettingsStore.sharePhoneNumber(context)
        val granted = context.checkSelfPermission(Manifest.permission.READ_PHONE_NUMBERS) == PackageManager.PERMISSION_GRANTED
        put("numberAuthorized", optedIn && granted)
        // Não coletar nem transmitir o número sem consentimento ativo neste aparelho.
        if (optedIn && granted && Build.VERSION.SDK_INT >= 33) {
            val manager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
            val number = runCatching { manager?.getPhoneNumber(SubscriptionManager.getDefaultSubscriptionId()) }.getOrNull()
            put("phoneNumber", number?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
        } else {
            put("phoneNumber", JSONObject.NULL)
        }
    }
}
