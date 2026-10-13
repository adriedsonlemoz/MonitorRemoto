# Convenções permanentes do projeto Monitor Remoto

- Toda alteração de código incrementa sequencialmente `versionCode` e `versionName` (ex.: `0.1.0-alpha.3` -> `0.1.0-alpha.4`). Sincronizar tela, README, CHANGELOG, RELEASE_NOTES e documentação.
- O artefato instalável da CI deve ter **nome real de arquivo** `MonitorRemoto-v<versionName>.apk`, sem `.zip` no final.
- O GitHub Actions `upload-artifact` sempre entrega ZIP ao baixar um Artifact. Para **APK direto**, o workflow **também deve publicar o `.apk` como arquivo de uma GitHub Release** (em workflow_dispatch e push ao branch padrão). Não confundir o pacote ZIP de código-fonte com o APK.
- Opcional: com KEYSTORE_BASE64, KEYSTORE_PASSWORD, KEY_ALIAS e KEY_PASSWORD, gerar APK Release assinado estável e AAB Release; nunca armazenar keystore ou segredos no repositório nem em arquivos públicos.
- Sem Secrets, somente APK Debug para testes, com assinatura temporária; avisar que o APK pode não atualizar sem reinstalação.
- Não acrescentar serviços de acessibilidade, captura de tela, sobreposição, leitura SMS direta ou vigilância oculta. Consentimento expresso; lista positiva de notificações vazia por padrão.
- Funcionar primeiro na rede local; não expor a porta na internet pública. Preservar criptografia, parada visível e conexão autenticada.
- Ao entregar versão, reportar status verdadeiro do build Android e disponibilizar ZIP do fonte e, somente após compilação bem-sucedida, o APK; priorizar cópia do fonte no Google Drive.
