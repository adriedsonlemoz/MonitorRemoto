package br.dev.monitorremoto.ui

import android.os.Handler
import android.os.Looper
import br.dev.monitorremoto.transport.PairingCode
import br.dev.monitorremoto.transport.RemoteClient
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/** QR pairing coordinator. It never persists temporary keys or interacts with Android Views. */
internal class PairingController {
    private val pairing = AtomicBoolean(false)
    private val main = Handler(Looper.getMainLooper())

    /** Returns false when a pairing operation is already in progress. */
    fun pair(code: PairingCode.Offer, onComplete: (Result<ByteArray>) -> Unit): Boolean {
        if (!pairing.compareAndSet(false, true)) return false
        thread(name = "MonitorRemoto-qr-pair") {
            val result = runCatching { RemoteClient.pair(code) }
            main.post {
                pairing.set(false)
                onComplete(result)
            }
        }
        return true
    }
}
