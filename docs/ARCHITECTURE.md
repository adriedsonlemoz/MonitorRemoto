# Arquitetura (0.1.0-alpha.28)

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

**Fluxo:** cliente envia `{"v":1,"op":"snapshot","ts":epochMillis}` cifrado num envelope `{n,c}` com nonce 96-bit aleatório. Servidor valida tamanho, autenticação AEAD, versão, operação, tempo ±5 minutos e rejeita nonce já recebido no processo. Servidor responde o snapshot atual com os 30 últimos eventos cifrado pela mesma chave. Não há comando remoto de leitura de arquivos ou acionamento de recursos perigosos.

O FGS `connectedDevice` mantém o socket de escuta durante monitoramento ativo. A permissão `CHANGE_NETWORK_STATE` é declarada para o pré-requisito do tipo FGS; o app não altera configurações de rede. O serviço não depende de localização, Accessibility, root, APIs ocultas ou desbloqueio da tela para consultar as APIs permitidas.

**Limites:** TCP não faz NAT traversal; internet depende da VPN, rede de dispositivos ou infraestrutura futura. O monitor não envia push para o controlador; este consulta quando aberto. OS/fabricantes podem encerrar/restringir o serviço. Testes reais em Android 13–17 são pendentes. Esquemas de sincronização e políticas granulares ainda não estão implementados.

## Distribuição e assinatura

O mesmo APK atende aos modos Monitorado e Controle. A fonte de versionamento é `app/build.gradle.kts`, com `versionName=0.1.0-alpha.28` e `versionCode=28`.

- GitHub Actions compila e testa; copia o `.apk` compilado para `dist/MonitorRemoto-v0.1.0-alpha.28.apk`.
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
