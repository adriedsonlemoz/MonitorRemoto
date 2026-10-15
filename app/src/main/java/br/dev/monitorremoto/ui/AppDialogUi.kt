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
    buttonText: String = "Entendi",
) {
    val (wrapper, layout) = dialogWrapper()
    layout.addView(iconSectionHeader(icon, title))
    sectionGap(layout, 12)
    layout.addView(panel(15).apply {
        background = shape(fieldColor, 18, true)
        addView(label(description, 14.2f).apply {
            setPadding(0, 0, 0, 0)
            setLineSpacing(dp(2).toFloat(), 1f)
        })
    })
    sectionGap(layout, 14)
    lateinit var dialog: AlertDialog
    layout.addView(button(buttonText, emphasis = true) { dialog.dismiss() })
    dialog = AlertDialog.Builder(this).setView(wrapper).create()
    showManagedDialog(dialog)
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
    layout.addView(panel(15).apply {
        background = shape(fieldColor, 18, true)
        addView(label(description, 14.2f).apply {
            setPadding(0, 0, 0, 0)
            setLineSpacing(dp(2).toFloat(), 1f)
        })
    })
    sectionGap(layout, 10)
    layout.addView(label("Confirme a ação abaixo. Você poderá voltar e mudar depois quando houver suporte.", 12.5f, muted = true))
    sectionGap(layout, 12)
    lateinit var dialog: AlertDialog
    layout.addView(button(confirmText, emphasis = true) {
        dialog.dismiss()
        onConfirm()
    })
    layout.addView(button("Cancelar") { dialog.dismiss() })
    dialog = AlertDialog.Builder(this).setView(wrapper).create()
    showManagedDialog(dialog)
}
