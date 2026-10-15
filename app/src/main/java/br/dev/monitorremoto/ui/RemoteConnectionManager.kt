package br.dev.monitorremoto.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import br.dev.monitorremoto.core.RecentDevices
import br.dev.monitorremoto.transport.RemoteClient
import org.json.JSONObject
import kotlin.concurrent.thread

/** State and encrypted network I/O for the remote controller, independent of Views/Activities.
 * Call [fetch] from the UI thread. Its callback is dispatched on the UI thread as well.
 */
internal class RemoteConnectionManager(context: Context) {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var generation = 0L
    private var fetchingHost = ""
    private var fetching = false

    var method = "Chave salva"
    var state = "idle" // idle / loading / connected / offline
        private set
    var message: String? = null
        private set
    var issue: ConnectionIssue? = null
        private set
    var cached: JSONObject? = null
        private set
    var cachedHost = ""
        private set
    var lastSuccessMs = 0L
        private set
    var lastAttemptMs = 0L
        private set

    fun effectiveState(host: String): String =
        if (host.isBlank() || RecentDevices.key(appContext, host) == null) "idle" else state

    /** Selecting a device invalidates all pending responses from the previous selection. */
    fun select(method: String) {
        invalidate()
        this.method = method
        state = "loading"
        message = null
        issue = null
    }

    /** A saved device was removed or has been forgotten. */
    fun clear(resetMethod: Boolean = true) {
        invalidate()
        cached = null
        cachedHost = ""
        lastSuccessMs = 0L
        state = "idle"
        issue = null
        message = if (resetMethod) "Seleção removida." else null
        if (resetMethod) method = "Chave salva"
    }

    fun pause() { if (state == "connected") state = "idle" }

    private fun invalidate() {
        generation++
        fetchingHost = ""
        fetching = false
    }

    /** No copy of the permanent key is stored in this manager or any UI model. */
    fun disconnect(host: String) {
        val key = if (host.isNotBlank()) RecentDevices.key(appContext, host) else null
        if (host.isNotBlank() && key != null) thread(name = "MonitorRemoto-disconnect") {
            runCatching { RemoteClient.disconnect(host, key, appContext) }
        }
    }

    /** Requisita SMS apenas ao abrir Mensagens; não consulta a inbox no polling de 15 s. */
    fun loadMessages(host: String, onComplete: (Result<JSONObject>) -> Unit) {
        val key = if (host.isBlank()) null else RecentDevices.key(appContext, host)
        if (key == null || state != "connected" || cachedHost != host) {
            onComplete(Result.failure(IllegalStateException("Conecte e atualize o aparelho primeiro.")))
            return
        }
        val accessMethod = method
        thread(name = "MonitorRemoto-messages-read") {
            val result = runCatching { RemoteClient.snapshot(host, key, appContext, accessMethod, true) }
            main.post {
                if (cachedHost == host && br.dev.monitorremoto.core.SettingsStore.getRemoteHost(appContext) == host)
                    onComplete(result)
            }
        }
    }

    /** A escolha do controlador pode mudar enquanto a resposta remota trafega. */
    fun sendMessage(host: String, operation: String, recipient: String, text: String,
        onComplete: (Result<String>) -> Unit, subscriptionId: Int = -1) {
        val key = if (host.isBlank()) null else RecentDevices.key(appContext, host)
        if (key == null || state != "connected" || cachedHost != host) {
            onComplete(Result.failure(IllegalStateException("Atualize a conexão antes de enviar.")))
            return
        }
        val accessMethod = method
        thread(name = "MonitorRemoto-message") {
            val result = runCatching { RemoteClient.messageCommand(host, key, appContext,
                accessMethod, operation, recipient, text, subscriptionId) }
            main.post {
                if (cachedHost == host && br.dev.monitorremoto.core.SettingsStore.getRemoteHost(appContext) == host) onComplete(result)
            }
        }
    }

    /** Resposta ignorada se o aparelho selecionado mudou durante a operação. */
    fun deviceControl(host: String, action: String, seconds: Int = 0,
        confirmation: String = "", onComplete: (Result<JSONObject>) -> Unit) {
        val key = if (host.isBlank()) null else RecentDevices.key(appContext, host)
        if (key == null || state != "connected" || cachedHost != host) {
            onComplete(Result.failure(IllegalStateException("Atualize a conexão antes de comandar.")))
            return
        }
        val accessMethod = method
        thread(name = "MonitorRemoto-device-control") {
            val response = runCatching { RemoteClient.deviceControl(host, key, appContext, accessMethod,
                action, seconds, confirmation) }
            main.post {
                if (state == "connected" && cachedHost == host &&
                    br.dev.monitorremoto.core.SettingsStore.getRemoteHost(appContext) == host) onComplete(response)
            }
        }
    }

    /**
     * Callback receives (finished, freshSnapshot). When finished is false the state became loading.
     * A failed request can still have [cached] for the same host, but it is not marked as live data.
     */
    fun fetch(host: String, stillVisible: () -> Boolean, onUpdate: (Boolean, JSONObject?) -> Unit) {
        if (fetching && fetchingHost == host) return
        val key = if (host.isBlank()) null else RecentDevices.key(appContext, host)
        if (host.isBlank() || key == null) {
            state = "idle"
            message = "Configure um aparelho em Conectar."
            onUpdate(true, null)
            return
        }
        fetching = true
        lastAttemptMs = System.currentTimeMillis()
        fetchingHost = host
        val token = ++generation
        val accessMethod = method
        state = "loading"
        message = null
        issue = null
        onUpdate(false, null)

        thread(name = "MonitorRemoto-client") {
            val response = runCatching { RemoteClient.snapshot(host, key, appContext, accessMethod) }
            main.post {
                // A disconnected or changed device cannot paint a stale response on the new screen.
                if (token != generation || fetchingHost != host) return@post
                fetching = false
                fetchingHost = ""
                if (!stillVisible()) return@post
                response.onSuccess { snapshot ->
                    lastSuccessMs = System.currentTimeMillis()
                    cached = snapshot
                    cachedHost = host
                    state = "connected"
                    issue = null
                    val deviceName = snapshot.optJSONObject("battery")?.optString("device").orEmpty()
                    RecentDevices.markSuccess(appContext, host, deviceName, lastSuccessMs)
                    onUpdate(true, snapshot)
                }.onFailure { error ->
                    state = "offline"
                    issue = ConnectionIssue.from(error)
                    message = issue?.title
                    onUpdate(true, null)
                }
            }
        }
    }
}
