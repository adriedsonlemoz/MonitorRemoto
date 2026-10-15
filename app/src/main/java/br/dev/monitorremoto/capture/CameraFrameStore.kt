package br.dev.monitorremoto.capture

import android.os.SystemClock
import br.dev.monitorremoto.core.CameraStreamPolicy
import br.dev.monitorremoto.core.CameraRemoteSwitchPolicy

/** Quadros JPEG voláteis de uma Activity de câmera VISÍVEL no monitorado. Sem start remoto. */
object CameraFrameStore {
    data class Frame(val sequence: Long, val lens: String, val jpeg: ByteArray)
    private var current: Frame? = null
    private var receivedAt = 0L
    private var sequence = 0L
    private var activeLens: String? = null
    private var session = 0L
    private var lastViewerRequestAt = 0L
    private var remoteSwitchAllowed = false
    private var pendingSwitch: SwitchRequest? = null
    private var lastSwitchRequestedAt = 0L

    data class SwitchRequest(val lens: String, val sessionToken: Long)

    /** Dados coerentes de UMA leitura; jpeg omitido quando o controlador já o possui. */
    data class Status(val active: Boolean, val lens: String?, val ageMs: Long,
        val sequence: Long, val jpeg: ByteArray?,
        val sessionToken: Long = 0L, val remoteSwitchAllowed: Boolean = false)

    /** Cada abertura recebe um token; quadros de câmeras antigas nunca são reutilizados. */
    @Synchronized fun begin(lens: String): Long {
        require(lens in setOf("Frontal", "Traseira"))
        sequence++
        current = null
        receivedAt = 0L
        activeLens = lens
        lastViewerRequestAt = 0L
        remoteSwitchAllowed = false
        pendingSwitch = null
        session++
        return session
    }

    @Synchronized fun stop() {
        activeLens = null
        lastViewerRequestAt = 0L
        remoteSwitchAllowed = false
        pendingSwitch = null
        lastSwitchRequestedAt = 0L // sessão local encerrada: nenhuma autorização prévia persiste
        current = null
        receivedAt = 0L
        sequence++
        session++
    }

    @Synchronized fun viewerPing() {
        if (activeLens != null) lastViewerRequestAt = SystemClock.elapsedRealtime()
    }

    @Synchronized fun hasRecentViewer(): Boolean =
        activeLens != null && CameraStreamPolicy.hasRecentViewer(
            lastViewerRequestAt, SystemClock.elapsedRealtime())

    /** Evita estado misto se a câmera for fechada/trocada durante a consulta. */
    @Synchronized fun status(afterSequence: Long): Status {
        val lens = activeLens ?: return Status(false, null, -1L, 0L, null)
        val age = frameAgeMs()
        val fresh = current?.takeIf { age in 0..CameraStreamPolicy.MAX_FRAME_AGE_MS }
        return Status(true, lens, age, fresh?.sequence ?: 0L,
            if (fresh != null && fresh.sequence != afterSequence) fresh.jpeg.copyOf() else null,
            session, remoteSwitchAllowed)
    }

    /** Opt-in temporário concedido na Activity visível; nunca salvo em disco. */
    @Synchronized fun setRemoteSwitchAllowed(sessionToken: Long, allowed: Boolean) {
        if (sessionToken != session || activeLens == null) return
        remoteSwitchAllowed = allowed
        if (!allowed) pendingSwitch = null
    }

    /** Apenas enfileira pedido para a Activity local. Não abre Camera2 no servidor. */
    @Synchronized fun requestRemoteSwitch(expectedSession: Long, targetLens: String): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (!CameraRemoteSwitchPolicy.accept(remoteSwitchAllowed, activeLens, targetLens,
                session, expectedSession, pendingSwitch != null,
                hasRecentViewer(), lastSwitchRequestedAt, now)) return false
        pendingSwitch = SwitchRequest(targetLens, session)
        lastSwitchRequestedAt = now
        return true
    }

    /** Consumido somente pela Activity já em primeiro plano, durante um frame Camera2. */
    @Synchronized fun consumeSwitchRequest(sessionToken: Long): SwitchRequest? {
        if (activeLens == null || !remoteSwitchAllowed || sessionToken != session) return null
        return pendingSwitch?.also { pendingSwitch = null }
    }

    @Synchronized fun isActive() = activeLens != null
    @Synchronized fun lens(): String? = activeLens

    @Synchronized fun publish(sessionToken: Long, bytes: ByteArray) {
        if (sessionToken != session) return
        val lens = activeLens ?: return
        if (!CameraStreamPolicy.validFrame(bytes)) return
        sequence++
        current = Frame(sequence, lens, bytes.copyOf())
        receivedAt = SystemClock.elapsedRealtime()
    }

    @Synchronized fun frameAgeMs(): Long =
        if (activeLens == null || receivedAt == 0L) -1L
        else (SystemClock.elapsedRealtime() - receivedAt).coerceAtLeast(0L)

    @Synchronized fun snapshot(): Frame? {
        if (frameAgeMs() !in 0..CameraStreamPolicy.MAX_FRAME_AGE_MS) return null
        return current?.let { it.copy(jpeg = it.jpeg.copyOf()) }
    }
}
