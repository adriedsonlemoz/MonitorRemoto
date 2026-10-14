package br.dev.monitorremoto.adb

/** Escolhe o canal de navegação sem confundir ADB com transporte de vídeo. */
object AdbScreenRouting {
    fun navigation(action: String, adbConnected: Boolean, screenAuthenticated: Boolean): AdbPolicy.Command? {
        if (!adbConnected || !screenAuthenticated) return null
        return when (action) {
            "back" -> AdbPolicy.Command.BACK
            "home" -> AdbPolicy.Command.HOME
            "recents" -> AdbPolicy.Command.RECENTS
            else -> null
        }
    }

    fun label(adbConnected: Boolean): String = if (adbConnected) {
        "Navegação: ADB conectado • Imagem: Monitor Remoto (MediaProjection)"
    } else {
        "Navegação: Monitor Remoto • Imagem: MediaProjection"
    }
}
