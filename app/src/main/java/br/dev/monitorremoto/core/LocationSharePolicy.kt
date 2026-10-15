package br.dev.monitorremoto.core

/** Perfis mínimos entre leituras; o Android pode atrasar ou agrupar atualizações. */
object LocationSharePolicy {
    const val ECONOMICO = "economico"
    const val EQUILIBRADO = "equilibrado"
    const val PRECISO = "preciso"
    fun intervalMs(profile: String): Long = when (profile) {
        PRECISO -> 10_000L
        EQUILIBRADO -> 60_000L
        else -> 300_000L
    }
    fun minDistanceM(profile: String): Float = when (profile) {
        PRECISO -> 5f
        EQUILIBRADO -> 15f
        else -> 40f
    }
    fun label(profile: String): String = when (profile) {
        PRECISO -> "Preciso · 10 s"
        EQUILIBRADO -> "Equilibrado · 60 s"
        else -> "Econômico · 5 min"
    }
    fun coordinatesValid(lat: Double, lon: Double): Boolean =
        lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 && lon in -180.0..180.0
    fun usableAgeMs(age: Long): Boolean = age in 0..1_800_000L
}
