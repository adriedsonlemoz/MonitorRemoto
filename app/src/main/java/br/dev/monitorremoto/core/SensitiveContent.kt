package br.dev.monitorremoto.core

import java.util.Locale

/**
 * Defesa em profundidade. Uma lista de nomes/pacotes nunca identifica todos os bancos.
 * O acesso global do sistema ao NotificationListenerService permanece, se autorizado.
 */
object SensitiveContent {
    private val sensitive = Regex(
        """(?i)(\botp\b|\b2fa\b|token|c[oó]digo|verifica[cç][aã]o|senha|password|autentica[cç][aã]o|\bauthentication\b|\bauthenticat(?:e|ion|ing)\b|\bverification\b|\bverify\b|\bsecurity[\s-]?code\b|\bone[\s-]?time\b|\bpasscode\b|\brecovery[\s-]?code\b|\bconfirm(?:ation)?[\s-]?code\b|\bsign[\s-]?in\b|\btwo[\s-]?factor\b|banco|bank|pix|transfer[eê]ncia|cart[aã]o|conta banc[aá]ria|pagamento|login|acesso à conta)"""
    )
    private val financialName = Regex(
        """(?i)(\bnubank\b|\bcaixa\b|\bitau\b|\bitaú\b|\bbradesco\b|\bsantander\b|\bbanco\b|\bbank\b|\bsicoob\b|\bsicredi\b|\bpicpay\b|\bmercado pago\b|\bneon\b|\binter\b|\bc6 bank\b|\bbtg\b|\bpagbank\b)"""
    )
    private val financialPackages = setOf(
        "com.nu.production", // Nubank
        "br.com.gabba.Caixa", // CAIXA, nome histórico; não é exaustivo
        "br.com.bb.android", "com.itau", "com.bradesco", "com.santander.app"
    ).mapTo(mutableSetOf()) { it.lowercase(Locale.ROOT) }

    fun isSensitive(title: String, body: String): Boolean =
        sensitive.containsMatchIn(title) || sensitive.containsMatchIn(body)

    fun isBlockedFinancialApp(packageName: String, displayName: String): Boolean {
        val pkg = packageName.lowercase(Locale.ROOT)
        return pkg in financialPackages ||
            financialName.containsMatchIn(displayName) ||
            pkg.startsWith("com.nu.") || pkg.contains("bradesco") ||
            pkg.contains("santander") || pkg.contains("itau") ||
            pkg.contains("caixa") || pkg.contains("nubank")
    }
}
