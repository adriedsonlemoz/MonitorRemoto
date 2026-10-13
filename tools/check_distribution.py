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
assert version == "0.1.0-alpha.11" and code.group(1) == "11", "Versões incompatíveis"
assert version in ui, "Tela de versão desatualizada"
assert f"versionCode={code.group(1)}" in (root / 'docs/ARCHITECTURE.md').read_text(), "Arquitetura com versionCode desatualizado"
# alpha.9 remove a Activity antiga do ZXing Embedded; Camera2 + ZXing Core offline.
assert 'IntentIntegrator' not in ui and 'BarcodeEncoder' not in ui
assert 'startActivityForResult(Intent(this, QrScanActivity::class.java), qrScanRequest)' in ui
assert 'override fun onActivityResult(' in ui and 'QrScanActivity.EXTRA_QR_RESULT' in ui
assert 'private fun askQrText()' in ui
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
assert 'private fun showConnectionDialog()' in ui and 'private fun showSettingsDialog()' in ui
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
print('PASSOU: UI alpha.11, migração única, Keystore por host, tema e sugestão de IP')
