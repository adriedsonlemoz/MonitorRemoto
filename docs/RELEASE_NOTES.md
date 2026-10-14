# Monitor Remoto v0.1.0-alpha.24

## Novidades da alpha.24

- Segunda etapa da refatoração para reduzir a `MainActivity` a cerca de 322 linhas.
- Novo gerenciador de conexão remota independente de Views, com cache, atualização e invalidação de respostas de conexões antigas.
- Diálogos de conexão manual, dispositivos salvos, QR Code e credenciais separados em módulos próprios.
- A tela de acesso recebido continua mostrando nome, IP, método e horários na mesma tela, com o ícone centralizado e ação Parar acesso.
- A conexão por QR/manual agora define o host ativo antes da consulta inicial.
- Nenhuma permissão nova e nenhuma mudança intencional de layout ou protocolo além do identificador de versão exigido.

## Compatibilidade

Os dois aparelhos precisam executar **alpha.24**, pois a verificação da versão exata permanece ativa.

## Validação pendente

Ainda é necessário executar o GitHub Actions com o compilador Android e testar em dois celulares, incluindo o fluxo de QR, conexão manual, consulta periódica e desconexão. O ZIP é código-fonte, não APK.
