# Monitor Remoto

**v0.1.0-alpha.14 / versionCode 14** · Android 8.0+ · Kotlin 2.2.21 · AGP 8.13.2 · Gradle 8.13

Dois modos no mesmo APK: **Monitorado** (servidor) e **Controle** (cliente).

## Recursos já implementados no código-fonte

- Snapshot de nível da bateria, carregamento, temperatura, tensão, corrente instantânea e média (quando o hardware/Android fornecem as propriedades).
- Captura de notificações **novas** autorizadas pelo usuário por `NotificationListenerService`. Não lê automaticamente o histórico de WhatsApp.
- Captura opcional de notificações de aplicativos selecionados. Não possui permissão de SMS direto.
- Filtro por conteúdo e bloqueio preventivo de nomes/pacotes financeiros conhecidos (não são infalíveis; o Android ainda concede acesso geral ao serviço).
- Histórico volátil limitado a 100 eventos no aparelho monitorado; mostra os últimos 30. Nada salvo em banco ou nuvem.
- Servidor TCP autenticado com chave de 256 bits e payload protegido por AES-256-GCM, timeout, nonce aleatório, janela temporal de 5 min e cache de rejeição de repetição por nonce.
- Segredos armazenados protegidos por Android Keystore no aparelho.
- Cliente com atualização ao abrir e polling de 15 segundos enquanto a tela do Controle estiver aberta.
- Pareamento por QR Code temporário no mesmo Wi-Fi/hotspot, com leitura pela câmera e conexão automática; conexão manual por IPv4/nome na mesma LAN ou entre redes por VPN privada (Tailscale/WireGuard). Porta **37841/TCP**.
- Notificação persistente do serviço foreground `connectedDevice`; parar por UI ou pela ação na notificação.
- Inicialização após reboot opcional (tentativa, sujeita a política do SO/fabricante).

## Como utilizar

1. Execute GitHub Actions > **Gerar APK e Release** > Run workflow. Para baixar **APK sem ZIP**, use Releases > `v0.1.0-alpha.14` > Assets > **`MonitorRemoto-v0.1.0-alpha.14.apk`**. Em Actions > Artifacts, um APK de contingência (quando presente) continua vindo em ZIP por comportamento padrão do GitHub. Instale o mesmo APK nos dois aparelhos.
2. No telefone secundário: abra modo **Monitorado**; autorize a notificação de serviço; ative o monitoramento; autorize notificações em Configurações. Selecione explicitamente os aplicativos cujas notificações deseja compartilhar.
3. Na tela Monitorado, toque em **Gerar QR Code para parear**; escolha o IP local acessível (normalmente interface Wi-Fi). O QR expira em 2 minutos e é válido para um único pareamento.
4. No telefone principal: modo **Controle**; toque em **Conectar > Ler QR**, aponte a câmera e confirme. IP e chave de acesso são salvos automaticamente. A digitação manual de IP e chave continua disponível.
5. Na internet: configure **Tailscale/WireGuard** em ambos, conecte-os à mesma rede privada VPN e, no Controle, substitua o IP Wi-Fi pelo IP/nome acessível na VPN. Não abra a porta no roteador à internet pública.
6. Com o secundário bloqueado, consulte os dados no principal. Depois de desligar/reiniciar, o serviço pode precisar ser reiniciado dependendo do Android, ROM, economia de bateria e permissões.

**Nota:** a versão alpha usa QR e/ou IP/hostname manual, mas não faz descoberta automática NSD/mDNS de aparelhos. O segundo aparelho requer preparação inicial presencial. O app não acessa dados sem consentimento/permissões. A notificação de monitoramento permanece visível.

## Considerações importantes

- **Google Play**: permissões SMS têm política extremamente restrita. A permissão `RECEIVE_SMS` foi removida. SMS novos só podem ser exibidos **quando aparecem em notificações do app de mensagens autorizado**. Não há leitura de caixa de entrada SMS, recuperação de mensagens ocultas nem garantia de aprovação na Play Store.
- **WhatsApp**: apenas texto das notificações que o Android oferece ao listener. Mensagens ocultas, silenciosas, apagadas, protegidas ou históricas não são recuperadas. É possível que o sistema oculte dados sensíveis.
- **Dados sensíveis**: mensagens e OTP podem ser exibidos caso o filtro não os identifique. A chave permite acesso a todos os últimos eventos; proteja e revogue a chave quando necessário.
- **Privacidade**: código não inclui nuvem nem telemetria. A VPN (se utilizada) é um serviço externo e tem políticas próprias.
- **Precisão da bateria**: a corrente `BATTERY_PROPERTY_CURRENT_NOW` é líquida e pode ter sinal ou unidade incorreta em alguns fabricantes. Não confundir com corrente no carregador. Medidas indisponíveis aparecem como indisponíveis.
- **Rede**: comunicação cifrada na aplicação mesmo em LAN, porém o endereço IP/porta ainda é observável. Não há TLS/mTLS. Sem firewall de rede, um atacante na mesma LAN pode tentar sobrecarregar o serviço; limite conexões e evite redes públicas.
- **Android 17**: como `targetSdk=36`, não usa a permissão de rede local introduzida para apps que miram SDK 37. Essa decisão exigirá revisão na atualização de target.

## Compilação

Requer Android SDK API 36, Java 17, Gradle 8.13. O repositório ainda não inclui wrapper binário. Execute no Android Studio ou terminal com Gradle instalado:

```bash
gradle :app:testDebugUnitTest :app:assembleDebug
```

APK local de teste: `app/build/outputs/apk/debug/app-debug.apk`. O workflow copia esse arquivo ou o Release assinado como **`MonitorRemoto-v0.1.0-alpha.14.apk`**. Baixe diretamente na aba GitHub **Releases**. **O APK não está incluído neste ZIP de código-fonte.** Veja `docs/PAIRING_QR.md` para segurança do QR.

Opcional: configure os quatro Secrets de assinatura para gerar APK Release assinado e AAB. Ver `docs/APK_DOWNLOAD.md`. Preservar a mesma keystore entre builds é essencial para atualizações sem reinstalação.

## Privacidade e robustez (alpha.6)

- Ao desligar o monitoramento ou reativar uma nova sessão, o buffer de notificações na memória é apagado. Ao retirar um aplicativo da lista permitida, seus eventos previamente coletados são removidos. Antes de responder ao Controle, a lista atual é verificada novamente para evitar a transmissão de eventos de permissões revogadas.
- O servidor limita a 3 o número de requisições simultâneas e a 12 a fila de conexões pendentes; conexões excedentes são fechadas sem fila ilimitada. Isso reduz o risco de consumo de recursos na rede local, mas não substitui um firewall nem testes de estresse.

## Interface Controle (alpha.11)

- Visual claro por padrão, com opções em **⚙ Configurações > Aparência**: claro, escuro e seguir sistema; escolha guardada apenas neste aparelho.
- Tela inicial agora abre direto no **Painel Controle**, sem abas fixas. O modo **Monitorado** fica acessível pelo menu **☰**, mantendo as duas funções no mesmo APK com menos poluição visual.
- Cabeçalho do Controle com botões **Conectar**, **☰** e **⚙**; chips de versão e estado do monitorado; atualização manual por **↻** e polling automático a cada 15 s.
- Pop-up **Conectar aparelho** reorganizado em blocos visíveis: **1. Endereço do aparelho**, **2. Chave de pareamento**, **3. Parear pelo QR Code** e **Aparelhos recentes**. Os campos receberam fundo destacado, borda, padding maior e ações **Colar/Limpar**.
- Até cinco aparelhos recentes com renomear/esquecer. Cada chave é protegida individualmente pelo Android Keystore, com migração da credencial antiga sem novo pareamento. O histórico registra somente nome/IP e último sucesso, **não** mensagens.
- A informação "Conectado" representa a **última consulta autenticada** e não um socket persistentemente conectado; em falha, status troca para "Sem resposta" e dados anteriores permanecem rotulados.
- QR continua com Camera2 (alpha.9); teste físico e compilação completa ainda pendentes.

## Próximas evoluções

Descoberta de dispositivos na LAN, reconexão ao mudar IP, permissões por categoria/app, bloqueio mais robusto de OTP, persistência local opcional criptografada, atualização em segundo plano no Controlador via canal push e testes físicos em ROMs diferentes.

## Estado de validação

Vide `docs/VALIDATION.md`. A presença do código não equivale a funcionalidade comprovada em hardware: executar build CI e ensaios com aparelhos reais antes de depender deste app.


## Alterações alpha.4 (versionCode 4)
- QR Code local com segredo temporário (2 minutos, uso único), confirmação antes da conexão e armazenamento protegido da chave permanente.
- Leitor nativo Camera2 com ZXing Core para decodificação offline e permissões de câmera apenas no momento da leitura. Suporta os mesmos dois modos com um APK.
- Servidor confirma bind da porta antes de indicar início do serviço.
- GitHub Actions evita duplicidade de APK entre Artifact e Release bem-sucedida; cria Artifact reserva se a Release falhar ou for ignorada.
- Alerta explícito: Play Protect classificou APK anterior como nocivo; QR não altera a classificação, e precisa de investigação/contestação. Não desativar Play Protect.

## Alterações alpha.2
- Corrigido GitHub Actions: `setup-android@v4` não solicita o pacote descontinuado `tools`.
- Corrigido erro no campo de texto da interface (`setTextColor(ink)`).
- Removido recebimento direto de SMS e o receiver para preparar um possível envio à Play.
- Notificações somente de aplicativos escolhidos pelo usuário, com aviso de consentimento e bloqueio conservador de apps financeiros/conteúdo sensível.
- Seleção de apps com `<queries>` de launcher, sem permissão `QUERY_ALL_PACKAGES`.
- `versionCode` 2; nome do artifact sincronizado; documentação de Google Play em `docs/PLAY_STORE.md`.

## Alterações alpha.3 (versionCode 3)
- Arquivo APK publicado diretamente em GitHub Releases com nome `MonitorRemoto-v0.1.0-alpha.3.apk`; Artifacts continuam gerando ZIP como backup.
- Assinatura Release opcional com quatro Secrets e geração do AAB para Google Play.
- Verificação de sincronização da versão e empacotamento, SHA-256 do binário, docs e instruções permanentes em `AGENTS.md`.
- Mantidas funcionalidades e filtros de segurança da alpha.2. Build completo depende do GitHub Actions.

### Correção da alpha.5

A execução GitHub Actions #4 falhou na compilação do leitor QR. A chamada ao ZXing foi corrigida para a constante `IntentIntegrator.QR_CODE`, mantendo pareamento temporário e configuração manual. Faça novo build pela ação **Gerar APK e Release**, baixando o APK diretamente em **Releases > Assets** após sucesso.

## Correções alpha.7 (build 6 e leitura de QR)

- Build #6 compilou o Kotlin principal, mas falhou em três testes `EventJournalTest` que acionavam os métodos `org.json` do `android.jar` mockado na JVM. Agora o teste usa `EventJournal.latest()` e testa a lógica de filtro sem serialização Android. `latestJson()` permanece disponível para transmissão real.
- Scanner alpha.9 agora usa **Camera2 nativo**, sem `CaptureActivity`, `IntentIntegrator` ou bibliotecas de câmera ZXing Embedded. O ZXing Core (3.5.4) fica exclusivamente na codificação/decodificação local. O botão **Inserir código QR manualmente** permite continuar se a câmera não funcionar.
- O fechamento do leitor informado em aparelho físico ainda **não possui stacktrace Android/logcat**; não se pode confirmar que a causa original foi eliminada sem nova instalação e teste. Caso persista, capturar `adb logcat -b crash -d` ou logcat com `FATAL EXCEPTION` e `MonitorRemotoQR`.
- Secrets de assinatura não acompanham o projeto nem devem ser adicionados ao GitHub. Para atualizar sem reinstalar é necessário **usar sempre o mesmo certificado** e manter o `applicationId`. APK Debug antigo e nova chave Release possuem assinaturas diferentes: talvez seja necessária uma única reinstalação ao migrar, com perda de dados locais sem backup.

## Correção alpha.8 (workflow #7)

- O log do GitHub Actions da alpha.7 falhou em `:app:compileDebugKotlin` porque `MainActivity.kt` declarava **duas vezes** `onRequestPermissionsResult` (linhas 422 e 580), com a mesma assinatura; o Kotlin relatou `Conflicting overloads`.
- Agora existe **um único callback** para os dois pedidos separados: `CAMERA` (abre o leitor QR quando concedida) e `POST_NOTIFICATIONS` (inicia o monitoramento quando concedida). Nenhuma das duas autorizações habilita a outra e a recusa continua tratada.
- O Artifact APK de contingência só é solicitado após o passo de nomear APK concluir com sucesso. Falhas de compilação não geram tentativa inútil de upload de arquivo inexistente.
- O build de GitHub Actions e o teste físico de QR da alpha.8 ainda precisam ser confirmados. A mensagem anterior sobre possível encerramento do QR não foi diagnosticada por logcat.
- Mantenha os mesmos quatro Secrets de assinatura da alpha.7. **Não gere outra keystore**; isso comprometeria a capacidade de atualizar APKs já assinados.

## alpha.9: nova abordagem para o fechamento do leitor QR

- A alpha.8 ainda abria a câmera usando a Activity do ZXing Embedded; as mudanças de manifesto e permissões não resolveram o encerramento relatado. Sem logcat do celular, a causa original específica **não foi comprovada**.
- A alpha.9 substitui integralmente essa tela por `QrScanActivity` com **Android Camera2** e `ImageReader YUV_420_888`, pré-visualização via `TextureView` e leitura ZXing Core em memória, sem Play Services/internet.
- Exibe estado e erros da câmera na própria tela (em vez de encerrá-la deliberadamente), com logs `MonitorRemotoQR` sem incluir o conteúdo confidencial do QR. Permissão continua sob autorização explícita.
- Em **Controle > Inserir código QR manualmente**, é possível digitar/colar o URI temporário se um leitor externo obtiver o texto; o QR temporário deve ser mantido privado e só vale pelo prazo curto.
- Nenhuma foto ou quadro é salvo/transmitido. O limite de leitura é de ~2,5 tentativas por segundo, economizando CPU em relação à decodificação de todos os frames.
- Necessário confirmar build Android `:app:testDebugUnitTest :app:assembleDebug`, leitura física entre dois aparelhos, orientação/câmera de fabricantes diferentes e upgrade APK assinado. Não afirmar correção comprovada sem esses testes.

## Revisão visual de diálogos e painel (alpha.14)

A tela Monitorado apresenta o estado real do serviço e IP local. Ativar, gerar QR e interromper são ações contextuais; notificações, segurança, inicialização e diagnóstico agora ficam em ⚙ Configurações.
- Menu, Configurações, Aparência, Aparelhos recentes, Diagnóstico e demais diálogos receberam cartões com subtítulo, melhor hierarquia visual e menos sensação de lista crua.
- O cartão principal do Painel Controle foi reorganizado para priorizar nome do aparelho, estado, ação principal e atualização manual, reduzindo textos duplicados.

