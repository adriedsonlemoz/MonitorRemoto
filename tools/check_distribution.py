#!/usr/bin/env python3
"""Testa invariantes de versão e distribuição, sem Gradle/SDK."""
from pathlib import Path
import re

root = Path(__file__).resolve().parent.parent
gradle = (root / "app/build.gradle.kts").read_text(encoding="utf-8")
workflow = (root / ".github/workflows/android.yml").read_text(encoding="utf-8")
ui = (root / "app/src/main/java/br/dev/monitorremoto/ui/MainActivity.kt").read_text(encoding="utf-8")
match = re.search(r'\bversionName\s*=\s*"([^"]+)"', gradle)
assert match, "versionName ausente"
version = match.group(1)
code = re.search(r'\bversionCode\s*=\s*(\d+)', gradle)
assert code, "versionCode ausente"
assert version == "0.1.0-alpha.21" and code.group(1) == "21", "Versões incompatíveis"
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
assert 'android.permission.RECEIVE_SMS' not in manifest
assert 'android.accessibilityservice' not in manifest
print(f"PASSOU: versionName={version}, versionCode={code.group(1)}, nome APK, Release, assinatura, AAB e permissões")

# Privacidade: limpeza efetiva e verificação na hora da transmissão.
journal = (root / 'app/src/main/java/br/dev/monitorremoto/core/EventJournal.kt').read_text(encoding='utf-8')
settings = (root / 'app/src/main/java/br/dev/monitorremoto/core/SettingsStore.kt').read_text(encoding='utf-8')
server = (root / 'app/src/main/java/br/dev/monitorremoto/transport/MonitorServer.kt').read_text(encoding='utf-8')
assert 'fun clear()' in journal and 'fun retainAllowed(' in journal and 'allowedPackages' in journal
assert 'if (!enabled || !wasEnabled) EventJournal.clear()' in settings
assert 'EventJournal.retainAllowed(packages)' in settings
assert 'EventJournal.latestJson(SettingsStore.notificationAllowlist(context))' in server
assert 'ArrayBlockingQueue<Runnable>(12)' in server and 'ThreadPoolExecutor.AbortPolicy()' in server
print('PASSOU: limpeza por revogação e limite de fila do servidor declarados')

# Regressão alpha.7: leitura QR não pode depender de Activity externa nem de permissão presumida.
assert 'IntentIntegrator' not in ui
assert 'requestPermissions(arrayOf(Manifest.permission.CAMERA)' in ui
assert 'android:name=".ui.QrScanActivity"' in manifest and 'android:exported="false"' in manifest
assert 'fun latest(allowedPackages: Set<String>): List<RecordedEvent>' in journal
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
assert 'private fun showConnectionChooser()' in ui and 'private fun showManualConnectionDialog()' in ui and 'private fun showSettingsDialog()' in ui
assert 'private fun renderController()' in ui and 'private fun updateConnectionLabels()' in ui
assert 'private fun localWifiPrefix()' in ui and 'TRANSPORT_WIFI' in ui
assert 'Ipv4Suggestion.prefix(' in ui and 'prefixLength' in ui
assert 'RecentDevices.migrateLegacy(this)' in ui and 'RecentDevices.save(' in ui
assert 'RecentDevices.key(this, host)' in ui and 'RecentDevices.markSuccess(' in ui
assert 'private var lastSuccessfulMs' in ui and 'connectionState = "offline"' in ui
assert 'fun setTheme(' in settings and '"light"' in settings and '"dark"' in settings
assert 'const val LIMIT = 5' in profiles and 'fun forget(' in profiles and 'fun rename(' in profiles
assert 'profileKeyName(' in secret and 'MessageDigest.getInstance("SHA-256")' in secret
assert 'fun prefix(' in ip_suggestion
assert (root / 'app/src/test/java/br/dev/monitorremoto/core/Ipv4SuggestionTest.kt').is_file()
print('PASSOU: UI alpha.15, migração única, Keystore por host, tema e sugestão de IP')

# alpha.15: entrada simples, ações explícitas e câmera em janela flutuante.
assert 'private fun connectSavedDevice(' in ui and 'private fun renameSavedDevice(' in ui
assert 'private fun forgetSavedDevice(' in ui and 'private fun showConnectionChooser()' in ui
assert 'showManualConnectionDialog()' in ui and 'setTitle("Confirmar aparelho")' in ui
assert 'actionButton("↻ Atualizar")' in ui and 'LinearLayout.LayoutParams(dp(116), dp(42))' in ui
assert '@android:style/Theme.Material.Light.Dialog.NoActionBar' in manifest
assert 'window.setLayout(' in scan and 'Aponte para o QR Code' in scan
print('PASSOU: fluxo alpha.15, confirmação QR, botões de ação, scanner pop-up')

# alpha.17: Home de escolha, modo Monitorado separado e manual seguro.
assert 'savedInstanceState?.getInt("active_mode", 2)' in ui
assert 'private fun navigateHome()' in ui and 'private fun renderHome()' in ui
assert 'private fun showMyConnectionInfo()' in ui and 'private fun preferredLocalAddress()' in ui
assert 'showAboutDialog()' in ui and 'showMainMenu()' not in ui
assert 'showMyConnectionInfo()' in ui and 'getOrCreateMonitorKey(this)' in ui
assert 'WindowManager.LayoutParams.FLAG_SECURE' in ui
assert 'ClipDescription.EXTRA_IS_SENSITIVE' in ui and 'Intent.ACTION_SEND' in ui
assert 'setTitle("Compartilhar credencial?")' in ui and 'setNegativeButton("Cancelar", null)' in ui
assert 'showConnectionChooser()' in ui and 'showHomeSavedDeviceOptions(' in ui
assert 'if (mode != 2) { mode = 2; render() }' in ui
print('PASSOU: alpha.17, Home independente, informações manuais protegidas e recentes')

# alpha.17: não reapresentar Monitorado como Home; atalhos separados e ação de gerenciamento por aparelho.
assert 'private var mode = 2 // 2: início, 0: monitorado, 1: controle' in ui
assert 'else -> renderHome()' in ui and '0 -> renderMonitor()' in ui and '1 -> {' in ui
assert '＋  Conectar novo aparelho' in ui and 'Aparelhos anteriores' in ui
assert 'Última conexão: $lastUsed' in ui and 'item.lastSuccessMs > 0L' in ui
assert 'showHomeSavedDeviceOptions(item)' in ui and 'returnToChooser = false' in ui
assert 'Abrir tela do aparelho' in ui and 'mode = 0' in ui
assert 'val columns = LinearLayout(this)' in ui and 'LinearLayout.LayoutParams(dp(118), -2)' in ui
assert 'addCompact("Gerar QR"' in ui and 'addCompact("Mostrar info")' in ui and 'addCompact("Interromper")' in ui
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
assert 'mode = 2\n        render()' in ui, 'Desconectar não retorna à Home'
assert 'actionButton("▣ QR Code"' in ui and 'actionButton("⌨ Manual"' in ui
assert 'addAction("Conectar", true)' in ui and 'addAction("Renomear")' in ui
assert 'addAction("Esquecer", red = true)' in ui
assert 'addInfoAction(revealButton)' in ui and 'addInfoAction(actionButton("Copiar"' in ui
assert 'addInfoAction(actionButton("Compartilhar"' in ui
assert 'private fun renderIncomingAccess()' in ui and 'private fun showIncomingAccessActions(' in ui
assert (root / 'app/src/test/java/br/dev/monitorremoto/transport/AuthenticatedAccessTest.kt').exists()
print('PASSOU: alpha.18, menus compactos, Home após desconexão, alertas autenticados e tempo de expiração')

# alpha.19: painel remoto compacto com grid e atalhos.
assert 'private fun networkIcon(host: String)' in ui and 'private fun connectionKind(host: String)' in ui
assert 'private fun compactMetricCard(' in ui and 'private fun addMetricGrid(' in ui
assert 'private fun actionTile(' in ui and 'private fun addActionGrid(' in ui
assert 'private fun showRemoteNotifications(root: JSONObject)' in ui
assert 'private fun copyRemoteSnapshot(root: JSONObject)' in ui
assert 'private fun showRemoteDetailsDialog(root: JSONObject)' in ui
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
assert 'Acesso remoto detectado' in incoming and 'Quem está?' in incoming and 'Parar acesso' in incoming
assert 'android:name=".ui.IncomingAccessActivity"' in manifest and 'android:exported="false"' in manifest
assert 'ACCESS_CHANNEL' in service and 'IMPORTANCE_DEFAULT' in service
assert 'SettingsStore.isMonitorEnabled(this) && !MonitorService.isRunning' in ui
assert 'private fun recoverAuthorizedServiceIfStopped()' in ui
assert 'options.chunked(2)' in ui and 'settingsIcon(' in ui
assert 'private fun compactMetricCard(' in ui and 'minimumHeight = dp(98)' in ui
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
print('PASSOU: alpha.21, teste de compatibilidade segue a versão atual')
