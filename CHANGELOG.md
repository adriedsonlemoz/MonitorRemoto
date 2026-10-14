# Histórico de versões

## 0.1.0-alpha.31 (14/10/2026)

- SMS em tempo real mediante autorização `RECEIVE_SMS`, buffer volátil e consulta sob demanda; histórico recente de recebidos e enviados.
- Callbacks por parte para envio/entrega SMS, identificação opcional de chips com READ_PHONE_STATE e escolha no formulário de resposta.
- MMS somente assuntos/datas quando autorizados pelo provedor, sem anexos.
- Seleção opcional de notificações de todos os apps elegíveis, preservando listas individuais e filtros de sensibilidade.
- Botões Copiar e Responder em SMS e notificações, diálogos de mensagens sem FLAG_SECURE para permitir capturas. QR e chaves continuam protegidos.
- versionCode 31 / versionName 0.1.0-alpha.31. Compilação Android real permanece pendente.

## 0.1.0-alpha.30 (14/10/2026)

- Central **Mensagens** no controlador, com SMS recebidos e notificações autorizadas, data/hora e ações de resposta com confirmação.
- Leitura opcional de até 30 SMS recebidos usando `READ_SMS`, consultados sob demanda, filtrando indícios de autenticação. Sem armazenamento local, sem `RECEIVE_SMS`, desativado por padrão; no Android a permissão pode ser bloqueada por ser *hard restricted*.
- Resposta SMS opcional por `SEND_SMS`, apenas para números regulares presentes na caixa recebida, taxa mínima de 20 s entre solicitações e corpo limitado a 500 caracteres; pode gerar cobrança, não confirma entrega.
- Resposta por notificação usando `Notification.Action` e `RemoteInput`, somente para aplicativos permitidos com ações ativas, token efêmero de uso único e validade de até 10 minutos. Histórico de WhatsApp não é acessado.
- As três autorizações independentes são desativadas por padrão; pedidos cifrados AES-GCM usam autenticação, nonce antirreplay, versão exata e confirmação de ação no controlador. Revogação limpa os tokens.
- `versionCode 30`, `versionName/AppVersion.NAME 0.1.0-alpha.30`. A validação sintática/estrutural local não substitui compilação Android, permissões reais ou envio de SMS em aparelhos.

## 0.1.0-alpha.29 (14/10/2026)

- Corrigido erro de sintaxe de `MonitorScreenUi.kt` (linhas 143–145) apontado no workflow “Gerar APK e Release 29”: a criação/abertura do diálogo de acesso autenticado tinha uma terminação `}.create()` duplicada após o refactor da alpha.28.
- `showIncomingAccessActions` volta a instanciar o diálogo uma vez e exibi-lo uma vez por `showManagedDialog(dialog)`, preservando as ações Trocar chave, Fechar e Parar serviço.
- Adicionada verificação de regressão no `tools/check_distribution.py` para impedir a duplicação do construtor/exibição do diálogo.
- `versionCode 29`, `versionName/AppVersion.NAME 0.1.0-alpha.29`, metadados, testes de compatibilidade e documentos sincronizados. Sem alterações de permissões ou protocolo além da exigência de igualdade exata da versão.
- Compilação Android completa e testes físicos dependem de nova execução do GitHub Actions; não há APK nesta entrega de fonte.

## 0.1.0-alpha.28 (14/10/2026)

- Falhas de conexão diferenciadas por causa provável (EOF, timeout, conexão recusada, endereço inválido, autenticação e versão), sem exibir exceções duplicadas no painel.
- Mismatch de versão em consultas autenticadas passa a receber uma resposta AES-GCM explícita do monitorado, permitindo identificar a versão remota quando o servidor também usa o novo fluxo. Chaves inválidas continuam sem resposta.
- Cartão de diagnóstico do controlador com versão local/remota quando conhecida, IP, horário, checagens, ações de tentar novamente/editar e cópia do diagnóstico sem chave.
- `AppDialogUi.kt` centraliza janelas do aplicativo para evitar sobreposição, com cabeçalhos de ícones e conteúdo em cartões para informações e confirmações importantes.
- Seleção de aplicativos para notificações agora mostra lista real com ícones, busca, checkboxes, contagem, tratamento de lista vazia e aviso de privacidade.
- Validação manual de IP/chave permanece no próprio formulário sem apagar o texto ao informar erros.
- Preservados QR seguro, Android Keystore, consentimento do número do chip e funcionalidades existentes; nenhuma permissão Android nova.
- Compilação Android completa e testes físicos pendentes.

## 0.1.0-alpha.27 (14/10/2026)

- Padronização visual do aplicativo segundo os cartões, bordas, ícones vetoriais e espaços da tela Detalhes do aparelho da alpha.26.
- Home redesenhada com dois atalhos lado a lado, aparelhos anteriores em cartões compactos e gestão por menu visual.
- Modo Monitorado atualizado com ícone de aparelho, informações de IP/acesso em cartões e ações QR/Informações/Interromper em grade horizontal.
- Conectar reformulado com cartões QR e Manual lado a lado; histórico, diálogos de segurança e formulário manual seguem o mesmo design.
- Submenus Configurações (Aparência, Permissões, Segurança, Serviço), Diagnóstico e Sobre agora usam opções ilustradas e métricas em cartões.
- Janelas Minhas informações e Gerar QR reorganizadas com cabeçalhos vetoriais, credencial inicialmente oculta e QR temporário protegido. Leitor QR utiliza cabeçalho e controles coerentes com a nova identidade.
- Cabeçalho principal usa ícones Android vetoriais; protocolo, credenciais, permissões e fluxo de conexão foram preservados.
- Compilação Gradle/Android e testes visuais em aparelhos físicos continuam pendentes.

## 0.1.0-alpha.26 (14/10/2026)

- Painel de controle redesenhado seguindo o layout aprovado: cartões de Bateria, Temperatura e Rede em linha, ícones vetoriais e grade de seis atalhos. Removidos os indicadores redundantes e chips de IP/chave da tela principal.
- Nova `RemoteDetailsActivity` com seções de bateria, Wi-Fi, conexão, rede móvel, chip, armazenamento, RAM, Android e hardware; progresso de uso do armazenamento calculado do aparelho monitorado.
- Novos leitores locais `DeviceInfoReader` e `TelephonyReader`; informações transmitidas somente dentro das consultas criptografadas existentes e quando o serviço está autorizado. `NetworkReader` acrescenta indicadores efetivamente observados pelo Android.
- `READ_PHONE_NUMBERS` e opção de compartilhamento do número da linha desligada por padrão; autorização explícita no aparelho monitorado, revogável no app e informação "Não disponível" quando não fornecida.
- Atalho **Recuperar dados** marcado Em breve; função de transferência remota ainda não implementada. Ação Copiar resumo técnico passou à tela Detalhes.
- Contrato de versão sincronizado para 0.1.0-alpha.26/versionCode 26, validadores e documentação atualizados. Testes físicos e compilação Android em CI pendentes.

## 0.1.0-alpha.25 (14/10/2026)

- Corrigidos dois erros de compilação Kotlin do workflow **Gerar APK e Release 25** na tela de acesso recebido (`IncomingAccessScreenUi.kt`, linhas 113 e 114), onde o receiver de `LinearLayout.apply` era passado como `Context` para `View` e `LinearLayout`.
- Usados `View(this@infoMetric)` e `LinearLayout(this@infoMetric)`, fazendo referência inequívoca à `IncomingAccessActivity` no método de extensão.
- Validação estática adicionada para impedir regressão desse tipo; versão sincronizada em Gradle, `AppVersion`, teste JUnit e documentação.
- Preservados design, protocolo, pareamento e permissões da alpha.24. Novos detalhes do aparelho e recuperação de dados ficam para etapa posterior.
- Compilação Android completa e testes físicos ainda precisam ser confirmados no GitHub Actions e em aparelhos.

## 0.1.0-alpha.24 (13/10/2026)

- Segunda etapa da refatoração modular, preservando layouts, permissões, endpoint e criptografia.
- `MainActivity.kt` reduzida de 1.000 para ~322 linhas; removidos controles de rede, geração de QR, conexão manual, diálogos de credenciais e seleção de notificações da Activity principal.
- Criados `RemoteConnectionManager`, `ConnectionController`, `ConnectionScreenUi`, `PairingController`, `PairingScreenUi`, `PairingQrUi` e `MonitorCredentialsUi`. A seleção de apps autorizados e Sobre passaram para `SettingsScreenUi`.
- Estado e cache de consulta ficam no `RemoteConnectionManager`, que invalida consultas pendentes ao trocar/desconectar o aparelho e fornece callbacks na thread principal.
- IP selecionado na conexão manual e no pareamento QR é persistido no fluxo de conexão antes da primeira consulta; a chave permanente segue no Android Keystore.
- Regra de versões iguais mantida: os dois celulares devem executar a alpha.24.
- Testes estruturais atualizados; build completo Android e testes físicos ainda pendentes.

## 0.1.0-alpha.23 (13/10/2026)

- Continuidade da refatoração da interface: a tela de acesso recebido foi separada em um módulo próprio (`IncomingAccessScreenUi.kt`), reduzindo a responsabilidade direta da `IncomingAccessActivity`.
- Tela **Acesso remoto detectado** redesenhada: ícone centralizado, cartão principal com informações completas já na primeira tela (nome do aparelho, IP, método, primeira e última consulta) e indicação de acessos adicionais.
- Removido o pop-up **Quem está?**. Quando houver mais de um aparelho recente, todos passam a ser exibidos diretamente na mesma tela, evitando uma segunda interface desnecessária.
- Mantido o botão **Parar acesso** e o retorno automático ao modo Monitorado quando o acesso expira ou o serviço é interrompido.
- Versionamento e documentação sincronizados para `0.1.0-alpha.23` / `versionCode 23`; verificador atualizado para exigir o novo módulo de UI e impedir o retorno do botão/diálogo removido.
- Compilação Android completa e testes físicos em dois celulares permanecem pendentes.

## 0.1.0-alpha.21 (13/10/2026)

- Corrigido erro Kotlin do workflow **Gerar APK e Release 21**, `IncomingAccessActivity.kt:93`: resolução de `background` como `Drawable` no `ScrollView.apply`, incompatível com o parâmetro `Int` de `setBackgroundColor()`.
- Referência ao atributo de cor da Activity agora explícita (`this@IncomingAccessActivity.background`), preservando o desenho da tela de acesso recebido.
- Versionamento `versionCode 21` e `AppVersion.NAME` sincronizados com README, RELEASE_NOTES, AGENTS, arquitetura e validações.
- Adicionado teste estrutural de regressão da colisão de receiver; build Android e teste em dois celulares pendentes.

## 0.1.0-alpha.20 (13/10/2026)

- Tela de acesso autenticado dedicada em Activity própria (aberta apenas com o aplicativo em primeiro plano), atualizada por evento e encerrada quando o controlador se desconecta explicitamente ou quando o registro expira.
- Botões **Quem está?** (dados informados/observados e horários) e **Parar acesso** (parada global confirmada). Serviço foreground sinaliza acesso novo por notificação sem invadir outros aplicativos.
- Rejeição de pareamento/consultas entre versões diferentes. `AppVersion.NAME` exigido no QR, nos snapshots e no pedido de encerramento; a versão do servidor é verificada também nas respostas.
- `RemoteClient.disconnect` envia aviso autenticado de saída; `AuthenticatedAccess.disconnected` remove apenas a entrada correspondente. Serviço permanece ativo e a tela retorna ao modo Monitorado pronta para gerar QR.
- Recuperação conservadora do serviço autorizado quando o usuário traz o app para o primeiro plano; parada intencional não reinicia automaticamente.
- Novo diálogo de Configurações com grade compacta e ícones vetoriais; componentes de métricas menores, ações remotas de toque ampliado e dados da rede realmente informados pelo Android (sem SSID presumido).
- Informações de cache indicadas como antigas após erro de conexão; sem aumento de permissões ou acesso oculto.
- Necessários testes Android no GitHub Actions e físicos entre dois celulares. Sem APK compilado localmente.

## 0.1.0-alpha.19 (13/10/2026)

- Painel do aparelho conectado redesenhado com cabeçalho compacto, status visível, IP, método de conexão e botões **Desconectar/Reconectar** e **↻ Atualizar** no topo à direita.
- Resumo visual do aparelho remoto em grade 3x2 com ícones para bateria, temperatura, correntes, tensão e contador de eventos.
- Inclusão de grade de **Funções remotas** com atalhos compactos: Notificações, Copiar, Detalhes, Mensagens, Ver tela e Localização. Os três últimos permanecem sinalizados como recursos futuros.
- Diálogo de **Notificações** exibe os eventos autorizados recentes; **Copiar** agora leva um resumo sensível do aparelho remoto para a área de transferência; **Detalhes** mostra dados técnicos e de rede.
- Mantidos fluxo Home separado, pareamento QR/manual, KeyStore, polling de 15 segundos e retorno à Home ao desconectar.

## 0.1.0-alpha.18 (13/10/2026)

- Compactação do menu Conectar: QR | Manual horizontal, sem opção Inserir código QR. Aparelhos recentes exibem Conectar | Renomear | Esquecer na mesma linha.
- Pop-up Minhas informações: Mostrar, Copiar e Compartilhar alinhados horizontalmente, preservando chave oculta e confirmação de envio.
- Desconectar retorna automaticamente à Home. Navegação continua sem parar o Monitorado.
- Registro volátil de acessos realmente autenticados no aparelho monitorado, com atualização por callback e expiração de 45 segundos. Nome e método declarados são identificados como informações não verificadas.
- Cartão de acessos recebidos e Ações: detalhes, parar serviço ou trocar chave global; sem fingir que existe sessão TCP persistente ou revogação individual.
- Mantidas compatibilidade do protocolo v1, validação AES-GCM, proteção do Keystore e polling de 15 segundos.

## 0.1.0-alpha.17 (13/10/2026)

- **Home independente como tela inicial**, com o convite para conectar novo aparelho, mesmo quando o serviço monitorado ou a rede estão indisponíveis.
- A seção **Aparelhos anteriores** lista os perfis já pareados, com nome, IP, data/hora da última consulta autenticada bem-sucedida ou indicação de que ainda não houve conexão confirmada.
- Cada aparelho anterior recebeu **Conectar** e engrenagem individual com **Renomear** e **Esquecer**. Ações a partir da Home retornam à Home, sem reabrir o seletor de métodos.
- **Monitorar este aparelho** abre uma página separada, acessível também quando não há aparelhos salvos. O botão **Home** (e Voltar) retorna à escolha inicial sem interromper o serviço.
- Na página Monitorado, **Gerar QR**, **Mostrar info** e **Interromper** foram reunidos em coluna à direita do cartão de status. Quando o serviço está parado, a coluna oferece Ativar/Reiniciar e Mostrar info.
- Preservados scanner QR com confirmação, conexão manual, Keystore, credenciais por aparelho, segurança da chave, serviço foreground e polling de 15 segundos no Controle.
- Verificações de fonte e integridade locais executadas; compilação Android completa e testes de layout físico ainda pendentes.


## 0.1.0-alpha.16 (13/10/2026)

- Início passa ao modo Monitorado, com botão Home no cabeçalho ao lado da engrenagem; o menu hambúrguer foi removido sem eliminar acesso ao Controle por conexão remota.
- Modo Monitorado mostra Gerar QR Code, Mostrar minhas informações (IP/chave em pop-up) e Interromper monitoramento quando ativo.
- Pop-up de informações usa IP privado atual (prioriza Wi-Fi), chave inicialmente oculta, FLAG_SECURE, cópia explícita de ambos e compartilhamento com confirmação antes do Android Sharesheet.
- Seção compacta Conectados recentemente na página inicial: até dois atalhos, acesso à lista completa e opção de conectar outro aparelho.
- Sobre o aplicativo migra para Configurações. Home preserva o serviço monitorado e não apaga credenciais.
- Sem mudanças no protocolo, QR temporário, histórico de pares, sincronização a cada 15 s ou permissões.
- Build Android e testes reais de tela, compartilhamento e Keystore permanecem pendentes.


## 0.1.0-alpha.15 (13/10/2026)

- Conectar/Reconectar passa a abrir uma escolha rápida: QR Code, Manualmente, colagem de URI QR e até 5 aparelhos anteriores.
- Cards de aparelhos salvos com botões empilhados Conectar, Renomear e Esquecer; exclusão confirmada e chave removida do Keystore.
- Campos de IP/chave migrados para diálogo exclusivo de conexão manual, com colagem e limpeza de chave.
- Leitor Camera2 agora é exibido em janela flutuante compacta, com orientação «Aponte para o QR Code», em vez de ocupar a tela toda. Leitura requer confirmação explícita antes de iniciar o pareamento.
- Painel Controle: Conectar/Reconectar/Desconectar compacto ao lado do nome; Atualizar azul, com texto e alinhado à direita.
- Protocolo, permissões, prazo e uso único do QR, histórico e atualização periódica de 15 segundos preservados.
- Testes físicos e compilação Gradle/Android continuam pendentes de CI.

## 0.1.0-alpha.14 (13/10/2026)

- Revisão visual do Painel Controle: cartão principal reorganizado para destacar nome do aparelho, estado, atualização e ação principal **Conectar/Desconectar**.
- Revisão visual dos diálogos **Menu**, **Configurações**, **Aparência**, **Aparelhos recentes**, **Permissões e notificações**, **Segurança e pareamento**, **Serviço e inicialização** e **Diagnóstico**, agora com cartões, subtítulos e melhor hierarquia.
- Mensagens rápidas e ações secundárias passam a usar o mesmo padrão visual dos demais pop-ups, reduzindo inconsistência entre telas.
- Mantidos protocolo local, pareamento por QR, chaves no Android Keystore, aparelhos recentes e polling de 15 s.
- Compilação Android completa e testes físicos continuam pendentes.

## 0.1.0-alpha.13 (13/10/2026)

- Polimento visual do Painel Controle: o botão **Conectar** saiu do cabeçalho e foi movido para o cartão do aparelho remoto, ao lado do nome/status.
- A ação principal agora é contextual: **Conectar** quando não há sessão ativa e **Desconectar** quando já existe consulta conectada/atualizando; desconectar remove apenas a seleção atual, preservando o histórico de aparelhos.
- Diálogos **Menu** e **Configurações** redesenhados em cartões com título + subtítulo, melhorando hierarquia visual e separação das opções.
- Mantidos modo Monitorado simplificado, histórico de aparelhos, pareamento por QR, segurança no Keystore e polling de 15 s.
- Compilação Android completa e testes físicos continuam pendentes.

## 0.1.0-alpha.12 (13/10/2026)

- Tela Monitorado reduzida a status real, IP de rede, QR e ativar/interromper conforme o estado do serviço.
- Configurações reorganizadas em Aparência, Permissões e notificações, Segurança e pareamento, Serviço e inicialização, Diagnóstico.
- Permissões de notificações continuam opcionais e independentes da leitura de bateria.
- Preservados chave de assinatura, pareamento, histórico de aparelhos e comunicação local existente.
- Compilação Android e testes físicos pendentes.

## 0.1.0-alpha.11 (13/10/2026)

- Tela inicial reorganizada: o aplicativo abre diretamente no **Painel Controle** e as abas fixas **Monitorado/Controle** foram removidas da tela principal.
- Novo menu **☰** no cabeçalho com acesso ao Painel Controle, Modo Monitorado, Aparelhos recentes e Sobre.
- Botões de topo reorganizados: **Conectar**, **☰** e **⚙**; chips de versão e estado do monitorado adicionados.
- Pop-up **Conectar aparelho** redesenhado em seções separadas, com campos mais visíveis, fundo destacado, maior área de toque e ações **Colar** e **Limpar** para a chave.
- Lista de aparelhos recentes mantida no diálogo, agora em cartões separados e mais legíveis.
- Painel de dados remotos reorganizado com cartões de bateria/temperatura e métricas em blocos distintos, preservando atualização manual por **↻** e automática a cada 15 s.
- Mantidas Camera2 para QR, chaves por aparelho no Android Keystore, assinatura estável via Secrets existentes e demais regras de segurança.
- Build Android completo e testes físicos permanecem pendentes; validações locais sem Gradle/SDK continuam documentadas em `docs/VALIDATION.md`.

## 0.1.0-alpha.10 (13/10/2026)

- Controle redesenhado com cabeçalho **Conectar** e **⚙**, cartão compacto de monitoramento, atualização discreta e estados de consulta ligados a sucesso/erro autenticado.
- Diálogo Conectar reúne IPv4/hostname, sugestão de prefixo Wi-Fi/CIDR, colar chave, ler QR Camera2, digitar conteúdo do QR e lista dos cinco aparelhos recentes.
- `RecentDevices` mantém nomes, hosts e última consulta, com chave individual protegida no `SecretVault`; migra a credencial antiga sem repareamento e impede reutilização acidental de chave entre hosts.
- Tema claro padrão, escuro e sistema, aplicado em atividades Android Views e armazenado nas preferências.
- Preservados protocolo criptografado, permissões, firmware remoto, QR, workflow de APK/AAB e assinatura estável da alpha.7.
- Sem confirmação de compilação Android completa ou comportamento físico nesta entrega.

## 0.1.0-alpha.9 (13/10/2026)

- Correção do build GitHub Actions #7: duplicidade do override de `onRequestPermissionsResult` em `MainActivity.kt` (422 e 580) causava `Conflicting overloads`. Unificado em callback único com tratamento independente de câmera e permissão de notificações.
- `POST_NOTIFICATIONS` mantém solicitação condicional a Android 13+ para ativar monitor; `CAMERA` permanece solicitada apenas quando o usuário abre o leitor QR.
- Workflow não tenta criar Artifact vazio caso Gradle pare antes da geração do APK.
- Teste estrutural regressivo exige callback único e os dois fluxos distintos; documentação sincronizada, `versionCode 9`.
- Mantida compatibilidade com a keystore Release gerada anteriormente (nenhuma chave nova).
- Build Android e QR no dispositivo ainda dependem do GitHub Actions e de ensaios físicos.


## 0.1.0-alpha.7 (13/10/2026)

- Correção da falha real GitHub Actions #6: 3 testes `EventJournalTest` acionavam métodos de `org.json` mockados em JVM Android; adicionada projeção `EventJournal.latest` independente de serialização JSON e testes adaptados. As funcionalidades reais do buffer e filtro são preservadas.
- Leitor QR passa a usar Activity explícita privada `QrScanActivity`, sem flag `clearTaskOnLaunch` herdada da Activity genérica; permissão de câmera solicitada e tratada antes de abrir o scanner; rotação desbloqueada e modo selecionado salvo nas recriações de Activity.
- Fluxo manual por IP/chave preservado. Hipótese sobre fechamento do QR ainda exige logcat/ensaio físico para diagnóstico definitivo.
- Preparação para uso de assinatura estável através dos quatro Secrets do workflow, sem incorporar chaves no código-fonte.
- versionCode 7 e sincronização de arquivos/versionamento.


## 0.1.0-alpha.6 (13/10/2026)

- Corrigido vazamento lógico do histórico volátil: interrupção do monitoramento, reinicialização de sessão e encerramento do serviço removem os eventos em memória; alteração da lista positiva remove notificações dos aplicativos revogados.
- Servidor revalida a lista positiva antes de transmitir os eventos, inclusive nos casos de corrida entre atualização da permissão e captura da notificação.
- Fila de conexões limitada a 12, com 3 trabalhadores; conexões excedentes são fechadas, sem fila ilimitada de sockets.
- Tipagem explícita de `java.util.ArrayDeque` para manter APIs de fila compatíveis com Kotlin/JVM.
- Nenhuma permissão nova, mudança de protocolo, segredo ou dependência; versão, documentação e verificações de regressão atualizadas.
- Build Android/CI e testes físicos entre aparelhos seguem pendentes de execução.

## 0.1.0-alpha.5 (13/10/2026)

- Corrigida a falha real do workflow GitHub Actions #4 (`:app:compileDebugKotlin`): `Unresolved reference QR_CODE_TYPES` e erro de encadeamento `setPrompt`.
- Substituída a constante inexistente `IntentIntegrator.QR_CODE_TYPES` pela constante suportada `IntentIntegrator.QR_CODE` do ZXing Android Embedded 4.3.0, com configuração explícita do scanner.
- Adicionadas verificações de regressão em `tools/check_distribution.py` para API do scanner e sincronização de `versionCode = 5`/`versionName = 0.1.0-alpha.5`.
- Mantidos QR temporário, conexão manual, protocolos locais, segurança, permissões, geração do APK direto em Releases e AAB opcional.
- Build Android completo e instalação física continuam pendentes após esta correção.

## 0.1.0-alpha.4 (versionCode 4)
- Pareamento presencial por QR local com código válido por 120 segundos e uso único, senha temporária e resposta cifrada com credencial permanente.
- Scanner de QR integrado, confirmação de IP e salvar chave via Android Keystore. Pareamento manual mantido.
- Validação do bind da porta 37841 antes do serviço ser marcado como iniciado; revogação de códigos QR ao parar serviço ou trocar a chave.
- Release passa a ser fonte primária do APK; Artifact de APK somente como contingência para execução sem publicação ou com falha.
- Documento de protocolo QR e riscos do Play Protect. O bloqueio por PHA da alpha.3 não foi solucionado nem reproduzido por testes locais.

## 0.1.0-alpha.3 (versionCode 3) — 2026-10-13

- Gerar arquivo real `MonitorRemoto-v0.1.0-alpha.3.apk`, publicado em GitHub Releases sem ZIP.
- Manter `upload-artifact` como alternativa (download do GitHub sempre vem ZIP).
- Preparar build Release assinado com quatro Secrets opcionais e AAB assinado, com fallback Debug.
- Adicionar validação automática da distribuição, hash SHA-256, documentação e diretrizes `AGENTS.md`.
- Sem alterações nas permissões sensíveis, mensagens e protocolo local da alpha.2.

## 0.1.0-alpha.2 (versionCode 2) — 2026-10-13

- Workflow corrigido: `android-actions/setup-android@v4` elimina falha no pacote obsoleto `tools`.
- Corrigida atribuição errada de cor (`setTextColor(text)`) no campo de texto da UI.
- Retirada a permissão `RECEIVE_SMS` e o receptor SMS. SMS novos só serão exibidos quando presentes em notificações permitidas.
- Nova lista positiva de aplicativos para notificações, vazia por padrão.
- Aviso visível antes de direcionar ao acesso a notificações; proteção conservadora contra conteúdo de autenticação e bancos conhecidos.
- Apps financeiros conhecidos excluídos da seleção; `queries` de launcher para listagem sem `QUERY_ALL_PACKAGES`.
- Documentação e artifact sincronizados. Ainda requer compilação Gradle e validação em aparelho.

## 0.1.0-alpha.1 (versionCode 1) — 2026-10-13

- Projeto inicial Kotlin Android com modos Monitorado e Controle.
- Telemetria de bateria, temperatura, tensão e correntes.
- Captura autorizada de notificações e SMS novos opcionais.
- Canal TCP cifrado AES-256-GCM, Android Keystore, chave pareada, serviço foreground.
- Workflow GitHub Actions de testes e APK Debug.
