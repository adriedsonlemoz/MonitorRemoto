package br.dev.monitorremoto.core

/** O monitorado só permite tentativas de iniciar apps quando sua UI está visível. */
object VisibleMonitorUi {
    @Volatile var active: Boolean = false
        private set
    fun changed(visible: Boolean) { active = visible }
}
