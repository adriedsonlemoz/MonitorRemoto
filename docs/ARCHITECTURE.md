# Arquitetura (0.1.0-alpha.49)

```text
MONITORADO                                      CONTROLE
NotificationListenerService --> EventJournal --\
Apps autorizados/allowlist -> EventJournal ----> MonitorServer :37841
BatteryReader ---------------------------------/       |
                    SecretVault (Android Keystore)     | AES-256-GCM bidirecional
                                                      |
LAN: 192.168.x.x / VPN Tailscale: 100.x.x.x     RemoteClient -> MainActivity
                                                    (poll 15 s)
```

**Fluxo:** cliente envia `{"v":1,"op":"snapshot","ts":epochMillis}` cifrado num envelope `{n,c}` com nonce 96-bit aleatório. Servidor valida tamanho, autenticação AEAD, versão, operação, tempo ±5 minutos e rejeita nonce já recebido no processo. Servidor responde o snapshot atual com os 30 últimos eventos cifrado pela mesma chave. A alpha.45 inclui operações `dataList` e `dataChunk` de leitura exclusivamente da árvore SAF autorizada no monitorado; não existe leitura arbitrária do sistema de arquivos.

O FGS `connectedDevice` mantém o socket de escuta durante monitoramento ativo. A permissão `CHANGE_NETWORK_STATE` é declarada para o pré-requisito do tipo FGS; o app não altera configurações de rede. O serviço não depende de localização, Accessibility, root, APIs ocultas ou desbloqueio da tela para consultar as APIs permitidas.

**Limites:** TCP não faz NAT traversal; internet depende da VPN, rede de dispositivos ou infraestrutura futura. O monitor não envia push para o controlador; este consulta quando aberto. OS/fabricantes podem encerrar/restringir o serviço. Testes reais em Android 13–17 são pendentes. Esquemas de sincronização e políticas granulares ainda não estão implementados.

## Modo de vídeo alpha.43

Ver tela começa em JPEG por responsividade. H.264 depende de seleção explícita: a captura usa MediaProjection + ImageReader e converte RGBA→YUV na CPU antes do MediaCodec; a decodificação AVC reconstrói Bitmap na CPU. A codificação AVC só permanece ativa enquanto houver consultas autenticadas `videoFrame` recentes (janela de 2 s), evitando degradar o JPEG por CPU e intervalo de captura maior. A troca de quadros passa por consultas TCP/AES-GCM individuais (não streaming persistente), razão pela qual H.264 pode ser mais lento que JPEG em aparelhos específicos. Ao exceder 5 quadros de distância, não envia sequencialmente quadros ultrapassados; espera ou pede IDR recente. Necessários dados reais de tempo de captura/codificação/rede/decodificação/desenho para otimização estrutural.

## Compartilhamento de tela (alpha.34)

`MainActivity` no Monitorado solicita `MediaProjectionManager.createScreenCaptureIntent()` e inicia `ScreenShareService` (foregroundServiceType `mediaProjection`) **somente** após autorização na interface do Android. Um `MediaProjection` novo corresponde a exatamente uma chamada `createVirtualDisplay`. Listener `onStop` limpa quadro e encerra serviço; `onCapturedContentResize` redimensiona a superfície sem criar outra projeção. Nenhum receiver reinicia o ScreenShareService em segundo plano.

A captura usa `ImageReader` RGBA, `Bitmap` JPEG com largura máxima 540 px, taxa 1 fps, até 110 KiB/frame e sem persistência. `ScreenFrameStore` mantém somente o quadro atual em RAM com validade de 3 s. Solicitações `screenFrame` são autenticadas via AES-GCM (versão exata, timestamp, nonce contra replay) na mesma porta TCP. O controlador decodifica e mostra na `RemoteScreenActivity` enquanto visível; nunca salva ou grava. **Não confundir 1 fps com vídeo fluido.** O Android protege conteúdo `FLAG_SECURE` e exige consentimento renovado após parada; o monitor não contorna.

## Distribuição e assinatura

O mesmo APK atende aos modos Monitorado e Controle. A fonte de versionamento é `app/build.gradle.kts`, com `versionName=0.1.0-alpha.49` e `versionCode=49`.

- GitHub Actions compila e testa; copia o `.apk` compilado para `dist/MonitorRemoto-v0.1.0-alpha.49.apk`.
- `upload-artifact` sempre distribui um ZIP; GitHub Releases recebe o **arquivo APK direto**.
- Com quatro Secrets, Gradle gera APK Release assinado e AAB. Sem Secrets, o arquivo é Debug de teste e não preserva necessariamente a assinatura entre execuções.
- Releases são prévias, não substituem a futura revisão da Google Play e nem os ensaios reais.


## Pareamento local por QR (alpha.4)

`PairingCode` valida URLs exclusivamente `monitorremoto://pair` e IPs privados, `PairingSession` guarda código temporário (uso único) por 120 segundos em memória. `MonitorServer` recebe pedidos cifrados com segredo temporário e devolve a chave permanente em resposta AES-GCM; `RemoteClient.pair()` autentica resposta antes de salvar no Android Keystore. Ver `PAIRING_QR.md`.

## Scanner QR alpha.7

`MainActivity` verifica a permissão `CAMERA` antes de iniciar a `QrScanActivity`, Activity explícita não exportada com o tema de captura do ZXing. O scanner não é um serviço persistente, nem transfere imagem para rede; recebe somente o conteúdo textual do QR. A tela de controle salva o modo na recriação pela Activity.

## Permissões independentes (alpha.8)

`MainActivity` usa um único override de `onRequestPermissionsResult`. Os códigos `cameraPermissionRequest` e `notificationPermissionRequest` seguem separados no `when`. Concessão da câmera inicia somente a captura QR; concessão de `POST_NOTIFICATIONS` inicia apenas o serviço solicitado pelo usuário. Negação de qualquer autorização não ativa o outro caminho.

## Scanner QR alpha.9

`MainActivity` pede CAMERA e abre `QrScanActivity` não exportada; Camera2 entrega frames YUV na memória, `QrFrameDecoder` usa somente ZXing Core 3.5.4 e devolve o texto ao `MainActivity` para validação de `PairingCode` e confirmação do usuário. Camera2 é fechada no `onPause()`. Leitor não depende de Google Play Services nem de Activity de terceiros. Texto do QR pode ser inserido manualmente como fallback, sempre sujeito às validações anteriores.

## Interface e lista de aparelhos alpha.11

`ui/MainActivity.kt` mantém Views Android sem bibliotecas adicionais. Tema claro é padrão; modo do tema fica em `SettingsStore` e é aplicado por `Activity.setTheme` antes de `onCreate`, com barras do sistema compatíveis. `core/Ipv4Suggestion.kt` extrai somente octetos completos cobertos pelo prefixo CIDR; apenas interface Wi-Fi ativa é usada para sugerir endereços e nunca há descoberta de hosts pela rede.

`core/RecentDevices.kt` armazena até cinco tuplas locais `(host, apelido, último sucesso)` em SharedPreferences, sem eventos sensíveis. O segredo individual é cifrado pelo Android Keystore em `SecretVault.profileKeyName(host)` com SHA-256 do host normalizado. Ao abrir a alpha.11, uma única vez é feita a migração local do `CONTROLLER_KEY` legado/host salvo, sem reemitir segredos. Um novo host **não** herda automaticamente a chave do anterior. Esquecer um aparelho apaga sua credencial cifrada; o protocolo WireProtocol e o esquema de chave de monitorado não mudam.


Na alpha.11, `MainActivity` passa a abrir diretamente no Painel Controle e move o modo Monitorado para um menu (`☰`), reduzindo o tamanho da tela principal sem remover a funcionalidade. O diálogo Conectar foi repartido em blocos visuais com campos mais destacados e ações explícitas para IP, chave e QR.


Na alpha.13, o Painel Controle deixa o botão Conectar no próprio cartão do dispositivo e transforma a ação em contextual (Conectar/Desconectar). Os diálogos de Menu e Configurações também passam a usar linhas em cartão com subtítulo para dar mais polimento e organização.


Na alpha.14, os pop-ups internos passam a seguir um padrão visual comum com cartões e subtítulos, enquanto o Painel Controle reorganiza o cartão principal para reduzir textos repetidos e reforçar a ação principal de conexão.

Na alpha.15, a interface separa escolha de conexão e formulário manual. Aparelhos salvos têm ações diretas; scanner Camera2 opera como janela de Activity em tema Dialog, mantendo o protocolo e a confirmação antes do pareamento.

Na alpha.16, a tela inicial volta a ser Monitorado, com retorno por Home. Controle só é aberto após conectar a outro celular pela seção de recentes ou escolha de conexão. Informações manuais são exibidas em diálogo protegido por `FLAG_SECURE`, com segredo oculto inicialmente, confirmação ao compartilhar e IP de rede lido a cada abertura. Nenhuma alteração no transporte criptografado.

## Fluxo de navegação alpha.17

`MainActivity.mode` tem três estados: `2` Home (padrão), `0` Monitorado e `1` Controle. Salvar/restaurar a escolha na recriação não altera o estado do serviço foreground. Home oferece escolha de conexão, até cinco pares locais e entrada para o modo Monitorado. O histórico de conexão vem exclusivamente de `RecentDevices.lastSuccessMs`, persistido após sucesso autenticado, sem inventar conexões e sem ler segredos para exibição. Engrenagem do perfil invoca renomeação/revogação no armazenamento existente; Voltar/Home troca apenas a interface. O Monitorado reúne no cartão status, IP privado atual e controles curtos empilhados à direita; chaves seguem protegidas no diálogo seguro já existente.


## Acessos recebidos na alpha.18

`RemoteClient.snapshot()` transmite metadados não secretos (`clientId` aleatório local, nome Android informado e método informado) dentro do envelope AES-GCM autenticado já existente. `MonitorServer` aceita campos opcionais para compatibilidade com alpha.17 e versões anteriores. Após resposta autenticada enviada, `AuthenticatedAccess` registra no processo somente IP de origem, valores declarados e instante do acesso; até 12 entradas, tempo de vida 45 segundos. Atualiza UI local por callbacks em memória, sem notificação externa ou servidor push.

Autenticação prova **posse da chave compartilhada**, não identidade do dispositivo. Portanto nome e método são exibidos como *informados pelo cliente*, não certificados; controladores antigos não os transmitem. Não há conexão permanente nem encerramento seletivo de cliente sem redesenhar credenciais por controlador. O botão Ações permite ver detalhes, parar serviço ou trocar a chave global com confirmação. Registro volátil é apagado ao parar serviço ou trocar chave.

## Compatibilidade e interrupção de acesso (alpha.20)

- `transport/AppVersion.kt` é a versão exata do aplicativo; permanece distinta de `WireProtocol.VERSION`, que identifica o formato básico da mensagem. `tools/check_distribution.py` verifica alinhamento com Gradle.
- O conteúdo QR inclui `app=<versão>`; o pareamento temporário também envia `appVersion` cifrado e exige resposta com versão correspondente. QR antigo não é aceito por design.
- Snapshots incluem `appVersion` em requisição e resposta. O servidor recusa pedidos de versões distintas antes de transmitir dados ou registrar o acesso; o cliente recusa respostas de versão ausente ou diferente.
- O pedido autenticado `op=disconnect` usa a chave por host, nonce e timestamp do protocolo, indica que o controlador encerrou a consulta e remove sua entrada do registro volátil. Não finaliza o serviço local. Se a rede cair, expiração de 45s continua como fallback.
- `IncomingAccessActivity` representa **consultas recentes autenticadas**, sem conexão TCP permanente. É acionada pela `MainActivity` quando foreground, incluindo diálogo de Configurações; retorna ao modo Monitorado depois que o registro desaparece. Fora do aplicativo, o `MonitorService` publica notificação padrão com conteúdo não sensível e sem disparar Activity por trás das regras do Android.
- A parada explícita (inclusive no aviso) persiste `monitorEnabled=false` e impede tentativas automáticas de reativação. A recuperação conservadora só ocorre ao trazer o app ao primeiro plano quando ainda está autorizado.
- `NetworkReader` informa tipo de transporte da rede ativa do **aparelho monitorado**. SSID e mesma rede Wi-Fi não são inferidos. Os endereços e rotas da VPN não são comprovação de identidade.

Na alpha.21 foi corrigida a resolução de `background` no escopo de `ScrollView.apply` da tela `IncomingAccessActivity`, com receptor explícito da propriedade `Int` definida na Activity.

## Extração de interfaces alpha.23

```text
MainActivity.kt              Activity / eventos / navegação / pareamento
 ├─ UiComponents.kt          componentes e tema em extensões internas
 ├─ HomeScreenUi.kt          aparelhos recentes e acesso ao monitorado
 ├─ MonitorScreenUi.kt       serviço local e acessos autenticados recentes
 ├─ SettingsScreenUi.kt      opções, diagnósticos e configurações
 └─ RemoteDashboardUi.kt     cartões remotos, eventos e detalhes
```

Os módulos de interface utilizam funções de extensão internas de `MainActivity` para
preservar a interface existente nesta primeira etapa. Essa estratégia é transitória:
uma refatoração posterior poderá separar estado e efeitos de apresentação em
controladores dedicados, após build Android e testes físicos. Não há novos endpoints
no `MonitorServer`, nem alteração de permissões, chaves ou autorização.


## Ajuste alpha.23

A tela dedicada de acesso recebido passou a delegar sua composição visual para `IncomingAccessScreenUi.kt`, deixando `IncomingAccessActivity.kt` responsável apenas pelo ciclo de vida, polling, observação dos acessos autenticados e ação de parada do monitoramento.


## Extração de estado e pareamento alpha.24

```text
MainActivity.kt                  Ciclo de vida, navegação e permissões (322 linhas)
 ├─ RemoteConnectionManager.kt Estado, cache, invalidação e I/O remoto em background
 ├─ ConnectionController.kt    Liga callbacks do manager à interface
 ├─ ConnectionScreenUi.kt      Cabeçalho, estado, IP e controles visuais
 ├─ PairingController.kt       Pareamento QR em worker e retorno à thread principal
 ├─ PairingScreenUi.kt         Seleção, manual, renomear e esquecer dispositivos
 ├─ PairingQrUi.kt             Scanner/gerador de QR e confirmação do pareamento
 ├─ MonitorCredentialsUi.kt    Janela protegida de IP/chave local
 └─ SettingsScreenUi.kt        Configurações, seleção de notificações e Sobre
```

O estado de conexão é propriedade de `RemoteConnectionManager` e sua geração lógica
invalida respostas pendentes quando se troca de dispositivo. A lógica de autenticação,
comunicação AES-GCM e credenciais por host continua em `transport/` e `security/`.
A Activity ainda recebe callbacks de permissões e de captura QR; novos módulos
visuais são extensões internas transitórias, para reduzir o risco da migração.

## Correção alpha.25

No helper `IncomingAccessActivity.infoMetric` em `IncomingAccessScreenUi.kt`, os construtores de Views dentro de `LinearLayout.apply` usam `this@infoMetric` como `Context`. Isto evita capturar implicitamente o receiver `LinearLayout` em vez da Activity. Nenhum ajuste de arquitetura ou protocolo foi necessário.


## Alpha.26: detalhes remotos e privacidade da linha

`MonitorServer` agrega `BatteryReader`, `NetworkReader`, `DeviceInfoReader` e `TelephonyReader` em snapshot autenticado. A nova tela `RemoteDetailsActivity` recebe exclusivamente o último snapshot autenticado do controlador, sem rede própria nem consulta local às informações de outro dispositivo.

A leitura do número da linha está desligada por padrão e requer escolha explícita **e** permissão `READ_PHONE_NUMBERS` no aparelho monitorado. Se o usuário desligar a opção, o número não é mais incluído em snapshots futuros. A tela de detalhes apresenta dados não retornados como indisponíveis.

O botão de recuperar dados não implementa transferência, acesso a arquivos ou bypass de bloqueio de tela.


## Identidade visual alpha.27

`UiComponents.kt` centraliza `iconTile`, `iconMenuRow`, `iconSectionHeader`, `iconMetric`, `twoColumnTiles` e `headerIconButton`. A Home, o Monitorado, os diálogos de Configurações e os fluxos QR/manual reutilizam esses componentes. A Activity mantém sua responsabilidade anterior de navegação, sem permissões novas nem modificação no protocolo.

## Interface de avisos e diagnósticos alpha.28

`AppDialogUi.kt` centraliza uma única janela visível na Activity. `ConnectionIssue.kt` categoriza erros locais sem inferir certeza a partir de EOF. `ConnectionErrorUi.kt` mostra o diagnóstico sem a chave de pareamento. Uma mensagem `versionMismatch` só é enviada pelo monitorado depois da autenticação AES-GCM, do controle de timestamp e de replay; versões antigas podem não emitir essa resposta.

## Alpha.30: canal de mensagens autorizado

`NotificationCaptureService` registra, apenas quando permitido, ações `RemoteInput` no buffer volátil `NotificationReplyActions` (máximo 60, validade 10 minutos, token único). O `EventJournal` só expõe o token quando o consentimento ainda está ativo. `SmsInboxReader` consulta a inbox apenas mediante `includeSms=true` na consulta manual de Mensagens, nunca no polling de 15 segundos, com READ_SMS e sem persistência, bloqueando mensagens com sinais de autenticação. `SmsReplySender` exige READ_SMS, SEND_SMS, opt-ins do monitorado, destinatário recebido anteriormente e limite de taxa. `MonitorServer` aceita `notificationReply` e `smsReply` após os mesmos controles AES-GCM, timestamp, nonce e versão exata de `snapshot`; respostas são `commandResult`, sem alegar entrega final. `RemoteMessagesUi.kt` usa confirmação explícita e `RemoteConnectionManager` invalida respostas de aparelhos trocados.

## Mensagens alpha.31

- `NotificationCaptureService` envia eventos filtrados à RAM `EventJournal`. Lista explícita de apps ou opção ampla opt-in, filtros OTP/financeiros permanecem. Respostas via `RemoteInput` somente com token atual e consentimento.
- `SmsInboxReader` consulta `Telephony.Sms.CONTENT_URI` sob demanda, limitando SMS recebidos/enviados e eliminando códigos suspeitos. `SmsReceivedReceiver` recebe `SMS_RECEIVED` somente se `RECEIVE_SMS` e opção local ativos; mantém até 20 entradas RAM, lidas na próxima atualização de Mensagens.
- `SmsReplySender` exige destinatário conhecido, SEND_SMS, consentimento, limite de tamanho e intervalo. `SmsSubscriptions` valida SIM se o usuário autorizar READ_PHONE_STATE. `SmsStatusReceiver` e `SmsDeliveryJournal` acompanham callbacks de envio/entrega em RAM, sem preservar texto.
- `MmsMetadataReader` consulta somente assunto/data quando o provider permitir, sem downloads de anexos e sem papel de SMS padrão.
- Snapshots cifrados trazem `smsInbox`, `smsLive`, `smsStatus`, `mmsMetadata`, `smsSubscriptions` **somente** quando `includeSms` foi solicitado; eventos de notificações seguem a seleção local. `FLAG_SECURE` removido dos diálogos de mensagens, preservado em QR/credenciais.

## Recuperação do monitorado e Configurações alpha.32

`MonitorService` mantém o FGS autorizado e supervisiona `MonitorServer.isHealthy()` a cada 15 s, sem abrir socket cliente ou gastar tráfego. `isHealthy` exige `ServerSocket` aberto e thread de aceitação ativa. Em falha, o listener antigo é fechado, um novo é criado, e erros de bind provocam atrasos progressivos com máximo de cinco minutos. Retorno de rede aciona uma verificação adicional. Diagnóstico persistente registra **apenas metadados** (estado, falhas e horário) com limitação de escritas em intervalo normal. Não registra credenciais, IP de controlador nem conteúdo das mensagens. O `START_STICKY` permite recriação pelo Android após finalização involuntária; **não** garante retomada se usuário força parada nem diante de restrições de fabricantes.

`MainActivity` mantém `mode=3` para Configurações e `settingsSection` (home, notifications, sms, service, diagnostics, security, appearance, about). `SettingsScreenUi.kt` monta telas completas e mantém diálogos curtos somente para solicitações e confirmações sensíveis. Voltar retorna à categoria anterior, depois ao modo de onde Configurações foi aberto. Sem novos componentes exportados ou permissões.

## Tela Mensagens alpha.33

`MainActivity` mantém o estado efêmero em RAM (`mode=4`), e `RemoteMessagesUi.kt` renderiza uma página com abas e ações compactas, sem diálogo. O servidor não é consultado automaticamente no polling da home; o usuário abre/atualiza a tela sob demanda. A recuperação da conexão é autenticada antes da consulta. Saída descarta `JSONObject`, invalida callbacks e preserva apenas a marca temporal de leitura. Conteúdo não entra no estado salvo da Activity. `SensitiveContent` reconhece OTP em PT/EN; `SmsInboxReader`, `SmsLiveJournal`, `EventJournal.latest` e a UI rejeitam conteúdo elegível à filtragem em camadas. As mensagens que não puderem ser identificadas como OTP ainda representam risco residual de privacidade.

## Alpha.35: controle e áudio

`RemoteTouchService` é opt-in na Acessibilidade, sem leitura de nós, acionado exclusivamente por comandos AES-GCM validados (`remoteTouch`) enquanto `ScreenFrameStore.sharing` e a preferência local estão ativos. `AudioPlaybackCaptureConfiguration` usa a projeção aprovada e publica PCM mono 16 kHz apenas em `AudioChunkStore`, consumido pelo controlador via `audioChunk`. `requestScreen` só emite notificação local limitada (não inicia projeção). Os três opcodes mantêm validação de appVersion e anti-replay já existentes. Não reutilizar token MediaProjection em uma nova sessão.

## Captura de imagem voluntária alpha.36

O servidor aceita `screenCapture` somente após autenticação AES-GCM/nonce e com a MediaProjection já ativa. `ScreenFrameStore` registra por 2,7 segundos uma solicitação única; `ScreenShareService` gera JPEG até 1080 pixels de largura e 480 KiB, somente quando solicitado, sem gravação no monitorado. Cliente espera até 2,3 segundos, valida marcadores/tamanho de JPEG e salva via MediaStore exclusivamente no controlador após gesto explícito. O botão Print usa quadro de prévia com idade máxima de 3 segundos. A visualização em tela cheia reorganiza o mesmo ImageView mantendo FIT_CENTER e a política existente de mapeamento de gestos.

## Vídeo alpha.40 (experimental)

Projeção única consentida por sessão no monitorado usa `ImageReader` RGBA. Quando há visualizador autenticado, os quadros são opcionalmente redimensionados e codificados H.264/AVC por `MediaCodec` usando entrada YUV flexível. Um ring limitado em RAM protege contra crescimento indefinido. O comando `videoFrame` recupera amostras ordenadas via AES-GCM com checagem de nonce, versão e chave; o controlador usa decoder `MediaCodec` ou retorna a JPEG. Não há socket persistente, logo 10 FPS são **alvo**, não medição. Acordar a tela é comando independente da projeção, com preferência local OFF, não desbloqueia o Keyguard e pode ser barrado pelo sistema.

## Interface imersiva alpha.41

A `RemoteScreenActivity` mantém o vídeo como camada de fundo do `FrameLayout` e desenha barras de ação com fundo translúcido na parte superior/inferior, visíveis somente no modo fullscreen. Os botões usam a mesma API remota e autorização local; não enviam comandos pela simples visualização. Os toques no `ImageView` seguem `RemoteGesturePolicy.fitCenterPoint/centerCropPoint`, respeitando recortes e barras que interceptam apenas toques diretamente sobre seus próprios botões. Exibir estado H.264/JPEG e tempo de rede somente quando recebidos do servidor.

## ADB alpha.42
`adb-core` contém protocolo ADB/TLS/SPAKE2 de libadb; `adb/AdbClient` limita operações; `AdbIdentity` protege identidade persistente; `AdbDiscovery` resolve mDNS em série; `AdbPolicy` valida endpoints/QR e comandos; `AdbKnownDevices` guarda localmente o histórico mínimo de aparelhos já conectados; `ui/AdbActivity` controla sessão foreground, reconexão rápida, cartões de aparelhos salvos e invalidação de callbacks. O servidor TCP/AES-GCM existente não encaminha comandos ADB. Ver ADB.md.

## Recuperar dados (alpha.45)

O monitorado usa `ACTION_OPEN_DOCUMENT_TREE` para o usuário selecionar diretamente a pasta autorizada. A permissão persistida de leitura é mantida até revogação; sem árvore ou permissão, o servidor rejeita pedidos mesmo de clientes com chave válida. `SharedDataFolder` reenumera os filhos a cada componente do caminho relativo, recusa `..` e separadores, limita 12 níveis/150 nomes e não aceita IDs de documentos externos enviados pelo controlador. O servidor resolve metadados, limita leitura a 32 MiB por arquivo e transmite blocos de até 48 KiB no envelope AES-GCM habitual. O controlador verifica hash SHA-256 dos blocos, tamanho e alteração de tamanho/data da origem, exige `ACTION_CREATE_DOCUMENT` para salvar a cópia e tenta descartar o documento parcial em caso de falha. A escolha de pastas privadas e a recuperação de arquivos apagados são limitações do Android/SAF e não estão implementadas.
