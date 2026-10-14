# Monitor Remoto v0.1.0-alpha.39

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
