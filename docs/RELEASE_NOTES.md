# Monitor Remoto v0.1.0-alpha.25

## Correção da alpha.25

- Ajuste dos dois erros `Argument type mismatch: actual type is LinearLayout, but Context was expected` do workflow 25, encontrados em `IncomingAccessScreenUi.kt:113` e `:114`.
- Os construtores de `View` e `LinearLayout` dentro do helper `infoMetric` agora recebem explicitamente a Activity como contexto (`this@infoMetric`), em vez do receptor implícito `LinearLayout.apply`.
- Mantidos o visual atual do alerta de acesso recebido, o aviso de autenticação, os dados e as permissões existentes.
- Exigida alpha.25 nos **dois celulares** pela política de versão exata.

## Ainda não implementado

A futura reorganização de Detalhes do aparelho (bateria, armazenamento e redes) e a função Recuperar dados não fazem parte desta correção.

## Validação pendente

Executar novamente `:app:compileDebugKotlin`, testes JVM e criação do APK pelo GitHub Actions. O ZIP contém somente o código-fonte.
