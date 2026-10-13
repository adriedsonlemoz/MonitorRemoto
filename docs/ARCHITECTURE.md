# Arquitetura (alpha.1)

```text
MONITORADO                                      CONTROLE
NotificationListenerService --> EventJournal --\
SmsReceiver -----------------> EventJournal ----> MonitorServer :37841
BatteryReader ---------------------------------/       |
                    SecretVault (Android Keystore)     | AES-256-GCM bidirecional
                                                      |
LAN: 192.168.x.x / VPN Tailscale: 100.x.x.x     RemoteClient -> MainActivity
                                                    (poll 15 s)
```

**Fluxo:** cliente envia `{"v":1,"op":"snapshot","ts":epochMillis}` cifrado num envelope `{n,c}` com nonce 96-bit aleatório. Servidor valida tamanho, autenticação AEAD, versão, operação, tempo ±5 minutos e rejeita nonce já recebido no processo. Servidor responde o snapshot atual com os 30 últimos eventos cifrado pela mesma chave. Não há comando remoto de leitura de arquivos ou acionamento de recursos perigosos.

O FGS `connectedDevice` mantém o socket de escuta durante monitoramento ativo. A permissão `CHANGE_NETWORK_STATE` é declarada para o pré-requisito do tipo FGS; o app não altera configurações de rede. O serviço não depende de localização, Accessibility, root, APIs ocultas ou desbloqueio da tela para consultar as APIs permitidas.

**Limites:** TCP não faz NAT traversal; internet depende da VPN, rede de dispositivos ou infraestrutura futura. O monitor não envia push para o controlador; este consulta quando aberto. OS/fabricantes podem encerrar/restringir o serviço. Testes reais em Android 13–17 são pendentes. Esquemas de sincronização e políticas granulares ainda não estão implementados.
