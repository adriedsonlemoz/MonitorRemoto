package br.dev.monitorremoto.ui

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import br.dev.monitorremoto.capture.RemoteTouchService
import br.dev.monitorremoto.capture.ScreenFrameStore
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.content.res.ColorStateList
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import br.dev.monitorremoto.R
import br.dev.monitorremoto.core.SettingsStore
import br.dev.monitorremoto.core.SensitiveContent
import br.dev.monitorremoto.security.CryptoBox
import br.dev.monitorremoto.security.SecretVault
import br.dev.monitorremoto.service.MonitorService
import br.dev.monitorremoto.transport.AppVersion
import br.dev.monitorremoto.transport.AuthenticatedAccess
import br.dev.monitorremoto.transport.PairingSession
import br.dev.monitorremoto.transport.WireProtocol
import java.util.Locale

/** Alpha.32: configurações são uma página navegável, não um AlertDialog gigante. */
internal fun MainActivity.showSettingsDialog() = openSettingsModule()

internal fun MainActivity.renderSettingsModule() {
    val labelBySection = mapOf(
        "notifications" to "Notificações",
        "sms" to "SMS e telefonia",
        "service" to "Serviço e recuperação",
        "screen" to "Tela e controle remoto",
        "appearance" to "Aparência",
        "security" to "Segurança",
        "diagnostics" to "Diagnóstico",
        "about" to "Sobre"
    )
    val section = settingsSection
    val title = labelBySection[section]
    val top = panel(16)
    top.addView(iconSectionHeader(R.drawable.ic_settings, title ?: "Configurações",
        if (title == null) "Escolha o que deseja ajustar neste aparelho" else "As alterações são feitas neste aparelho"))
    if (title != null) {
        sectionGap(top, 12)
        top.addView(actionButton("‹ Voltar às categorias") { openSettingsSection("home") })
    }
    content.addView(top)
    sectionGap(content, 13)
    if (section == "home") {
        val groupA = panel(15)
        groupA.addView(label("PRIVACIDADE E PERMISSÕES", 11.5f, strong = true, muted = true))
        sectionGap(groupA, 10)
        settingsEntry(groupA, R.drawable.ic_bell, "Notificações",
            "Escolher aplicativos e permitir respostas", "notifications")
        settingsEntry(groupA, R.drawable.ic_message, "SMS e telefonia",
            "Ler, receber, responder e selecionar chip", "sms")
        settingsEntry(groupA, R.drawable.ic_monitor, "Tela e controle remoto",
            "Compartilhar, controlar toque e capturar áudio", "screen")
        content.addView(groupA)
        sectionGap(content, 12)
        val groupB = panel(15)
        groupB.addView(label("APARELHO E MANUTENÇÃO", 11.5f, strong = true, muted = true))
        sectionGap(groupB, 10)
        settingsEntry(groupB, R.drawable.ic_power, "Serviço e recuperação",
            "Inicialização, retomada e funcionamento", "service")
        settingsEntry(groupB, R.drawable.ic_activity, "Diagnóstico",
            "Conexão, permissões e estado do servidor", "diagnostics")
        content.addView(groupB)
        sectionGap(content, 12)
        val groupC = panel(15)
        groupC.addView(label("APLICATIVO", 11.5f, strong = true, muted = true))
        sectionGap(groupC, 10)
        settingsEntry(groupC, R.drawable.ic_shield, "Segurança e pareamento", "Credenciais e revogação", "security")
        settingsEntry(groupC, R.drawable.ic_palette, "Aparência", "Tema claro, escuro ou sistema", "appearance")
        settingsEntry(groupC, R.drawable.ic_info, "Sobre", "Versão e privacidade", "about", false)
        content.addView(groupC)
    } else {
        val page = panel(15)
        when (section) {
            "notifications" -> fillNotificationSettings(page)
            "sms" -> fillSmsSettings(page)
            "screen" -> fillScreenSettings(page)
            "service" -> fillServiceSettings(page)
            "security" -> fillSecuritySettings(page)
            "appearance" -> fillAppearanceSettings(page)
            "diagnostics" -> fillDiagnosticsSettings(page)
            "about" -> fillAboutSettings(page)
            else -> page.addView(label("Categoria não encontrada", 13f))
        }
        content.addView(page)
    }
    sectionGap(content, 16)
    content.addView(label("Monitor Remoto · ${AppVersion.NAME}", 11f, muted = true))
}

private fun MainActivity.settingsEntry(
    into: LinearLayout, icon: Int, title: String, subtitle: String, section: String, divider: Boolean = true
) {
    into.addView(iconMenuRow(icon, title, subtitle) { openSettingsSection(section) })
    if (divider) sectionGap(into, 9)
}

private fun MainActivity.fillNotificationSettings(layout: LinearLayout) {
    val state = if (hasListener()) "Autorizado" else "Sem acesso"
    val count = SettingsStore.notificationAllowlist(this).size
    dialogIntro(layout, "O monitoramento da bateria funciona sem esta permissão. As notificações só são enviadas para apps autorizados e não financeiros.")
    layout.addView(iconMenuRow(R.drawable.ic_bell, "Acesso às notificações", state) {
        showAppConfirm(
            "Acesso às notificações",
            "O Android poderá mostrar notificações sensíveis. Somente aplicativos escolhidos serão transmitidos, com filtros que podem falhar. O acesso pode ser revogado nas Configurações do Android.",
            R.drawable.ic_bell, "Configurar no Android"
        ) { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
    })
    sectionGap(layout, 8)
    val allApps = SettingsStore.allNotifications(this)
    layout.addView(iconMenuRow(R.drawable.ic_bell, "Todas as notificações elegíveis",
        if (allApps) "Autorizadas (desativar)" else "Desativadas (ativar)") {
        if (allApps) {
            SettingsStore.setAllNotifications(this, false)
            message("Acesso amplo desativado. Apenas a lista de aplicativos individuais continua valendo.")
        } else showAppConfirm("Autorizar notificações de todos os apps?",
            "As notificações de aplicativos não financeiros poderão ser consultadas por aparelhos com a chave. Conteúdo sensível ainda será filtrado, sem garantia absoluta. O acesso pode ser revogado aqui.",
            R.drawable.ic_bell, "Autorizar todos") {
            SettingsStore.setAllNotifications(this, true)
            message("Autorização ampla ativada no monitorado.")
        }
    })
    sectionGap(layout, 8)
    layout.addView(iconMenuRow(R.drawable.ic_phone, "Aplicativos autorizados",
        if (allApps) "Todos elegíveis · lista individual ainda disponível" else "$count selecionado(s)") { selectNotificationApps() })
    sectionGap(layout, 8)
    val replyOn = SettingsStore.allowNotificationReplies(this)
    layout.addView(iconMenuRow(R.drawable.ic_message, "Responder notificações", if (replyOn) "Habilitado (desativar)" else "Desativado (ativar)") {
        if (replyOn) {
            SettingsStore.setAllowNotificationReplies(this, false)
            message("Respostas por notificações desativadas. Tokens pendentes apagados.")
        } else showAppConfirm("Autorizar respostas remotas?",
            "Aparelhos com a chave de pareamento poderão enviar respostas usando ações de notificações dos apps que você selecionou. A mensagem será sempre digitada e confirmada no controlador. A permissão de notificações e a lista de apps continuam necessárias.",
            R.drawable.ic_message, "Habilitar") {
            SettingsStore.setAllowNotificationReplies(this, true)
            message("Respostas ativadas para novas notificações elegíveis.")
        }
    })
    sectionGap(layout, 8)
}

private fun MainActivity.fillSmsSettings(layout: LinearLayout) {
    dialogIntro(layout, "A leitura e o envio de SMS dependem da autorização local e das permissões do Android. Ative somente o necessário.")
    val smsRead = SettingsStore.shareSmsInbox(this) &&
        checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
    layout.addView(iconMenuRow(R.drawable.ic_message, "Ler caixa de SMS", if (smsRead) "Habilitado (desativar)" else "Desativado (ativar)") {
        if (smsRead) {
            SettingsStore.setShareSmsInbox(this, false)
            SettingsStore.setAllowSmsReplies(this, false)
            SettingsStore.setReceiveSmsEvents(this, false)
            message("Compartilhamento da caixa de SMS e respostas SMS desativados.")
        } else showAppConfirm("Compartilhar SMS recebidos?",
            "Serão consultados sob demanda até 30 SMS recentes, com supressão de conteúdo sensível. O Android pode restringir READ_SMS mesmo fora da Play Store. Apenas aparelhos pareados poderão consultar as mensagens.",
            R.drawable.ic_message, "Solicitar READ_SMS") {
            if (checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) {
                SettingsStore.setShareSmsInbox(this, true)
                message("Compartilhamento de SMS autorizado.")
            } else requestPermissions(arrayOf(Manifest.permission.READ_SMS), smsReadPermissionRequest)
        }
    })
    sectionGap(layout, 8)
    val smsLive = SettingsStore.receiveSmsEvents(this) &&
        checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
    layout.addView(iconMenuRow(R.drawable.ic_message, "Receber SMS em tempo real",
        if (smsLive) "Habilitado (desativar)" else "Desativado (ativar)") {
        if (smsLive) {
            SettingsStore.setReceiveSmsEvents(this, false)
            message("Recepção de eventos SMS desativada.")
        } else if (!SettingsStore.shareSmsInbox(this)) {
            message("Ative primeiro a leitura da caixa SMS.")
        } else showAppConfirm("Autorizar recepção de SMS?",
            "Novos SMS elegíveis serão detectados no monitorado e mantidos temporariamente na memória. Apenas uma nova consulta autenticada os mostrará ao controlador.",
            R.drawable.ic_message, "Solicitar RECEIVE_SMS") {
            if (checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED) {
                SettingsStore.setReceiveSmsEvents(this, true)
                message("Recepção de novos SMS autorizada.")
            } else requestPermissions(arrayOf(Manifest.permission.RECEIVE_SMS), smsReceivePermissionRequest)
        }
    })
    sectionGap(layout, 8)
    val smsWrite = SettingsStore.allowSmsReplies(this) &&
        checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
    layout.addView(iconMenuRow(R.drawable.ic_message, "Responder SMS", if (smsWrite) "Habilitado (desativar)" else "Desativado (ativar)") {
        if (smsWrite) {
            SettingsStore.setAllowSmsReplies(this, false)
            message("Respostas SMS remotas desativadas.")
        } else if (!SettingsStore.shareSmsInbox(this)) {
            message("Autorize primeiro a leitura de SMS para permitir respostas a números conhecidos.")
        } else showAppConfirm("Autorizar resposta a SMS?",
            "O envio usa a operadora e pode gerar cobrança. Apenas números regulares de mensagens recebidas poderão ser respondidos. O controlador precisará confirmar cada envio. Solicitar SEND_SMS agora?",
            R.drawable.ic_message, "Solicitar SEND_SMS") {
            if (checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
                SettingsStore.setAllowSmsReplies(this, true)
                message("Respostas SMS autorizadas.")
            } else requestPermissions(arrayOf(Manifest.permission.SEND_SMS), smsSendPermissionRequest)
        }
    })
    sectionGap(layout, 8)
    val simAccess = checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
    layout.addView(iconMenuRow(R.drawable.ic_sim, "Escolher chip para SMS",
        if (simAccess) "Autorizado · seleção no controlador" else "Usando chip padrão") {
        if (simAccess) message("Permissão de SIM já autorizada. A seleção é feita ao responder uma mensagem SMS.")
        else showAppConfirm("Autorizar seleção de SIM?",
            "Opcional: identificar apenas SIM 1/SIM 2 para enviar SMS. Nenhum identificador ICCID ou número de linha será transmitido.",
            R.drawable.ic_sim, "Solicitar READ_PHONE_STATE") {
            requestPermissions(arrayOf(Manifest.permission.READ_PHONE_STATE), smsSimPermissionRequest)
        }
    })
    sectionGap(layout, 8)
    val numberEnabled = SettingsStore.sharePhoneNumber(this) &&
        checkSelfPermission(Manifest.permission.READ_PHONE_NUMBERS) == PackageManager.PERMISSION_GRANTED
    layout.addView(iconMenuRow(R.drawable.ic_sim, "Número do chip", if (numberEnabled) "Compartilhamento ativado" else "Desativado (padrão)") {
        if (numberEnabled) {
            SettingsStore.setSharePhoneNumber(this, false)
            message("Compartilhamento do número desativado. O Android pode manter a permissão concedida, mas o app não transmitirá o número.")
        } else {
            showAppConfirm(
                "Compartilhar número do chip?",
                "Opcional. Quando disponível, o número será enviado somente a aparelhos pareados e autenticados. Não é necessário para bateria, redes ou armazenamento. Você poderá desativar esta opção a qualquer momento.",
                R.drawable.ic_sim, "Autorizar"
            ) {
                if (checkSelfPermission(Manifest.permission.READ_PHONE_NUMBERS) == PackageManager.PERMISSION_GRANTED) {
                    SettingsStore.setSharePhoneNumber(this, true)
                    message("Compartilhamento ativado. Algumas operadoras não informam o número ao Android.")
                } else requestPermissions(arrayOf(Manifest.permission.READ_PHONE_NUMBERS), phoneNumberPermissionRequest)
            }
        }
    })
}

private fun MainActivity.fillScreenSettings(layout: LinearLayout) {
    dialogIntro(layout, "A tela requer aprovação do Android a cada nova sessão. Reconectar o controlador reutiliza a sessão ainda ativa, sem nova autorização.")
    layout.addView(iconMenuRow(R.drawable.ic_monitor, "Compartilhamento de tela",
        if (ScreenFrameStore.sharing) "Sessão ativa · disponível para reconexões" else "Inativo · iniciar no Modo monitorado") {
        message(if (ScreenFrameStore.sharing) "A projeção já está ativa. Use Parar compartilhamento no Modo monitorado para encerrá-la."
            else "Abra o Modo monitorado e toque em Compartilhar tela para aprovar pelo Android.")
    })
    sectionGap(layout, 9)
    val touch = SettingsStore.allowRemoteTouch(this)
    layout.addView(iconMenuRow(R.drawable.ic_phone, "Controle por toque",
        when { !touch -> "Desativado (ativar)"; RemoteTouchService.isReady() -> "Autorizado e serviço ativo";
            else -> "Ativado · habilite a Acessibilidade no Android" }) {
        if (touch) {
            SettingsStore.setAllowRemoteTouch(this, false)
            message("Controle por toque desativado neste aparelho.")
        } else showAppConfirm("Permitir toque remoto?",
            "Durante uma sessão de tela ativa, aparelhos que possuem a chave de pareamento poderão tocar, deslizar e usar Voltar/Início neste celular. Acessibilidade deverá ser habilitada manualmente. Não lê textos nem senhas da interface.",
            R.drawable.ic_shield, "Permitir e configurar") {
            SettingsStore.setAllowRemoteTouch(this, true)
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    })
    sectionGap(layout, 9)
    val wake = SettingsStore.allowRemoteWake(this)
    layout.addView(iconMenuRow(R.drawable.ic_remote_wake, "Permitir acender tela remotamente",
        if (wake) "Autorizado · não remove senha ou biometria" else "Desativado (ativar)") {
        if (SettingsStore.allowRemoteWake(this)) {
            SettingsStore.setAllowRemoteWake(this, false)
            message("Ativação remota da tela desativada neste aparelho.")
        } else showAppConfirm("Permitir acender a tela?",
            "Um controlador já pareado poderá solicitar que a tela deste aparelho acenda mesmo se a projeção terminou. " +
                "Não desbloqueia PIN, senha ou biometria. O Android pode recusar a tentativa. " +
                "O monitoramento precisa permanecer ativado.",
            R.drawable.ic_remote_wake, "Permitir") {
            SettingsStore.setAllowRemoteWake(this, true)
            message("Autorizado: acender tela sob comando do controlador. Pode depender do Android.")
        }
    })
    sectionGap(layout, 9)
    val automatic = SettingsStore.autoWakeOnView(this)
    layout.addView(iconMenuRow(R.drawable.ic_remote_wake, "Acender automaticamente ao abrir Ver tela",
        if (automatic) "Ativado neste controlador" else "Desativado (ativar)") {
        val next = !SettingsStore.autoWakeOnView(this)
        SettingsStore.setAutoWakeOnView(this, next)
        message(if (!next) "Ativação ao abrir desabilitada neste aparelho."
            else "Ao abrir Ver tela, será enviada uma única solicitação. O monitorado também precisa autorizar.")
    })
    sectionGap(layout, 9)
    val audio = SettingsStore.captureInternalAudio(this)
    layout.addView(iconMenuRow(R.drawable.ic_bell, "Capturar áudio interno",
        if (audio) "Habilitado · sessão nova necessária se já compartilhar" else "Desativado (ativar)") {
        if (audio) {
            SettingsStore.setCaptureInternalAudio(this, false)
            if (ScreenFrameStore.sharing) startService(Intent(this, br.dev.monitorremoto.capture.ScreenShareService::class.java)
                .setAction(br.dev.monitorremoto.capture.ScreenShareService.ACTION_AUDIO_STOP))
            message("Compartilhamento de áudio desativado. Não afeta a imagem.")
        } else if (android.os.Build.VERSION.SDK_INT < 29) {
            message("Áudio de reprodução exige Android 10 ou superior.")
        } else showAppConfirm("Permitir áudio interno?",
            "Capta reprodução de músicas, vídeos e jogos que autorizem captura. Não capta microfone nem chamadas protegidas. Exige RECORD_AUDIO e captura de tela autorizada. Para começar após a alteração, inicie uma nova sessão de tela.",
            R.drawable.ic_bell, "Solicitar autorização") {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                SettingsStore.setCaptureInternalAudio(this, true)
                message("Áudio interno ativado. Inicie uma nova sessão de tela para capturar som.")
            } else requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), audioPermissionRequest)
        }
    })
    sectionGap(layout, 9)
    layout.addView(label("O acesso à tela não reinicia automaticamente após encerramento da projeção, bloqueio pelo Android ou reinício do aparelho. O monitorado sempre pode parar pela notificação.", 11.5f, muted = true))
}

private fun MainActivity.fillSecuritySettings(layout: LinearLayout) {

    dialogIntro(layout, "As credenciais são protegidas pelo Android Keystore. Compartilhe a chave somente entre seus próprios aparelhos.")
    layout.addView(iconMenuRow(R.drawable.ic_shield, "Mostrar/copiar chave", "IP e chave de pareamento protegidos") { showMonitorKey() })
    sectionGap(layout, 8)
    layout.addView(iconMenuRow(R.drawable.ic_logout, "Trocar chave e revogar acessos", "Revoga o acesso de todos os controladores", dangerous = true) { confirmRotateMonitorKey() })
}

private fun MainActivity.showMonitorKey() { showMyConnectionInfo() }

internal fun MainActivity.confirmRotateMonitorKey() {
    showAppConfirm(
        "Revogar acessos?",
        "Todos os controladores perderão a chave atual e precisarão parear novamente. Essa operação não pode ser desfeita.",
        R.drawable.ic_shield, "Trocar chave"
    ) {
        SecretVault.save(this, SecretVault.MONITOR_KEY, CryptoBox.newPairingKey())
        PairingSession.revoke()
        AuthenticatedAccess.clear()
        message("Chave substituída. Os acessos anteriores foram revogados.")
    }
}

private fun MainActivity.fillAppearanceSettings(layout: LinearLayout) {
    dialogIntro(layout, "Escolha o tema usado no aplicativo.")
    val options = listOf(
        Triple("light", "Claro", "Visual claro padrão"),
        Triple("dark", "Escuro", "Preferível em ambientes escuros"),
        Triple("system", "Seguir o sistema", "Acompanha o Android")
    )
    options.forEachIndexed { i, entry ->
        layout.addView(iconMenuRow(R.drawable.ic_palette, entry.second,
            if (SettingsStore.theme(this) == entry.first) "✓ Selecionado · ${entry.third}" else entry.third) {
            if (SettingsStore.theme(this) != entry.first) {
                SettingsStore.setTheme(this, entry.first)
                recreate()
            }
        })
        if (i != options.lastIndex) sectionGap(layout, 9)
    }
}

private fun MainActivity.fillServiceSettings(layout: LinearLayout) {
    val active = SettingsStore.isMonitorEnabled(this)
    val listening = MonitorService.isRunning
    dialogIntro(layout, "A recuperação atua enquanto o monitoramento foi autorizado. Não reinicia após Parar nem após uma parada forçada do Android.")
    layout.addView(iconSectionHeader(R.drawable.ic_power, "Monitoramento local",
        if (!active) "Desativado" else if (listening) "Servidor escutando" else "Aguardando recuperação"))
    sectionGap(layout, 12)
    layout.addView(iconMenuRow(R.drawable.ic_refresh, "Verificar / retomar agora",
        if (active) "Solicita a revalidação do servidor" else "Ative o monitoramento primeiro") {
        if (active) {
            startServiceExplicitly()
            message("Solicitação de retomada enviada. Confira o estado em Diagnóstico.")
        } else message("Ative primeiro o monitoramento na tela Monitorado.")
    })
    sectionGap(layout, 9)
    val autostart = SettingsStore.autoStartEnabled(this)
    layout.addView(iconMenuRow(R.drawable.ic_clock, "Iniciar depois de reiniciar o celular",
        if (autostart) "Ativado · sujeito às regras do Android" else "Desativado (toque para ativar)") {
        SettingsStore.setAutoStart(this, !autostart)
        render()
    })
    sectionGap(layout, 9)
    val jobsEnabled = SettingsStore.recoveryJobsEnabled(this)
    layout.addView(iconMenuRow(R.drawable.ic_refresh, "Recuperação agendada (15 min)",
        if (jobsEnabled) "Autorizada · Android pode atrasar ou bloquear" else "Desativada · toque para autorizar") {
        SettingsStore.setRecoveryJobs(this, !jobsEnabled)
        render()
    })
    sectionGap(layout, 9)
    layout.addView(iconMenuRow(R.drawable.ic_activity, "Consultar diagnóstico",
        "Estado do servidor, falhas e última verificação") { openSettingsSection("diagnostics") })
    sectionGap(layout, 13)
    layout.addView(label("O supervisor TCP verifica a porta a cada 15 s enquanto o serviço existe. O agendador é um plano B, em intervalos de pelo menos 15 min, sujeitos ao Android e ao Doze. Se a retomada em segundo plano for proibida, será oferecida uma notificação para reativar. Parar desativa ambos.", 12.5f, muted = true))
    sectionGap(layout, 12)
    layout.addView(iconMenuRow(R.drawable.ic_phone, "Ajustar bateria no Android",
        "Permitir atividade em segundo plano quando disponível") {
        runCatching {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                android.net.Uri.parse("package:$packageName")))
        }.onFailure { message("Não foi possível abrir as configurações do Android.") }
    })
}

private fun MainActivity.fillDiagnosticsSettings(layout: LinearLayout) {

    val running = MonitorService.isRunning
    dialogIntro(layout, "Resumo técnico do serviço local e das permissões relacionadas.")
    val card = panel(14)
    card.addView(iconSectionHeader(R.drawable.ic_activity, "Serviço e rede", "Estado atual deste aparelho"))
    sectionGap(card, 12)
    val firstRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
    firstRow.addView(iconMetric("Serviço", if (running) "Ativo" else "Parado", R.drawable.ic_power),
        LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(9) })
    firstRow.addView(iconMetric("Monitoramento", if (SettingsStore.isMonitorEnabled(this)) "Autorizado" else "Desativado", R.drawable.ic_shield),
        LinearLayout.LayoutParams(0, -2, 1f))
    card.addView(firstRow)
    sectionGap(card, 9)
    val secondRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
    secondRow.addView(iconMetric("Porta TCP", WireProtocol.PORT.toString(), R.drawable.ic_wifi),
        LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(9) })
    secondRow.addView(iconMetric("Notificações", if (hasListener()) "Autorizadas" else "Sem acesso", R.drawable.ic_bell),
        LinearLayout.LayoutParams(0, -2, 1f))
    card.addView(secondRow)
    sectionGap(card, 10)
    card.addView(dialogMetricRow("IP local", localIps().ifBlank { "Indisponível" }))
    card.addView(dialogMetricRow("Apps permitidos", SettingsStore.notificationAllowlist(this).size.toString()))
    card.addView(dialogMetricRow("Leitura SMS", if (SettingsStore.shareSmsInbox(this) &&
        checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) "Autorizada" else "Desativada/sem permissão"))
    card.addView(dialogMetricRow("Resposta SMS", if (SettingsStore.allowSmsReplies(this) &&
        checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) "Autorizada" else "Desativada/sem permissão"))
    card.addView(dialogMetricRow("Resposta notificação", if (SettingsStore.allowNotificationReplies(this)) "Ativa" else "Desativada"))
    card.addView(dialogMetricRow("Notificações de todos apps", if (SettingsStore.allNotifications(this)) "Autorizadas" else "Lista individual"))
    card.addView(dialogMetricRow("RECEIVE_SMS", if (checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED) "Concedida" else "Ausente"))
    card.addView(dialogMetricRow("Leitura SMS em tempo real", if (SettingsStore.receiveSmsEvents(this)) "Ativada" else "Desativada"))
    card.addView(dialogMetricRow("READ_PHONE_STATE", if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) "Concedida" else "Ausente"))
    layout.addView(card)
    sectionGap(card, 10)
    card.addView(dialogMetricRow("Estado do servidor", SettingsStore.serverHealthState(this)))
    card.addView(dialogMetricRow("Falhas consecutivas", SettingsStore.serverFailureCount(this).toString()))
    val last = SettingsStore.serverLastCheck(this)
    card.addView(dialogMetricRow("Última verificação", if (last == 0L) "Ainda não realizada" else formatDate(last)))
    card.addView(dialogMetricRow("Reiniciar após boot", if (SettingsStore.autoStartEnabled(this)) "Autorizado" else "Desativado"))
    card.addView(dialogMetricRow("Serviço Android", if (MonitorService.isServiceActive) "Em execução" else "Não está ativo"))
    card.addView(dialogMetricRow("Recuperação agendada", if (SettingsStore.recoveryJobsEnabled(this)) "Autorizada" else "Desativada"))
    card.addView(dialogMetricRow("Último evento de recuperação", SettingsStore.recoveryLastEvent(this)))
    val eventMs = SettingsStore.recoveryLastEventMs(this)
    card.addView(dialogMetricRow("Data do evento", if (eventMs == 0L) "Sem registro" else formatDate(eventMs)))
}

internal fun MainActivity.showAboutDialog() { openSettingsModule(); openSettingsSection("about") }
private fun MainActivity.fillAboutSettings(layout: LinearLayout) {

    dialogIntro(layout, "Resumo rápido do foco atual do projeto.")
    val card = panel(14)
    card.addView(iconSectionHeader(R.drawable.ic_info, "Monitor Remoto", "Uso particular e conexão privada"))
    sectionGap(card, 9)
    card.addView(dialogMetricRow("Versão", AppVersion.NAME))
    card.addView(dialogMetricRow("Tela inicial", "Home com escolha de aparelhos"))
    card.addView(dialogMetricRow("Modo monitorado", "No mesmo APK"))
    card.addView(dialogMetricRow("Segurança", "Pareamento e Android Keystore"))
    layout.addView(card)
    layout.addView(label("Privacidade, baixo consumo e permissões explícitas continuam como prioridades.", 12f, muted = true))
}

internal fun MainActivity.selectNotificationApps() {
    val apps = packageManager.getInstalledApplications(0)
        .filter {
            it.packageName != packageName &&
                packageManager.getLaunchIntentForPackage(it.packageName) != null &&
                !SensitiveContent.isBlockedFinancialApp(
                    it.packageName, packageManager.getApplicationLabel(it).toString()
                )
        }
        .sortedBy { packageManager.getApplicationLabel(it).toString().lowercase(Locale.getDefault()) }
    val selected = SettingsStore.notificationAllowlist(this).toMutableSet()
    val (wrapper, layout) = dialogWrapper()
    layout.addView(iconSectionHeader(R.drawable.ic_bell, "Aplicativos autorizados", "Permita apenas o que deseja compartilhar"))
    sectionGap(layout, 8)
    dialogIntro(layout, "As notificações dos aplicativos selecionados poderão ser consultadas por aparelhos pareados. Os filtros de conteúdo sensível não são infalíveis.")
    val search = field("Buscar aplicativo por nome").apply {
        inputType = android.text.InputType.TYPE_CLASS_TEXT
    }
    layout.addView(search)
    sectionGap(layout, 8)
    val count = label("", 11.5f, muted = true)
    layout.addView(count)
    val appList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    val listScroll = android.widget.ScrollView(this).apply {
        isFillViewport = false
        addView(appList)
    }
    layout.addView(listScroll, LinearLayout.LayoutParams(-1, dp(300)))
    sectionGap(layout, 8)
    layout.addView(panel(11).apply {
        background = shape(fieldColor, 15)
        addView(label("Aplicativos bancários conhecidos são excluídos da lista. A leitura opcional de SMS usa outra permissão e precisa de autorização independente.", 11f, muted = true))
    })

    fun refreshList(filter: String) {
        appList.removeAllViews()
        val visible = apps.filter { packageManager.getApplicationLabel(it).toString()
            .contains(filter.trim(), ignoreCase = true) }
        count.text = "${selected.size} selecionado(s) · ${visible.size} aplicativo(s) exibido(s)"
        if (visible.isEmpty()) {
            appList.addView(label(if (apps.isEmpty()) "Nenhum aplicativo elegível foi encontrado neste aparelho."
                else "Nenhum aplicativo corresponde à pesquisa.", 12.5f, muted = true))
            return
        }
        visible.forEach { app ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = shape(fieldColor, 15, true)
                setPadding(dp(10), dp(8), dp(10), dp(8))
            }
            val symbol = ImageView(this).apply {
                setImageDrawable(runCatching { packageManager.getApplicationIcon(app.packageName) }.getOrNull())
                contentDescription = null
            }
            row.addView(symbol, LinearLayout.LayoutParams(dp(30), dp(30)))
            row.addView(android.view.View(this), LinearLayout.LayoutParams(dp(10), 1))
            val name = packageManager.getApplicationLabel(app).toString()
            val title = label(name, 13f, strong = true).apply {
                maxLines = 2; setPadding(0, 0, 0, 0)
            }
            row.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
            val check = android.widget.CheckBox(this).apply {
                isChecked = app.packageName in selected
                contentDescription = "Permitir notificações de $name"
                setOnCheckedChangeListener { _, checked ->
                    if (checked) selected.add(app.packageName) else selected.remove(app.packageName)
                    count.text = "${selected.size} selecionado(s) · ${visible.size} aplicativo(s) exibido(s)"
                }
            }
            row.addView(check)
            row.setOnClickListener { check.isChecked = !check.isChecked }
            appList.addView(row)
            sectionGap(appList, 7)
        }
    }
    search.addTextChangedListener(object : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            refreshList(s?.toString().orEmpty())
        }
        override fun afterTextChanged(s: android.text.Editable?) = Unit
    })
    refreshList("")
    showManagedDialog(AlertDialog.Builder(this)
        .setView(wrapper)
        .setNegativeButton("Cancelar", null)
        .setPositiveButton("Salvar") { _, _ ->
            SettingsStore.setNotificationAllowlist(this, selected)
            render()
        })
}
