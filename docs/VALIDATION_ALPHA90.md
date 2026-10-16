# Validação alpha.90 · correção do workflow 82

## Falha encontrada
- `RemoteFilesActivity.kt:604:40`: `Unresolved reference 'scroll'`.
- `RemoteFilesActivity.kt:604:48`: `Unresolved reference 'create'` (cascata da primeira falha).
- `RemoteFilesActivity.kt:605–606`: acesso não seguro a `AlertDialog?`.
- `:app:compileDebugKotlin FAILED` antes do APK Debug.

## Ajustes
- `openActions()` declara `ScrollView` local envolvendo o conteúdo do menu inferior.
- `AlertDialog` local não nulo usado após `create()`; menu nullable só para encerramento nas callbacks.
- VersionName/versionCode 0.1.0-alpha.90/90 e regressão no validador.

## Pendências
- Executar GitHub Actions: `:app:testDebugUnitTest :app:assembleDebug` e inspecionar qualquer novo erro.
- Teste no Android dos menus longos e das permissões de acesso aos arquivos na pasta fixa.
