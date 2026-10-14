# Monitor Remoto 0.1.0-alpha.31
# Convenções permanentes do projeto Monitor Remoto

- Toda alteração de código incrementa sequencialmente `versionCode` e `versionName` (ex.: `0.1.0-alpha.8` -> `0.1.0-alpha.9`). Sincronizar tela, README, CHANGELOG, RELEASE_NOTES e documentação.
- O artefato instalável da CI deve ter **nome real de arquivo** `MonitorRemoto-v<versionName>.apk`, sem `.zip` no final.
- O GitHub Actions `upload-artifact` sempre entrega ZIP ao baixar um Artifact. Publicar Artifact APK só se Release falhar ou for ignorada, evitando duas fontes para a mesma execução. Para **APK direto**, o workflow **também deve publicar o `.apk` como arquivo de uma GitHub Release** (em workflow_dispatch e push ao branch padrão). Não confundir o pacote ZIP de código-fonte com o APK.
- Opcional: com KEYSTORE_BASE64, KEYSTORE_PASSWORD, KEY_ALIAS e KEY_PASSWORD, gerar APK Release assinado estável e AAB Release; nunca armazenar keystore ou segredos no repositório nem em arquivos públicos.
- Sem Secrets, somente APK Debug para testes, com assinatura temporária; avisar que o APK pode não atualizar sem reinstalação.
- Não acrescentar serviços de acessibilidade, captura de tela, sobreposição ou vigilância oculta. A alpha.30 inclui leitura opcional de SMS somente após autorização local explícita e READ_SMS, que pode ser bloqueada pelo Android; não contornar permissões restritas. Consentimento expresso; lista positiva de notificações vazia por padrão.
- Funcionar primeiro na rede local; não expor a porta na internet pública. Preservar criptografia, parada visível e conexão autenticada.
- Ao entregar versão, reportar status verdadeiro do build Android e disponibilizar ZIP do fonte e, somente após compilação bem-sucedida, o APK; priorizar cópia do fonte no Google Drive.

- QR local: utilizar credencial de curta duração/uso único, proteger a janela com `FLAG_SECURE`, não salvar temporária em preferências e manter pareamento manual disponível. Código QR inclui apenas IP privado da LAN.
- Alertas `App nocivo bloqueado` do Google Play Protect são sinais reais e exigem auditoria e eventual contestação; não mascarar, não prometer que gerar novo APK/QR resolve e não instruir a desligar Play Protect.

- Regressão alpha.8: manter **uma única** implementação `onRequestPermissionsResult` em `MainActivity`; ao adicionar nova permissão, ampliar o `when`, não criar outro override. Executar `python3 tools/check_distribution.py` antes do pacote.

- Alpha.9: leitura QR usa **Camera2 nativo** (`ui/QrScanActivity.kt`) + `ui/QrFrameDecoder.kt` (ZXing Core). Não voltar a `IntentIntegrator`, `CaptureActivity` do ZXing Embedded ou permissões ocultas sem diagnóstico e testes.
- Para problemas de abertura: conferir o logcat `FATAL EXCEPTION` e tag `MonitorRemotoQR`, ciclo de vida Activity/SurfaceTexture/CameraDevice/ImageReader. Não registrar conteúdo do QR (segredo temporário). Oferecer sempre inserção manual do URI.

- Alpha.10: manter tema **claro padrão**, as opções da engrenagem, janela Conectar, botão de atualização compacto, lista de até 5 aparelhos recentes e os dados do Controle no topo. Não reintroduzir formulário longo na tela principal.
- Credenciais por dispositivo usam `RecentDevices` + `SecretVault.profileKeyName`, com migração única da alpha.9. Não reaproveitar `CONTROLLER_KEY` antigo para IPs novos nem gravar segredo em preferências sem criptografia. Esquecer apaga o segredo correspondente.
- Respostas HTTP/TCP não são conexão persistente. Exibir "Conectado" apenas após consulta autenticada bem-sucedida e "Sem resposta" após erro. Não inventar tempos de atualização.
- Próxima versão sequencial: `0.1.0-alpha.32`, `versionCode 32`.

- Alpha.15: preservar a seleção Conectar → QR/Manual/Salvos, confirmação do QR antes de parear, cartões salvos com três ações e o scanner em Activity com tema Dialog.

- Alpha.16: Home retorna ao modo Monitorado sem interromper serviço; tela inicial reúne QR, informações manuais e recentes. Sobre fica em Configurações. Chave oculta no pop-up `FLAG_SECURE`, cópia explícita marcada sensível e compartilhamento somente com confirmação prévia. Preservar.

- Alpha.17: Home (modo 2) é a tela inicial, acessível offline; Monitorado (modo 0) e Controle (modo 1) são páginas separadas. Exibir última conexão real persistida por `RecentDevices.markSuccess`; não tratar tentativa como sucesso. Engrenagem de cada aparelho na Home oferece Renomear/Esquecer, nunca expor chave. Botões compactos no cartão Monitorado à direita. Home/Voltar não interrompem serviço. Na alpha.18, priorizar avaliação das telas de dados do aparelho remoto e do aparelho monitorado, sem alterar navegação silenciosamente.

- Alpha.18: seleção QR/Manual compacta na mesma linha; dispositivos salvos com ações horizontais Conectar/Renomear/Esquecer; Minhas informações tem Mostrar/Copiar/Compartilhar lado a lado. Desconectar volta à Home.
- Alpha.18: `AuthenticatedAccess` registra SOMENTE consultas respondidas após AES-GCM autenticado. Servidor não possui sessão TCP permanente: registro em memória expira após 45 s sem novas consultas. Nome/método são declarados pelo controlador e podem ser falsificados por outro portador da chave. Nunca apresentá-los como identidade verificada. Sem revogação individual enquanto a chave é compartilhada: interromper serviço ou rotacionar chave afeta todos os aparelhos.

- Alpha.19: tela do aparelho conectado deve priorizar cabeçalho compacto com ações principais no topo, resumo visual em grade e atalhos compactos lado a lado. Funções futuras podem aparecer como “Em breve”, mas sem simular implementação.

- Alpha.20: `AppVersion.NAME` deve seguir o `versionName` do Gradle rigorosamente. QR, snapshots e `disconnect` autenticado exigem **igualdade exata** de versão em ambos os aparelhos. Não confundir versão do aplicativo com protocolo `v=1`.
- Alpha.20: `IncomingAccessActivity` em primeiro plano alerta em qualquer área do app e volta ao Monitorado quando o acesso termina; em segundo plano usar apenas notificação Android, sem iniciar tela automaticamente. Identidade/método informados não são verificados; consultas têm expiração de 45s, não conexão permanente. Parada explícita deve permanecer parada.
- Alpha.20: configurações em grade com ícones vetoriais, bateria compacta e ações remotas com toque adequado; não copiar diálogos antigos em lista simples. Rede remota não pode inventar SSID.

- Alpha.21: correção de compilação do workflow 21: no `ScrollView.apply` de `IncomingAccessActivity`, qualificar `this@IncomingAccessActivity.background` para impedir colisão com `View.background: Drawable`; exigir teste de regressão no validador antes de distribuir. Sem alteração de protocolo além da versão obrigatoriamente sincronizada.

- Alpha.22: interface dividida em `UiComponents`, `HomeScreenUi`, `MonitorScreenUi`, `SettingsScreenUi` e `RemoteDashboardUi`, usando extensões internas de `MainActivity`. Manter uma implementação por função e expandir `tools/check_distribution.py` para verificar também módulos externos. Não reintroduzir funções visuais extraídas na Activity sem justificativa. Preservar identidade visual, protocolos, consentimento e comportamento de acessos antes de mudanças arquiteturais mais profundas.


- Alpha.23: `IncomingAccessActivity` deve permanecer enxuta; a UI da tela de acesso recebido fica em `IncomingAccessScreenUi.kt`. Não reintroduzir o pop-up “Quem está?” se as informações puderem aparecer na tela principal; preferir lista/stack inline para acessos adicionais.

- Alpha.24: `MainActivity` coordena ciclo de vida/navegação; estados/consultas ficam em `RemoteConnectionManager`, callbacks em `ConnectionController`, diálogos de pareamento em `PairingScreenUi` e QR em `PairingQrUi`, com worker separado em `PairingController`. Credenciais locais ficam em `MonitorCredentialsUi`.
- Preservar a invalidação de requisições obsoletas ao trocar/desconectar aparelhos. Ao parear por QR ou conectar manualmente, sempre salvar o host selecionado antes de consultar. Não mover chaves para preferências em texto puro, nem autorizar operações remotas sem consentimento.

- Alpha.25: Em `IncomingAccessScreenUi.kt`, dentro de `LinearLayout.apply`, construtores `View` e `LinearLayout` devem receber `this@infoMetric`, não `this` implicitamente. Rodar `tools/check_distribution.py` e compilar o Android após refatorações.

- Alpha.26: preservar painel compacto de três cartões e seis funções remotas com ícones vetoriais nativos. Detalhes técnicos devem ficar em `RemoteDetailsActivity` e usar apenas dados realmente retornados pelo monitorado. Não fingir acesso a arquivos: Recuperar dados permanece "Em breve" até implementar fluxo autorizado de transferência. Nunca coletar/transmitir número de chip sem opção explícita e permissão `READ_PHONE_NUMBERS`. Não adicionar `READ_PHONE_STATE` ou outros acessos implícitos por conveniência.

- Alpha.27: manter a linguagem visual da tela Detalhes do aparelho (cartões claros/escuros, ícones vetoriais, estados curtos e ações lado a lado) também em Home, Monitorado, Conectar e Configurações. `UiComponents.kt` é o ponto central da padronização. Não retroceder para menus nativos sem necessidade funcional; não comprometer o QR temporário, FLAG_SECURE nem a cópia explícita e sensível de credenciais.

- Alpha.28: usar `showManagedDialog` ou `showAppNotice`/`showAppConfirm` para diálogos de `MainActivity`, nunca abrir dois diálogos internos simultaneamente. Erro em campo manual deve ser exibido inline sem descartar campos.
- Mensagem de versão incompatível autenticada do servidor deve permanecer cifrada com a chave já validada. Nunca revelar versão a clientes sem chave válida; não afirmar que EOF prova incompatibilidade. `ConnectionIssue` descreve hipóteses e o diagnóstico copiado não inclui segredo.

- Alpha.29: `MonitorScreenUi.showIncomingAccessActions` cria o `AlertDialog` uma única vez e chama `showManagedDialog(dialog)` uma única vez. Evitar a terminação `.create().let { showManagedDialog(it) }` em conjunto com novo `.create()`. O validador exige essa invariável após o erro `:app:compileDebugKotlin` no workflow 29.

- Alpha.30: compartilhar SMS, permitir SMS enviados e responder notificações são três opções independentes inicialmente desligadas. A consulta SMS é sob demanda (`includeSms=true` só quando Mensagens é aberta), sem RECEIVE_SMS e sem persistência; a leitura depende de READ_SMS, que pode ser restrita por instalador. O comando de resposta SMS usa SEND_SMS e número já visto na inbox, confirma no controlador, limita taxa/corpo e não garante entrega. RemoteInput somente em ação ativa e permitida, token único com expiração, e confirmação do controlador. Comandos cifrados no mesmo protocolo e chave, com proteção de replay e versão exata. Bloquear códigos, apps financeiros e parar ao revogar; nenhum acesso furtivo ou bypass das proteções Android.

- Alpha.31: novas permissões SMS (`RECEIVE_SMS`, `READ_PHONE_STATE`) e opção ampla de notificações são consentidas separadamente no monitorado e ficam desativadas por padrão. Nunca tentar contornar hard restricted READ_SMS. Mensagens SMS ao vivo são temporárias e consultadas quando Mensagens abre/atualiza, não transmitidas em push oculto. Limite de snapshots, filtros financeiros/OTP, escolha validada de SIM ativo, botão Copiar explícito. Não usar `FLAG_SECURE` em Mensagens, mas **sempre** proteger credenciais e QR; envio exige revisão e confirmação no controlador, sem comandos em lote. MMS inclui apenas metadados quando provider concede. Resultados de entrega SMS podem ser incertos quando operador não confirma. Conservar chave autenticada e versão exata entre aparelhos.
