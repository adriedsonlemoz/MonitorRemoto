package br.dev.monitorremoto.ui

import android.app.AlertDialog
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import br.dev.monitorremoto.R

/** Um único diálogo do aplicativo por Activity: evita menus e avisos empilhados. */
internal fun MainActivity.showManagedDialog(dialog: AlertDialog): AlertDialog {
    activeUiDialog?.takeIf { it !== dialog && it.isShowing }?.dismiss()
    activeUiDialog = dialog
    dialog.show()
    return dialog
}

internal fun MainActivity.showManagedDialog(builder: AlertDialog.Builder): AlertDialog =
    showManagedDialog(builder.create())

internal fun MainActivity.showAppNotice(
    title: String,
    description: String,
    icon: Int = R.drawable.ic_info,
    button: String = "Entendi",
) {
    val (wrapper, layout) = dialogWrapper()
    layout.addView(iconSectionHeader(icon, title))
    sectionGap(layout, 12)
    layout.addView(panel(13).apply {
        background = shape(fieldColor, 17, true)
        addView(label(description, 13.5f).apply { setPadding(0, 0, 0, 0) })
    })
    showManagedDialog(AlertDialog.Builder(this).setView(wrapper).setPositiveButton(button, null))
}

internal fun MainActivity.showAppConfirm(
    title: String,
    description: String,
    icon: Int = R.drawable.ic_shield,
    confirmText: String,
    onConfirm: () -> Unit,
) {
    val (wrapper, layout) = dialogWrapper()
    layout.addView(iconSectionHeader(icon, title))
    sectionGap(layout, 12)
    layout.addView(panel(13).apply {
        background = shape(fieldColor, 17, true)
        addView(label(description, 13.5f).apply { setPadding(0, 0, 0, 0) })
    })
    showManagedDialog(AlertDialog.Builder(this)
        .setView(wrapper).setNegativeButton("Cancelar", null)
        .setPositiveButton(confirmText) { _, _ -> onConfirm() })
}
