package br.dev.monitorremoto.core

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SubscriptionManager
import android.telephony.SmsManager
import org.json.JSONArray
import org.json.JSONObject

/** Apenas índices de SIM. Número telefônico e ICCID nunca são expostos. */
object SmsSubscriptions {
    fun hasAccess(context: Context) =
        context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED

    fun options(context: Context): JSONArray = JSONArray().apply {
        put(JSONObject().put("id", -1).put("label", "Chip padrão"))
        if (!hasAccess(context)) return@apply
        runCatching {
            val manager = context.getSystemService(SubscriptionManager::class.java)
            manager?.activeSubscriptionInfoList.orEmpty().forEach { item ->
                put(JSONObject().put("id", item.subscriptionId).put("label", "SIM ${item.simSlotIndex + 1}"))
            }
        }
    }

    fun sender(context: Context, subId: Int): SmsManager {
        if (subId != -1) {
            check(hasAccess(context)) { "READ_PHONE_STATE necessária para escolher chip." }
            val list = context.getSystemService(SubscriptionManager::class.java)?.activeSubscriptionInfoList.orEmpty()
            check(list.any { it.subscriptionId == subId }) { "Chip solicitado não está ativo." }
        }
        if (Build.VERSION.SDK_INT >= 31) {
            val manager = context.getSystemService(SmsManager::class.java)
            return if (subId == -1) manager else manager.createForSubscriptionId(subId)
        }
        @Suppress("DEPRECATION")
        return if (subId == -1) SmsManager.getDefault() else SmsManager.getSmsManagerForSubscriptionId(subId)
    }
}
