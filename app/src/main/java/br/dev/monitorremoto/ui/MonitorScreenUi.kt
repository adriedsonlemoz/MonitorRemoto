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
    content.addView(label("Modo monitorado · este celular", 18f, strong = true))
    content.addView(label("Compartilhe dados somente com aparelhos autorizados.", 12f, muted = true))
    gap(9)
    val enabled = SettingsStore.isMonitorEnabled(this)
    val running = MonitorService.isRunning
    val card = panel(14)
    val columns = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.TOP
    }
    val details = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    details.addView(label("Este aparelho", 17f, strong = true))
    val state = when {
        !enabled -> "● Monitoramento inativo"
        running -> "● Monitoramento ativo"
        else -> "● Serviço sem execução"
    }
    details.addView(label(state, 13.5f, strong = true).apply {
        setTextColor(if (enabled && running) highlight else if (enabled) Color.rgb(190, 88, 61) else dim)
    })
    val preferred = preferredLocalAddress()
    details.addView(label("IP: ${preferred ?: "rede indisponível"}", 12.5f).apply {
        maxLines = 3
        ellipsize = android.text.TextUtils.TruncateAt.END
    })
    details.addView(label("Acesso autorizado por chave de pareamento.", 11f, muted = true))
    columns.addView(details, LinearLayout.LayoutParams(0, -2, 1f))
    columns.addView(View(this), LinearLayout.LayoutParams(dp(10), 1))

    val actions = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    fun addCompact(title: String, emphasize: Boolean = false, onClick: () -> Unit) {
        if (actions.childCount > 0) sectionGap(actions, 7)
        actions.addView(actionButton(title, emphasis = emphasize, onClick = onClick).apply {
            minWidth = 0
            textSize = 11.5f
            setPadding(dp(4), 0, dp(4), 0)
        }, LinearLayout.LayoutParams(-1, dp(46)))
    }
    if (enabled && running) {
        addCompact("Gerar QR", true) { chooseQrAddress() }
        addCompact("Mostrar info") { showMyConnectionInfo() }
        addCompact("Interromper") {
            SettingsStore.setMonitorEnabled(this, false)
            stopService(Intent(this, MonitorService::class.java))
            render()
        }
    } else {
        addCompact(if (enabled) "Retomar" else "Ativar", true) {
            if (enabled) startServiceExplicitly() else requestStart()
        }
        addCompact("Mostrar info") { showMyConnectionInfo() }
    }
    columns.addView(actions, LinearLayout.LayoutParams(dp(118), -2))
    card.addView(columns)
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

