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

/** Diálogos compactos de tema único; alertas importantes mantêm confirmação explícita. */
private fun MainActivity.showThemedMessageDialog(
    title: String, description: String, icon: Int,
    primary: String, destructive: Boolean = false, secondary: String? = null,
    onPrimary: (() -> Unit)? = null,
) {
    val (wrapper, layout) = dialogWrapper()
    layout.background = shape(surface, 22, true)
    layout.setPadding(dp(18), dp(17), dp(18), dp(15))
    layout.addView(iconSectionHeader(icon, title))
    sectionGap(layout, 8)
    layout.addView(label(description, 14f).apply {
        setLineSpacing(dp(2).toFloat(), 1f)
    })
    sectionGap(layout, 14)
    lateinit var dialog: AlertDialog
    val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    if (secondary != null) {
        actions.addView(actionButton(secondary) { dialog.dismiss() },
            LinearLayout.LayoutParams(0, dp(46), 1f).apply { rightMargin = dp(8) })
    }
    val confirmButton = actionButton(primary, emphasis = secondary == null || !destructive) {
        dialog.dismiss()
        onPrimary?.invoke()
    }
    if (destructive) confirmButton.setTextColor(android.graphics.Color.rgb(188, 62, 55))
    actions.addView(confirmButton, LinearLayout.LayoutParams(0, dp(46), 1f))
    layout.addView(actions)
    dialog = themedPopup(this).setView(wrapper).create()
    showManagedDialog(dialog)
    dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
    dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.91f).toInt(), -2)
}

internal fun MainActivity.showAppNotice(
    title: String,
    description: String,
    icon: Int = R.drawable.ic_info,
    buttonText: String = "Entendi",
) = showThemedMessageDialog(title, description, icon, buttonText)

internal fun MainActivity.showAppConfirm(
    title: String,
    description: String,
    icon: Int = R.drawable.ic_shield,
    confirmText: String,
    onConfirm: () -> Unit,
) = showThemedMessageDialog(title, description, icon, confirmText,
    destructive = title.contains("excluir", true) || title.contains("apagar", true) || title.contains("revogar", true),
    secondary = "Cancelar", onPrimary = onConfirm)
