# LB-03 — JAR único, classloaders e direitos de distribuição

Número canônico: 3. Estado autoritativo: [status.json](../status.json), entrada `LB-03`.

## Prompt para executar

Trabalhe apenas no projeto existente LegacyBridge. Leia integralmente o
[contrato comum](../README.md), seu status atual e as referências abaixo antes de agir.
Execute um recorte verificável deste prompt, respeitando as dependências de cada corte;
não presuma que o pedido autoriza completar toda a família numa única mensagem.
Se este não for o cursor ativo, confirme a mudança de prioridade no contrato antes de implementar.

## Ponto de partida da revisão 2026-09-07

LegacyBridge.jar existe; usa bibliotecas da plataforma auditadas e SRGUtils aninhado. Dados simples estão incluídos. Direitos de distribuição/relinking ainda não encerrados.

## Recortes e dependências

- `PACKAGE-BASE`: Empacotamento atual verificado e servidor do artefato final registrado; não é aprovação legal/publicação. Requer: `PRODUCT-BASE`.
- `PACKAGE-RIGHTS`: Resolver termos do projeto, licença LGPL da dependência, fonte correspondente e substituição/relinking verificável. Requer: `PACKAGE-BASE`.
- `PACKAGE-PROMOTION`: Requalificar cada novo contrato no JAR público, serviços, módulos e lados. Requer: `PRODUCT-COMPOSED`, `DATA-EXPAND`.

Os estados mutáveis ficam somente no JSON. Um corte aceito prova apenas o escopo
da sua descrição/evidência, não todo o prompt nem artefatos posteriores.

## Trabalho solicitado

Checkpoint após a implementação técnica, 2026-09-12:
**TECHNICAL_RELINK_V1 entregue; OWNER_TERMS_DECISION pendente**.
Leia [a entrega técnica](../../SRGUTILS_RELINKING.md) e
[a evidência](../../evidence/relink-2026-09-12.json). Não repetir prepare/build/
replace ou a matriz de LB-02 como padrão. Obter direção do titular sobre termos
proprietários com permissões adequadas ou avaliação de licença aberta; nenhuma
opção é aplicada automaticamente. A receita independente, equivalência finita
e hashes não encerram direitos, materiais de release ou Forge JiJ/JPMS nativo.
Os estados autoritativos continuam no JSON; nenhum aceite completo foi promovido.

Escopo técnico solicitado na retomada após LB-02 (histórico):
**PACKAGE-RIGHTS / rota técnica de fonte correspondente e relinking**. Trabalhar
em cópias isoladas sem Minecraft/tela; verificar materiais disponíveis da
biblioteca, construir somente a biblioteca autorizada se viável e implementar
uma substituição reprodutível/verificável sem tocar a instalação do jogador.
Não executar classes originais/convertidas como parte dessa verificação.
Registrar o que foi provado estaticamente e o que ainda exige Forge nativo.
O bloqueio de licença/termos do titular continua real: não mudar LICENSE,
inventar um oferecimento de fonte ou considerar a análise técnica autorização
jurídica. Se faltar uma decisão do titular, apresentar a decisão exata após
esgotar o trabalho técnico permitido. Não reiniciar a matriz de LB-02 como
substituto deste trabalho nem transferir QA de um hash para outro.

1. Leia a estratégia já implementada antes de escolher JiJ/shading/relocation. Preserve Forge 47.4.23 e a identidade legacybridge_runtime 0.1.1-SNAPSHOT até migração explícita.
2. Audite ASM, logging/facades/providers, TOML/JSON, services, manifest, metadados, split packages, módulos/classloaders e classes duplicadas. Não empacote CLI logger, fixtures, probes ou dados privados.
3. Feche a revisão de direitos com o titular: termos compatíveis, fonte correspondente/build e rota de substituição da biblioteca realmente testada, ou alternativa aprovada. Não trate parecer de engenharia como autorização jurídica.
4. Para cada promoção, teste apenas o JAR público em instalação Forge comum cliente/servidor, sem classpath Gradle, cache ou segunda instalação de LegacyBridge.

## Aceite específico

- Relacione SHA-256 do JAR final, bibliotecas e dados a cada evidência. Alterar recursos/notices exige reauditar o artefato.
- Servidor não resolve classes exclusivas do cliente. Direitos pendentes mantêm experimental/Beta sem publicação.

## Limites e parada

Publicação/upload exige autorização separada; não inventar uma isenção de licença.

Qualquer referência/contrato externo desconhecido, ausência de equivalência implementada,
violação de lado/thread/ownership ou resultado inválido bloqueia a conversão afetada
com MANUAL/UNSUPPORTED/erro estruturado e sem JAR parcial. Impossibilidade ou nova
autoridade necessária deve ser registrada, não ocultada por um adapter fictício.

## Entrega desta retomada

Implemente apenas o corte selecionado (ou registre o bloqueio comprovado), fixtures
positivas/negativas, regra/adapter/operação e documentação. Rode build/test serialmente
depois das mudanças. Comportamento alterado exige comparação isolada origem/destino;
teste nativo não disponível fica explicitamente pendente.
Atualize `status.json`, evidência e checkpoint seguindo o contrato comum. Informe:
ID, realizado, testes efetivamente executados/skips, hashes, pendências e próximo ID.
Não avance status por quantidade de mensagens, símbolos ou testes.

## Risco, esforço e referências

Risco: alto: ABI, classes duplicadas e licença. Estimativa: Correção técnica por dependência; decisão de direitos tem espera não estimável.

- [docs/PACKAGING_ARCHITECTURE.md](../../../docs/PACKAGING_ARCHITECTURE.md)
- [docs/PACKAGING_RIGHTS.md](../../../docs/PACKAGING_RIGHTS.md)
- [docs/PACKAGING_VALIDATION.md](../../../docs/PACKAGING_VALIDATION.md)
