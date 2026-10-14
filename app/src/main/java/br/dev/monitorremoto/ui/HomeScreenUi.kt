package br.dev.monitorremoto.ui

import android.app.AlertDialog
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import br.dev.monitorremoto.R
import br.dev.monitorremoto.core.RecentDevices
import br.dev.monitorremoto.core.SettingsStore
import br.dev.monitorremoto.service.MonitorService
import br.dev.monitorremoto.transport.AuthenticatedAccess
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Alpha.27: Home segue os cartões vetoriais e os agrupamentos do painel remoto. */
internal fun MainActivity.renderHome() {
    val recentAccess = AuthenticatedAccess.recent()
    if (recentAccess.isNotEmpty() && SettingsStore.isMonitorEnabled(this) && MonitorService.isRunning) {
        val accessCard = panel(15)
        accessCard.addView(iconSectionHeader(R.drawable.ic_shield, "Acesso remoto recente", "${recentAccess.size} aparelho(s) consultaram este celular"))
        sectionGap(accessCard, 12)
        accessCard.addView(iconMenuRow(R.drawable.ic_phone, "Ver acessos", "Identificar consultas autorizadas") {
            mode = 0
            render()
        })
        content.addView(accessCard)
        gap(12)
    }

    content.addView(label("Seus aparelhos", 21f, strong = true))
    content.addView(label("Escolha como usar o Monitor Remoto.", 12f, muted = true))
    gap(14)
    val shortcuts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    twoColumnTiles(shortcuts, listOf(
        iconTile(R.drawable.ic_qr, "Conectar novo aparelho", "QR Code ou IP", featured = true) {
            showConnectionChooser()
        },
        iconTile(R.drawable.ic_phone, "Monitorar este aparelho", "Compartilhar dados") {
            mode = 0
            render()
        }
    ))
    content.addView(shortcuts)
    gap(15)

    val recent = RecentDevices.list(this)
    val listPanel = panel(16).apply { background = shape(surface, 24, true) }
    listPanel.addView(iconSectionHeader(R.drawable.ic_phone, "Aparelhos anteriores", "Conexões salvas neste celular"))
    sectionGap(listPanel, 14)
    if (recent.isEmpty()) {
        val empty = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = shape(fieldColor, 20)
            setPadding(dp(18), dp(24), dp(18), dp(24))
        }
        empty.addView(vectorIcon(R.drawable.ic_qr, 32, dim))
        empty.addView(label("Nenhum aparelho salvo", 14f, strong = true).apply { gravity = Gravity.CENTER })
        empty.addView(label("Use Conectar novo aparelho para iniciar um pareamento.", 11.5f, muted = true).apply {
            gravity = Gravity.CENTER
        })
        listPanel.addView(empty)
    } else {
        recent.forEachIndexed { index, item ->
            if (index != 0) sectionGap(listPanel, 10)
            val device = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = shape(fieldColor, 20, true)
                setPadding(dp(13), dp(13), dp(13), dp(13))
            }
            val heading = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val iconHolder = LinearLayout(this).apply {
                gravity = Gravity.CENTER
                background = shape(surface, 14, true)
                addView(vectorIcon(R.drawable.ic_phone, 24, highlight))
            }
            heading.addView(iconHolder, LinearLayout.LayoutParams(dp(46), dp(46)))
            heading.addView(View(this), LinearLayout.LayoutParams(dp(10), 1))
            val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            labels.addView(label(item.name, 14.5f, strong = true).apply {
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(0, 0, 0, 0)
            })
            labels.addView(label(item.host, 11.5f, muted = true).apply { setPadding(0, dp(4), 0, 0) })
            heading.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
            device.addView(heading)
            val lastUsed = if (item.lastSuccessMs > 0L)
                SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(item.lastSuccessMs))
                else "ainda sem conexão confirmada"
            device.addView(label("Última conexão: $lastUsed", 11f, muted = true).apply { setPadding(0, dp(7), 0, dp(8)) })
            val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            buttons.addView(actionButton("Conectar", emphasis = true) { connectSavedDevice(item) },
                LinearLayout.LayoutParams(0, dp(44), 1f))
            buttons.addView(View(this), LinearLayout.LayoutParams(dp(9), 1))
            val settings = ImageView(this).apply {
                setImageResource(R.drawable.ic_settings)
                imageTintList = ColorStateList.valueOf(ink)
                setPadding(dp(12), dp(12), dp(12), dp(12))
                background = shape(surface, 14, true)
                contentDescription = "Gerenciar ${item.name}: renomear ou esquecer"
                isClickable = true
                isFocusable = true
                setOnClickListener { showHomeSavedDeviceOptions(item) }
            }
            buttons.addView(settings, LinearLayout.LayoutParams(dp(44), dp(44)))
            device.addView(buttons)
            listPanel.addView(device)
        }
    }
    content.addView(listPanel)
}

/** Opções de um aparelho ficam em cartões com ícones, sem lista nativa simples. */
private fun MainActivity.showHomeSavedDeviceOptions(item: RecentDevices.Device) {
    val (wrapper, layout) = dialogWrapper()
    dialogIntro(layout, "${item.name} · ${item.host}")
    var popup: AlertDialog? = null
    layout.addView(iconMenuRow(R.drawable.ic_phone, "Conectar", "Abrir este aparelho salvo") {
        popup?.dismiss()
        connectSavedDevice(item)
    })
    sectionGap(layout, 9)
    layout.addView(iconMenuRow(R.drawable.ic_info, "Renomear", "Alterar o nome do aparelho salvo") {
        popup?.dismiss()
        renameSavedDevice(item, returnToChooser = false)
    })
    sectionGap(layout, 9)
    layout.addView(iconMenuRow(R.drawable.ic_logout, "Esquecer aparelho", "Remover credencial e histórico local", dangerous = true) {
        popup?.dismiss()
        forgetSavedDevice(item, returnToChooser = false)
    })
    popup = AlertDialog.Builder(this)
        .setTitle("Gerenciar aparelho")
        .setView(wrapper)
        .setNegativeButton("Fechar", null)
        .create()
    popup?.let { showManagedDialog(it) }
}
