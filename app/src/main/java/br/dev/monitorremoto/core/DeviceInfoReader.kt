package br.dev.monitorremoto.core

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import org.json.JSONObject

/** Apenas informações disponíveis ao app, sem acesso a dados privados de outros apps. */
object DeviceInfoReader {
    fun snapshot(context: Context): JSONObject = JSONObject().apply {
        put("manufacturer", Build.MANUFACTURER.orEmpty())
        put("brand", Build.BRAND.orEmpty())
        put("model", Build.MODEL.orEmpty())
        put("androidVersion", Build.VERSION.RELEASE.orEmpty())
        put("sdk", Build.VERSION.SDK_INT)
        put("securityPatch", Build.VERSION.SECURITY_PATCH.orEmpty())
        put("architecture", Build.SUPPORTED_ABIS.firstOrNull().orEmpty())
        put("uptimeMs", SystemClock.elapsedRealtime())
        val memory = runCatching {
            ActivityManager.MemoryInfo().also {
                (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(it)
            }
        }.getOrNull()
        put("ramTotalBytes", memory?.totalMem ?: JSONObject.NULL)
        put("ramAvailableBytes", memory?.availMem ?: JSONObject.NULL)
        // Partição de dados do usuário; não representa o tamanho da nuvem ou cartão SD.
        val storage = runCatching { StatFs(Environment.getDataDirectory().absolutePath) }.getOrNull()
        put("storageTotalBytes", storage?.totalBytes ?: JSONObject.NULL)
        put("storageAvailableBytes", storage?.availableBytes ?: JSONObject.NULL)
    }
}
