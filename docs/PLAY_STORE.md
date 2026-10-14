# Nota sobre distribuição particular (alpha.30)

O proprietário decidiu **não publicar o Monitor Remoto no Google Play**. As informações abaixo sobre Play são históricas e não determinam os recursos desta versão.

A alpha.30 inclui READ_SMS e SEND_SMS, ambos opcionais. O Android pode bloquear READ_SMS por ser uma permissão *hard restricted*, independentemente da distribuição particular, e não há contorno dessas restrições. A proteção Play Protect também pode classificar APKs instalados externamente; investigar alertas reais, sem desligar a proteção.

---

# Requisitos para uma publicação futura no Google Play (verificado em 2026-10-13)

**Isto não certifica o app para publicação.** A alpha.8 continua em testes e o fluxo de produção precisa de avaliação, política de privacidade hospedada e revisão de consentimento.

## Bloqueadores e mudanças implementadas

- O uso direto de `RECEIVE_SMS` foi removido: a política da Play geralmente exige que o app seja o gerenciador padrão de SMS/Assistente ou se enquadre em exceção aprovada. O Monitor Remoto não é um app de SMS padrão. Isso descreve versões anteriores; na alpha.30 o acesso à inbox pode ser solicitado separadamente com READ_SMS, mas a concessão é limitada pelo Android.
- Não há `AccessibilityService`, captura de tela, sobreposição, `QUERY_ALL_PACKAGES`, nem VPN requerida para modo Wi-Fi.
- A lista permitida de apps inicia vazia e o filtro tenta bloquear serviços financeiros conhecidos, mas **a permissão de notificação do Android permite tecnicamente que o serviço receba notificações de todas as aplicações antes da filtragem**. Isso ainda pode disparar verificações de bancos e exige testes e análise de conformidade.
- Deve existir divulgação em destaque, consentimento claro antes da coleta e opção de revogar. Há um aviso antes da página de configurações na alpha.2; é necessária auditoria de UX e políticas.

## Publicação

1. Criar conta de desenvolvedor no Play Console: taxa única atual de US$25 e verificação de identidade; requisitos adicionais podem variar.
2. Usar `targetSdk=36` (Android 16), conforme obrigatório para novos envios desde 31/08/2026; usar também Android SDK e ferramentas compatíveis.
3. Produzir **Android App Bundle (AAB)** de release assinado com **chave de upload própria**, e ativar/usar Play App Signing. O workflow alpha.8 já gera AAB Release assinado **somente se** os quatro Secrets de assinatura estiverem configurados; não garante publicação ou compatibilidade Play. Nunca coloque keystore ou senhas no código/repositório.
4. Criar página da loja com nome/ícone, imagens reais, capturas de tela, descrição, informações de suporte/contato, classificação etária e declarações de conteúdo.
5. Disponibilizar **política de privacidade pública por URL HTTPS** e dentro do aplicativo; preencher **Segurança dos dados** com coleta, transferência local criptografada, retenção e controles; confirmar classificação correta caso a transferência entre dispositivos conte como coleta no contexto da política.
6. Declarar o serviço foreground `connectedDevice` e justificar a necessidade contínua no Play Console, incluindo demonstração em vídeo se solicitado; comprovar recurso visível e botão de parada.
7. Conta pessoal criada depois de 13/11/2023 exige teste fechado com pelo menos **12 testadores por 14 dias contínuos** para solicitar acesso à produção (política vigente em 13/10/2026).
8. Realizar testes em aparelhos reais e auditoria: falhas de autenticação, exposição acidental de conteúdo bancário/OTP, permissões e dados, conexão em segundo plano, power management e compatibilidade bancária. Aprovação nunca é garantida.

Fontes oficiais:
- https://support.google.com/googleplay/android-developer/answer/11926878?hl=pt-BR
- https://support.google.com/googleplay/android-developer/answer/16558241?hl=pt-BR
- https://support.google.com/googleplay/android-developer/answer/14151465?hl=pt
- https://support.google.com/googleplay/android-developer/answer/10787469?hl=pt-BR
- https://support.google.com/googleplay/android-developer/answer/10144311?hl=pt-BR
- https://support.google.com/googleplay/android-developer/answer/16559646?hl=pt-BR
- https://support.google.com/googleplay/android-developer/answer/6112435?hl=pt-BR


## Observação alpha.4: Google Play Protect

A instalação da alpha.3 foi classificada como `App nocivo bloqueado` pelo Play Protect. Isso **não** equivale a simples aviso de aplicativo desconhecido; a origem exata não foi confirmada. Verificar permissões e comportamento da distribuição, consultar as orientações oficiais de contestação e nunca instruir usuário a desativar proteção. QR e remoção de ZIP não resolvem automaticamente essa classificação. A variante atual ainda contém `NotificationListenerService`.

## Alpha.9

Scanner QR usa Android Camera2 e ZXing Core offline, sem integração com serviços Google de leitura. A permissão CAMERA continua solicitada em tempo de uso, não no início. Essa modificação **não garante** que qualquer classificação prévia do Play Protect tenha sido resolvida; investigar por vias oficiais.
