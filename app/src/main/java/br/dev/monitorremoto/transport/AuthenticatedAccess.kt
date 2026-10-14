package br.dev.monitorremoto.transport

import java.util.concurrent.CopyOnWriteArrayList

/** Only successful AES-GCM snapshot replies count as access. Not a permanent socket session. */
object AuthenticatedAccess {
    const val STALE_AFTER_MS = 45_000L
    data class Peer(
        val key: String, val address: String, val name: String,
        val method: String, val lastSeenMs: Long, val sinceMs: Long
    )

    private val peers = linkedMapOf<String, Peer>()
    private val listeners = CopyOnWriteArrayList<() -> Unit>()
    fun subscribe(callback: () -> Unit) { listeners.addIfAbsent(callback) }
    fun unsubscribe(callback: () -> Unit) { listeners.remove(callback) }

    @Synchronized fun recent(now: Long = System.currentTimeMillis()): List<Peer> {
        expire(now)
        return peers.values.sortedByDescending { it.lastSeenMs }
    }

    @Synchronized private fun expire(now: Long) {
        val it = peers.iterator()
        while (it.hasNext()) {
            val age = now - it.next().value.lastSeenMs
            if (age < 0 || age > STALE_AFTER_MS) it.remove()
        }
    }

    fun authenticated(address: String, id: String, name: String, method: String, now: Long = System.currentTimeMillis()) {
        val safeId = id.take(64).takeIf { it.matches(Regex("[a-zA-Z0-9-]{8,64}")) }
        val key = if (safeId == null) "legacy:$address" else "$safeId:$address"
        val cleanName = name.filter { it >= ' ' && it != '\u007f' }.take(72).trim()
        val cleanMethod = when (method) {
            "QR Code", "Manual", "Chave salva" -> method
            else -> "Não informado"
        }
        synchronized(this) {
            expire(now)
            val old = peers[key]
            peers[key] = Peer(key, address, cleanName, cleanMethod, now,
                old?.sinceMs ?: now)
            // Limit memory even if multiple clients authenticate with the same shared key.
            if (peers.size > 12) peers.remove(peers.values.minByOrNull { it.lastSeenMs }!!.key)
        }
        listeners.forEach { runCatching { it.invoke() } }
    }

    /** Encerramento explícito pelo controlador autenticado; não desliga o serviço. */
    fun disconnected(address: String, id: String) {
        val safeId = id.take(64).takeIf { it.matches(Regex("[a-zA-Z0-9-]{8,64}")) }
        val key = if (safeId == null) "legacy:$address" else "$safeId:$address"
        synchronized(this) { peers.remove(key) }
        listeners.forEach { runCatching { it.invoke() } }
    }

    fun clear() {
        synchronized(this) { peers.clear() }
        listeners.forEach { runCatching { it.invoke() } }
    }
}
