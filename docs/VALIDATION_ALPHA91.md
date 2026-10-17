# Validação alpha.91

- Testar clique em Bateria e Temperatura no painel, popup central, recarga, fechar e trocar host.
- Sem ADB salvo: indicadores públicos ainda aparecem, CPU/GPU explicados como indisponíveis.
- ADB pareado para mesmo IP: `dumpsys thermalservice` somente leitura; deduplicar HAL/cached, não mostrar sentinelas e mostrar falha sem travar o app.
- Executar GitHub Actions `:app:compileDebugKotlin`, `:app:testDebugUnitTest`, `:app:assembleDebug` e testes em ambos aparelhos.
- Validar consumo e limitação a uma consulta por toque, sem coleta em segundo plano.
