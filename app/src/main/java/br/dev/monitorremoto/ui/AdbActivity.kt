package br.dev.monitorremoto.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.view.WindowManager
import android.widget.*
import br.dev.monitorremoto.adb.*
import br.dev.monitorremoto.core.SettingsStore
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import java.util.concurrent.Executors

/** Fluxo ADB explícito: selecionar papel, parear e só depois conectar. Nenhum estado falso persistido. */
class AdbActivity : Activity() {
    private enum class Page { START, ROLE, METHOD, QR, CODE, CONNECT, CONNECTED, MONITORED, MONITORED_CHECK, TOOLS }

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
    private val pages = linkedMapOf<Page, LinearLayout>()
    private val seen = mutableSetOf<String>()
    private var reportedPhone = ""
    private lateinit var progress: TextView
    private lateinit var connectionLabel: TextView
    private lateinit var deviceInfo: TextView
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

    private val dark get() = when (SettingsStore.theme(this)) {
        "dark" -> true
        "system" -> resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        else -> false
    }
    private val fg get() = if (dark) Color.WHITE else Color.rgb(23, 34, 48)
    private val muted get() = if (dark) Color.rgb(179, 193, 211) else Color.rgb(83, 99, 116)
    private val blue get() = if (dark) Color.rgb(95, 159, 244) else Color.rgb(31, 97, 187)
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun background(color: Int, radius: Int = 16) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
    }
    private fun text(value: String, size: Float = 14f, secondary: Boolean = false) = TextView(this).apply {
        text = value; textSize = size; setTextColor(if (secondary) muted else fg)
        setPadding(0, dp(6), 0, dp(6))
    }
    private fun button(label: String, prominent: Boolean = false, allowBusy: Boolean = false, action: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; minHeight = dp(52)
        if (prominent) {
            background = background(blue, 12)
            setTextColor(Color.WHITE)
        }
        setOnClickListener { if (allowBusy || !busy) action() }
    }
    private fun field(label: String, numeric: Boolean = false, secret: Boolean = false) = EditText(this).apply {
        hint = label; setSingleLine(); setTextColor(this@AdbActivity.fg); isSaveEnabled = false
        inputType = if (secret) InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
                    else if (numeric) InputType.TYPE_CLASS_NUMBER
                    else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        layoutParams = LinearLayout.LayoutParams(-1, dp(56))
    }
    private fun card(root: LinearLayout, title: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(14), dp(16), dp(16))
        background = background(if (dark) Color.rgb(28, 41, 58) else Color.WHITE)
        root.addView(this, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        addView(text(title, 18f))
    }
    private fun page(root: LinearLayout, step: Page, headline: String): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            root.addView(this, LinearLayout.LayoutParams(-1, -2))
            addView(text(headline, 22f))
            pages[step] = this
        }
    private fun back(target: Page, label: String = "‹ Voltar") = button(label) { go(target) }
    private fun option(root: LinearLayout, title: String, explanation: String, action: () -> Unit) {
        val tile = card(root, title)
        tile.addView(text(explanation, 14f, secondary = true))
        tile.addView(button("Continuar ›", prominent = true, action = action))
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
        val state = AdbWizardStatus.resolve(connected, pairedThisSession,
            page == Page.MONITORED || page == Page.MONITORED_CHECK)
        connectionLabel.text = when (state) {
            AdbWizardStatus.State.CONNECTED -> "● CONECTADO POR ADB"
            AdbWizardStatus.State.PAIRED_NOT_CONNECTED -> "● PAREADO NESTA TENTATIVA · FALTA CONECTAR"
            AdbWizardStatus.State.MONITORED_NOT_VERIFIABLE -> "● AUTORIZAÇÃO NO ANDROID · NÃO VERIFICÁVEL AQUI"
            AdbWizardStatus.State.DISCONNECTED -> "● NÃO CONECTADO POR ADB"
        }
        connectionLabel.setTextColor(when (state) {
            AdbWizardStatus.State.CONNECTED -> if (dark) Color.rgb(121, 231, 174) else Color.rgb(19, 131, 75)
            AdbWizardStatus.State.PAIRED_NOT_CONNECTED -> if (dark) Color.rgb(250, 192, 94) else Color.rgb(168, 106, 12)
            else -> muted
        })
        deviceInfo.text = if (connected) {
            "Destino: ${active?.endpoint ?: "endereço indisponível"}" +
                (if (reportedPhone.isNotBlank()) "\n$reportedPhone" else "\nModelo ainda não confirmado")
        } else if (page == Page.MONITORED || page == Page.MONITORED_CHECK) {
            "Este telefone não pode confirmar por esta tela se outro cliente ADB está conectado. Verifique no controlador."
        } else {
            "Telefone conectado: nenhum. Uma credencial salva não comprova uma conexão ativa."
        }
    }

    override fun onCreate(state: Bundle?) {
        setTheme(if (dark) android.R.style.Theme_Material_NoActionBar else android.R.style.Theme_Material_Light_NoActionBar)
        super.onCreate(state)
        // Capturas permitidas por padrão. Apenas QR com senha temporária recebe FLAG_SECURE.
        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(12), dp(16), dp(16))
        }
        scroll = ScrollView(this).apply {
            isFillViewport = true; addView(root)
            setBackgroundColor(if (dark) Color.rgb(14, 22, 35) else Color.rgb(247, 249, 252))
            setOnApplyWindowInsetsListener { v, inset ->
                v.setPadding(inset.systemWindowInsetLeft, inset.systemWindowInsetTop,
                    inset.systemWindowInsetRight, inset.systemWindowInsetBottom); inset
            }
        }
        setContentView(scroll); scroll.requestApplyInsets()
        root.addView(button("‹ Configurações", allowBusy = true) { if (busy) cancel(); finish() })
        // Corrige ícones e relógio brancos sobre fundo claro (inclusive em Android edge-to-edge).
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            val lightBars = android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            window.insetsController?.setSystemBarsAppearance(if (dark) 0 else lightBars, lightBars)
        } else {
            window.decorView.systemUiVisibility = if (dark) 0 else
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
        root.addView(text("Modo avançado ADB", 26f))
        root.addView(text("Conexão por Wi-Fi entre dois celulares, com autorização no Android.", secondary = true))
        val stateCard = card(root, "ESTADO DA CONEXÃO")
        connectionLabel = text("● NÃO CONECTADO POR ADB", 15f)
        stateCard.addView(connectionLabel)
        deviceInfo = text("Telefone conectado: nenhum.", 13f, secondary = true)
        stateCard.addView(deviceInfo)
        status = text("Escolha Configurar novo para começar.", 13f, secondary = true)
        stateCard.addView(status)
        progress = text("ETAPA 1 DE 3", 12f, secondary = true)
        progress.setPadding(dp(8), dp(12), 0, dp(12))
        root.addView(progress)

        val start = page(root, Page.START, "Como deseja começar?")
        val welcome = card(start, "Configuração guiada")
        welcome.addView(text("O assistente explica cada etapa e mostra quando o ADB realmente conectou."))
        welcome.addView(button("+ Configurar novo", prominent = true) {
            pairedThisSession = false
            host.text.clear(); pairPort.text.clear(); connectPort.text.clear()
            connectionHost.text.clear(); code.text.clear()
            status.text = "Selecione o papel deste telefone."
            go(Page.ROLE)
        })
        welcome.addView(button("Já autorizei antes · Conectar") {
            status.text = "Autorização anterior não verificada. Informe a porta atual de conexão ADB."
            go(Page.CONNECT)
        })
        start.addView(button("Ferramentas e diagnóstico ADB") { go(Page.TOOLS) })
        start.addView(text("O ADB é experimental e diferente do acesso normal do Monitor Remoto. Ele não transmite a tela e não desbloqueia PIN/biometria.", secondary = true))

        val role = page(root, Page.ROLE, "Qual será a função deste celular?")
        option(role, "📱 Vou controlar outro celular",
            "Este aparelho será o controlador: ele gera o QR e estabelece a conexão com o outro telefone.") {
            status.text = "Controlador selecionado. Escolha QR ou código."
            go(Page.METHOD)
        }
        option(role, "🛡 Vou permitir controle deste celular",
            "Este é o celular monitorado: você autoriza a Depuração sem fios nas próprias Configurações do Android.") {
            status.text = "No monitorado, o consentimento é feito nas Configurações do Android."
            go(Page.MONITORED)
        }
        role.addView(back(Page.START))

        val method = page(root, Page.METHOD, "Como deseja parear?")
        method.addView(text("Mantenha os dois celulares na mesma rede Wi-Fi. No telefone monitorado, ative Depuração sem fios (Android 11+ ou ROM compatível)."))
        option(method, "▦ QR Code · Recomendado",
            "Mostre um QR neste controlador e escaneie usando o próprio telefone monitorado.") {
            status.text = "Gere o QR e leia-o no telefone monitorado."
            go(Page.QR)
        }
        option(method, "123 Código de seis dígitos",
            "Alternativa se o leitor de QR do Android não funcionar. Usa IP e porta de pareamento.") {
            status.text = "Informe o código exibido no Android do monitorado."
            go(Page.CODE)
        }
        method.addView(back(Page.ROLE))

        val qrPage = page(root, Page.QR, "Parear usando QR")
        val qrCard = card(qrPage, "QUEM LÊ O QR?")
        qrCard.addView(text("1. Neste controlador: toque Gerar QR abaixo.\n2. No OUTRO celular: Configurações → Opções do desenvolvedor → Depuração sem fios → Emparelhar dispositivo com código QR.\n3. Use a câmera aberta pelo Android do OUTRO celular para ler o QR deste.\n4. Aguarde a confirmação de pareamento. Depois conecte pela porta de CONEXÃO."))
        qrCard.addView(button("Gerar QR temporário · 2 minutos", prominent = true) { generateQr() })
        image = ImageView(this).apply {
            contentDescription = "QR temporário de pareamento ADB"
            visibility = View.GONE
        }
        qrCard.addView(image, LinearLayout.LayoutParams(-1, dp(252)))
        qrCard.addView(text("Enquanto o QR estiver visível, capturas de tela ficam bloqueadas para proteger a credencial temporária.", secondary = true))
        qrCard.addView(button("Ocultar QR e liberar captura de tela", allowBusy = true) {
            clearQr(); status.text = "QR oculto. Capturas liberadas."
        })
        qrPage.addView(button("Já autorizei · Ir para conexão", prominent = true) {
            status.text = "Pareamento não confirmado aqui. Tente conectar usando a porta de conexão atual."
            go(Page.CONNECT)
        })
        qrPage.addView(back(Page.METHOD))

        val codePage = page(root, Page.CODE, "Parear usando código")
        val codeCard = card(codePage, "NO TELEFONE MONITORADO")
        codeCard.addView(text("Configurações → Opções do desenvolvedor → Depuração sem fios → Emparelhar dispositivo com código de pareamento. Copie IP, PORTA DE PAREAMENTO e seis dígitos. A porta de conexão é diferente."))
        host = field("IP do monitorado (rede local)"); codeCard.addView(host)
        pairPort = field("Porta de pareamento", true); codeCard.addView(pairPort)
        code = field("Código de seis dígitos", secret = true); codeCard.addView(code)
        codeCard.addView(button("Parear por código", prominent = true) {
            validate {
                val endpoint = AdbPolicy.endpoint(host.text.toString().trim(), pairPort.text.toString().trim())
                val password = AdbPolicy.code(code.text.toString().trim()); code.text.clear(); clearQr()
                runOperation("Pareando…", operation = { adb ->
                    adb.pair(endpoint.first, endpoint.second, password)
                    "Pareamento aceito por ${endpoint.first}. Agora conecte usando a outra porta."
                }, onSuccess = {
                    pairedThisSession = true
                    connectionHost.setText(endpoint.first)
                    status.text = "Pareado! Falta conectar com a porta ADB de conexão."
                    go(Page.CONNECT)
                })
            }
        })
        codePage.addView(back(Page.METHOD))

        val connect = page(root, Page.CONNECT, "Conectar ao telefone monitorado")
        val connectCard = card(connect, "ETAPA FINAL: CONEXÃO ADB")
        connectCard.addView(text("No monitorado, volte à tela principal de Depuração sem fios. Copie 'Endereço IP e porta'. Esta porta de CONEXÃO é diferente da porta de pareamento e pode mudar. Você também pode localizar a porta por mDNS abaixo."))
        connectionHost = field("IP do monitorado (rede local)"); connectCard.addView(connectionHost)
        connectPort = field("Porta de conexão ADB", true); connectCard.addView(connectPort)
        connectCard.addView(button("Conectar e identificar telefone", prominent = true) {
            validate {
                val endpoint = AdbPolicy.endpoint(connectionHost.text.toString().trim(), connectPort.text.toString().trim())
                runOperation("Conectando…", operation = { adb ->
                    adb.connect(endpoint.first, endpoint.second)
                    val lines = runCatching { adb.execute(AdbPolicy.Command.DIAGNOSTIC) }
                        .getOrNull()?.lines()?.map { it.trim().take(72) }.orEmpty()
                    val model = lines.getOrNull(0)?.takeIf { it.isNotBlank() }.orEmpty()
                    val android = lines.getOrNull(1)?.takeIf { it.isNotBlank() }.orEmpty()
                    val summary = if (model.isBlank()) "Modelo não disponível" else
                        "Modelo informado: $model" + (if (android.isNotBlank()) " • Android $android" else "")
                    "Conectado a ${endpoint.first}:${endpoint.second}\n$summary"
                }, onSuccess = { result ->
                    if (client?.connected() == true) {
                        reportedPhone = result.substringAfter('\n', "Modelo não disponível")
                        deviceInfo.text = "Telefone conectado: $reportedPhone. Confirme no próprio aparelho antes de enviar comandos."
                        status.text = "Conexão ADB confirmada pelo cliente TLS."
                        go(Page.CONNECTED)
                    } else {
                        status.text = "ADB desconectou antes da confirmação. Confira a porta atual e tente novamente."
                    }
                })
            }
        })
        connect.addView(button("Ainda preciso parear") { go(Page.METHOD) })
        connect.addView(back(Page.START, "‹ Início"))

        val connected = page(root, Page.CONNECTED, "✓ Conectado por ADB")
        val activeCard = card(connected, "CONEXÃO ATIVA")
        activeCard.addView(text("A identificação e o endereço aparecem no cartão de estado acima. Confirme o aparelho antes de enviar comandos."))
        activeCard.addView(button("Consultar modelo e versão Android") {
            runOperation("Consultando…", operation = { adb ->
                "Diagnóstico ADB:\n" + adb.execute(AdbPolicy.Command.DIAGNOSTIC)
            })
        })
        val commands = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("Voltar" to AdbPolicy.Command.BACK,
            "Início" to AdbPolicy.Command.HOME,
            "Recentes" to AdbPolicy.Command.RECENTS).forEach { (label, cmd) ->
            commands.addView(button(label) {
                if (client?.connected() != true) {
                    status.text = "Sem conexão ativa. Reconecte antes de enviar comandos."
                    go(Page.CONNECT)
                } else {
                    AlertDialog.Builder(this).setTitle("Enviar $label?")
                        .setMessage("Executar por ADB em ${client?.endpoint ?: "nenhum aparelho conectado"}.")
                        .setNegativeButton("Cancelar", null)
                        .setPositiveButton("Enviar") { _, _ ->
                            runOperation("Enviando…", operation = { adb ->
                                adb.execute(cmd); "Comando enviado. Confira o resultado no monitorado."
                            })
                        }.show()
                }
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        activeCard.addView(commands)
        activeCard.addView(button("Desconectar", prominent = true, allowBusy = true) {
            cancel(); status.text = "Desconectado manualmente."
            go(Page.START)
        })

        val monitored = page(root, Page.MONITORED, "Permitir controle deste celular")
        val localCard = card(monitored, "AUTORIZAÇÃO LOCAL · ANDROID 11+")
        localCard.addView(text("1. Confirme que os celulares estão no mesmo Wi-Fi.\n2. Abra as Configurações DESTE celular e ative Opções do desenvolvedor → Depuração sem fios.\n3. No outro aparelho, abra Monitor Remoto → Modo avançado ADB → Configurar novo → Vou controlar.\n4. Escolha QR ou código no controlador.\n5. Para QR, neste celular, em Depuração sem fios, toque Emparelhar dispositivo com código QR e escaneie o QR mostrado no controlador.\n6. Para código, veja os seis dígitos e a porta no Android deste celular e informe-os no controlador."))
        localCard.addView(button("Abrir Configurações do Android", prominent = true) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        })
        monitored.addView(button("Já autorizei · Como confirmar?") {
            go(Page.MONITORED_CHECK)
        })
        monitored.addView(back(Page.ROLE))

        val monitoredCheck = page(root, Page.MONITORED_CHECK, "Como saber se conectou?")
        val checkCard = card(monitoredCheck, "VERIFIQUE NO CONTROLADOR")
        checkCard.addView(text("1. No controlador, use 'Conectar e identificar telefone' com o IP e a porta de CONEXÃO mostrados em Depuração sem fios deste celular.\n2. O controlador deve mostrar 'CONECTADO POR ADB', IP e o modelo que o Android informou.\n3. Se estiver 'PAREADO · FALTA CONECTAR', ainda não há conexão ADB.\n4. Nesta tela do monitorado não é possível verificar com segurança se o outro telefone está conectado por ADB."))
        checkCard.addView(text("ADB oferece acesso amplo ao aparelho. Desative Depuração sem fios nas Configurações do Android para revogar o acesso; o Monitor Remoto não habilita essa opção automaticamente.", secondary = true))
        monitoredCheck.addView(back(Page.MONITORED, "‹ Ver instruções novamente"))
        monitoredCheck.addView(button("Concluir orientação") {
            status.text = "Orientações concluídas; conexão recebida não verificável neste aplicativo."
            go(Page.START)
        })

        val tools = page(root, Page.TOOLS, "Ferramentas ADB")
        val toolsCard = card(tools, "DIAGNÓSTICO E SEGURANÇA")
        toolsCard.addView(text("Descoberta de serviços ADB disponíveis no Wi-Fi, portas e credencial local. Os endereços encontrados não provam a identidade do telefone."))
        toolsCard.addView(button("Desconectar / cancelar operação", allowBusy = true) {
            cancel(); status.text = "Desconectado."
            go(Page.START)
        })
        toolsCard.addView(button("Apagar credencial ADB local") {
            AlertDialog.Builder(this).setTitle("Apagar identidade ADB?")
                .setMessage("Será necessário parear novamente os aparelhos. No monitorado, use Esquecer em Dispositivos pareados para revogar a chave antiga.")
                .setNegativeButton("Cancelar", null).setPositiveButton("Apagar") { _, _ ->
                    if (busy) status.text = "Cancele a operação antes de apagar a identidade."
                    else {
                        cancel(); pairedThisSession = false
                        worker.execute {
                            AdbIdentity.forget(applicationContext)
                            handler.post { if (visible) { status.text = "Credencial local apagada."; updateConnection() } }
                        }
                    }
                }.show()
        })
        tools.addView(back(Page.START))

        discoveryCard = card(root, "LOCALIZAR PORTA PELO WI-FI · OPCIONAL")
        discoveryCard.addView(text("A busca mDNS mostra serviços de pareamento e conexão separados. Confira o IP nas Configurações do monitorado. Selecionar um resultado só preenche os campos; não confirma a conexão."))
        discoveryCard.addView(button("Procurar serviços ADB (mDNS)") { discover() })
        devices = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        discoveryCard.addView(devices)
        root.addView(text("ADB experimental. O QR ADB não é o QR normal do Monitor Remoto. A conexão encerra ao sair desta tela; a autorização pode permanecer no Android até ser revogada. Para transmitir a tela, use o Monitor Remoto normal com MediaProjection e consentimento por sessão.", secondary = true))
        go(Page.START)
    }

    private fun validate(action: () -> Unit) {
        try { action() } catch (e: IllegalArgumentException) { status.text = e.message ?: "Dados inválidos" }
    }
    private fun clearQr() {
        qr = null
        if (this::image.isInitialized) { image.setImageDrawable(null); image.visibility = View.GONE }
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
            image.setImageBitmap(bitmap); image.visibility = View.VISIBLE
            status.text = "QR ativo até 2 minutos. Escaneie com o Android do monitorado."
            discover()
            handler.postDelayed({
                if (qr === session) {
                    clearQr(); discovery?.stop()
                    status.text = "QR expirado; gere outro se necessário."
                }
            }, 120000)
        } catch (e: Exception) {
            clearQr()
            status.text = "Falha ao gerar QR (${e.javaClass.simpleName}). Tente por código."
        }
    }
    private fun discover() {
        discovery?.stop(); seen.clear(); devices.removeAllViews()
        discovery = AdbDiscovery(this, { endpoint ->
            if (visible) {
                val session = qr
                if (session != null && session.valid(SystemClock.elapsedRealtime()) &&
                    endpoint.type.contains("_adb-tls-pairing") && endpoint.name == session.service && !busy) {
                    clearQr()
                    host.setText(endpoint.host)
                    connectionHost.setText(endpoint.host)
                    pairPort.setText(endpoint.port.toString())
                    runOperation("Pareando pelo QR…", operation = { adb ->
                        adb.pair(endpoint.host, endpoint.port, session.password)
                        "Pareado pelo QR com ${endpoint.host}. Falta conectar com a porta correta."
                    }, onSuccess = {
                        pairedThisSession = true
                        status.text = "Pareamento QR confirmado. Agora conecte à porta de conexão."
                        go(Page.CONNECT)
                    })
                }
                val key = "${endpoint.name}/${endpoint.type}/${endpoint.host}:${endpoint.port}"
                if (seen.size < 16 && seen.add(key)) {
                    val isPair = endpoint.type.contains("_adb-tls-pairing")
                    devices.addView(button("${if (isPair) "Pareamento" else "Conexão"} · ${endpoint.host}:${endpoint.port} (conferir IP)") {
                        host.setText(endpoint.host)
                        connectionHost.setText(endpoint.host)
                        (if (isPair) pairPort else connectPort).setText(endpoint.port.toString())
                        status.text = "Porta preenchida pela descoberta; confira no Android."
                    })
                }
            }
        }, { if (visible && !busy) status.text = it }).also { it.start() }
        val current = discovery
        handler.postDelayed({
            if (discovery === current) {
                current?.stop()
                if (visible) devices.addView(text("Busca encerrada. Confira portas no Android e tente novamente, se necessário."))
            }
        }, 120000)
    }
    private fun runOperation(label: String, operation: (AdbClient) -> String, onSuccess: ((String) -> Unit)? = null) {
        if (busy || !visible) return
        busy = true; status.text = label
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
                        active.close(); if (client === active) client = null
                    }
                    updateConnection()
                } else {
                    // Não deixar socket e chave ativos por operação que terminou após cancelar/sair.
                    active.close()
                }
            }
        }
    }
    private fun cancel() {
        generation++; busy = false; clearQr()
        discovery?.stop(); discovery = null
        val old = client; client = null
        old?.requestCancel()
        reportedPhone = ""; pairedThisSession = false
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
        visible = false; cancel(); code.text.clear()
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
