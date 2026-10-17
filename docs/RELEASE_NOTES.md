# Monitor Remoto v0.1.0-alpha.93 · Localização dinâmica

- Consulta autenticada **somente de localização** (`locationSnapshot`), reduzindo trabalho no servidor sem contornar autorização nem forçar leitura GNSS nova.
- Novo painel de localização com indicadores de conectividade e estado do GPS que não confundem coordenada antiga com posição atual.
- Mostra última resposta, idade do fix, precisão e tempo da requisição; modo **Acompanhar** faz pedidos a cada 3 segundos somente com a tela aberta.
- A posição muda apenas quando o Android monitorado dispõe de um novo fix; consulta de 3 s não garante GPS a cada 3 s.
- Compilação Gradle e teste em dois aparelhos pendentes.

# Monitor Remoto v0.1.0-alpha.91 · Termometria detalhada

- Janela central ao tocar Bateria ou Temperatura, com leituras reais e fontes identificadas.
- Android: bateria, estado térmico, margem térmica e ambiente se houver sensor.
- ADB opcional para host previamente pareado: enumera as zonas expostas via `dumpsys thermalservice`, sem comando remoto arbitrário, root ou acesso a dados pessoais.
- Leituras indisponíveis não são inferidas; validação Android completa ainda pendente.

# Monitor Remoto v0.1.0-alpha.90 · Correção de compilação do menu de arquivos

## Alterações
- Corrige o erro do GitHub Actions `Gerar APK e Release 82`: `RemoteFilesActivity.kt:604-606`, `scroll` indefinido e tentativa de chamar métodos de `AlertDialog?` sem garantir não nulidade.
- O menu inferior passa a possuir uma `ScrollView` local com seu conteúdo; a instância `dialog` de tipo não nulo é usada para mostrar e configurar a janela.
- Mantidos os recursos da alpha.89: Downloads/Monitor Remoto, gerenciamento de arquivos próprios, grade de três colunas, novas Configurações, AES-GCM e SHA-256.

## Validação
- Verificação sintática/estrutural local não substitui `:app:compileDebugKotlin` real. Reexecutar a CI e instalar a alpha.90 em ambos os aparelhos.

# Monitor Remoto v0.1.0-alpha.89 · Menus modernos e Downloads fixos

## Novidades
- **Downloads/Monitor Remoto** criado automaticamente no Android 10+; essa é a única pasta de transferência padrão nos dois aparelhos, sem seletor de pasta.
- Listagem remota restrita a arquivos próprios que o app criou no MediaStore, filtrados por proprietário do pacote e caminho relativo. Não há acesso oculto aos downloads de outros aplicativos.
- Operações autenticadas disponíveis sobre arquivos próprios: download, upload, renomeação, movimentação, cópia, lixeira e exclusão confirmada; diretórios vazios podem ser criados e removidos.
- Modernização de diálogos temáticos em várias telas e menu inferior para ações de arquivo. Mantida grade de 3 colunas.
- Validação e publicação SHA-256, retomada privada de downloads e versão exata nos dois aparelhos preservadas.

## Limitações
- Transferência na modalidade fixa requer Android 10+. Os arquivos que eram acessíveis via árvore SAF na alpha.88 NÃO migram automaticamente. É necessário enviá-los ao Monitor Remoto como arquivos específicos.
- Arquivos pessoais de outros apps em Downloads não aparecem na pasta remota sem autorização específica; nenhum bypass de privacidade/segurança do Android.
- Compilação Android completa e testes reais não disponíveis nesta entrega.

# Monitor Remoto v0.1.0-alpha.89 · Painel em 3 colunas e Configurações pesquisáveis

## Novidades
- Painel compacto com **12 funções em 3 colunas**, mantendo toque e acessibilidade. Detalhes na barra do aparelho.
- Configurações agrupadas em seis áreas com pesquisa local instantânea (aceita acentos) e navegação para as opções atuais.
- Avisos e confirmações centrais em diálogos temáticos compactos. Permissões e consentimentos não foram relaxados.
- Tempo de requisição autenticada em milissegundos junto à última resposta. O relógio da última observação remota permanece distinto.

## Limites
- O intervalo de atualização ainda é de 15 s; RTT mede a duração total da chamada, não velocidade de rede pura.
- Telas especializadas e alguns menus herdados ainda usam diálogos Android e são candidatos às próximas versões.
- Android Gradle completo e testes visuais reais pendentes. Instalar a mesma versão nos dois celulares.

# Monitor Remoto v0.1.0-alpha.87 · Mapa visível e modo recuperação de GPS

## Novidades
- Mapa interno agora renderiza blocos OSM da região de referência mesmo sem resposta GPS, com aviso de **nenhuma posição remota** e sem marcador inventado. Mantém consentimento de blocos online por controlador/aparelho e atribuição.
- Área de status sobe acima do mapa; cartão e textos mais compactos. Consultas automáticas ficam ligadas enquanto a tela estiver aberta e podem ser desligadas.
- No aparelho monitorado, opção **Ativar recuperação automática** com configuração local única. A sessão de localização permanece em primeiro plano com notificação e ação Parar, mesmo após apagar a tela.
- Tentativa de restaurar GPS por boot/reinício do MonitorService SOMENTE quando modo recuperação e permissões Android, inclusive acesso de localização em segundo plano, foram previamente autorizados. O usuário pode abrir Configurações do Android para conceder "Permitir o tempo todo". Serviço pode não ser restaurado conforme Android/fabricante.
- Consulta autenticada não solicita outra autorização do monitorado. Falha de rede pode manter nesta tela um marcador anterior somente com aviso **não ao vivo**.

## Limitações
- O GPS do Android não é ligado remotamente; instalar e autorizar ainda exige acesso físico uma única vez. Nenhum comando escondido ativa GPS.
- O app não tem serviço público de internet: fora da LAN/VPN não poderá localizar o celular desaparecido. O modo não funciona após desinstalação, force-stop, perda de permissão ou sem energia/conectividade.
- Permissão ACCESS_BACKGROUND_LOCATION requer declaração adicional e pode limitar publicação Google Play. Testes Gradle/Android/boot nos Redmi estão pendentes.
- Para transmissão de fotos, as regras separadas de câmera não mudaram.
