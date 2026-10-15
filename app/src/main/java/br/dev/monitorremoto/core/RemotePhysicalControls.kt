package br.dev.monitorremoto.core

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import org.json.JSONObject

/** Pertence ao serviço de monitoramento. Não grava estados em disco nem altera volume/silencioso. */
object RemotePhysicalControls {
    private val main = Handler(Looper.getMainLooper())
    private var tone: ToneGenerator? = null
    private var soundUntilMs = 0L
    private var soundGeneration = 0
    private var torchManager: CameraManager? = null
    private var torchId: String? = null
    private var torchState = "desconhecida"
    private var torchOwned = false
    private var callback: CameraManager.TorchCallback? = null
    private var lastTorchRequestMs = 0L

    /** A câmera selecionada tem flash físico; o callback observa alterações reais do Android. */
    @Synchronized fun start(context: Context) {
        if (callback != null) return
        val manager = context.getSystemService(CameraManager::class.java) ?: return
        val id = try {
            manager.cameraIdList.firstOrNull { candidate ->
                val info = manager.getCameraCharacteristics(candidate)
                info.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK &&
                    info.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        } catch (_: Exception) { null }
        torchManager = manager
        torchId = id
        torchState = if (id == null) "indisponível" else "desconhecida"
        if (id == null) return
        val observer = object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                synchronized(this@RemotePhysicalControls) {
                    if (cameraId == torchId) {
                        torchState = if (enabled) "ligada" else "desligada"
                        if (!enabled) torchOwned = false
                    }
                }
            }
            override fun onTorchModeUnavailable(cameraId: String) {
                synchronized(this@RemotePhysicalControls) {
                    if (cameraId == torchId) { torchState = "indisponível"; torchOwned = false }
                }
            }
        }
        try {
            manager.registerTorchCallback(observer, main)
            callback = observer
        } catch (_: Exception) {
            torchState = "desconhecida"
        }
    }

    @Synchronized fun startSound(seconds: Int): String {
        check(seconds in DeviceControlPolicy.durations) { "Duração inválida." }
        stopSound()
        val generator = ToneGenerator(AudioManager.STREAM_ALARM, 70)
        // A API informa se iniciou, não se foi audível: DND, volume e políticas ainda se aplicam.
        if (!generator.startTone(ToneGenerator.TONE_PROP_BEEP, 650)) {
            generator.release()
            error("O Android não iniciou o alerta sonoro.")
        }
        tone = generator
        val generation = ++soundGeneration
        soundUntilMs = SystemClock.elapsedRealtime() + seconds * 1000L
        fun scheduleBeep() {
            main.postDelayed(object : Runnable {
                override fun run() {
                    synchronized(this@RemotePhysicalControls) {
                        if (generation != soundGeneration || tone == null) return
                        if (SystemClock.elapsedRealtime() >= soundUntilMs) { stopSound(); return }
                        if (runCatching { tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 650) }.getOrNull() != true) {
                            stopSound(); return
                        }
                        scheduleBeep()
                    }
                }
            }, 1000L)
        }
        scheduleBeep()
        return "Alerta solicitado por ${seconds}s. Volume e Não Perturbe podem impedir a audição."
    }

    @Synchronized fun stopSound(): String {
        soundGeneration++
        soundUntilMs = 0L
        runCatching { tone?.stopTone(); tone?.release() }
        tone = null
        return "Alerta interrompido."
    }

    /** Desliga somente o flash ligado por este serviço, não o uso prévio por outro app. */
    @Synchronized fun stopOwnedTorch() {
        if (torchOwned) {
            val id = torchId
            if (id != null) runCatching { torchManager?.setTorchMode(id, false) }
            torchOwned = false
            torchState = "desconhecida" // confirmar somente por TorchCallback
        }
    }

    @Synchronized fun setTorch(enabled: Boolean): String {
        val manager = torchManager ?: error("Câmera não disponível neste aparelho.")
        val id = torchId ?: error("Sem flash traseiro compatível.")
        // Evita spam involuntário e disputa com outros apps.
        val now = SystemClock.elapsedRealtime()
        check(now - lastTorchRequestMs >= 500L) { "Aguarde antes de repetir o comando." }
        lastTorchRequestMs = now
        try { manager.setTorchMode(id, enabled) }
        catch (e: SecurityException) { error("Autorize Câmera nas permissões do Android monitorado.") }
        catch (e: Exception) { error("Flash indisponível ou ocupado por outro aplicativo.") }
        torchOwned = enabled
        // Não inventar sucesso do hardware enquanto o callback não o confirmar.
        torchState = "desconhecida"
        return "Solicitação enviada; consulte o estado confirmado pelo Android."
    }

    @Synchronized fun status(context: Context): JSONObject = JSONObject()
        .put("soundAllowed", SettingsStore.allowRemoteSound(context))
        .put("torchAllowed", SettingsStore.allowRemoteTorch(context))
        .put("lockAllowed", SettingsStore.allowRemoteLock(context))
        .put("lockAdminActive", RemoteScreenLock.adminActive(context))
        .put("lockSecure", RemoteScreenLock.secureLockConfigured(context))
        .put("lockSupported", RemoteScreenLock.supported(context))
        .put("soundActive", tone != null && SystemClock.elapsedRealtime() < soundUntilMs)
        .put("soundRemainingSeconds", if (tone == null) 0 else
            ((soundUntilMs - SystemClock.elapsedRealtime() + 999) / 1000).coerceAtLeast(0))
        .put("torchState", torchState)

    @Synchronized fun shutdown() {
        stopSound()
        stopOwnedTorch()
        val listener = callback
        if (listener != null) runCatching { torchManager?.unregisterTorchCallback(listener) }
        callback = null
        torchManager = null
        torchId = null
        torchState = "desconhecida"
        lastTorchRequestMs = 0L
    }
}
