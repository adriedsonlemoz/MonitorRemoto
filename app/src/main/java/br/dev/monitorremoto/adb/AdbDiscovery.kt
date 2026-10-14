package br.dev.monitorremoto.adb

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper

@Suppress("DEPRECATION")
class AdbDiscovery(context: Context, private val found: (Endpoint) -> Unit, private val error: (String) -> Unit) {
    data class Endpoint(val name: String, val type: String, val host: String, val port: Int)
    private val manager = context.getSystemService(NsdManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val listeners = mutableListOf<NsdManager.DiscoveryListener>()
    private val queue = ArrayDeque<NsdServiceInfo>()
    private var resolving = false
    private var stopped = false
    fun start(connectOnly: Boolean = false) {
        val types = if (connectOnly) listOf("_adb-tls-connect._tcp.")
            else listOf("_adb-tls-pairing._tcp.", "_adb-tls-connect._tcp.")
        for (type in types) {
            val listener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(t: String) {}
                override fun onDiscoveryStopped(t: String) {}
                override fun onStartDiscoveryFailed(t: String, code: Int) { main.post { if (!stopped) error("mDNS indisponível ($code). Use IP e portas do Android.") } }
                override fun onStopDiscoveryFailed(t: String, code: Int) {}
                override fun onServiceLost(info: NsdServiceInfo) { main.post { queue.removeAll { it.serviceName == info.serviceName } } }
                override fun onServiceFound(info: NsdServiceInfo) { main.post {
                    if (!stopped && queue.size < 32) { queue.addLast(info); next() }
                } }
            }
            listeners.add(listener)
            try { manager.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener) }
            catch (_: Exception) { error("Não foi possível iniciar mDNS. Use o pareamento manual.") }
        }
    }
    private fun next() {
        if (stopped || resolving || queue.isEmpty()) return
        resolving = true
        val info = queue.removeFirst()
        try { manager.resolveService(info, object : NsdManager.ResolveListener {
            override fun onResolveFailed(service: NsdServiceInfo, code: Int) { main.post { resolving = false; next() } }
            override fun onServiceResolved(service: NsdServiceInfo) { main.post {
                resolving = false
                if (!stopped) {
                    val host = service.host?.hostAddress
                    if (host != null && runCatching { AdbPolicy.endpoint(host, service.port.toString()) }.isSuccess)
                        found(Endpoint(service.serviceName, service.serviceType, host, service.port))
                    next()
                }
            } }
        }) } catch (_: Exception) { resolving = false; next() }
    }
    fun stop() {
        stopped = true; queue.clear()
        listeners.forEach { runCatching { manager.stopServiceDiscovery(it) } }
        listeners.clear()
    }
}
