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
    val card = panel(17).apply { background = shape(surface, 24, true) }
    val saved = RecentDevices.list(this).firstOrNull { it.host == host }
    val remoteName = remoteConnection.cached?.optJSONObject("battery")?.optString("device")
        ?.takeIf { remoteConnection.cachedHost == host && it.isNotBlank() }
    val titleText = if (saved?.customName == true) saved.name
        else remoteName ?: saved?.name ?: "Nenhum aparelho selecionado"

    val heading = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    val infoColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    currentDeviceTitle = label(titleText, 20f, strong = true).apply {
        maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END
        setPadding(0, 0, 0, dp(5))
    }
    infoColumn.addView(currentDeviceTitle)
    currentStatus = label("", 13f, strong = true).apply { setPadding(0, 0, 0, 0) }
    infoColumn.addView(currentStatus)
    currentUpdated = label("", 10.5f, muted = true).apply {
        setPadding(0, dp(6), 0, 0)
        maxLines = 2
    }
    infoColumn.addView(currentUpdated)
    heading.addView(infoColumn, LinearLayout.LayoutParams(0, -2, 1f))
    heading.addView(View(this), LinearLayout.LayoutParams(dp(10), 1))

    val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    val refresh = android.widget.ImageView(this).apply {
        setImageResource(br.dev.monitorremoto.R.drawable.ic_refresh)
        imageTintList = android.content.res.ColorStateList.valueOf(ink)
        setPadding(dp(11), dp(11), dp(11), dp(11))
        background = shape(fieldColor, 14, true)
        isClickable = true; isFocusable = true
        contentDescription = "Atualizar dados do aparelho"
        setOnClickListener { refreshRemote(silent = false) }
    }
    actions.addView(refresh, LinearLayout.LayoutParams(dp(44), dp(44)))
    actions.addView(View(this), LinearLayout.LayoutParams(dp(7), 1))
    currentConnectButton = actionButton("Sair") {
        when (effectiveConnectionState()) {
            "connected", "loading" -> disconnectCurrent()
            else -> showConnectionChooser()
        }
    }.apply {
        textSize = 11.5f
        minWidth = 0
        setPadding(dp(4), 0, dp(4), 0)
    }
    actions.addView(currentConnectButton, LinearLayout.LayoutParams(dp(59), dp(44)))
    heading.addView(actions)
    card.addView(heading)
    sectionGap(card, 18)

    resultBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    card.addView(resultBox)
    content.addView(card)
    updateConnectionLabels()
    val cached = remoteConnection.cached
    if (cached != null && remoteConnection.cachedHost == host) displayResult(cached, fromCache = true)
    else if (remoteConnection.state == "offline") resultBox?.addView(connectionIssueCard(host))
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
    currentConnectButton?.contentDescription = if (actualState == "connected" || actualState == "loading")
        "Desconectar e voltar à Home" else "Conectar ou escolher aparelho"
    currentConnectButton?.text = when (actualState) {
        "connected", "loading" -> "Sair"
        "offline" -> "Opções"
        else -> "Conectar"
    }
    currentUpdated?.text = when {
        !hasKey -> "Selecione uma forma de conexão no botão Conectar"
        actualState == "offline" -> "Última tentativa: ${formatDate(remoteConnection.lastAttemptMs)}" +
            if (remoteConnection.cachedHost == host && remoteConnection.lastSuccessMs > 0L)
                " · última leitura: ${formatDate(remoteConnection.lastSuccessMs)}" else ""
        remoteConnection.lastSuccessMs > 0 && remoteConnection.cachedHost == host ->
            "Atualizado: ${formatDate(remoteConnection.lastSuccessMs)}"
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
