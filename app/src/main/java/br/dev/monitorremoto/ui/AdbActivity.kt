package br.dev.monitorremoto.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.InputType
import android.view.View
import android.view.WindowManager
import android.widget.*
import br.dev.monitorremoto.adb.*
import br.dev.monitorremoto.core.SettingsStore
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import java.util.concurrent.Executors

/** User-visible, foreground-only ADB client. Secrets never enter savedInstanceState. */
class AdbActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private var client: AdbClient? = null
    private var discovery: AdbDiscovery? = null
    private var qr: AdbPolicy.QrSession? = null
    private var generation = 0
    private var busy = false
    private var visible = false
    private lateinit var connectionLabel: TextView
    private lateinit var deviceInfo: TextView
    private var reportedPhone = ""
    private lateinit var status: TextView
    private lateinit var devices: LinearLayout
    private lateinit var image: ImageView
    private lateinit var host: EditText
    private lateinit var pairPort: EditText
    private lateinit var connectPort: EditText
    private lateinit var code: EditText
    private val seen = mutableSetOf<String>()
    private val dark get() = when (SettingsStore.theme(this)) {
        "dark" -> true
        "system" -> resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        else -> false
    }
    private val fg get() = if (dark) Color.WHITE else Color.rgb(23,34,48)
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun text(value: String, size: Float = 14f) = TextView(this).apply {
        text = value; textSize = size; setTextColor(fg); setPadding(0, dp(6), 0, dp(6))
    }
    private fun button(label: String, action: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; minHeight = dp(48)
        setOnClickListener { action() }
    }
    private fun field(label: String, numeric: Boolean = false, secret: Boolean = false) = EditText(this).apply {
        hint = label; setSingleLine(); setTextColor(fg); isSaveEnabled = false
        inputType = if (secret) InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
                    else if (numeric) InputType.TYPE_CLASS_NUMBER else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        layoutParams = LinearLayout.LayoutParams(-1, dp(52))
    }
    private fun card(root: LinearLayout, title: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(16),dp(12),dp(16),dp(12))
        background = GradientDrawable().apply { setColor(if(dark) Color.rgb(28,41,58) else Color.WHITE); cornerRadius=dp(16).toFloat() }
        root.addView(this, LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(12) })
        addView(text(title,18f))
    }
    override fun onCreate(state: Bundle?) {
        setTheme(if(dark) android.R.style.Theme_Material_NoActionBar else android.R.style.Theme_Material_Light_NoActionBar)
        super.onCreate(state)
        // Permitir capturas da tela de diagnóstico; proteger apenas o QR ADB temporário.
        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(16),dp(12),dp(16),dp(12)) }
        val scroll=ScrollView(this).apply { isFillViewport=true; addView(root); setBackgroundColor(if(dark) Color.rgb(14,22,35) else Color.rgb(247,249,252)) }
        scroll.setOnApplyWindowInsetsListener { v, inset ->
            v.setPadding(inset.systemWindowInsetLeft,inset.systemWindowInsetTop,inset.systemWindowInsetRight,inset.systemWindowInsetBottom); inset
        }
        setContentView(scroll); scroll.requestApplyInsets()
        root.addView(button("‹ Configurações") { finish() })
        root.addView(text("Modo avançado ADB",24f))
        root.addView(text("Dois celulares: ESTE é o controlador (mostra o QR). O OUTRO, que você deseja acessar com autorização, é o monitorado (lê o QR nas Configurações do Android). Ambos devem estar no mesmo Wi-Fi. O QR normal do Monitor Remoto é diferente do QR ADB."))
        root.addView(text("No monitorado: ative manualmente Opções do desenvolvedor → Depuração sem fios. Use uma rede local confiável. O ADB concede acesso amplo ao aparelho; desative-o no Android para revogar o acesso."))
        connectionLabel=text("ADB desconectado"); root.addView(connectionLabel)
        deviceInfo=text("Telefone: não identificado. Conecte para consultar o modelo informado pelo Android."); root.addView(deviceInfo)
        status=text("Desconectado"); root.addView(status)
        val pairing=card(root,"1. Parear os aparelhos (uma vez por autorização)")
        pairing.addView(text("POR QR: no controlador, toque Gerar QR abaixo. No monitorado (Android 11+), abra Configurações → Opções do desenvolvedor → Depuração sem fios → Emparelhar dispositivo com código QR. Aponte a câmera do PRÓPRIO MONITORADO para este QR. Aguarde a mensagem Pareado pelo QR."))
        pairing.addView(button("Gerar QR para o monitorado ler • 2 minutos") { generateQr() })
        image=ImageView(this).apply { contentDescription="QR temporário de pareamento ADB"; visibility=View.GONE }
        pairing.addView(image, LinearLayout.LayoutParams(-1,dp(240)))
        pairing.addView(text("O QR contém uma credencial temporária: enquanto aparecer, capturas e gravações desta janela ficam bloqueadas. Para tirar prints das instruções, feche/aguarde o fim do QR."))
        pairing.addView(button("Ocultar QR e liberar captura de tela") { clearQr(); status.text="QR cancelado. Capturas de tela liberadas nesta página." })
        pairing.addView(text("POR CÓDIGO (alternativa ao QR): no monitorado abra Depuração sem fios → Emparelhar dispositivo com código de pareamento. Copie o endereço IP, a PORTA DE PAREAMENTO e os seis dígitos exibidos. Preencha abaixo e toque Parear por código. NÃO use ainda a porta de conexão."))
        host=field("IP do monitorado (rede local)"); pairing.addView(host)
        pairPort=field("Porta de pareamento",true); pairing.addView(pairPort)
        code=field("Código de seis dígitos",secret=true); pairing.addView(code)
        pairing.addView(button("Parear por código") {
            validate {
                val endpoint=AdbPolicy.endpoint(host.text.toString().trim(), pairPort.text.toString().trim())
                val password=AdbPolicy.code(code.text.toString().trim()); code.text.clear(); clearQr()
                runOperation("Pareando…", operation = { adb -> adb.pair(endpoint.first, endpoint.second, password); "Pareado com ${endpoint.first}. Informe a porta de conexão, diferente da porta de pareamento." })
            }
        })
        val connection=card(root,"2. Conectar depois de parear")
        connection.addView(text("No monitorado, volte à tela principal de Depuração sem fios e consulte Endereço IP e porta (CONEXÃO). A porta muda e é DIFERENTE da porta de pareamento. Preencha o mesmo IP acima e a porta de conexão abaixo, ou selecione um serviço de Conexão na lista mDNS. Só então toque Conectar."))
        connectPort=field("Porta de conexão ADB",true); connection.addView(connectPort)
        connection.addView(button("Conectar") {
            validate {
                val endpoint=AdbPolicy.endpoint(host.text.toString().trim(),connectPort.text.toString().trim())
                runOperation("Conectando…", operation = { adb ->
                    adb.connect(endpoint.first, endpoint.second)
                    val lines = runCatching { adb.execute(AdbPolicy.Command.DIAGNOSTIC) }
                        .getOrNull()?.lines()?.map { it.trim().take(72) }.orEmpty()
                    val model = lines.getOrNull(0)?.takeIf { it.isNotBlank() }.orEmpty()
                    val android = lines.getOrNull(1)?.takeIf { it.isNotBlank() }.orEmpty()
                    val summary = if (model.isBlank()) "Modelo não disponível" else
                        "Modelo informado: $model" + (if (android.isNotBlank()) " • Android $android" else "")
                    "Conectado ao IP ${endpoint.first}:${endpoint.second}\n$summary"
                }, onSuccess = { result ->
                    reportedPhone = result.substringAfter('\n', "Modelo não disponível")
                    deviceInfo.text = "Telefone conectado: $reportedPhone. Confirme no próprio aparelho antes de enviar comandos."
                })
            }
        })
        connection.addView(button("Desconectar / cancelar") { cancel(); status.text="Desconectado. Se cancelou durante o pareamento, confira a lista de aparelhos autorizados no Android." })
        connection.addView(button("Consultar modelo e versão Android") {
            runOperation("Consultando…", operation = { adb -> "Diagnóstico ADB:\n" + adb.execute(AdbPolicy.Command.DIAGNOSTIC) })
        })
        val row=LinearLayout(this)
        listOf("Voltar" to AdbPolicy.Command.BACK, "Início" to AdbPolicy.Command.HOME, "Recentes" to AdbPolicy.Command.RECENTS).forEach { (label,cmd) ->
            row.addView(button(label) { AlertDialog.Builder(this).setTitle("Enviar $label?").setMessage("Executar por ADB em ${client?.endpoint ?: "nenhum aparelho conectado"}.").setNegativeButton("Cancelar",null).setPositiveButton("Enviar") { _,_ ->
                runOperation("Enviando…", operation = { adb -> adb.execute(cmd); "Comando enviado. Verifique o resultado no monitorado." })
            }.show() },LinearLayout.LayoutParams(0,-2,1f))
        }; connection.addView(row)
        connection.addView(button("Apagar credencial ADB local") {
            AlertDialog.Builder(this).setTitle("Apagar identidade ADB?").setMessage("Será necessário parear novamente todos os aparelhos. No monitorado, use Esquecer em Dispositivos pareados para revogar a chave antiga.")
                .setNegativeButton("Cancelar",null).setPositiveButton("Apagar") { _,_ ->
                    if (busy) status.text="Cancele e aguarde a operação antes de apagar a identidade."
                    else { cancel(); worker.execute { AdbIdentity.forget(applicationContext); handler.post { if(visible) status.text="Credencial local apagada. Pareie novamente." } } }
                }.show()
        })
        val discoveryCard=card(root,"3. Localizar as portas pelo Wi-Fi (opcional)")
        discoveryCard.addView(text("Toque Descobrir por mDNS para procurar serviços ADB anunciados na mesma rede. Pareamento e Conexão são serviços DIFERENTES. Um endereço listado não identifica com segurança o aparelho: confira o IP nas Configurações do telefone monitorado e o modelo após conectar."))
        discoveryCard.addView(button("Descobrir por mDNS") { discover() })
        devices=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }; discoveryCard.addView(devices)
        root.addView(text("Alpha.43 • ADB experimental. O ADB desta página ainda NÃO transmite imagens: a função Ver tela continua usando MediaProjection e JPEG/H.264 do Monitor Remoto. Não desbloqueia PIN/biometria nem apaga a tela física via ADB. Ao sair, a conexão ADB é encerrada; o pareamento permanece até ser revogado no Android."))
    }
    private fun validate(action: () -> Unit) { try { action() } catch(e: IllegalArgumentException) { status.text=e.message } }
    private fun clearQr() {
        qr = null
        image.setImageDrawable(null)
        image.visibility = View.GONE
        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
    private fun generateQr() {
        if(busy) return
        val session=AdbPolicy.newQr(SystemClock.elapsedRealtime()); qr=session
        val matrix=MultiFormatWriter().encode(session.payload(),BarcodeFormat.QR_CODE,512,512)
        val bitmap=Bitmap.createBitmap(512,512,Bitmap.Config.ARGB_8888)
        bitmap.setPixels(IntArray(512*512) { i -> if(matrix[i%512,i/512]) Color.BLACK else Color.WHITE },0,512,0,0,512,512)
        // O QR transporta um segredo ADB; esta proteção permanece enquanto ele está visível.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        image.setImageBitmap(bitmap); image.visibility=View.VISIBLE
        status.text="QR ativo por 2 minutos. Aguardando leitura no monitorado…"; discover()
        handler.postDelayed({ if(qr===session) { clearQr(); discovery?.stop(); status.text="QR expirado. Gere outro para tentar novamente." } },120000)
    }
    private fun discover() {
        discovery?.stop(); seen.clear(); devices.removeAllViews()
        discovery=AdbDiscovery(this,{ endpoint ->
            if(visible) {
                val session=qr
                if(session!=null && session.valid(SystemClock.elapsedRealtime()) && endpoint.type.contains("_adb-tls-pairing") && endpoint.name==session.service && !busy) {
                    clearQr(); host.setText(endpoint.host); pairPort.setText(endpoint.port.toString())
                    runOperation("Pareando pelo QR…", operation = { adb -> adb.pair(endpoint.host, endpoint.port, session.password); "Pareado pelo QR com ${endpoint.host}. Selecione o serviço de conexão abaixo e toque Conectar." })
                }
                val key="${endpoint.name}/${endpoint.type}/${endpoint.host}:${endpoint.port}"
                if(seen.size<16 && seen.add(key)) {
                    val isPair=endpoint.type.contains("_adb-tls-pairing")
                    devices.addView(button("${if(isPair) "Pareamento" else "Conexão"} • ${endpoint.host}:${endpoint.port} (conferir IP)") {
                        if(!busy) { host.setText(endpoint.host); (if(isPair) pairPort else connectPort).setText(endpoint.port.toString()) }
                    })
                }
            }
        },{ if(visible && !busy) status.text=it }).also { it.start() }
        val current=discovery
        handler.postDelayed({ if(discovery===current) { current?.stop(); if(visible) devices.addView(text("Busca encerrada. Endereços podem mudar; atualize antes de conectar.")) } },120000)
    }
    private fun runOperation(label: String, operation: (AdbClient) -> String, onSuccess: ((String) -> Unit)? = null) {
        if(busy || !visible) return
        busy=true; status.text=label
        val token=++generation
        val active=client ?: AdbClient(applicationContext).also { client=it }
        val timeout=Runnable { if(visible && generation==token && busy) { cancel(); status.text="Tempo limite. Confirme Wi-Fi, portas atuais e autorização no Android; tente novamente." } }
        handler.postDelayed(timeout,35000)
        worker.execute {
            val result=try { operation(active) } catch(e: Exception) {
                active.close()
                "Falha ADB (${e.javaClass.simpleName}). Confira o código atual, as duas portas, o Wi-Fi e a Depuração sem fios. Se a autorização foi removida, pareie novamente."
            } catch(e: LinkageError) { active.close(); "Biblioteca ADB nativa incompatível (${e.javaClass.simpleName}). Envie o log da compilação/aparelho." }
            handler.post {
                handler.removeCallbacks(timeout)
                if(visible && generation==token) {
                    busy=false; status.text=result
                    if(active.connected() && onSuccess != null) onSuccess(result)
                    if(!active.connected()) { active.close(); client=null }
                }
            }
        }
    }
    private fun cancel() {
        generation++; busy=false; clearQr(); discovery?.stop(); discovery=null
        val old=client; client=null; old?.requestCancel()
        reportedPhone = ""
        connectionLabel.text="ADB desconectado"
        deviceInfo.text="Telefone: nenhum conectado"
        // Socket close must not block the main thread (AdbConnection joins its reader).
        if(old!=null) Thread { old.close() }.start()
    }
    private val connectionWatch = object : Runnable {
        override fun run() {
            if (!visible) return
            val active = client
            connectionLabel.text = if(active?.connected()==true)
                "Conectado a ${active.endpoint}" + (if(reportedPhone.isNotBlank()) "\n$reportedPhone" else " · consultando identidade...")
                else "ADB desconectado"
            if (!busy && active != null && !active.connected()) {
                client = null; Thread { active.close() }.start()
                status.text = "Conexão ADB encerrada. Confira a Depuração sem fios e a porta atual."
            }
            handler.postDelayed(this, 2000)
        }
    }
    override fun onStart() { super.onStart(); visible=true; status.text="Desconectado • credencial preservada para reconexão"; handler.post(connectionWatch) }
    override fun onStop() { handler.removeCallbacks(connectionWatch); visible=false; cancel(); code.text.clear(); super.onStop() }
    override fun onDestroy() { handler.removeCallbacksAndMessages(null); worker.shutdownNow(); super.onDestroy() }
}
