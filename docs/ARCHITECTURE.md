# Arquitetura (0.1.0-alpha.74)

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

Na alpha.57, `RemoteFilesActivity` oferece filtros, ordenação e seleção em memória dos metadados `dataList`. A cópia múltipla **não amplia a API do servidor**: usa sequencialmente `dataChunk` autenticado, com metadados originais imutáveis para cada arquivo, e escreve somente no controlador, em destino SAF escolhido via `ACTION_OPEN_DOCUMENT_TREE`. Limites locais: 20 arquivos, 128 MiB/lote e 32 MiB/arquivo; não sobrescrever nomes existentes e tentar remover parciais. Origem permanece somente leitura, sem nova permissão Android nem operação destrutiva.

O FGS `connectedDevice` mantém o socket de escuta durante monitoramento ativo. A permissão `CHANGE_NETWORK_STATE` é declarada para o pré-requisito do tipo FGS; o app não altera configurações de rede. O serviço não depende de localização, Accessibility, root, APIs ocultas ou desbloqueio da tela para consultar as APIs permitidas.

**Limites:** TCP não faz NAT traversal; internet depende da VPN, rede de dispositivos ou infraestrutura futura. O monitor não envia push para o controlador; este consulta quando aberto. OS/fabricantes podem encerrar/restringir o serviço. Testes reais em Android 13–17 são pendentes. Esquemas de sincronização e políticas granulares ainda não estão implementados.

## Modo de vídeo alpha.43

Ver tela começa em JPEG por responsividade. H.264 depende de seleção explícita: a captura usa MediaProjection + ImageReader e converte RGBA→YUV na CPU antes do MediaCodec; a decodificação AVC reconstrói Bitmap na CPU. A codificação AVC só permanece ativa enquanto houver consultas autenticadas `videoFrame` recentes (janela de 2 s), evitando degradar o JPEG por CPU e intervalo de captura maior. A troca de quadros passa por consultas TCP/AES-GCM individuais (não streaming persistente), razão pela qual H.264 pode ser mais lento que JPEG em aparelhos específicos. Ao exceder 5 quadros de distância, não envia sequencialmente quadros ultrapassados; espera ou pede IDR recente. Necessários dados reais de tempo de captura/codificação/rede/decodificação/desenho para otimização estrutural.

## Compartilhamento de tela (alpha.34)

`MainActivity` no Monitorado solicita `MediaProjectionManager.createScreenCaptureIntent()` e inicia `ScreenShareService` (foregroundServiceType `mediaProjection`) **somente** após autorização na interface do Android. Um `MediaProjection` novo corresponde a exatamente uma chamada `createVirtualDisplay`. Listener `onStop` limpa quadro e encerra serviço; `onCapturedContentResize` redimensiona a superfície sem criar outra projeção. Nenhum receiver reinicia o ScreenShareService em segundo plano.

A captura usa `ImageReader` RGBA, `Bitmap` JPEG com largura máxima 540 px, taxa 1 fps, até 110 KiB/frame e sem persistência. `ScreenFrameStore` mantém somente o quadro atual em RAM com validade de 3 s. Solicitações `screenFrame` são autenticadas via AES-GCM (versão exata, timestamp, nonce contra replay) na mesma porta TCP. O controlador decodifica e mostra na `RemoteScreenActivity` enquanto visível; nunca salva ou grava. **Não confundir 1 fps com vídeo fluido.** O Android protege conteúdo `FLAG_SECURE` e exige consentimento renovado após parada; o monitor não contorna.

## Distribuição e assinatura

O mesmo APK atende aos modos Monitorado e Controle. A fonte de versionamento é `app/build.gradle.kts`, com `versionName=0.1.0-alpha.74` e `versionCode=74`.

- GitHub Actions compila e testa; copia o `.apk` compilado para `dist/MonitorRemoto-v0.1.0-alpha.74.apk`.
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

## Sessão e reconexão ADB alpha.51

`AdbActivity` acompanha uma sessão ADB consentida com heartbeat de 30 s (`echo monitor_remoto_alive`) e limite de 8 s. Após queda detectada, `AdbReconnectPolicy` agenda 2/4/8/15/30 s, reaproveita IP/porta guardados e procura `_adb-tls-connect` apenas no mesmo IPv4 privado; não varre IPs, não aceita serviços de pareamento para reconectar e não tenta habilitar depuração. A recuperação roda somente com a tela ADB visível e permite cancelamento explícito; `onStop()` encerra o canal para não manter acesso oculto. O fluxo de vídeo do app continua separado.

## ADB alpha.42
`adb-core` contém protocolo ADB/TLS/SPAKE2 de libadb; `adb/AdbClient` limita operações; `AdbIdentity` protege identidade persistente; `AdbDiscovery` resolve mDNS em série; `AdbPolicy` valida endpoints/QR e comandos; `AdbKnownDevices` guarda localmente o histórico mínimo de aparelhos já conectados; `ui/AdbActivity` controla sessão foreground, reconexão rápida, cartões de aparelhos salvos e invalidação de callbacks. O servidor TCP/AES-GCM existente não encaminha comandos ADB. Ver ADB.md.

## Recuperar dados (alpha.45)

O monitorado usa `ACTION_OPEN_DOCUMENT_TREE` para o usuário selecionar diretamente a pasta autorizada. A permissão persistida de leitura é mantida até revogação; sem árvore ou permissão, o servidor rejeita pedidos mesmo de clientes com chave válida. `SharedDataFolder` reenumera os filhos a cada componente do caminho relativo, recusa `..` e separadores, limita 12 níveis/150 nomes e não aceita IDs de documentos externos enviados pelo controlador. O servidor resolve metadados, limita leitura a 32 MiB por arquivo e transmite blocos de até 48 KiB no envelope AES-GCM habitual. O controlador verifica hash SHA-256 dos blocos, tamanho e alteração de tamanho/data da origem, exige `ACTION_CREATE_DOCUMENT` para salvar a cópia e tenta descartar o documento parcial em caso de falha. A escolha de pastas privadas e a recuperação de arquivos apagados são limitações do Android/SAF e não estão implementadas.

## UI ADB alpha.50 e descoberta automática

`AdbActivity` é uma tela principal compacta com diálogo de seleção de papel, método QR/código e controles opcionais. Depois que `AdbClient.pair()` retorna com sucesso, `AdbDiscovery` procura `_adb-tls-connect` no mesmo endereço obtido do serviço de pareamento; `AdbAutoConnectPolicy` ignora outros IPs e serviços de pareamento. Em caso de ambiguidade/erro, exige fallback manual. Reconexão consulta `AdbKnownDevices` e confirma divergências de modelo. As credenciais não são gravadas no histórico, e o ADB é encerrado ao sair da Activity. A identificação não é atestado criptográfico do dispositivo.

## Handoff ADB para o visualizador (alpha.52)

A `AdbActivity` pode passar a instância **já conectada** do `AdbClient` para a ponte volátil `AdbScreenBridge` somente após ação explícita **Ver tela remota • controle ADB**, confirmação da mesma origem IPv4 e presença de uma chave do protocolo normal em `RecentDevices`. Ao entrar em `RemoteScreenActivity`, a imagem continua vinda de `RemoteClient.screenFrame`/`videoFrame` com AES-GCM e MediaProjection autorizada; **ADB não é transporte de imagem**. `AdbScreenRouting` mapeia somente `back`, `home` e `recents` para comandos ADB permitidos, e a Activity exige que uma consulta da imagem tenha retornado autenticada antes de enviar esses comandos à ponte.

A sessão ADB é detida no processo, não em um serviço. Ao sair do visualizador, `onPause()` encerra a ponte/socket. Ao voltar para a tela ADB visível, a reconexão da alpha.51 pode retomar o dispositivo explicitamente escolhido. Se ADB cair durante a visualização, os comandos de navegação podem seguir por `RemoteClient.touch` quando a Acessibilidade no monitorado estiver autorizada. A reconexão automática da alpha.51 não é transportada para a ponte enquanto o visualizador está ativo; isso exigirá outra revisão de ciclo de vida. Sem acesso ADB livre, gravação de vídeo, captura sem MediaProjection nem novos privilégios.

## Catálogos remotos (alpha.58)

`RemoteCatalogActivity` abre somente após seleção de aparelho pareado e consulta o snapshot autenticado ou `appsList` AES-GCM. `NotificationCaptureService` sinaliza estado em `NotificationCaptureStatus` (RAM), reintegra apenas eventos ainda ativos após reconexão e mantém filtros de segurança. `InstalledAppsReader` retorna até 120 atividades de lançador (únicas por pacote), sujeitas à visibilidade de pacotes Android. `SettingsStore.shareInstalledApps()` é um opt-in local revogável; a resposta ao cliente é sempre autenticada e não altera aplicativos.

## Telemetria de bateria (alpha.59)

No monitorado, `BatteryReader.snapshot` lê o sticky broadcast `ACTION_BATTERY_CHANGED` no mesmo momento da solicitação `snapshot` e calcula percentual validado a partir de `EXTRA_LEVEL/EXTRA_SCALE`. O objeto `BatteryTelemetry` traduz `EXTRA_STATUS` (2 carregando, 3 descarregando, 4 não carregando, 5 cheia, demais desconhecido), e `EXTRA_PLUGGED` (1 AC, 2 USB, 4 sem fio, 8 dock) sem inventar fonte para códigos não reconhecidos. O retorno é um snapshot cifrado com a chave já pareada, sem listener contínuo, permissões adicionais nem aumento de frequência de polling. `state`, `statusCode`, `powerSource`, `pluggedCode`, `chargingIndicator` são campos novos; `charging`/`full` permanecem disponíveis.

O painel Controller apresenta texto secundário em seu cartão de bateria e um indicador ⚡ quando o Android confirmou carga (ou cheia ligada a fonte reconhecida). `NOT_CHARGING` não é equivalente a `DISCHARGING`. Dados em cache permanecem rotulados conforme estado de conexão. A Activity de detalhes mostra a alimentação informada pelo Android. Verificações com cabo USB, tomada, Qi, bateria cheia, aparelho descarregando e dados incompletos ainda dependem de testes físicos.


## Alpha.60: controles físicos autenticados

O `RemoteClient.deviceControl` envia somente `status`, `soundStart`, `soundStop`, `torchOn` ou `torchOff` pelo `WireProtocol` AES-GCM, com chave de pareamento, nonce, timestamp e exigência de `AppVersion` exata. A resposta contém `ok`, mensagem e `status`. `DeviceControlPolicy` fecha allowlist e tempos permitidos. `MonitorServer` revalida as duas autorizações locais do `SettingsStore` a cada comando; a consulta de status não altera hardware.

O `RemotePhysicalControls`, propriedade lógica do `MonitorService`, mantém o alarme ToneGenerator e o callback Camera2. O som para no limite escolhido, na revogação ou no encerramento do serviço. TorchCallback é a fonte de estado; `setTorchMode()` não confirma imediatamente que o flash mudou. O servidor não abre câmera nem transmite fotos, não altera volume e não tenta ignorar Não Perturbe. Controles novos ficam na página **Configurações > Som e lanterna**, padrão desativado. Build Android e testes físicos pendentes.

## Câmeras locais e JPEG remoto (alpha.62)

`CameraShareActivity` só abre Camera2 em foreground visível no monitorado com CAMERA autorizada; fecha ao sair. Prévia TextureView + ImageReader YUV_420_888 compartilham uma CameraCaptureSession. `CameraImageCodec` respeita os strides e gera JPEG de no máximo 110 KB a até um quadro/s, volátil por 3 segundos em `CameraFrameStore`. Um request autenticado AES-GCM `cameraFrame` no `MonitorServer` retorna estado e, se disponível, JPEG recente. `RemoteCameraActivity` só consulta enquanto visível, descarta respostas antigas e salva manualmente. Não inicia câmera via rede, nem FGS/background, nem H.264, áudio ou gravação.

### Orientação e geração da câmera (alpha.63)

- `CameraNv21Rotation.degrees` combina `CameraCharacteristics.SENSOR_ORIENTATION`, `Surface.ROTATION_*` e lente, sem usar orientação assumida pelo modelo. `rotate` transforma planos Y e pares VU do NV21 (0/90/180/270) antes de `YuvImage.compressToJpeg`.
- `CameraFrameStore.begin` devolve token por sessão. O callback valida geração e `publish(token, jpeg)` não aceita sessão obsoleta; o serviço revoga a memória em `onDestroy`, e o endpoint de leitura também valida `MonitorService.isRunning`.
- Ainda é necessário validar câmera e rotação reais e executar o build Android completo.

## Alpha.64: economia na câmera JPEG e telemetria da visualização

- `cameraFrame` permanece uma **leitura cifrada/autenticada**. O controlador manda `afterSequence`; o servidor responde com `sequence` e envia `jpeg` apenas se o quadro for diferente e ainda válido. Dados de `active/lens/frameAgeMs/sequence` são obtidos de uma única leitura sincronizada do store.
- Cada consulta válida e autorizada marca `viewerPing` no `CameraFrameStore`. Somente `CameraShareActivity` visível, com sessão Camera2 ativa e ping nos últimos 4,5 segundos, converte YUV em JPEG. `ImageReader` continua descartando imagens e fechando cada `Image`; Camera2 é encerrado em `onPause`. Sem pedidos, a conversão para, mas a prévia local ainda está ativa.
- Controlador mede tempo de requisição/resposta (inclui leitura de credencial + rede + processamento), bytes reais de JPEG e intervalo observado entre novos quadros. Esses valores **não** são latência ponta a ponta, FPS da câmera, nem estatísticas de rede desagregadas. Ao perder a Activity/expirar um quadro, limpa o preview.
- Comandos remotos de ativar e trocar câmera continuam inexistentes. A alternância entre frontal e traseira segue **somente local** no aparelho monitorado.

## Alpha.65: troca remota de lente limitada à sessão consentida

- `CameraShareActivity` é **a única entidade que pode abrir/reconfigurar Camera2**. O CheckBox de autorização, OFF por padrão e não persistido, chama `CameraFrameStore.setRemoteSwitchAllowed` na sessão ativa. Pausa/encerramento revogam autorização e pedidos.
- Endpoint cifrado `cameraSwitch` recebe somente `lens` da allowlist `Frontal`/`Traseira` e `sessionToken` obtido após `cameraFrame`. Não inicia câmera nem usa ADB. `CameraRemoteSwitchPolicy` rejeita ausência de consentimento, demanda expirada, token obsoleto, solicitação duplicada, lente inválida e frequência menor que 2 segundos.
- O store enfileira apenas uma mudança; o callback ImageReader da Activity visível consome o pedido e reabre Camera2. O controlador só considera a lente alterada depois que `cameraFrame` reporta a lente real de nova sessão. Quadro antigo não atravessa gerações.
- Não existe persistência de autorização, comando de gravação/áudio, foreground service de câmera ou permissão Android adicional.

## Alpha.70: selos de estado, sessão Camera2 e polling

`ui/SettingsBadgeKind.kt` define selos explícitos; telas fornecem booleanos reais via `SettingsBadgeKind.forEnabled`, evitando inferência por legendas. `CameraFrameStore.stop(token)` ignora pedido obsoleto, enquanto `stop()` continua disponível ao serviço para parada global imediata. `CameraShareActivity` possui somente o token de sua sessão, não revoga a captura aberta em nova instância. `RemoteScreenActivity` usa executor de polling de thread única, geração por visita e shutdown em `onDestroy`; protocolos de transporte inalterados. Build Android e testes de concorrência em hardware seguem pendentes.

## Alpha.72: diagnósticos e recuperação SAF

`DeviceStateReader` fornece leitura local de estado do display, bloqueio, disponibilidade indicativa das câmeras (callback associado ao serviço monitorado) e o último evento de atividade quando `PACKAGE_USAGE_STATS` foi liberado manualmente. Sem inspeção do conteúdo da tela. `NetworkReader` lê SSID e RSSI apenas com localização Android autorizada, devolvendo nulos quando indisponível. `BatteryTemperaturePolicy` define cores de interface, não uma garantia de segurança térmica.

`SharedDataFolder` continua restrito à árvore SAF e agora responde `dataHash` (hash do arquivo integral) e `dataChunks` (até dois blocos autenticados por pedido). A cópia recursiva é pré-enumerada pelo controlador e limitada a 20 arquivos, 128 MiB e 200 itens, sem sobrescrever os existentes. Não há fluxo persistente/resumível caso Activity ou processo termine.

## Alpha.73: alertas e proteção local

- `HealthAlertMonitor` é consultado pelo serviço local aproximadamente a cada 30 segundos enquanto o monitoramento autorizado estiver ativo. Classifica bateria e temperatura com histerese; o status térmico do Android 10+ tem precedência. Registra apenas o último evento com sequência, motivo e horário. A API de snapshot já cifrada inclui `healthAlerts`, sem criar novo endpoint público.
- `ControllerHealthAlerts` apresenta notificação somente após receber um snapshot AES-GCM válido, desde que o usuário tenha ativado os alertas nesse controlador e permitido notificações. Eventos antigos são identificados pelo horário; o app marca sequências para evitar avisos repetidos.
- `RemoteHealthJobService` usa `JobScheduler.setPeriodic(15 min)` apenas com opt-in, aparelho salvo e conectividade disponível. Não há push nem conexão permanente; o Android pode adiar a execução por Doze/restrições OEM, e o trabalho não sobrevive a interrupção explícita do app.
- Proteção térmica OFF por padrão: ao identificar status térmico severo/temperatura crítica, encerra `ScreenShareService`, revoga os quadros de câmera e a Activity local de câmera encerra sua captura. Não força encerramento de app externo, não reduz carregamento físico, não prova que Minha Câmera grava.
- `VersionNewsUi` exibe as mudanças uma vez por versão na abertura, e a opção Configurações > Novidades permite rever manualmente.
