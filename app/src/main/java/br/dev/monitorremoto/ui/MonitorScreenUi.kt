package br.dev.monitorremoto.ui

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import br.dev.monitorremoto.core.SettingsStore
import br.dev.monitorremoto.service.MonitorService
import br.dev.monitorremoto.transport.AuthenticatedAccess

/** Alpha.22: módulo de interface extraído de MainActivity. Sem estado de conexão próprio. */

/** Tela monitorada: ações agrupadas à direita do cartão, sem botões longos. */
internal fun MainActivity.renderMonitor() {
    content.addView(label("Modo monitorado", 21f, strong = true))
    content.addView(label("Compartilhe dados somente com aparelhos autorizados.", 12f, muted = true))
    gap(13)
    val enabled = SettingsStore.isMonitorEnabled(this)
    val running = MonitorService.isRunning
    val card = panel(17).apply { background = shape(surface, 24, true) }
    val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    val iconHolder = LinearLayout(this).apply {
        gravity = Gravity.CENTER
        background = shape(fieldColor, 17)
        addView(vectorIcon(br.dev.monitorremoto.R.drawable.ic_phone, 31, highlight))
    }
    header.addView(iconHolder, LinearLayout.LayoutParams(dp(62), dp(62)))
    header.addView(View(this), LinearLayout.LayoutParams(dp(12), 1))
    val headerText = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    headerText.addView(label("Este aparelho", 17f, strong = true).apply { setPadding(0, 0, 0, 0) })
    val state = when {
        !enabled -> "● Monitoramento inativo"
        running -> "● Monitoramento ativo"
        else -> "● Serviço sem execução"
    }
    headerText.addView(label(state, 12.5f, strong = true).apply {
        setPadding(0, dp(6), 0, 0)
        setTextColor(if (enabled && running) highlight else if (enabled) Color.rgb(190, 88, 61) else dim)
    })
    header.addView(headerText, LinearLayout.LayoutParams(0, -2, 1f))
    card.addView(header)
    sectionGap(card, 14)

    val columns = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
    val details = iconMetric("IP da rede", preferredLocalAddress() ?: "Indisponível", br.dev.monitorremoto.R.drawable.ic_wifi)
    columns.addView(details, LinearLayout.LayoutParams(0, -2, 1f))
    columns.addView(View(this), LinearLayout.LayoutParams(dp(9), 1))
    val access = iconMetric("Acesso", "Chave autorizada", br.dev.monitorremoto.R.drawable.ic_shield)
    columns.addView(access, LinearLayout.LayoutParams(0, -2, 1f))
    card.addView(columns)
    sectionGap(card, 16)

    val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
    fun addCompact(title: String, icon: Int, emphasize: Boolean = false, onClick: () -> Unit) {
        if (actions.childCount > 0) actions.addView(View(this), LinearLayout.LayoutParams(dp(8), 1))
        val tile = iconTile(icon, title, featured = emphasize, onClick = onClick).apply { minimumHeight = dp(102) }
        actions.addView(tile, LinearLayout.LayoutParams(0, -2, 1f))
    }
    if (enabled && running) {
        addCompact("Gerar QR", br.dev.monitorremoto.R.drawable.ic_qr, true) { chooseQrAddress() }
        addCompact("Mostrar info", br.dev.monitorremoto.R.drawable.ic_info) { showMyConnectionInfo() }
        addCompact("Interromper", br.dev.monitorremoto.R.drawable.ic_power) {
            SettingsStore.setMonitorEnabled(this, false)
            stopService(Intent(this, MonitorService::class.java))
            render()
        }
    } else {
        addCompact(if (enabled) "Retomar" else "Ativar", br.dev.monitorremoto.R.drawable.ic_power, true) {
            if (enabled) startServiceExplicitly() else requestStart()
        }
        addCompact("Mostrar info", br.dev.monitorremoto.R.drawable.ic_info) { showMyConnectionInfo() }
    }
    card.addView(actions)
    content.addView(card)
    gap(12)
    if (enabled && running) renderIncomingAccess()
    else content.addView(label("Aguardando ativação do monitoramento.", 12f, muted = true))
    gap(10)
    addText("Permissões e segurança: ⚙ Configurações.", 11f, muted = true)
}

/** Event-driven on authenticated requests, plus timeout refresh for disconnected clients. */
private fun MainActivity.renderIncomingAccess() {
    val peers = AuthenticatedAccess.recent()
    val card = panel(12)
    if (peers.isEmpty()) {
        card.addView(label("● Aguardando aparelho", 14f, strong = true).apply { setTextColor(dim) })
        card.addView(label("Nenhuma consulta autenticada nos últimos 45 segundos.", 11f, muted = true))
    } else {
        card.addView(label("● ${if (peers.size == 1) "Aparelho conectado recentemente" else "${peers.size} aparelhos com acesso recente"}", 14f, strong = true).apply {
            setTextColor(highlight)
        })
        card.addView(label("Atividade detectada por consultas autenticadas, sem sessão permanente.", 10.5f, muted = true))
        peers.forEach { peer ->
            sectionGap(card, 6)
            val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val data = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            data.addView(label(peer.name.ifBlank { "Aparelho não identificado" }, 13f, strong = true).apply {
                maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
            })
            data.addView(label("${peer.address} · ${peer.method}", 11f, muted = true).apply {
                maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END
            })
            line.addView(data, LinearLayout.LayoutParams(0, -2, 1f))
            line.addView(actionButton("Ações") { showIncomingAccessActions(peer) }.apply {
                textSize = 11f; minWidth = 0
                setPadding(dp(3), 0, dp(3), 0)
            }, LinearLayout.LayoutParams(dp(78), dp(40)))
            card.addView(line)
        }
    }
    content.addView(card)
}

private fun MainActivity.showIncomingAccessActions(peer: AuthenticatedAccess.Peer) {
    val (wrapper, layout) = dialogWrapper()
    dialogIntro(layout, "Identidade e método declarados pelo cliente autenticado; não constituem verificação independente.")
    layout.addView(dialogMetricRow("Nome informado", peer.name.ifBlank { "Não informado" }))
    layout.addView(dialogMetricRow("IP de origem", peer.address))
    layout.addView(dialogMetricRow("Método informado", peer.method))
    layout.addView(dialogMetricRow("Último acesso", formatDate(peer.lastSeenMs)))
    layout.addView(dialogMetricRow("Primeiro acesso", formatDate(peer.sinceMs)))
    sectionGap(layout, 8)
    layout.addView(label("O acesso não é uma sessão TCP contínua. Interromper ou trocar a chave afeta todos os controladores.", 11f, muted = true))
    val dialog = AlertDialog.Builder(this)
        .setTitle("Acesso autenticado")
        .setView(wrapper)
        .setNeutralButton("Trocar chave") { _, _ -> confirmRotateMonitorKey() }
        .setNegativeButton("Fechar", null)
        .setPositiveButton("Parar serviço") { _, _ ->
            AlertDialog.Builder(this)
                .setTitle("Interromper monitoramento?")
                .setMessage("Todos os controladores perderão acesso aos dados enquanto o serviço estiver parado.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Interromper") { _, _ ->
                    SettingsStore.setMonitorEnabled(this, false)
                    stopService(Intent(this, MonitorService::class.java))
                    render()
                }.show()
        }.create()
    dialog.show()
}

