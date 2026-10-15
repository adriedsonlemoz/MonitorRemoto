package br.dev.monitorremoto.core

/** Faixas de interface orientativas da bateria, não limiares de segurança do fabricante. */
object BatteryTemperaturePolicy {
    enum class Zone { UNKNOWN, COLD, NORMAL, WARM, HOT }
    fun zone(celsius: Double?): Zone = when {
        celsius == null || !celsius.isFinite() || celsius < -30 || celsius > 100 -> Zone.UNKNOWN
        celsius < 15 -> Zone.COLD
        celsius < 38 -> Zone.NORMAL
        celsius < 43 -> Zone.WARM
        else -> Zone.HOT
    }
    fun label(zone: Zone): String = when (zone) {
        Zone.UNKNOWN -> "Sem leitura"
        Zone.COLD -> "Fria"
        Zone.NORMAL -> "Normal"
        Zone.WARM -> "Aquecendo"
        Zone.HOT -> "Quente"
    }
}
