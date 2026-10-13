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

**Validações locais (alpha.2):** verificação estrutural de permissões, workflow e versões; parser PSI Kotlin de todos os arquivos `.kt` sem erros sintáticos; 12 verificações isoladas de criptografia, chaves e filtragem com Kotlin/JVM; XML parseado e integridade/CRC do ZIP. Não representam compilação Android completa nem teste instrumentado. Nenhum APK foi produzido nem testado em dispositivo nesta sessão.

**Pendências alpha.2:** indicador real de porta em escuta; callback de erro de bind; descoberta NSD/mDNS ou QR; limites de tráfego por origem; revisão do filtro de mensagens sensíveis; migração para histórico criptografado opcional.
