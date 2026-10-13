# Pareamento por QR (v0.1.0-alpha.8)

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
- Scanner ZXing Android Embedded 4.3.0; câmera usada localmente, **não** enviada pela rede nem gravada. Projeto deve manter a declaração de câmera como hardware opcional.

## Estado do Play Protect

A captura de notificações permanece declarada no manifesto da variante atual. Um QR Code **não resolve nem contorna** a classificação `App nocivo bloqueado`. A página do Google para desenvolvedores diferencia esse aviso de um mero alerta de aplicativo não verificado e orienta revisão de políticas/contestações. Não desative o Play Protect. Para desenvolver versão apenas de bateria sem acesso às notificações, será necessário uma variante manifest distinta e um novo teste de classificação.

## Estabilidade do scanner alpha.7

O código usa uma `QrScanActivity` explícita no Manifest (não exportada), com permissão `CAMERA` solicitada antes de abrir a câmera, tratamento de negação e leitura só de QR. A biblioteca original já inclui um `CaptureActivity`, mas o aplicativo não depende mais das configurações de tarefa dessa Activity genérica. **Ainda faltam testes físicos.** Para identificar a causa exata de um encerramento, capturar logcat (`FATAL EXCEPTION`, `MonitorRemotoQR`, `Camera`) imediatamente após tocar em Ler QR. Sem esse registro não há comprovação de correção para todos os dispositivos.

### Correção de build alpha.8

Foi removida a duplicidade de `onRequestPermissionsResult` que impedia gerar o APK alpha.7. O scanner continua dependendo de permissão explícita e de teste em hardware; nenhum logcat foi disponibilizado para confirmar a causa do fechamento original.
