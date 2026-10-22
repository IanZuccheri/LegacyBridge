# LB-07 — Corpus autorizado e priorização por mods inteiros

Número canônico: 7. Estado autoritativo: [status.json](../status.json), entrada `LB-07`.

## Prompt para executar

Trabalhe apenas no projeto existente LegacyBridge. Leia integralmente o
[contrato comum](../README.md), seu status atual e as referências abaixo antes de agir.
Execute um recorte verificável deste prompt, respeitando as dependências de cada corte;
não presuma que o pedido autoriza completar toda a família numa única mensagem.
Se este não for o cursor ativo, confirme a mudança de prioridade no contrato antes de implementar.

## Ponto de partida da revisão 2026-09-07

Snapshot 2026.09.06.1: seis identidades de fixtures/controles e três releases reais autorizados, zero ports completos validados. Outras fixtures recentes têm ledgers próprios, não foram incorporadas silenciosamente.

## Recortes e dependências

- `CORPUS-BASE`: Corpus e backlog versionados com direitos/hashes e níveis de evidência separados. Requer: `GOV-01`.
- `CORPUS-REFRESH`: Reconciliar fixtures já existentes e bloqueios transversais, sem nova aquisição. Requer: `CORPUS-BASE`.
- `CORPUS-PRIORITY`: Priorizar coortes e feixes completos de bloqueios, sem contar primeiro erro removido como mod portado. Requer: `CORPUS-REFRESH`, `COMPOSE-01`.

Os estados mutáveis ficam somente no JSON. Um corte aceito prova apenas o escopo
da sua descrição/evidência, não todo o prompt nem artefatos posteriores.

## Trabalho solicitado

1. Atualize versão do catálogo com originais autorais e releases já autorizados, metadados exatos, dependências/lados, features, blockers e logs/hash por etapa.
2. Não publique nomes/hashes de arquivos privados sem autorização para esses metadados. Não confunda direito de uso com redistribuição.
3. Use B01/B02/B03 existentes como hipóteses de impacto, incluindo todos os bloqueios restantes de Clumps/Controlling/AppleSkin. AppleSkin com origem adjacente 1.16.4/Forge35 precisa decisão, não relabel.
4. Produza fila por número de releases distintos potencialmente desbloqueados, dependências, risco semântico e esforço/certeza. Amostra de três não sustenta frequência global nem promessa de popularidade.
5. Defina cenários comportamentais de cada release antes de habilitar suporte; por exemplo Clumps exige XP por denominação, Mending, merge/pickup/cancelamento, persistência, spawn e renderer.
6. Nova aquisição de mods famosos permanece reservada a LB-22 depois dos gates de qualidade; planejar a amostra não autoriza baixá-la agora.

## Aceite específico

- Analisável, convertível, bytecode validado, inicializa, comportamento e multiplayer permanecem campos separados por versão exata.
- Número de testes/símbolos e quantidade de first-refusals removidas nunca são porcentagem de mods compatíveis.

## Limites e parada

Sem compra/download/publicação nesta etapa de planejamento; sem usar uma versão adjacente como prova da baseline.

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

Risco: alto em seleção, direitos e métricas. Estimativa: B01: 3–5; B02: 8–15 dias-pessoa no backlog existente; condicionais, não soma de prazo global.

- [corpus/README.md](../../../corpus/README.md)
- [corpus/BACKLOG.md](../../../corpus/BACKLOG.md)
- [corpus/SCHEMA.md](../../../corpus/SCHEMA.md)
- [docs/CORPUS_VALIDATION.md](../../../docs/CORPUS_VALIDATION.md)

