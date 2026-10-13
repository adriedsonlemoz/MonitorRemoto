# Monitor Remoto v0.1.0-alpha.13

## Novidades

- Modo Monitorado simplificado e dinâmico: status, IP, QR e ligar/desligar. Ajustes avançados agrupados na engrenagem.

- Tela inicial abre direto no Painel Controle, sem abas fixas na tela principal.
- Cabeçalho reorganizado com **Conectar**, **☰** e **⚙**; chips de versão e do estado do monitorado; atualização manual pelo ícone **↻** e automática a cada 15 s com tela aberta.
- Pop-up **Conectar aparelho** redesenhado em blocos separados: endereço, chave, QR Code e aparelhos recentes, com campos mais visíveis e ações **Colar/Limpar**.
- Aparelhos recentes (máximo 5) seguem com reconexão por toque, renomear e esquecer; credenciais separadas por aparelho e migradas do formato alpha.9 quando possível.
- Temas **claro (padrão)**, escuro e automático; escolha persistida localmente.
- Status "Conectado" vinculado a resposta autenticada recente. Falha marcada "Sem resposta", preservando data do último sucesso.
- Configuração antiga do GitHub Actions, assinatura Release opcional e geração AAB mantidas. **Reutilizar os Secrets/keystore da alpha.7**, não criar outra chave.

## Validação pendente

- Build Android completo e testes em dois aparelhos ainda não executados neste ambiente. A leitura da câmera Camera2 ainda precisa de teste real.
- A classificação anterior do Play Protect exige investigação por meios oficiais; a mudança visual não altera essa classificação comprovadamente.

- Conectar saiu do cabeçalho e foi movido para o cartão do aparelho; quando a consulta está ativa, o botão vira **Desconectar**.
- Menu e Configurações receberam linhas mais legíveis com subtítulos, reduzindo sensação de lista crua.
