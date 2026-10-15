package br.dev.monitorremoto.core

/** Lista fechada: nem shell/ADB genérico nem comandos de administração arbitrários. */
object DeviceControlPolicy {
    val durations = setOf(5, 10, 20, 30)
    val actions = setOf("status", "soundStart", "soundStop", "torchOn", "torchOff", "lockNow")
    const val LOCK_CONFIRMATION = "BLOQUEAR"

    fun valid(action: String, seconds: Int, confirmation: String = ""): Boolean = when (action) {
        "soundStart" -> seconds in durations && confirmation.isEmpty()
        "lockNow" -> seconds == 0 && confirmation == LOCK_CONFIRMATION
        in actions -> seconds == 0 && confirmation.isEmpty()
        else -> false
    }

    fun permitted(action: String, soundAllowed: Boolean, torchAllowed: Boolean,
        lockAllowed: Boolean = false): Boolean = when (action) {
        "status", "soundStop" -> true
        "soundStart" -> soundAllowed
        "torchOn", "torchOff" -> torchAllowed
        "lockNow" -> lockAllowed
        else -> false
    }
}
