# Validação 0.1.0-alpha.93 (17/10/2026)

## Mudanças
- Introduzida operação `locationSnapshot` com criptografia AES-GCM e versões sincronizadas, retornando somente os campos de localização já autorizada no monitorado.
- Card de status de mapa com chips de conectividade e idade do GPS, com métricas separadas de resposta, precisão e idade real da coordenada.
- Novo modo `Acompanhar`, com consultas a cada 3 segundos enquanto a Activity está visível; o perfil do GNSS monitorado não é alterado.
- A posição anteriormente recebida pode permanecer no mapa durante falha transitória, com aviso de posição antiga; revogação de serviço/permissão limpa o marcador.

## Verificações realizadas
- `python3 tools/check_distribution.py`: passou, incluindo regressões da alpha.93.
- Parser PSI Kotlin: 193 fontes/testes, 0 erros de sintaxe.
- Regras `LocationUiPolicy` isoladas: 9 verificações executáveis aprovadas.
- XML: 51 válidos.
- ZIP: verificar CRC e integridade após empacotar.

## Limites
- O teste de sintaxe não é compilação completa Gradle/Android. Build CI e teste de UI nos aparelhos ainda pendentes.
- Consulta frequente não garante um fix novo: a obtenção de coordenadas é controlada pelo Android, disponibilidade de satélites e perfil do serviço.
- Sem acesso remoto de rede fora da LAN/VPN, este modo não fornece rastreamento mundial do aparelho.
