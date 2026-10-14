package br.dev.monitorremoto.core

/** Filtros conservadores locais. Não são garantia de detectar toda informação privada. */
object MessagePolicy {
    fun validReplyAddress(address: String): Boolean = address.matches(Regex("\\+?[0-9]{8,15}"))

    fun looksLikeAuthenticationSms(text: String): Boolean {
        val lowered = text.trim()
        if (SensitiveContent.isSensitive("", lowered)) return true
        // Alguns OTPs vêm somente com uma sequência numérica, sem palavra-chave.
        if (lowered.matches(Regex("(?:[0-9]\\s?){4,8}"))) return true
        return false
    }
}
