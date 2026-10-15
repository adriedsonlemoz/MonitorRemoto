package br.dev.monitorremoto.core

import android.app.KeyguardManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/** Sem ADB, sem senha/biometria e sem permissões de excluir dados. */
object RemoteScreenLock {
    private val main = Handler(Looper.getMainLooper())
    private var lastRequestMs = 0L

    fun adminComponent(context: Context): ComponentName =
        ComponentName(context, MonitorLockAdminReceiver::class.java)

    fun adminActive(context: Context): Boolean =
        context.getSystemService(DevicePolicyManager::class.java)
            ?.isAdminActive(adminComponent(context)) == true

    fun secureLockConfigured(context: Context): Boolean =
        context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true

    fun supported(context: Context): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_DEVICE_ADMIN)

    fun requirement(context: Context): String? = when {
        !SettingsStore.isMonitorEnabled(context) -> "O monitoramento está desativado."
        !SettingsStore.allowRemoteLock(context) -> "Autorize o bloqueio remoto nas Configurações do monitorado."
        !supported(context) -> "Este Android não oferece administração de dispositivo compatível."
        !adminActive(context) -> "Ative o administrador de dispositivo na tela oficial do Android monitorado."
        !secureLockConfigured(context) -> "Configure um PIN, padrão ou senha de bloqueio no monitorado."
        else -> null
    }

    /** Confirma somente aceitação. lockNow() não informa se a tela foi efetivamente bloqueada. */
    @Synchronized fun requestLock(context: Context): String {
        val app = context.applicationContext
        check(requirement(app) == null) { requirement(app) ?: "Bloqueio indisponível." }
        val now = SystemClock.elapsedRealtime()
        check(lastRequestMs == 0L || now - lastRequestMs >= 15_000L) {
            "Aguarde pelo menos 15 segundos antes de outra solicitação."
        }
        lastRequestMs = now
        // Dar oportunidade à resposta autenticada sair antes de o Android apagar a tela.
        main.postDelayed({
            if (requirement(app) == null) {
                runCatching { app.getSystemService(DevicePolicyManager::class.java)?.lockNow() }
            }
        }, 650L)
        return "Solicitação aceita. O Android tentará bloquear a tela; a conclusão não é confirmada pela rede."
    }
}
