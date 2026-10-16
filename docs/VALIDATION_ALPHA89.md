# Validação alpha.89

## Alterações
- Pasta fixa Downloads/Monitor Remoto no MediaStore (Android 10+), acessando somente arquivos de propriedade do aplicativo. O app materializa a pasta com um README após a abertura/início de monitoramento.
- Interface sem seletores de diretório no fluxo normal. Seletor de arquivos ainda aparece somente para escolher os arquivos a enviar.
- Modernização de menus e diálogos sem relaxar autenticação, confirmação de exclusão e restrições do Android.

## Validação local
- Verificador de distribuição e parser sintático Kotlin; integridade XML/ZIP e testes de políticas, conforme resultados de execução local.
- Sem Android SDK/Gradle disponível neste ambiente: `:app:assembleDebug` e teste físico permanecem pendentes.
