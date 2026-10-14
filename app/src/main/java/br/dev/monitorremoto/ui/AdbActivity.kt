package br.dev.monitorremoto.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.*
import br.dev.monitorremoto.adb.AdbClient
import br.dev.monitorremoto.adb.AdbDiscovery
import br.dev.monitorremoto.adb.AdbIdentity
import br.dev.monitorremoto.adb.AdbPolicy
import br.dev.monitorremoto.core.SettingsStore
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import java.util.concurrent.Executors

/** Fluxo ADB explícito com visual mais simples: selecionar papel, parear e só depois conectar. */
class AdbActivity : Activity() {
    private enum class Page { START, ROLE, METHOD, QR, CODE, CONNECT, CONNECTED, MONITORED, MONITORED_CHECK, TOOLS }
    private enum class ButtonTone { PRIMARY, SECONDARY, OUTLINE }

    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private var client: AdbClient? = null
    private var discovery: AdbDiscovery? = null
    private var qr: AdbPolicy.QrSession? = null
    private var generation = 0
    private var busy = false
    private var visible = false
    private var pairedThisSession = false
    private var page = Page.START
    private var advancedVisible = false
    private val pages = linkedMapOf<Page, LinearLayout>()
    private val seen = mutableSetOf<String>()
    private var reportedPhone = ""

    private lateinit var progress: TextView
    private lateinit var connectionLabel: TextView
    private lateinit var summaryTitle: TextView
    private lateinit var summarySubtitle: TextView
    private lateinit var summaryMeta: TextView
    private lateinit var status: TextView
    private lateinit var devices: LinearLayout
    private lateinit var discoveryCard: LinearLayout
    private lateinit var image: ImageView
    private lateinit var host: EditText
    private lateinit var connectionHost: EditText
    private lateinit var pairPort: EditText
    private lateinit var connectPort: EditText
    private lateinit var code: EditText
    private lateinit var scroll: ScrollView
    private lateinit var primaryActionButton: Button
    private lateinit var secondaryActionButton: Button
    private lateinit var startHint: TextView
    private lateinit var advancedToggleButton: Button
    private lateinit var advancedPanel: LinearLayout

    private val dark get() = when (SettingsStore.theme(this)) {
        "dark" -> true
        "system" -> resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        else -> false
    }
    private val fg get() = if (dark) Color.WHITE else Color.rgb(24, 31, 40)
    private val muted get() = if (dark) Color.rgb(184, 194, 210) else Color.rgb(103, 114, 127)
    private val blue get() = if (dark) Color.rgb(82, 144, 235) else Color.rgb(42, 103, 204)
    private val appBg get() = if (dark) Color.rgb(15, 21, 31) else Color.rgb(247, 248, 251)
    private val cardBg get() = if (dark) Color.rgb(29, 36, 47) else Color.WHITE
    private val softCardBg get() = if (dark) Color.rgb(36, 45, 58) else Color.rgb(244, 246, 249)
    private val line get() = if (dark) Color.rgb(61, 72, 89) else Color.rgb(225, 230, 236)
    private val successBg get() = if (dark) Color.rgb(39, 74, 54) else Color.rgb(225, 245, 234)
    private val successFg get() = if (dark) Color.rgb(133, 235, 181) else Color.rgb(28, 141, 84)
    private val warningBg get() = if (dark) Color.rgb(84, 61, 25) else Color.rgb(255, 243, 214)
    private val warningFg get() = if (dark) Color.rgb(255, 216, 129) else Color.rgb(154, 102, 11)
    private val neutralChipBg get() = if (dark) Color.rgb(52, 61, 74) else Color.rgb(237, 240, 245)
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()

    private fun rounded(fill: Int, radius: Int = 20, strokeWidth: Int = 0, strokeColor: Int = line) =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(radius).toFloat()
            if (strokeWidth > 0) setStroke(dp(strokeWidth), strokeColor)
        }

    private fun text(
        value: String,
        size: Float = 14f,
        secondary: Boolean = false,
        bold: Boolean = false,
        center: Boolean = false
    ) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(if (secondary) muted else fg)
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        setLineSpacing(0f, 1.12f)
        if (center) gravity = Gravity.CENTER_HORIZONTAL
        setPadding(0, dp(4), 0, dp(4))
    }

    private fun button(
        label: String,
        tone: ButtonTone = ButtonTone.SECONDARY,
        allowBusy: Boolean = false,
        action: () -> Unit
    ) = Button(this).apply {
        text = label
        isAllCaps = false
        minHeight = dp(52)
        elevation = 0f
        when (tone) {
            ButtonTone.PRIMARY -> {
                background = rounded(blue, 16)
                setTextColor(Color.WHITE)
            }
            ButtonTone.SECONDARY -> {
                background = rounded(softCardBg, 16)
                setTextColor(fg)
            }
            ButtonTone.OUTLINE -> {
                background = rounded(cardBg, 16, 1)
                setTextColor(fg)
            }
        }
        setOnClickListener { if (allowBusy || !busy) action() }
    }

    private fun field(label: String, numeric: Boolean = false, secret: Boolean = false) = EditText(this).apply {
        hint = label
        setSingleLine()
        setPadding(dp(16), dp(14), dp(16), dp(14))
        setTextColor(this@AdbActivity.fg)
        setHintTextColor(muted)
        background = rounded(softCardBg, 14, 1)
        isSaveEnabled = false
        inputType = if (secret) {
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        } else if (numeric) {
            InputType.TYPE_CLASS_NUMBER
        } else {
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        }
        importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        layoutParams = LinearLayout.LayoutParams(-1, dp(56)).apply { bottomMargin = dp(10) }
    }

    private fun card(root: LinearLayout, title: String? = null): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(18), dp(18), dp(18))
        background = rounded(cardBg, 22, 1)
        elevation = 0f
        root.addView(this, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) })
        if (!title.isNullOrBlank()) addView(text(title, 14f, secondary = true, bold = true))
    }

    private fun page(root: LinearLayout, step: Page, headline: String): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            root.addView(this, LinearLayout.LayoutParams(-1, -2))
            if (headline.isNotBlank()) addView(text(headline, 24f, bold = true))
            pages[step] = this
        }

    private fun back(target: Page, label: String = "‹ Voltar") =
        button(label, tone = ButtonTone.OUTLINE, allowBusy = true) { go(target) }

    private fun option(root: LinearLayout, title: String, explanation: String, action: () -> Unit) {
        val tile = card(root)
        tile.addView(text(title, 22f, bold = true))
        tile.addView(text(explanation, 14f, secondary = true))
        tile.addView(button("Continuar", tone = ButtonTone.PRIMARY, action = action))
    }

    private fun stepCard(root: LinearLayout, stepNumber: Int, title: String, body: String): LinearLayout {
        val step = card(root)
        step.addView(text("PASSO $stepNumber", 12f, secondary = true, bold = true))
        step.addView(text(title, 18f, bold = true))
        step.addView(text(body, 14f, secondary = true))
        return step
    }

    private fun applyChip(view: TextView, label: String, fill: Int, color: Int) {
        view.text = label
        view.setTextColor(color)
        view.typeface = Typeface.DEFAULT_BOLD
        view.background = rounded(fill, 999)
    }

    private fun setAdvancedVisible(show: Boolean) {
        advancedVisible = show
        if (this::advancedPanel.isInitialized) advancedPanel.visibility = if (show) View.VISIBLE else View.GONE
        if (this::advancedToggleButton.isInitialized) {
            advancedToggleButton.text = if (show) "Ocultar configurações avançadas ▲" else "Mostrar configurações avançadas ▼"
        }
    }

    private fun resetWizardFields() {
        pairedThisSession = false
        host.text.clear()
        pairPort.text.clear()
        connectPort.text.clear()
        connectionHost.text.clear()
        code.text.clear()
        clearQr()
    }

    private fun startWizard() {
        resetWizardFields()
        status.text = "Selecione o papel deste telefone."
        go(Page.ROLE)
    }

    private fun go(next: Page) {
        if (page == Page.QR && next != Page.QR) clearQr()
        page = next
        pages.forEach { (key, view) -> view.visibility = if (key == next) View.VISIBLE else View.GONE }
        discoveryCard.visibility = if (next in listOf(Page.QR, Page.CODE, Page.CONNECT, Page.TOOLS)) View.VISIBLE else View.GONE
        progress.visibility = if (next in listOf(Page.START, Page.CONNECTED, Page.TOOLS)) View.GONE else View.VISIBLE
        progress.text = when (next) {
            Page.ROLE -> "ETAPA 1 DE 3  •  ESCOLHER PAPEL"
            Page.METHOD, Page.QR, Page.CODE, Page.MONITORED -> "ETAPA 2 DE 3  •  AUTORIZAR E PAREAR"
            else -> "ETAPA 3 DE 3  •  CONFERIR CONEXÃO"
        }
        updateConnection()
        scroll.post { scroll.smoothScrollTo(0, 0) }
    }

    private fun updateConnection() {
        if (!this::connectionLabel.isInitialized) return
        val active = client
        val connected = active?.connected() == true
        val state = AdbWizardStatus.resolve(
            connected,
            pairedThisSession,
            page == Page.MONITORED || page == Page.MONITORED_CHECK
        )
        when (state) {
            AdbWizardStatus.State.CONNECTED -> applyChip(connectionLabel, "Conectado", successBg, successFg)
            AdbWizardStatus.State.PAIRED_NOT_CONNECTED -> applyChip(connectionLabel, "Pareado", warningBg, warningFg)
            AdbWizardStatus.State.MONITORED_NOT_VERIFIABLE -> applyChip(connectionLabel, "Aguardando", neutralChipBg, fg)
            AdbWizardStatus.State.DISCONNECTED -> applyChip(connectionLabel, "Desconectado", neutralChipBg, muted)
        }

        when (state) {
            AdbWizardStatus.State.CONNECTED -> {
                val headline = reportedPhone.takeIf { it.isNotBlank() }
                    ?: active?.endpoint?.let { "Aparelho em $it" }
                    ?: "Aparelho conectado"
                summaryTitle.text = headline
                summarySubtitle.text = "Conexão ADB ativa"
                summaryMeta.text = active?.endpoint ?: "Endereço indisponível"
                status.text = "Conexão ADB confirmada. Você já pode abrir os controles ou desconectar."
            }
            AdbWizardStatus.State.PAIRED_NOT_CONNECTED -> {
                summaryTitle.text = "Pareado nesta tentativa"
                summarySubtitle.text = "Falta conectar com a porta ADB"
                summaryMeta.text = "Abra a etapa Conectar e informe o IP e a porta de conexão."
            }
            AdbWizardStatus.State.MONITORED_NOT_VERIFIABLE -> {
                summaryTitle.text = "Este celular será controlado"
                summarySubtitle.text = "A autorização é feita no Android"
                summaryMeta.text = "A confirmação real aparece no controlador após ele conectar."
            }
            AdbWizardStatus.State.DISCONNECTED -> {
                summaryTitle.text = "Nenhum aparelho conectado"
                summarySubtitle.text = "Use o assistente para parear e conectar"
                summaryMeta.text = "Uma credencial salva não prova conexão ativa."
            }
        }

        if (this::primaryActionButton.isInitialized) {
            if (connected) {
                primaryActionButton.text = "Controlar aparelho"
                secondaryActionButton.text = "Desconectar"
                startHint.text = "O ADB conectou de verdade. O nome exibido vem do Android do aparelho remoto."
            } else {
                primaryActionButton.text = "+ Configurar novo"
                secondaryActionButton.text = if (pairedThisSession) "Continuar · Conectar" else "Já autorizei antes · Conectar"
                startHint.text = "O assistente mostra cada etapa e diferencia autorização, pareamento e conexão ativa."
            }
        }
    }

    override fun onCreate(state: Bundle?) {
        setTheme(if (dark) android.R.style.Theme_Material_NoActionBar else android.R.style.Theme_Material_Light_NoActionBar)
        super.onCreate(state)
        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(20))
        }
        scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(root)
            setBackgroundColor(appBg)
            setOnApplyWindowInsetsListener { v, inset ->
                v.setPadding(
                    inset.systemWindowInsetLeft,
                    inset.systemWindowInsetTop,
                    inset.systemWindowInsetRight,
                    inset.systemWindowInsetBottom
                )
                inset
            }
        }
        setContentView(scroll)
        scroll.requestApplyInsets()

        root.addView(button("‹ Configurações", tone = ButtonTone.SECONDARY, allowBusy = true) {
            if (busy) cancel()
            finish()
        })

        if (android.os.Build.VERSION.SDK_INT >= 30) {
            val lightBars = android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            window.insetsController?.setSystemBarsAppearance(if (dark) 0 else lightBars, lightBars)
        } else {
            window.decorView.systemUiVisibility = if (dark) 0 else
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }

        root.addView(text("Modo avançado ADB", 30f, bold = true))
        root.addView(text("Conexão por Wi-Fi entre dois celulares, com autorização no Android.", secondary = true))

        val stateCard = card(root)
        val cardHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val headerTexts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        headerTexts.addView(text("Monitor Remoto", 16f, bold = true))
        headerTexts.addView(text("Resumo do modo ADB", 12f, secondary = true))
        cardHeader.addView(headerTexts)
        connectionLabel = text("Desconectado", 13f, bold = true).apply {
            setPadding(dp(14), dp(8), dp(14), dp(8))
        }
        cardHeader.addView(connectionLabel)
        stateCard.addView(cardHeader)
        summaryTitle = text("Nenhum aparelho conectado", 22f, bold = true)
        stateCard.addView(summaryTitle)
        summarySubtitle = text("Use o assistente para parear e conectar", 15f, secondary = true)
        stateCard.addView(summarySubtitle)
        summaryMeta = text("Uma credencial salva não prova conexão ativa.", 14f, secondary = true)
        stateCard.addView(summaryMeta)
        status = text("Escolha Configurar novo para começar.", 13f, secondary = true)
        stateCard.addView(status)

        progress = text("ETAPA 1 DE 3", 12f, secondary = true, bold = true)
        progress.setPadding(dp(6), dp(8), 0, dp(10))
        root.addView(progress)

        val start = page(root, Page.START, "")
        primaryActionButton = button("+ Configurar novo", tone = ButtonTone.PRIMARY, allowBusy = true) {
            if (client?.connected() == true) {
                go(Page.CONNECTED)
            } else {
                startWizard()
            }
        }
        start.addView(primaryActionButton)
        secondaryActionButton = button("Já autorizei antes · Conectar", tone = ButtonTone.OUTLINE, allowBusy = true) {
            if (client?.connected() == true) {
                cancel()
                status.text = "Desconectado manualmente."
                go(Page.START)
            } else {
                status.text = "Autorização anterior não verificada. Informe a porta atual de conexão ADB."
                go(Page.CONNECT)
            }
        }
        start.addView(secondaryActionButton)
        advancedToggleButton = button("Mostrar configurações avançadas ▼", tone = ButtonTone.OUTLINE, allowBusy = true) {
            setAdvancedVisible(!advancedVisible)
        }
        start.addView(advancedToggleButton)
        advancedPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        val advancedCard = card(advancedPanel, "CONFIGURAÇÕES AVANÇADAS")
        advancedCard.addView(text("Abra somente se precisar diagnosticar portas, limpar a credencial local ou usar descoberta mDNS.", 14f, secondary = true))
        advancedCard.addView(button("Ferramentas e diagnóstico ADB", tone = ButtonTone.SECONDARY) { go(Page.TOOLS) })
        advancedCard.addView(button("Reiniciar configuração guiada", tone = ButtonTone.SECONDARY, allowBusy = true) { startWizard() })
        start.addView(advancedPanel)
        startHint = text("O assistente mostra cada etapa e diferencia autorização, pareamento e conexão ativa.", 14f, secondary = true)
        start.addView(startHint)
        start.addView(text("O ADB é experimental e não transmite a tela nem desbloqueia PIN ou biometria. Para ver a tela, use o modo normal do Monitor Remoto com MediaProjection.", 13f, secondary = true))

        val role = page(root, Page.ROLE, "Qual será a função deste celular?")
        option(
            role,
            "Vou controlar outro celular",
            "Este aparelho será o controlador. Ele gera o QR ou recebe o código, depois faz a conexão ADB."
        ) {
            status.text = "Controlador selecionado. Escolha QR ou código."
            go(Page.METHOD)
        }
        option(
            role,
            "Vou permitir controle deste celular",
            "Este é o celular monitorado. Aqui você só autoriza a Depuração sem fios no Android e segue as etapas."
        ) {
            status.text = "No monitorado, a autorização é feita nas Configurações do Android."
            go(Page.MONITORED)
        }
        role.addView(back(Page.START))

        val method = page(root, Page.METHOD, "Como deseja parear?")
        method.addView(text("Mantenha os dois celulares no mesmo Wi-Fi. O QR costuma ser mais fácil; use código se o leitor do Android não abrir ou não conseguir ler.", secondary = true))
        option(
            method,
            "QR Code · recomendado",
            "Este controlador mostra um QR temporário. O celular monitorado abre a câmera do próprio Android para ler esse QR."
        ) {
            status.text = "Gere o QR e leia-o no telefone monitorado."
            go(Page.QR)
        }
        option(
            method,
            "Código de seis dígitos",
            "Alternativa manual. Você copia IP, porta de pareamento e os seis dígitos mostrados no Android do monitorado."
        ) {
            status.text = "Informe o código exibido no Android do monitorado."
            go(Page.CODE)
        }
        method.addView(back(Page.ROLE))

        val qrPage = page(root, Page.QR, "Parear usando QR")
        val qrStep1 = stepCard(
            qrPage,
            1,
            "Gerar QR neste controlador",
            "Toque no botão abaixo. O QR vale por até 2 minutos e só deve ser lido pelo outro celular."
        )
        qrStep1.addView(button("Gerar QR temporário · 2 minutos", tone = ButtonTone.PRIMARY) { generateQr() })
        image = ImageView(this).apply {
            contentDescription = "QR temporário de pareamento ADB"
            visibility = View.GONE
            adjustViewBounds = true
        }
        qrStep1.addView(image, LinearLayout.LayoutParams(-1, dp(252)))
        qrStep1.addView(text("Enquanto o QR estiver visível, as capturas desta tela ficam bloqueadas para proteger a credencial temporária.", 13f, secondary = true))
        qrStep1.addView(button("Ocultar QR e liberar captura de tela", tone = ButtonTone.OUTLINE, allowBusy = true) {
            clearQr()
            status.text = "QR oculto. Capturas liberadas."
        })
        stepCard(
            qrPage,
            2,
            "Ler o QR no celular monitorado",
            "No OUTRO celular: Configurações → Opções do desenvolvedor → Depuração sem fios → Emparelhar dispositivo com código QR. Use a câmera aberta pelo Android do monitorado para escanear o QR deste controlador."
        )
        stepCard(
            qrPage,
            3,
            "Concluir o pareamento",
            "Aguarde a mensagem de que o dispositivo foi pareado. Depois siga para a etapa de conexão, porque parear não significa que o ADB já está conectado."
        )
        qrPage.addView(button("Já autorizei · Ir para conexão", tone = ButtonTone.PRIMARY) {
            status.text = "Pareamento não confirmado aqui. Tente conectar usando a porta de conexão atual."
            go(Page.CONNECT)
        })
        qrPage.addView(back(Page.METHOD))

        val codePage = page(root, Page.CODE, "Parear usando código")
        stepCard(
            codePage,
            1,
            "Abrir o pareamento por código no monitorado",
            "No telefone monitorado: Configurações → Opções do desenvolvedor → Depuração sem fios → Emparelhar dispositivo com código de pareamento."
        )
        stepCard(
            codePage,
            2,
            "Copiar os dados mostrados no Android",
            "Copie o IP, a PORTA DE PAREAMENTO e os seis dígitos. Atenção: a porta de conexão é outra e será usada depois."
        )
        val codeCard = stepCard(
            codePage,
            3,
            "Preencher e parear aqui",
            "Informe os dados abaixo exatamente como aparecem no Android do monitorado."
        )
        host = field("IP do monitorado (rede local)")
        codeCard.addView(host)
        pairPort = field("Porta de pareamento", true)
        codeCard.addView(pairPort)
        code = field("Código de seis dígitos", secret = true)
        codeCard.addView(code)
        codeCard.addView(button("Parear por código", tone = ButtonTone.PRIMARY) {
            validate {
                val endpoint = AdbPolicy.endpoint(host.text.toString().trim(), pairPort.text.toString().trim())
                val password = AdbPolicy.code(code.text.toString().trim())
                code.text.clear()
                clearQr()
                runOperation(
                    "Pareando…",
                    operation = { adb ->
                        adb.pair(endpoint.first, endpoint.second, password)
                        "Pareamento aceito por ${endpoint.first}. Agora conecte usando a outra porta."
                    },
                    onSuccess = {
                        pairedThisSession = true
                        connectionHost.setText(endpoint.first)
                        status.text = "Pareado! Falta conectar com a porta ADB de conexão."
                        go(Page.CONNECT)
                    }
                )
            }
        })
        codePage.addView(back(Page.METHOD))

        val connect = page(root, Page.CONNECT, "Conectar ao telefone monitorado")
        stepCard(
            connect,
            1,
            "Abrir a tela principal de Depuração sem fios no monitorado",
            "No aparelho monitorado, volte para Depuração sem fios e localize “Endereço IP e porta”. É essa porta de conexão que deve ser usada aqui."
        )
        stepCard(
            connect,
            2,
            "Copiar IP e porta de conexão",
            "Essa porta costuma ser diferente da porta de pareamento. Ela pode mudar, então confira sempre no Android antes de conectar."
        )
        val connectCard = stepCard(
            connect,
            3,
            "Informar os dados e conectar",
            "Digite o IP e a porta de conexão ou use a descoberta mDNS logo abaixo apenas para preencher os campos."
        )
        connectionHost = field("IP do monitorado (rede local)")
        connectCard.addView(connectionHost)
        connectPort = field("Porta de conexão ADB", true)
        connectCard.addView(connectPort)
        connectCard.addView(button("Conectar e identificar telefone", tone = ButtonTone.PRIMARY) {
            validate {
                val endpoint = AdbPolicy.endpoint(connectionHost.text.toString().trim(), connectPort.text.toString().trim())
                runOperation(
                    "Conectando…",
                    operation = { adb ->
                        adb.connect(endpoint.first, endpoint.second)
                        val lines = runCatching { adb.execute(AdbPolicy.Command.DIAGNOSTIC) }
                            .getOrNull()?.lines()?.map { it.trim().take(72) }.orEmpty()
                        val model = lines.getOrNull(0)?.takeIf { it.isNotBlank() }.orEmpty()
                        val android = lines.getOrNull(1)?.takeIf { it.isNotBlank() }.orEmpty()
                        val summary = if (model.isBlank()) {
                            "Modelo não disponível"
                        } else {
                            "Modelo informado: $model" + (if (android.isNotBlank()) " • Android $android" else "")
                        }
                        "Conectado a ${endpoint.first}:${endpoint.second}\n$summary"
                    },
                    onSuccess = { result ->
                        if (client?.connected() == true) {
                            reportedPhone = result.substringAfter('\n', "Modelo não disponível")
                            status.text = "Conectado a aparelho identificado pelo Android."
                            go(Page.CONNECTED)
                        } else {
                            status.text = "ADB desconectou antes da confirmação. Confira a porta atual e tente novamente."
                        }
                    }
                )
            }
        })
        connect.addView(button("Ainda preciso parear", tone = ButtonTone.OUTLINE) { go(Page.METHOD) })
        connect.addView(back(Page.START, "‹ Início"))

        val connected = page(root, Page.CONNECTED, "Aparelho conectado")
        val activeCard = card(connected, "AÇÕES DISPONÍVEIS")
        activeCard.addView(text("Quando o ADB está ativo, o resumo acima mostra o aparelho informado pelo Android. Use os botões abaixo para abrir os controles ou desconectar.", 14f, secondary = true))
        activeCard.addView(button("Consultar modelo e versão Android", tone = ButtonTone.SECONDARY) {
            runOperation(
                "Consultando…",
                operation = { adb -> "Diagnóstico ADB:\n" + adb.execute(AdbPolicy.Command.DIAGNOSTIC) }
            )
        })
        val commands = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf(
            "Voltar" to AdbPolicy.Command.BACK,
            "Início" to AdbPolicy.Command.HOME,
            "Recentes" to AdbPolicy.Command.RECENTS
        ).forEach { (label, cmd) ->
            commands.addView(button(label, tone = ButtonTone.SECONDARY) {
                if (client?.connected() != true) {
                    status.text = "Sem conexão ativa. Reconecte antes de enviar comandos."
                    go(Page.CONNECT)
                } else {
                    AlertDialog.Builder(this)
                        .setTitle("Enviar $label?")
                        .setMessage("Executar por ADB em ${client?.endpoint ?: "nenhum aparelho conectado"}.")
                        .setNegativeButton("Cancelar", null)
                        .setPositiveButton("Enviar") { _, _ ->
                            runOperation(
                                "Enviando…",
                                operation = { adb ->
                                    adb.execute(cmd)
                                    "Comando enviado. Confira o resultado no monitorado."
                                }
                            )
                        }
                        .show()
                }
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { if (commands.childCount > 0) marginStart = dp(8) })
        }
        activeCard.addView(commands)
        activeCard.addView(button("Desconectar", tone = ButtonTone.OUTLINE, allowBusy = true) {
            cancel()
            status.text = "Desconectado manualmente."
            go(Page.START)
        })
        connected.addView(back(Page.START, "‹ Início"))

        val monitored = page(root, Page.MONITORED, "Permitir controle deste celular")
        monitored.addView(text("Siga os passos abaixo neste celular. O outro aparelho será o controlador e mostrará o QR ou pedirá o código.", secondary = true))
        val monitoredStep1 = stepCard(
            monitored,
            1,
            "Abrir as Configurações do Android",
            "Use o botão abaixo para abrir as Configurações deste celular. Se o atalho de desenvolvedor não abrir, o app volta para Configurações gerais como alternativa."
        )
        monitoredStep1.addView(button("Abrir Configurações do Android", tone = ButtonTone.PRIMARY) { openAndroidSettings() })
        monitoredStep1.addView(button("Abrir Opções do desenvolvedor", tone = ButtonTone.OUTLINE) { openDeveloperSettings() })
        stepCard(
            monitored,
            2,
            "Ativar Opções do desenvolvedor e Depuração sem fios",
            "Se “Opções do desenvolvedor” não aparecer, abra Sobre o telefone e toque 7 vezes no número da versão. Depois entre em Opções do desenvolvedor e ative Depuração sem fios."
        )
        stepCard(
            monitored,
            3,
            "No outro celular, tocar em Configurar novo",
            "No controlador: Monitor Remoto → Modo avançado ADB → Configurar novo → Vou controlar. Escolha QR ou código."
        )
        stepCard(
            monitored,
            4,
            "Concluir o pareamento",
            "Se escolheu QR, neste celular toque “Emparelhar dispositivo com código QR” e escaneie o QR do controlador. Se escolheu código, leia os seis dígitos e a porta mostrados aqui e informe-os no controlador."
        )
        monitored.addView(button("Já autorizei · Como confirmar?", tone = ButtonTone.OUTLINE) { go(Page.MONITORED_CHECK) })
        monitored.addView(back(Page.ROLE))

        val monitoredCheck = page(root, Page.MONITORED_CHECK, "Como saber se conectou?")
        stepCard(
            monitoredCheck,
            1,
            "No controlador, usar a etapa Conectar",
            "Após o pareamento, o controlador ainda precisa copiar o IP e a porta de conexão mostrados em Depuração sem fios deste celular."
        )
        stepCard(
            monitoredCheck,
            2,
            "Esperar a confirmação no controlador",
            "O controlador deve mostrar o chip “Conectado”, o endereço e o modelo informado pelo Android. Se aparecer “Pareado”, ainda falta conectar."
        )
        stepCard(
            monitoredCheck,
            3,
            "Revogar quando terminar",
            "Para encerrar o acesso, desative Depuração sem fios nas Configurações do Android ou use “Esquecer” nos dispositivos pareados."
        )
        monitoredCheck.addView(text("Nesta tela do monitorado não é possível verificar com segurança se outro cliente ADB está conectado. A confirmação real aparece no controlador.", 13f, secondary = true))
        monitoredCheck.addView(back(Page.MONITORED, "‹ Ver passos novamente"))
        monitoredCheck.addView(button("Concluir orientação", tone = ButtonTone.SECONDARY) {
            status.text = "Orientações concluídas. A confirmação real aparece no controlador."
            go(Page.START)
        })

        val tools = page(root, Page.TOOLS, "Ferramentas e diagnóstico ADB")
        val toolsCard = card(tools, "RECURSOS AVANÇADOS")
        toolsCard.addView(text("Use estas opções somente quando precisar diagnosticar portas, encerrar a sessão ou apagar a credencial ADB local. Os serviços encontrados por mDNS não provam a identidade do telefone.", 14f, secondary = true))
        toolsCard.addView(button("Desconectar / cancelar operação", tone = ButtonTone.SECONDARY, allowBusy = true) {
            cancel()
            status.text = "Desconectado."
            go(Page.START)
        })
        toolsCard.addView(button("Apagar credencial ADB local", tone = ButtonTone.OUTLINE) {
            AlertDialog.Builder(this)
                .setTitle("Apagar identidade ADB?")
                .setMessage("Será necessário parear novamente os aparelhos. No monitorado, use Esquecer em Dispositivos pareados para revogar a chave antiga.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Apagar") { _, _ ->
                    if (busy) {
                        status.text = "Cancele a operação antes de apagar a identidade."
                    } else {
                        cancel()
                        pairedThisSession = false
                        worker.execute {
                            AdbIdentity.forget(applicationContext)
                            handler.post {
                                if (visible) {
                                    status.text = "Credencial local apagada."
                                    updateConnection()
                                }
                            }
                        }
                    }
                }
                .show()
        })
        tools.addView(back(Page.START, "‹ Início"))

        discoveryCard = card(root, "LOCALIZAR PORTA PELO WI-FI · OPCIONAL")
        discoveryCard.addView(text("A descoberta mDNS mostra serviços de pareamento e conexão separadamente. Selecionar um resultado apenas preenche os campos; isso não confirma a conexão. Sempre confira o IP e a porta nas Configurações do Android do monitorado.", 14f, secondary = true))
        discoveryCard.addView(button("Procurar serviços ADB (mDNS)", tone = ButtonTone.SECONDARY) { discover() })
        devices = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        discoveryCard.addView(devices)
        root.addView(text("ADB experimental. O QR ADB não é o QR normal do Monitor Remoto. Ao sair desta tela, a sessão local é encerrada; a autorização pode permanecer no Android até ser revogada manualmente.", 13f, secondary = true))

        setAdvancedVisible(false)
        go(Page.START)
    }

    private fun openAndroidSettings() {
        try {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        } catch (_: Exception) {
            status.text = "Não foi possível abrir as Configurações do Android."
        }
    }

    private fun openDeveloperSettings() {
        val intents = listOf(
            Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )
        var opened = false
        intents.forEach { intent ->
            if (!opened) {
                try {
                    startActivity(intent)
                    opened = true
                } catch (_: Exception) {
                }
            }
        }
        if (!opened) status.text = "Não foi possível abrir as Opções do desenvolvedor."
    }

    private fun validate(action: () -> Unit) {
        try {
            action()
        } catch (e: IllegalArgumentException) {
            status.text = e.message ?: "Dados inválidos"
        }
    }

    private fun clearQr() {
        qr = null
        if (this::image.isInitialized) {
            image.setImageDrawable(null)
            image.visibility = View.GONE
        }
        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    private fun generateQr() {
        if (busy || !visible) return
        clearQr()
        val session = AdbPolicy.newQr(SystemClock.elapsedRealtime())
        qr = session
        try {
            val matrix = MultiFormatWriter().encode(session.payload(), BarcodeFormat.QR_CODE, 512, 512)
            val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
            bitmap.setPixels(IntArray(512 * 512) { i ->
                if (matrix[i % 512, i / 512]) Color.BLACK else Color.WHITE
            }, 0, 512, 0, 0, 512, 512)
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            image.setImageBitmap(bitmap)
            image.visibility = View.VISIBLE
            status.text = "QR ativo até 2 minutos. Escaneie com o Android do monitorado."
            discover()
            handler.postDelayed({
                if (qr === session) {
                    clearQr()
                    discovery?.stop()
                    status.text = "QR expirado. Gere outro se necessário."
                }
            }, 120000)
        } catch (e: Exception) {
            clearQr()
            status.text = "Falha ao gerar QR (${e.javaClass.simpleName}). Tente por código."
        }
    }

    private fun discover() {
        discovery?.stop()
        seen.clear()
        devices.removeAllViews()
        discovery = AdbDiscovery(this, { endpoint ->
            if (visible) {
                val session = qr
                if (
                    session != null && session.valid(SystemClock.elapsedRealtime()) &&
                    endpoint.type.contains("_adb-tls-pairing") && endpoint.name == session.service && !busy
                ) {
                    clearQr()
                    host.setText(endpoint.host)
                    connectionHost.setText(endpoint.host)
                    pairPort.setText(endpoint.port.toString())
                    runOperation(
                        "Pareando pelo QR…",
                        operation = { adb ->
                            adb.pair(endpoint.host, endpoint.port, session.password)
                            "Pareado pelo QR com ${endpoint.host}. Falta conectar com a porta correta."
                        },
                        onSuccess = {
                            pairedThisSession = true
                            status.text = "Pareamento QR confirmado. Agora conecte à porta de conexão."
                            go(Page.CONNECT)
                        }
                    )
                }
                val key = "${endpoint.name}/${endpoint.type}/${endpoint.host}:${endpoint.port}"
                if (seen.size < 16 && seen.add(key)) {
                    val isPair = endpoint.type.contains("_adb-tls-pairing")
                    devices.addView(button(
                        "${if (isPair) "Pareamento" else "Conexão"} · ${endpoint.host}:${endpoint.port} (conferir IP)",
                        tone = ButtonTone.SECONDARY
                    ) {
                        host.setText(endpoint.host)
                        connectionHost.setText(endpoint.host)
                        (if (isPair) pairPort else connectPort).setText(endpoint.port.toString())
                        status.text = "Porta preenchida pela descoberta. Confira no Android antes de prosseguir."
                    })
                }
            }
        }, { if (visible && !busy) status.text = it }).also { it.start() }
        val current = discovery
        handler.postDelayed({
            if (discovery === current) {
                current?.stop()
                if (visible) devices.addView(text("Busca encerrada. Confira as portas no Android e tente novamente, se necessário.", 13f, secondary = true))
            }
        }, 120000)
    }

    private fun runOperation(label: String, operation: (AdbClient) -> String, onSuccess: ((String) -> Unit)? = null) {
        if (busy || !visible) return
        busy = true
        status.text = label
        val token = ++generation
        val active = client ?: AdbClient(applicationContext).also { client = it }
        val timeout = Runnable {
            if (visible && generation == token && busy) {
                cancel()
                status.text = "Tempo limite. Confira Wi-Fi, portas e autorização no Android."
            }
        }
        handler.postDelayed(timeout, 35000)
        worker.execute {
            var message = ""
            var succeeded = false
            try {
                message = operation(active)
                succeeded = true
            } catch (e: Exception) {
                message = "Falha ADB (${e.javaClass.simpleName}). Confira IP, porta, código, Wi-Fi e autorização no Android."
            } catch (e: LinkageError) {
                message = "Biblioteca ADB incompatível (${e.javaClass.simpleName}). Consulte os logs."
            }
            handler.post {
                handler.removeCallbacks(timeout)
                if (visible && generation == token) {
                    busy = false
                    status.text = message
                    if (succeeded) onSuccess?.invoke(message)
                    if (!active.connected()) {
                        active.close()
                        if (client === active) client = null
                    }
                    updateConnection()
                } else {
                    active.close()
                }
            }
        }
    }

    private fun cancel() {
        generation++
        busy = false
        clearQr()
        discovery?.stop()
        discovery = null
        val old = client
        client = null
        old?.requestCancel()
        reportedPhone = ""
        pairedThisSession = false
        if (old != null) Thread { old.close() }.start()
        updateConnection()
    }

    private val connectionWatch = object : Runnable {
        override fun run() {
            if (!visible) return
            val active = client
            if (!busy && active != null && !active.connected()) {
                client = null
                Thread { active.close() }.start()
                status.text = "Conexão ADB encerrada. Verifique Wi-Fi e a porta atual."
                if (page == Page.CONNECTED) go(Page.CONNECT)
            }
            updateConnection()
            handler.postDelayed(this, 2000)
        }
    }

    override fun onStart() {
        super.onStart()
        visible = true
        handler.removeCallbacks(connectionWatch)
        handler.post(connectionWatch)
    }

    override fun onStop() {
        handler.removeCallbacks(connectionWatch)
        visible = false
        cancel()
        code.text.clear()
        if (page == Page.CONNECTED) {
            status.text = "Sessão encerrada ao sair. Reconecte para controlar novamente."
            go(Page.START)
        } else if (page == Page.QR) {
            status.text = "QR cancelado ao sair. Gere outro para continuar."
        }
        super.onStop()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        worker.shutdownNow()
        super.onDestroy()
    }
}
