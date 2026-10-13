package br.dev.monitorremoto.core

/** Sugere apenas octetos integralmente cobertos pela máscara, sem assumir /24. */
object Ipv4Suggestion {
    fun prefix(address: String, prefixLength: Int): String? {
        if (prefixLength !in 8..30) return null
        val parts = address.split('.')
        if (parts.size != 4 || parts.any { token ->
                val octet = token.toIntOrNull()
                token.isEmpty() || token.length > 3 ||
                    token.any { c -> !c.isDigit() } || octet == null || octet !in 0..255
            }) return null
        val bytes = prefixLength / 8
        return if (bytes in 1..3) parts.take(bytes).joinToString(".") + "." else null
    }
}
