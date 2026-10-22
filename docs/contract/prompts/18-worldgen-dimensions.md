# LB-18 — Mundo, geração, biomas, estruturas e dimensões

Número canônico: 18. Estado autoritativo: [status.json](../status.json), entrada `LB-18`.

## Prompt para executar

Trabalhe apenas no projeto existente LegacyBridge. Leia integralmente o
[contrato comum](../README.md), seu status atual e as referências abaixo antes de agir.
Execute um recorte verificável deste prompt, respeitando as dependências de cada corte;
não presuma que o pedido autoriza completar toda a família numa única mensagem.
Se este não for o cursor ativo, confirme a mudança de prioridade no contrato antes de implementar.

## Ponto de partida da revisão 2026-09-07

Worldgen/dimensões ainda são recusados. A mudança envolve registries/dados/lifecycle/contextos, não apenas renomes. Nenhuma etapa atual migra mundos antigos.

## Recortes e dependências

- `WORLD-ACCESS`: Acesso a mundo/posição/chunk finito no lado/thread corretos, sem carregamento arbitrário. Requer: `COMPOSE-01`, `BLOCK-STATE`, `LIFE-EVENTS`.
- `WORLD-GENERATION`: Feature/placement em mundo novo; depois bioma/estrutura/registry dinâmico por corte. Requer: `WORLD-ACCESS`, `DATA-CONDITIONAL`.
- `WORLD-DIMENSION`: Dimensão isolada, persistência de mundo novo e transição/spawn/respawn/reconexão. Requer: `WORLD-GENERATION`, `CAP-PLAYER`, `NET-C2S`.

Os estados mutáveis ficam somente no JSON. Um corte aceito prova apenas o escopo
da sua descrição/evidência, não todo o prompt nem artefatos posteriores.

## Trabalho solicitado

1. Audite registries estáticos/dinâmicos, codecs/bootstrap, level keys, altura/limites, feature/placement/bioma/estrutura e decisões de geração nas duas versões.
2. Comece com leitura/mutação localizada já autorizada em servidor e chunk disponível. Depois uma feature em mundo novo isolado com seed e condições verificáveis.
3. Amplie feature → placement → biome integration → estrutura/template/processor/pool/locate → carver/noise apenas com fixture e contrato de geração aceito; não transcodificar JSON desconhecido.
4. Defina oracle observável e diferenças nativas permitidas: condições, distribuição/localização limitada, boundaries e integridade de chunk, não igualdade universal de terrain.
5. Dimensão em separado: carregamento/keys, criação/save/reload, portal/transição, spawn/respawn, retorno, desconexão e dois clientes; rede/capabilities devem preservar identidade e lifecycle.
6. Teste reload/datapack inválido/missing ID, ciclo, geração repetida, fronteiras e reinício. Nunca executar contra mundos do usuário.

## Aceite específico

- Mundo novo permanece consistente após save/reload; geração não duplica estrutura/loot por repetição e não provoca carga arbitrária.
- Mudanças incompatíveis no formato ou ausência de rota semântica abortam a conversão, não geram mundo parcialmente migrado.

## Limites e parada

Migração de saves 1.16.5, biomas existentes e DataFixer de mundos são projeto separado com autorização/backups/contrato próprio.

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

Risco: crítico: formatos dinâmicos e corrupção de estado. Estimativa: Sem estimativa global defensável; provas em mundos novos por formato/contexto.

- [docs/RESOURCE_API_BASELINES.md](../../../docs/RESOURCE_API_BASELINES.md)
- [docs/DEPENDENCY_GRAPH.md](../../../docs/DEPENDENCY_GRAPH.md)
- [docs/SECURITY.md](../../../docs/SECURITY.md)

