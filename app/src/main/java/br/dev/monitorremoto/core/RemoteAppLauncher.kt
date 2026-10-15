package br.dev.monitorremoto.core

import android.content.Context
import android.content.Intent

object RemoteAppLauncher {
    private var lastAttemptMs = 0L
    /** Android pode negar a abertura mesmo quando há uma Activity visível. Nunca afirmar sucesso visual. */
    @Synchronized fun request(context: Context, packageName: String): String {
        check(RemoteAppsPolicy.validPackage(packageName)) { "Pacote inválido ou protegido." }
        check(SettingsStore.isMonitorEnabled(context) && SettingsStore.shareInstalledApps(context) &&
            SettingsStore.allowRemoteAppLaunch(context)) { "Autorize Abrir aplicativos no monitorado." }
        check(VisibleMonitorUi.active) { "Tela do Monitor Remoto não está visível no monitorado. Use ADB autorizado." }
        val app = InstalledAppsReader.launchable(context)
        check((0 until app.length()).any { app.optJSONObject(it)?.optString("package") == packageName }) {
            "Aplicativo não está no catálogo autorizado."
        }
        val launch = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: error("Não existe Activity de inicialização disponível.")
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val now = android.os.SystemClock.elapsedRealtime()
        check(lastAttemptMs == 0L || now - lastAttemptMs >= 5_000L) { "Aguarde antes de solicitar outra abertura." }
        lastAttemptMs = now
        context.startActivity(launch)
        return "Pedido de abertura enviado ao Android; confira no monitorado."
    }
}
