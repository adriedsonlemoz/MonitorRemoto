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
    val card = panel(15).apply { background = shape(surface, 22, true) }
    val saved = RecentDevices.list(this).firstOrNull { it.host == host }
    val remoteName = remoteConnection.cached?.optJSONObject("battery")?.optString("device")
        ?.takeIf { remoteConnection.cachedHost == host && it.isNotBlank() }
    val titleText = if (saved?.customName == true) saved.name
        else remoteName ?: saved?.name ?: "Nenhum aparelho selecionado"

    val heading = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    val headingNames = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    currentDeviceTitle = label(titleText, 16.5f, strong = true).apply {
        maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
        setPadding(0, 0, 0, 0)
    }
    val renameRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    renameRow.addView(currentDeviceTitle, LinearLayout.LayoutParams(0, -2, 1f))
    val editName = android.widget.ImageView(this).apply {
        setImageResource(br.dev.monitorremoto.R.drawable.ic_edit)
        imageTintList = android.content.res.ColorStateList.valueOf(dim)
        setPadding(dp(7), dp(7), dp(7), dp(7))
        background = shape(fieldColor, 11, true)
        contentDescription = "Renomear aparelho salvo"
        isFocusable = true; isClickable = true
        setOnClickListener {
            RecentDevices.list(this@renderController).firstOrNull { it.host == host }
                ?.let { renameSavedDevice(it, returnToChooser = false) }
                ?: showAppNotice("Renomear", "Pareie ou salve este aparelho primeiro.")
        }
    }
    renameRow.addView(editName, LinearLayout.LayoutParams(dp(34), dp(34)))
    headingNames.addView(renameRow)
    if (remoteName != null && saved?.customName == true) {
        headingNames.addView(label(remoteName, 10f, muted = true).apply {
            maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(0, 0, 0, 0)
        })
    }
    heading.addView(headingNames, LinearLayout.LayoutParams(0, -2, 1f))
    heading.addView(View(this), LinearLayout.LayoutParams(dp(7), 1))
    val refresh = android.widget.ImageView(this).apply {
        setImageResource(br.dev.monitorremoto.R.drawable.ic_refresh)
        imageTintList = android.content.res.ColorStateList.valueOf(ink)
        setPadding(dp(9), dp(9), dp(9), dp(9))
        background = shape(fieldColor, 13, true)
        isClickable = true; isFocusable = true
        contentDescription = "Atualizar dados do aparelho"
        setOnClickListener { refreshRemote(silent = false) }
    }
    heading.addView(refresh, LinearLayout.LayoutParams(dp(38), dp(38)))
    heading.addView(View(this), LinearLayout.LayoutParams(dp(6), 1))
    currentConnectButton = actionButton("Desconectar") {
        when (effectiveConnectionState()) {
            "connected", "loading" -> disconnectCurrent()
            else -> showConnectionChooser()
        }
    }.apply { textSize = 10.5f; minWidth = 0; setPadding(dp(2), 0, dp(2), 0) }
    heading.addView(currentConnectButton, LinearLayout.LayoutParams(dp(92), dp(38)))
    card.addView(heading)
    sectionGap(card, 6)
    val statusRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    currentStatus = label("", 11f, strong = true).apply {
        setPadding(0, 0, 0, 0); maxLines = 1
    }
    statusRow.addView(currentStatus)
    statusRow.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
    currentUpdated = label("", 9.5f, muted = true).apply {
        setPadding(0, 0, 0, 0); maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
        gravity = Gravity.END
    }
    statusRow.addView(currentUpdated, LinearLayout.LayoutParams(0, -2, 1.45f))
    card.addView(statusRow)
    sectionGap(card, 10)

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
        "connected", "loading" -> "Desconectar"
        "offline" -> "Opções"
        else -> "Conectar"
    }
    currentUpdated?.text = when {
        !hasKey -> "Selecione uma forma de conexão no botão Conectar"
        actualState == "offline" -> "Última tentativa: ${formatDate(remoteConnection.lastAttemptMs)}" +
            if (remoteConnection.cachedHost == host && remoteConnection.lastSuccessMs > 0L)
                " · última leitura: ${formatDate(remoteConnection.lastSuccessMs)}" else ""
        remoteConnection.lastSuccessMs > 0 && remoteConnection.cachedHost == host ->
            "Leitura: ${formatDate(remoteConnection.lastSuccessMs)}"
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
