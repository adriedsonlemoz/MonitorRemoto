package br.dev.monitorremoto.core

/** Estado de alerta de bateria/temperatura; limiares orientativos, não são limites OEM. */
object HealthAlertPolicy {
    enum class Level { NORMAL, WARNING, CRITICAL }
    data class Status(val level: Level, val reason: String)

    fun classify(percent: Int?, temperature: Double?, thermal: Int?, previous: Level = Level.NORMAL): Status {
        val severe = thermal != null && thermal >= 3
        val veryHot = temperature != null && temperature.isFinite() && temperature >= (if (previous == Level.CRITICAL) 40.0 else 43.0)
        val lowCritical = percent != null && percent in 0..(if (previous == Level.CRITICAL) 13 else 10)
        if (severe || veryHot || lowCritical) return Status(Level.CRITICAL,
            when { severe -> "Android em estado térmico severo"; veryHot -> "Bateria com temperatura elevada"; else -> "Bateria crítica" })
        val warming = temperature != null && temperature.isFinite() && temperature >= (if (previous == Level.WARNING) 35.0 else 38.0)
        val low = percent != null && percent in 0..(if (previous == Level.WARNING) 23 else 20)
        return if (warming || low || (thermal != null && thermal >= 2)) Status(Level.WARNING,
            when { warming -> "Bateria aquecendo"; low -> "Bateria baixa"; else -> "Sistema aquecendo" })
        else Status(Level.NORMAL, "Condição normal")
    }
    fun shouldProtect(thermal: Int?, level: Level, temperature: Double?): Boolean =
        (thermal != null && thermal >= 3) || (level == Level.CRITICAL && temperature != null && temperature.isFinite() && temperature >= 43.0)
}
