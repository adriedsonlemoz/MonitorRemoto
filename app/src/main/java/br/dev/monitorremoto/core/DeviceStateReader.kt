package br.dev.monitorremoto.core

import android.app.AppOpsManager
import android.app.KeyguardManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import org.json.JSONObject

/** Diagnóstico do próprio aparelho monitorado, nunca captura conteúdo de outros apps. */
object DeviceStateReader {
    private val lock = Any()
    private val known = mutableSetOf<String>()
    private val unavailable = mutableSetOf<String>()
    private var registered = false
    private var initialized = false
    private var cameraManager: CameraManager? = null
    private var lastAppCheck = 0L
    private var cachedApp: String? = null
    private var lastVisibleApp: String? = null
    private var lastVisibleAt = 0L
    private val callback = object : CameraManager.AvailabilityCallback() {
        override fun onCameraAvailable(cameraId: String) = synchronized(lock) {
            known.add(cameraId); unavailable.remove(cameraId); initialized = true
        }
        override fun onCameraUnavailable(cameraId: String) = synchronized(lock) {
            known.add(cameraId); unavailable.add(cameraId); initialized = true
        }
    }

    fun start(context: Context) = synchronized(lock) {
        if (registered) return@synchronized
        val manager = runCatching { context.getSystemService(CameraManager::class.java) }.getOrNull() ?: return@synchronized
        runCatching { manager.registerAvailabilityCallback(callback, android.os.Handler(context.mainLooper)) }
            .onSuccess { cameraManager = manager; registered = true }
    }

    fun stop() = synchronized(lock) {
        if (registered) runCatching { cameraManager?.unregisterAvailabilityCallback(callback) }
        registered = false; initialized = false; cameraManager = null
        known.clear(); unavailable.clear()
        cachedApp = null; lastAppCheck = 0L; lastVisibleApp = null; lastVisibleAt = 0L
    }

    private fun usageAccess(context: Context): Boolean = runCatching {
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= 29)
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
        else ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
        mode == AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)

    private fun foregroundPackage(context: Context): String? {
        val now = SystemClock.elapsedRealtime()
        synchronized(lock) {
            if (now - lastAppCheck in 0..7_999L) return cachedApp
            lastAppCheck = now
        }
        val result = runCatching {
            val usage = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val end = System.currentTimeMillis()
            // Apenas eventos da última hora; ausência de evento não significa ausência de app.
            val events = usage.queryEvents(end - 3_600_000L, end)
            val item = UsageEvents.Event()
            var last: String? = null
            while (events.hasNextEvent()) {
                events.getNextEvent(item)
                if (item.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) last = item.packageName
                else if (item.eventType == UsageEvents.Event.MOVE_TO_BACKGROUND && item.packageName == last) last = null
            }
            last
        }.getOrNull()
        synchronized(lock) {
            cachedApp = result
            if (!result.isNullOrBlank()) {
                lastVisibleApp = result
                lastVisibleAt = System.currentTimeMillis()
            }
        }
        return result
    }

    fun snapshot(context: Context): JSONObject {
        val interactive = runCatching { context.getSystemService(PowerManager::class.java).isInteractive }.getOrNull()
        val locked = runCatching { context.getSystemService(KeyguardManager::class.java).isDeviceLocked }.getOrNull()
        val screenState = runCatching {
            val display = context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
            when (display?.state) {
                Display.STATE_OFF -> "Desligada"
                Display.STATE_ON -> "Ligada"
                Display.STATE_DOZE, Display.STATE_DOZE_SUSPEND -> "Ambiente"
                else -> "Desconhecida"
            }
        }.getOrDefault("Desconhecida")
        val camera = synchronized(lock) { if (!registered || !initialized || known.isEmpty()) null else unavailable.isNotEmpty() }
        val permission = usageAccess(context)
        val app = if (permission && interactive == true && locked == false) foregroundPackage(context) else null
        val remembered = synchronized(lock) { lastVisibleApp to lastVisibleAt }
        return JSONObject().apply {
            put("screenInteractive", interactive ?: JSONObject.NULL)
            put("screenState", screenState)
            put("deviceLocked", locked ?: JSONObject.NULL)
            put("cameraPossiblyBusy", camera ?: JSONObject.NULL)
            put("cameraEvidence", "Indisponibilidade da câmera não comprova gravação nem identifica aplicativo")
            put("usagePermission", permission)
            put("foregroundPackage", app ?: JSONObject.NULL)
            put("lastForegroundPackage", if (permission) remembered.first ?: JSONObject.NULL else JSONObject.NULL)
            put("lastForegroundAt", if (permission && remembered.second > 0) remembered.second else JSONObject.NULL)
            put("updatedMs", System.currentTimeMillis())
        }
    }
}
