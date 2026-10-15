# Monitor Remoto v0.1.0-alpha.69

**Correção de compilação da interface de Configurações**: o workflow **Gerar APK e Release #64** falhou em `:app:compileDebugKotlin` com 8 erros em `UiComponents.kt` e `AppDialogUi.kt`. O espaçamento do TextView agora chama `setLineSpacing` em vez de atribuir a `val lineSpacingExtra`, e a função de tema `darkTheme()` foi disponibilizada internamente à UI modular. Testes estruturais de regressão foram adicionados. Mantém o redesenho da alpha.68 e as funções anteriores, sem novas permissões, fluxo de rede ou comandos. **Ainda requer novo build Android/Gradle e teste físico.**

---

# Monitor Remoto v0.1.0-alpha.68

**Melhoria de usabilidade nas Configurações:** os fluxos de ativar, desativar, escolher e confirmar ações estavam confusos, com letras pequenas e estados pouco claros. A alpha.68 redesenha os cartões de Configurações com tipografia maior, ícones e espaçamentos reforçados e um selo visual em formato de botão (`ATIVADO`, `DESATIVADO`, `ABRIR`, `REVOGAR`).

Também foram refeitos os diálogos do app usados em Configurações: agora usam botões grandes e consistentes com o visual do Monitor Remoto, facilitando confirmar ou cancelar operações locais como autorizações, revogações e permissões.

As funcionalidades existentes foram preservadas; não foram adicionadas permissões novas, nem alterados o canal autenticado, o pareamento, a captura de tela, a câmera, os comandos físicos ou a recuperação de arquivos. Esta versão ainda precisa de compilação Gradle/Android e validação manual em aparelho.

---

# Monitor Remoto v0.1.0-alpha.67

**Correção do workflow #62:** os cinco testes locais do armazenamento de JPEG da câmera falhavam ao chamar `SystemClock.elapsedRealtime()` fora do Android. A alpha.67 adota um relógio monotônico JVM/Android somente no `CameraFrameStore`, sem alterar permissões, canal autenticado nem controle de consentimento. A compilação Kotlin da alpha.66 havia sido aprovada, mas a suíte de testes interrompeu o workflow antes do APK. Esta versão ainda exige execução integral do GitHub Actions e validação em aparelhos.

---

# Monitor Remoto v0.1.0-alpha.66

## Correção de compilação da câmera compartilhada

- Corrigido erro Kotlin da execução **Gerar APK e Release #61 (tentativa 2)** em `CameraShareActivity.kt:83`: `TextureView` estava sendo atribuído ao `SurfaceTextureListener`, devido ao `this` implícito do bloco `apply`.
- O listener agora aponta explicitamente para `this@CameraShareActivity`. Nenhum comando remoto, permissão, criptografia ou função foi alterado.
- Mantidos som, lanterna, bloqueio, inventário de aplicativos, notificações, arquivos, ADB Wi-Fi, transmissão de tela, câmera JPEG e troca remota autorizada.
- Esta versão precisa passar por `:app:testDebugUnitTest :app:assembleDebug` no GitHub Actions antes de declarar APK funcional. Testes físicos também permanecem pendentes.
