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
import br.dev.monitorremoto.R
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
    else {
        val (wrapper, layout) = dialogWrapper()
        layout.addView(iconSectionHeader(R.drawable.ic_wifi, "Escolher rede", "Selecione o endereço que o outro celular consegue alcançar"))
        sectionGap(layout, 10)
        addresses.forEach { (iface, ip) ->
            layout.addView(iconMenuRow(R.drawable.ic_wifi, ip, "Interface: $iface") {
                activeUiDialog?.dismiss()
                showPairingQr(ip)
            })
            sectionGap(layout, 7)
        }
        showManagedDialog(AlertDialog.Builder(this).setView(wrapper).setNegativeButton("Cancelar", null))
    }
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
            setPadding(dp(18), dp(14), dp(18), dp(14))
        }
        panel.addView(iconSectionHeader(R.drawable.ic_qr, "Pareamento seguro", "Código temporário de uso único"))
        sectionGap(panel, 12)
        val size = minOf(resources.displayMetrics.widthPixels - dp(116), dp(300))
        val qrHolder = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            background = shape(surface, 20, true)
            setPadding(dp(13), dp(13), dp(13), dp(13))
        }
        qrHolder.addView(ImageView(this).apply {
            setImageBitmap(bitmap)
            contentDescription = "QR Code de pareamento temporário. Não compartilhe."
        }, LinearLayout.LayoutParams(size, size))
        panel.addView(qrHolder, LinearLayout.LayoutParams(-1, -2))
        sectionGap(panel, 12)
        val network = iconMenuRow(R.drawable.ic_wifi, "Rede local", "$ip:${WireProtocol.PORT}") {}
        network.isClickable = false
        network.isFocusable = false
        panel.addView(network)
        sectionGap(panel, 10)
        panel.addView(label("No aparelho controlador, escolha QR Code e aponte a câmera.", 12.5f))
        panel.addView(label("Expira em 2 minutos ou após o primeiro pareamento. Não compartilhe a imagem deste QR.", 11.5f, muted = true))
        val dialog = AlertDialog.Builder(this)
            .setTitle("Parear pela rede local")
            .setView(panel)
            .setNegativeButton("Fechar", null)
            .create()
        dialog.setOnDismissListener { PairingSession.revoke(offer.session) }
        showManagedDialog(dialog)
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
        showAppConfirm(
            "Confirmar aparelho",
            "Aparelho: ${RecentDevices.list(this).firstOrNull { it.host == code.host }?.name ?: "Não identificado (novo)"}\n" +
                "IP: ${code.host}:${WireProtocol.PORT}\nConfira se é o celular esperado. O nome só pode ser conhecido depois da autenticação.",
            R.drawable.ic_qr, "Confirmar"
        ) { pairByQr(code) }
    } catch (e: Exception) {
        if (e.message?.contains("Versões incompatíveis") == true) {
            showAppNotice("QR incompatível", e.message ?: "Atualize os dois aparelhos.", R.drawable.ic_shield)
        } else showAppNotice("QR não reconhecido", e.message ?: "Gere um novo QR e tente novamente.", R.drawable.ic_qr)
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
            val issue = ConnectionIssue.from(e)
            showAppNotice(issue.title,
                "${issue.explanation}\n\n" + issue.checks.joinToString("\n") { "• $it" },
                R.drawable.ic_activity)
        }
    }
}
