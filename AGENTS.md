# Convenções permanentes do projeto Monitor Remoto

- Toda alteração de código incrementa sequencialmente `versionCode` e `versionName` (ex.: `0.1.0-alpha.7` -> `0.1.0-alpha.8`). Sincronizar tela, README, CHANGELOG, RELEASE_NOTES e documentação.
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
