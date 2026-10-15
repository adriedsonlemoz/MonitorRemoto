package br.dev.monitorremoto.core

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.Base64
import java.io.ByteArrayOutputStream
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** Só apps com lançador visíveis ao Android; nunca lê dados internos. */
object InstalledAppsReader {
    fun iconBatch(context: Context, requested: List<String>): JSONObject {
        require(requested.size in 1..RemoteAppsPolicy.pageSize()) { "Lote inválido." }
        val launchable = launchable(context)
        val allowed = (0 until launchable.length()).mapNotNull { launchable.optJSONObject(it)?.optString("package") }.toSet()
        val result = JSONObject()
        val pm = context.packageManager
        requested.distinct().filter { it in allowed }.forEach { pkg ->
            val encoded = runCatching {
                val icon = pm.getApplicationIcon(pkg)
                val bitmap = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                icon.setBounds(0, 0, 40, 40)
                icon.draw(canvas)
                val out = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                if (out.size() > 4_096) {
                    out.reset()
                    @Suppress("DEPRECATION")
                    bitmap.compress(Bitmap.CompressFormat.WEBP, 70, out)
                }
                bitmap.recycle()
                out.toByteArray().takeIf { it.size <= 4_096 }?.let { Base64.encodeToString(it, Base64.NO_WRAP) }
            }.getOrNull()
            if (encoded != null) result.put(pkg, encoded)
        }
        return result
    }

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
