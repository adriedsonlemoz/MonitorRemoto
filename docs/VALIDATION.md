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
