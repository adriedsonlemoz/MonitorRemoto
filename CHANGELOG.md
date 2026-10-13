# Histórico de versões

## 0.1.0-alpha.8 (13/10/2026)

- Correção do build GitHub Actions #7: duplicidade do override de `onRequestPermissionsResult` em `MainActivity.kt` (422 e 580) causava `Conflicting overloads`. Unificado em callback único com tratamento independente de câmera e permissão de notificações.
- `POST_NOTIFICATIONS` mantém solicitação condicional a Android 13+ para ativar monitor; `CAMERA` permanece solicitada apenas quando o usuário abre o leitor QR.
- Workflow não tenta criar Artifact vazio caso Gradle pare antes da geração do APK.
- Teste estrutural regressivo exige callback único e os dois fluxos distintos; documentação sincronizada, `versionCode 8`.
- Mantida compatibilidade com a keystore Release gerada anteriormente (nenhuma chave nova).
- Build Android e QR no dispositivo ainda dependem do GitHub Actions e de ensaios físicos.


## 0.1.0-alpha.7 (13/10/2026)

- Correção da falha real GitHub Actions #6: 3 testes `EventJournalTest` acionavam métodos de `org.json` mockados em JVM Android; adicionada projeção `EventJournal.latest` independente de serialização JSON e testes adaptados. As funcionalidades reais do buffer e filtro são preservadas.
- Leitor QR passa a usar Activity explícita privada `QrScanActivity`, sem flag `clearTaskOnLaunch` herdada da Activity genérica; permissão de câmera solicitada e tratada antes de abrir o scanner; rotação desbloqueada e modo selecionado salvo nas recriações de Activity.
- Fluxo manual por IP/chave preservado. Hipótese sobre fechamento do QR ainda exige logcat/ensaio físico para diagnóstico definitivo.
- Preparação para uso de assinatura estável através dos quatro Secrets do workflow, sem incorporar chaves no código-fonte.
- versionCode 7 e sincronização de arquivos/versionamento.


## 0.1.0-alpha.6 (13/10/2026)

- Corrigido vazamento lógico do histórico volátil: interrupção do monitoramento, reinicialização de sessão e encerramento do serviço removem os eventos em memória; alteração da lista positiva remove notificações dos aplicativos revogados.
- Servidor revalida a lista positiva antes de transmitir os eventos, inclusive nos casos de corrida entre atualização da permissão e captura da notificação.
- Fila de conexões limitada a 12, com 3 trabalhadores; conexões excedentes são fechadas, sem fila ilimitada de sockets.
- Tipagem explícita de `java.util.ArrayDeque` para manter APIs de fila compatíveis com Kotlin/JVM.
- Nenhuma permissão nova, mudança de protocolo, segredo ou dependência; versão, documentação e verificações de regressão atualizadas.
- Build Android/CI e testes físicos entre aparelhos seguem pendentes de execução.

## 0.1.0-alpha.5 (13/10/2026)

- Corrigida a falha real do workflow GitHub Actions #4 (`:app:compileDebugKotlin`): `Unresolved reference QR_CODE_TYPES` e erro de encadeamento `setPrompt`.
- Substituída a constante inexistente `IntentIntegrator.QR_CODE_TYPES` pela constante suportada `IntentIntegrator.QR_CODE` do ZXing Android Embedded 4.3.0, com configuração explícita do scanner.
- Adicionadas verificações de regressão em `tools/check_distribution.py` para API do scanner e sincronização de `versionCode = 5`/`versionName = 0.1.0-alpha.5`.
- Mantidos QR temporário, conexão manual, protocolos locais, segurança, permissões, geração do APK direto em Releases e AAB opcional.
- Build Android completo e instalação física continuam pendentes após esta correção.

## 0.1.0-alpha.4 (versionCode 4)
- Pareamento presencial por QR local com código válido por 120 segundos e uso único, senha temporária e resposta cifrada com credencial permanente.
- Scanner de QR integrado, confirmação de IP e salvar chave via Android Keystore. Pareamento manual mantido.
- Validação do bind da porta 37841 antes do serviço ser marcado como iniciado; revogação de códigos QR ao parar serviço ou trocar a chave.
- Release passa a ser fonte primária do APK; Artifact de APK somente como contingência para execução sem publicação ou com falha.
- Documento de protocolo QR e riscos do Play Protect. O bloqueio por PHA da alpha.3 não foi solucionado nem reproduzido por testes locais.

## 0.1.0-alpha.3 (versionCode 3) — 2026-10-13

- Gerar arquivo real `MonitorRemoto-v0.1.0-alpha.3.apk`, publicado em GitHub Releases sem ZIP.
- Manter `upload-artifact` como alternativa (download do GitHub sempre vem ZIP).
- Preparar build Release assinado com quatro Secrets opcionais e AAB assinado, com fallback Debug.
- Adicionar validação automática da distribuição, hash SHA-256, documentação e diretrizes `AGENTS.md`.
- Sem alterações nas permissões sensíveis, mensagens e protocolo local da alpha.2.

## 0.1.0-alpha.2 (versionCode 2) — 2026-10-13

- Workflow corrigido: `android-actions/setup-android@v4` elimina falha no pacote obsoleto `tools`.
- Corrigida atribuição errada de cor (`setTextColor(text)`) no campo de texto da UI.
- Retirada a permissão `RECEIVE_SMS` e o receptor SMS. SMS novos só serão exibidos quando presentes em notificações permitidas.
- Nova lista positiva de aplicativos para notificações, vazia por padrão.
- Aviso visível antes de direcionar ao acesso a notificações; proteção conservadora contra conteúdo de autenticação e bancos conhecidos.
- Apps financeiros conhecidos excluídos da seleção; `queries` de launcher para listagem sem `QUERY_ALL_PACKAGES`.
- Documentação e artifact sincronizados. Ainda requer compilação Gradle e validação em aparelho.

## 0.1.0-alpha.1 (versionCode 1) — 2026-10-13

- Projeto inicial Kotlin Android com modos Monitorado e Controle.
- Telemetria de bateria, temperatura, tensão e correntes.
- Captura autorizada de notificações e SMS novos opcionais.
- Canal TCP cifrado AES-256-GCM, Android Keystore, chave pareada, serviço foreground.
- Workflow GitHub Actions de testes e APK Debug.
