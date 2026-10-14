# Dependências do módulo ADB

- libadb-android 3.1.1: Muntashir Al-Islam; Apache-2.0 OU GPL-3.0-or-later, escolhida Apache-2.0. Fonte e licenças incluídas em adb-core, origem/commit em UPSTREAM.md. Modificações locais descritas em ADB.md; não são release oficial do upstream.
- spake2-android 2.2.1: https://github.com/MuntashirAkon/spake2-java ; LGPL-3.0; fonte correspondente da tag 2.2.1, submódulo nativo e licença incluídos em `third_party/spake2-2.2.1`. O código do aplicativo e scripts de build permitem recompilar/revincular uma versão modificada.
- Bouncy Castle bcprov/bcpkix/bcutil 1.81: licença Bouncy Castle (estilo MIT), https://www.bouncycastle.org/licence.html
- Conscrypt Android 2.5.3: Apache-2.0 com notices de componentes incorporados, https://github.com/google/conscrypt
- AndroidX Annotation 1.9.1: Apache-2.0, https://android.googlesource.com/platform/frameworks/support/
- ZXing Core 3.5.4 (já existente): Apache-2.0, https://github.com/zxing/zxing

Não foi incluída a dependência opcional sun.security-android/LGPL sugerida em exemplos do libadb; certificados são gerados com Bouncy Castle. Não foi adicionado HiddenApiBypass. Dependências são resolvidas pelo Gradle, sem copiar credenciais, SDK ou caches para o ZIP. Nenhuma licença foi removida do código de terceiros incluído.
