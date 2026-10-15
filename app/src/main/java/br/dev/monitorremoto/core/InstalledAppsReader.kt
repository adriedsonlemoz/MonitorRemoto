package br.dev.monitorremoto.core

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** Só apps com lançador visíveis ao Android; nunca lê dados internos, não controla apps. */
object InstalledAppsReader {
    fun launchable(context: Context): JSONArray {
        val request = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = context.packageManager.queryIntentActivities(request, 0)
            .mapNotNull { info ->
                val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                val label = runCatching { info.loadLabel(context.packageManager).toString().trim() }
                    .getOrDefault(pkg)
                if (label.isBlank() || pkg.length > 160) null else pkg to label.take(80)
            }
            .distinctBy { it.first }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.second })
            .take(120)
        return JSONArray().apply {
            apps.forEach { (pkg, name) -> put(JSONObject().put("name", name).put("package", pkg)) }
        }
    }
}
