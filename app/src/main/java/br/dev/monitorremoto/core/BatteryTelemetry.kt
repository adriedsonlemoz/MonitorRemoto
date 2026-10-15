package br.dev.monitorremoto.core

/** Interpretação conservadora dos valores públicos de BatteryManager/Intent.ACTION_BATTERY_CHANGED.
 * Valores originais em BatteryManager: STATUS_UNKNOWN=1, CHARGING=2, DISCHARGING=3,
 * NOT_CHARGING=4, FULL=5; PLUGGED_AC=1, USB=2, WIRELESS=4 e DOCK=8.
 * A distinção NOT_CHARGING evita afirmar que há descarga quando o Android não confirmou isso.
 */
internal object BatteryTelemetry {
    fun percentage(level: Int, scale: Int): Int? =
        if (scale <= 0 || level < 0 || level > scale) null
        else ((level.toLong() * 100L + scale / 2L) / scale).toInt()

    fun state(status: Int): String = when (status) {
        2 -> "Carregando"
        3 -> "Descarregando"
        4 -> "Não carregando"
        5 -> "Cheia"
        else -> "Desconhecido"
    }

    /** `null` é deliberado para bateria desconectada, dados ausentes e fontes não identificadas. */
    fun powerSource(plugged: Int): String? = when (plugged) {
        1 -> "Tomada"
        2 -> "USB"
        4 -> "Sem fio"
        8 -> "Base" // BATTERY_PLUGGED_DOCK (API 33+), sem presumir o status de carga.
        else -> null
    }

    fun showChargingIndicator(status: Int, plugged: Int): Boolean =
        status == 2 || (powerSource(plugged) != null && status == 5)
}
