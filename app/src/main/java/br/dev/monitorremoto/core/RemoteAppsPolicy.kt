package br.dev.monitorremoto.core

/** Pacotes ADB restritos: jamais aceitar texto arbitrário como shell. */
object RemoteAppsPolicy {
    private val packageRegex = Regex("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+")
    private val critical = setOf("android", "com.android.systemui", "com.android.settings",
        "com.google.android.gms", "com.google.android.gsf", "br.dev.monitorremoto")
    private val criticalPrefixes = listOf("com.android.", "com.miui.", "com.xiaomi.",
        "com.samsung.android.", "com.sec.android.")
    fun validPackage(value: String): Boolean = value.length in 3..160 && packageRegex.matches(value)
    fun canForceStop(value: String): Boolean = validPackage(value) && value !in critical &&
        criticalPrefixes.none { value.startsWith(it) }
    fun command(packageName: String, action: String): String {
        require(validPackage(packageName)) { "Pacote protegido ou inválido." }
        return when (action) {
            "open" -> "monkey -p $packageName -c android.intent.category.LAUNCHER 1"
            "settings" -> "am start -a android.settings.APPLICATION_DETAILS_SETTINGS -d package:$packageName"
            "stop" -> {
                require(canForceStop(packageName)) { "A parada de apps do sistema/Monitor Remoto é bloqueada." }
                "am force-stop $packageName"
            }
            else -> error("Ação não permitida.")
        }
    }
    fun pageSize() = 12
}
