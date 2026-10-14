# Monitor Remoto v0.1.0-alpha.34

## Principal novidade: Ver tela com consentimento

- **Controlador**: botão Ver tela abre visualização autenticada em página própria. Prévia de 1 imagem/s, sem áudio ou comandos de toque.
- **Monitorado**: Compartilhar tela solicita autorização MediaProjection do Android em cada sessão; serviço foreground notifica continuamente e permite parar. Sem captura furtiva nem retomada após reboot ou force-stop.
- Imagem comprimida até 540 px/110 KiB, quadro efêmero RAM (3 segundos) e resposta protegida por AES-GCM/replay. Rotação tratada por redimensionamento e parada por callback da projeção.
- Compilação Android e testes com aparelhos reais pendentes; versão exata alpha.34 em ambos.

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
- `versionCode 34` e versão exata exigida em ambos os aparelhos.

## Limites e testes pendentes

- O fluxo de SMS pode ser negado por restrições do instalador/Android, ainda que não exista publicação na Play Store.
- MMS não fornece corpo/anexos nem recepção completa; Android pode negar até metadados.
- O receptor detecta SMS no monitorado; o controlador só vê ao atualizar. Estados de entrega são voláteis e podem faltar; código pode sumir após reinício do processo.
- Notificações podem não oferecer resposta; WhatsApp antigo não é lido. Filtro OTP é heurístico, sem cobertura perfeita.
- Compilação Gradle/Android, testes reais, e atualização entre versões ainda dependem do GitHub Actions e aparelhos de teste.
