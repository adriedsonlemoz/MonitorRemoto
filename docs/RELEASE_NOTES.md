# Monitor Remoto v0.1.0-alpha.30

## Funcionalidades implementadas no código-fonte

- Central **Mensagens** com notificações autorizadas e até 30 SMS recebidos consultados sob demanda, com filtros de dados sensíveis.
- Respostas WhatsApp e de outros apps somente usando a ação de resposta ativa da notificação (`RemoteInput`). Não lê conversas históricas.
- Respostas SMS por `SmsManager`, somente a número regular já recebido, com confirmação no controlador e limite de taxa; não comprova entrega.
- Controles distintos no monitorado para habilitar **SMS recebido**, **resposta SMS** e **resposta de notificações**, desativados por padrão; sem ação furtiva.
- Criptografia/autenticação AES-GCM, proteção contra replay e versão idêntica entre os aparelhos.

## Limitações

- `READ_SMS` é hard restricted em diversas versões do Android; a instalação fora do Google Play **não garante** que a permissão seja concedida. O app não contorna essa restrição.
- A resposta a notificação expira quando o Android/app cancela a notificação ou expira o token; o recebimento pelo destinatário não é confirmado.
- Conteúdo financeiro/OTP é filtrado em melhor esforço. SMS podem custar dinheiro e exigir SIM habilitado.
- Requer build CI e testes reais; este arquivo é código-fonte, não APK.
