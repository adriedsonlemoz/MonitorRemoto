package br.dev.monitorremoto.transport

/** Versão exata exigida dos dois aparelhos. Sincronizar com app/build.gradle.kts. */
object AppVersion {
    const val NAME = "0.1.0-alpha.69"
    fun compatible(remote: String?): Boolean = remote == NAME
    fun incompatibility(remote: String?): String =
        "Versões incompatíveis. Ambos os celulares precisam executar $NAME. " +
        "Versão recebida: ${remote?.takeIf { it.isNotBlank() } ?: "não informada"}."
}
