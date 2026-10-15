# Monitor Remoto v0.1.0-alpha.67

**Correção do workflow #62:** os cinco testes locais do armazenamento de JPEG da câmera falhavam ao chamar `SystemClock.elapsedRealtime()` fora do Android. A alpha.67 adota um relógio monotônico JVM/Android somente no `CameraFrameStore`, sem alterar permissões, canal autenticado nem controle de consentimento. A compilação Kotlin da alpha.66 havia sido aprovada, mas a suíte de testes interrompeu o workflow antes do APK. Esta versão ainda exige execução integral do GitHub Actions e validação em aparelhos.

---

# Monitor Remoto v0.1.0-alpha.66

## Correção de compilação da câmera compartilhada

- Corrigido erro Kotlin da execução **Gerar APK e Release #61 (tentativa 2)** em `CameraShareActivity.kt:83`: `TextureView` estava sendo atribuído ao `SurfaceTextureListener`, devido ao `this` implícito do bloco `apply`.
- O listener agora aponta explicitamente para `this@CameraShareActivity`. Nenhum comando remoto, permissão, criptografia ou função foi alterado.
- Mantidos som, lanterna, bloqueio, inventário de aplicativos, notificações, arquivos, ADB Wi-Fi, transmissão de tela, câmera JPEG e troca remota autorizada.
- Esta versão precisa passar por `:app:testDebugUnitTest :app:assembleDebug` no GitHub Actions antes de declarar APK funcional. Testes físicos também permanecem pendentes.
