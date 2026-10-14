# Monitor Remoto v0.1.0-alpha.21

## Correção alpha.21

- Compilação do workflow 21 parava em `IncomingAccessActivity.kt:93`: `Drawable` recebido por `setBackgroundColor(Int)`.
- Corrigida a resolução do atributo `background` usando `this@IncomingAccessActivity.background` dentro do `ScrollView`.
- Interface e recursos da alpha.20 preservados. Os dois aparelhos devem executar a mesma versão exata (alpha.21).
- Executar a CI para comprovar a compilação Android e a geração do APK.


## Novidades da alpha.20

- Tela dedicada de aviso de acesso recebido, com identidade declarada do controlador, IP de origem, método informado e horário da última consulta.
- Quem está? e Parar acesso. Encerramento explícito retira o registro do controlador sem interromper o monitoramento; falta de comunicação expira após 45 segundos.
- Acesso detectado com o app visível mostra a tela imediatamente. Em segundo plano, apenas notificação do Android.
- Exigência de mesma versão exata nos dois aparelhos para pareamento QR e consultas remotas.
- Interface de Configurações redesenhada com grade 2×3 e ícones vetoriais; resumo da bateria mais baixo e atalhos de funções maiores.
- Tipo real de rede ativa do celular monitorado disponibilizado sem SSID. Indicação clara de dados anteriores quando não há resposta recente.

## Atenção

**Atualize os dois celulares para a alpha.21 antes de conectar.** Esta é uma versão prévia. Funções de mensagens diretas, espelhamento de tela e localização ainda não estão implementadas. O nome recebido do controlador não constitui identificação independente. A parada do serviço revoga acesso enquanto estiver desligado, mas não substitui rotação de chave.

## Validação pendente

Compilação Gradle Android e teste físico de conexão/QR/desconexão em dois aparelhos. O ZIP distribuído contém somente código-fonte, não APK.
