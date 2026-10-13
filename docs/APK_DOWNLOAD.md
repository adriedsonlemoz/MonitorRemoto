# Como obter APK em vez de ZIP no GitHub

GitHub Actions > Artifacts: **o GitHub baixa um ZIP por decisão da plataforma**, mesmo quando o arquivo armazenado é `MonitorRemoto-v0.1.0-alpha.3.apk`. Renomear a identificação do Artifact não muda isso.

Para obter o APK **diretamente**, no repositório abra a aba **Releases** > `v0.1.0-alpha.3` > **Assets** > `MonitorRemoto-v0.1.0-alpha.3.apk`.

O workflow `Gerar APK e Release` publica automaticamente a Release quando é acionado manualmente por **Run workflow** ou quando ocorre push ao ramo principal/padrão, depois de passar nos testes/build. Pull requests nunca publicam release. Caso a etapa tenha falha HTTP 403, verifique **Settings > Actions > General > Workflow permissions** e as restrições do repositório/organização. `GITHUB_TOKEN` necessita `contents: write`.

A Release é do GitHub e não da Google Play. Se o repositório for público, o APK da Release também será público.

## Assinatura estável opcional

No repositório GitHub > Settings > Secrets and variables > Actions, configure TODOS estes Secrets:

- `KEYSTORE_BASE64`: bytes da sua keystore em Base64 sem quebras de linha
- `KEYSTORE_PASSWORD`: senha da keystore
- `KEY_ALIAS`: alias da chave
- `KEY_PASSWORD`: senha da chave

Com os quatro Secrets, o workflow produz `:app:assembleRelease`, `:app:bundleRelease` e publica o **APK Release assinado** como `.apk` direto; guarda o AAB nos Artifacts. Sem os quatro, compila e publica só **APK Debug de testes**. Configurar apenas parte dos Secrets gera erro proposital.

Nunca publique keystore ou Secrets no código, no ZIP do projeto, na biblioteca pública ou no Drive compartilhado. Preservar a **mesma chave** nas versões seguintes é necessário para atualizar APKs diretamente. Google Play App Signing requer planejar a chave de upload separadamente.

O workflow não cria APK dentro do arquivo ZIP do código-fonte: o GitHub Actions precisa compilar primeiro. O `.apk` vem da execução bem-sucedida do workflow.
