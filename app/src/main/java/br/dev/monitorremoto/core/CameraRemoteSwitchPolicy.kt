package br.dev.monitorremoto.core

/** Lista positiva e barreiras de consentimento para troca de câmera, sem iniciar captura. */
object CameraRemoteSwitchPolicy {
    private const val MIN_REQUEST_INTERVAL_MS = 2_000L
    fun validLens(lens: String) = lens == "Frontal" || lens == "Traseira"

    fun accept(allowed: Boolean, currentLens: String?, targetLens: String,
        currentSession: Long, expectedSession: Long, hasPending: Boolean,
        recentViewer: Boolean, lastRequestMs: Long, nowMs: Long): Boolean =
        allowed && validLens(targetLens) && currentLens != null && currentLens != targetLens &&
            currentSession > 0L && expectedSession == currentSession &&
            !hasPending && recentViewer && (lastRequestMs == 0L ||
                (nowMs >= lastRequestMs && nowMs - lastRequestMs >= MIN_REQUEST_INTERVAL_MS))
}
