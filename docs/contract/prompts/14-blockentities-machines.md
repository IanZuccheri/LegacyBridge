# LB-14 — BlockEntities, máquinas e automação

Número canônico: 14. Estado autoritativo: [status.json](../status.json), entrada `LB-14`.

## Prompt para executar

Trabalhe apenas no projeto existente LegacyBridge. Leia integralmente o
[contrato comum](../README.md), seu status atual e as referências abaixo antes de agir.
Execute um recorte verificável deste prompt, respeitando as dependências de cada corte;
não presuma que o pedido autoriza completar toda a família numa única mensagem.
Se este não for o cursor ativo, confirme a mudança de prioridade no contrato antes de implementar.

## Ponto de partida da revisão 2026-09-07

TileEntity/BlockEntity é bloqueado. Nem um block simples nem uma capability isolada implementam o ciclo de vida de um host persistente.

## Recortes e dependências

- `BE-PERSIST`: Primeiro host persistente sem tick/GUI, registro/construção/save/load/remoção. Requer: `BLOCK-STATE`, `CAP-INVENTORY`.
- `BE-TICK`: Tick servidor com efeito limitado, unload/reload e dirty-state. Requer: `BE-PERSIST`.
- `BE-MACHINE`: Máquina finita, recursos/capabilities/recipe e automação por face. Requer: `BE-TICK`, `CAP-FLUID`, `NET-C2S`, `DATA-RECIPES-QA`.

Os estados mutáveis ficam somente no JSON. Um corte aceito prova apenas o escopo
da sua descrição/evidência, não todo o prompt nem artefatos posteriores.

## Trabalho solicitado

1. Audite transição TileEntity para BlockEntity sem tratar o nome como receita. Verifique fábricas/tipos, contexto de estado/posição, registro, super/construtor, leitura/escrita NBT e callbacks.
2. Fixture inicial guarda um contador sem GUI/tick: colocar, salvar, fechar, carregar, retirar bloco, validar invalidation e drops uma vez.
3. Depois ticker somente no lado correto, pausa/unload/reload de chunk e dirty-state. Diferencie sync update tag/packet de persistência.
4. Adicione máquina pequena com recipe concreto e inventário/energia/fluido somente quando seus contratos estiverem aceitos. Preserve combustível/input/output/XP, faces e limites.
5. Teste hopper/automação, duas instâncias, mudança de bloco, quebra durante processo, save/crash/reabertura, chunk inacessível e interação por dois jogadores.

## Aceite específico

- Estado não vaza entre hosts; recursos conservados em save/reload, extração concorrente, quebra e automação.
- Máquina só pode ser promovida com menu/render/sync realmente necessários implementados, não com interfaces vazias.

## Limites e parada

Multiblocks/redes de máquinas/tickets de chunk exigem novos subcortes se encontrados; migração de mundos excluída.

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

Risco: crítico: persistência, ticking e dupe. Estimativa: Sem estimativa defensável de família; dividir por host persistente/tick/máquina.

- [docs/COMPAT_LAYER.md](../../../docs/COMPAT_LAYER.md)
- [docs/RESOURCE_API_BASELINES.md](../../../docs/RESOURCE_API_BASELINES.md)
- [docs/DEPENDENCY_GRAPH.md](../../../docs/DEPENDENCY_GRAPH.md)

