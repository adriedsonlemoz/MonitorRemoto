package br.dev.monitorremoto.ui

import android.content.Context
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import br.dev.monitorremoto.core.Ipv4Suggestion
import br.dev.monitorremoto.core.RecentDevices
import br.dev.monitorremoto.core.SettingsStore
import br.dev.monitorremoto.transport.PairingCode
import java.net.Inet4Address

internal fun MainActivity.renderController() {
    val host = SettingsStore.getRemoteHost(this)
    val card = panel(18)
    val savedDevice = RecentDevices.list(this).firstOrNull { it.host == host }
    val lastDeviceName = remoteConnection.cached?.optJSONObject("battery")?.optString("device")
        ?.takeIf { remoteConnection.cachedHost == host && it.isNotBlank() }
    val titleText = if (savedDevice?.customName == true) savedDevice.name
        else lastDeviceName ?: savedDevice?.name ?: "Nenhum aparelho selecionado"

    val heading = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.TOP }
    val infoColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    currentDeviceTitle = label(titleText, 19f, strong = true).apply {
        maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END
    }
    infoColumn.addView(currentDeviceTitle)
    val statusRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    currentStatus = label("", 13f, strong = true)
    statusRow.addView(currentStatus)
    statusRow.addView(View(this), LinearLayout.LayoutParams(dp(6), 1))
    statusRow.addView(infoChip("${networkIcon(host)} ${connectionKind(host)}"))
    infoColumn.addView(statusRow)
    currentUpdated = label("", 11.5f, muted = true).apply { setPadding(0, dp(1), 0, dp(2)) }
    infoColumn.addView(currentUpdated)

    val extraRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    extraRow.addView(infoChip("🌐 ${if (host.isBlank()) "Sem IP" else host}"))
    extraRow.addView(View(this), LinearLayout.LayoutParams(dp(6), 1))
    extraRow.addView(infoChip("🔐 ${remoteConnection.method}"))
    infoColumn.addView(extraRow)

    heading.addView(infoColumn, LinearLayout.LayoutParams(0, -2, 1f))
    heading.addView(View(this), LinearLayout.LayoutParams(dp(10), 1))
    val actionColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    currentConnectButton = actionButton("Conectar", emphasis = true) {
        val actualState = effectiveConnectionState()
        if (actualState == "connected" || actualState == "loading") disconnectCurrent()
        else showConnectionChooser()
    }.apply { textSize = 11.5f; minWidth = 0; setPadding(dp(4), 0, dp(4), 0) }
    actionColumn.addView(currentConnectButton, LinearLayout.LayoutParams(dp(116), dp(42)))
    actionColumn.addView(View(this), LinearLayout.LayoutParams(1, dp(8)))
    actionColumn.addView(actionButton("↻ Atualizar") { refreshRemote(silent = false) }.apply {
        background = shape(secondaryAccent, 12)
        setTextColor(Color.WHITE)
        textSize = 11.5f
        minWidth = 0
        setPadding(dp(4), 0, dp(4), 0)
    }, LinearLayout.LayoutParams(dp(116), dp(42)))
    heading.addView(actionColumn)
    card.addView(heading)
    sectionGap(card, 10)

    resultBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    card.addView(resultBox)
    content.addView(card)
    updateConnectionLabels()
    val cached = remoteConnection.cached
    if (cached != null && remoteConnection.cachedHost == host) displayResult(cached, fromCache = true)
    else resultBox?.addView(label("Toque em Conectar para escolher QR Code, conexão manual ou aparelho salvo.", 13f, muted = true))
    gap(12)
    addText("Atualização automática a cada 15 segundos enquanto esta tela estiver aberta.", 11f, muted = true)
}

internal fun MainActivity.effectiveConnectionState(): String {
    val host = SettingsStore.getRemoteHost(this)
    return remoteConnection.effectiveState(host)
}

internal fun MainActivity.updateConnectionLabels() {
    val host = SettingsStore.getRemoteHost(this)
    val hasKey = host.isNotBlank() && RecentDevices.key(this, host) != null
    val actualState = effectiveConnectionState()
    val text = when (actualState) {
        "connected" -> "● Conectado"
        "offline" -> "● Sem resposta"
        "loading" -> "◌ Atualizando…"
        else -> "○ Desconectado"
    }
    currentStatus?.text = text
    currentStatus?.setTextColor(if (actualState == "connected") highlight else
        if (actualState == "offline") Color.rgb(190, 88, 61) else dim)
    currentConnectButton?.text = when (actualState) {
        "connected", "loading" -> "Desconectar"
        "offline" -> "Reconectar"
        else -> "Conectar"
    }
    currentUpdated?.text = when {
        !hasKey -> "Selecione uma forma de conexão no botão Conectar"
        remoteConnection.lastSuccessMs > 0 && remoteConnection.cachedHost == host ->
            "Atualizado: ${formatDate(remoteConnection.lastSuccessMs)}" +
                if (actualState == "offline") " · dados anteriores" else ""
        else -> remoteConnection.message ?: "Aguardando resposta de $host"
    }
}

internal fun MainActivity.networkIcon(host: String): String = when {
    host.isBlank() -> "○"
    localWifiPrefix()?.let { host.startsWith(it) } == true -> "📶"
    PairingCode.isPrivateIpv4(host) -> "📡"
    else -> "🛡"
}

internal fun MainActivity.connectionKind(host: String): String = when {
    host.isBlank() -> "Sem aparelho"
    localWifiPrefix()?.let { host.startsWith(it) } == true -> "IP da rede local"
    PairingCode.isPrivateIpv4(host) -> "IP privado / VPN"
    else -> "VPN / nome de rede"
}

/** Apenas o IP da interface de rede Wi-Fi ativa; sem supor prefixos de outras redes. */
internal fun MainActivity.localWifiPrefix(): String? {
    return try {
        val manager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = manager.activeNetwork
        val capabilities = network?.let { manager.getNetworkCapabilities(it) }
        if (network == null || capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) != true) null
        else manager.getLinkProperties(network)?.linkAddresses?.firstNotNullOfOrNull { link ->
            val ip = link.address as? Inet4Address ?: return@firstNotNullOfOrNull null
            if (!ip.isSiteLocalAddress) return@firstNotNullOfOrNull null
            Ipv4Suggestion.prefix(ip.hostAddress.orEmpty(), link.prefixLength)
        }
    } catch (_: Exception) { null }
}
