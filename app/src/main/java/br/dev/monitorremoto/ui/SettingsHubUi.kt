package br.dev.monitorremoto.ui

import android.content.Intent
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.TextView
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import br.dev.monitorremoto.R
import java.text.Normalizer
import java.util.Locale

/** Alpha.88: índice pesquisável, sem mudar consentimentos nem abrir permissões automaticamente. */
private data class SettingsDestination(
    val icon: Int, val title: String, val hint: String, val section: String,
)
private data class SettingsGroup(val title: String, val entries: List<SettingsDestination>)

private val settingGroups = listOf(
    SettingsGroup("SEGURANÇA E PERMISSÕES", listOf(
        SettingsDestination(R.drawable.ic_shield, "Segurança e pareamento", "Chaves, dispositivos e revogação", "security"),
        SettingsDestination(R.drawable.ic_shield, "Bloqueio remoto", "Permissão e confirmação para bloquear", "lock"),
        SettingsDestination(R.drawable.ic_activity, "Diagnóstico", "Status do servidor e permissões", "diagnostics"),
        SettingsDestination(R.drawable.ic_shield, "Modo avançado ADB", "Conexão ADB local e autorizada", "adb"),
    )),
    SettingsGroup("RECUPERAÇÃO E LOCALIZAÇÃO", listOf(
        SettingsDestination(R.drawable.ic_pin, "Localização", "Recuperação automática e GPS", "location"),
        SettingsDestination(R.drawable.ic_power, "Serviço e recuperação", "Inicialização, retomada e parada", "service"),
        SettingsDestination(R.drawable.ic_flashlight, "Som e lanterna", "Autorizações para localizar aparelho", "physical"),
    )),
    SettingsGroup("CÂMERA E TELA", listOf(
        SettingsDestination(R.drawable.ic_remote_camera, "Câmera e fotografia", "Permissão da câmera e sessão autorizada", "physical"),
        SettingsDestination(R.drawable.ic_monitor, "Tela e controle remoto", "Compartilhamento, toque e áudio", "screen"),
    )),
    SettingsGroup("ARQUIVOS E APLICATIVOS", listOf(
        SettingsDestination(R.drawable.ic_folder_download, "Arquivos e armazenamento", "Pasta compartilhada, leitura e escrita", "files"),
        SettingsDestination(R.drawable.ic_phone, "Aplicativos instalados", "Compartilhamento da lista e abertura", "apps"),
    )),
    SettingsGroup("ALERTAS E COMUNICAÇÃO", listOf(
        SettingsDestination(R.drawable.ic_bell, "Notificações", "Acesso, seleção e respostas", "notifications"),
        SettingsDestination(R.drawable.ic_message, "SMS e telefonia", "Consulta, envio e chips", "sms"),
        SettingsDestination(R.drawable.ic_thermometer, "Alertas e proteção", "Bateria, temperatura e saúde", "health"),
    )),
    SettingsGroup("APARÊNCIA E APLICATIVO", listOf(
        SettingsDestination(R.drawable.ic_palette, "Aparência", "Temas claro, escuro e sistema", "appearance"),
        SettingsDestination(R.drawable.ic_info, "Novidades da versão", "Mudanças nesta atualização", "news"),
        SettingsDestination(R.drawable.ic_info, "Sobre", "Versão e privacidade", "about"),
    )),
)

private fun folded(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT).trim()

internal fun MainActivity.renderSettingsHome() {
    val header = panel(14)
    header.addView(label("Configure este celular", 15f, strong = true))
    header.addView(label("As autorizações são concedidas no próprio aparelho. O controlador não pode ativá-las por aqui.", 11.5f, muted = true))
    content.addView(header)
    sectionGap(content, 10)

    val search = EditText(this).apply {
        hint = "Buscar: GPS, câmera, arquivos..."
        textSize = 14f
        setTextColor(ink)
        setHintTextColor(dim)
        background = shape(fieldColor, 14, true)
        inputType = android.text.InputType.TYPE_CLASS_TEXT
        imeOptions = EditorInfo.IME_ACTION_SEARCH
        isSingleLine = true
        setPadding(dp(13), dp(8), dp(13), dp(8))
        contentDescription = "Pesquisar configurações"
    }
    content.addView(search, LinearLayout.LayoutParams(-1, dp(48)))
    sectionGap(content, 10)
    val results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    content.addView(results)
    fun update(query: String) {
        results.removeAllViews()
        val normalized = folded(query)
        var count = 0
        settingGroups.forEach { group ->
            val matches = group.entries.filter { entry ->
                normalized.isBlank() || folded("${group.title} ${entry.title} ${entry.hint}").contains(normalized)
            }
            if (matches.isEmpty()) return@forEach
            count += matches.size
            val card = panel(12)
            card.addView(label(group.title, 11f, strong = true, muted = true))
            sectionGap(card, 6)
            matches.forEachIndexed { index, item ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    background = shape(fieldColor, 13, true)
                    setPadding(dp(11), dp(9), dp(11), dp(9))
                    minimumHeight = dp(64)
                    isClickable = true
                    isFocusable = true
                    contentDescription = "${item.title}. ${item.hint}"
                    setOnClickListener {
                        if (item.section == "adb") startActivity(Intent(this@renderSettingsHome, AdbActivity::class.java))
                        else openSettingsSection(item.section)
                    }
                }
                row.addView(vectorIcon(item.icon, 21, highlight))
                row.addView(View(this), LinearLayout.LayoutParams(dp(11), 1))
                val text = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                text.addView(label(item.title, 13.5f, strong = true).apply { setPadding(0, 0, 0, 0) })
                text.addView(label(item.hint, 10.5f, muted = true).apply { setPadding(0, dp(2), 0, 0); maxLines = 2 })
                row.addView(text, LinearLayout.LayoutParams(0, -2, 1f))
                row.addView(label("›", 21f, muted = true))
                card.addView(row)
                if (index < matches.lastIndex) sectionGap(card, 7)
            }
            results.addView(card)
            sectionGap(results, 10)
        }
        if (count == 0) results.addView(label("Nenhuma configuração encontrada. Tente outra palavra.", 13f, muted = true))
    }
    search.addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = update(s?.toString().orEmpty())
        override fun afterTextChanged(s: Editable?) = Unit
    })
    update("")
}
