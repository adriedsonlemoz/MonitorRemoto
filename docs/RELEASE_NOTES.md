# Monitor Remoto v0.1.0-alpha.18

## Novidades da alpha.18

- Desconectar retorna à Home.
- Janela Conectar com QR Code e Manual lado a lado, sem a opção textual de QR; três ações dos aparelhos anteriores também aparecem horizontalmente.
- Janela Minhas informações com Mostrar, Copiar, Compartilhar na mesma linha.
- Tela Monitorado informa o último aparelho com acesso autenticado (nome e método declarados pelo cliente, IP real da conexão) e permite abrir Ações e interromper serviço ou revogar a chave global. Indicador expira 45 s depois do último acesso.
- Nome/método **não são identidade verificada** e não há sessão TCP permanente nem revogação individual com chave compartilhada.

## Histórico alpha.17

- Nova Home independente do serviço, seleção de aparelhos recentes e atalhos compactos.
- Monitorado separado, retorno por Home/Voltar e estado do Controle preservado.

## Ainda pendente

Compilação Gradle Android na CI e testes físicos em dois aparelhos, inclusive rotação de chaves e timing de atualização. Código-fonte ZIP não contém APK.
