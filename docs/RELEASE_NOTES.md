# Monitor Remoto v0.1.0-alpha.76

## Notificação inteligente e identidade visual

- Ícone de escudo próprio na tela inicial e nas notificações.
- Notificação do serviço indica **Aguardando controlador**, **Acesso(s) recente(s)** ou **Reconectando**, usando acessos autenticados com validade de até 45 segundos e o estado do servidor.
- O resumo mostra módulos locais ativos: localização, compartilhamento de tela, câmera do Monitor Remoto e alertas ativados. Em Detalhes são mostrados acessos recentes e seus horários. **Nome informado pelo controlador não confirma identidade.**
- Botões **Parar** e **Detalhes** no aviso permanente. Atualizações sem alerta sonoro repetido; conteúdo sensível oculto na versão pública da tela bloqueada.
- A notificação independente da localização continua com a própria ação Parar localização. Sem novas permissões, mudanças no protocolo de pareamento ou captura oculta.
- Ambos os celulares devem utilizar a alpha.76. A compilação Android completa e a verificação do layout real em aparelhos continuam pendentes.

---

# Monitor Remoto v0.1.0-alpha.75

## Localização remota autorizada

1. Instale a alpha.75 nos dois aparelhos. No monitorado, ative o serviço e abra Configurações > Localização.
2. Selecione um perfil, toque Iniciar compartilhamento e conceda localização (e notificações, quando pedidas). Ative localização no Android.
3. Com a notificação de compartilhamento visível, no controlador toque Localização e Atualizar posição. A primeira correção pode demorar, especialmente em ambientes fechados.
4. Para copiar coordenadas ou abrir em um aplicativo de mapas instalado, use as ações na tela do controlador. O mapa pode requerer dados offline baixados previamente ou acesso à internet.
5. Para terminar, toque Parar compartilhamento no monitorado ou Parar localização na notificação. Parar o Monitor Remoto também encerra a sessão.

**Segurança e limites:** nenhuma ativação remota silenciosa, histórico ou persistência de coordenadas. A localização enviada depende de sessão local e chave AES-GCM dos aparelhos pareados, sem autorização individual por controlador. Intervalos são mínimos e não garantem precisão/frequência; funcionamento com tela apagada depende das regras de FGS/bateria do Android. Dados de GPS não dependem de internet, mas a comunicação requer rede local/VPN. Build APK e teste físico pendentes.

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