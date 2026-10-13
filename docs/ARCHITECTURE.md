# Arquitetura (0.1.0-alpha.5)

```text
MONITORADO                                      CONTROLE
NotificationListenerService --> EventJournal --\
Apps autorizados/allowlist -> EventJournal ----> MonitorServer :37841
BatteryReader ---------------------------------/       |
                    SecretVault (Android Keystore)     | AES-256-GCM bidirecional
                                                      |
LAN: 192.168.x.x / VPN Tailscale: 100.x.x.x     RemoteClient -> MainActivity
                                                    (poll 15 s)
```

**Fluxo:** cliente envia `{"v":1,"op":"snapshot","ts":epochMillis}` cifrado num envelope `{n,c}` com nonce 96-bit aleatório. Servidor valida tamanho, autenticação AEAD, versão, operação, tempo ±5 minutos e rejeita nonce já recebido no processo. Servidor responde o snapshot atual com os 30 últimos eventos cifrado pela mesma chave. Não há comando remoto de leitura de arquivos ou acionamento de recursos perigosos.

O FGS `connectedDevice` mantém o socket de escuta durante monitoramento ativo. A permissão `CHANGE_NETWORK_STATE` é declarada para o pré-requisito do tipo FGS; o app não altera configurações de rede. O serviço não depende de localização, Accessibility, root, APIs ocultas ou desbloqueio da tela para consultar as APIs permitidas.

**Limites:** TCP não faz NAT traversal; internet depende da VPN, rede de dispositivos ou infraestrutura futura. O monitor não envia push para o controlador; este consulta quando aberto. OS/fabricantes podem encerrar/restringir o serviço. Testes reais em Android 13–17 são pendentes. Esquemas de sincronização e políticas granulares ainda não estão implementados.

## Distribuição e assinatura

O mesmo APK atende aos modos Monitorado e Controle. A fonte de versionamento é `app/build.gradle.kts`, com `versionName=0.1.0-alpha.5` e `versionCode=5`.

- GitHub Actions compila e testa; copia o `.apk` compilado para `dist/MonitorRemoto-v0.1.0-alpha.5.apk`.
- `upload-artifact` sempre distribui um ZIP; GitHub Releases recebe o **arquivo APK direto**.
- Com quatro Secrets, Gradle gera APK Release assinado e AAB. Sem Secrets, o arquivo é Debug de teste e não preserva necessariamente a assinatura entre execuções.
- Releases são prévias, não substituem a futura revisão da Google Play e nem os ensaios reais.


## Pareamento local por QR (alpha.4)

`PairingCode` valida URLs exclusivamente `monitorremoto://pair` e IPs privados, `PairingSession` guarda código temporário (uso único) por 120 segundos em memória. `MonitorServer` recebe pedidos cifrados com segredo temporário e devolve a chave permanente em resposta AES-GCM; `RemoteClient.pair()` autentica resposta antes de salvar no Android Keystore. Ver `PAIRING_QR.md`.
