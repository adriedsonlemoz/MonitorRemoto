# Monitor Remoto v0.1.0-alpha.45

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
