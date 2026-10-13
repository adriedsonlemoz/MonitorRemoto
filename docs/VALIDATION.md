# Validação e riscos conhecidos

- [ ] `gradle :app:testDebugUnitTest :app:assembleDebug` em CI (não executado neste ambiente sem Gradle/SDK).
- [ ] Instalação Android 8 a 16 e permissões solicitadas.
- [ ] Acesso às notificações: revisar tratamento de configurações restritas em instalações por APK.
- [ ] Notificações de novos SMS via aplicativo de mensagens autorizado (sem `RECEIVE_SMS`).
- [ ] Tela bloqueada e duração mínima de 1 hora no monitorado.
- [ ] Reconexão após alternar Wi-Fi/rede de dados e religar VPN.
- [ ] Stop/Start e troca de chave bloqueiam clientes antigos.
- [ ] Reboot e políticas de economia de energia do fabricante.
- [ ] Medição da corrente contra USB power meter e outro app.
- [ ] Auditoria de vazamento, ataques por replay, excesso de conexões e políticas de privacidade.

**Validações locais (alpha.3):** verificação estrutural de permissões, workflow e versões; parser PSI Kotlin de todos os arquivos `.kt` sem erros sintáticos; 12 verificações isoladas de criptografia, chaves e filtragem com Kotlin/JVM; XML parseado e integridade/CRC do ZIP. Não representam compilação Android completa nem teste instrumentado. Nenhum APK foi produzido nem testado em dispositivo nesta sessão.

**Pendências alpha.3:** indicador real de porta em escuta; callback de erro de bind; descoberta NSD/mDNS ou QR; limites de tráfego por origem; revisão do filtro de mensagens sensíveis; migração para histórico criptografado opcional.

### Distribuição alpha.3

- [x] `tools/check_distribution.py`: versões, nome do APK, Release, assinatura opcional, AAB e manifesto; **aprovado localmente**.
- [ ] `gradle :app:testDebugUnitTest :app:assembleDebug` no GitHub Actions.
- [ ] Com Secrets: `gradle :app:assembleRelease :app:bundleRelease` e compatibilidade de assinatura.
- [ ] Validar upload direto do `.apk` na Release e instalações/atualizações entre versões no aparelho.
- [ ] Testar fluxo de erro de GitHub 403/500 com permissões do repositório.
- [ ] Verificar update da versão alpha.2 no mesmo aparelho: assinatura Debug dos runners diferentes pode impedir.

`upload-artifact` sempre gera ZIP ao fazer download; a Release disponibiliza APK direto.

**Executado localmente na alpha.3:** `tools/check_distribution.py` passou; YAML lido pelo PyYAML sem erro; 14 etapas do workflow verificadas com `bash -n`; XML do Manifest parseado; 12 verificações Kotlin/JVM reais de criptografia e filtros passaram (`kotlinc` e `java -jar`). Ausência de Android SDK/Gradle impede afirmar compilação Android completa ou criar APK neste ambiente.


## alpha.4

**Testes locais concluídos:** verificação da distribuição e sincronização da versão; parser PSI Kotlin em 18 arquivos sem erros sintáticos; 28 asserções isoladas Kotlin/JVM para QR, faixas IPv4, uso único, invalidação e criptografia; XML/YAML/ZIP (a conferir antes da entrega). Nenhum build Android completo foi executado neste ambiente.


- [ ] GitHub Actions: compilar e executar testes Kotlin/JUnit com a dependência ZXing.
- [ ] Instalar alpha.4; testar QR com Wi-Fi comum e hotspot, permissão de câmera, tempo limite, expiração, uso único e revogação.
- [ ] Confirmar compatibilidade de assinatura ao atualizar a alpha.3 (certificate Debug temporário pode impedir atualização).
- [ ] Confirmar que a Release fornece APK direto e nenhum Artifact duplicado numa execução bem-sucedida.
- [ ] Verificar classificação real no Play Protect (a alpha.3 foi bloqueada como potencialmente nociva); auditoria ou contestação necessária.
- [ ] Ensaiar fluxo de notificações e permissões sem perda de segurança bancária.

## Correção do workflow #4 (alpha.5)

- Fonte: execução GitHub Actions `38457828570` em 13/10/2026, tarefa `:app:compileDebugKotlin`.
- Erros reais: `MainActivity.kt:404:60 Unresolved reference 'QR_CODE_TYPES'` e `MainActivity.kt:405:18 Unresolved reference 'setPrompt'`.
- Ajuste: `IntentIntegrator.QR_CODE` com configuração independente do scanner; API comprovada no código da dependência JourneyApps 4.3.0.
- Verificações locais: sintaxe Kotlin, testes isolados de classes independentes de Android, testes de integridade e regras de distribuição.
- Não foi realizada compilação Android completa neste ambiente.

## alpha.6 (13/10/2026)

- [x] Análise dos pontos de retenção: desligar monitoramento, iniciar nova sessão, editar lista permitida e gerar resposta.
- [x] Adicionados testes unitários `EventJournalTest` para limpar, podar eventos revogados e filtrar no envio.
- [ ] Validar os testes JUnit pela tarefa Gradle oficial no CI.
- [ ] Confirmar em dois celulares o comportamento da revogação quando há notificações simultâneas.
- [ ] Testar tráfego abusivo e reconexão do servidor com cargas reais.
- [ ] Investigar classificação Play Protect com diagnóstico da versão/build/assinatura.

**Verificações locais concluídas nesta entrega:** 16 asserções Kotlin/JVM sobre classes reais do projeto (com implementação mínima de `org.json` somente no harness externo ao ZIP); compilação Kotlin isolada de `MonitorServer`, `SettingsStore`, `EventJournal` e transportes com stubs mínimos; `tools/check_distribution.py`; parse do `AndroidManifest.xml` e do YAML da CI; validação de ausência de keystore no ZIP. A compilação Android oficial via Gradle, os testes JUnit no plugin Android e os testes físicos **não foram executados** neste ambiente.

## alpha.7: análise do workflow #6 e scanner (13/10/2026)

- GitHub Actions #6: `:app:compileDebugKotlin` concluiu, 12 testes iniciados, 3 falhas do `EventJournalTest` nas linhas 12, 21 e 28 (chamadas aos métodos mockados `org.json` de testes Android na JVM). `:app:assembleDebug` não chegou a terminar porque Gradle interrompeu na tarefa de testes.
- Testes de eventos passaram a validar `latest()` (registros Kotlin puros), sem depender do `org.json` no classpath de teste JVM. O código de produção continua serializando com `latestJson()`; não foi removida criptografia nem filtro de apps.
- Registrada Activity de scanner própria, com tratamento de permissão; sem logcat de dispositivo não é possível atribuir causa definitiva ao fechamento relatado.
- [ ] Workflow alpha.7 `:app:testDebugUnitTest`, `:app:assembleRelease`, `:app:bundleRelease` com Secrets.
- [ ] Testar scanner após primeira concessão, recusa, retorno e rotação em dois dispositivos.
- [ ] Confirmar impressão digital do APK assinado e que alpha.8 instala em cima do alpha.7 assinado.

### Execução local alpha.7

- [x] 12 testes Kotlin/JVM de `EventJournalTest`, `SensitiveContentTest`, `CryptoBoxTest`, `PairingCodeTest`: passaram em harness JUnit compatível, com JSON mínimo apenas externo ao ZIP.
- [x] Parser PSI Kotlin: 20 arquivos `.kt` verificados, zero erros sintáticos.
- [x] `python3 tools/check_distribution.py`: aprovado.
- [x] XML do AndroidManifest e YAML do workflow válidos.
- [x] Keystore PKCS#12 privada gerada e validada via `keytool`; Secrets são arquivos SEPARADOS, não incluídos no fonte nem no Drive.
- [ ] Compilação Android Gradle da alpha.7: pendente GitHub Actions (sem Android SDK/Gradle neste ambiente).
- [ ] Teste em telefone Android do QR e checagem do certificado do APK assinado: pendentes.

## alpha.8: workflow #7 (13/10/2026)

- Log real: `:app:compileDebugKotlin FAILED` com dois erros `Conflicting overloads` em `MainActivity.kt` linhas 422 e 580. Não houve build de APK; não é uma falha do scanner em aparelho.
- Removida duplicação; todos os retornos de permissão estão em um único override com branches independentes para câmera e notificações. O teste `tools/check_distribution.py` passou a exigir esta condição.
- Artifact de contingência só é executado após empacotamento bem-sucedido, evitando aviso por arquivo inexistente quando a compilação falha.
- [ ] Executar `gradle :app:testDebugUnitTest :app:assembleDebug` no GitHub Actions para alpha.8.
- [ ] Executar `:app:assembleRelease :app:bundleRelease` com os quatro Secrets de assinatura **já configurados**.
- [ ] Validar leitura QR, recusa/concessão de permissão, orientação, retorno ao Controle e pareamento em dois aparelhos Android.
- [ ] Confirmar que alpha.8 assinada com a **mesma** keystore consegue atualizar a alpha.7 se esta foi instalada com a assinatura Release equivalente.

## alpha.9: substituir caminho de captura que continuava encerrando

- [x] Remover `zxing-android-embedded` do Gradle e `CaptureActivity` do fluxo; implementar scanner Camera2 nativo independente.
- [x] Adicionar `QrFrameDecoderTest` para luminância de QR gerado e entrada inválida (código do teste presente).
- [x] Adicionar fallback manual do texto do QR com confirmação do destino.
- [ ] Executar testes unitários Android pelo Gradle/CI, inclusive `QrFrameDecoderTest`.
- [ ] Confirmar que abrir QR com permissão concedida/negada não fecha o app.
- [ ] Testar câmera traseira, orientação, autofocus e leitura de QR entre dois aparelhos.
- [ ] Se ainda houver encerramento, coletar logcat com `FATAL EXCEPTION` e `MonitorRemotoQR` sem compartilhar QR ou chaves.
- [ ] Confirmar continuidade da assinatura no update da alpha.8 para alpha.9.

## alpha.10: interface e segurança dos aparelhos recentes

- [x] Inspeção do código alpha.9 como fonte; versionCode atualizado para 10.
- [x] Inserido cartão Controle com botão pequeno ↻, status por consulta e última atualização real.
- [x] Diálogo Conectar com sugestão CIDR baseada em Wi-Fi, botão Colar, QR e lista recente.
- [x] Separadas credenciais por aparelho e definida migração única das antigas, sem exportar chaves.
- [x] Tema claro padrão e opções escuro/sistema, guardados em preferências.
- [ ] Compilar :app:testDebugUnitTest :app:assembleDebug no GitHub Actions e verificar se dialogs cabem em tela reduzida.
- [ ] Testar fluxo de migração alpha.9 -> alpha.10 em celular sem perder pareamento e com APK de mesma assinatura.
- [ ] Testar QR Camera2, reconexão, múltiplos aparelhos, Wi-Fi com máscara diferente de /24 e VPN.

### Resultado local da alpha.10 (13/10/2026)

- **PASSOU:** `python3 tools/check_distribution.py` (6 grupos de invariantes de versões, segurança, scanner e interface).
- **PASSOU:** parser PSI de Kotlin: 25 arquivos `.kt`, zero erros sintáticos.
- **PASSOU:** 16 testes unitários Kotlin/JVM executados com executor JUnit compatível local (12 anteriores + 4 novos de IP/CIDR). Os testes antigos foram executados a partir das classes compiladas previamente da alpha.9, cujo código de produção não foi alterado na alpha.10, e os quatro novos foram compilados da fonte atual. Esse procedimento **não** substitui o Gradle/Android.
- **PASSOU:** parsing de Manifest XML e workflow YAML; CRC/ZIP da distribuição.
- **NÃO EXECUTADO:** compilação `:app:compileDebugKotlin`, `:app:assembleDebug` e testes JUnit pelo Gradle devido à ausência de Android SDK/Gradle completo no ambiente local.
- **NÃO EXECUTADO:** migração de credenciais em Android físico, comportamento dos temas/popup em telas reduzidas, QR Camera2 em aparelho real e atualização do APK assinado.

## alpha.11: tela inicial em Controle, menu e pop-up Conectar refinado

- [x] Abrir o app diretamente no Painel Controle; remover as abas fixas da tela principal.
- [x] Mover o modo Monitorado para menu dedicado, mantendo o mesmo APK e os fluxos existentes.
- [x] Reorganizar o diálogo Conectar em seções com campos mais visíveis e melhor separação visual.
- [x] Manter leitura QR Camera2, aparelhos recentes e chaves por host.
- [ ] Compilar `:app:assembleDebug` / `:app:testDebugUnitTest` no GitHub Actions após a mudança visual.
- [ ] Testar visualmente em aparelho físico o cabeçalho, o menu e o pop-up em telas pequenas.
- [ ] Confirmar atualização sobre a alpha.10 com a mesma assinatura Release.

### Resultado local da alpha.11 (13/10/2026)

- **PASSOU:** `python3 tools/check_distribution.py` após sincronização de versão e regras de distribuição.
- **PASSOU:** 16 testes Kotlin/JVM locais (core/crypto/pairing), executados por runner compatível com JUnit construído localmente, cobrindo `EventJournal`, `Ipv4Suggestion`, `SensitiveContent`, `CryptoBox` e `PairingCode/PairingSession`.
- **PASSOU:** integridade estrutural do ZIP final.
- **NÃO EXECUTADO:** compilação Android completa com SDK/Gradle, `QrFrameDecoderTest` dependente de ZXing no ambiente local, testes visuais da nova interface, leitura Camera2 em hardware e atualização do APK assinado em aparelho.

## alpha.12: Monitorado compacto

- [x] Modo Monitorado limitado a status, IP, QR e ativar/interromper contextuais.
- [x] Configurações categorizadas; operações de permissão, segredo, revogação e inicialização preservadas.
- [ ] Build Android completo pelo GitHub Actions.
- [ ] Verificar visual e funcionamento em dois celulares reais.
