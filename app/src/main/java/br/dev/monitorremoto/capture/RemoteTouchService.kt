package br.dev.monitorremoto.capture

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.os.SystemClock
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import br.dev.monitorremoto.core.SettingsStore
import br.dev.monitorremoto.core.RemoteGesturePolicy

/** Serviço de acessibilidade opt-in. Não lê textos, nós de UI ou senhas. */
class RemoteTouchService : AccessibilityService() {
    companion object {
        @Volatile private var current: RemoteTouchService? = null
        @Volatile private var lastTouchMs = 0L
        fun isReady() = current != null

        @Synchronized fun send(
            x: Float, y: Float, endX: Float, endY: Float, duration: Long,
            action: String
        ): Boolean {
            val service = current ?: return false
            if (!SettingsStore.isMonitorEnabled(service) || !SettingsStore.allowRemoteTouch(service) ||
                !ScreenFrameStore.sharing) return false
            if (!RemoteGesturePolicy.valid(action, x, y, endX, endY, duration)) return false
            val now = SystemClock.elapsedRealtime()
            if (now - lastTouchMs < 125) return false
            if (action in setOf("back", "home")) {
                lastTouchMs = now
                return service.performGlobalAction(if (action == "back") AccessibilityService.GLOBAL_ACTION_BACK else AccessibilityService.GLOBAL_ACTION_HOME)
            }
            val metrics = service.resources.displayMetrics
            val bounds = if (Build.VERSION.SDK_INT >= 30)
                service.getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds else null
            val w = bounds?.width() ?: metrics.widthPixels
            val h = bounds?.height() ?: metrics.heightPixels
            if (w <= 0 || h <= 0) return false
            val path = Path().apply {
                moveTo(x * (w - 1), y * (h - 1))
                if (action == "swipe") lineTo(endX * (w - 1), endY * (h - 1))
            }
            val gesture = GestureDescription.Builder().addStroke(
                GestureDescription.StrokeDescription(path, 0, if (action == "tap") 80L else if (action == "longPress") duration.coerceAtLeast(550L) else duration)
            ).build()
            val accepted = service.dispatchGesture(gesture, null, null)
            if (accepted) lastTouchMs = now
            return accepted // aceito pela API, não confirma efeito final na interface
        }
    }

    override fun onServiceConnected() { super.onServiceConnected(); current = this }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) { /* intencional: sem inspeção de conteúdo */ }
    override fun onInterrupt() = Unit
    override fun onDestroy() { if (current === this) current = null; super.onDestroy() }
}
