package br.dev.monitorremoto.adb

/** Parser isolado e testável para saída de leitura ADB. Não executa shell. */
object ThermalAdbParser {
    data class Reading(val name: String, val type: String, val celsius: Double)
    private val temperature = Regex("""Temperature\{mValue=([-+]?[0-9]+(?:\.[0-9]+)?),\s*mType=([0-9]+),\s*mName=([^,}]{1,90}),\s*mStatus=([0-9]+)\}""")
    fun parse(output: String): List<Reading> {
        // HAL atual é mais recente que cache. Não exibir entradas duplicadas.
        val hal = output.substringAfter("Current temperatures from HAL:", "")
            .substringBefore("Current cooling devices from HAL:")
        val cached = output.substringAfter("Cached temperatures:", "")
            .substringBefore("HAL Ready:")
        val source = if (temperature.containsMatchIn(hal)) hal else cached
        val distinct = linkedMapOf<String, Reading>()
        temperature.findAll(source.take(60_000)).forEach { match ->
            val c = match.groupValues[1].toDoubleOrNull() ?: return@forEach
            // -99.9 e zero são valores sentinelas frequentes para sensores inexistentes.
            if (!c.isFinite() || c <= -40.0 || c == 0.0 || c > 180.0) return@forEach
            val type = when (match.groupValues[2].toIntOrNull()) {
                0 -> "cpu"; 1 -> "gpu"; 2 -> "battery"; 3 -> "skin"
                4 -> "usb"; 5 -> "power"; 6 -> "bcl"; 7 -> "modem"
                8 -> "npu"; 9 -> "npu"; else -> "other"
            }
            val name = match.groupValues[3].trim().filter { it.isLetterOrDigit() || it in " _-./" }.take(68)
            if (name.isBlank()) return@forEach
            distinct["${type}:${name.lowercase()}"] = Reading(name, type, c)
        }
        return distinct.values.take(48)
    }
}
