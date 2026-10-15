# Monitor Remoto v0.1.0-alpha.74

## Destaques

- Visual mais compacto nas telas de conexão manual, erro por versões incompatíveis e copiar diagnóstico.
- Botões de ação sem sombra inconsistente, incluindo o botão Limpar.
- Componentes-base de diálogo, campos e badges compactados para manter a mesma linguagem visual da alpha.73 com menos altura e melhor aproveitamento do espaço.
- Requer o mesmo APK alpha.74 em ambos aparelhos; compilação Android completa e testes em hardware continuam pendentes.



**Diagnóstico do Minha Câmera e telemetria compacta:** painel Controle mostra o estado real do display (ligada, desligada ou ambiente, conforme o Android), bloqueio, indício de indisponibilidade da câmera e o último pacote em primeiro plano após acesso ao uso concedido localmente. A indicação de câmera ocupada **não confirma gravação**, pois o Monitor Remoto não tem integração de telemetria com Minha Câmera. Cartões de Bateria/Temperatura/Rede mais baixos; temperatura usa faixas orientativas coloridas e Wi-Fi apresenta SSID/RSSI quando o Android permite a leitura por permissão explícita de localização.

**Recuperação SAF:** navega até 12 níveis de pasta e copia arquivos nesse nível; rejeita nomes duplicados ambíguos; permite copiar pasta com subpastas para uma nova pasta de destino (até 20 arquivos, 128 MiB no lote e 32 MiB por arquivo, 150 entradas por pasta, 200 itens no total); agrupa dois blocos criptografados de 48 KiB por consulta e confere hash SHA-256 de cada bloco e do arquivo integral na origem/destino. Retries curtos não substituem retomada após encerramento do processo, e a cópia continua vinculada à Activity. Pastas parciais são removidas em caso de falha quando o provedor permite.

**Validação:** script de regressão e verificação sintática Kotlin; testes JVM puros da política e temperatura. **Ainda não compilada pelo Android Gradle Plugin nem testada em dois aparelhos**. Executar `:app:testDebugUnitTest :app:assembleDebug` na CI antes de instalar.

---

# Monitor Remoto v0.1.0-alpha.71

**Correção de compilação no visualizador:** workflow #66 falhou em `:app:compileDebugKotlin` na linha 472 de `RemoteScreenActivity.kt` por análise recursiva de tipos do Kotlin, causada por `poll` referenciar a si mesmo no inicializador. O polling agora declara `Runnable` explicitamente e o callback que recebe um resultado de visita anterior reagenda `this` em vez de referenciar a propriedade `poll`. Preservadas autenticação, consentimento, câmera visível, Configurações, executor único e os demais recursos.

**Validação:** análise estática de regressão e verificações locais; a compilação Android e o APK precisam ser comprovados por nova execução da CI.

---

# Monitor Remoto v0.1.0-alpha.70

**Estabilidade e fidelidade dos estados:** Configurações exibem `ATIVADO`, `DESATIVADO`, `ABRIR` ou `REVOGAR` a partir do estado real das permissões e preferências, não de palavras nos textos. A câmera compartilhada encerra apenas sua sessão de captura, protegendo uma nova sessão contra callbacks atrasados. O visualizador reutiliza uma thread para consultar quadros, descartando resultados obsoletos ao retornar ao app. Acrescentados testes Kotlin/JVM e verificações estruturais.

Sem alterações nas permissões, autorização local, protocolo de rede, pareamento, criptografia ou políticas de captura em primeiro plano. **APK e execução real ainda não validados por Gradle/Android ou testes físicos.**

---

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