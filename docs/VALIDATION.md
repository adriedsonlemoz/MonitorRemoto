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
