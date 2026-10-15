## Alpha.73 · painel, novidades e proteção térmica (15/10/2026)

- [x] Partiu da alpha.72 preservando criptografia, emparelhamento e cópia de arquivos.
- [x] Novo painel, permissão acessível em Diagnóstico local, cabeçalho com nome editável.
- [x] Políticas de alertas e notificação local, resumo cifrado e verificação remota opcional por JobScheduler de melhor esforço.
- [x] Novidades persistentes por versão e opt-in independente para proteção térmica, câmera própria fecha ao sinal térmico severo.
- [ ] Android Gradle compileDebugKotlin, testDebugUnitTest e assembleDebug: ainda não confirmados.
- [ ] Ensaios físicos de permissão de uso/SSID, alternância da tela com Minha Câmera, aquecimento, bloqueio de tela, Doze, alertas e reconexão.
- [ ] Integração real com Minha Câmera e entrega imediata de push em segundo plano: não implementadas.

## Alpha.72 · telemetria e cópias SAF (15/10/2026)

- [x] Base 0.1.0-alpha.71 preservada, versão Gradle/AppVersion, AGENTS, README, CHANGELOG e RELEASE_NOTES atualizados para 0.1.0-alpha.72+72.
- [x] `tools/check_distribution.py` aprovado com novas invariantes para DeviceStateReader, NetworkReader, bateria e transferência de arquivos.
- [x] Parser PSI Kotlin: 106 arquivos de produção sem erros de sintaxe; código puro Kotlin/JVM com 20 verificações de limites e temperatura executadas.
- [x] Duplicatas SAF rejeitadas em vez de selecionar o primeiro documento, profundidade de caminho corrigida, limite de pasta recursiva e verificação SHA-256 da origem implementados.
- [ ] **Build Android não realizado**: falta Android SDK e Gradle operacional no ambiente. Só o GitHub Actions confirmará compileDebugKotlin, testDebugUnitTest e assembleDebug.
- [ ] Testes físicos: tela desligada com Minha Câmera, comportamento de permissão SSID, disponibilidade CameraManager, cópia de pasta completa, cancelamento, provedor SAF e consumo.
- [ ] Retomada persistente após encerramento da Activity/processo e transferência desacoplada da Activity (futuro; ainda não implementadas).

## Alpha.71 · correção da compilação #66 (15/10/2026)

- [x] Examinado o log original do workflow #66: `:app:compileDebugKotlin` falhou em `RemoteScreenActivity.kt:472:107`, analisador Kotlin não resolve inferência recursiva.
- [x] Tipo `private val poll: Runnable` explícito e `main.post(this)` no callback obsoleto, preservando uso de executor único e proteção da visita.
- [x] Versão `0.1.0-alpha.71` / `versionCode 71` e documentação sincronizadas; teste de regressão acrescentado ao verificador.
- [ ] Reexecutar `:app:testDebugUnitTest :app:assembleDebug` em CI e confirmar APK; não alegar compilação Android completa a partir do verificador estático.
- [ ] Testar tela JPEG/H.264 e ciclo `onPause/onResume` em dois telefones reais.

## Alpha.69 — correção de compilação da interface (15/10/2026)

- [x] GitHub Actions execução #64 (`38734079523`) analisada: `:app:compileDebugKotlin` falhou com **oito erros** em `AppDialogUi.kt`/`UiComponents.kt` (`lineSpacingExtra` read-only, `darkTheme` privado).
- [x] `setLineSpacing(extra, 1f)` substituiu as 4 atribuições à propriedade somente leitura; `darkTheme()` é agora `internal` na `MainActivity`.
- [x] Versionamento sequencial `0.1.0-alpha.69` / `versionCode 69` sincronizado em Gradle, AppVersion e documentação.
- [x] Regressões de compilação adicionadas ao validador estrutural para evitar os dois erros da alpha.68.
- [ ] Compilação Android real, suíte JUnit Gradle e execução em aparelhos não realizadas localmente; reexecutar CI com alpha.69.

## Alpha.68 — usabilidade das Configurações (15/10/2026)

- [x] Base alpha.67 analisada antes das alterações; foco restrito à experiência de Configurações, sem mexer no protocolo remoto.
- [x] `UiComponents.kt`: cartões `iconMenuRow` ampliados com selo de estado/ação (`ATIVADO`, `DESATIVADO`, `ABRIR`, `VOLTAR`, `REVOGAR`) e tipografia maior.
- [x] `AppDialogUi.kt`: confirmações e avisos migrados para layout próprio com botões grandes, evitando os botões pequenos padrão do `AlertDialog`.
- [x] `SettingsScreenUi.kt`: subtítulos de várias opções reescritos para refletir o efeito real de cada ação, reduzindo ambiguidades de “ativar/desativar”.
- [x] Versionamento sequencial 0.1.0-alpha.68 / versionCode 68 sincronizado em Gradle, AppVersion, README, CHANGELOG, AGENTS, notas, arquitetura e verificador.
- [x] `python3 tools/check_distribution.py` deve continuar sendo executado antes do empacotamento.
- [ ] Compilação Android/Gradle completa não executada neste ambiente.
- [ ] Validação manual visual em aparelho real ainda pendente, incluindo telas adicionais de Configurações além das mostradas nos prints.

## Alpha.67 — correção da suíte JUnit do workflow 62 (15/10/2026)

- [x] ZIP de logs da execução 62 analisado (105 testes, 5 falhas em `CameraFrameStoreGenerationTest`), `:app:compileDebugKotlin` passou com avisos não fatais.
- [x] Causa raiz: `CameraFrameStore` chamava `SystemClock.elapsedRealtime()` a partir de teste JVM sem implementação real de API Android.
- [x] Correção: tempo monotônico relativo ao processo em `System.nanoTime()` com sentinela positivo; limites de validade, token por sessão e opt-in local continuam. Adicionado teste explícito de regressão sem `SystemClock`.
- [x] Versionamento sequencial 0.1.0-alpha.67 / versionCode 67, README, AGENTS, changelog, notas e arquitetura sincronizados.
- [x] 23 testes isolados Kotlin/JVM de `CameraFrameStoreGenerationTest`, `CameraRemoteSwitchPolicyTest` e `CameraStreamPolicyTest` executados com classes de asserção JUnit compatíveis, sem android.jar. Incluem as cinco falhas anteriores e a nova regressão; **não equivalem** ao Gradle do Android.
- [x] Verificador estrutural, XMLs, YAML e integridade ZIP conferidos localmente.
- [ ] **Reexecutar** `:app:testDebugUnitTest :app:assembleDebug` no GitHub Actions após publicar alpha.67. Suíte Gradle completa não disponível localmente.
- [ ] Testes reais Camera2, segurança e latência em dois telefones continuam pendentes.

## Alpha.66 — correção do workflow 61 (15/10/2026)

- [x] **Falha confirmada no GitHub Actions**: `drilemoz/MonitorRemoto`, execução `38685307320`, número 61, tentativa 2, `:app:compileDebugKotlin`. Compilador reportou `CameraShareActivity.kt:83:70 Assignment type mismatch: actual type is 'TextureView', but 'TextureView.SurfaceTextureListener?' was expected`.
- [x] Causa corrigida: `surfaceTextureListener = this@CameraShareActivity` em vez de `this` dentro do `TextureView.apply`.
- [x] Teste de regressão incluído em `tools/check_distribution.py` para conferir que o receiver explícito é mantido.
- [x] Versões sincronizadas para `versionName=0.1.0-alpha.66` e `versionCode=66`, sem alteração de políticas de permissão, comandos ou transmissão.
- [x] `python3 tools/check_distribution.py` aprovado, incluindo a regressão de `surfaceTextureListener = this@CameraShareActivity`.
- [x] Teste Kotlin/JVM de escopo compilado e executado com stub tipado; forma incorreta `= this` falha com *type mismatch* (teste negativo). Não compila a Activity Android real.
- [x] Parser Kotlin PSI: 132 fontes sem erros sintáticos (não resolve API Android).
- [x] 46 XML válidos.
- [x] 0 JSON válidos.
- [x] Workflow YAML analisado.
- [ ] **Compilação Android real ainda NÃO executada para alpha.66**; aplicar no GitHub e verificar resultados do workflow novo. O log original não substitui build da versão corrigida.
- [ ] Testes de prévia Camera2, trocas autorizadas, rede, bateria e regressão nos aparelhos permanecem pendentes.

## Alpha.65 — alternância remota de câmera com opt-in temporário (15/10/2026)

- [x] Base alpha.64 extraída e `AGENTS.md`, README, CHANGELOG, código da câmera e documentação examinados antes das alterações.
- [x] Versão `0.1.0-alpha.65` / `versionCode 65`, `AppVersion`, workflow de nomes APK, notas, arquitetura e documentação sincronizados.
- [x] `python3 tools/check_distribution.py`: aprovado, incluindo ausência de comando de abertura remota, autorização local temporária, token e descarte de pedido após revogação.
- [x] 22 testes Kotlin/JVM isolados executados com stubs de `SystemClock` e asserções compatíveis com JUnit. Cobrem validação da câmera, sessão corrente, demanda recente, revogação, duplicatas, limite entre pedidos e continuidade do JPEG. Este ensaio **não equivale** a Android Gradle Test.
- [x] Parser PSI Kotlin: 132 arquivos `*.kt` sem erros sintáticos. A análise não resolve APIs Android.
- [x] 46 XML Android válidos e workflow YAML interpretável.
- [x] Não acrescentadas permissões Android, comando ADB nem serviço de câmera em background.
- [ ] Build completo `:app:testDebugUnitTest :app:assembleDebug` ainda não executado: não há Android SDK/Gradle preparado neste ambiente.
- [ ] Troca de câmera em dois dispositivos, recusa por câmera ocupada, revogação durante pedido, orientação, reconexão, consumo e latência reais continuam pendentes.

## Alpha.64 — validação local (15/10/2026)

- [x] Código base alpha.63, AGENTS, README, CHANGELOG e documentação lidos antes da modificação.
- [x] `versionName=0.1.0-alpha.64`, `versionCode=64`, AppVersion e documentação sincronizados.
- [x] `python3 tools/check_distribution.py`: APROVADO (incluindo requisitos alpha.64).
- [x] Testes Kotlin/JVM isolados com stubs de `SystemClock` e JUnit: 11/11 aprovados (CameraStreamPolicy e CameraFrameStore, incluindo demanda/delta JPEG).
- [x] Parser PSI Kotlin: 130 arquivos de produção e testes sem erros sintáticos; sem resolução Android completa.
- [x] 44 XML Android e 1 workflow YAML válidos.
- [x] Política de demanda autenticada Camera2 e resposta delta JPEG inspecionadas e protegidas por validador.
- [ ] Não há métricas reais de ganho de bateria/banda: medir nos aparelhos antes de quantificar economia.
- [ ] Build completo `:app:testDebugUnitTest :app:assembleDebug`: não disponível neste ambiente sem SDK Android/Gradle.
- [ ] Instalação e testes reais de camera front/back, orientação, abertura/fechamento e consumo energético.
- [ ] Medir FPS/latência ponta a ponta em rede real antes de prometer desempenho.

## Alpha.63 · orientação e concorrência Camera2 (15/10/2026)

- [x] Base alpha.62 revisada e `AGENTS.md` consultado; `versionName=0.1.0-alpha.63`, `versionCode=63`, versão de protocolo de aplicação, README/CHANGELOG/Release Notes/arquitetura sincronizados.
- [x] `python3 tools/check_distribution.py`: aprovado, incluindo invariantes de Camera2 e sessões anteriores.
- [x] Testes Kotlin isolados em JVM (executor de asserções compatível com JUnit e stub somente para `SystemClock`): **14 testes aprovados**, incluindo rotações NV21 de 0/90/180/270°, frontal/traseira, rejeição de dados inválidos, limites JPEG, troca e revogação de sessão. Não confundir com suíte Gradle.
- [x] Parser Kotlin PSI: **130 fontes `.kt` sem erros de sintaxe** (não é compilação com resolução de símbolos).
- [x] **46 XMLs válidos**; nenhuma nova permissão no manifesto, nem captura sem Activity local.
- [ ] **APK/build completo com Android Gradle Plugin não realizado**: ambiente sem SDK Android/Gradle de projeto.
- [ ] **Hardware e regressões não realizados**: câmera frontal/traseira, rotações reais do fabricante, espelhamento/preview local, toque para salvar, fluxo com dois telefones, rede e consumo; verificar também câmera indisponível e serviço encerrado durante captura.

## Alpha.62 · módulo de câmera visível e JPEG autenticado (15/10/2026)

- Política de streaming JPEG, expiração e limite de payload com testes JVM, verificador estrutural e integridade ZIP.
- Não existe operação de rede para iniciar a câmera; acesso somente depois da abertura local e permissão Android.
- **Ainda não validados**: build Android completo, Camera2 físico, alternância de lentes, rotacionar, fechar ao bloquear a tela, concorrência com leitor QR/flash, latência/consumo real, salvamento de foto e rede em dois aparelhos.

## Alpha.61 · bloqueio remoto (15/10/2026)

- [x] Código fonte da alpha.60 preservado e versionCode/versionName incrementados.
- [x] DeviceAdminReceiver com política única force-lock; não há reset-password nem wipe-data.
- [x] Autorização local padrão OFF, ativação Android pela tela oficial, PIN/senha/padrão necessários.
- [x] Confirmação digitada e validada no cliente e servidor, rate limit, retorno de solicitação conservador.
- [ ] Compilação Android/Gradle completa ainda pendente.
- [ ] Teste real em dois Android e revogação DeviceAdmin pendentes.

## Alpha.59 (14/10/2026) — cartão de bateria

- `python3 tools/check_distribution.py`: **aprovado**, regressões das versões anteriores e novos invariantes de telemetria/ícones.
- `kotlinc` isolado em `BatteryTelemetry.kt` com runner JVM: **29 casos aprovados**, contemplando escala, limites, cinco estados do Android, fontes e indicador.
- Parser Kotlin PSI (Kotlin 1.9): **90 fontes Kotlin do app sem erros sintáticos**. Não resolve símbolos nem equivale a Gradle/Android.
- XML: **41 arquivos Android válidos**. JSON: parsing aprovado. Integridade ZIP/CRC verificada na etapa de empacotamento.
- **Não executados:** Gradle `:app:testDebugUnitTest` e `:app:assembleDebug`, porque o projeto principal não inclui wrapper Gradle e o SDK Android não está instalado neste ambiente. Assim, os novos testes JUnit existem, mas não foram executados como suite Gradle.
- **Não executados:** instalação, teste USB/AC/sem fio em hardware real, latência/consumo, regressão em dois Androids, telas claro/escuro. Não afirmar que esses ensaios passaram.

## Alpha.54 — correção do workflow 52 (14/10/2026)

- Evidência: log fornecido pelo usuário, workflow *Gerar APK e Release 52*. Erro em `:app:compileDebugKotlin`: `MainActivity.kt:192:9 Unresolved reference 'lastRecoveryAttemptMs'`.
- Correção: declaração de `lastRecoveryAttemptMs` em RAM na Activity, inicializada em zero, usando relógio monotônico já existente para limitar retomadas autorizadas do serviço.
- Regressão adicionada ao verificador estrutural; este erro não é sintático e pode passar no parser Kotlin/PSI sem resolução de símbolos.
- Build Android e instalação não executados localmente; testar no GitHub Actions.

## Alpha.53 — Manutenção / navegação / Recuperar dados (14/10/2026)

- Código: opção Reiniciar normal por ADB `reboot:` com confirmação digitada; remoção da Activity automática de acesso remoto, mantendo notificação/cartão; novo Voltar e instruções SAF.
- Verificações locais: verificador estrutural aprovado; parser PSI Kotlin de 85 fontes de produção, sem erros sintáticos; XML e empacotamento verificados antes da entrega. Build Gradle/Android e testes reais de reinicialização **não executados**. Não testar a reinicialização em aparelho sem botão Power sem alternativa de recuperação.

## Alpha.52 — integração entre ADB e Ver tela (14/10/2026)

- Escopo: transferência explícita de uma sessão ADB já ativa para o visualizador, somente se existir chave normal AES-GCM para o mesmo IP. Indicador separado de **Navegação ADB** e **Imagem via Monitor Remoto/MediaProjection**. Voltar/Início/Recentes usam ADB depois de uma resposta autenticada do canal de imagem; comandos restantes continuam no protocolo existente.
- `python3 tools/check_distribution.py`: **aprovado**, incluindo regressões do handoff e fechamento ao sair.
- Parser Kotlin PSI: **85 arquivos Kotlin de produção**, nenhum erro sintático. Parser não faz resolução completa dos símbolos Android.
- Compilação Kotlin/JVM isolada de `AdbScreenBridge`, `AdbScreenRouting` e `AdbPolicy` com stubs Android: **aprovada**; **14 casos** de roteamento de comandos, IP diferente, canal autenticado, sessão e encerramento passaram. Isso não substitui os testes Android.
- **Pendências:** compilação Android completa no GitHub Actions, testes reais em dois celulares, expiração/queda de ADB durante o visualizador, mudança de IP, retorno do background, autorização MediaProjection, testes de toque e latência. A alpha.52 **não fornece vídeo por ADB** nem reconexão ADB durante o visualizador.

## Alpha.51 — retomada ADB e controles (14/10/2026)

- Escopo: heartbeat de resposta ADB 30 s, timeout 8 s, tentativas progressivas 2/4/8/15/30 s enquanto a Activity ADB estiver visível, redescoberta mDNS somente `_adb-tls-connect` no último IP, ação Cancelar reconexão e controles fixos de navegação/volume.
- Checagens locais: `python3 tools/check_distribution.py`, compilação/execução isolada da política de retry Kotlin/JVM, parser PSI em 104 fontes Kotlin, conferência XML/YAML e CRC do ZIP.
- **Não executado:** compilação Android Gradle (`:app:testDebugUnitTest :app:assembleDebug`) e teste de queda/retorno de Wi-Fi, depuração desligada, alteração de porta, múltiplos aparelhos e reentrada na Activity. A compilação depende do CI com SDK Android.
- Sem tentativa de reativar a Depuração sem fios quando o Android a desliga. A conexão depende do serviço ADB TLS estar novamente ativo; o espelhamento continua em MediaProjection separado.

## Alpha.47 — assistente de configuração ADB (14/10/2026)

- GUI: início → papel (controlador/monitorado) → QR/código ou instruções Android → conexão ou orientação de confirmação. Exibição de IP/modelo apenas com `AdbClient.connected() == true`; o pareamento da sessão não equivale a conexão.
- Operações ADB nomeadas e callback de sucesso desacoplado do estado de conexão para permitir avançar depois de `pair`. Erros não sinalizam pareado/conectado.
- **Verificações nesta entrega:** `python3 tools/check_distribution.py` aprovado (incluindo regressão wizard); parser PSI em 99 arquivos Kotlin sem erros sintáticos; compilação e execução Kotlin/JVM de 5 cenários de status aprovadas; 43 XML e 2 YAML válidos. A suíte JUnit foi adicionada, mas não pôde ser executada localmente por falta do JUnit no classpath do ambiente; requer Android Gradle/CI.
- **Pendente:** compilação Android completa (`:app:testDebugUnitTest :app:assembleDebug`), teste físico de QR/código/mDNS, telas e comportamento em dois celulares. Checagens de sintaxe/estrutura não substituem testes de dispositivo.

## Alpha.46 — correção do workflow 45 (14/10/2026)

- Falha observada no ZIP de logs workflow 45: `:app:compileDebugKotlin` em `RemoteFilesActivity.kt:59:9` e `:66:9` com `None of the following candidates is applicable: setTextColor(ColorStateList!), setTextColor(Int)`; demais tarefas alcançadas antes da compilação não validam o APK.
- Causa: `View.foreground` de tipo `Drawable` prevalece sobre o getter `foreground` de cor da Activity dentro de `apply`. Duas chamadas passaram a usar `this@RemoteFilesActivity.foreground` (`Int`).
- Testes locais: `tools/check_distribution.py` aprovado, parser PSI em 97 fontes Kotlin com 0 erros sintáticos e contrato isolado Kotlin/JVM das duas chamadas `setTextColor(Int)` aprovado. Essas verificações não fazem resolução completa das APIs Android; compilar com Android Gradle no CI ainda é necessário.

## Alpha.45 — Recuperar dados (14/10/2026)

- Implementados picker SAF local e revogação, operações autenticadas `dataList` e `dataChunk`, UI de navegação/salvamento, limites e SHA-256 por bloco.
- O acesso é limitado à árvore selecionada localmente, resolvendo cada segmento por enumeração e sem aceitar IDs brutos de documentos pela rede. Android 11+ pode proibir pastas no seletor.
- Verificações locais: `python3 tools/check_distribution.py` aprovado (inclui invariantes alpha.45, manifesto sem permissão de armazenamento amplo, operação autenticada e revogação); parser Kotlin PSI em 79 arquivos principais sem erros sintáticos; teste isolado JVM de `RemoteFilePolicy` aprovado (caminho/limites/blocos); 41 XML válidos.
- Compilação Gradle Android completa e transferência real em 2 celulares **não executadas neste ambiente**. O teste JVM isolado não substitui os testes JUnit instrumentados nem comprova o funcionamento do provedor SAF em um Android real.

## Alpha.44 — correção do workflow 43 (14/10/2026)

- Logs recebidos: workflow 43 falhou em `:app:compileDebugKotlin` com `Unresolved reference pair` (linhas 104 e 177) e `execute` (130 e 135) em `AdbActivity.kt`. Causa: uso de trailing lambda associado ao último parâmetro `onSuccess: ((String) -> Unit)?`, em vez de `operation: (AdbClient) -> String`.
- Correção: quatro chamadas receberam `operation = { adb -> ... }`; chamada Conectar também usa argumentos nomeados e callback `onSuccess = { result -> ... }` para impedir ambiguidade futura.
- Verificação local: `python3 tools/check_distribution.py` aprovou todas as invariantes, inclusive a checagem de regressão de cinco chamadas nomeadas. Parser PSI Kotlin 1.9.0: 76 arquivos e zero erros sintáticos. Contrato de lambdas `AdbClient`/`String` compilado e executado com Kotlin/JVM 1.9.0 usando stubs de API (não é compilação real da Activity Android). 41 XML e 1 YAML válidos.
- ZIP: integridade/CRC conferidos após gerar o pacote.
- Não há compilação Gradle Android completa nem testes de pareamento em aparelho atestados para alpha.44; rodar CI e medir separadamente.

## Alpha.43 — validação desta edição (14/10/2026)

- O código-fonte foi alterado para melhorar o modo ADB e o modo de tela conforme CHANGELOG.
- Build Gradle e testes físicos: PENDENTES nesta edição; não reutilizar o resultado positivo da alpha.42 como prova de build da alpha.43.
- `python3 tools/check_distribution.py`: aprovado com novas verificações alpha.43.
- Parser PSI Kotlin 1.9.0: 76 arquivos do app, 0 erros sintáticos (não verifica resolução de símbolos Android).
- Teste Kotlin/JVM isolado de `VideoFrameStore` com `SystemClock` substituído por stub: aprovado para fila longa, novo IDR, quadros contíguos e expiração de demanda AVC.
- Validação estrutural do ZIP e CRC: pendente antes do empacotamento final.

# Validação e riscos conhecidos

## Alpha.42 — validação executada em 14/10/2026

- Build Android completo **APROVADO**: `gradle :app:testDebugUnitTest :app:assembleDebug` com JDK 17, Gradle 8.13, AGP 8.13.2, SDK 36 e Build Tools 35.0.0. A última execução terminou com `BUILD SUCCESSFUL`; avisos de APIs deprecated permanecem.
- **54 testes JUnit passaram**, zero falhas/erros/ignorados, em 17 classes. Seis testes novos de AdbPolicy cobrem IPv4 privado, rejeição de endereços públicos/nomes, limites de portas, código com zero inicial, formato/entropia/expiração do QR e lista fixa de comandos. Não são testes de handshake em aparelho.
- APK Debug produzido e manifesto inspecionado: `br.dev.monitorremoto`, versionName `0.1.0-alpha.42`, versionCode `42`, minSdk 26, targetSdk 36. Assinatura local Debug temporária, não assinatura Release estável; não presumir atualização sobre APK assinado com outra chave.
- Verificador estrutural do projeto aprovado; XML e YAML parseados; ZIP completo com CRC conferido no empacotamento.
- Comparação com ZIP alpha.41: entre os arquivos Kotlin existentes, somente AppVersion.kt e SettingsScreenUi.kt mudaram. Captura, vídeo, SMS, servidor e criptografia existentes permaneceram byte a byte iguais. Novos módulos isolados não substituem testes de regressão física.
- Bibliotecas nativas SPAKE2/Conscrypt contêm armeabi-v7a, arm64-v8a, x86 e x86_64. Isso não é prova de compatibilidade em cada aparelho/ROM ou modo de páginas de memória.
- **Pendente:** pareamento real por QR/código, mDNS, revogação, persistência Keystore, navegação MIUI/HyperOS, interface/rotação e interrupção durante operação. Nenhum celular/emulador foi usado; não foram medidas latência, temperatura ou autonomia. Espelhamento/tela física apagada via ADB não implementados.
- Dependências foram realmente resolvidas e Java/Kotlin compilados. Durante o desenvolvimento houve correção de uso de API interna de libadb; a versão final usa as APIs públicas de chave/certificado.

As seções abaixo são registros históricos de versões anteriores, não o status atual de build.


- [ ] `gradle :app:testDebugUnitTest :app:assembleDebug` em CI (não executado neste ambiente sem Gradle/SDK).
- [ ] Instalação Android 8 a 16 e permissões solicitadas.
- [ ] Acesso às notificações: revisar tratamento de configurações restritas em instalações por APK.
- [ ] Notificações de novos SMS via aplicativo de mensagens autorizado ou receptor `RECEIVE_SMS` com opção local.
- [ ] Tela bloqueada e duração mínima de 1 hora no monitorado.
- [ ] Reconexão após alternar Wi-Fi/rede de dados e religar VPN.
- [ ] Stop/Start e troca de chave bloqueiam clientes antigos.
- [ ] Reboot e políticas de economia de energia do fabricante.
- [ ] Medição da corrente contra USB power meter e outro app.
- [ ] Auditoria de vazamento, ataques por replay, excesso de conexões e políticas de privacidade.

**Validações locais (alpha.3):** verificação estrutural de permissões, workflow e versões; parser PSI Kotlin de todos os arquivos `.kt` sem erros sintáticos; 12 verificações isoladas de criptografia, chaves e filtragem com Kotlin/JVM; XML parseado e integridade/CRC do ZIP. Não representam compilação Android completa nem teste instrumentado. Nenhum APK foi produzido nem testado em dispositivo nesta sessão.

**Pendências alpha.3:** indicador real de porta em escuta; callback de erro de bind; descoberta NSD/mDNS ou QR; limites de tráfego por origem; revisão do filtro de mensagens sensíveis; migração para histórico criptografado opcional.

### Distribuição alpha.3

- [x] `tools/check_distribution.py`: versões, nome do APK, Release, assinatura opcional, AAB e manifesto; **aprovado localmente**.
- [ ] `gradle :app:testDebugUnitTest :app:assembleDebug` no GitHub Actions.
- [ ] Com Secrets: `gradle :app:assembleRelease :app:bundleRelease` e compatibilidade de assinatura.
- [ ] Validar upload direto do `.apk` na Release e instalações/atualizações entre versões no aparelho.
- [ ] Testar fluxo de erro de GitHub 403/500 com permissões do repositório.
- [ ] Verificar update da versão alpha.2 no mesmo aparelho: assinatura Debug dos runners diferentes pode impedir.

`upload-artifact` sempre gera ZIP ao fazer download; a Release disponibiliza APK direto.

**Executado localmente na alpha.3:** `tools/check_distribution.py` passou; YAML lido pelo PyYAML sem erro; 14 etapas do workflow verificadas com `bash -n`; XML do Manifest parseado; 12 verificações Kotlin/JVM reais de criptografia e filtros passaram (`kotlinc` e `java -jar`). Ausência de Android SDK/Gradle impede afirmar compilação Android completa ou criar APK neste ambiente.


## alpha.4

**Testes locais concluídos:** verificação da distribuição e sincronização da versão; parser PSI Kotlin em 18 arquivos sem erros sintáticos; 28 asserções isoladas Kotlin/JVM para QR, faixas IPv4, uso único, invalidação e criptografia; XML/YAML/ZIP (a conferir antes da entrega). Nenhum build Android completo foi executado neste ambiente.


- [ ] GitHub Actions: compilar e executar testes Kotlin/JUnit com a dependência ZXing.
- [ ] Instalar alpha.4; testar QR com Wi-Fi comum e hotspot, permissão de câmera, tempo limite, expiração, uso único e revogação.
- [ ] Confirmar compatibilidade de assinatura ao atualizar a alpha.3 (certificate Debug temporário pode impedir atualização).
- [ ] Confirmar que a Release fornece APK direto e nenhum Artifact duplicado numa execução bem-sucedida.
- [ ] Verificar classificação real no Play Protect (a alpha.3 foi bloqueada como potencialmente nociva); auditoria ou contestação necessária.
- [ ] Ensaiar fluxo de notificações e permissões sem perda de segurança bancária.

## Correção do workflow #4 (alpha.5)

- Fonte: execução GitHub Actions `38457828570` em 13/10/2026, tarefa `:app:compileDebugKotlin`.
- Erros reais: `MainActivity.kt:404:60 Unresolved reference 'QR_CODE_TYPES'` e `MainActivity.kt:405:18 Unresolved reference 'setPrompt'`.
- Ajuste: `IntentIntegrator.QR_CODE` com configuração independente do scanner; API comprovada no código da dependência JourneyApps 4.3.0.
- Verificações locais: sintaxe Kotlin, testes isolados de classes independentes de Android, testes de integridade e regras de distribuição.
- Não foi realizada compilação Android completa neste ambiente.

## alpha.6 (13/10/2026)

- [x] Análise dos pontos de retenção: desligar monitoramento, iniciar nova sessão, editar lista permitida e gerar resposta.
- [x] Adicionados testes unitários `EventJournalTest` para limpar, podar eventos revogados e filtrar no envio.
- [ ] Validar os testes JUnit pela tarefa Gradle oficial no CI.
- [ ] Confirmar em dois celulares o comportamento da revogação quando há notificações simultâneas.
- [ ] Testar tráfego abusivo e reconexão do servidor com cargas reais.
- [ ] Investigar classificação Play Protect com diagnóstico da versão/build/assinatura.

**Verificações locais concluídas nesta entrega:** 16 asserções Kotlin/JVM sobre classes reais do projeto (com implementação mínima de `org.json` somente no harness externo ao ZIP); compilação Kotlin isolada de `MonitorServer`, `SettingsStore`, `EventJournal` e transportes com stubs mínimos; `tools/check_distribution.py`; parse do `AndroidManifest.xml` e do YAML da CI; validação de ausência de keystore no ZIP. A compilação Android oficial via Gradle, os testes JUnit no plugin Android e os testes físicos **não foram executados** neste ambiente.

## alpha.7: análise do workflow #6 e scanner (13/10/2026)

- GitHub Actions #6: `:app:compileDebugKotlin` concluiu, 12 testes iniciados, 3 falhas do `EventJournalTest` nas linhas 12, 21 e 28 (chamadas aos métodos mockados `org.json` de testes Android na JVM). `:app:assembleDebug` não chegou a terminar porque Gradle interrompeu na tarefa de testes.
- Testes de eventos passaram a validar `latest()` (registros Kotlin puros), sem depender do `org.json` no classpath de teste JVM. O código de produção continua serializando com `latestJson()`; não foi removida criptografia nem filtro de apps.
- Registrada Activity de scanner própria, com tratamento de permissão; sem logcat de dispositivo não é possível atribuir causa definitiva ao fechamento relatado.
- [ ] Workflow alpha.7 `:app:testDebugUnitTest`, `:app:assembleRelease`, `:app:bundleRelease` com Secrets.
- [ ] Testar scanner após primeira concessão, recusa, retorno e rotação em dois dispositivos.
- [ ] Confirmar impressão digital do APK assinado e que alpha.8 instala em cima do alpha.7 assinado.

### Execução local alpha.7

- [x] 12 testes Kotlin/JVM de `EventJournalTest`, `SensitiveContentTest`, `CryptoBoxTest`, `PairingCodeTest`: passaram em harness JUnit compatível, com JSON mínimo apenas externo ao ZIP.
- [x] Parser PSI Kotlin: 20 arquivos `.kt` verificados, zero erros sintáticos.
- [x] `python3 tools/check_distribution.py`: aprovado.
- [x] XML do AndroidManifest e YAML do workflow válidos.
- [x] Keystore PKCS#12 privada gerada e validada via `keytool`; Secrets são arquivos SEPARADOS, não incluídos no fonte nem no Drive.
- [ ] Compilação Android Gradle da alpha.7: pendente GitHub Actions (sem Android SDK/Gradle neste ambiente).
- [ ] Teste em telefone Android do QR e checagem do certificado do APK assinado: pendentes.

## alpha.8: workflow #7 (13/10/2026)

- Log real: `:app:compileDebugKotlin FAILED` com dois erros `Conflicting overloads` em `MainActivity.kt` linhas 422 e 580. Não houve build de APK; não é uma falha do scanner em aparelho.
- Removida duplicação; todos os retornos de permissão estão em um único override com branches independentes para câmera e notificações. O teste `tools/check_distribution.py` passou a exigir esta condição.
- Artifact de contingência só é executado após empacotamento bem-sucedido, evitando aviso por arquivo inexistente quando a compilação falha.
- [ ] Executar `gradle :app:testDebugUnitTest :app:assembleDebug` no GitHub Actions para alpha.8.
- [ ] Executar `:app:assembleRelease :app:bundleRelease` com os quatro Secrets de assinatura **já configurados**.
- [ ] Validar leitura QR, recusa/concessão de permissão, orientação, retorno ao Controle e pareamento em dois aparelhos Android.
- [ ] Confirmar que alpha.8 assinada com a **mesma** keystore consegue atualizar a alpha.7 se esta foi instalada com a assinatura Release equivalente.

## alpha.9: substituir caminho de captura que continuava encerrando

- [x] Remover `zxing-android-embedded` do Gradle e `CaptureActivity` do fluxo; implementar scanner Camera2 nativo independente.
- [x] Adicionar `QrFrameDecoderTest` para luminância de QR gerado e entrada inválida (código do teste presente).
- [x] Adicionar fallback manual do texto do QR com confirmação do destino.
- [ ] Executar testes unitários Android pelo Gradle/CI, inclusive `QrFrameDecoderTest`.
- [ ] Confirmar que abrir QR com permissão concedida/negada não fecha o app.
- [ ] Testar câmera traseira, orientação, autofocus e leitura de QR entre dois aparelhos.
- [ ] Se ainda houver encerramento, coletar logcat com `FATAL EXCEPTION` e `MonitorRemotoQR` sem compartilhar QR ou chaves.
- [ ] Confirmar continuidade da assinatura no update da alpha.8 para alpha.9.

## alpha.10: interface e segurança dos aparelhos recentes

- [x] Inspeção do código alpha.9 como fonte; versionCode atualizado para 10.
- [x] Inserido cartão Controle com botão pequeno ↻, status por consulta e última atualização real.
- [x] Diálogo Conectar com sugestão CIDR baseada em Wi-Fi, botão Colar, QR e lista recente.
- [x] Separadas credenciais por aparelho e definida migração única das antigas, sem exportar chaves.
- [x] Tema claro padrão e opções escuro/sistema, guardados em preferências.
- [ ] Compilar :app:testDebugUnitTest :app:assembleDebug no GitHub Actions e verificar se dialogs cabem em tela reduzida.
- [ ] Testar fluxo de migração alpha.9 -> alpha.10 em celular sem perder pareamento e com APK de mesma assinatura.
- [ ] Testar QR Camera2, reconexão, múltiplos aparelhos, Wi-Fi com máscara diferente de /24 e VPN.

### Resultado local da alpha.10 (13/10/2026)

- **PASSOU:** `python3 tools/check_distribution.py` (6 grupos de invariantes de versões, segurança, scanner e interface).
- **PASSOU:** parser PSI de Kotlin: 25 arquivos `.kt`, zero erros sintáticos.
- **PASSOU:** 16 testes unitários Kotlin/JVM executados com executor JUnit compatível local (12 anteriores + 4 novos de IP/CIDR). Os testes antigos foram executados a partir das classes compiladas previamente da alpha.9, cujo código de produção não foi alterado na alpha.10, e os quatro novos foram compilados da fonte atual. Esse procedimento **não** substitui o Gradle/Android.
- **PASSOU:** parsing de Manifest XML e workflow YAML; CRC/ZIP da distribuição.
- **NÃO EXECUTADO:** compilação `:app:compileDebugKotlin`, `:app:assembleDebug` e testes JUnit pelo Gradle devido à ausência de Android SDK/Gradle completo no ambiente local.
- **NÃO EXECUTADO:** migração de credenciais em Android físico, comportamento dos temas/popup em telas reduzidas, QR Camera2 em aparelho real e atualização do APK assinado.

## alpha.11: tela inicial em Controle, menu e pop-up Conectar refinado

- [x] Abrir o app diretamente no Painel Controle; remover as abas fixas da tela principal.
- [x] Mover o modo Monitorado para menu dedicado, mantendo o mesmo APK e os fluxos existentes.
- [x] Reorganizar o diálogo Conectar em seções com campos mais visíveis e melhor separação visual.
- [x] Manter leitura QR Camera2, aparelhos recentes e chaves por host.
- [ ] Compilar `:app:assembleDebug` / `:app:testDebugUnitTest` no GitHub Actions após a mudança visual.
- [ ] Testar visualmente em aparelho físico o cabeçalho, o menu e o pop-up em telas pequenas.
- [ ] Confirmar atualização sobre a alpha.10 com a mesma assinatura Release.

### Resultado local da alpha.11 (13/10/2026)

- **PASSOU:** `python3 tools/check_distribution.py` após sincronização de versão e regras de distribuição.
- **PASSOU:** 16 testes Kotlin/JVM locais (core/crypto/pairing), executados por runner compatível com JUnit construído localmente, cobrindo `EventJournal`, `Ipv4Suggestion`, `SensitiveContent`, `CryptoBox` e `PairingCode/PairingSession`.
- **PASSOU:** integridade estrutural do ZIP final.
- **NÃO EXECUTADO:** compilação Android completa com SDK/Gradle, `QrFrameDecoderTest` dependente de ZXing no ambiente local, testes visuais da nova interface, leitura Camera2 em hardware e atualização do APK assinado em aparelho.

## alpha.12: Monitorado compacto

- [x] Modo Monitorado limitado a status, IP, QR e ativar/interromper contextuais.
- [x] Configurações categorizadas; operações de permissão, segredo, revogação e inicialização preservadas.
- [ ] Build Android completo pelo GitHub Actions.
- [ ] Verificar visual e funcionamento em dois celulares reais.

## alpha.13: polimento do Painel Controle e ações contextuais

- [x] Remover botão Conectar do cabeçalho do Controle e reposicioná-lo no cartão do aparelho.
- [x] Alternar o rótulo da ação principal entre Conectar, Reconectar e Desconectar conforme o estado.
- [x] Melhorar Menu e Configurações com cartões e subtítulos.
- [x] Preservar pareamento QR, aparelhos recentes e segurança no Keystore.
- [ ] Compilar `:app:assembleDebug`/`:app:testDebugUnitTest` no GitHub Actions após o polimento.
- [ ] Testar visualmente em aparelho físico o novo posicionamento do botão e os diálogos.

### Resultado local da alpha.13 (13/10/2026)

- **PASSOU:** `python3 tools/check_distribution.py` após sincronização de versão/documentação.
- **PASSOU:** 16 testes Kotlin/JVM locais (runner compatível construído localmente) para `EventJournal`, `Ipv4Suggestion`, `SensitiveContent`, `CryptoBox` e `PairingCode/PairingSession`.
- **PASSOU:** integridade do ZIP final.
- **NÃO EXECUTADO:** compilação Android completa com Gradle/SDK, testes físicos da interface, geração do APK/AAB nesta sessão e testes Camera2 em hardware.

## alpha.14: revisão visual de diálogos e painel

- [x] Reorganizar o cartão principal do Painel Controle para reduzir repetição e dar mais destaque à ação principal.
- [x] Padronizar Menu, Configurações e subdiálogos com cartões e subtítulos.
- [x] Melhorar Aparelhos recentes, Diagnóstico e mensagens simples com hierarquia visual mais clara.
- [x] Preservar pareamento QR, segurança do Keystore, histórico de aparelhos e fluxo de consulta.
- [ ] Compilar `:app:assembleDebug`/`:app:testDebugUnitTest` com Gradle/SDK Android.
- [ ] Testar visualmente em aparelho físico todos os pop-ups revisados.

### Resultado local da alpha.14 (13/10/2026)

- **PASSOU:** `python3 tools/check_distribution.py` após sincronização de versão/documentação.
- **PASSOU:** 16 testes Kotlin/JVM locais (runner compatível construído localmente) para `EventJournal`, `Ipv4Suggestion`, `SensitiveContent`, `CryptoBox` e `PairingCode/PairingSession`.
- **PASSOU:** integridade do ZIP final.
- **NÃO EXECUTADO:** compilação Android completa com Gradle/SDK, geração real de APK/AAB nesta sessão, testes físicos de interface e testes Camera2 em hardware.

## alpha.15: seleção de método, cartões e QR compacto (13/10/2026)

- [x] Código de UI: seleção QR/Manual/Salvos, ações Conectar/Renomear/Esquecer, formulário manual separado.
- [x] Scanner Camera2 em janela flutuante com câmera local e confirmação explícita antes do pareamento.
- [x] Painel com ação Conectar/Reconectar ao lado do título e botão textual colorido Atualizar à direita.
- [ ] Compilação completa e testes instrumentados Android com Gradle/SDK.
- [ ] Testar scanner no Redmi Note 11 Pro+ e em segundo aparelho, incluindo permissões, rotação, cancelamento, captura e confirmação.
- [ ] Verificar layout dos cartões em telas estreitas e fontes ampliadas.

**Validação executada localmente:** `python3 tools/check_distribution.py` passou; 20 fontes Kotlin sem erros sintáticos no parser PSI (arquivos main/test); Manifest XML válido; workflow YAML válido. **Não** equivale a `assembleDebug` nem teste em hardware.

## alpha.16: início Monitorado e compartilhamento manual (13/10/2026)

- [x] Fonte reorganizada: Home, Configurações com Sobre, QR e informações manuais na tela Monitorado e aparelhos recentes compactos.
- [x] Pop-up manual com IP atual e Keystore, chave oculta, `FLAG_SECURE`, cópia marcada sensível (API 33+) e confirmação para compartilhar pelo sistema.
- [x] Fluxo alpha.15 QR/Manual/Salvos e serviço/polling mantidos no código.
- [x] Verificação local de invariantes `tools/check_distribution.py`: 8 grupos aprovados. Parser PSI Kotlin: 25 fontes, nenhum erro sintático. XML e workflow YAML válidos.
- [ ] Compilar `:app:testDebugUnitTest :app:assembleDebug` em CI; **não inferir APK gerado a partir das verificações estáticas**.
- [ ] Testar em aparelho: botão Home com serviço em execução, lista de recentes, cópia, share sheet, ocultação de segredo, aparelho sem Wi-Fi e rotação da tela.

## alpha.17: Home independente e ações compactas (13/10/2026)

- [x] Página inicial Home separada do Monitorado, inclusive offline; fluxo QR/manual preservado.
- [x] Lista de pares na Home mostra último sucesso autenticado real (`RecentDevices.lastSuccessMs`), botão Conectar e engrenagem para Renomear/Esquecer.
- [x] Diálogos Renomear/Esquecer iniciados na Home retornam à Home; outras entradas preservam comportamento anterior.
- [x] Monitorado mantém informações do aparelho à esquerda e três ações compactas à direita quando ativo, ativação/reinício quando parado.
- [x] Home/Voltar não chamam stopService nem apagam chaves.
- [ ] Compilação Gradle/Android completa, instalação e navegação física, temas claro/escuro e fontes grandes.
- [ ] Prova prática com dois celulares da conexão, captura QR, autenticidade do último horário e reentrada do app.


## alpha.18: conexões recebidas e interface compacta (13/10/2026)

- `tools/check_distribution.py`: invariantes de versão, permissão e fluxo de segurança.
- Kotlin PSI: 25+ fontes Kotlin, incluindo `AuthenticatedAccess` e testes, sem falhas sintáticas.
- Teste JVM `AuthenticatedAccessTest`: registro autenticado, expiração, método declarado e observadores (pendente execução pela CI quando não houver Android SDK local).
- Riscos ainda a validar em dois aparelhos: atualização de tela ao primeiro snapshot, latência real de atualização, atualização de estado após 45 s sem consulta, identificação informada pelo controlador, rotação global de credencial.
- Limitação deliberada: sem sessão TCP contínua nem revogação individual; nomes enviados pelo cliente não são atestados.
- Build Android / teste físico: não executados localmente se ferramentas/SDK ausentes.

## alpha.19: painel remoto compacto (13/10/2026)

- [x] Reorganização da tela do aparelho conectado com cabeçalho compacto, IP, método e ações à direita.
- [x] Grade visual com ícones para leituras de bateria e contador de eventos.
- [x] Grade de funções remotas e pop-ups para notificações, cópia de resumo e detalhes técnicos.
- [ ] Compilação Android/Gradle completa e testes físicos de layout, rotação e densidades.
- [ ] Validação visual em aparelhos menores e confirmação de contraste/legibilidade em tema claro/escuro.

## alpha.20: versões, acesso recebido e UX (13/10/2026)

- [x] Adicionado protocolo de versão do aplicativo separadamente da versão `WireProtocol.VERSION`.
- [x] Requisições de pareamento/snapshot/desconexão exigem mesma versão; testes de compatibilidade e desconexão adicionados.
- [x] `IncomingAccessActivity` desenhada com cartões, ícones e ações; aviso imediato enquanto a Activity está visível.
- [x] Notificação Android para acesso autenticado com o aplicativo em segundo plano, sem iniciar Activity automaticamente.
- [x] Serviço permanece ativo ao desconectar controlador; registro removido por mensagem autenticada, com expiração de 45s se o aviso não chegar.
- [x] Grade de Configurações e ajustes compactos do painel remoto; medições antigas marcadas.
- [ ] Compilação Android completa e testes unitários via Gradle/GitHub Actions.
- [ ] Testes em dois aparelhos alpha.20, QR, manual, bloqueio alpha.19↔alpha.20 e acesso/saída com configurações abertas.
- [ ] Verificação física de layouts em diferentes densidades, temas e escala de fonte.

### Execuções locais da alpha.20

- [x] `python3 tools/check_distribution.py`: 12 blocos de verificações aprovados.
- [x] Parser Kotlin PSI: sem erros sintáticos em arquivos de produção e testes Kotlin.
- [x] Teste JVM isolado: versão exata, rejeição da alpha.19, duas conexões, desconexão de uma, nova conexão e expiração de 45s.
- [x] Todos os 14 XML (Manifest e vetores) analisados com parser XML.
- [ ] Compilação Android completa: indisponível no ambiente sem Android SDK/Gradle configurados. Validar no GitHub Actions.

## alpha.21: correção Kotlin do build #21 (13/10/2026)

- [x] Identificada nos logs a falha `:app:compileDebugKotlin` em `IncomingAccessActivity.kt:93` (`Drawable!` onde `Int` era esperado).
- [x] Corrigida a chamada `setBackgroundColor(this@IncomingAccessActivity.background)`.
- [x] Regressão estática adicionada em `tools/check_distribution.py`.
- [x] `versionName/versionCode/AppVersion`, `AppVersionTest` e referências de distribuição sincronizados.
- [ ] Executar `:app:compileDebugKotlin`, `:app:testDebugUnitTest` e `:app:assembleDebug` no GitHub Actions.
- [ ] Testar exibição da tela de acesso e status em dois Androids com alpha.21.

## alpha.22: modularização da interface (13/10/2026)

- [x] Extração de cinco módulos visuais, sem mudanças funcionais no protocolo ou nas permissões.
- [x] Versionamento 22 sincronizado entre Gradle, AppVersion, JUnit e documentação.
- [x] Preservação de único callback de permissões e dos fluxos Home, Monitorado, Controle e QR.
- [x] Validação de sintaxe Kotlin por parser PSI e testes de distribuição de todos os módulos.
- [ ] Build Android completo com Gradle e SDK; dependências não disponíveis localmente.
- [ ] Testes físicos em ambos aparelhos, temas, escala de fonte, QR, credenciais e ciclo de vida.

## alpha.24: controle de conexão e pareamento desacoplados (13/10/2026)

- [x] MainActivity reduzida para ~322 linhas, mantendo callbacks Android e navegação.
- [x] Transferência de estados/cache/I-O da conexão para `RemoteConnectionManager`.
- [x] Separação de renderização do Controle, diálogos manual/QR, credenciais e seleção de apps.
- [x] Ajuste de host selecionado antes da primeira consulta por QR ou manual.
- [x] Kotlin do gerenciador de conexão e do pareamento compilados isoladamente com stubs; troca de aparelho, cancelamento de resposta antiga e cache testados.
- [ ] Compilação Gradle/Android completa e testes físicos em dois aparelhos.
- [ ] Confirmar visual e interações em Android com modos claro/escuro, escala de fonte, QR e navegação.

## alpha.25: correção do build 25 (14/10/2026)

- [x] Lidos logs do workflow 25: `:app:compileDebugKotlin` falhou apenas nos erros registrados em `IncomingAccessScreenUi.kt:113` e `:114` (`LinearLayout` onde `Context` era esperado).
- [x] Corrigidos ambos os construtores qualificando explicitamente o receiver da Activity (`this@infoMetric`).
- [x] Adicionada regressão ao verificador de distribuição; versão 25 sincronizada no Gradle, `AppVersion`, JUnit e documentação.
- [x] Parser PSI: zero erros sintáticos em Kotlin; 14 XML válidos; typecheck Kotlin isolado de `IncomingAccessScreenUi.kt` com stubs mínimos Android/Activity sem erros de tipo.
- [ ] Compilação Android real e testes instrumentados ainda pendentes da CI.
- [ ] Teste físico do alerta de acesso com múltiplos dispositivos.


## alpha.26: painel e telemetria (14/10/2026)

- [x] Gradle, AppVersion e documentação apontam para 0.1.0-alpha.26/versionCode 26.
- [x] Painel remoto reduzido a três cartões com ícones, seis funções e detalhes movidos para Activity própria.
- [x] Recursos futuros distinguíveis dos operacionais; Recuperar dados ainda não implementado.
- [x] Leitores de sistema, armazenamento, telephony e rede integrados no servidor autenticado.
- [x] Número de telefone desligado por padrão, com permissão e autorização explícitas.
- [ ] Compilação Android completa por Gradle/CI.
- [ ] Testes visuais em tema claro/escuro e em telas compactas.
- [ ] Testes físicos de SIM único, dual SIM, sem chip, Android 8-16 e VPN.

## alpha.27: padronização visual (14/10/2026)

- [x] Home, Modo Monitorado, Configurações e Conexão usam cartões e ícones vetoriais comuns.
- [x] Janelas de QR, leitor QR e credenciais seguem a linguagem do painel conectado.
- [x] Fluxos de permissão, pareamento, serviço e revogação não foram intencionalmente modificados.
- [ ] Compilação Android/Gradle completa no GitHub Actions.
- [ ] Teste manual das telas, tema claro/escuro e em aparelhos com diferentes tamanhos.

## alpha.28 (14/10/2026)

- [x] Revisão estática das rotas de avisos e ausência de sobreposição de diálogos da MainActivity.
- [x] Verificação local da classificação de incompatibilidade, EOF, timeout e recusa de conexão em Kotlin/JVM.
- [ ] Compilação Gradle Android completa no GitHub Actions.
- [ ] Testar alpha.28 x alpha.28, alpha.28 x versão antiga, chave alterada e Wi-Fi indisponível em dois aparelhos.
- [ ] Confirmar visual e rolagem de todos os diálogos em telas menores.

## alpha.29 (14/10/2026)

- [x] Analisado workflow 29: falha em `:app:compileDebugKotlin`, `MonitorScreenUi.kt:143–145` (`Expecting a top level declaration`).
- [x] Removido `.create()` extra da tela Acesso autenticado, mantendo única chamada gerenciada de exibição.
- [x] Acrescentada regressão ao `tools/check_distribution.py` e sincronizados versionCode, versão textual e testes.
- [ ] Executar novo workflow GitHub Actions e confirmar `:app:testDebugUnitTest :app:assembleDebug`.
- [ ] Validar visualmente o diálogo de acesso e ações de troca de chave/parada no aparelho.
- [x] Parser Kotlin PSI local (Kotlin 1.9) passou em 51 arquivos `.kt` de produção/testes, sem erros gramaticais.
- [x] Smoke Kotlin/JVM local passou em 6 asserções (AppVersion e classificação de erros).
- [x] `python3 tools/check_distribution.py` passou com as invariantes da alpha.29.
- [ ] Build Android/AGP completo indisponível neste ambiente (sem SDK/Gradle); verificar em CI.

## alpha.30 (14/10/2026)

- [x] `tools/check_distribution.py`: 22 grupos de invariantes, inclusive consentimentos e comandos de mensagem.
- [x] Parser PSI Kotlin: 57 fontes de app/teste, 0 erros sintáticos.
- [x] Kotlin/JVM: 10 verificações reais de filtro de SMS e numeração.
- [x] Sem leitura de inbox no polling normal: somente quando `includeSms=true` no pedido de abertura Mensagens.
- [ ] Compilação Android completa, testes instrumentados e permissões reais ainda pendentes.

- [x] Novos caminhos de SMS e RemoteInput com consentimentos separados, inicialmente falsos, e autenticação obrigatória.
- [x] Mensagens sem coleta oculta, sem RECEIVE_SMS, sem acesso histórico interno de WhatsApp.
- [ ] Build Gradle Android e teste instrumentado em dispositivo real (API 26–36).
- [ ] Conferir permissão hard-restricted READ_SMS no instalador/ROM.
- [ ] Testar resposta WhatsApp com notificação existente, expirando token, revogando lista e cancelando a notificação.
- [ ] Testar SMS com SIM, custo, multi-SIM, mensagens multipartes e confirmação de envio real.

## alpha.31 (14/10/2026)

- [x] Implementação em fonte de RECEIVE_SMS sob autorização local; SMS recentes enviados/recebidos, estados de entrega e seleção de SIM.
- [x] API de notificações de apps elegíveis opt-in, com Copy/Reply e manutenção de filtros.
- [x] `FLAG_SECURE` ausente somente de `RemoteMessagesUi`, presente no QR/credenciais.
- [ ] Compilação real Gradle Android :app:assembleDebug e unit tests Android.
- [ ] Teste SMS em duas SIMs e com mensagens multipart, recebimento bloqueado e permissões negadas.
- [ ] Teste em aparelho de retorno PendingIntent para enviado/entregue e falhas de operadora.
- [ ] Teste MMS em provedores variados (metadados apenas, sem anexos).
- [ ] Teste WhatsApp e outro aplicativo com RemoteInput ativo, expiração e revogação do listener.

## alpha.32 (14/10/2026)

- [x] Verificação estática dos pontos de supervisão TCP e navegação de Configurações em página, incluindo permissão e parada explícita.
- [ ] Build Android `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug` no GitHub Actions.
- [ ] Teste real: alternar Wi-Fi e dados, derrubar listener, conferir backoff e retomada com a mesma chave; verificar que a UI não marca servidor morto como ativo após 15 s.
- [ ] Teste real: remover tarefa, reiniciar aparelho com opção de início automático, e forçar parada nas configurações Android (não deve reiniciar até novo início explícito).
- [ ] Teste de interface: Configurações → Notificações / SMS / Serviço / Diagnóstico / Segurança / Aparência / Sobre, botão voltar, rotação, permissão negada, screenshots SMS e QR.

## alpha.33 (14/10/2026)

- Redesign da UI Mensagens como página dedicada com abas SMS e Notificações; remoção do diálogo antigo e de botões Copiar de tela inteira.
- Defesas adicionais contra SMS OTP em inglês, revalidação do EventJournal e checagem antes de copiar. Testes Kotlin/JVM de reconhecimento com frases PT/EN.
- Validar compilação Gradle/Android no GitHub Actions e UX/scroll/voltar/reconexão em aparelho físico, ainda pendentes.

### Verificações locais executadas na alpha.33

- `python3 tools/check_distribution.py`: aprovado; inclui travas contra retorno ao diálogo antigo e contra remoção de filtros OTP.
- Kotlin PSI: **65 fontes `.kt`, nenhum erro sintático** (incluindo testes).
- Smoke test Kotlin/JVM sem Android SDK: 7 amostras de OTP bloqueadas, 3 mensagens comuns preservadas, 1 teste de app financeiro bloqueado.
- Integridade do ZIP e XML de Manifest conferidos; Gradle/Android completo não compilado neste ambiente.


## alpha.34 (14/10/2026)

- Ver tela (preview 1 fps) usando a autorização local explícita `MediaProjection` do Android, `ScreenShareService` do tipo foreground `mediaProjection`, indicação/ação de parada e frame transitório cifrado no protocolo já autenticado.
- Checkpoints: consentimento por sessão, token único por `createVirtualDisplay`, `onStop`, callback de resize, limite 110 KiB/3 s e sem persistência.
- [ ] **Compilação Android completa** `:app:assembleDebug :app:testDebugUnitTest :app:lintDebug` no GitHub Actions.
- [ ] **Teste físico em dois aparelhos**: consentimento concedido/negado, rotação, bloqueio da tela, parada pela notificação, queda/reconexão Wi-Fi, force-stop, memórias/carga de CPU, frames protegidos por FLAG_SECURE.
- [ ] Validar tela no Redmi/Android 14+ e medir FPS/tráfego, sem assumir vídeo fluido.

## alpha.35 (14/10/2026)

- Testes locais: verificador estrutural; parser Kotlin; validação XML/YAML; testes JVM de RemoteGesturePolicy e integridade CRC do ZIP (resultados no relatório da entrega).
- Pendentes: compilação Android/AGP no GitHub Actions, testes instrumentados de MediaProjection em Android 14+, acesso de Acessibilidade no aparelho, áudio de aplicativos compatíveis, reconexão de rede, Bluetooth, rotação e suspensão de tela.

## alpha.36 (14/10/2026)

- [x] Conferir versão 36 em Gradle, AppVersion, README, CHANGELOG e documentação.
- [x] Incluir teste isolado para cabeçalho JPEG, tamanho de payload e expiração de quadro.
- [x] Adicionar ação manual de print/Foto HD, sem captura automática nem armazenamento no monitorado.
- [x] Preservar coordenadas de toque em `FIT_CENTER` e fornecer saída de tela cheia.
- [ ] Compilar Android com GitHub Actions e testar MediaStore, captura HD, rotação e reconexão em dispositivos reais.


## alpha.37 (14/10/2026)

- [x] Diagnóstico de `:app:compileDebugKotlin` no workflow #37 via logs reais: `RemoteClient.kt:144` (Base64 ausente) e `RemoteScreenActivity.kt:263` (fillViewport inexistente).
- [x] Importação `android.util.Base64` e substituição por `isFillViewport = true`.
- [x] Regressões adicionadas ao `tools/check_distribution.py`; sincronizados Gradle, `AppVersion`, README, CHANGELOG, RELEASE_NOTES, AGENTS e arquitetura.
- [x] `tools/check_distribution.py` aprovado, parser PSI Kotlin com 76 arquivos sem erros sintáticos, 40 XML válidos, YAML do workflow válido e 11 validações Kotlin/JVM de política de captura aprovadas.
- [x] Integridade do arquivo ZIP verificada antes da entrega.
- [ ] Confirmar `:app:compileDebugKotlin`, testes Gradle, geração do APK e funcionalidade em aparelhos reais após publicar código da alpha.37.

## alpha.38 (14/10/2026)

- [x] Política pura de recuperação apenas quando monitoramento e Jobs estão habilitados e serviço Android ausente, com 17 asserções Kotlin/JVM aprovadas no ambiente local.
- [x] `JobService` declarado com `BIND_JOB_SERVICE`, agendamento não persistente após reboot, cancelamento em Parar.
- [x] Verificação na tela aberta, separação entre estado de serviço e porta TCP, diagnóstico de última recuperação. Parser PSI: 80 arquivos Kotlin/zero erros sintáticos; 40 XML válidos, YAML parseado e `tools/check_distribution.py` aprovado.
- [ ] Build Android em GitHub Actions e confirmação do comportamento do agendador em Android 12–16 e MIUI/HyperOS.
- [ ] Confirmar que Doze/force-stop e bloqueio da retomada mantêm as garantias de consentimento.

## alpha.39 (14/10/2026)

- Corrigidas navegação remota e tratamento de WindowInsets conforme falhas observadas nas capturas do usuário.
- Acrescentados testes de navegação global, mapeamento CENTER_CROP, flags de bloqueio e verificação do fluxo consentido.
- Alteração da cadência da prévia JPEG condicionada à presença de visualizador e modo economia; métricas de tempo são estimativas por consulta, não latência ponta a ponta.
- Build Android via Gradle / validação visual nos celulares ainda pendentes. Não substituir por teste PSI.

## alpha.40 (14/10/2026)

- Implementadas codificação/decodificação AVC com MediaCodec (YUV 420), anel limitado em RAM e fallback automático JPEG; nenhuma sessão nova MediaProjection.
- Novo comando WakeScreen com autorização local explícita, proteção contra repetição, cooldown e wake lock limitado; estado de bloqueio seguro continua intacto.
- Verificar no GitHub Actions `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug` e ensaiar codecs de dois modelos reais, falha do codec, reconexão, orientação, latência, áudio, bateria, aquecimento e permissões.
- Nenhuma latência real/FPS foi medida nesta sessão de desenvolvimento.

## alpha.41 (14/10/2026)

- Verificar tela cheia nas orientações retrato/paisagem, barra superior/inferior, clique em cada ação, recortes/notch e status visível sem ocultar toda a imagem.
- Conferir que Voltar/Início/Recentes/Notificações acionam `RemoteClient.touch` com chave AES-GCM e independem do modo Toque; sem Acessibilidade e captura, devem mostrar indisponibilidade.
- Testar Print e Foto HD no MediaStore, áudio, Acender tela apenas após opt-in local, alternância Ajustar/Preencher com coordenadas corretas, rotação e saída da imersão.
- Verificar H.264/JPEG e tempos medidos na tela, comparar latência, bateria e aquecimento em dispositivos físicos. Compilação Android e testes de integração ainda pendentes.

- O indicador de FPS calcula (N-1)/(t_último - t_primeiro) dos últimos até 12 quadros exibidos nos últimos 2 segundos, e zera após 2,5 s sem quadro. O dado não é latência de ponta a ponta.
- Em desenvolvimento: 87 arquivos Kotlin passaram no verificador PSI sem erros sintáticos, XML e YAML válidos, verificador estrutural do projeto passou e 10 verificações JVM do cálculo de FPS passaram com `kotlinc` 1.9. A compilação Android completa e os testes visuais permanecem pendentes.

## alpha.56 (14/10/2026)

- `python3 tools/check_distribution.py`: aprovado, incluindo a regressão nova para margens, lista ADB sem duplicação, seletor JPEG/H.264 e aviso de bloqueio.
- Parser PSI Kotlin local: 107 fontes `.kt` em `app/src`, sem `PsiErrorElement`.
- Kotlin/JVM isolado: 8 métodos das políticas `AdbPolicyTest` e `AdbReconnectPolicyTest` com asserções JUnit compatíveis, aprovados.
- 41 arquivos XML lidos com ElementTree e workflow YAML válido com PyYAML.
- Verificar integridade/CRC do ZIP de código-fonte após empacotar.
- PENDENTE: `:app:testDebugUnitTest :app:assembleDebug` no GitHub Actions; este ambiente não possui wrapper Gradle nem build Android completo. Não foram realizados testes físicos do heartbeat, nomes do fabricante, toque com tela bloqueada nem da latência JPEG/H.264.


## Alpha.60 · 14/10/2026

- Alterações: controle físico de som e lanterna em AES-GCM com lista fechada e opt-in local separado.
- Testes locais: scripts de validação e testes de política JVM (veja relatório final da entrega).
- **Não comprovados**: build Android integral, execução da UI e chamada real de ToneGenerator/Camera2 em aparelhos físicos. Sem APK gerado aqui.

## Alpha.70 (15/10/2026)

- [x] Fonte derivada do ZIP alpha.69; versões, constantes e nomes de distribuição sincronizados para alpha.70.
- [x] Estado dos selos explícito (26 linhas): teste JVM de enumeração/booleano e verificador estrutural.
- [x] Encerramento Camera2 por token: teste de callback/sessão obsoleta adicionado.
- [x] Polling de tela com executor único e invalidação na retomada.
- [x] Testes Kotlin/JVM isolados: 26/26 aprovados (câmera, política de demanda, troca, selos). Parser PSI: 134/134 Kotlin sem erros sintáticos; 46 XML e 2 YAML válidos.
- [ ] GitHub Actions `:app:testDebugUnitTest :app:assembleDebug`; build local completo indisponível sem SDK/Gradle.
- [ ] Testes físicos de duas instâncias de CameraShareActivity, H.264/JPEG e fluidez/consumo.
