package br.dev.monitorremoto.ui

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.view.Gravity
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import br.dev.monitorremoto.core.RecentDevices
import br.dev.monitorremoto.core.SettingsStore
import br.dev.monitorremoto.service.MonitorService
import br.dev.monitorremoto.transport.PairingCode
import br.dev.monitorremoto.transport.PairingSession
import br.dev.monitorremoto.transport.WireProtocol
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.net.Inet4Address
import java.net.NetworkInterface

internal fun MainActivity.availableLocalIps(): List<Pair<String, String>> = try {
    NetworkInterface.getNetworkInterfaces().toList()
        .filter { it.isUp && !it.isLoopback }
        .flatMap { iface -> iface.inetAddresses.toList().filterIsInstance<Inet4Address>()
            .mapNotNull { ip ->
                val address = ip.hostAddress.orEmpty()
                if (PairingCode.isPrivateIpv4(address)) iface.name to address else null
            }
        }.distinctBy { it.second }
} catch (_: Exception) { emptyList() }

internal fun MainActivity.chooseQrAddress() {
    if (!MonitorService.isRunning) {
        message("Ative o monitoramento antes de gerar o QR Code.")
        return
    }
    val addresses = availableLocalIps()
    if (addresses.isEmpty()) {
        message("Não encontrei IP privado. Conecte ambos os celulares ao mesmo Wi-Fi ou ponto de acesso e tente novamente.")
        return
    }
    if (addresses.size == 1) showPairingQr(addresses.first().second)
    else AlertDialog.Builder(this)
        .setTitle("Escolha a rede do outro celular")
        .setItems(addresses.map { "${it.first}: ${it.second}" }.toTypedArray()) { _, index ->
            showPairingQr(addresses[index].second)
        }
        .setNegativeButton("Cancelar", null)
        .show()
}

internal fun MainActivity.showPairingQr(ip: String) {
    val offer = PairingSession.issue()
    try {
        val code = PairingCode.encode(offer.copy(host = ip))
        val matrix = QRCodeWriter().encode(code, BarcodeFormat.QR_CODE, 600, 600)
        val pixels = IntArray(600 * 600) { index ->
            if (matrix[index % 600, index / 600]) Color.BLACK else Color.WHITE
        }
        val bitmap = Bitmap.createBitmap(600, 600, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, 600, 0, 0, 600, 600)
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(8))
        }
        panel.addView(TextView(this).apply {
            text = "No aparelho CONTROLADOR, toque em 'Ler QR Code'.\nRede: $ip:${WireProtocol.PORT}"
            textSize = 14f
        })
        val size = minOf(resources.displayMetrics.widthPixels - dp(96), dp(320))
        panel.addView(ImageView(this).apply {
            setImageBitmap(bitmap)
            contentDescription = "QR Code de pareamento temporário. Não compartilhe."
        }, LinearLayout.LayoutParams(size, size).apply { gravity = Gravity.CENTER_HORIZONTAL })
        panel.addView(TextView(this).apply {
            text = "Acesso temporário. Expira em 2 minutos ou após o primeiro pareamento. Não compartilhe imagens deste código."
            textSize = 12f
        })
        val dialog = AlertDialog.Builder(this)
            .setTitle("Parear pela rede local")
            .setView(panel)
            .setNegativeButton("Fechar", null)
            .create()
        dialog.setOnDismissListener { PairingSession.revoke(offer.session) }
        dialog.show()
        dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val monitorQr = object : Runnable {
            override fun run() {
                if (!dialog.isShowing) return
                if (PairingSession.active()?.first != offer.session) dialog.dismiss()
                else handler.postDelayed(this, 1_000L)
            }
        }
        handler.postDelayed(monitorQr, 1_000L)
        handler.postDelayed({ if (dialog.isShowing) dialog.dismiss() }, PairingSession.EXPIRES_MS)
    } catch (e: Exception) {
        PairingSession.revoke(offer.session)
        message("Não foi possível exibir o QR: ${e.message}")
    }
}


internal fun MainActivity.startQrScanner() {
    // O fluxo de autorização é visível antes de abrir a Activity de câmera.
    if (!packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
        message("Este aparelho não possui câmera disponível para leitura. Use o pareamento manual.")
        return
    }
    if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
        requestPermissions(arrayOf(Manifest.permission.CAMERA), cameraPermissionRequest)
        return
    }
    launchQrCapture()
}


internal fun MainActivity.launchQrCapture() {
    try {
        // Activity nativa: elimina a antiga Activity de câmera da biblioteca ZXing Embedded.
        startActivityForResult(Intent(this, QrScanActivity::class.java), qrScanRequest)
    } catch (e: Exception) {
        android.util.Log.e("MonitorRemotoQR", "Falha ao abrir câmera nativa", e)
        message("Falha ao abrir leitor (${e.javaClass.simpleName}). Use conexão manual por IP/chave ou consulte o diagnóstico da câmera.")
    }
}


internal fun MainActivity.confirmQrCode(text: String) {
    try {
        val code = PairingCode.decode(text)
        AlertDialog.Builder(this)
            .setTitle("Confirmar aparelho")
            .setMessage("Aparelho: ${RecentDevices.list(this).firstOrNull { it.host == code.host }?.name ?: "Não identificado (novo)"}\nEndereço: ${code.host}:${WireProtocol.PORT}\n\nConfira se é o celular monitorado. O nome só poderá ser verificado após autenticação.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Confirmar") { _, _ -> pairByQr(code) }
            .show()
    } catch (e: Exception) {
        message("QR inválido ou incompatível: ${e.message}")
    }
}

internal fun MainActivity.pairByQr(code: PairingCode.Offer) {
    pairingController.pair(code) { result ->
        if (isFinishing || isDestroyed) return@pair
        result.onSuccess { key ->
            try {
                RecentDevices.save(this, code.host, key)
                SettingsStore.setRemoteHost(this, code.host)
                remoteConnection.select("QR Code")
                mode = 1
                render()
                message("Pareamento concluído. IP e chave salvos com proteção do Android Keystore.")
                refreshRemote(silent = false)
            } catch (e: Exception) { message("Erro ao salvar pareamento: ${e.message}") }
        }.onFailure { e ->
            message("Não foi possível parear: ${e.message}. Verifique o Wi-Fi, serviço ativo, tempo do QR e isolamento de clientes no roteador. Gere novo QR.")
        }
    }
}
