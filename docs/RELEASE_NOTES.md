# Monitor Remoto v0.1.0-alpha.85 · Arquivos, painel e localização

## Novidades
- Gerenciador de arquivos: histórico local e voluntário de resultados de download, upload, exclusão, movimento e demais operações autenticadas. Até 48 eventos dos últimos 30 dias. Não armazena IP, nomes, caminhos, hashes nem conteúdo. Filtrado por aparelho salvo e removível no controlador, sem alterar a origem remota.
- Indicação mais evidente de pasta somente leitura ou leitura/escrita e último resultado registrado.
- Painel: funções em duas colunas com alvos de toque maiores; cartão distingue resposta autenticada recém-obtida de dados armazenados e mostra última atividade de arquivos quando houver.
- Localização: indicador separado de idade e precisão reportadas, estado antigo/sem resposta e fundo do mapa adaptado ao tema. Tiles online continuam desativados por padrão, com autorização por aparelho.

## Segurança e limites
- Nenhuma nova permissão ou comando remoto. Histórico apenas local e limitado a metadados mínimos; não é um histórico remoto do celular monitorado.
- Transferências continuam protegidas por AES-GCM e SHA-256. A retomada persistente após fechar a tela **não** está disponível; em caso de interrupção é necessário iniciar novamente.
- Não foram acrescentadas sondagens periódicas de GPS, câmera ou arquivos.

## Validação
- Checagens sintáticas e estruturais locais; build Gradle/Android e testes em aparelhos ainda pendentes. Instale a mesma alpha.85 nos dois aparelhos após compilação pelo GitHub Actions.
