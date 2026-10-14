# Monitor Remoto v0.1.0-alpha.31

## Implementado no código-fonte

- Permissão explícita para SMS em tempo real (`RECEIVE_SMS`), com buffer RAM, exibido na atualização manual.
- Consulta sob demanda de até 60 SMS (recebidos/enviados), status efêmero de envio e entrega via callbacks do sistema, e seleção de chip se houver READ_PHONE_STATE.
- Consulta opcional de metadados MMS (assunto/data quando provider concede), sem anexos e sem se declarar app padrão de SMS.
- Opção explícita para notificações de todos os aplicativos elegíveis ou lista individual. Filtros de aplicativos financeiros, notificações secretas e códigos suspeitos continuam.
- Botões **Copiar** e **Responder** nas telas de SMS/notificações. Responder via `RemoteInput` ou SMS com revisão/confirmacão e autorização separadas.
- Remoção de `FLAG_SECURE` somente dos diálogos de Mensagens e respostas, para capturas de tela. QR e credenciais continuam protegidos.
- `versionCode 31` e versão exata exigida em ambos os aparelhos.

## Limites e testes pendentes

- O fluxo de SMS pode ser negado por restrições do instalador/Android, ainda que não exista publicação na Play Store.
- MMS não fornece corpo/anexos nem recepção completa; Android pode negar até metadados.
- O receptor detecta SMS no monitorado; o controlador só vê ao atualizar. Estados de entrega são voláteis e podem faltar; código pode sumir após reinício do processo.
- Notificações podem não oferecer resposta; WhatsApp antigo não é lido. Filtro OTP é heurístico, sem cobertura perfeita.
- Compilação Gradle/Android, testes reais, e atualização entre versões ainda dependem do GitHub Actions e aparelhos de teste.
