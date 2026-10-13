# Arquitetura (0.1.0-alpha.10)

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

O mesmo APK atende aos modos Monitorado e Controle. A fonte de versionamento é `app/build.gradle.kts`, com `versionName=0.1.0-alpha.10` e `versionCode=10`.

- GitHub Actions compila e testa; copia o `.apk` compilado para `dist/MonitorRemoto-v0.1.0-alpha.10.apk`.
- `upload-artifact` sempre distribui um ZIP; GitHub Releases recebe o **arquivo APK direto**.
- Com quatro Secrets, Gradle gera APK Release assinado e AAB. Sem Secrets, o arquivo é Debug de teste e não preserva necessariamente a assinatura entre execuções.
- Releases são prévias, não substituem a futura revisão da Google Play e nem os ensaios reais.


## Pareamento local por QR (alpha.4)

`PairingCode` valida URLs exclusivamente `monitorremoto://pair` e IPs privados, `PairingSession` guarda código temporário (uso único) por 120 segundos em memória. `MonitorServer` recebe pedidos cifrados com segredo temporário e devolve a chave permanente em resposta AES-GCM; `RemoteClient.pair()` autentica resposta antes de salvar no Android Keystore. Ver `PAIRING_QR.md`.

## Scanner QR alpha.7

`MainActivity` verifica a permissão `CAMERA` antes de iniciar a `QrScanActivity`, Activity explícita não exportada com o tema de captura do ZXing. O scanner não é um serviço persistente, nem transfere imagem para rede; recebe somente o conteúdo textual do QR. A tela de controle salva o modo na recriação pela Activity.

## Permissões independentes (alpha.8)

`MainActivity` usa um único override de `onRequestPermissionsResult`. Os códigos `cameraPermissionRequest` e `notificationPermissionRequest` seguem separados no `when`. Concessão da câmera inicia somente a captura QR; concessão de `POST_NOTIFICATIONS` inicia apenas o serviço solicitado pelo usuário. Negação de qualquer autorização não ativa o outro caminho.

## Scanner QR alpha.9

`MainActivity` pede CAMERA e abre `QrScanActivity` não exportada; Camera2 entrega frames YUV na memória, `QrFrameDecoder` usa somente ZXing Core 3.5.4 e devolve o texto ao `MainActivity` para validação de `PairingCode` e confirmação do usuário. Camera2 é fechada no `onPause()`. Leitor não depende de Google Play Services nem de Activity de terceiros. Texto do QR pode ser inserido manualmente como fallback, sempre sujeito às validações anteriores.

## Interface e lista de aparelhos alpha.10

`ui/MainActivity.kt` mantém Views Android sem bibliotecas adicionais. Tema claro é padrão; modo do tema fica em `SettingsStore` e é aplicado por `Activity.setTheme` antes de `onCreate`, com barras do sistema compatíveis. `core/Ipv4Suggestion.kt` extrai somente octetos completos cobertos pelo prefixo CIDR; apenas interface Wi-Fi ativa é usada para sugerir endereços e nunca há descoberta de hosts pela rede.

`core/RecentDevices.kt` armazena até cinco tuplas locais `(host, apelido, último sucesso)` em SharedPreferences, sem eventos sensíveis. O segredo individual é cifrado pelo Android Keystore em `SecretVault.profileKeyName(host)` com SHA-256 do host normalizado. Ao abrir a alpha.10, uma única vez é feita a migração local do `CONTROLLER_KEY` legado/host salvo, sem reemitir segredos. Um novo host **não** herda automaticamente a chave do anterior. Esquecer um aparelho apaga sua credencial cifrada; o protocolo WireProtocol e o esquema de chave de monitorado não mudam.
