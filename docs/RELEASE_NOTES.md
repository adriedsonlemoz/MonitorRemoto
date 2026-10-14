# Monitor Remoto v0.1.0-alpha.23

## Novidades da alpha.23

- Continuação da refatoração da interface, agora com a tela de acesso recebido extraída para `IncomingAccessScreenUi.kt`.
- Tela **Acesso remoto detectado** com ícone centralizado e informações completas já na primeira visualização: aparelho, IP, método, primeira consulta e última consulta.
- Removido o fluxo **Quem está?**. Se houver mais de um aparelho recente, a própria tela lista os acessos adicionais.
- Mantido o botão **Parar acesso**, sem mudar o protocolo, a exigência de versão exata ou as permissões do aplicativo.
- Exige alpha.23 nos **dois celulares** para conectar.

## Atenção

**Atualize os dois celulares para a alpha.23 antes de conectar.** Esta é uma versão prévia. Funções de mensagens diretas, espelhamento de tela e localização ainda não estão implementadas. O nome recebido do controlador não constitui identificação independente. A parada do serviço revoga acesso enquanto estiver desligado, mas não substitui rotação de chave.

## Validação pendente

Compilação Gradle Android e teste físico de conexão/QR/desconexão em dois aparelhos. O ZIP distribuído contém somente código-fonte, não APK.
