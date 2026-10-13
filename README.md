# Monitor Remoto

**v0.1.0-alpha.2 / versionCode 2** · Android 8.0+ · Kotlin 2.2.21 · AGP 8.13.2 · Gradle 8.13

Dois modos no mesmo APK: **Monitorado** (servidor) e **Controle** (cliente).

## Recursos já implementados no código-fonte

- Snapshot de nível da bateria, carregamento, temperatura, tensão, corrente instantânea e média (quando o hardware/Android fornecem as propriedades).
- Captura de notificações **novas** autorizadas pelo usuário por `NotificationListenerService`. Não lê automaticamente o histórico de WhatsApp.
- Captura opcional de notificações de aplicativos selecionados. Não possui permissão de SMS direto.
- Filtro por conteúdo e bloqueio preventivo de nomes/pacotes financeiros conhecidos (não são infalíveis; o Android ainda concede acesso geral ao serviço).
- Histórico volátil limitado a 100 eventos no aparelho monitorado; mostra os últimos 30. Nada salvo em banco ou nuvem.
- Servidor TCP autenticado com chave de 256 bits e payload protegido por AES-256-GCM, timeout, nonce aleatório, janela temporal de 5 min e cache de rejeição de repetição por nonce.
- Segredos armazenados protegidos por Android Keystore no aparelho.
- Cliente com atualização ao abrir e polling de 15 segundos enquanto a tela do Controle estiver aberta.
- Conexão manual por IPv4/nome na mesma LAN ou entre redes por VPN privada (Tailscale/WireGuard). Porta **37841/TCP**.
- Notificação persistente do serviço foreground `connectedDevice`; parar por UI ou pela ação na notificação.
- Inicialização após reboot opcional (tentativa, sujeita a política do SO/fabricante).

## Como utilizar

1. Gere o APK no GitHub Actions (workflow `Gerar APK`, Artifact `Monitor-Remoto-alpha2-debug`). Instale o **mesmo APK** nos dois aparelhos.
2. No telefone secundário: abra modo **Monitorado**; autorize a notificação de serviço; ative o monitoramento; autorize notificações em Configurações. Selecione explicitamente os aplicativos cujas notificações deseja compartilhar.
3. Na tela Monitorado, veja os IPs disponíveis e toque em **Mostrar/copiar chave de pareamento**. Guarde essa chave em local seguro.
4. No telefone principal: modo **Controle**; preencha o IPv4 da mesma rede, cole a chave e toque **Salvar acesso**.
5. Na internet: configure **Tailscale/WireGuard** em ambos, conecte-os à mesma rede privada VPN e, no Controle, substitua o IP Wi-Fi pelo IP/nome acessível na VPN. Não abra a porta no roteador à internet pública.
6. Com o secundário bloqueado, consulte os dados no principal. Depois de desligar/reiniciar, o serviço pode precisar ser reiniciado dependendo do Android, ROM, economia de bateria e permissões.

**Nota:** a versão alpha usa IP/hostname manual, não faz descoberta automática de aparelhos nem pareamento por QR. O segundo aparelho requer preparação inicial presencial. O app não acessa dados sem consentimento/permissões. A notificação de monitoramento permanece visível.

## Considerações importantes

- **Google Play**: permissões SMS têm política extremamente restrita. A permissão `RECEIVE_SMS` foi removida. SMS novos só podem ser exibidos **quando aparecem em notificações do app de mensagens autorizado**. Não há leitura de caixa de entrada SMS, recuperação de mensagens ocultas nem garantia de aprovação na Play Store.
- **WhatsApp**: apenas texto das notificações que o Android oferece ao listener. Mensagens ocultas, silenciosas, apagadas, protegidas ou históricas não são recuperadas. É possível que o sistema oculte dados sensíveis.
- **Dados sensíveis**: mensagens e OTP podem ser exibidos caso o filtro não os identifique. A chave permite acesso a todos os últimos eventos; proteja e revogue a chave quando necessário.
- **Privacidade**: código não inclui nuvem nem telemetria. A VPN (se utilizada) é um serviço externo e tem políticas próprias.
- **Precisão da bateria**: a corrente `BATTERY_PROPERTY_CURRENT_NOW` é líquida e pode ter sinal ou unidade incorreta em alguns fabricantes. Não confundir com corrente no carregador. Medidas indisponíveis aparecem como indisponíveis.
- **Rede**: comunicação cifrada na aplicação mesmo em LAN, porém o endereço IP/porta ainda é observável. Não há TLS/mTLS. Sem firewall de rede, um atacante na mesma LAN pode tentar sobrecarregar o serviço; limite conexões e evite redes públicas.
- **Android 17**: como `targetSdk=36`, não usa a permissão de rede local introduzida para apps que miram SDK 37. Essa decisão exigirá revisão na atualização de target.

## Compilação

Requer Android SDK API 36, Java 17, Gradle 8.13. O repositório ainda não inclui wrapper binário. Execute no Android Studio ou terminal com Gradle instalado:

```bash
gradle :app:testDebugUnitTest :app:assembleDebug
```

APK esperado: `app/build/outputs/apk/debug/app-debug.apk`. **O APK não está incluído neste ZIP.**

## Próximas evoluções

Descoberta de dispositivos na LAN, pareamento por QR, permissões por categoria/app, bloqueio mais robusto de OTP, persistência local opcional criptografada, atualização em segundo plano no Controlador via canal push e testes físicos em ROMs diferentes.

## Estado de validação

Vide `docs/VALIDATION.md`. A presença do código não equivale a funcionalidade comprovada em hardware: executar build CI e ensaios com aparelhos reais antes de depender deste app.


## Alterações alpha.2
- Corrigido GitHub Actions: `setup-android@v4` não solicita o pacote descontinuado `tools`.
- Corrigido erro no campo de texto da interface (`setTextColor(ink)`).
- Removido recebimento direto de SMS e o receiver para preparar um possível envio à Play.
- Notificações somente de aplicativos escolhidos pelo usuário, com aviso de consentimento e bloqueio conservador de apps financeiros/conteúdo sensível.
- Seleção de apps com `<queries>` de launcher, sem permissão `QUERY_ALL_PACKAGES`.
- `versionCode` 2; nome do artifact sincronizado; documentação de Google Play em `docs/PLAY_STORE.md`.
