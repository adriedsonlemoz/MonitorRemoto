package br.dev.monitorremoto.core

/** Pure gate: only the owner arms recovery; no remote request grants new permissions. */
object LocationRecoveryMode {
    fun canRecover(armed: Boolean, monitorEnabled: Boolean, permission: Boolean,
                   backgroundAccess: Boolean, locationOn: Boolean): Boolean =
        armed && monitorEnabled && permission && backgroundAccess && locationOn
}
