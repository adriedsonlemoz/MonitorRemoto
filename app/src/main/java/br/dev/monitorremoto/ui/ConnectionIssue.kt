package br.dev.monitorremoto.ui

import java.io.EOFException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.io.IOException
import javax.crypto.AEADBadTagException

/** Exibe hipóteses úteis sem afirmar que uma falha de rede prova versão incorreta. */
internal data class ConnectionIssue(
    val code: Code,
    val title: String,
    val explanation: String,
    val checks: List<String>,
    val technical: String,
    val remoteVersion: String? = null,
) {
    enum class Code { VERSION, TIMEOUT, REFUSED, HOST, CLOSED, AUTH, INVALID, UNKNOWN }

    companion object {
        fun from(error: Throwable): ConnectionIssue {
            val chain = generateSequence(error) { it.cause }.take(5).toList()
            val names = chain.mapNotNull { it.message }
            val detail = (names.firstOrNull { it.isNotBlank() } ?: error.javaClass.simpleName)
                .take(180).replace(Regex("[\\r\\n]+"), " ")
            val technical = "${error.javaClass.simpleName}: $detail"
            val versionText = names.firstOrNull { it.contains("Versões incompatíveis", ignoreCase = true) }
            if (versionText != null) {
                val remote = Regex("Versão recebida:\\s*([^\\s]+)", RegexOption.IGNORE_CASE)
                    .find(versionText)?.groupValues?.getOrNull(1)?.trimEnd('.')
                    ?.takeUnless { it == "não" || it == "não informada" }
                return ConnectionIssue(Code.VERSION, "Versões incompatíveis",
                    "Os dois celulares precisam executar a mesma versão do Monitor Remoto. A conexão foi bloqueada.",
                    listOf("Atualize o aplicativo nos dois aparelhos.", "Confira a versão mostrada no início do app."),
                    technical, remote)
            }
            return when {
                chain.any { it is SocketTimeoutException } -> ConnectionIssue(Code.TIMEOUT,
                    "Tempo de resposta esgotado", "O outro aparelho não respondeu dentro do prazo.",
                    listOf("Confira o Wi-Fi ou a VPN nos dois aparelhos.", "Verifique se o monitoramento remoto está ativo.", "Tente novamente."), technical)
                chain.any { it is ConnectException } -> ConnectionIssue(Code.REFUSED,
                    "Conexão recusada", "Não foi possível abrir uma conexão com o serviço remoto.",
                    listOf("Confira o endereço IP do aparelho.", "Verifique se o serviço Monitorado está em execução.", "Confira se há bloqueio da rede.") , technical)
                chain.any { it is UnknownHostException } -> ConnectionIssue(Code.HOST,
                    "Endereço não encontrado", "O nome ou endereço informado não pôde ser localizado.",
                    listOf("Confira o IP ou nome da VPN.", "Verifique se a rede está conectada."), technical)
                chain.any { it is EOFException } -> ConnectionIssue(Code.CLOSED,
                    "Conexão encerrada pelo aparelho", "O outro lado fechou a conexão antes de enviar uma resposta completa.",
                    listOf("Verifique se ambos usam a mesma versão (não foi possível confirmá-la).",
                        "Confira se a chave de pareamento continua válida.", "Confirme que o modo Monitorado está ativo."), technical)
                chain.any { it is AEADBadTagException || it is SecurityException } ||
                    detail.contains("autentica", ignoreCase = true) || detail.contains("chave", ignoreCase = true) ->
                    ConnectionIssue(Code.AUTH, "Falha de autenticação", "Não foi possível validar a resposta recebida.",
                        listOf("Confirme a chave de pareamento.", "Se a chave foi alterada, faça novo pareamento."), technical)
                error is IllegalArgumentException -> ConnectionIssue(Code.INVALID,
                    "Dados da conexão inválidos", "Alguma informação da conexão precisa ser corrigida.",
                    listOf("Verifique o IP ou nome da VPN.", "Confira o código ou a chave de pareamento."), technical)
                else -> ConnectionIssue(Code.UNKNOWN, "Não foi possível conectar",
                    "A consulta não foi concluída. O motivo exato ainda não foi identificado.",
                    listOf("Confira as versões instaladas.", "Verifique rede, IP e monitoramento ativo.", "Tente novamente."), technical)
            }
        }
    }
}
