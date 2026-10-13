# Pareamento por QR (v0.1.0-alpha.11)

## Uso

1. Instale o mesmo APK nos dois celulares e conecte ambos ao **mesmo Wi-Fi** ou ao hotspot de um dos aparelhos. A internet não é necessária. Roteadores com **isolamento de clientes** podem bloquear a comunicação local.
2. No **MONITORADO**, toque em `Ativar monitoramento` e depois em **Gerar QR Code para parear**. Se houver mais de uma interface com IP privado, escolha a que está acessível pelo CONTROLADOR (normalmente `wlan0` em Wi-Fi).
3. No **CONTROLE**, toque em **Ler QR Code do outro celular**, autorize a câmera quando solicitada, aponte para a tela do monitorado e confirme o IP exibido.
4. O controlador se comunica diretamente com a porta TCP **37841** por uma credencial temporária cifrada, obtém a chave de longa duração dentro da resposta AES-256-GCM e armazena-a via Android Keystore. IP e chave são salvos; consultas posteriores não exigem novo scan enquanto a chave não for revogada.
5. Se a rede mudar e o IP do monitorado mudar, refaça o pareamento ou ajuste manualmente o IP na tela Controle. **Sem descoberta automática de rede ainda.**

## Protocolo e segurança

O QR guarda `monitorremoto://pair?v=1&host=IPv4_PRIVADO&port=37841&sid=ID&secret=CHAVE_TEMPORARIA`.

- `secret` é chave aleatória **temporária**, não é a chave permanente usada nas consultas. Sua validade é de 120 segundos em relógio monotônico e admite **uma conexão de pareamento**. Novo QR revoga o anterior; fechar a janela revoga a sessão. Parar o serviço e trocar chave também revoga a sessão.
- O IP é restrito às faixas RFC1918. O código deve ser escaneado presencialmente e não deve ser compartilhado por mensageiro ou foto; a janela do QR usa `FLAG_SECURE` para desincentivar screenshots.
- O usuário vê e confirma IP do destino antes da conexão. Um QR de outro aplicativo é rejeitado; campos repetidos, versão incorreta e destinos públicos são rejeitados.
- A chave permanente só trafega **criptografada e autenticada** pela chave temporária, com AES-256-GCM. Somente a chave permanente é armazenada no controlador via Keystore.
- Um atacante com acesso físico à tela do QR durante sua validade pode tentar se parear primeiro. Por isso, mostre apenas a pessoas autorizadas. O sistema não substitui proteção física do aparelho, autenticação do Android nem revisão criptográfica independente.
- Se o pareamento falhar, gere um QR novo. Relógios de ambos devem estar razoavelmente sincronizados (janela de 2 minutos para evitar reutilização).
- Camera2 nativo com ZXing Core 3.5.4; câmera usada localmente, **não** enviada pela rede nem gravada. Projeto deve manter a declaração de câmera como hardware opcional.

## Estado do Play Protect

A captura de notificações permanece declarada no manifesto da variante atual. Um QR Code **não resolve nem contorna** a classificação `App nocivo bloqueado`. A página do Google para desenvolvedores diferencia esse aviso de um mero alerta de aplicativo não verificado e orienta revisão de políticas/contestações. Não desative o Play Protect. Para desenvolver versão apenas de bateria sem acesso às notificações, será necessário uma variante manifest distinta e um novo teste de classificação.

## Fluxo QR alpha.9

A tela `QrScanActivity` agora controla a câmera **nativamente com Camera2**, não usa mais a Activity `CaptureActivity` do ZXing Embedded. Permissão CAMERA é solicitada na tela de controle, e o leitor apresenta erros na própria tela, com botão Cancelar. O conteúdo do QR não é registrado em logs nem em arquivos. O decoder ZXing Core trabalha sobre frames YUV em memória.

Se a câmera continuar indisponível, é possível usar **Inserir código QR manualmente** no Controle, com o URI temporário completo obtido por um leitor de QR externo. Copiar o URI para a área de transferência expõe temporariamente o segredo a outros apps que tenham acesso ao clipboard; use apenas aparelhos confiáveis e não compartilhe o código. **Faltam testes físicos.** Para identificar a causa original exata do encerramento anterior, capturar logcat (`FATAL EXCEPTION`, `MonitorRemotoQR`, `Camera`) imediatamente após tocar em Ler QR.

### Correção de build alpha.8

Foi removida a duplicidade de `onRequestPermissionsResult` que impedia gerar o APK alpha.7. O scanner continua dependendo de permissão explícita e de teste em hardware; nenhum logcat foi disponibilizado para confirmar a causa do fechamento original.

### Mudança de implementação alpha.9

A alpha.8 ainda usava a Activity do ZXing Embedded e o encerramento foi relatado novamente. A alpha.9 removeu o componente de captura antigo e oferece Camera2 nativo, decoder ZXing Core e fallback manual. Isso é uma alteração preventiva de arquitetura, **não comprova** qual foi a exceção original nem que todos os aparelhos estarão livres de falhas.
