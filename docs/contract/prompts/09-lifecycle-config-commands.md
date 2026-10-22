# LB-09 — Registros, eventos, lifecycle, configurações e comandos

Número canônico: 9. Estado autoritativo: [status.json](../status.json), entrada `LB-09`.

## Prompt para executar

Trabalhe apenas no projeto existente LegacyBridge. Leia integralmente o
[contrato comum](../README.md), seu status atual e as referências abaixo antes de agir.
Execute um recorte verificável deste prompt, respeitando as dependências de cada corte;
não presuma que o pedido autoriza completar toda a família numa única mensagem.
Se este não for o cursor ativo, confirme a mudança de prioridade no contrato antes de implementar.

## Marco de implementação integrado (2026-09-19)

[CONFIG_RELOAD_SCOPE + TYPED_COMMAND_TREE](../../LIFECYCLE_SUITE.md) entregues
em um candidato privado de sete classes: COMMON Loading/Reloading filtrados por
spec/type, late registry em common setup enfileirado, evento próprio HIGH/LOW,
subcomandos integer/boolean e árvore nova por RegisterCommandsEvent.
CLIENT startup e SERVER por mundo permanecem candidatos separados.
APIs, regras, source/target/readback, cancelamento e Brigadier isolado verificados;
nenhuma classe original executada durante conversão, nenhum jogo iniciado.

O cursor de desenvolvimento segue para **LB-10 / ITEM-EQUIPMENT /
DURABILITY_REPAIR**. LIFE-EVENTS, LIFE-CONFIG e LIFE-COMMANDS continuam PARTIAL,
com requires intactos. Isso encerra o recorte delimitado de implementação,
não elimina padrões mais amplos nem substitui os aceites nativos abaixo.
Não repetir este recorte ao retomar; novas expansões de LB-09 exigem fixture/
contrato próprios, e sua QA fica aguardando autorização.

## Ponto de partida da revisão 2026-09-07

Perfil fechado já preserva late lookup, common setup, COMMON bool/int e comando limitado. Eventos arbitrários, prioridades, CLIENT/SERVER config e reload não estão generalizados.

## Recortes e dependências

- `LIFE-BASE`: Fixture finito e comparação de defaults/config inválida documentados. Requer: `LOGIC-BASE`.
- `LIFE-EVENTS`: Formas reais de registro/callback com bus/fase/lado/ordem e listeners em composição. Requer: `COMPOSE-01`.
- `LIFE-CONFIG`: CLIENT e SERVER separados, defaults/validação/carga/reload e política de reinicialização. Requer: `LIFE-EVENTS`.
- `LIFE-COMMANDS`: Expandir argumentos, permissão, origem e recriação do dispatcher sem alterar comportamento. Requer: `LIFE-EVENTS`, `DEPS-COHORT`.

Os estados mutáveis ficam somente no JSON. Um corte aceito prova apenas o escopo
da sua descrição/evidência, não todo o prompt nem artefatos posteriores.

## Trabalho solicitado

EVENT_PRIORITY_CANCELLATION foi implementado como candidato privado e prova
source/target/readback; limites e evidências em
[EVENT_PRIORITY_CANCELLATION](../../../docs/EVENT_PRIORITY_CANCELLATION.md).
LIFE-EVENTS continua PARTIAL: assinaturas equivalentes não provam eventos
arbitrários nem dispensam COMPOSE-01/comparação nativa.

O recorte implementado preserva carga inicial e reload COMMON separadamente;
não implementa reload CLIENT/SERVER remoto, sincronização própria ou Unloading.
A prova estática de composição não aceita automaticamente estas famílias nem
dispensa os itens de comportamento e corpus a seguir.

1. Parta de callback encontrado no corpus e fixture mínimo com corpo traduzido integralmente. Preserve mandatory deps, bus MOD/Forge, prioridade/cancelamento, inscrição única, ordem garantida e lado lógico/físico.
2. Teste lookup tardio de RegistryObject e negativo de uso antecipado. Não substituir lookup por estado prematuro ou esconder ausência obrigatória.
3. Compare dois listeners com prioridades e evento cancelável, setup enfileirado e subscriber inválido no servidor dedicado; mantenha a thread correta e o contrato de ordem realmente garantido.
4. Config em cortes: COMMON existente/regressão, depois CLIENT, depois SERVER, defaults e validadores reais, carga inicial e reload. Persistência/reload/restart/sync são contratos diferentes.
5. Comandos em cortes: registro/dispatcher, permissões console/jogador, argumento único e execução observável no servidor; depois dependências/subcomandos. Recriar dispatcher não duplica comandos.
6. Redefina estimativas depois de auditar B02 e possíveis callbacks Clumps/Controlling/AppleSkin; remover a primeira recusa não aceita esses mods.

## Aceite específico

- Negativos de lifecycle incompatível, wrong-side listener, registry antecipado, config inválida e permissão insuficiente não passam silenciosamente.
- Teste mudança real de config no jogo e reload quando alegado; endpoint chamado diretamente não prova lifecycle.

## Limites e parada

Não descartar dependência nem converter evento com efeitos diferentes só por ter nome semelhante.

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

Risco: alto: ordem, lados e recarga. Estimativa: B02 8–15 dias-pessoa é um recorte do corpus, não toda esta família.

- [docs/LIFECYCLE_PRESERVING_PROFILE.md](../../../docs/LIFECYCLE_PRESERVING_PROFILE.md)
- [docs/LIFECYCLE_API_BASELINES.md](../../../docs/LIFECYCLE_API_BASELINES.md)
- [docs/CONFIG_COMMAND_API_BASELINES.md](../../../docs/CONFIG_COMMAND_API_BASELINES.md)
- [docs/SERVER_CONFIG_SCOPE.md](../../../docs/SERVER_CONFIG_SCOPE.md)
- [docs/DEPENDENCY_GRAPH.md](../../../docs/DEPENDENCY_GRAPH.md)
