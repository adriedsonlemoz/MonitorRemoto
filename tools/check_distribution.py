#!/usr/bin/env python3
"""Testa invariantes de versão e distribuição, sem Gradle/SDK."""
from pathlib import Path
import re

root = Path(__file__).resolve().parent.parent
gradle = (root / "app/build.gradle.kts").read_text(encoding="utf-8")
workflow = (root / ".github/workflows/android.yml").read_text(encoding="utf-8")
ui_root = root / "app/src/main/java/br/dev/monitorremoto/ui"
main_ui = (ui_root / "MainActivity.kt").read_text(encoding="utf-8")
modules = ["UiComponents.kt", "HomeScreenUi.kt", "MonitorScreenUi.kt", "SettingsScreenUi.kt", "RemoteDashboardUi.kt", "IncomingAccessScreenUi.kt", "ConnectionScreenUi.kt", "ConnectionController.kt", "PairingScreenUi.kt", "PairingQrUi.kt", "MonitorCredentialsUi.kt", "PairingController.kt", "RemoteConnectionManager.kt", "RemoteMessagesUi.kt"]
module_ui = {name: (ui_root / name).read_text(encoding="utf-8") for name in modules}
ui = main_ui + "\n" + "\n".join(module_ui.values())
match = re.search(r'\bversionName\s*=\s*"([^"]+)"', gradle)
assert match, "versionName ausente"
version = match.group(1)
code = re.search(r'\bversionCode\s*=\s*(\d+)', gradle)
assert code, "versionCode ausente"
assert version == "0.1.0-alpha.35" and code.group(1) == "35", "Versões incompatíveis"
appver = (root / 'app/src/main/java/br/dev/monitorremoto/transport/AppVersion.kt').read_text()
assert f'const val NAME = "{version}"' in appver, "AppVersion fora de sincronia com o Gradle"
assert 'AppVersion.NAME' in ui, "Tela de versão desatualizada"
assert f"versionCode={code.group(1)}" in (root / 'docs/ARCHITECTURE.md').read_text(), "Arquitetura com versionCode desatualizado"
# alpha.9 remove a Activity antiga do ZXing Embedded; Camera2 + ZXing Core offline.
assert 'IntentIntegrator' not in ui and 'BarcodeEncoder' not in ui
assert 'startActivityForResult(Intent(this, QrScanActivity::class.java), qrScanRequest)' in ui
assert 'override fun onActivityResult(' in ui and 'QrScanActivity.EXTRA_QR_RESULT' in ui
assert 'private fun askQrText()' not in ui
assert 'QRCodeWriter().encode(' in ui, 'Gerador de QR não usa ZXing Core' 
assert 'dist/MonitorRemoto-v${VERSION}.apk' in workflow, "Arquivo .apk final não é nomeado"
assert 'gh release upload' in workflow and 'gh release create' in workflow, "Falta APK direto via Release"
assert 'actions/upload-artifact' in workflow, "Artifact de reserva ausente"
assert 'id: release' in workflow and "steps.release.outcome == 'failure'" in workflow, "Artifact duplicado em Release bem-sucedida"
assert 'com.google.zxing:core:3.5.4' in gradle, 'ZXing Core offline ausente'
assert 'zxing-android-embedded' not in gradle, 'Captura legada não deve retornar'
qr = (root / 'app/src/main/java/br/dev/monitorremoto/transport/PairingCode.kt').read_text()
assert 'isPrivateIpv4' in qr and 'secret=' in qr, "QR local ou protocolo ausente"
assert 'PairingSession.consume' in (root / 'app/src/main/java/br/dev/monitorremoto/transport/MonitorServer.kt').read_text()
assert 'app/build/outputs/apk/release/app-release.apk' in workflow
assert 'app/build/outputs/apk/debug/app-debug.apk' in workflow
assert 'app/build/outputs/bundle/release/*.aab' in workflow
assert 'KEYSTORE_BASE64' in workflow and 'MONITOR_SIGNING_STORE_FILE' in gradle
for path in ['README.md', 'CHANGELOG.md', 'docs/RELEASE_NOTES.md', 'AGENTS.md']:
    assert version in (root / path).read_text(encoding="utf-8"), f"Versão ausente em {path}"
manifest = (root / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
assert 'android.permission.RECEIVE_SMS' in manifest
assert 'android.accessibilityservice.AccessibilityService' in manifest and 'android:exported="true"' in manifest
print(f"PASSOU: versionName={version}, versionCode={code.group(1)}, nome APK, Release, assinatura, AAB e permissões")

# Privacidade: limpeza efetiva e verificação na hora da transmissão.
journal = (root / 'app/src/main/java/br/dev/monitorremoto/core/EventJournal.kt').read_text(encoding='utf-8')
settings = (root / 'app/src/main/java/br/dev/monitorremoto/core/SettingsStore.kt').read_text(encoding='utf-8')
server = (root / 'app/src/main/java/br/dev/monitorremoto/transport/MonitorServer.kt').read_text(encoding='utf-8')
assert 'fun clear()' in journal and 'fun retainAllowed(' in journal and 'allowedPackages' in journal
assert 'if (!enabled || !wasEnabled) {' in settings and 'EventJournal.clear()' in settings and 'NotificationReplyActions.clear()' in settings
assert 'EventJournal.retainAllowed(packages)' in settings
assert 'EventJournal.latestJson(SettingsStore.notificationAllowlist(context),' in server
assert 'ArrayBlockingQueue<Runnable>(12)' in server and 'ThreadPoolExecutor.AbortPolicy()' in server
print('PASSOU: limpeza por revogação e limite de fila do servidor declarados')

# Regressão alpha.7: leitura QR não pode depender de Activity externa nem de permissão presumida.
assert 'IntentIntegrator' not in ui
assert 'requestPermissions(arrayOf(Manifest.permission.CAMERA)' in ui
assert 'android:name=".ui.QrScanActivity"' in manifest and 'android:exported="false"' in manifest
assert 'fun latest(allowedPackages: Set<String>, allApps: Boolean = false): List<RecordedEvent>' in journal
unit_tests = (root / 'app/src/test/java/br/dev/monitorremoto/core/EventJournalTest.kt').read_text()
assert 'latestJson(' not in unit_tests, 'Testes JVM não devem depender dos métodos mockados de android.jar/org.json'
print('PASSOU: scanner nativo independente, permissão explícita, testes EventJournal sem org.json mockado')

# Regressão alpha.8: a compilação #7 falhou por duas sobrescritas do mesmo callback.
# Ambas as permissões devem ser tratadas no MESMO callback: câmera e notificações.
callbacks = re.findall(r'\boverride\s+fun\s+onRequestPermissionsResult\s*\(', ui)
assert len(callbacks) == 1, f"Callback duplicado: {len(callbacks)} implementações"
assert 'cameraPermissionRequest ->' in ui and 'notificationPermissionRequest ->' in ui
assert 'requestPermissions(arrayOf(Manifest.permission.CAMERA), cameraPermissionRequest)' in ui
assert 'requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), notificationPermissionRequest)' in ui
assert 'if (granted) launchQrCapture()' in ui and 'if (granted) startServiceExplicitly()' in ui
assert 'id: package_apk' in workflow and "steps.package_apk.outcome == 'success'" in workflow
print('PASSOU: callback de permissões único, ambos os fluxos e fallback somente após APK criado')

# alpha.9: não pode reaparecer o fluxo antigo do ZXing, nem abrir captura sem tratamento de falhas.
scan = (root / 'app/src/main/java/br/dev/monitorremoto/ui/QrScanActivity.kt').read_text(encoding='utf-8')
decoder = (root / 'app/src/main/java/br/dev/monitorremoto/ui/QrFrameDecoder.kt').read_text(encoding='utf-8')
assert 'class QrScanActivity : Activity(), TextureView.SurfaceTextureListener' in scan
assert 'manager.openCamera(' in scan and 'createCaptureSession(' in scan
assert 'ImageFormat.YUV_420_888' in scan and 'CameraDevice.StateCallback()' in scan
assert 'Log.e(TAG,' in scan and 'updateStatus(' in scan, 'Erros de câmera não são visíveis'
assert 'reader.acquireLatestImage()' in scan and 'finally {' in scan and 'image?.close()' in scan
assert 'reader.decodeWithState(' in decoder and 'PlanarYUVLuminanceSource' in decoder
assert 'android:name=".ui.QrScanActivity"' in manifest and 'android:exported="false"' in manifest
assert 'CaptureActivity' not in scan, 'Dependência da Activity antiga voltou'
assert (root / 'app/src/test/java/br/dev/monitorremoto/ui/QrFrameDecoderTest.kt').exists()
print('PASSOU: Camera2, YUV, ZXing Core, cancelamento, fallback manual e testes de QR presentes')

# alpha.10: regressões de UI, temas, histórico e credenciais separadas por host.
profiles = (root / 'app/src/main/java/br/dev/monitorremoto/core/RecentDevices.kt').read_text()
secret = (root / 'app/src/main/java/br/dev/monitorremoto/security/SecretVault.kt').read_text()
ip_suggestion = (root / 'app/src/main/java/br/dev/monitorremoto/core/Ipv4Suggestion.kt').read_text()
assert 'internal fun MainActivity.showConnectionChooser()' in ui and 'internal fun MainActivity.showManualConnectionDialog()' in ui and 'internal fun MainActivity.showSettingsDialog()' in ui
assert 'internal fun MainActivity.renderController()' in ui and 'internal fun MainActivity.updateConnectionLabels()' in ui
assert 'internal fun MainActivity.localWifiPrefix()' in ui and 'TRANSPORT_WIFI' in ui
assert 'Ipv4Suggestion.prefix(' in ui and 'prefixLength' in ui
assert 'RecentDevices.migrateLegacy(this)' in ui and 'RecentDevices.save(' in ui
assert 'RecentDevices.key(this, host)' in ui and 'RecentDevices.markSuccess(' in ui
assert 'var lastSuccessMs = 0L' in ui and 'state = "offline"' in ui
assert 'fun setTheme(' in settings and '"light"' in settings and '"dark"' in settings
assert 'const val LIMIT = 5' in profiles and 'fun forget(' in profiles and 'fun rename(' in profiles
assert 'profileKeyName(' in secret and 'MessageDigest.getInstance("SHA-256")' in secret
assert 'fun prefix(' in ip_suggestion
assert (root / 'app/src/test/java/br/dev/monitorremoto/core/Ipv4SuggestionTest.kt').is_file()
print('PASSOU: UI alpha.15, migração única, Keystore por host, tema e sugestão de IP')

# alpha.15: entrada simples, ações explícitas e câmera em janela flutuante.
assert 'internal fun MainActivity.connectSavedDevice(' in ui and 'internal fun MainActivity.renameSavedDevice(' in ui
assert 'internal fun MainActivity.forgetSavedDevice(' in ui and 'internal fun MainActivity.showConnectionChooser()' in ui
assert 'showManualConnectionDialog()' in ui and '"Confirmar aparelho"' in ui
assert 'R.drawable.ic_refresh' in ui and 'contentDescription = "Atualizar dados do aparelho"' in ui
assert '@android:style/Theme.Material.Light.Dialog.NoActionBar' in manifest
assert 'window.setLayout(' in scan and 'Aponte para o QR Code' in scan
print('PASSOU: fluxo alpha.15, confirmação QR, botões de ação, scanner pop-up')

# alpha.17: Home de escolha, modo Monitorado separado e manual seguro.
assert 'savedInstanceState?.getInt("active_mode", 2)' in ui
assert 'private fun navigateHome()' in ui and 'internal fun MainActivity.renderHome()' in ui
assert 'internal fun MainActivity.showMyConnectionInfo()' in ui and 'internal fun MainActivity.preferredLocalAddress()' in ui
assert 'showAboutDialog()' in ui and 'showMainMenu()' not in ui
assert 'showMyConnectionInfo()' in ui and 'getOrCreateMonitorKey(this)' in ui
assert 'WindowManager.LayoutParams.FLAG_SECURE' in ui
assert 'ClipDescription.EXTRA_IS_SENSITIVE' in ui and 'Intent.ACTION_SEND' in ui
assert 'showAppConfirm("Compartilhar credencial?"' in ui and 'setNegativeButton("Cancelar", null)' in ui
assert 'showConnectionChooser()' in ui and 'showHomeSavedDeviceOptions(' in ui
assert 'if (mode != 2) { if (mode == 4) clearRemoteMessagesScreen(); mode = 2; settingsSection = "home"; render() }' in ui
print('PASSOU: alpha.17, Home independente, informações manuais protegidas e recentes')

# alpha.17: não reapresentar Monitorado como Home; atalhos separados e ação de gerenciamento por aparelho.
assert 'internal var mode = 2 // 2: início, 0: monitorado, 1: controle' in ui
assert 'else -> renderHome()' in ui and '0 -> renderMonitor()' in ui and '1 -> {' in ui
assert 'iconTile(R.drawable.ic_qr, "Conectar novo aparelho"' in ui and 'Aparelhos anteriores' in ui
assert 'Última conexão: $lastUsed' in ui and 'item.lastSuccessMs > 0L' in ui
assert 'showHomeSavedDeviceOptions(item)' in ui and 'returnToChooser = false' in ui
assert 'iconTile(R.drawable.ic_phone, "Monitorar este aparelho"' in ui and 'mode = 0' in ui
assert 'val columns = LinearLayout(this)' in ui and 'iconMetric("IP da rede"' in ui
assert 'addCompact("Gerar QR"' in ui and 'addCompact("Mostrar info"' in ui and 'addCompact("Interromper"' in ui
assert 'showMyConnectionInfo()' in ui and 'FLAG_SECURE' in ui
assert 'startServiceExplicitly()' in ui and 'stopService(Intent(this, MonitorService::class.java))' in ui
assert 'private fun renderRecentDevicesHome()' not in ui
print('PASSOU: Home com recentes, última conexão real, engrenagem e controles locais compactos')

# alpha.18: authenticated access tracking is optional in protocol and expires, no imaginary sessions.
tracker = (root / 'app/src/main/java/br/dev/monitorremoto/transport/AuthenticatedAccess.kt').read_text()
client = (root / 'app/src/main/java/br/dev/monitorremoto/transport/RemoteClient.kt').read_text()
assert 'AuthenticatedAccess.authenticated(' in server and 'WireProtocol.send(socket.getOutputStream()' in server
assert 'STALE_AFTER_MS = 45_000L' in tracker and 'fun clear()' in tracker
assert 'AuthenticatedAccess.clear()' in (root / 'app/src/main/java/br/dev/monitorremoto/service/MonitorService.kt').read_text()
assert 'controllerId' in client and 'clientName' in client and 'accessMethod' in client
assert 'mode = 2\n    render()' in ui, 'Desconectar não retorna à Home'
assert 'iconTile(R.drawable.ic_qr, "QR Code"' in ui and 'iconTile(R.drawable.ic_phone, "Manual"' in ui
assert 'addAction("Conectar", true)' in ui and 'addAction("Renomear")' in ui
assert 'addAction("Esquecer", red = true)' in ui
assert 'addInfoAction(revealButton)' in ui and 'addInfoAction(actionButton("Copiar"' in ui
assert 'addInfoAction(actionButton("Compartilhar"' in ui
assert 'private fun MainActivity.renderIncomingAccess()' in ui and 'private fun MainActivity.showIncomingAccessActions(' in ui
assert (root / 'app/src/test/java/br/dev/monitorremoto/transport/AuthenticatedAccessTest.kt').exists()
print('PASSOU: alpha.18, menus compactos, Home após desconexão, alertas autenticados e tempo de expiração')

# alpha.19: painel remoto compacto com grid e atalhos.
assert 'internal fun MainActivity.networkIcon(host: String)' in ui and 'internal fun MainActivity.connectionKind(host: String)' in ui
assert 'private fun MainActivity.compactMetricCard(' in ui and 'private fun MainActivity.addMetricGrid(' in ui
assert 'private fun MainActivity.actionTile(' in ui and 'private fun MainActivity.addActionGrid(' in ui
assert 'private fun MainActivity.showRemoteNotifications(root: JSONObject)' in ui
assert 'private fun MainActivity.showRemoteDetailsDialog(root: JSONObject)' in ui
assert 'private fun MainActivity.showRemoteDetailsDialog(root: JSONObject)' in ui
assert 'Funções remotas' in ui and 'Mensagens' in ui and 'Ver tela' in ui and 'Localização' in ui
print('PASSOU: alpha.19, painel remoto compacto, atalhos e detalhes dedicados')

# alpha.20: compatibilidade EXATA, disconnect autenticado, aviso em tela dedicada.
incoming = (root / 'app/src/main/java/br/dev/monitorremoto/ui/IncomingAccessActivity.kt').read_text()
service = (root / 'app/src/main/java/br/dev/monitorremoto/service/MonitorService.kt').read_text()
network = (root / 'app/src/main/java/br/dev/monitorremoto/core/NetworkReader.kt').read_text()
assert 'AppVersion.compatible(' in qr and 'app=${AppVersion.NAME}' in qr
assert '.put("appVersion", AppVersion.NAME)' in client and 'fun disconnect(' in client
assert 'AppVersion.compatible(regular.optString("appVersion"))' in server
assert 'AppVersion.compatible(pairing.optString("appVersion"))' in server
assert 'AuthenticatedAccess.disconnected(' in server and 'fun disconnected(' in tracker
assert 'NetworkReader.snapshot(context)' in server and 'TRANSPORT_WIFI' in network
assert 'private fun showIncomingAccessIfNeeded()' in ui and 'IncomingAccessActivity::class.java' in ui
assert 'class IncomingAccessActivity : Activity()' in incoming
assert ('Acesso remoto detectado' in incoming or 'Acesso remoto detectado' in ui) and ('Parar acesso' in incoming or 'Parar acesso' in ui)
assert 'Quem está?' not in incoming and 'showWho(' not in incoming
assert 'android:name=".ui.IncomingAccessActivity"' in manifest and 'android:exported="false"' in manifest
assert 'ACCESS_CHANNEL' in service and 'IMPORTANCE_DEFAULT' in service
assert 'SettingsStore.isMonitorEnabled(this) && !MonitorService.isRunning' in ui
assert 'private fun recoverAuthorizedServiceIfStopped()' in ui
assert 'renderSettingsModule()' in ui and 'openSettingsModule()' in ui
assert 'private fun MainActivity.compactMetricCard(' in ui and 'minimumHeight = dp(115)' in ui
assert (root / 'app/src/test/java/br/dev/monitorremoto/transport/AppVersionTest.kt').is_file()
assert len(list((root / 'app/src/main/res/drawable').glob('ic_*.xml'))) >= 10
print('PASSOU: alpha.20, versão exata, disconnect, notificação, Activity de acesso e UI vetorial')

# alpha.21: regressão do conflito entre Activity.background (Int) e View.background (Drawable).
# O erro de compileDebugKotlin no build #21 ocorreu no apply receiver de ScrollView.
assert 'setBackgroundColor(this@IncomingAccessActivity.background)' in incoming, (
    'IncomingAccessActivity deve qualificar o background de cor da Activity (Int)'
)
assert 'setBackgroundColor(background)' not in incoming, (
    'Colisão de nome com ScrollView.background: Drawable reintroduzida'
)
print('PASSOU: alpha.21, cor do ScrollView referenciada na Activity; tipo Int preservado')

# A versão exata usada pelo teste JUnit deve acompanhar AppVersion.NAME a cada release.
app_version_test = (root / 'app/src/test/java/br/dev/monitorremoto/transport/AppVersionTest.kt').read_text()
assert 'assertTrue(AppVersion.compatible(AppVersion.NAME))' in app_version_test
assert f'assertFalse(AppVersion.compatible("0.1.0-alpha.20"))' in app_version_test
assert f'assertFalse(AppVersion.compatible("0.1.0-alpha.21"))' in app_version_test
assert f'assertFalse(AppVersion.compatible("0.1.0-alpha.22"))' in app_version_test
print('PASSOU: alpha.21, teste de compatibilidade segue a versão atual')

# alpha.22: a interface cresceu, mas MainActivity não volta a concentrar telas inteiras.
assert len(main_ui.splitlines()) <= 430, "MainActivity voltou a concentrar a UI"
assert all(len(text.splitlines()) >= 25 for text in module_ui.values()), "Módulo UI vazio ou parcial"
assert "internal fun MainActivity.renderHome()" in module_ui["HomeScreenUi.kt"]
assert "internal fun MainActivity.renderMonitor()" in module_ui["MonitorScreenUi.kt"]
assert "internal fun MainActivity.showSettingsDialog()" in module_ui["SettingsScreenUi.kt"]
assert "internal fun MainActivity.displayResult(" in module_ui["RemoteDashboardUi.kt"]
assert "internal fun MainActivity.panel(" in module_ui["UiComponents.kt"]
for entry in ["renderHome", "renderMonitor", "showSettingsDialog", "displayResult", "dialogCardRow"]:
    found = re.findall(r"\bfun\s+(?:MainActivity\.)?" + entry + r"\s*\(", ui)
    assert len(found) == 1, f"Função duplicada ou ausente: {entry}: {len(found)}"
assert "private fun displayResult(" not in main_ui
assert "private fun renderHome(" not in main_ui
assert "private fun showSettingsDialog(" not in main_ui
assert "AppVersion.NAME" in main_ui and 'put("appVersion", AppVersion.NAME)' in client
assert "internal fun IncomingAccessActivity.renderIncomingAccessScreen(" in module_ui["IncomingAccessScreenUi.kt"]
assert 'Quem está?' not in module_ui["IncomingAccessScreenUi.kt"] and 'Outros acessos recentes' in module_ui["IncomingAccessScreenUi.kt"]
print(f"PASSOU: alpha.22-24, módulos extraídos, MainActivity={len(main_ui.splitlines())} linhas e funções únicas")

# alpha.24: remote state, selected host and UI extraction must remain independent.
remote_manager = module_ui['RemoteConnectionManager.kt']
connection_controller = module_ui['ConnectionController.kt']
pairing_ui = module_ui['PairingScreenUi.kt']
pairing_qr = module_ui['PairingQrUi.kt']
pairing_controller = module_ui['PairingController.kt']
credentials_ui = module_ui['MonitorCredentialsUi.kt']
assert 'class RemoteConnectionManager(' in remote_manager and 'fun fetch(' in remote_manager
assert 'token != generation' in remote_manager and 'generation++' in remote_manager
assert 'RemoteClient.snapshot(' in remote_manager and 'RemoteClient.disconnect(' in remote_manager
assert 'Handler(Looper.getMainLooper())' in remote_manager
assert 'internal fun MainActivity.disconnectCurrent()' in connection_controller
assert 'internal fun MainActivity.refreshRemote(silent: Boolean)' in connection_controller
assert 'remoteConnection.fetch(' in connection_controller
assert 'internal fun MainActivity.renderController()' in module_ui['ConnectionScreenUi.kt']
assert 'internal fun MainActivity.showConnectionChooser()' in pairing_ui
assert 'internal fun MainActivity.showManualConnectionDialog()' in pairing_ui
assert 'RecentDevices.save(this, address, parsedKey)\n                SettingsStore.setRemoteHost(this, address)' in pairing_ui
assert 'RecentDevices.save(this, code.host, key)\n                SettingsStore.setRemoteHost(this, code.host)' in pairing_qr
assert 'AtomicBoolean(false)' in pairing_controller and 'RemoteClient.pair(code)' in pairing_controller
assert 'internal fun MainActivity.showMyConnectionInfo()' in credentials_ui
assert 'ClipDescription.EXTRA_IS_SENSITIVE' in credentials_ui
assert 'internal fun MainActivity.selectNotificationApps()' in module_ui['SettingsScreenUi.kt']
assert 'private var fetching = false' not in main_ui
assert 'private fun refreshRemote(' not in main_ui and 'private fun showConnectionChooser(' not in main_ui
assert 'override fun onRequestPermissionsResult(' in main_ui and 'override fun onActivityResult(' in main_ui
assert 'assertFalse(AppVersion.compatible("0.1.0-alpha.23"))' in app_version_test
for fname in ['ConnectionController.kt','ConnectionScreenUi.kt','PairingScreenUi.kt','PairingQrUi.kt','MonitorCredentialsUi.kt']:
    assert len(module_ui[fname].strip()) > 100
print('PASSOU: alpha.24, controles separados, host selecionado, cancelamento de respostas antigas e credenciais protegidas')

# alpha.25: regressão compileDebugKotlin do build #25.
# Dentro de LinearLayout.apply, `this` e a View.current do layout não são o Context.
# Referenciar explicitamente o receiver da extensão IncomingAccessActivity.infoMetric.
incoming_screen = module_ui["IncomingAccessScreenUi.kt"]
assert 'addView(View(this@infoMetric), LinearLayout.LayoutParams(dp(10), 1))' in incoming_screen
assert 'val column = LinearLayout(this@infoMetric).apply' in incoming_screen
assert 'addView(View(this), LinearLayout.LayoutParams(dp(10), 1))' not in incoming_screen
assert 'val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }' not in incoming_screen
assert 'assertFalse(AppVersion.compatible("0.1.0-alpha.24"))' in app_version_test
assert 'assertFalse(AppVersion.compatible("0.1.0-alpha.25"))' in app_version_test
print('PASSOU: alpha.25, Context correto nos construtores View/LinearLayout e versão sincronizada')

# alpha.26: dashboard, detalhes e coleta somente após autorização explícita do monitorado.
details = (ui_root / 'RemoteDetailsActivity.kt').read_text()
device_reader = (root / 'app/src/main/java/br/dev/monitorremoto/core/DeviceInfoReader.kt').read_text()
telephony_reader = (root / 'app/src/main/java/br/dev/monitorremoto/core/TelephonyReader.kt').read_text()
assert 'android:name=".ui.RemoteDetailsActivity" android:exported="false"' in manifest
assert 'put("deviceInfo", DeviceInfoReader.snapshot(context))' in server
assert 'put("telephony", TelephonyReader.snapshot(context))' in server
assert 'StatFs(Environment.getDataDirectory().absolutePath)' in device_reader
assert 'ActivityManager.MemoryInfo()' in device_reader
assert 'put("wifiDetected"' in network and 'put("cellularDetected"' in network
assert 'READ_PHONE_NUMBERS' in manifest and 'READ_PHONE_STATE' in manifest
assert 'getBoolean("share_phone_number", false)' in settings
assert 'if (optedIn && granted && Build.VERSION.SDK_INT >= 33)' in telephony_reader
assert 'phoneNumberPermissionRequest ->' in main_ui
assert 'SettingsStore.setSharePhoneNumber(this, false)' in ui
assert 'R.drawable.ic_battery' in ui and 'R.drawable.ic_thermometer' in ui
assert 'R.drawable.ic_folder_download' in ui and 'Recuperar dados' in ui
assert 'Em breve' in ui and 'showRemoteDetailsDialog(root)' in ui
assert 'Armazenamento' in details and 'Rede móvel e chip' in details and 'Sistema e hardware' in details
assert 'Copiar resumo técnico' in details and 'FLAG_SECURE' in details
assert len(list((root / 'app/src/main/res/drawable').glob('ic_*.xml'))) >= 25
print('PASSOU: alpha.26, painel compacto, nova Activity, leitores, consentimento e recursos futuros')

# alpha.27: interface padronizada em módulos sem regressão no fluxo autorizado.
components = module_ui['UiComponents.kt']
home = module_ui['HomeScreenUi.kt']
monitor = module_ui['MonitorScreenUi.kt']
settings_ui = module_ui['SettingsScreenUi.kt']
qr_reader = (ui_root / 'QrScanActivity.kt').read_text()
for function in ['iconTile(', 'iconMenuRow(', 'iconSectionHeader(', 'iconMetric(', 'twoColumnTiles(', 'headerIconButton(']:
    assert function in components, f'Componente visual alpha.27 ausente: {function}'
assert 'twoColumnTiles(shortcuts' in home and 'R.drawable.ic_phone' in home and 'showHomeSavedDeviceOptions(item)' in home
assert 'iconTile(R.drawable.ic_qr, "Conectar novo aparelho"' in home
assert 'addCompact("Gerar QR"' in monitor and 'addCompact("Mostrar info"' in monitor and 'addCompact("Interromper"' in monitor
assert 'iconMetric("IP da rede"' in monitor and 'SettingsStore.setMonitorEnabled(this, false)' in monitor
assert 'renderSettingsModule()' in settings_ui and 'iconMenuRow(R.drawable.ic_sim' in settings_ui
assert 'iconMetric("Porta TCP"' in settings_ui and 'fillAppearanceSettings(' in settings_ui
assert 'iconTile(R.drawable.ic_qr, "QR Code"' in pairing_ui and 'iconTile(R.drawable.ic_phone, "Manual"' in pairing_ui
assert 'showManualConnectionDialog()' in pairing_ui and 'setOnClickListener' in pairing_ui
assert 'iconSectionHeader(R.drawable.ic_qr, "Pareamento seguro"' in pairing_qr
assert 'PairingSession.revoke(offer.session)' in pairing_qr and 'WindowManager.LayoutParams.FLAG_SECURE' in pairing_qr
assert 'headerIconButton(R.drawable.ic_settings' in main_ui
assert 'Aponte para o QR Code' in qr_reader and 'this@QrScanActivity' in qr_reader
assert 'ic_qr' in qr_reader and 'window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)' in qr_reader
assert 'ClipDescription.EXTRA_IS_SENSITIVE' in credentials_ui
assert 'assertFalse(AppVersion.compatible("0.1.0-alpha.26"))' in app_version_test
print('PASSOU: alpha.27, Home, Monitorado, Configurações, QR, credenciais, ícones e versões')

# alpha.28: diagnóstico, contrato de incompatibilidade e controle central de diálogos.
connection_issue = (ui_root / 'ConnectionIssue.kt').read_text(encoding='utf-8')
connection_error = (ui_root / 'ConnectionErrorUi.kt').read_text(encoding='utf-8')
app_dialog = (ui_root / 'AppDialogUi.kt').read_text(encoding='utf-8')
assert '"versionMismatch"' in server and 'WireProtocol.encode(permanentKey, mismatch)' in server
assert 'if (it.optString("op") == "versionMismatch")' in client
assert 'ConnectionIssue.from(error)' in remote_manager
assert 'EOFException' in connection_issue and 'SocketTimeoutException' in connection_issue
assert 'remoteVersion' in connection_error and 'Copiar diagnóstico' in connection_error
assert 'Tentar novamente' in connection_error and 'Editar conexão' in connection_error
assert 'showManagedDialog' in app_dialog and 'activeUiDialog' in main_ui
assert 'val inputError = label(' in pairing_ui and 'inputError.visibility = View.VISIBLE' in pairing_ui
assert 'fun refreshList(filter: String)' in settings_ui and 'getApplicationIcon(' in settings_ui
assert 'Nenhum aplicativo elegível' in settings_ui and 'CheckBox(this)' in settings_ui
assert 'showAppConfirm(' in settings_ui and 'showAppConfirm(' in credentials_ui
assert 'assertFalse(AppVersion.compatible("0.1.0-alpha.27"))' in app_version_test
assert 'assertFalse(AppVersion.compatible("0.1.0-alpha.28"))' in app_version_test
assert (root / 'app/src/test/java/br/dev/monitorremoto/ui/ConnectionIssueTest.kt').exists()
print('PASSOU: alpha.28, incompatibilidade cifrada, erros classificados, diálogo único e seleção de apps')

# Regressão alpha.29: o workflow 29 falhou com duas terminações `.create()`
# em showIncomingAccessActions, causando erro de sintaxe Kotlin a partir da linha 143.
# Não regressar para .let { showManagedDialog(it) } seguido de novo .create(),
# nem invocar a apresentação do mesmo diálogo duas vezes.
access_actions = monitor.split('private fun MainActivity.showIncomingAccessActions(', 1)
assert len(access_actions) == 2, 'Ações de acesso recebido não encontradas'
access_actions = access_actions[1]
assert access_actions.count('val dialog = AlertDialog.Builder(this)') == 1, 'Construtor do diálogo deve ser único'
assert access_actions.count('.create()') == 1, 'O diálogo deve ser criado apenas uma vez'
assert access_actions.count('showManagedDialog(dialog)') == 1, 'O diálogo deve ser apresentado uma única vez'
assert '.create().let { showManagedDialog(it) }' not in access_actions, 'Terminação antiga duplicada'
assert 'showAppConfirm(' in access_actions and 'confirmRotateMonitorKey()' in access_actions
print('PASSOU: alpha.29, construtor e exibição únicos no diálogo de acesso autenticado')

# alpha.30: consentimentos novos e envio seguro, sem AccessibilityService. Alpha.31 acrescenta RECEIVER SMS consentido.
assert 'android.permission.READ_SMS' in manifest and 'android.permission.SEND_SMS' in manifest
assert 'android.permission.RECEIVE_SMS' in manifest
assert 'android.accessibilityservice.AccessibilityService' in manifest and 'android:exported="true"' in manifest
assert 'getBoolean("share_sms_inbox", false)' in settings
assert 'getBoolean("allow_sms_replies", false)' in settings
assert 'getBoolean("allow_notification_replies", false)' in settings
assert 'smsReadPermissionRequest ->' in main_ui and 'smsSendPermissionRequest ->' in main_ui
assert 'notificationReply' in server and 'smsReply' in server and 'commandResult' in server
assert 'NotificationReplyActions.reply' in server and 'SmsReplySender.send' in server
assert 'if (regular.optBoolean("includeSms")) SmsInboxReader.snapshot(context)' in server
assert '.put("includeSms", includeSms)' in client
assert 'fun loadMessages(' in remote_manager
assert (root / 'app/src/main/java/br/dev/monitorremoto/core/SmsInboxReader.kt').exists()
assert (root / 'app/src/main/java/br/dev/monitorremoto/capture/NotificationReplyActions.kt').exists()
assert (root / 'app/src/test/java/br/dev/monitorremoto/core/MessagePolicyTest.kt').exists()
assert 'openRemoteMessagesScreen()' in module_ui['RemoteDashboardUi.kt']
assert 'RemoteInput.addResultsToIntent' in (root / 'app/src/main/java/br/dev/monitorremoto/capture/NotificationReplyActions.kt').read_text()
assert 'showAppConfirm("Confirmar envio remoto?"' in module_ui['RemoteMessagesUi.kt']
print('PASSOU: alpha.30, consentimentos SMS/RemoteInput, controle remoto autenticado e UI de confirmação')

# alpha.31: SMS opcional, notificação expandida, captura de tela de SMS, sem expor credenciais.
for name in ['SmsReceivedReceiver.kt', 'SmsStatusReceiver.kt']:
    assert (root / 'app/src/main/java/br/dev/monitorremoto/capture' / name).exists(), name
for name in ['SmsLiveJournal.kt', 'SmsDeliveryJournal.kt', 'MmsMetadataReader.kt', 'SmsSubscriptions.kt']:
    assert (root / 'app/src/main/java/br/dev/monitorremoto/core' / name).exists(), name
messages = module_ui['RemoteMessagesUi.kt']
assert 'addFlags(' not in messages, 'Mensagens não devem bloquear captura de tela'
assert 'copyRemoteMessage(' in messages and '"Responder"' in messages and '"Responder SMS"' in messages
assert 'ClipData.newPlainText' in messages
assert 'SettingsStore.allNotifications(context)' in server and 'SettingsStore.allNotifications(this)' in (root / 'app/src/main/java/br/dev/monitorremoto/capture/NotificationCaptureService.kt').read_text()
assert '"all_notifications", false' in settings and '"receive_sms_events", false' in settings
assert 'smsReceivePermissionRequest ->' in main_ui and 'smsSimPermissionRequest ->' in main_ui
assert 'SmsDeliveryJournal.latest()' in server and 'MmsMetadataReader.snapshot(context)' in server
assert 'SmsSubscriptions.sender(context, subscriptionId)' in (root / 'app/src/main/java/br/dev/monitorremoto/core/SmsReplySender.kt').read_text()
assert 'WindowManager.LayoutParams.FLAG_SECURE' in module_ui['MonitorCredentialsUi.kt'] and 'FLAG_SECURE' in module_ui['PairingQrUi.kt']
print('PASSOU: alpha.31, SMS realtime/status/SIM, MMS metadados, notificações opt-in e captura permitida somente em Mensagens')

# alpha.32: módulo de Configurações real, estado TCP verificado sem depender da UI.
monitor_service = (root / 'app/src/main/java/br/dev/monitorremoto/service/MonitorService.kt').read_text()
monitor_server = (root / 'app/src/main/java/br/dev/monitorremoto/transport/MonitorServer.kt').read_text()
assert 'mode = 3' in main_ui and 'settingsSection = "home"' in main_ui
assert '3 -> renderSettingsModule()' in main_ui and 'navigateBackFromSettings()' in main_ui
assert 'renderSettingsModule()' in settings_ui and 'fillNotificationSettings(' in settings_ui
assert 'fillSmsSettings(' in settings_ui and 'fillServiceSettings(' in settings_ui
assert 'fillDiagnosticsSettings(' in settings_ui and 'showManagedDialog(' in settings_ui  # only app-picker dialog
assert 'fun isHealthy()' in monitor_server and 'acceptThread?.isAlive' in monitor_server
assert 'private val serverWatchdog' in monitor_service and 'ensureServerReady()' in monitor_service
assert 'registerNetworkCallback(' in monitor_service and 'nextRecoveryMs' in monitor_service
assert 'return START_STICKY' in monitor_service and 'ACTION_STOP' in monitor_service
assert 'recordServerHealth(' in monitor_service and 'serverLastCheck(' in settings
assert 'Intent.ACTION_MY_PACKAGE_REPLACED' in (root / 'app/src/main/java/br/dev/monitorremoto/service/BootReceiver.kt').read_text()
assert 'android.intent.action.MY_PACKAGE_REPLACED' in manifest
print('PASSOU: alpha.32, recuperação TCP, diagnóstico e Configurações sem janela gigante')

# alpha.33: o módulo Mensagens não pode mais exibir uma lista gigante num AlertDialog.
assert '4 ->' not in main_ui or 'renderRemoteMessagesPage(); return' in main_ui
assert 'renderRemoteMessagesPage(); return' in main_ui
assert 'openRemoteMessagesScreen()' in module_ui['RemoteDashboardUi.kt']
assert 'fun MainActivity.renderRemoteMessagesPage()' in messages
assert 'messagesTab = index' in messages and 'SMS (${sms.size})' in messages
assert 'showRemoteMessages(' not in messages, 'Remover a janela antiga de mensagens'
assert 'AlertDialog.Builder(this).setTitle("Mensagens remotas")' not in messages
assert 'messagesRequestGeneration++' in messages and 'messagesData = null' in messages
assert 'MessagePolicy.looksLikeAuthenticationSms' in messages
sensitive = (root / 'app/src/main/java/br/dev/monitorremoto/core/SensitiveContent.kt').read_text()
assert 'authentication' in sensitive and 'verification' in sensitive
assert '!SensitiveContent.isSensitive(it.title, it.preview)' in journal
assert 'EnglishAuthentication' in (root / 'app/src/test/java/br/dev/monitorremoto/core/SensitiveContentTest.kt').read_text()
print('PASSOU: alpha.33, central de Mensagens em página própria, abas, filtro OTP multilíngue, sem diálogo enorme')

# alpha.34: captura somente com consentimento local, foreground visível e sessão efêmera.
screen = (root / 'app/src/main/java/br/dev/monitorremoto/capture/ScreenShareService.kt').read_text()
frame_store = (root / 'app/src/main/java/br/dev/monitorremoto/capture/ScreenFrameStore.kt').read_text()
viewer = (root / 'app/src/main/java/br/dev/monitorremoto/ui/RemoteScreenActivity.kt').read_text()
assert 'android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION' in manifest
assert 'android:foregroundServiceType="mediaProjection"' in manifest
assert 'android:name=".ui.RemoteScreenActivity"' in manifest
assert 'createScreenCaptureIntent()' in ui and 'screenCaptureRequest' in main_ui
assert 'if (resultCode == Activity.RESULT_OK && data != null' in main_ui
assert 'FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION' in screen
assert 'getMediaProjection(resultCode, grant)' in screen and 'registerCallback(' in screen
assert 'override fun onStop()' in screen and 'override fun onCapturedContentResize' in screen
assert 'START_NOT_STICKY' in screen and 'ACTION_STOP' in screen
assert 'fun stop()' in frame_store and 'STALE_MS = 3_000L' in frame_store
assert 'MAX_JPEG_BYTES = 110_000' in frame_store
assert '"screenFrame"' in server and 'ScreenFrameStore.snapshot()' in server
assert 'fun screenFrame(' in client and 'RemoteClient.screenFrame(' in viewer
assert '"Ver tela", "Ao vivo' in module_ui['RemoteDashboardUi.kt']
assert 'createVirtualDisplay(' in screen and 'createVirtualDisplay(' not in viewer
assert 'MediaProjectionManager' not in viewer
assert 'ScreenShareService.stop(this)' in monitor_service
print('PASSOU: alpha.34, projeção autorizada, FGS visível, frame criptografado/volátil, sem reinício oculto')

# alpha.35: acesso explícito e independente. Protocolo AES-GCM sem portas extras.
touch = (root / 'app/src/main/java/br/dev/monitorremoto/capture/RemoteTouchService.kt').read_text()
audio = (root / 'app/src/main/java/br/dev/monitorremoto/capture/AudioChunkStore.kt').read_text()
request = (root / 'app/src/main/java/br/dev/monitorremoto/capture/ScreenShareRequest.kt').read_text()
ui_screen = (root / 'app/src/main/java/br/dev/monitorremoto/ui/RemoteScreenActivity.kt').read_text()
assert 'android.permission.RECORD_AUDIO' in manifest
assert 'android.permission.BIND_ACCESSIBILITY_SERVICE' in manifest
assert 'android:canRetrieveWindowContent="false"' in (root / 'app/src/main/res/xml/remote_touch_accessibility.xml').read_text()
assert 'android:canPerformGestures="true"' in (root / 'app/src/main/res/xml/remote_touch_accessibility.xml').read_text()
assert 'getBoolean("remote_touch", false)' in settings and 'getBoolean("internal_audio", false)' in settings
assert 'ScreenFrameStore.sharing' in touch and 'SettingsStore.allowRemoteTouch(service)' in touch
assert 'AudioPlaybackCaptureConfiguration.Builder(grantedProjection)' in screen
assert 'checkSelfPermission(Manifest.permission.RECORD_AUDIO)' in screen
assert 'AudioChunkStore.stop()' in screen and 'MAX_CHUNK = 16_000' in audio
assert '"remoteTouch"' in server and '"audioChunk"' in server and '"requestScreen"' in server
assert 'ScreenShareRequest.notifyLocal(context)' in server and '60_000L' in request
assert 'RemoteClient.touch(' in ui_screen and 'RemoteClient.audioChunk(' in ui_screen
assert 'setOnTouchListener' in ui_screen and 'Touch: ligado' not in ui_screen
assert 'touchReady' in server and 'audioReady' in server
assert 'ACTION_AUDIO_STOP' in screen and 'ScreenShareService.stop(this)' in module_ui['MonitorScreenUi.kt']
assert (root / 'app/src/test/java/br/dev/monitorremoto/core/RemoteGesturePolicyTest.kt').exists()
print('PASSOU: alpha.35, toque e áudio internos opt-in, MediaProjection em foreground e solicitação local')
