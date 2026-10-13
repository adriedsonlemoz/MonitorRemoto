# Monitor Remoto v0.1.0-alpha.10

## Novidades

- Interface Controle compacta: nome/status do aparelho, bateria, temperatura, tensão e correntes em cartão prioritário.
- Botões **Conectar** e **⚙** no cabeçalho; atualização manual pelo ícone discreto **↻** e automática a cada 15 s com tela aberta.
- Conexão por pop-up: sugestão de prefixo IPv4 da rede Wi-Fi ativa (respeitando CIDR), edição manual IP/VPN, botão Colar, leitor QR e alternativa inserir texto do QR.
- Aparelhos recentes (máximo 5), reconexão por toque, renomear e esquecer; credenciais separadas por aparelho e migradas do formato alpha.9 quando possível.
- Temas **claro (padrão)**, escuro e automático; escolha persistida localmente.
- Status "Conectado" vinculado a resposta autenticada recente. Falha marcada "Sem resposta", preservando data do último sucesso.
- Configuração antiga do GitHub Actions, assinatura Release opcional e geração AAB mantidas. **Reutilizar os Secrets/keystore da alpha.7**, não criar outra chave.

## Validação pendente

- Build Android completo e testes em dois aparelhos ainda não executados neste ambiente. A leitura da câmera Camera2 ainda precisa de teste real.
- A classificação anterior do Play Protect exige investigação por meios oficiais; a mudança visual não altera essa classificação comprovadamente.
