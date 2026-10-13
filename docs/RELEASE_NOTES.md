# Monitor Remoto v0.1.0-alpha.7

- Segurança alpha.6 mantida: ao desativar o monitoramento ou remover um app permitido, notificações antigas deixam de ser compartilhadas e são removidas do buffer correspondente.
- Fila do servidor limitada para reduzir consumo excessivo de recursos diante de conexões repetidas.
- Novo fluxo local: gerar QR no Monitorado e escanear no Controle, confirmando o destino e salvando IP + chave protegida.
- QR de uso único válido por até 2 minutos; chave definitiva não é exibida no QR.
- Manual por IP/chave ainda disponível.
- GitHub Releases publica `MonitorRemoto-v0.1.0-alpha.7.apk` diretamente; Artifact de APK aparece apenas como contingência.
- **Atenção:** a alpha.3 foi bloqueada como potencialmente nociva pelo Google Play Protect no dispositivo de teste. Esta atualização não implica liberação pelo Play Protect, e a presença de `NotificationListenerService` ainda requer auditoria/avaliação específica. Não desative o Play Protect.
- Build alpha.6 compilou as fontes Kotlin mas falhou em 3 testes JUnit que chamavam `org.json` mockado; testes corrigidos na alpha.7 com projeção de eventos sem JSON.
- Scanner registrado no Manifest com Activity interna isolada, permissão de câmera solicitada antes de iniciar e tratamento de recusa; causa exata do encerramento físico ainda depende de logcat.
- A compilação Gradle alpha.7 e os testes físicos do QR estão pendentes.
- Para atualizações sem desinstalação, configure os quatro Secrets e **preserve sempre o mesmo certificado de assinatura**. Pode ser necessária migração única da assinatura Debug anterior.

**Correção alpha.5:** resolução da compilação Kotlin do scanner QR (`IntentIntegrator.QR_CODE`) verificada contra a API real. Teste em dispositivo permanece pendente.
