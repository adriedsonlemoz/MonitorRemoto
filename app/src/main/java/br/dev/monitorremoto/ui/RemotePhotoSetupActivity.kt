package br.dev.monitorremoto.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import br.dev.monitorremoto.core.SettingsStore
import br.dev.monitorremoto.service.MonitorService
import br.dev.monitorremoto.service.PhotoReadyService

/** Armar exige uma ação visível neste aparelho ANTES de deixá-lo sem acesso físico. */
class RemotePhotoSetupActivity : Activity() {
    private val permissionCode = 6280
    private lateinit var status: TextView
    private lateinit var arm: Button
    private fun dp(v: Int) = (resources.displayMetrics.density * v).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(248, 250, 253))
            setPadding(dp(20), dp(24), dp(20), dp(20))
        }
        layout.addView(TextView(this).apply { text = "Fotografia remota · preparação"; textSize = 21f; setTextColor(Color.BLACK) })
        layout.addView(TextView(this).apply {
            text = "Para fotografar sem tocar neste aparelho a cada foto, arme a sessão antecipadamente. " +
                "Ela ficará identificada por uma notificação permanente e poderá ser encerrada aqui ou na notificação. " +
                "Somente controladores com a chave de pareamento podem solicitar fotos; os JPEGs são enviados por AES-GCM. " +
                "Nenhum vídeo contínuo é enviado. A câmera só é aberta durante o disparo.\n\n" +
                "Importante: não é possível rearmar silenciosamente após o Android encerrar o serviço ou o aparelho reiniciar. " +
                "Também é necessário manter a comunicação de rede com o aparelho monitorado."
            textSize = 14f
            setTextColor(Color.DKGRAY)
            setPadding(0, dp(12), 0, dp(12))
        })
        status = TextView(this).apply { textSize = 14f; setTextColor(Color.rgb(22, 79, 124)) }
        layout.addView(status)
        arm = Button(this).apply { isAllCaps = false; text = "Armar para fotos remotas"; setOnClickListener { authorize() } }
        layout.addView(arm, LinearLayout.LayoutParams(-1, dp(54)))
        layout.addView(Button(this).apply {
            isAllCaps = false; text = "Parar fotos remotas"
            setOnClickListener { PhotoReadyService.stop(this@RemotePhotoSetupActivity); updateStatus() }
        }, LinearLayout.LayoutParams(-1, dp(54)))
        layout.addView(Button(this).apply { isAllCaps = false; text = "Voltar"; setOnClickListener { finish() } },
            LinearLayout.LayoutParams(-1, dp(48)))
        setContentView(layout)
    }

    override fun onResume() { super.onResume(); updateStatus() }
    private fun updateStatus(message: String? = null) {
        status.text = message ?: if (PhotoReadyService.armed) "● Foto remota armada. Notificação visível no monitorado."
            else "○ Foto remota desativada."
        arm.isEnabled = !PhotoReadyService.armed
    }

    private fun authorize() {
        if (!SettingsStore.isMonitorEnabled(this) || !MonitorService.isRunning) {
            updateStatus("Ative primeiro o Modo monitorado e aguarde o serviço de rede ficar disponível.")
            return
        }
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), permissionCode)
            return
        }
        begin()
    }

    private fun begin() {
        try {
            val intent = Intent(this, PhotoReadyService::class.java).setAction(PhotoReadyService.ACTION_START)
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
            updateStatus("Solicitação enviada. Aguarde a notificação de câmera pronta do Android.")
        } catch (ex: Exception) {
            updateStatus("Android não autorizou armar a câmera: ${ex.message ?: "falha"}")
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == permissionCode) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) begin()
            else updateStatus("Permissão CAMERA recusada. A sessão não será iniciada.")
        }
    }
}
