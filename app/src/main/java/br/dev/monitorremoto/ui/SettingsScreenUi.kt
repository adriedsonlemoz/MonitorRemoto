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
        "apps" to "Aplicativos instalados",
        "sms" to "SMS e telefonia",
        "service" to "Serviço e recuperação",
        "physical" to "Som e lanterna",
        "lock" to "Bloqueio remoto",
        "files" to "Arquivos e recuperação",
        "screen" to "Tela e controle remoto",
        "appearance" to "Aparência",
        "security" to "Segurança",
        "diagnostics" to "Diagnóstico",
        "health" to "Alertas e proteção",
        "location" to "Localização",
        "news" to "Novidades",
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
        renderSettingsHome()
    } else {
        val page = panel(15)
        when (section) {
            "notifications" -> fillNotificationSettings(page)
            "apps" -> fillAppsSettings(page)
            "sms" -> fillSmsSettings(page)
            "screen" -> fillScreenSettings(page)
            "physical" -> fillPhysicalControlSettings(page)
            "lock" -> fillRemoteLockSettings(page)
            "files" -> fillFileRecoverySettings(page)
            "service" -> fillServiceSettings(page)
            "security" -> fillSecuritySettings(page)
            "appearance" -> fillAppearanceSettings(page)
            "diagnostics" -> fillDiagnosticsSettings(page)
            "health" -> fillHealthSettings(page)
            "location" -> fillLocationSettings(page)
            "news" -> fillVersionNewsSettings(page)
            "about" -> fillAboutSettings(page)
            else -> page.addView(label("Categoria não encontrada", 13f))
        }
        content.addView(page)
    }
    sectionGap(content, 16)
    content.addView(label("Monitor Remoto · ${AppVersion.NAME}", 12f, muted = true))
}

private fun MainActivity.settingsEntry(
    into: LinearLayout, icon: Int, title: String, subtitle: String, section: String, divider: Boolean = true
) {
    into.addView(iconMenuRow(icon, title, subtitle) { openSettingsSection(section) })
    if (divider) sectionGap(into, 9)
}

private fun MainActivity.fillNotificationSettings(layout: LinearLayout) {
    val state = when {
        !hasListener() -> "Sem permissão do Android"
        !br.dev.monitorremoto.capture.NotificationCaptureStatus.connected -> "Autorizado · serviço desconectado"
        else -> "Autorizado · serviço ativo"
    }
    val count = SettingsStore.notificationAllowlist(this).size
    dialogIntro(layout, "O monitoramento da bateria funciona sem esta permissão. As notificações só são enviadas para apps autorizados e não financeiros.")
    layout.addView(iconMenuRow(R.drawable.ic_bell, "Acesso às notificações", state, badge = SettingsBadgeKind.forEnabled(hasListener())) {
        showAppConfirm(
            "Acesso às notificações",
            "O Android poderá mostrar notificações sensíveis. Somente aplicativos escolhidos serão transmitidos, com filtros que podem falhar. O acesso pode ser revogado nas Configurações do Android.",
            R.drawable.ic_bell, "Configurar no Android"
        ) { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
    })
    sectionGap(layout, 8)
    layout.addView(iconMenuRow(R.drawable.ic_activity, "Diagnóstico da captura",
        if (br.dev.monitorremoto.capture.NotificationCaptureStatus.connected) "Listener conectado" else "Listener sem conexão; verifique autorização e bateria") {
        message("A permissão do Android e a lista de aplicativos são necessárias. Uma notificação existente só pode ser recuperada enquanto ainda estiver ativa no sistema. Verifique também restrições de bateria.")
    })
    sectionGap(layout, 8)
    val allApps = SettingsStore.allNotifications(this)
    layout.addView(iconMenuRow(R.drawable.ic_bell, "Todas as notificações elegíveis",
        if (allApps) "Apps não financeiros elegíveis estão liberados" else "Somente a lista individual está liberada", badge = SettingsBadgeKind.forEnabled(allApps)) {
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
        if (allApps) "Todos os apps elegíveis podem enviar notificações" else "$count aplicativo(s) selecionado(s)", badge = SettingsBadgeKind.forEnabled((allApps || count > 0))) { selectNotificationApps() })
    sectionGap(layout, 8)
    val replyOn = SettingsStore.allowNotificationReplies(this)
    layout.addView(iconMenuRow(R.drawable.ic_message, "Responder notificações", if (replyOn) "Respostas remotas liberadas para eventos elegíveis" else "O controlador não pode responder por aqui", badge = SettingsBadgeKind.forEnabled(replyOn)) {
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

private fun MainActivity.fillAppsSettings(layout: LinearLayout) {
    val allowed = SettingsStore.shareInstalledApps(this)
    layout.addView(iconSectionHeader(R.drawable.ic_phone, "Aplicativos instalados", "Apenas apps com ícone no lançador"))
    sectionGap(layout, 10)
    layout.addView(label("Consulta sob demanda pelos controladores pareados. Compartilha nomes e identificadores de pacote, sem dados internos; alguns apps podem não aparecer pelas restrições do Android.", 12.5f, muted = true))
    sectionGap(layout, 10)
    layout.addView(iconMenuRow(R.drawable.ic_shield,
        "Permitir consulta de aplicativos", if (allowed) "Controladores pareados podem consultar a lista" else "Consulta bloqueada neste aparelho", badge = SettingsBadgeKind.forEnabled(allowed)) {
        if (allowed) {
            SettingsStore.setShareInstalledApps(this, false)
            message("Compartilhamento da lista de aplicativos desativado.")
        } else showAppConfirm("Compartilhar lista de aplicativos?",
            "Qualquer controlador que possua a chave deste monitorado poderá consultar os nomes dos aplicativos com ícone de launcher. Nenhum dado interno dos aplicativos será compartilhado.",
            R.drawable.ic_phone, "Autorizar lista") {
            SettingsStore.setShareInstalledApps(this, true)
            message("Lista de aplicativos autorizada para consulta autenticada.")
        }
    })
    sectionGap(layout, 10)
    val launchEnabled = SettingsStore.allowRemoteAppLaunch(this)
    layout.addView(iconMenuRow(R.drawable.ic_phone,
        "Permitir abrir aplicativos", if (launchEnabled) "Somente quando a tela do Monitor Remoto estiver aberta neste aparelho" else "Desativado. ADB exige autorização separada", badge = SettingsBadgeKind.forEnabled(launchEnabled)) {
        if (launchEnabled) {
            SettingsStore.setAllowRemoteAppLaunch(this, false)
            message("Abertura remota por API Android desativada.")
        } else showAppConfirm("Autorizar abertura remota?",
            "O controlador pareado poderá solicitar que aplicativos sejam abertos somente enquanto o Monitor Remoto estiver visível neste aparelho. O Android pode restringir a abertura; o ADB continua separado.",
            R.drawable.ic_phone, "Autorizar abertura") {
            SettingsStore.setAllowRemoteAppLaunch(this, true)
            message("Abertura remota autorizada com interface visível.")
        }
    })
}

private fun MainActivity.fillSmsSettings(layout: LinearLayout) {
    dialogIntro(layout, "A leitura e o envio de SMS dependem da autorização local e das permissões do Android. Ative somente o necessário.")
    val smsRead = SettingsStore.shareSmsInbox(this) &&
        checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
    layout.addView(iconMenuRow(R.drawable.ic_message, "Ler caixa de SMS", if (smsRead) "Até 30 SMS recentes podem ser consultados" else "Leitura de SMS bloqueada neste aparelho", badge = SettingsBadgeKind.forEnabled(smsRead)) {
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
        if (smsLive) "Novos SMS elegíveis podem ser detectados" else "Novos SMS não serão acompanhados", badge = SettingsBadgeKind.forEnabled(smsLive)) {
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
    layout.addView(iconMenuRow(R.drawable.ic_message, "Responder SMS", if (smsWrite) "O controlador pode responder SMS elegíveis" else "Envio remoto de SMS bloqueado", badge = SettingsBadgeKind.forEnabled(smsWrite)) {
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
        if (simAccess) "Controlador pode escolher SIM 1 ou SIM 2" else "Usando o chip padrão do Android", badge = SettingsBadgeKind.forEnabled(simAccess)) {
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
    layout.addView(iconMenuRow(R.drawable.ic_sim, "Número do chip", if (numberEnabled) "Número poderá ser compartilhado quando o Android informar" else "Número não será compartilhado", badge = SettingsBadgeKind.forEnabled(numberEnabled)) {
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

private fun MainActivity.fillPhysicalControlSettings(layout: LinearLayout) {
    dialogIntro(layout, "Autorizações somente neste aparelho. O controlador deve ter a chave pareada e a mesma versão do app. Nenhum controle funciona se o serviço estiver parado.")
    layout.addView(iconMenuRow(R.drawable.ic_bell, "Permitir alerta sonoro remoto",
        if (SettingsStore.allowRemoteSound(this)) "Controlador pode tocar alerta de até 30 segundos" else "Alerta sonoro remoto bloqueado", badge = SettingsBadgeKind.forEnabled(SettingsStore.allowRemoteSound(this))) {
        if (SettingsStore.allowRemoteSound(this)) {
            SettingsStore.setAllowRemoteSound(this, false)
            openSettingsSection("physical")
        } else showAppConfirm("Autorizar alerta sonoro?",
            "Quem possuir a chave de pareamento poderá tocar um alerta por 5, 10, 20 ou 30 segundos. O som segue o volume, Não Perturbe e restrições do Android. Pode ser interrompido no aparelho controlador ou revogando esta opção aqui.",
            R.drawable.ic_bell, "Autorizar") {
            SettingsStore.setAllowRemoteSound(this, true)
            openSettingsSection("physical")
        }
    })
    sectionGap(layout, 10)
    layout.addView(iconMenuRow(R.drawable.ic_flashlight, "Permitir lanterna remota",
        if (SettingsStore.allowRemoteTorch(this)) "Controlador pode ligar o flash quando disponível" else "Lanterna remota bloqueada", badge = SettingsBadgeKind.forEnabled(SettingsStore.allowRemoteTorch(this))) {
        if (SettingsStore.allowRemoteTorch(this)) {
            SettingsStore.setAllowRemoteTorch(this, false)
            openSettingsSection("physical")
        } else showAppConfirm("Autorizar lanterna remota?",
            "Quem possuir a chave de pareamento poderá ligar ou desligar o flash traseiro. " +
                "A permissão Câmera precisa estar concedida neste aparelho. O Android pode negar se outra câmera estiver ocupada. O flash remoto é desligado ao encerrar o serviço.",
            R.drawable.ic_flashlight, "Autorizar") {
            SettingsStore.setAllowRemoteTorch(this, true)
            openSettingsSection("physical")
        }
    })
    sectionGap(layout, 12)
    val cameraGranted = checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    layout.addView(iconMenuRow(R.drawable.ic_remote_camera, "Permissão Câmera do Android",
        if (cameraGranted) "Permissão da câmera já concedida ao app" else "Abra o Android para conceder a permissão da câmera", badge = SettingsBadgeKind.forEnabled(cameraGranted)) {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            android.net.Uri.parse("package:$packageName")))
    })
    sectionGap(layout, 8)
    layout.addView(label("O Monitor Remoto não liga câmera nem microfone por estas funções. Tocar som não aumenta o volume e a lanterna pode consumir bastante bateria.", 12.8f, muted = true))
}


/** Duplo consentimento: administrador Android pelo usuário e chave local independente. */
private fun MainActivity.fillRemoteLockSettings(layout: LinearLayout) {
    val admin = br.dev.monitorremoto.core.RemoteScreenLock.adminActive(this)
    val secure = br.dev.monitorremoto.core.RemoteScreenLock.secureLockConfigured(this)
    val supported = br.dev.monitorremoto.core.RemoteScreenLock.supported(this)
    val enabled = SettingsStore.allowRemoteLock(this)
    layout.addView(iconSectionHeader(R.drawable.ic_shield, "Bloqueio remoto", "Somente com autorização neste celular"))
    sectionGap(layout, 9)
    layout.addView(label("Requer administrador de dispositivo autorizado pelo Android e PIN, padrão ou senha já configurados. Não altera senha nem substitui biometria. O bloqueio pode impedir acesso a celulares sem botão Power.", 12.5f, muted = true))
    sectionGap(layout, 12)
    layout.addView(iconMenuRow(R.drawable.ic_shield, "Administrador do Android", when {
        !supported -> "Não suportado neste aparelho"
        admin -> "Administrador já ativo no Android"
        else -> "Ative manualmente no Android para usar o bloqueio"
    }, badge = SettingsBadgeKind.forEnabled(admin)) {
        if (!supported) {
            message("Este dispositivo Android não oferece a função necessária.")
        } else if (admin) {
            showAppNotice("Administrador ativado",
                "Para revogar, abra Configurações do Android › Segurança › Apps de administração do dispositivo. O Monitor Remoto não desativa a proteção do sistema por comando remoto.")
        } else showAppConfirm("Ativar administrador para bloqueio?",
            "O Android abrirá a tela oficial para você aprovar ou recusar. O Monitor Remoto solicita somente a política de bloquear a tela: não pode apagar dados nem definir senhas. A chave de autorização remota continuará desligada até sua ativação separada.",
            R.drawable.ic_shield, "Abrir autorização Android") {
            val intent = Intent(android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                .putExtra(android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                    br.dev.monitorremoto.core.RemoteScreenLock.adminComponent(this))
                .putExtra(android.app.admin.DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Apenas bloqueio remoto da tela após confirmação no outro celular.")
            runCatching { startActivity(intent) }.onFailure { message("Não foi possível abrir a autorização do Android.") }
        }
    })
    sectionGap(layout, 10)
    layout.addView(iconMenuRow(R.drawable.ic_shield, "Bloqueio seguro do Android",
        if (secure) "O Android já possui proteção de bloqueio configurada" else "Abra o Android e configure PIN, padrão ou senha", badge = SettingsBadgeKind.forEnabled(secure)) {
        runCatching { startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS)) }
            .onFailure { message("Abra Configurações › Segurança e configure a tela de bloqueio.") }
    })
    sectionGap(layout, 10)
    layout.addView(iconMenuRow(R.drawable.ic_shield, "Permitir bloqueio remoto",
        if (enabled) "Controlador pareado pode solicitar o bloqueio" else "Bloqueio remoto ainda não foi liberado", badge = SettingsBadgeKind.forEnabled(enabled)) {
        if (enabled) {
            SettingsStore.setAllowRemoteLock(this, false)
            openSettingsSection("lock")
        } else if (!supported || !admin || !secure) {
            showAppNotice("Autorização incompleta",
                "Primeiro ative o administrador pelo Android e configure PIN, padrão ou senha. Depois volte a esta tela e autorize o comando.")
        } else showAppConfirm("Autorizar bloqueio remoto?",
            "Qualquer controlador com a chave de pareamento poderá solicitar o bloqueio após confirmação digitada. Isto pode apagar a tela e exigir PIN, senha ou padrão para desbloquear. Não habilite se não puder acordar/desbloquear este celular.",
            R.drawable.ic_shield, "Autorizar bloqueio") {
            SettingsStore.setAllowRemoteLock(this, true)
            openSettingsSection("lock")
        }
    })
    sectionGap(layout, 10)
    layout.addView(label("Para revogar imediatamente, desative a opção acima ou pare o monitoramento. Também é possível remover o administrador pelas Configurações do Android.", 12f, muted = true))
}

private fun MainActivity.fillScreenSettings(layout: LinearLayout) {
    dialogIntro(layout, "A tela requer aprovação do Android a cada nova sessão. Reconectar o controlador reutiliza a sessão ainda ativa, sem nova autorização.")
    layout.addView(iconMenuRow(R.drawable.ic_monitor, "Compartilhamento de tela",
        if (ScreenFrameStore.sharing) "Sessão ativa e pronta para reconectar" else "Abra o Modo monitorado para iniciar a tela", badge = SettingsBadgeKind.forEnabled(ScreenFrameStore.sharing)) {
        message(if (ScreenFrameStore.sharing) "A projeção já está ativa. Use Parar compartilhamento no Modo monitorado para encerrá-la."
            else "Abra o Modo monitorado e toque em Compartilhar tela para aprovar pelo Android.")
    })
    sectionGap(layout, 9)
    val touch = SettingsStore.allowRemoteTouch(this)
    layout.addView(iconMenuRow(R.drawable.ic_phone, "Controle por toque",
        when { !touch -> "Toques remotos estão bloqueados"; RemoteTouchService.isReady() -> "Toques remotos permitidos e serviço pronto";
            else -> "Autorizado no app; falta ativar a Acessibilidade" }, badge = SettingsBadgeKind.forEnabled(touch)) {
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
        if (wake) "Tela pode acender remotamente, sem desbloquear" else "Acender tela remotamente está bloqueado", badge = SettingsBadgeKind.forEnabled(wake)) {
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
        if (automatic) "Ao abrir Ver tela, este controlador tentará acender a tela" else "Este controlador não faz a solicitação automática", badge = SettingsBadgeKind.forEnabled(automatic)) {
        val next = !SettingsStore.autoWakeOnView(this)
        SettingsStore.setAutoWakeOnView(this, next)
        message(if (!next) "Ativação ao abrir desabilitada neste aparelho."
            else "Ao abrir Ver tela, será enviada uma única solicitação. O monitorado também precisa autorizar.")
    })
    sectionGap(layout, 9)
    val audio = SettingsStore.captureInternalAudio(this)
    layout.addView(iconMenuRow(R.drawable.ic_bell, "Capturar áudio interno",
        if (audio) "Novas sessões poderão incluir áudio interno" else "Áudio interno não será capturado", badge = SettingsBadgeKind.forEnabled(audio)) {
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
    layout.addView(label("O acesso à tela não reinicia automaticamente após encerramento da projeção, bloqueio pelo Android ou reinício do aparelho. O monitorado sempre pode parar pela notificação.", 12.4f, muted = true))
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
            if (SettingsStore.theme(this) == entry.first) "Tema selecionado agora · ${entry.third}" else entry.third, badge = if (SettingsStore.theme(this) == entry.first) SettingsBadgeKind.ACTIVE else SettingsBadgeKind.ACTION) {
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
        if (active) "Faz uma tentativa imediata de retomada do servidor" else "Ative primeiro o monitoramento local") {
        if (active) {
            startServiceExplicitly()
            message("Solicitação de retomada enviada. Confira o estado em Diagnóstico.")
        } else message("Ative primeiro o monitoramento na tela Monitorado.")
    })
    sectionGap(layout, 9)
    val autostart = SettingsStore.autoStartEnabled(this)
    layout.addView(iconMenuRow(R.drawable.ic_clock, "Iniciar depois de reiniciar o celular",
        if (autostart) "O app tentará voltar após reiniciar o aparelho" else "O app não tentará iniciar após reiniciar", badge = SettingsBadgeKind.forEnabled(autostart)) {
        SettingsStore.setAutoStart(this, !autostart)
        render()
    })
    sectionGap(layout, 9)
    val jobsEnabled = SettingsStore.recoveryJobsEnabled(this)
    layout.addView(iconMenuRow(R.drawable.ic_refresh, "Recuperação agendada (15 min)",
        if (jobsEnabled) "Há tentativas agendadas de recuperação a cada 15 min" else "A recuperação agendada está desligada", badge = SettingsBadgeKind.forEnabled(jobsEnabled)) {
        SettingsStore.setRecoveryJobs(this, !jobsEnabled)
        render()
    })
    sectionGap(layout, 9)
    layout.addView(iconMenuRow(R.drawable.ic_activity, "Consultar diagnóstico",
        "Ver detalhes técnicos do servidor, falhas e verificações") { openSettingsSection("diagnostics") })
    sectionGap(layout, 13)
    layout.addView(label("O supervisor TCP verifica a porta a cada 15 s enquanto o serviço existe. O agendador é um plano B, em intervalos de pelo menos 15 min, sujeitos ao Android e ao Doze. Se a retomada em segundo plano for proibida, será oferecida uma notificação para reativar. Parar desativa ambos.", 12.5f, muted = true))
    sectionGap(layout, 12)
    layout.addView(iconMenuRow(R.drawable.ic_phone, "Ajustar bateria no Android",
        "Abrir as preferências de bateria do app no Android") {
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
    card.addView(dialogMetricRow("Tela local", br.dev.monitorremoto.core.DeviceStateReader.snapshot(this).optBoolean("screenInteractive").let { if (it) "Interativa" else "Apagada/não interativa" }))
    card.addView(iconMenuRow(R.drawable.ic_wifi, "Nome e sinal da rede Wi-Fi",
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) "Permissão concedida (localização também deve estar ligada)" else "Permissão opcional necessária") {
        showAppConfirm("Mostrar rede Wi-Fi?", "Para informar o nome (SSID) e a intensidade do sinal, o Android exige a permissão de localização e que a localização esteja ligada. O Monitor Remoto não coleta coordenadas GPS para esse recurso.", R.drawable.ic_wifi, "Configurar permissão") {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 9262)
            else startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        }
    })
    card.addView(iconMenuRow(R.drawable.ic_phone, "Aplicativo visível",
        if (br.dev.monitorremoto.core.DeviceStateReader.snapshot(this).optBoolean("usagePermission")) "Acesso ao uso permitido" else "Exige autorização especial opcional") {
        showAppConfirm("Ver aplicativo em primeiro plano?", "Conceder Acesso ao uso permite identificar o último aplicativo em primeiro plano, sem ler a tela nem mensagens. Outros controladores pareados podem receber esse nome.", R.drawable.ic_phone, "Abrir acesso ao uso") {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
    })
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

/** Seleção local no próprio monitorado. Nunca solicitar MANAGE_EXTERNAL_STORAGE. */
private fun MainActivity.fillFileRecoverySettings(layout: LinearLayout) {
    val chosen = SettingsStore.sharedDataTree(this).isNotBlank()
    val active = br.dev.monitorremoto.core.SharedDataFolder.available(this)
    dialogIntro(layout, "No telefone que contém os arquivos, escolha uma pasta pelo seletor do Android. " +
        "Leitura permite baixar; gerenciamento (escrita) permite também enviar, criar, copiar, mover, renomear ou apagar. " +
        "A escrita fica DESLIGADA até ser autorizada separadamente neste aparelho.")
    layout.addView(iconMenuRow(R.drawable.ic_folder_download, "Pasta autorizada",
        if (active) "Leitura ativa · ${if (br.dev.monitorremoto.core.SharedDataFolder.writable(this)) "escrita ativa" else "escrita desativada"} · trocar pasta" else if (chosen) "Pasta selecionada · serviço desativado ou permissão expirada" else "Nenhuma pasta ativa · toque para escolher", badge = SettingsBadgeKind.forEnabled(active)) {
        showAppConfirm("Autorizar recuperação de arquivos?",
            "Você deverá selecionar pessoalmente uma pasta neste celular. " +
            "Controladores que possuem a chave de pareamento poderão listar e baixar os arquivos dessa pasta. " +
            "Gerenciamento/remoção requer uma segunda autorização, desligada por padrão. " +
            "Não permite acessar pastas privadas de outros aplicativos, nem recuperar dados apagados. " +
            "Escolha apenas uma pasta com arquivos que deseja compartilhar.",
            R.drawable.ic_shield, "Escolher pasta") {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            }, 4321)
        }
    })
    sectionGap(layout, 10)
    val canWrite = br.dev.monitorremoto.core.SharedDataFolder.writable(this)
    layout.addView(iconMenuRow(R.drawable.ic_edit, "Gerenciar arquivos remotamente",
        if (canWrite) "ATIVADO · envio, renomeação, movimento e exclusão" else
            "DESATIVADO · ativação local exige pasta com escrita concedida",
        badge = SettingsBadgeKind.forEnabled(canWrite)) {
        if (canWrite) {
            br.dev.monitorremoto.core.SettingsStore.setAllowRemoteFileWrite(this, false)
            message("Escrita remota desativada. Download permanece autorizado.")
            render()
        } else showAppConfirm("Permitir gerenciamento remoto?",
            "Qualquer controlador com a chave de pareamento poderá ENVIAR, RENOMEAR, MOVER, COPIAR " +
                "e EXCLUIR arquivos da pasta escolhida e suas subpastas. A exclusão pode ser definitiva. " +
                "Não autorize pastas contendo arquivos sensíveis; controle individual por controlador ainda não existe.",
            R.drawable.ic_shield, "Autorizar escrita") {
            val authorized = contentResolver.persistedUriPermissions.any { permission ->
                permission.uri.toString() == SettingsStore.sharedDataTree(this) && permission.isWritePermission
            }
            if (!authorized) message("O Android não concedeu escrita. Escolha a pasta novamente para solicitar a permissão.")
            else { SettingsStore.setAllowRemoteFileWrite(this, true); render(); message("Gerenciamento remoto ativado.") }
        }
    })
    sectionGap(layout, 12)
    layout.addView(iconMenuRow(R.drawable.ic_shield, "Revogar acesso à pasta",
        if (active || SettingsStore.sharedDataTree(this).isNotBlank()) "Retirar imediatamente a autorização" else "Sem autorização ativa", badge = if (chosen) SettingsBadgeKind.WARNING else SettingsBadgeKind.INACTIVE) {
        if (SettingsStore.sharedDataTree(this).isBlank()) message("Nenhuma pasta está autorizada.")
        else showAppConfirm("Revogar recuperação remota?",
            "O Monitor Remoto deixará de servir novos blocos de arquivos dessa pasta. " +
            "Cópias já concluídas em outro telefone não serão apagadas.",
            R.drawable.ic_shield, "Revogar") {
            br.dev.monitorremoto.core.SharedDataFolder.revoke(this)
            message("Permissão revogada no monitorado.")
        }
    })
    sectionGap(layout, 12)
    layout.addView(label("Limites: até 2 GiB por arquivo/lote, até 20 arquivos por lote e 150 itens por pasta. " +
        "Escrita somente com opt-in local; Downloads raiz tem restrições do seletor Android. " +
        "A transferência exige o serviço Monitor Remoto ativo e a mesma versão nos dois aparelhos.",
        11.5f, muted = true))
}

internal fun MainActivity.handleRecoveryPickerResult(resultCode: Int, data: Intent?) {
    val selectedUri = data?.data
    if (resultCode == android.app.Activity.RESULT_OK && selectedUri != null) {
        val outcome = runCatching {
            br.dev.monitorremoto.core.SharedDataFolder.authorize(this, selectedUri, data?.flags ?: 0)
        }
        message(outcome.fold({ "Pasta compartilhada para leitura. Escrita remota continua desativada." },
            { "Não foi possível autorizar a pasta: ${it.message ?: "permissão recusada"}" }))
    } else message("Seleção cancelada: nenhuma pasta nova foi autorizada.")
}

private fun MainActivity.fillVersionNewsSettings(layout: LinearLayout) {
    dialogIntro(layout, "Veja novamente as melhorias desta versão.")
    layout.addView(iconMenuRow(R.drawable.ic_info, "Abrir novidades", "Versão ${AppVersion.NAME}") { showVersionNews() })
}

private fun MainActivity.fillHealthSettings(layout: LinearLayout) {
    dialogIntro(layout, "Opções locais deste aparelho. Monitorado e controlador podem ter configurações diferentes.")
    val local = SettingsStore.localHealthAlerts(this)
    layout.addView(iconMenuRow(R.drawable.ic_bell, "Alertas Android neste aparelho",
        if (local) "Avisos de bateria/temperatura habilitados" else "Avisos locais desativados",
        badge = SettingsBadgeKind.forEnabled(local)) {
        SettingsStore.setLocalHealthAlerts(this, !local)
        openSettingsSection("health")
        if (!local) requestHealthNotificationPermission()
    })
    sectionGap(layout, 9)
    val protect = SettingsStore.thermalProtection(this)
    layout.addView(iconMenuRow(R.drawable.ic_shield, "Proteger contra aquecimento severo",
        if (protect) "Pode encerrar tela e câmera do Monitor Remoto" else "Desligada · não controla aplicativos externos",
        badge = SettingsBadgeKind.forEnabled(protect)) {
        if (protect) {
            SettingsStore.setThermalProtection(this, false)
            openSettingsSection("health")
        } else showAppConfirm("Ativar proteção térmica?",
            "Se o Android informar estado térmico severo ou a bateria atingir temperatura crítica, o Monitor Remoto interromperá suas próprias capturas de tela e câmera. Não interrompe gravação do Minha Câmera nem altera carregamento físico. A proteção do fabricante continua independente.",
            R.drawable.ic_shield, "Ativar proteção") {
            SettingsStore.setThermalProtection(this, true)
            openSettingsSection("health")
        }
    })
    sectionGap(layout, 9)
    val remote = SettingsStore.controllerHealthAlerts(this)
    layout.addView(iconMenuRow(R.drawable.ic_phone, "Receber alertas de aparelho remoto",
        if (remote) "Avisos durante consulta e tarefa periódica (mínimo 15 min)" else "Desativado · ative neste celular controlador",
        badge = SettingsBadgeKind.forEnabled(remote)) {
        if (remote) {
            SettingsStore.setControllerHealthAlerts(this, false)
            openSettingsSection("health")
        } else showAppConfirm("Ativar alertas remotos?",
            "Este celular consultará periodicamente o aparelho salvo usando a chave de pareamento. No segundo plano, o Android pode adiar ou bloquear a tarefa e o Wi-Fi deve alcançar o monitorado. A consulta tem custo de bateria. Não garante aviso instantâneo.",
            R.drawable.ic_bell, "Ativar alertas") {
            SettingsStore.setControllerHealthAlerts(this, true)
            openSettingsSection("health")
            requestHealthNotificationPermission()
        }
    })
    sectionGap(layout, 12)
    val health = br.dev.monitorremoto.core.HealthAlertMonitor.snapshot(this)
    layout.addView(dialogMetricRow("Estado local", health.optString("level", "Sem avaliação")))
    layout.addView(dialogMetricRow("Motivo", health.optString("reason", "Sem leitura")))
    layout.addView(label("Faixas sugeridas: bateria ≤20% ou ≥38 °C para atenção; ≤10% ou ≥43 °C para alerta crítico. O Android pode sinalizar severidade térmica independentemente. Esses limites são orientativos e têm histerese.", 12f, muted = true))
}

private fun MainActivity.requestHealthNotificationPermission() {
    if (Build.VERSION.SDK_INT >= 33 &&
        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 9268)
    }
}

/** O usuário ativa o FGS de localização pela tela visível do próprio monitorado. */
internal fun MainActivity.fillLocationSettings(layout: LinearLayout) {
    val active = br.dev.monitorremoto.service.LocationShareService.running
    val profile = SettingsStore.locationProfile(this)
    val hasPermission = br.dev.monitorremoto.core.LocationShareStore.hasPermission(this)
    val enabled = br.dev.monitorremoto.core.LocationShareStore.locationEnabled(this)
    dialogIntro(layout, "Configure uma vez no aparelho monitorado. No modo recuperação, o GPS continua disponível com a tela apagada enquanto o Android permitir, sem novo toque a cada consulta. A notificação indica quando está ativo.")
    layout.addView(dialogMetricRow("Sessão", if (active) "Ativa" else "Desligada"))
    layout.addView(dialogMetricRow("Permissão Android", if (hasPermission) "Concedida" else "Não autorizada"))
    layout.addView(dialogMetricRow("Localização Android", if (enabled) "Ligada" else "Desligada"))
    val recovery = SettingsStore.locationRecoveryEnabled(this)
    layout.addView(dialogMetricRow("Modo recuperação", if (recovery) "Armado" else "Desligado"))
    layout.addView(dialogMetricRow("Acesso em segundo plano",
        if (br.dev.monitorremoto.core.LocationShareStore.hasBackgroundPermission(this)) "Concedido" else "Não concedido"))
    layout.addView(iconMenuRow(R.drawable.ic_pin,
        if (recovery) "Desligar recuperação automática" else "Ativar recuperação automática",
        if (recovery) "Permanente até você desligar" else "Configuração única no aparelho",
        actionLabel = if (recovery) "DESLIGAR" else "ATIVAR") {
        if (recovery) {
            SettingsStore.setLocationRecoveryEnabled(this, false)
            br.dev.monitorremoto.service.LocationShareService.stop(this)
            openSettingsSection("location")
        } else {
            showManagedDialog(AlertDialog.Builder(this)
                .setTitle("Ativar localização para recuperação?")
                .setMessage("O aparelho continuará compartilhando localização com controladores já pareados enquanto o monitoramento estiver ativo, inclusive com a tela apagada. Não precisa confirmar cada consulta. A opção de iniciar monitoramento após reiniciar também será habilitada. O Android poderá exigir acesso em segundo plano para restaurar após reiniciar. Uma notificação mostrará a sessão e permitirá pará-la. Não funciona fora do alcance da rede ou VPN configurada.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Ativar") { _, _ ->
                    if (!br.dev.monitorremoto.core.LocationShareStore.hasPermission(this)) {
                        showAppNotice("Autorize primeiro o GPS", "Conceda localização precisa ou aproximada no Android, depois retorne e ative a recuperação.")
                        requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 9275)
                    } else if (!br.dev.monitorremoto.core.LocationShareStore.locationEnabled(this)) {
                        showAppNotice("GPS do Android desligado", "Ligue a localização e ative novamente o modo recuperação.")
                    } else if (!SettingsStore.isMonitorEnabled(this) || !MonitorService.isRunning) {
                        showAppNotice("Monitoramento desligado", "Inicie o monitoramento do aparelho antes de armar a recuperação.")
                    } else {
                        SettingsStore.setLocationRecoveryEnabled(this, true)
                        SettingsStore.setAutoStart(this, true)
                        startLocationSharingFromSettings()
                        openSettingsSection("location")
                    }
                })
        }
    })
    if (recovery && !br.dev.monitorremoto.core.LocationShareStore.hasBackgroundPermission(this) && Build.VERSION.SDK_INT >= 29) {
        layout.addView(actionButton("Permitir localização o tempo todo no Android") {
            showAppNotice("Permissão para recuperação após reinício", "Abra Permissões → Localização → Permitir o tempo todo nas configurações deste aplicativo, quando essa opção estiver disponível. Sem ela, a localização ativa pode funcionar, mas a restauração após reiniciar não é garantida.")
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                android.net.Uri.parse("package:$packageName")))
        })
    }
    layout.addView(iconMenuRow(R.drawable.ic_pin, "Perfil de localização",
        br.dev.monitorremoto.core.LocationSharePolicy.label(profile), actionLabel = "ALTERAR") {
        if (active) {
            showAppNotice("Sessão em andamento", "Pare a sessão antes de alterar o intervalo GPS.")
        } else {
            showManagedDialog(AlertDialog.Builder(this)
                .setTitle("Intervalo de localização")
                .setSingleChoiceItems(arrayOf("Econômico · 5 minutos", "Equilibrado · 60 segundos", "Intermediário · 30 segundos", "Preciso · 10 segundos"),
                    listOf("economico", "equilibrado", "intermediario", "preciso").indexOf(profile)) { dialog, which ->
                    SettingsStore.setLocationProfile(this, listOf("economico", "equilibrado", "intermediario", "preciso")[which])
                    dialog.dismiss(); openSettingsSection("location")
                })
        }
    })
    sectionGap(layout, 10)
    if (active) layout.addView(button("Parar compartilhamento", emphasis = true) {
        br.dev.monitorremoto.service.LocationShareService.stop(this)
        openSettingsSection("location")
    }) else layout.addView(button("Iniciar compartilhamento", emphasis = true) {
        startLocationSharingFromSettings()
    })
    if (!enabled) layout.addView(actionButton("Abrir localização do Android") {
        startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
    })
    sectionGap(layout, 7)
    layout.addView(label("No modo recuperação, a posição fica disponível para os controladores pareados sem novo toque, enquanto a sessão e a rede funcionarem. Após reiniciar, a retomada depende de permissões e regras do Android/fabricante. Sem internet/VPN remota, a conexão continua restrita à rede alcançável. Parar revoga o modo e a sessão.", 11.5f, muted = true))
}

internal fun MainActivity.startLocationSharingFromSettings() {
    if (!SettingsStore.isMonitorEnabled(this) || !MonitorService.isRunning) {
        showAppNotice("Ative o monitoramento", "Inicie primeiro o serviço Monitorado neste aparelho.")
        return
    }
    if (!br.dev.monitorremoto.core.LocationShareStore.hasPermission(this)) {
        requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 9275)
        return
    }
    if (android.os.Build.VERSION.SDK_INT >= 33 &&
        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 9276)
        return
    }
    if (!br.dev.monitorremoto.core.LocationShareStore.locationEnabled(this)) {
        showAppNotice("Localização desligada", "Ative a localização nas Configurações do Android e retorne para iniciar a sessão.")
        return
    }
    try {
        val intent = Intent(this, br.dev.monitorremoto.service.LocationShareService::class.java)
            .setAction(br.dev.monitorremoto.service.LocationShareService.ACTION_START)
        startForegroundService(intent)
        message("Solicitação de compartilhamento enviada. Confira a notificação permanente.")
        openSettingsSection("location")
    } catch (error: Exception) {
        message("Não foi possível iniciar a localização: ${error.message}")
    }
}

internal fun MainActivity.onLocationPermissionsResult(requestCode: Int, results: IntArray) {
    if (results.any { it == PackageManager.PERMISSION_GRANTED }) startLocationSharingFromSettings()
    else message(if (requestCode == 9275) "Localização não autorizada; compartilhamento desligado."
        else "Notificações não autorizadas; compartilhamento desligado.")
}
