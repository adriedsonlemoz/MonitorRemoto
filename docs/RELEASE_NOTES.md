# Monitor Remoto v0.1.0-alpha.64

## Câmera JPEG: menos consumo e diagnóstico de transmissão

- A câmera compartilhada só comprime quadros quando há consultas recentes e autenticadas de um controlador. A prévia local permanece visível e fecha ao sair.
- A mesma imagem não é retransmitida se o controlador já a recebeu; a atualização ocorre quando um novo JPEG válido fica disponível.
- Em **Câmeras**, o status mostra o tempo da consulta, tamanho do JPEG e intervalo entre quadros novos. Esses números são medições do app, não garantia de FPS ou latência ponta a ponta.
- Trocar frontal/traseira continua sendo uma ação local no telefone monitorado. Sem gravação, microfone, captura oculta ou permissões novas.
- **Pendente:** compilar com Gradle/SDK em CI e validar em dois aparelhos (troca/rotação, câmera ocupada, Wi-Fi ruim, bateria e retomada). Ambos devem rodar alpha.64.

---

# Monitor Remoto v0.1.0-alpha.63

## Câmera JPEG: orientação e estabilidade

- A imagem transmitida é girada no monitorado para respeitar a orientação Camera2 e rotação da tela. A correção transforma Y e VU da imagem NV21 antes de gerar JPEG; nada é inventado na imagem.
- Quadros de sessões anteriores são rejeitados, inclusive se callbacks chegarem depois de uma troca de câmera. Fechar o serviço revoga a memória da câmera.
- Falhas de abertura/desconexão liberam ImageReader, sessão e superfícies, permitindo uma nova tentativa local.
- O início permanece **exclusivamente local e visível** no aparelho monitorado. Sem gravação de vídeos, áudio ou câmera em segundo plano; nenhuma permissão nova.
- **Pendente:** compilar via Gradle/CI e testar câmera frontal/traseira, rotação, interrupção e desempenho nos aparelhos reais. Ambos os aparelhos exigem alpha.63.

---

# Monitor Remoto v0.1.0-alpha.62

## Câmera remota JPEG com consentimento no monitorado

- Inicie localmente **Modo monitorado > Abrir câmera neste aparelho**. A câmera abre somente com tela visível e permissão Android CAMERA. Voltar/pausar fecha e apaga quadros temporários.
- Controlador: cartão **Câmeras**, visualização JPEG de até 1 quadro/s, indicador de sessão ativa e foto salva por ação manual; troca frontal/traseira é feita localmente no monitorado.
- Rede e autenticação do aplicativo preservadas, com nova consulta de leitura `cameraFrame` limitada. Sem gravação, áudio, abertura silenciosa, ADB arbitrário ou novo FGS de câmera.
- **Pendente**: build APK completo no CI e testes Android reais; API câmera, rotação, bloqueio de tela, fabricantes, redes e desempenho variam.

---

# Monitor Remoto v0.1.0-alpha.61

## Bloqueio remoto com duplo consentimento

- Cartão **Bloquear** e confirmação digitada BLOQUEAR no controlador, sem execução por toque acidental.
- Autorização independente no monitorado, desligada por padrão, além da ativação manual do administrador pelo Android.
- Somente política `force-lock`, sem reset de senhas, exclusão de dados ou ADB arbitrário.
- Recusa quando não há PIN/padrão/senha ou administrador ativado; intervalo mínimo de 15 s. Resposta informa aceitação, sem alegar confirmação do bloqueio físico.
- Preservados Tocar som, Lanterna, bateria e demais recursos. Validação estrutural/JVM local disponível; build e ensaios Android reais pendentes.

---

# Monitor Remoto v0.1.0-alpha.60

## Tocar som e Lanterna

- Dois novos cartões no painel remoto, com pop-ups compactos e confirmação do status obtido do aparelho monitorado.
- Em **Configurações > Som e lanterna**, no telefone monitorado, ative separadamente as permissões locais. Por padrão ambas estão desativadas.
- **Tocar som**: escolher 5, 10, 20 ou 30 segundos, Iniciar som / Interromper, autoencerramento. Respeita o volume do canal alarme e as políticas do sistema; a tentativa iniciada não é prova de que ficou audível.
- **Lanterna**: ligar/desligar o flash traseiro via Camera2, com atualização de estado real por TorchCallback. Flash ocupado, ausência de hardware ou permissão Câmera sem concessão podem bloquear. `Desconhecida` significa que o Android ainda não confirmou o estado.
- Ações utilizam somente a rede local autenticada AES-GCM e mesmo versionName. Parar o serviço interrompe o som e tenta desligar somente a lanterna ligada por ele. Nenhum comando ADB livre foi criado.
- APK/compilação Android completos e testes com dois celulares permanecem pendentes; este ZIP contém código-fonte.

---

# Monitor Remoto v0.1.0-alpha.59

## Cartão de bateria do celular remoto

- No painel remoto, **Bateria** mostra percentual, estado real e origem elétrica quando informada, por exemplo `72% ⚡ / Carregando · USB`.
- `Descarregando`, `Cheia`, `Não carregando` e `Desconhecido` correspondem a estados distintos do Android, sem atribuição automática de descarga quando não há leitura.
- São exibidos somente os dados do broadcast `ACTION_BATTERY_CHANGED` enviado pelo monitorado na consulta autenticada já existente. Temperatura e Rede seguem no painel. Detalhes inclui Alimentação.
- Nenhuma nova permissão, código de controle remoto, câmera ou ADB. **Tocar som e Lanterna não implementados nesta versão**, aguardando controles locais e testes seguros.
- Ambos os aparelhos devem usar exatamente alpha.59. Compilação Android e testes físicos são pendentes até serem executados.

---

### Alpha.58 · notificações e aplicativos

- Tela de Notificações redesenhada e diagnóstico para eventos ausentes.
- Captura de notificações ainda ativas quando o listener se reconecta; sem persistência do conteúdo.
- Nova lista de aplicativos com acesso autorizado no monitorado, disponível sob demanda e sem alterações remotas.
- Build completo e validação em aparelhos pendentes.

# Monitor Remoto v0.1.0-alpha.59

## Gerenciador remoto SAF e manutenção

- Novo layout de Recuperar dados: topo compacto, Atualizar/Ajuda, navegação em pastas, ícones, tamanho e data, alternância lista/grade, busca, ordenação e informações.
- Seleção múltipla com cópia de até 20 arquivos (128 MiB total, 32 MiB por arquivo) para pasta autorizada pelo controlador. Origem permanece somente leitura; blocos autenticados com integridade e tentativa de limpeza de parcial em erro.
- Manutenção reorganizada com visual consistente e alerta de risco. Reinicialização normal ADB continua exigindo confirmação digitada; sem desligamento remoto.
- Mensagens ADB não configurado mais orientativas.
- Build Android completo e teste físico SAF/ADB continuam pendentes.

---

# Monitor Remoto v0.1.0-alpha.56

## Ajustes de interface e diagnóstico ADB

- Ações ADB com margens consistentes, pares compactos, sem duplicar o dispositivo ativo na lista de históricos.
- Renomear aparelho na área conectada e no pop-up de controles; modelo anterior e apelido são preservados quando o diagnóstico de propriedades não traz um nome legível.
- Lista de aparelhos salvos só é reconstruída quando há mudanças, não a cada verificação periódica.
- Heartbeat e comandos ADB serializados; janela de timeout menos agressiva para evitar quedas falsas.
- Ver tela com seletor estilizado de JPEG e H.264 e avisos específicos para tela bloqueada/apagada, projeção encerrada e Acessibilidade.
- Nenhum bypass de bloqueio, novo comando ADB arbitrário ou tentativa de reativar depuração desabilitada.
- Build Android/CI e testes de duas telas reais ainda pendentes.

---

# Monitor Remoto v0.1.0-alpha.55

## Correção dos testes ADB do workflow 53 (alpha.55)

- Atualizado `AdbPolicyTest.commandAllowlist`: a política ADB contém atualmente 7 comandos, não mais 4.
- O teste confere a lista fechada de comandos, os comandos shell esperados e palavras-chave proibidas; não adiciona novas permissões ou comandos.
- Preservados manutenção com reinício normal `reboot:` confirmado, reconexão ADB, recuperação de arquivos, vídeos e interface das versões anteriores.
- Build Android completo e testes em aparelhos físicos continuam pendentes.

## Correção de compilação no workflow 52 (alpha.54)

- Resolvida a referência indefinida `lastRecoveryAttemptMs` em `MainActivity.kt`, mantendo o controle de frequência da retomada conservadora do serviço previamente autorizado.
- Nenhuma alteração ao ADB, permissões, reinicialização ou acesso aos arquivos. Mantidas todas as funcionalidades da alpha.53.
- Compilação Android completa e testes físicos da alpha.54 pendentes.

## Manutenção e recuperação visual (alpha.53)

- Manutenção no painel de funções remotas, pop-up apenas com Reiniciar via ADB. Proteção por confirmação digitada e aviso para aparelho sem botão Power; comando normal `reboot:`.
- Removida abertura automática da tela de acesso remoto. Notificação e painel continuam informando acessos autorizados; detalhes têm Voltar.
- Recuperar dados agora inclui Voltar visível, Tentar novamente e instruções para liberar a pasta no monitorado.
- Testes reais e compilação Android da versão ainda pendentes.

## Integração ADB com Ver tela (alpha.52)

- Botão **Ver tela remota • controle ADB** após uma conexão ADB ativa. A imagem exige o pareamento normal e consentimento MediaProjection no aparelho monitorado.
- Indicador no visualizador: **Navegação ADB conectada / Imagem Monitor Remoto (MediaProjection)**; sem confundir os transportes.
- Voltar/Início/Recentes usam ADB durante a sessão em primeiro plano após autenticação da visualização. Os demais controles permanecem no canal AES-GCM normal.
- A ponte de ADB é encerrada ao sair do visualizador. Não há captura por ADB nem sessão oculta.
- Compilação Android CI e ensaio real em dois aparelhos pendentes.


## Reconexão ADB quando a sessão cair (alpha.51)

- Reconexão automática enquanto a Activity do modo ADB estiver visível, com espera crescente e redescoberta mDNS somente no IP anteriormente escolhido.
- Verificação periódica da resposta real do ADB; indicação de reconectando, orientação caso a Depuração sem fios esteja desligada e opção Cancelar reconexão.
- Atalho Comandos ADB no resumo da conexão, incluindo navegação e volume; não adiciona captura de tela, streaming ADB nem controle em segundo plano.
- O Android continua exigindo autorização manual para Depuração sem fios. A recuperação não liga esse recurso e não remove PIN/biometria.
- Build Android/Gradle completo e testes em aparelhos reais pendentes.


## Novo fluxo ADB compacto (alpha.50)

- Tela principal organizada em aparelhos salvos e botão Reconectar. Opções técnicas ficam recolhidas.
- Seleção de papéis, método QR/código e ajuda em janelas centralizadas e compactas.
- Após o QR ou código, a conexão TLS é procurada automaticamente pelo IP do dispositivo pareado; quando o mDNS falha, o usuário pode informar a porta manualmente.
- Controles de navegação ficaram em Configurações avançadas e não ocupam a tela principal.
- Reconexão tenta porta salva e nova descoberta no mesmo IP; identificação do aparelho muda apenas com confirmação quando há divergência.
- Mantida proteção de QR, consentimento Android, ADB experimental e isolamento do modo MediaProjection.

---

# Monitor Remoto v0.1.0-alpha.46

## Correção de compilação do workflow 45 (alpha.46)

- Resolvidos dois erros de tipo em `RemoteFilesActivity.kt`, linhas 59 e 66 da versão anterior.
- `TextView.apply` e `Button.apply` agora recebem o `Int` da cor da Activity em `setTextColor`, em vez de `View.foreground: Drawable`.
- Preservados recuperação SAF, criptografia, limites, consentimento, QR/ADB e modos de tela.
- Compilação Android e testes em aparelhos ainda aguardam execução para a alpha.46.

## Histórico da versão anterior (alpha.45)

## Recuperação de dados por pasta autorizada (alpha.45)

- **Monitorado:** configurações de Arquivos e recuperação permitem selecionar/revogar pasta via seletor Android, somente leitura e desativado até autorização local.
- **Controlador:** botão Recuperar dados navega no conteúdo da pasta permitida e salva arquivos individuais no local escolhido pelo usuário.
- Transferência segmentada cifrada/autenticada, hash SHA-256 por bloco, progresso, cancelamento e limites de 32 MiB por arquivo, 150 nomes por pasta e 12 níveis.
- Não há recuperação de arquivos apagados nem leitura de áreas privadas de aplicativos; não há novos privilégios.
- Ainda requer compilação Gradle/CI e testes de dois aparelhos.

## Histórico: correção de compilação do modo avançado ADB (alpha.44)

- Corrigidas as quatro referências inválidas `pair` e `execute` da alpha.43 com parâmetro nomeado `operation` em `runOperation`.
- Mantidos guia ADB/QR, captura protegida somente com QR visível, JPEG inicial e AVC opcional da alpha.43.
- Workflow 43 parou em `:app:compileDebugKotlin`; a compilação e testes da alpha.44 ainda dependem de nova execução do CI.

## Registro da versão anterior (alpha.43)


## Ajustes de pareamento ADB e transmissão de tela (alpha.43)

- Capturas de tela liberadas no modo ADB quando não há QR com segredo visível; QR ainda é protegido.
- Guia de pareamento dentro da tela, com papéis dos dois telefones, IP e portas; indicação do modelo informado pelo Android após conectar.
- JPEG passa a ser modo inicial de Ver tela, H.264 experimental pode ser selecionado; fila AVC com atraso descarta quadros antigos e pede nova sincronização. A codificação AVC no monitorado deixa de consumir CPU após 2 segundos sem solicitações específicas de vídeo.
- Ainda pendente: medições em dois celulares e build Android da alpha.43.

## Modo avançado ADB por Wi-Fi (alpha.42)

Configurações → Modo avançado ADB oferece QR de 2 minutos, pareamento por código, descoberta mDNS, IP/portas manuais, conexão TLS, diagnóstico de modelo/Android e comandos Voltar/Início/Recentes com confirmação.
A Depuração sem fios deve ser habilitada manualmente no monitorado (Android 11+ ou ROM compatível). O controlador continua Android 8+. O QR é do protocolo ADB (`WIFI:T:ADB`), distinto do QR normal do Monitor Remoto.
A identidade RSA é protegida pelo cofre AES-GCM/Android Keystore. Não é preciso Termux/PC em tempo de uso. Ao sair da tela, a conexão é encerrada; a identidade permanece. Portas não são persistidas e devem ser redescobertas ou informadas novamente.
Esta versão **não implementa espelhamento via ADB, tela física apagada via ADB, desbloqueio, reconexão em segundo plano ou shell livre**. Leia [arquitetura, dependências e testes ADB](ADB.md). Validação física permanece pendente.

## Controles transparentes no modo tela cheia (alpha.41)

- Barra superior flutuante: Sair, Ajustar/Preencher, Print, Ouvir áudio e Foto HD.
- Barra inferior flutuante: Voltar, Início, Recentes, Notificações, Toque e Acender tela.
- Os botões permanecem visíveis e discretos, com leitura acessível; fora da faixa de controles os toques continuam na imagem.
- Indicador discreto do estado real do streaming, rede e FPS de quadros exibidos; respeito ao recorte da tela; opção de tela cheia preservada ao recriar a Activity.
- Mecanismos existentes de autenticação e autorização preservados. FPS/latência em aparelho e compilação Gradle pendentes naquela entrega. v0.1.0-alpha.41 / versionCode 41.

## AVC/H.264 experimental, JPEG reserva e comando Acender tela (alpha.40)

- MediaCodec no monitorado e controlador, ring limitado de amostras H.264, alvo 10 FPS não garantido; JPEG continua quando codec for indisponível.
- Acender tela após permissão local explícita no monitorado, com limitação temporal. Não desbloqueia PIN nem contorna o Android; talvez não funcione em toda ROM.
- Captura única MediaProjection aprovada e notificação visível permanecem obrigatórias. Nenhum áudio do microfone ou gravação de vídeo.
- Compilação Android real e desempenho físico não verificados nesta entrega.
- Código-fonte versionado 0.1.0-alpha.40, versionCode 40; instalar a mesma versão nos dois celulares.

## Interface, comandos de navegação e prévia mais responsiva (alpha.39)

- Voltar, Início, Recentes e Notificações funcionam independentemente do modo de toque na imagem (sempre com Acessibilidade autorizada).
- Corrigido topo sob status bar pelo tratamento de WindowInsets; aprimorado fullscreen imersivo, com opções Ajustar/Preencher e preservação das coordenadas.
- Prévia JPEG com alvo aproximado de 3 FPS e RTT de rede / idade do quadro expostos; modo de economia reduz cadência. Nenhum H.264 ou FPS garantido.
- Estados de tela apagada e bloqueada informados; nenhuma tentativa de desbloquear ou ocultar autorização. Testes Android/visual em aparelhos físicos pendentes.
- Versão 0.1.0-alpha.39, versionCode 39; usar em ambos os aparelhos.

## Recuperação de serviço (alpha.38)

- `JobScheduler` opcional como segunda camada de recuperação do serviço no monitorado, checagem inexata a cada >= 15 minutos e ação de notificação se o Android bloquear a retomada no plano de fundo.
- Tela Monitorado também reavalia periodicamente serviço Android ausente e registra diagnóstico local de recuperações. Portas TCP continuam sob supervisor de 15 segundos.
- Parada explícita cancela o agendamento e nenhum Job reinicia captura de tela/áudio; exige consentimento local para novas sessões.

## Correção de compilação (alpha.37)

- Corrigidas duas referências que impediram `:app:compileDebugKotlin` no workflow #37: importação ausente de `android.util.Base64` em `RemoteClient.kt` e propriedade `fillViewport` renomeada para `isFillViewport` em `RemoteScreenActivity.kt`.
- Funções de Print, Foto HD, Tela cheia, áudio e toque remoto preservadas, com seus controles e consentimentos.
- `versionCode 38` e compatibilidade exata da versão mantida entre controlador e monitorado. Build Android completo e teste físico continuam pendentes.


## Novidades: Print, Foto HD e Tela cheia (alpha.36)

- Novo visualizador alinhado aos conceitos fornecidos, com ações compactas e tema claro/escuro.
- **Print** salva o quadro recente no controlador. **Foto HD** solicita um quadro mais nítido, exclusivamente durante projeção consentida; não garante imagem nítida quando o conteúdo é protegido ou a rede está ruim.
- Salvar em Imagens/Monitor Remoto (Android 10+) ou seletor de documentos (Android 8-9).
- Tela cheia imersiva reversível, preserva proporção e mapeamento do toque remoto.
- Código-fonte disponível. Compilação Android e testes nos aparelhos ainda pendentes.

## Principal novidade: controle por toque, áudio interno e reconexões

- `Ver tela` agora solicita a autorização de forma visível no monitorado quando não existe sessão de captura, mas o usuário ainda precisa confirmar no Android.
- A sessão MediaProjection autorizada pode sobreviver a desconexões do controlador. Parar, reiniciar ou expirar pelo Android exige autorização nova.
- Toques e gestos (tap/swipe/Voltar/Início) por serviço de acessibilidade manualmente habilitado e opção local separada; áudio interno por AudioPlaybackCapture opcional, Android 10+ e RECORD_AUDIO. Sem microfone, gravação ou chamadas protegidas.
- Controlador apresenta botões compactos e áudio PCM 16 kHz mono experimental e JPEG de ~1-2 quadros/s sob demanda, sem garantia de fluidez. Testes Android/compilação completa pendentes.

## Histórico: Ver tela com consentimento (alpha.34)

- **Controlador**: botão Ver tela abre visualização autenticada em página própria. Prévia original da alpha.34: 1 imagem/s, sem áudio ou comandos de toque; alpha.35 inclui ambos opcionalmente.
- **Monitorado**: Compartilhar tela solicita autorização MediaProjection do Android em cada sessão; serviço foreground notifica continuamente e permite parar. Sem captura furtiva nem retomada após reboot ou force-stop.
- Imagem comprimida até 540 px/110 KiB, quadro efêmero RAM (3 segundos) e resposta protegida por AES-GCM/replay. Rotação tratada por redimensionamento e parada por callback da projeção.
- Compilação Android e testes com aparelhos reais pendentes; versão exata alpha.35 em ambos.

## Central de mensagens (alpha.33)

- Mensagens em tela própria, abas SMS e Notificações, ações compactas Copiar e Responder, controle de volta/atualização.
- Filtros de autenticação multilíngues corrigidos e aplicados também na leitura dos registros antes da transmissão. Mensagens não ficam gravadas no aparelho controlador.
- Compatibilidade de respostas continua condicionada ao RemoteInput do app/à permissão SMS no monitorado.

## Implementado no código-fonte

- Configurações em módulo dedicado, sem diálogo único: categorias Notificações, SMS e telefonia, Serviço e recuperação, Diagnóstico, Segurança, Aparência e Sobre. Navegação Voltar entre páginas e retorno à tela anterior.
- Supervisor TCP do serviço foreground, verificação do listener real a cada 15 s, recriação do socket em falhas com backoff, verificação após retorno de rede e diagnóstico de tentativas.
- Mantidos START_STICKY, controle de parada explícita, chave existente, sessões cifradas e filtragem de mensagens; após force-stop o Android ainda exige ação do usuário.


- Permissão explícita para SMS em tempo real (`RECEIVE_SMS`), com buffer RAM, exibido na atualização manual.
- Consulta sob demanda de até 60 SMS (recebidos/enviados), status efêmero de envio e entrega via callbacks do sistema, e seleção de chip se houver READ_PHONE_STATE.
- Consulta opcional de metadados MMS (assunto/data quando provider concede), sem anexos e sem se declarar app padrão de SMS.
- Opção explícita para notificações de todos os aplicativos elegíveis ou lista individual. Filtros de aplicativos financeiros, notificações secretas e códigos suspeitos continuam.
- Botões **Copiar** e **Responder** nas telas de SMS/notificações. Responder via `RemoteInput` ou SMS com revisão/confirmacão e autorização separadas.
- Remoção de `FLAG_SECURE` somente dos diálogos de Mensagens e respostas, para capturas de tela. QR e credenciais continuam protegidos.
- `versionCode 36` e versão exata exigida em ambos os aparelhos.

## Limites e testes pendentes

- O fluxo de SMS pode ser negado por restrições do instalador/Android, ainda que não exista publicação na Play Store.
- MMS não fornece corpo/anexos nem recepção completa; Android pode negar até metadados.
- O receptor detecta SMS no monitorado; o controlador só vê ao atualizar. Estados de entrega são voláteis e podem faltar; código pode sumir após reinício do processo.
- Notificações podem não oferecer resposta; WhatsApp antigo não é lido. Filtro OTP é heurístico, sem cobertura perfeita.
- Compilação Gradle/Android, testes reais, e atualização entre versões ainda dependem do GitHub Actions e aparelhos de teste.
