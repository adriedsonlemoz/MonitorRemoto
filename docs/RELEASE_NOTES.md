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
