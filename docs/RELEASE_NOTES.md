# Monitor Remoto v0.1.0-alpha.28

## Novidades

- Avisos e confirmações no padrão de cartões e ícones, com fechamento do diálogo anterior ao abrir a próxima ação.
- Escolha de apps de notificação com lista visual, pesquisa e seleção explícita; sem conteúdo vazio ou pop-ups empilhados.
- Painel **Sem resposta** redesenhado com diagnóstico classificado, ações para tentar novamente, editar endereço/chave e copiar informações técnicas sem segredos.
- **Versões incompatíveis**: resposta criptografada explícita a um cliente previamente autenticado, com a versão do aparelho remoto quando conhecida. Versões antigas podem encerrar a conexão sem se identificar; nesse caso será exibida a informação como não confirmada.
- Mensagens de conexão manual permanecem na própria janela sem perder o texto informado.
- Nenhuma permissão nova ou mudança no acesso às credenciais.

## Segurança e compatibilidade

Instale alpha.28 nos dois aparelhos. O servidor só divulga sua versão a clientes autenticados com uma chave válida. Conexões sem chave e pacotes inválidos não recebem diagnóstico.

## Validação

Executar workflow GitHub Actions e testar conexão com alpha.28 nos dois celulares, incompatibilidade e erros de rede. ZIP contém apenas o código-fonte.
