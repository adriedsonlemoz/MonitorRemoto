# Monitor Remoto v0.1.0-alpha.29

## Correção de compilação

- Corrigida a duplicação de `.create()` no final de `showIncomingAccessActions`, em `MonitorScreenUi.kt`, que impedia `:app:compileDebugKotlin` no workflow 29.
- As ações do diálogo **Acesso autenticado** foram preservadas: Trocar chave, Fechar e Parar serviço com confirmação.
- Novo teste de regressão no validador do projeto verifica uma única criação e uma única exibição do diálogo.

## Recursos mantidos da alpha.28

- Diálogos gerenciados para evitar sobreposição; confirmação visível de ações sensíveis.
- Diagnóstico de conexão, inclusive resposta de versão incompatível criptografada somente após autenticação.
- Seleção explícita de aplicativos de notificação, QR temporário e credenciais Android Keystore.
- Não houve novas permissões Android. O protocolo exige **versões idênticas** nos dois aparelhos.

## Validação e distribuição

Execute novamente o GitHub Actions para compilar/testar e, se bem-sucedido, publicar o APK em Releases. Instale a alpha.29 em **ambos** os celulares. Este ZIP contém somente código-fonte, não um APK.
