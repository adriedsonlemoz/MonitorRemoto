# Validação alpha.88

Escopo: UI em três colunas, Configurações pesquisáveis, diálogos de aviso/confirmar, duração monotônica das consultas.

A executar no GitHub Actions: `:app:testDebugUnitTest`, `:app:assembleDebug`, `:app:lintDebug`.
A testar nos Redmi: grade em 3 colunas e fontes ampliadas; temas claro/escuro; busca nas Configurações com acentos; alerta Cancelar/Confirmar; estado de rede; rotação e permissões locais.

Nenhuma mudança em permissões Android, regras AES-GCM, controle de câmera, SMS, GPS, pareamento e ADB.
