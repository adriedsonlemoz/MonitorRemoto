package br.dev.monitorremoto.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import br.dev.monitorremoto.R
import br.dev.monitorremoto.transport.AppVersion

/** Mensagens reproduzíveis, sem detalhes crus repetidos no cabeçalho. */
internal fun MainActivity.connectionIssueCard(host: String): LinearLayout {
    val issue = remoteConnection.issue ?: ConnectionIssue(
        ConnectionIssue.Code.UNKNOWN, "Sem resposta", "Não foi possível concluir a consulta.",
        listOf("Confira o serviço remoto, endereço e rede."), "Falha não identificada")
    return LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        val activity = this@connectionIssueCard
        background = shape(fieldColor, 20, true)
        setPadding(dp(14), dp(16), dp(14), dp(14))
        addView(iconSectionHeader(
            if (issue.code == ConnectionIssue.Code.VERSION) R.drawable.ic_shield else R.drawable.ic_activity,
            issue.title,
            issue.explanation,
        ))
        sectionGap(this, 10)
        if (issue.code == ConnectionIssue.Code.VERSION) {
            addView(dialogMetricRow("Este celular", AppVersion.NAME))
            addView(dialogMetricRow("Outro celular", issue.remoteVersion ?: "Não identificada"))
        }
        addView(dialogMetricRow("Endereço", host.ifBlank { "Não configurado" }))
        addView(dialogMetricRow("Última tentativa", if (remoteConnection.lastAttemptMs > 0)
            formatDate(remoteConnection.lastAttemptMs) else "Não informada"))
        sectionGap(this, 8)
        addView(label("O que verificar", 12f, strong = true))
        issue.checks.forEach { check ->
            addView(label("• $check", 11.5f, muted = true).apply {
                setPadding(0, dp(2), 0, dp(2))
            })
        }
        sectionGap(this, 8)
        val buttons = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        buttons.addView(actionButton("Tentar novamente", emphasis = true) { refreshRemote(silent = false) }.apply {
            textSize = 11f; minWidth = 0; setPadding(dp(3), 0, dp(3), 0)
        }, LinearLayout.LayoutParams(0, dp(44), 1.15f))
        buttons.addView(View(activity), LinearLayout.LayoutParams(dp(7), 1))
        buttons.addView(actionButton("Editar conexão") { showManualConnectionDialog() }.apply {
            textSize = 11f; minWidth = 0; setPadding(dp(2), 0, dp(2), 0)
        }, LinearLayout.LayoutParams(0, dp(44), 1f))
        addView(buttons)
        sectionGap(this, 8)
        addView(iconMenuRow(R.drawable.ic_info, "Copiar diagnóstico", "Erro técnico: ${issue.technical.take(70)}") {
            val report = buildString {
                append("Monitor Remoto - diagnóstico de conexão\n")
                append("Versão local: ").append(AppVersion.NAME).append('\n')
                append("Versão remota: ").append(issue.remoteVersion ?: "não identificada").append('\n')
                append("Endereço: ").append(host).append('\n')
                append("Tentativa: ").append(if (remoteConnection.lastAttemptMs > 0) formatDate(remoteConnection.lastAttemptMs) else "N/D").append('\n')
                append("Situação: ").append(issue.title).append('\n')
                append("Detalhe: ").append(issue.technical)
            }
            (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                .setPrimaryClip(ClipData.newPlainText("Diagnóstico Monitor Remoto", report))
            message("Diagnóstico copiado, sem incluir a chave de pareamento.")
        })
    }
}
