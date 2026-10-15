# Modo avançado ADB — implementação alpha.52

## Regressão de comandos autorizados (alpha.55)

A lista fechada de `AdbPolicy.Command` contém **sete comandos**: `DIAGNOSTIC`, `HEARTBEAT`, `BACK`, `HOME`, `RECENTS`, `VOLUME_UP` e `VOLUME_DOWN`. O workflow 53 falhou porque `AdbPolicyTest.commandAllowlist` ainda esperava quatro. Na alpha.55, o teste verifica a lista completa e o comando exato de cada item; não amplia privilégios. O serviço `reboot:` de manutenção é uma operação separada e continua exigindo confirmação explícita.

## Reinício remoto explícito (alpha.53)

O serviço ADB `reboot:` é solicitado somente por toque explícito na opção Reiniciar da Manutenção e confirmação digitada `REINICIAR`, após reconexão ADB autorizada com o mesmo aparelho. Não inclui desligamento, bootloader, recovery, shell arbitrário nem repetição do comando após EOF/timeout. O Android pode não reativar Depuração sem fios após iniciar; em aparelho sem botão Power, há risco relevante de perda de acesso.

## Visualizador de tela com navegação por ADB (alpha.52)

Após estabelecer uma sessão ADB TLS no **Modo avançado ADB**, a ação explícita **Ver tela remota • controle ADB** verifica se existe uma chave de pareamento **normal** do Monitor Remoto para o mesmo IP. Em caso negativo, apresenta as instruções para autorizar a captura de tela no aparelho monitorado, sem fingir que ADB já entrega o vídeo.

Quando a chave normal existe, a Activity transfere a sessão ADB já conectada para `AdbScreenBridge`, ponte volátil no processo. `RemoteScreenActivity` mostra **Navegação: ADB conectado • Imagem: Monitor Remoto (MediaProjection)**. Somente Voltar, Início e Recentes passam pelo ADB. Isso exige uma resposta autenticada do canal normal para o mesmo IP; os toques na imagem, captura JPEG/H.264, áudio e ações de notificações continuam pelo Monitor Remoto e dependem das permissões existentes. Se o ADB perder a conexão, navegação pode usar novamente o canal normal quando a Acessibilidade estiver autorizada.

Ao sair de Ver tela (`onPause`), a ponte e o socket ADB são encerrados. Não há streaming via ADB, reconexão ADB do visualizador ou permanência oculta no fundo. A reconexão automática da alpha.51 continua dentro da tela de configuração ADB enquanto visível; a sessão transferida não herda essa rotina na alpha.52. Revalidar fisicamente o status e o funcionamento dos comandos.

## Reconexão segura da alpha.51

Após sessão ADB confirmada, a tela realiza heartbeat a cada 30 segundos. Se a resposta falhar/expirar, ela encerra o socket e tenta reconectar ao mesmo aparelho usando o último IP/porta e o serviço TLS mDNS encontrado no mesmo IP. A espera entre tentativas cresce até 30 segundos. Há ação explícita para cancelar, e nenhuma tentativa persiste enquanto a tela ADB está fora do primeiro plano. Ao reabrir a mesma Activity, a retomada solicitada pode continuar.

**Limite real:** quando o Android ou o usuário desativar Depuração sem fios, o servidor ADB deixa de escutar. Nenhuma permissão comum permite reativá-la; o usuário ou o sistema Android (somente se suportar rede confiável) precisa reativar. Se o IP mudar ou houver mDNS bloqueado, a UI exige reconexão manual para evitar associar dispositivos errados. Heartbeat envia somente um comando fixo e limitado. Os controles do resumo são navegação e volume, não espelhamento de tela. Não criar shell arbitrário, servidor oculto ou automação de permissões.

## Fluxo compacto da alpha.50

A tela principal contém aparelhos conhecidos, Reconectar/Desconectar, Adicionar outro celular e Configurações avançadas recolhidas. Escolha do papel (controlador/monitorado), QR, código e instruções curtas ficam em diálogos centralizados.

Depois que o pareamento ADB por QR/código é aceito, o aplicativo tenta localizar um único serviço `_adb-tls-connect` no **mesmo IPv4 privado do serviço de pareamento**. Se houver um único serviço encontrado, tenta a conexão TLS; se houver múltiplos anúncios, nenhuma porta, falha ou rede sem mDNS, oferece entrada manual. A descoberta não é validação criptográfica do aparelho e não deve permitir associação automática de outro IP.

A reconexão rápida tenta IP/porta salvos. Quando falhar, procura a porta atual no mesmo IP. Caso o Android informe modelo diferente do salvo, exige confirmação. O nome de modelo/comercial é dado pelo Android e não garante identidade física; a interface não afirma o contrário. A credencial ADB local continua cifrada via Android Keystore.

O telefone monitorado deve abrir **Opções do desenvolvedor → Depuração sem fios** e autorizar o pareamento manualmente. Nenhuma configuração de desenvolvedor é ativada pelo app. O QR fica protegido por `FLAG_SECURE` enquanto visível; cancelar/sair encerra a sessão.

## Escopo entregue

Cliente Android real, em Kotlin com biblioteca Java/JNI: QR e código numérico chamam o protocolo de pareamento; conectar abre um transporte ADB TLS; diagnóstico lê saída real do shell remoto. Nenhum resultado é simulado. A compilação e os testes disponíveis estão registrados em VALIDATION.md; teste físico é uma etapa independente.

O controlador usa Android 8+; o monitorado precisa oferecer Depuração sem fios (normalmente Android 11+). Não exige Termux ou computador durante o uso. A compilação do projeto requer Gradle, Android SDK e acesso aos repositórios de dependências.

## Protocolo e dependências

| Componente | Implementação |
|---|---|
| ADB, streams, pareamento | `adb-core`, fonte libadb-android 3.1.1 incluída; commit em `adb-core/UPSTREAM.md` |
| Canal de pareamento | TLS 1.3 e TLS exporter; SPAKE2 vincula o segredo ao canal; HKDF/AES-GCM protege PeerInfo |
| TLS Android/exporter | Conscrypt Android 2.5.3, API pública da biblioteca; sem HiddenApiBypass |
| SPAKE2 | spake2-android 2.2.1, dependência JNI via JitPack, grupo restrito no Gradle |
| Certificado e primitivas | Bouncy Castle bcprov/bcpkix 1.81 |
| Identidade | RSA 2048 e certificado próprio; PKCS#8+certificado cifrados no SecretVault AES-GCM/Android Keystore |
| QR | ZXing Core 3.5.4 já usado pelo aplicativo |
| Descoberta | Android NsdManager; resolve uma instância por vez |

QR: `WIFI:T:ADB;S:studio-<aleatório>;P:<segredo>;;`. Serviço e senha têm, cada um, 128 bits aleatórios codificados em hexadecimal. A sessão dura 120 segundos, não entra em preferências/Bundle/clipboard e a janela usa FLAG_SECURE. Ao ler, o monitorado inicia seu servidor de pareamento com o nome solicitado; o controlador busca exatamente esse nome em `_adb-tls-pairing._tcp` e executa o handshake. O segredo é consumido na primeira tentativa; erro exige gerar novo QR. Não é um QR com IP/porta nem o QR normal do Monitor Remoto.

O código de seis dígitos usa a porta temporária mostrada no diálogo do Android. A conexão posterior usa a outra porta, anunciada em `_adb-tls-connect._tcp` ou mostrada na tela principal de Depuração sem fios. Não se presume porta 5555. O usuário seleciona o endereço de conexão; não há associação automática entre serviços de aparelhos diferentes.

## Arquitetura e proteção

- `AdbActivity`: tela própria com tema claro/escuro, insets, campos sem autofill/restauração e ações explícitas. Operações em executor serial, resultados invalidados por geração, limite de 35 segundos por ação e conexão encerrada ao sair. mDNS para após dois minutos.
- `AdbPolicy`: aceita somente IPv4 numérico privado RFC1918 e portas válidas. IPv6, hostnames, loopback e endereços públicos não são suportados neste primeiro módulo. Rede privada não equivale a rede confiável.
- `AdbClient`: parear, conectar, desconectar e quatro comandos fixos. Diagnóstico retorna modelo, versão Android e API; navegação exige confirmação. Sem entrada de comandos arbitrários, comandos de PIN, captura, instalação ou shell em segundo plano. Saída limitada a 8 KiB.
- `AdbIdentity`: única identidade ADB local compartilhada entre aparelhos pareados, separada das chaves normais do aplicativo. Apagar a identidade exige novo pareamento em todos eles. A chave privada precisa estar em memória para Conscrypt; em disco está cifrada, não é uma chave RSA não exportável.
- `adb-core`: rejeita AUTH legado e CNXN sem TLS; TCP/handshake com timeout, limite de payload de 1 MiB e fila de 16 payloads, contexto TLS novo a cada identidade/conexão. Fechar conexão não destrói a identidade compartilhada. Pareamento tem cancelamento de socket e cleanup seguro após erro parcial.
- Sem nova porta de escuta no controlador. O aplicativo não ativa depuração no monitorado, não publica roteador/UPnP e não muda o servidor AES-GCM existente.

**Modelo de confiança:** certificados ADB são próprios, sem uma CA pública. Como na biblioteca-base, o cliente não usa PKI/pinning do certificado do daemon na conexão; o pareamento usa SPAKE2 e vinculação ao TLS. IP e nome mDNS não constituem identidade verificada do aparelho. Use rede local confiável; não afirmar autenticação forte do servidor remoto apenas porque a conexão TLS foi aberta. Pinning/associação de identidade do daemon exigem investigação adicional, pois o certificado do servidor de pareamento não deve ser presumido igual ao do daemon de conexão.

As permissões de SMS, MediaProjection e toque do protocolo normal permanecem independentes. A autorização ADB é concedida pelo próprio Android e dá poderes mais amplos que as permissões do Monitor Remoto. Para revogar, no monitorado use Depuração sem fios → Dispositivos pareados → MonitorRemoto → Esquecer, ou desligue a Depuração sem fios. Desconectar no controlador encerra somente a sessão, não revoga a chave no Android. Nenhum mecanismo remove PIN, senha, biometria ou conteúdo protegido.

## Roteiro de teste real em dois celulares

1. Instale a mesma alpha.42 nos dois aparelhos para preservar compatibilidade do protocolo normal. Abra o módulo ADB somente no controlador. No monitorado habilite manualmente Depuração sem fios numa rede confiável.
2. **Código:** abra Emparelhar com código no Android, preencha IP, porta de pareamento e seis dígitos no controlador. Espere o resultado real. Depois use a porta de conexão da tela principal e toque Conectar.
3. **QR:** gere outro QR, escolha Emparelhar por QR no Android monitorado e leia. Espere Pareado pelo QR, selecione o serviço de conexão com o mesmo IP e toque Conectar. Se mDNS não atravessar o roteador, volte ao código/manual.
4. Consulte modelo/versão e compare com Sobre o telefone. Confirme Voltar/Início/Recentes e observe o monitorado. A mensagem de envio não garante que a ROM executou a ação; MIUI pode impor restrições adicionais.
5. Saia da tela, reabra e reconecte sem parear; teste reinício do controlador e portas novas após desligar/ligar depuração. A identidade deve persistir, as portas precisam ser atualizadas.
6. Teste código incorreto, porta trocada, QR expirado, Wi-Fi desligado, mDNS bloqueado, dois aparelhos anunciando serviços e cancelamento durante handshake. Não deve exibir sucesso inventado nem aceitar IP público.
7. Apague a credencial local e esqueça o controlador no Android. Reconectar deve exigir novo pareamento. Teste temas, rotação e saída durante operação; rotação encerra a sessão/QR nesta versão.
8. Regrida o fluxo normal: QR AES-GCM, bateria, mensagens autorizadas, tela MediaProjection, H.264/JPEG, áudio e controles flutuantes. ADB não deve alterar esses caminhos.

## Comparação com scrcpy e próximos passos

scrcpy envia e executa um servidor no monitorado e separa canais de vídeo, áudio e controle, com codificação MediaCodec e pouca acumulação de quadros. Apenas adicionar ADB não muda o transporte de imagens atual. Este módulo não envia servidor scrcpy, não implementa vídeo persistente ADB nem controle de energia do display. Para isso será necessário projetar sessão visível/consentida, compatibilidade por versão/ROM, streaming/decodificação e medições reais de latência, temperatura e energia; manter o requisito do projeto de captura consentida por MediaProjection. Tela física apagada sem encerrar vídeo não é garantida e não é equivalente a desbloqueio.

## Fontes consultadas

- AOSP, arquitetura Wi-Fi ADB: https://android.googlesource.com/platform/packages/modules/adb/+/HEAD/docs/dev/adb_wifi.md
- Android Developers, Wireless debugging: https://developer.android.com/tools/adb#wireless
- Fonte libadb e licenças: https://github.com/MuntashirAkon/libadb-android (commit fixado em UPSTREAM.md)
- Fonte SPAKE2: https://github.com/MuntashirAkon/spake2-java
- Arquitetura scrcpy: https://github.com/Genymobile/scrcpy/blob/master/doc/develop.md

Fontes consultadas em 14/10/2026. Licenças da biblioteca-base em `adb-core/LICENSES`; dependências e obrigações em THIRD_PARTY.md.

## Ajustes da alpha.56

Botões de controle e manutenção receberam espaçamento; o aparelho ativo não é repetido na lista de salvos. Renomear está disponível no painel ADB e em Controles ADB. Quando a consulta shell não informa modelo legível, o rótulo preserva apelido ou modelo anterior, sem declarar identificação certificada. A reconexão da sessão permanece limitada ao tempo em primeiro plano, com intervalos crescentes. A interface do visualizador diferencia tela bloqueada, apagada e MediaProjection encerrada; não tenta desbloquear a tela segura.

A alpha.56 passou a executar heartbeat e comandos ADB no mesmo executor serial, reduzindo concorrência de streams numa única sessão. Heartbeat: intervalo de 45s e tolerância de 12s; perda de conexão real ainda exige diagnóstico físico. O monitor remoto não reativa depuração desabilitada.

## Manutenção alpha.57

A apresentação da função Reiniciar foi padronizada no painel principal, mas sua operação continua **exclusivamente ADB TLS autorizado do mesmo host**, com confirmação digitada `REINICIAR`. O comando é o serviço ADB normal `reboot:`, sem shell `poweroff`/`shutdown`, sem parâmetros de recovery e sem repetição automática. A interface de ADB não configurado oferece iniciar o pareamento. Nenhum comando de reinício é executado apenas por abrir a janela.
