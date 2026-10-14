# Monitor Remoto

**v0.1.0-alpha.37 / versionCode 37** · Android 8.0+ · Kotlin 2.2.21 · AGP 8.13.2 · Gradle 8.13

## Correção de compilação do workflow #37 (alpha.37)

- Corrigida a referência `Base64` em `RemoteClient.kt` com importação de `android.util.Base64` na decodificação da Foto HD.
- Corrigida a propriedade `ScrollView.isFillViewport` em `RemoteScreenActivity.kt`, que impedia `compileDebugKotlin`.
- Adicionadas verificações de regressão e sincronizado `versionName/versionCode/AppVersion`. Nenhuma alteração em permissões, captura, criptografia ou protocolo além da exigência de versão exata.
- **Status:** o workflow #37 falhou ao compilar a alpha.36; a compilação Android da alpha.37 ainda depende de nova execução no GitHub Actions.

## Ver tela: nova interface e capturas manuais (alpha.36)

- **Interface redesenhada:** cabeçalho compacto Voltar, Print, Áudio, Tela cheia; painel de imagem arredondado; controles Toque, Voltar remoto, Início e **Foto HD**. Respeita modo claro/escuro.
- **Print:** toque no ícone de câmera para salvar um quadro JPEG recente da prévia no controlador. Não salva um frame antigo de sessão caída. Pressionar e segurar a câmera também aciona Foto HD.
- **Foto HD:** solicita uma única imagem mais nítida (até 1080 px de largura, com redução progressiva para respeitar 480 KiB) pela sessão MediaProjection *já* autorizada. Não é captura furtiva nem vídeo, e pode falhar quando não houver novos quadros ou em telas protegidas.
- **Salvar:** Android 10+ usa MediaStore em `Imagens/Monitor Remoto`, sem permissão ampla; Android 8–9 usa seletor do sistema para o usuário escolher destino.
- **Tela cheia:** oculta barras do sistema temporariamente, mantém aspecto da imagem e mapeamento correto de toques. Botão visível permite sair. Não há gravação automática, nem gravação de áudio.
- **Segurança:** solicitações são autenticadas no mesmo canal AES-GCM; foto fica temporariamente na RAM do monitorado e só é gravada no controlador mediante ação explícita.
- **Validação:** checagens locais não equivalem à compilação do APK e aos testes reais no Android.

## Controle remoto e áudio interno (alpha.35)

- **Monitorado → Configurações → Tela e controle remoto**: ative **Controle por toque** e habilite manualmente **Monitor Remoto - controle por toque** no serviço de Acessibilidade do Android. O serviço **não coleta árvores de interface nem textos**; só despacha gestos enquanto houver projeção ativa e opção local ligada. Desative no mesmo menu a qualquer momento.
- **Áudio interno**: nessa categoria, autorize RECORD_AUDIO e ative a opção. Depois pare e recomece a sessão de tela para inicializar a captura de áudio. Apenas reprodução de aplicativos que permitem `AudioPlaybackCapture` (Android 10+) pode ser transmitida. Microfone e chamadas protegidas **não** são capturados.
- **Captura de tela**: ao iniciar **Compartilhar tela** no monitorado e aprovar no diálogo oficial Android, a sessão continua mesmo que o controlador perca Wi-Fi, permitindo reconexão sem novo consentimento **enquanto a projeção ainda estiver ativa**. Encerrar a captura, bloquear tela quando o Android a encerra, reiniciar, ou forçar parada exige aprovação nova. Não é possível permitir captura invisível automaticamente.
- **Controlador → Ver tela**: os botões **Toque**, **Ouvir áudio**, **Voltar remoto** e **Início remoto** operam somente quando o monitorado autorizar cada recurso. Tocar, manter pressionado e arrastar na imagem enviam comandos normalizados, respeitando barras laterais. Selecionar **tela inteira** no diálogo do Android oferece melhor correspondência de toques do que compartilhar uma janela isolada.
- Se não houver projeção ativa, **Ver tela** envia **uma solicitação de autorização visível ao monitorado** (notificação limitada a uma por minuto). Apenas o próprio monitorado pode iniciar a captura; não há execução oculta, gravação nem reinício após encerramento.
- **Áudio experimental**: PCM 16 kHz mono, até 500 ms por bloco efêmero em RAM, consulta periódica criptografada pela rede. Pode haver atraso, silêncio e cortes; a prévia usa ~1-2 quadros/s, com codificação JPEG somente quando há visualizador conectado, portanto toque remoto será limitado. Não há streaming H.264 nem audio em alta fidelidade.
- Ao desativar o monitoramento, a sessão de captura é parada. Pareamento e AES-GCM são preservados; aparelhos com a **mesma chave** têm acesso durante sessão explicitamente autorizada. Proteja e revogue a chave quando necessário. **Instale alpha.37 nos dois aparelhos.**

## Ver tela autorizada (alpha.34)

- No aparelho **monitorado**: com o monitoramento ativo, toque em **Compartilhar tela** e confirme no diálogo oficial de captura do Android. A captura só começa após o consentimento local e mostra notificação permanente com ação **Parar tela**. A seção Monitorado também oferece parada explícita.
- No **controlador**: abra aparelho conectado → **Ver tela**. A tela remota é apresentada em uma página própria e consultada pelo canal AES-GCM já pareado. Controle por toque e som interno são opcionais na alpha.35; há exportação manual de capturas, sem gravação contínua.
- Prévia de baixo consumo: aproximadamente **1-2 quadros JPEG/s enquanto houver visualizador**, largura comprimida a até **540 px**, até 110 KiB de imagem por quadro, limite de idade de 3 segundos, sem persistir em disco. O controlador consulta enquanto a página está visível e limpa a visualização ao sair. Uso contínuo ainda aumenta o consumo de bateria.
- **Condições do Android**: consentimento para **cada sessão**; Android 14+ impede reutilização do token. Android 15 QPR1+ exibe indicação destacada e encerra projeção ao bloquear a tela. É necessário reautorizar depois da parada. O app nunca reinicia a captura sozinho, nem após boot, nem após queda do serviço.
- Uma chave compartilhada permite que aparelhos anteriormente pareados assistam durante uma sessão autorizada. Rotacione a chave se quiser revogar todos os controladores. Não abra a porta TCP diretamente para a internet pública.
- A funcionalidade é experimental e requer compilação Android e teste em dois aparelhos. A interface não indica transmissão enquanto a autorização do sistema não estiver efetiva.

## Central de Mensagens (alpha.33)

- **Mensagens** abre tela própria com abas **SMS** e **Notificações**. Não existe mais uma única janela alta com dezenas de botões grandes.
- Mensagens agrupadas por remetente/app, com ícone **Copiar** por item e botão **Responder** apenas quando autorizado e tecnicamente disponível. Envio mantém revisão e confirmação.
- **Voltar** limpa o conteúdo da RAM. **Atualizar** solicita dados sob demanda e revalida a conexão. Após reconectar pode ser necessário solicitar atualização novamente.
- **Correção de privacidade**: SMS de autenticação em inglês, inclusive “authentication code”, são bloqueados antes da transmissão. Eventos capturados anteriormente são revalidados na transmissão, e a interface filtra novamente. Filtros de OTP não são perfeitos.
- Status de envio e metadados de MMS ficam recolhidos em Detalhes; captura de tela liberada apenas onde já era permitida.

## Configurações e recuperação do serviço (alpha.32)

A engrenagem abre uma **página de Configurações**, não uma janela modal. Escolha Notificações, SMS e telefonia, Serviço e recuperação, Diagnóstico, Segurança, Aparência ou Sobre. Cada categoria é uma página separada, com retorno às categorias pelo botão Voltar. Acessos a permissões continuam autorizados exclusivamente pelo Android no aparelho monitorado. Capturas de SMS continuam permitidas; telas de chave e QR continuam protegidas.

No monitorado, **Configurações → Serviço e recuperação** mostra retomada manual, preferência para iniciar após reiniciar e link para as opções de bateria do Android. Enquanto o serviço foreground estiver ativo e autorizado, um supervisor a cada 15 segundos verifica se o servidor TCP realmente escuta e recria o listener se falhar; em erro de bind aguarda 2 s, 5 s, 10 s, 30 s, 60 s e depois até 5 min para novas tentativas. O retorno da rede gera verificação adicional. **Configurações → Diagnóstico** mostra último estado/horário verificado e falhas consecutivas. O monitoramento só volta automaticamente após morte do processo quando o Android permitir a recriação pelo `START_STICKY`; "Forçar parada" nas configurações do Android e restrições de fabricante não podem ser anuladas.


Dois modos no mesmo APK: **Monitorado** (servidor) e **Controle** (cliente).

**Alpha.28:** diagnósticos amigáveis de conexão, detecção criptografada de versão diferente para aparelhos autenticados, seleção de apps de notificação com pesquisa e diálogos padronizados que não se sobrepõem.

## Alpha.27: identidade visual unificada

A Home, o Modo Monitorado, a escolha de conexão, os submenus de Configurações, o diagnóstico, as credenciais e o pareamento QR seguem o mesmo estilo de cartões, ícones vetoriais e espaços compactos da tela Detalhes do aparelho. Esta versão não acrescenta permissões nem amplia o acesso remoto; preserva todas as ações existentes.


> **Alpha.25 (histórico):** correção Kotlin do acesso recebido após o workflow 25; correção localizada de `Context` em Views. A reorganização da tela de Detalhes do aparelho e o futuro atalho Recuperar dados continuam planejados, não estão nesta correção.

> **Alpha.23 (histórico):** continuação da refatoração da interface e correção da tela **Acesso remoto detectado**, que agora mostra as informações completas já na primeira tela, sem o pop-up “Quem está?”.

## Alpha.24: segunda etapa da refatoração

- `MainActivity.kt` foi reduzida de 1.000 para aproximadamente 322 linhas e passou a coordenar principalmente ciclo de vida, navegação e callbacks de permissões.
- `RemoteConnectionManager.kt` agora concentra estados, cache, consulta criptografada, cancelamento de respostas obsoletas e aviso de desconexão.
- `ConnectionScreenUi.kt` e `ConnectionController.kt` separam visual do painel conectado e suas ações.
- `PairingScreenUi.kt`, `PairingQrUi.kt` e `PairingController.kt` separam os diálogos, scanner/gerador de QR e pareamento de rede.
- `MonitorCredentialsUi.kt` preserva a proteção e a exibição sob demanda de IP/chave; os diálogos de notificação foram realocados para Configurações.
- Mantidos o protocolo de acesso, a versão exata entre aparelhos, as permissões e o estilo visual. O IP selecionado por QR/manual agora é definido explicitamente antes da primeira consulta.
- Necessária compilação Android e teste físico nos dois aparelhos. Código-fonte ZIP não inclui APK.


## Novidades da alpha.26: painel remoto e detalhes técnicos

- Painel conectado redesenhado em três indicadores visuais (bateria, temperatura e rede) com ícones vetoriais e seis funções remotas em cartões.
- Detalhes do aparelho agora abre uma tela própria com seções de bateria, Wi-Fi, rede móvel/SIM, armazenamento interno (total, usado, livre), RAM e sistema Android.
- Servidor autenticado envia `deviceInfo`, `telephony` e informações de rede observadas, sem expor a chave de pareamento; recursos ainda não implementados mostram **Em breve**.
- Número de linha é opcional, desativado inicialmente e compartilhado apenas após permissão `READ_PHONE_NUMBERS` **e** aceite em **Configurações → Notificações e permissões → Número do chip**, sempre no aparelho monitorado. O número pode não existir no registro da operadora.
- O atalho **Recuperar dados** foi adicionado visualmente, mas a transferência de arquivos ainda não está implementada; continua prevista para uma versão posterior.
- Atualize os dois aparelhos para alpha.27: a conexão exige versão exata igual.

## Recursos já implementados no código-fonte

- Snapshot de nível da bateria, carregamento, temperatura, tensão, corrente instantânea e média (quando o hardware/Android fornecem as propriedades).
- Captura de notificações **novas** autorizadas pelo usuário por `NotificationListenerService`. Não lê automaticamente o histórico de WhatsApp; resposta apenas por ação `RemoteInput` vigente.
- Captura opcional de notificações de aplicativos selecionados; a alpha.30 oferece leitura SMS recebidos sob demanda (READ_SMS) e respostas SMS (SEND_SMS), ambos desativados por padrão e sujeitos às restrições do Android.
- Filtro por conteúdo e bloqueio preventivo de nomes/pacotes financeiros conhecidos (não são infalíveis; o Android ainda concede acesso geral ao serviço).
- Histórico volátil limitado a 100 eventos no aparelho monitorado; mostra os últimos 30. Nada salvo em banco ou nuvem.
- Servidor TCP autenticado com chave de 256 bits e payload protegido por AES-256-GCM, timeout, nonce aleatório, janela temporal de 5 min e cache de rejeição de repetição por nonce.
- Segredos armazenados protegidos por Android Keystore no aparelho.
- Cliente com atualização ao abrir e polling de 15 segundos enquanto a tela do Controle estiver aberta.
- Pareamento por QR Code temporário no mesmo Wi-Fi/hotspot, com leitura pela câmera e confirmação do usuário antes do pareamento; conexão manual por IPv4/nome na mesma LAN ou entre redes por VPN privada (Tailscale/WireGuard). Porta **37841/TCP**.
- Notificação persistente do serviço foreground `connectedDevice`; parar por UI ou pela ação na notificação.
- Inicialização após reboot opcional (tentativa, sujeita a política do SO/fabricante).

## Como utilizar

1. Execute GitHub Actions > **Gerar APK e Release** > Run workflow. Para baixar **APK sem ZIP**, use Releases > `v0.1.0-alpha.37` > Assets > **`MonitorRemoto-v0.1.0-alpha.37.apk`**. Em Actions > Artifacts, um APK de contingência (quando presente) continua vindo em ZIP por comportamento padrão do GitHub. Instale o mesmo APK nos dois aparelhos.
2. No telefone secundário: na nova **Home**, toque em **Monitorar este aparelho**; autorize a notificação de serviço e ative o monitoramento. Em Configurações, conceda apenas as permissões necessárias e selecione os aplicativos cujas notificações deseja compartilhar.
3. Na página Monitorado, toque no cartão **Gerar QR**. Escolha o IP privado acessível. O QR expira em 2 minutos e é válido para um único pareamento.
4. No telefone principal: na **Home**, toque em **Conectar novo aparelho > QR Code**, aponte a câmera na janela compacta e confirme os dados do aparelho antes do pareamento. Para um aparelho salvo, use **Aparelhos anteriores > Conectar**. A digitação manual de IP e chave continua disponível.
5. Na internet: configure **Tailscale/WireGuard** em ambos, conecte-os à mesma rede privada VPN e, em **Conectar novo aparelho > Manualmente**, informe o IP/nome acessível na VPN. Não abra a porta no roteador à internet pública.
6. Com o secundário bloqueado, consulte os dados no principal. Depois de desligar/reiniciar, o serviço pode precisar ser reiniciado dependendo do Android, ROM, economia de bateria e permissões.

**Nota:** a versão alpha usa QR e/ou IP/hostname manual, mas não faz descoberta automática NSD/mDNS de aparelhos. O segundo aparelho requer preparação inicial presencial. O app não acessa dados sem consentimento/permissões. A notificação de monitoramento permanece visível.

## Considerações importantes

- **Distribuição particular**: a alpha.31 declara `READ_SMS`, `SEND_SMS`, `RECEIVE_SMS` e `READ_PHONE_STATE`. Cada permissão precisa de autorização local do Android e da opção correspondente no app; `READ_SMS` pode continuar restrita mesmo fora da Play Store. O app não é o gerenciador padrão SMS; MMS só expõe assunto/data quando possível. Responder SMS pode gerar custos e a confirmação de entrega depende da operadora.
- **WhatsApp**: só texto de notificações capturadas e filtradas; resposta apenas se houver ação `RemoteInput` ativa, permitida e válida, com confirmação no controlador. Não acessa histórico interno, mensagens apagadas ou chats sem notificação. A entrega da resposta não pode ser confirmada pela API.
- **Dados sensíveis**: mensagens e OTP podem escapar dos filtros; não se deve tratar o bloqueio por expressões como infalível. SMS com padrões de autenticação são descartados da consulta. A chave permite acesso aos recursos expressamente autorizados; revogue a chave quando necessário.
- **Privacidade**: código não inclui nuvem nem telemetria. A VPN (se utilizada) é um serviço externo e tem políticas próprias.
- **Precisão da bateria**: a corrente `BATTERY_PROPERTY_CURRENT_NOW` é líquida e pode ter sinal ou unidade incorreta em alguns fabricantes. Não confundir com corrente no carregador. Medidas indisponíveis aparecem como indisponíveis.
- **Rede**: comunicação cifrada na aplicação mesmo em LAN, porém o endereço IP/porta ainda é observável. Não há TLS/mTLS. Sem firewall de rede, um atacante na mesma LAN pode tentar sobrecarregar o serviço; limite conexões e evite redes públicas.
- **Android 17**: como `targetSdk=36`, não usa a permissão de rede local introduzida para apps que miram SDK 37. Essa decisão exigirá revisão na atualização de target.

## Compilação

Requer Android SDK API 36, Java 17, Gradle 8.13. O repositório ainda não inclui wrapper binário. Execute no Android Studio ou terminal com Gradle instalado:

```bash
gradle :app:testDebugUnitTest :app:assembleDebug
```

APK local de teste: `app/build/outputs/apk/debug/app-debug.apk`. O workflow copia esse arquivo ou o Release assinado como **`MonitorRemoto-v0.1.0-alpha.37.apk`**. Baixe diretamente na aba GitHub **Releases**. **O APK não está incluído neste ZIP de código-fonte.** Veja `docs/PAIRING_QR.md` para segurança do QR.

Opcional: configure os quatro Secrets de assinatura para gerar APK Release assinado e AAB. Ver `docs/APK_DOWNLOAD.md`. Preservar a mesma keystore entre builds é essencial para atualizações sem reinstalação.

## Privacidade e robustez (alpha.6)

- Ao desligar o monitoramento ou reativar uma nova sessão, o buffer de notificações na memória é apagado. Ao retirar um aplicativo da lista permitida, seus eventos previamente coletados são removidos. Antes de responder ao Controle, a lista atual é verificada novamente para evitar a transmissão de eventos de permissões revogadas.
- O servidor limita a 3 o número de requisições simultâneas e a 12 a fila de conexões pendentes; conexões excedentes são fechadas sem fila ilimitada. Isso reduz o risco de consumo de recursos na rede local, mas não substitui um firewall nem testes de estresse.

## Interface Controle (alpha.11)

- Visual claro por padrão, com opções em **⚙ Configurações > Aparência**: claro, escuro e seguir sistema; escolha guardada apenas neste aparelho.
- **Histórico:** a tela inicial já foi Controle (alpha.11) e Monitorado (alpha.16). **Desde a alpha.17**, a tela inicial é a **Home**, com Conectar novo aparelho, Aparelhos anteriores e Monitorar este aparelho; o ícone de casa sempre retorna à Home.
- Cabeçalho com **⌂ Home** e **⚙ Configurações**; no cartão do aparelho, botão compacto **Conectar/Reconectar/Desconectar** junto ao nome e **↻ Atualizar** colorido à direita; chips de versão e estado do monitorado; atualização manual e polling automático a cada 15 s.
- A etapa inicial **Conectar aparelho** oferece QR Code, Manualmente, aparelhos salvos com botões Conectar/Renomear/Esquecer. Somente Manualmente abre os blocos IP e chave com Colar/Limpar.
- Até cinco aparelhos recentes com renomear/esquecer. Cada chave é protegida individualmente pelo Android Keystore, com migração da credencial antiga sem novo pareamento. O histórico registra somente nome/IP e último sucesso, **não** mensagens.
- A informação "Conectado" representa a **última consulta autenticada** e não um socket persistentemente conectado; em falha, status troca para "Sem resposta" e dados anteriores permanecem rotulados.
- QR continua com Camera2 (alpha.9); teste físico e compilação completa ainda pendentes.

## Mensagens remotas (alpha.31)

No **aparelho monitorado**, ative Monitoramento; em **Configurações > Notificações**, conceda acesso ao listener e autorize os apps individualmente ou ative **Todas as notificações elegíveis**. Separadamente, habilite **Responder notificações**, **Ler caixa de SMS**, **Receber SMS em tempo real** e **Responder SMS** conforme desejar. A seleção manual de SIM exige READ_PHONE_STATE. O Android pode negar READ_SMS mesmo em distribuição particular; não é pedido o papel de app SMS padrão. Notificações sigilosas, apps financeiros conhecidos e conteúdos detectados como OTP continuam filtrados em melhor esforço.

No **controlador**, instale a mesma alpha.31, conecte-se, abra **Mensagens** e toque em **Atualizar mensagens**. São apresentados até 60 SMS recebidos e enviados recentes, notificações autorizadas e, quando disponíveis, eventos SMS ao vivo e metadados MMS. **Copiar** copia o texto para a área de transferência. **Responder** aparece quando o SMS possui número regular já presente na caixa recebida ou quando a notificação oferece `RemoteInput` válido. Para SMS, escolha o chip disponível, escreva, revise e confirme. Status *solicitado, enviado, entregue, falhou* ou *entrega não confirmada* refletem callbacks locais na RAM, não garantem leitura pelo destinatário. A ação de responder a uma notificação pode expirar.

A janela **Mensagens** não define `FLAG_SECURE`, permitindo capturas de tela. Janelas com QR e credenciais continuam protegidas. Novas mensagens não são enviadas automaticamente ao controlador: ele precisa abrir/atualizar a tela. Ao desativar o monitoramento, os buffers voláteis são limpos.

As opções estão **desativadas após atualização** e podem ser revogadas no próprio monitorado. Todos os acessos dependem da chave AES-GCM e da versão idêntica em ambos os telefones. Mensagens SMS são obtidas sem salvar banco ou arquivo; os eventos de notificação continuam num buffer volátil. O histórico de WhatsApp e mensagens criptografadas internamente não é acessível pelo Monitor Remoto.

## Próximas evoluções

Descoberta de dispositivos na LAN, reconexão ao mudar IP, permissões por categoria/app, bloqueio mais robusto de OTP, persistência local opcional criptografada, atualização em segundo plano no Controlador via canal push e testes físicos em ROMs diferentes.

## Estado de validação

Vide `docs/VALIDATION.md`. A presença do código não equivale a funcionalidade comprovada em hardware: executar build CI e ensaios com aparelhos reais antes de depender deste app.


## Alterações alpha.4 (versionCode 4)
- QR Code local com segredo temporário (2 minutos, uso único), confirmação antes da conexão e armazenamento protegido da chave permanente.
- Leitor nativo Camera2 com ZXing Core para decodificação offline e permissões de câmera apenas no momento da leitura. Suporta os mesmos dois modos com um APK.
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
- Scanner alpha.9 agora usa **Camera2 nativo**, sem `CaptureActivity`, `IntentIntegrator` ou bibliotecas de câmera ZXing Embedded. O ZXing Core (3.5.4) fica exclusivamente na codificação/decodificação local. Se a câmera falhar, permaneça a alternativa Manual com IP e chave.
- O fechamento do leitor informado em aparelho físico ainda **não possui stacktrace Android/logcat**; não se pode confirmar que a causa original foi eliminada sem nova instalação e teste. Caso persista, capturar `adb logcat -b crash -d` ou logcat com `FATAL EXCEPTION` e `MonitorRemotoQR`.
- Secrets de assinatura não acompanham o projeto nem devem ser adicionados ao GitHub. Para atualizar sem reinstalar é necessário **usar sempre o mesmo certificado** e manter o `applicationId`. APK Debug antigo e nova chave Release possuem assinaturas diferentes: talvez seja necessária uma única reinstalação ao migrar, com perda de dados locais sem backup.

## Correção alpha.8 (workflow #7)

- O log do GitHub Actions da alpha.7 falhou em `:app:compileDebugKotlin` porque `MainActivity.kt` declarava **duas vezes** `onRequestPermissionsResult` (linhas 422 e 580), com a mesma assinatura; o Kotlin relatou `Conflicting overloads`.
- Agora existe **um único callback** para os dois pedidos separados: `CAMERA` (abre o leitor QR quando concedida) e `POST_NOTIFICATIONS` (inicia o monitoramento quando concedida). Nenhuma das duas autorizações habilita a outra e a recusa continua tratada.
- O Artifact APK de contingência só é solicitado após o passo de nomear APK concluir com sucesso. Falhas de compilação não geram tentativa inútil de upload de arquivo inexistente.
- O build de GitHub Actions e o teste físico de QR da alpha.8 ainda precisam ser confirmados. A mensagem anterior sobre possível encerramento do QR não foi diagnosticada por logcat.
- Mantenha os mesmos quatro Secrets de assinatura da alpha.7. **Não gere outra keystore**; isso comprometeria a capacidade de atualizar APKs já assinados.

## alpha.9: nova abordagem para o fechamento do leitor QR

- A alpha.8 ainda abria a câmera usando a Activity do ZXing Embedded; as mudanças de manifesto e permissões não resolveram o encerramento relatado. Sem logcat do celular, a causa original específica **não foi comprovada**.
- A alpha.9 substitui integralmente essa tela por `QrScanActivity` com **Android Camera2** e `ImageReader YUV_420_888`, pré-visualização via `TextureView` e leitura ZXing Core em memória, sem Play Services/internet.
- Exibe estado e erros da câmera na própria tela (em vez de encerrá-la deliberadamente), com logs `MonitorRemotoQR` sem incluir o conteúdo confidencial do QR. Permissão continua sob autorização explícita.
- A opção antiga de colar o URI temporário do QR foi retirada na alpha.18; utilize QR pela câmera ou conexão Manual com IP e chave.
- Nenhuma foto ou quadro é salvo/transmitido. O limite de leitura é de ~2,5 tentativas por segundo, economizando CPU em relação à decodificação de todos os frames.
- Necessário confirmar build Android `:app:testDebugUnitTest :app:assembleDebug`, leitura física entre dois aparelhos, orientação/câmera de fabricantes diferentes e upgrade APK assinado. Não afirmar correção comprovada sem esses testes.

## Revisão visual de diálogos e painel (alpha.14)

A tela Monitorado apresenta o estado real do serviço e IP local. Ativar, gerar QR e interromper são ações contextuais; notificações, segurança, inicialização e diagnóstico agora ficam em ⚙ Configurações.
- Menu, Configurações, Aparência, Aparelhos recentes, Diagnóstico e demais diálogos receberam cartões com subtítulo, melhor hierarquia visual e menos sensação de lista crua.
- O cartão principal do Painel Controle foi reorganizado para priorizar nome do aparelho, estado, ação principal e atualização manual, reduzindo textos duplicados.


## Fluxo de conexão e cartões (alpha.15)

- Conectar/Reconectar abre a escolha de método, em vez do formulário grande. No modo conectado, Desconectar apenas limpa a seleção, preservando os aparelhos pareados.
- Na alpha.15 havia as opções de QR Code, Manual e Inserir código QR. Na alpha.18 restaram QR e Manual lado a lado, mais cartões de aparelhos anteriores.
- Cada cartão salvo traz os botões verticais **Conectar**, **Renomear** e **Esquecer**. Esquecer requer confirmação e apaga a chave do aparelho no Android Keystore.
- QR escaneado **não conecta automaticamente**: apresenta endereço e nome local se o aparelho já estiver salvo; se for novo, apresenta «Não identificado (novo)» até ser autenticado. **Confirmar** inicia o pareamento e **Cancelar** o interrompe.
- No painel, botões menores ficam alinhados à direita: ação de conexão junto ao título e **↻ Atualizar** em azul. As consultas continuam a cada 15 s com a tela aberta.

## Tela inicial e informações manuais (alpha.16)

- O aplicativo inicia no modo Monitorado; **⌂ Home** no cabeçalho retorna a esse modo sem interromper o serviço. O modo Controle abre ao conectar por QR, manualmente ou por dispositivo salvo.
- Com o monitor ativo, as ações **Gerar QR Code**, **Mostrar minhas informações** e **Interromper monitoramento** ficam juntas.
- **Mostrar minhas informações** abre uma janela protegida por `FLAG_SECURE` com o IP privado local atual (preferindo Wi-Fi) e a chave guardada no Android Keystore, oculta até o usuário solicitar.
- **Copiar informações** copia IP e chave, marcando conteúdo como sensível em Android 13+. **Compartilhar** exige confirmação e abre o menu nativo do Android. Evite enviar chaves a terceiros; apague o clipboard após utilização.
- Abaixo das ações, **Conectados recentemente** mostra atalhos compactos de até dois aparelhos, botão **Ver todos** (escolha QR/Manual/Salvos) e **Conectar outro aparelho**.
- **Sobre o aplicativo** agora está dentro de **Configurações**. O diálogo de conexão existente da alpha.15 permanece disponível.
- A compilação Gradle/Android e teste real de interface e compartilhamento permanecem pendentes de CI e aparelho.

## Home e monitoramento separados (alpha.17)

- **Home** é a primeira página e continua acessível sem Wi-Fi. Oferece **Conectar novo aparelho** (QR/manual), os **Aparelhos anteriores** e a opção **Monitorar este aparelho**.
- Cada aparelho anterior mostra nome, IP e **Última conexão**. Essa data é derivada de `RecentDevices.lastSuccessMs`, atualizada **somente após uma consulta autenticada bem-sucedida**; se não houve sucesso, informa isso claramente. O botão **Conectar** é imediato; a **engrenagem** abre Renomear e Esquecer, com confirmação e revogação do segredo.
- Na página Monitorado, os botões **Gerar QR**, **Mostrar info** e **Interromper** aparecem empilhados à direita do resumo deste aparelho. Não se trata de uma nova página inicial.
- O ícone **Home** e a ação **Voltar** nas telas de monitoramento/controle retornam à escolha inicial sem encerrar serviços, apagar dados ou revogar credenciais.
- As telas detalhadas de controle remoto e de aparelho monitorado serão refinadas em versão posterior; a alpha.17 só reorganiza a navegação e as ações locais.


## Alpha.22: primeira etapa da refatoração da interface

- A `MainActivity.kt` foi reduzida de ~1.795 para ~1.005 linhas; agora coordena ciclo de vida, permissões, conexão, pareamento e navegação.
- A construção visual da **Home**, da tela **Monitorado**, de **Configurações** e do **Painel remoto** foi separada em `ui/HomeScreenUi.kt`, `ui/MonitorScreenUi.kt`, `ui/SettingsScreenUi.kt` e `ui/RemoteDashboardUi.kt`.
- Componentes Android Views comuns (botões, cartões, campos, chips, diálogos e espaçamento) foram extraídos para `ui/UiComponents.kt`.
- Esta etapa não introduz recursos ou permissões novos, não modifica o protocolo criptografado nem os tempos de monitoramento. Mantém atalhos compactos e o fluxo atual dos dois aparelhos.
- Como a aplicação exige versões idênticas para se conectar, **instale alpha.23 nos dois dispositivos**. Ainda falta compilação Gradle/Android em CI e teste físico de regressão visual.

## Correção da alpha.21: compilação Android

- No workflow **Gerar APK e Release 21**, o compilador Kotlin apontou incompatibilidade de tipo em `IncomingAccessActivity.kt` na configuração do `ScrollView`: o nome `background` foi resolvido como `Drawable`, mas `setBackgroundColor()` exige `Int` de cor.
- A chamada foi corrigida com uma referência explícita a `this@IncomingAccessActivity.background`. O protocolo e as telas permanecem iguais à alpha.20; a versão muda para **alpha.21 nos dois aparelhos**, pois o aplicativo exige igualdade exata da versão.
- A compilação real deve ser repetida no GitHub Actions. A validação estática e o teste sintático local não substituem um build Android completo.

## Novidades da alpha.20: aviso de acesso, compatibilidade e telas renovadas

- O telefone monitorado exibe uma **tela dedicada** sempre que recebe uma consulta autenticada enquanto o aplicativo está em primeiro plano, inclusive se as Configurações estiverem abertas. A tela informa nome e método *declarados* pelo controlador (não verificados), IP real, horário da última consulta, botão **Quem está?** e **Parar acesso** (interrompe o serviço para todos). A captura continua sendo apenas de dados autorizados; não há controle de tela.
- Após **Desconectar** no controlador, um aviso autenticado de encerramento remove o registro de acesso correspondente no monitorado, que volta automaticamente à página **Modo monitorado**, pronta para gerar um novo QR; o serviço continua ativo. Caso a rede falhe antes do aviso, o registro expira após 45 segundos.
- Com o aplicativo monitorado **em segundo plano**, o Android mostra uma notificação de acesso recente, sem forçar a abertura de uma tela sobre outros aplicativos.
- Desde a alpha.20, exigida **versão exata igual nos dois aparelhos** (para esta entrega, alpha.27): QR, pareamento e consultas autenticadas não aceitam versões anteriores. Os dois dispositivos devem ser atualizados antes de reconectar; isso **não** é uma identidade individual verificada.
- A interface Configurações foi reconstruída com grade 2x3, ícones vetoriais e cartões adaptados ao tema; a tela de aviso recebido usa layout próprio de destaque. A chave fica oculta no diálogo de informações, com proteção contra capturas.
- Painel remoto: cartões da bateria mais compactos, ações remotas maiores, redução de informações duplicadas e exibição do tipo de rede do próprio celular monitorado (sem inventar SSID ou rotas).
- Se um serviço autorizado estiver parado, o aplicativo pode tentar retomá-lo **ao voltar ao primeiro plano**, sem contrariar uma parada explícita do usuário. Restrições do Android/ROM podem impedir recuperação. Sem promessa de reinício persistente após qualquer falha.

## Mudanças da alpha.19: painel remoto compacto

- Tela do aparelho conectado reorganizada como painel compacto, com nome, status, IP, método e ações principais no topo.
- Resumo remoto em grade com ícones para bateria, temperatura, corrente atual, corrente média, tensão e quantidade de eventos.
- Grade de funções remotas com atalhos compactos: Notificações, Copiar, Detalhes, Mensagens, Ver tela e Localização.
- Notificações autorizadas e detalhes técnicos passaram a abrir em pop-ups; copiar monta um resumo sensível do aparelho atual.

## Mudanças da alpha.18: conectividade e acesso recebido

- Na janela Conectar aparelho, **QR Code | Manual** dividem a mesma linha. A opção de inserir texto de QR foi retirada do menu (a conexão manual por IP/chave continua).
- Em Aparelhos anteriores, os três controles **Conectar | Renomear | Esquecer** dividem uma única linha em vez de empilharem.
- O pop-up **Minhas informações** exibe os botões **Mostrar | Copiar | Compartilhar** lado a lado, sem mostrar a chave até o usuário solicitar.
- Em Controle, o botão **Desconectar** retorna automaticamente à Home e cancela a seleção do host.
- O aparelho Monitorado exibe um quadro de **acesso recebido** assim que recebe resposta autenticada com sucesso, com IP de origem, nome/método declarados pelo cliente, primeiro e último acesso e botão Ações. A tela Home também sinaliza acessos recentes. A interface expira indicadores após 45 segundos sem consulta autenticada; atualiza via evento e temporizador de 5 segundos.
- **Não existe sessão permanente**: cliente consulta a cada 15 segundos com tela Controle aberta. Método/nome enviados pelo cliente não provam identidade, pois os controladores autorizados compartilham a mesma chave. Encerrar serviço ou trocar chave revoga acesso globalmente; não existe controle individual nesta versão.
- Controladores antigos, que não enviam nome/método, aparecem como **não identificados**. Não inventar método ou atribuir identidade com base apenas no IP.
