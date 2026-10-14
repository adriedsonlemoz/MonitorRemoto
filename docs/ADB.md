# Modo avançado ADB — alpha.42

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
