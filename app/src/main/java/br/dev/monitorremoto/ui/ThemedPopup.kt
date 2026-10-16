package br.dev.monitorremoto.ui

import android.app.AlertDialog
import android.content.Context
import android.content.res.Configuration
import br.dev.monitorremoto.core.SettingsStore

/** Estilo de diálogo compartilhado por telas legadas e novas; preserva callbacks,
 * confirmação negativa e semântica das ações perigosas. */
internal fun themedPopup(context: Context): AlertDialog.Builder {
    val night = when (SettingsStore.theme(context)) {
        "dark" -> true
        "system" -> context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
        else -> false
    }
    return AlertDialog.Builder(context,
        if (night) android.R.style.Theme_Material_Dialog_Alert
        else android.R.style.Theme_Material_Light_Dialog_Alert)
}
