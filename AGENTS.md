# Monitor Remoto 0.1.0-alpha.17
# Convenções permanentes do projeto Monitor Remoto

- Toda alteração de código incrementa sequencialmente `versionCode` e `versionName` (ex.: `0.1.0-alpha.8` -> `0.1.0-alpha.9`). Sincronizar tela, README, CHANGELOG, RELEASE_NOTES e documentação.
- O artefato instalável da CI deve ter **nome real de arquivo** `MonitorRemoto-v<versionName>.apk`, sem `.zip` no final.
- O GitHub Actions `upload-artifact` sempre entrega ZIP ao baixar um Artifact. Publicar Artifact APK só se Release falhar ou for ignorada, evitando duas fontes para a mesma execução. Para **APK direto**, o workflow **também deve publicar o `.apk` como arquivo de uma GitHub Release** (em workflow_dispatch e push ao branch padrão). Não confundir o pacote ZIP de código-fonte com o APK.
- Opcional: com KEYSTORE_BASE64, KEYSTORE_PASSWORD, KEY_ALIAS e KEY_PASSWORD, gerar APK Release assinado estável e AAB Release; nunca armazenar keystore ou segredos no repositório nem em arquivos públicos.
- Sem Secrets, somente APK Debug para testes, com assinatura temporária; avisar que o APK pode não atualizar sem reinstalação.
- Não acrescentar serviços de acessibilidade, captura de tela, sobreposição, leitura SMS direta ou vigilância oculta. Consentimento expresso; lista positiva de notificações vazia por padrão.
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
- Próxima versão sequencial: `0.1.0-alpha.18`, `versionCode 18`.

- Alpha.15: preservar a seleção Conectar → QR/Manual/Salvos, confirmação do QR antes de parear, cartões salvos com três ações e o scanner em Activity com tema Dialog.

- Alpha.16: Home retorna ao modo Monitorado sem interromper serviço; tela inicial reúne QR, informações manuais e recentes. Sobre fica em Configurações. Chave oculta no pop-up `FLAG_SECURE`, cópia explícita marcada sensível e compartilhamento somente com confirmação prévia. Preservar.

- Alpha.17: Home (modo 2) é a tela inicial, acessível offline; Monitorado (modo 0) e Controle (modo 1) são páginas separadas. Exibir última conexão real persistida por `RecentDevices.markSuccess`; não tratar tentativa como sucesso. Engrenagem de cada aparelho na Home oferece Renomear/Esquecer, nunca expor chave. Botões compactos no cartão Monitorado à direita. Home/Voltar não interrompem serviço. Na alpha.18, priorizar avaliação das telas de dados do aparelho remoto e do aparelho monitorado, sem alterar navegação silenciosamente.
