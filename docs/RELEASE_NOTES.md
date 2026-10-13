# Monitor Remoto v0.1.0-alpha.3

Prévia para **testes privados**, não uma versão validada para publicação na Google Play.

- Workflow nomeia o binário como `MonitorRemoto-v0.1.0-alpha.3.apk` e anexa o APK **diretamente** à GitHub Release, para evitar o ZIP que a página de Artifacts entrega.
- Continuam disponíveis os Artifacts em ZIP como reserva, testes unitários e verificação SHA-256 do APK.
- Preparado fluxo opcional de APK Release assinado e AAB para Play Store mediante quatro Secrets de assinatura.
- Preserva a conexão Wi-Fi, telemetria da bateria e leitura autorizada de notificações da alpha.2.

## Atenção à assinatura

Sem os quatro Secrets de assinatura, o arquivo é **Debug**, assinado com uma chave temporária do runner GitHub. Os builds resultantes podem não instalar como atualização sobre versões anteriores; faça backup de eventuais dados antes de reinstalar. Nunca envie segredos ao repositório.
