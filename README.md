# Monitor Remoto

**v0.1.0-alpha.7 / versionCode 7** · Android 8.0+ · Kotlin 2.2.21 · AGP 8.13.2 · Gradle 8.13

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
- Pareamento por QR Code temporário no mesmo Wi-Fi/hotspot, com leitura pela câmera e conexão automática; conexão manual por IPv4/nome na mesma LAN ou entre redes por VPN privada (Tailscale/WireGuard). Porta **37841/TCP**.
- Notificação persistente do serviço foreground `connectedDevice`; parar por UI ou pela ação na notificação.
- Inicialização após reboot opcional (tentativa, sujeita a política do SO/fabricante).

## Como utilizar

1. Execute GitHub Actions > **Gerar APK e Release** > Run workflow. Para baixar **APK sem ZIP**, use Releases > `v0.1.0-alpha.7` > Assets > **`MonitorRemoto-v0.1.0-alpha.7.apk`**. Em Actions > Artifacts, um APK de contingência (quando presente) continua vindo em ZIP por comportamento padrão do GitHub. Instale o mesmo APK nos dois aparelhos.
2. No telefone secundário: abra modo **Monitorado**; autorize a notificação de serviço; ative o monitoramento; autorize notificações em Configurações. Selecione explicitamente os aplicativos cujas notificações deseja compartilhar.
3. Na tela Monitorado, toque em **Gerar QR Code para parear**; escolha o IP local acessível (normalmente interface Wi-Fi). O QR expira em 2 minutos e é válido para um único pareamento.
4. No telefone principal: modo **Controle**; toque em **Ler QR Code do outro celular**, aponte a câmera e confirme. IP e chave de acesso são salvos automaticamente. A digitação manual de IP e chave continua disponível.
5. Na internet: configure **Tailscale/WireGuard** em ambos, conecte-os à mesma rede privada VPN e, no Controle, substitua o IP Wi-Fi pelo IP/nome acessível na VPN. Não abra a porta no roteador à internet pública.
6. Com o secundário bloqueado, consulte os dados no principal. Depois de desligar/reiniciar, o serviço pode precisar ser reiniciado dependendo do Android, ROM, economia de bateria e permissões.

**Nota:** a versão alpha usa QR e/ou IP/hostname manual, mas não faz descoberta automática NSD/mDNS de aparelhos. O segundo aparelho requer preparação inicial presencial. O app não acessa dados sem consentimento/permissões. A notificação de monitoramento permanece visível.

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

APK local de teste: `app/build/outputs/apk/debug/app-debug.apk`. O workflow copia esse arquivo ou o Release assinado como **`MonitorRemoto-v0.1.0-alpha.7.apk`**. Baixe diretamente na aba GitHub **Releases**. **O APK não está incluído neste ZIP de código-fonte.** Veja `docs/PAIRING_QR.md` para segurança do QR.

Opcional: configure os quatro Secrets de assinatura para gerar APK Release assinado e AAB. Ver `docs/APK_DOWNLOAD.md`. Preservar a mesma keystore entre builds é essencial para atualizações sem reinstalação.

## Privacidade e robustez (alpha.6)

- Ao desligar o monitoramento ou reativar uma nova sessão, o buffer de notificações na memória é apagado. Ao retirar um aplicativo da lista permitida, seus eventos previamente coletados são removidos. Antes de responder ao Controle, a lista atual é verificada novamente para evitar a transmissão de eventos de permissões revogadas.
- O servidor limita a 3 o número de requisições simultâneas e a 12 a fila de conexões pendentes; conexões excedentes são fechadas sem fila ilimitada. Isso reduz o risco de consumo de recursos na rede local, mas não substitui um firewall nem testes de estresse.

## Próximas evoluções

Descoberta de dispositivos na LAN, reconexão ao mudar IP, permissões por categoria/app, bloqueio mais robusto de OTP, persistência local opcional criptografada, atualização em segundo plano no Controlador via canal push e testes físicos em ROMs diferentes.

## Estado de validação

Vide `docs/VALIDATION.md`. A presença do código não equivale a funcionalidade comprovada em hardware: executar build CI e ensaios com aparelhos reais antes de depender deste app.


## Alterações alpha.4 (versionCode 4)
- QR Code local com segredo temporário (2 minutos, uso único), confirmação antes da conexão e armazenamento protegido da chave permanente.
- Leitor de câmera ZXing integrado e permissões de câmera apenas no momento da leitura. Suporta os mesmos dois modos com um APK.
- Servidor confirma bind da porta antes de indicar início do serviço.
- GitHub Actions evita duplicidade de APK entre Artifact e Release bem-sucedida; cria Artifact reserva se a Release falhar ou for ignorada.
- Alerta explícito: Play Protect classificou APK anterior como nocivo; QR não altera a classificação, e precisa de investigação/contestação. Não desativar Play Protect.

## Alterações alpha.2
- Corrigido GitHub Actions: `setup-android@v4` não solicita o pacote descontinuado `tools`.
- Corrigido erro no campo de texto da interface (`setTextColor(ink)`).
- Removido recebimento direto de SMS e o receiver para preparar um possível envio à Play.
- Notificações somente de aplicativos escolhidos pelo usuário, com aviso de consentimento e bloqueio conservador de apps financeiros/conteúdo sensível.
- Seleção de apps com `<queries>` de launcher, sem permissão `QUERY_ALL_PACKAGES`.
- `versionCode` 2; nome do artifact sincronizado; documentação de Google Play em `docs/PLAY_STORE.md`.

## Alterações alpha.3 (versionCode 3)
- Arquivo APK publicado diretamente em GitHub Releases com nome `MonitorRemoto-v0.1.0-alpha.3.apk`; Artifacts continuam gerando ZIP como backup.
- Assinatura Release opcional com quatro Secrets e geração do AAB para Google Play.
- Verificação de sincronização da versão e empacotamento, SHA-256 do binário, docs e instruções permanentes em `AGENTS.md`.
- Mantidas funcionalidades e filtros de segurança da alpha.2. Build completo depende do GitHub Actions.

### Correção da alpha.5

A execução GitHub Actions #4 falhou na compilação do leitor QR. A chamada ao ZXing foi corrigida para a constante `IntentIntegrator.QR_CODE`, mantendo pareamento temporário e configuração manual. Faça novo build pela ação **Gerar APK e Release**, baixando o APK diretamente em **Releases > Assets** após sucesso.

## Correções alpha.7 (build 6 e leitura de QR)

- Build #6 compilou o Kotlin principal, mas falhou em três testes `EventJournalTest` que acionavam os métodos `org.json` do `android.jar` mockado na JVM. Agora o teste usa `EventJournal.latest()` e testa a lógica de filtro sem serialização Android. `latestJson()` permanece disponível para transmissão real.
- Scanner usa `QrScanActivity` interna não exportada, declarada explicitamente com tema ZXing; solicita permissão de câmera antes de iniciar, trata negação e mantém a orientação livre. A API usada é a `IntentIntegrator` existente na versão 4.3.0.
- O fechamento do leitor informado em aparelho físico ainda **não possui stacktrace Android/logcat**; não se pode confirmar que a causa original foi eliminada sem nova instalação e teste. Caso persista, capturar `adb logcat -b crash -d` ou logcat com `FATAL EXCEPTION` e `MonitorRemotoQR`.
- Secrets de assinatura não acompanham o projeto nem devem ser adicionados ao GitHub. Para atualizar sem reinstalar é necessário **usar sempre o mesmo certificado** e manter o `applicationId`. APK Debug antigo e nova chave Release possuem assinaturas diferentes: talvez seja necessária uma única reinstalação ao migrar, com perda de dados locais sem backup.
