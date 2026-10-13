# Monitor Remoto v0.1.0-alpha.6

- Segurança alpha.6: ao desativar o monitoramento ou remover um app permitido, notificações antigas deixam de ser compartilhadas e são removidas do buffer correspondente.
- Fila do servidor limitada para reduzir consumo excessivo de recursos diante de conexões repetidas.
- Novo fluxo local: gerar QR no Monitorado e escanear no Controle, confirmando o destino e salvando IP + chave protegida.
- QR de uso único válido por até 2 minutos; chave definitiva não é exibida no QR.
- Manual por IP/chave ainda disponível.
- GitHub Releases publica `MonitorRemoto-v0.1.0-alpha.6.apk` diretamente; Artifact de APK aparece apenas como contingência.
- **Atenção:** a alpha.3 foi bloqueada como potencialmente nociva pelo Google Play Protect no dispositivo de teste. Esta atualização não implica liberação pelo Play Protect, e a presença de `NotificationListenerService` ainda requer auditoria/avaliação específica. Não desative o Play Protect.
- A compilação Gradle no GitHub Actions e os testes físicos do QR estão pendentes.

**Correção alpha.5:** resolução da compilação Kotlin do scanner QR (`IntentIntegrator.QR_CODE`) verificada contra a API real. Teste em dispositivo permanece pendente.
