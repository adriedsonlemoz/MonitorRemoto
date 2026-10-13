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
assert version == "0.1.0-alpha.3" and code.group(1) == "3", "Versões incompatíveis"
assert version in ui, "Tela de versão desatualizada"
assert 'dist/MonitorRemoto-v${VERSION}.apk' in workflow, "Arquivo .apk final não é nomeado"
assert 'gh release upload' in workflow and 'gh release create' in workflow, "Falta APK direto via Release"
assert 'actions/upload-artifact' in workflow, "Artifact de reserva ausente"
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
