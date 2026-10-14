package br.dev.monitorremoto.ui

import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import br.dev.monitorremoto.R
import br.dev.monitorremoto.transport.AuthenticatedAccess

internal fun IncomingAccessActivity.renderIncomingAccessScreen(
    peers: List<AuthenticatedAccess.Peer>,
    onStop: () -> Unit,
    onBackToMonitor: () -> Unit,
) {
    val main = peers.first()
    root.removeAllViews()
    root.addView(action("‹ Voltar ao aplicativo", false) { onBackToMonitor() },
        LinearLayout.LayoutParams(-1, dp(52)))
    root.addView(gap(14))
    root.addView(text("MONITOR REMOTO", 13.5f, bold = true).apply { setTextColor(accent) })
    root.addView(gap(20))

    val halo = LinearLayout(this).apply {
        background = shape(surface, 28)
        elevation = dp(1).toFloat()
        gravity = Gravity.CENTER
        setPadding(dp(22), dp(22), dp(22), dp(22))
        addView(icon(R.drawable.ic_shield, accent, 48))
    }
    root.addView(halo, LinearLayout.LayoutParams(dp(96), dp(96)).apply {
        gravity = Gravity.CENTER_HORIZONTAL
    })
    root.addView(gap(20))
    root.addView(text("Acesso remoto detectado", 27f, bold = true))
    root.addView(gap(8))
    root.addView(text(
        "Este celular recebeu uma consulta autenticada. Abaixo estão os dados mais recentes do aparelho que acessou o modo monitorado.",
        13.5f,
        subtle = true,
    ))
    root.addView(gap(20))

    root.addView(accessCard(main, "● ACESSO RECENTE", peers.size > 1, isPrimary = true))

    if (peers.size > 1) {
        root.addView(gap(16))
        root.addView(text("Outros acessos recentes", 16f, bold = true))
        root.addView(gap(4))
        root.addView(text("Todos usam a chave de pareamento autorizada deste aparelho.", 11.5f, subtle = true))
        peers.drop(1).forEach { peer ->
            root.addView(gap(10))
            root.addView(accessCard(peer, "● SECUNDÁRIO", false, isPrimary = false))
        }
    }

    root.addView(gap(18))
    root.addView(action("Parar acesso", true) { onStop() }, LinearLayout.LayoutParams(-1, dp(56)))
    root.addView(gap(12))
    root.addView(text(
        "Nome e método são informados pelo outro aparelho e servem como referência visual. Não representam identidade independente verificada.",
        11.5f,
        subtle = true,
    ))
}

private fun IncomingAccessActivity.accessCard(
    peer: AuthenticatedAccess.Peer,
    badge: String,
    showCountHint: Boolean,
    isPrimary: Boolean,
): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    background = shape(surface, 22, true)
    setPadding(dp(18), dp(18), dp(18), dp(18))

    val top = LinearLayout(this@accessCard).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    top.addView(text(badge, 11.5f, bold = true).apply { setTextColor(accent) }, LinearLayout.LayoutParams(0, -2, 1f))
    if (showCountHint) {
        top.addView(text("Mais aparelhos abaixo", 11f, subtle = true))
    }
    addView(top)
    addView(gap(10))
    addView(text(peer.name.ifBlank { "Aparelho não identificado" }, if (isPrimary) 20f else 17f, bold = true))
    addView(gap(14))

    addView(infoRow(
        infoMetric(R.drawable.ic_wifi, "IP de origem", peer.address),
        infoMetric(R.drawable.ic_shield, "Método informado", peer.method),
    ))
    addView(gap(10))
    addView(infoRow(
        infoMetric(R.drawable.ic_clock, "Primeira consulta", fmt(peer.sinceMs)),
        infoMetric(R.drawable.ic_clock, "Última consulta", fmt(peer.lastSeenMs)),
    ))
}

private fun IncomingAccessActivity.infoRow(
    first: LinearLayout,
    second: LinearLayout,
): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    addView(first, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(10) })
    addView(second, LinearLayout.LayoutParams(0, -2, 1f))
}

private fun IncomingAccessActivity.infoMetric(iconId: Int, label: String, value: String): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.TOP
        background = shape(this@infoMetric.background, 18)
        setPadding(dp(12), dp(12), dp(12), dp(12))
        minimumHeight = dp(74)
        addView(icon(iconId, accent, 19))
        addView(View(this@infoMetric), LinearLayout.LayoutParams(dp(10), 1))
        val column = LinearLayout(this@infoMetric).apply { orientation = LinearLayout.VERTICAL }
        column.addView(text(label, 10.5f, subtle = true))
        column.addView(gap(3))
        column.addView(text(value.ifBlank { "—" }, 13.5f, bold = true).apply {
            maxLines = 3
            ellipsize = TextUtils.TruncateAt.END
        })
        addView(column, LinearLayout.LayoutParams(0, -2, 1f))
    }
