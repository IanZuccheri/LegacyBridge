# LB-15 — Entidades, atributos, combate, spawn e AI

Número canônico: 15. Estado autoritativo: [status.json](../status.json), entrada `LB-15`.

## Prompt para executar

Trabalhe apenas no projeto existente LegacyBridge. Leia integralmente o
[contrato comum](../README.md), seu status atual e as referências abaixo antes de agir.
Execute um recorte verificável deste prompt, respeitando as dependências de cada corte;
não presuma que o pedido autoriza completar toda a família numa única mensagem.
Se este não for o cursor ativo, confirme a mudança de prioridade no contrato antes de implementar.

## Ponto de partida da revisão 2026-09-07

Não existem adapters/perfis de entidade. O alvo de impacto Clumps exige um feixe de persistência, spawn, renderer e XP por denominação, não apenas renomear ExperienceOrb.

## Recortes e dependências

- `ENTITY-CORE`: Entidade não viva: registro, construtor, spawn/tracking e estado persistente finito. Requer: `COMPOSE-01`, `LIFE-EVENTS`, `NET-QA-03`.
- `ENTITY-LIVING`: Living/atributos/dano/equipamento/morte e conservação de drops/XP. Requer: `ENTITY-CORE`, `ITEM-EQUIPMENT`.
- `ENTITY-AI`: Goal/pathfinding finito; prioridade, scheduling, spawn/despawn e navegação. Requer: `ENTITY-LIVING`.
- `ENTITY-CORPUS`: Fechar comportamento de um fixture derivado de caso autorizado do corpus, incluindo XP quando aplicável. Requer: `ENTITY-AI`, `RENDER-ENTITY`, `PERSIST-SAVEDDATA`.

Os estados mutáveis ficam somente no JSON. Um corte aceito prova apenas o escopo
da sua descrição/evidência, não todo o prompt nem artefatos posteriores.

## Trabalho solicitado

1. Primeiro fixture entidade não viva com efeito observável, posicionamento/movimento, spawn/tracking, serialização e reload. Conecte renderer real em LB-17 antes de alegar comportamento visual.
2. Depois living: atributos e IDs de modificadores, dano/invulnerabilidade, equipamentos/efeitos, morte/drops/XP e respawn. Não assumir equivalência de fórmulas vanilla.
3. AI separada: um goal real, prioridade/controle exclusivo, servidor, scheduling e navegação; depois pathfinding, sensores/brain/POIs apenas se ocorrerem no corpus e houver contratos.
4. Trate projéteis, montaria/passageiros, tameables, inventário e spawn/despawn como subcortes adicionais explícitos, não aliases automáticos.
5. Para Clumps, preserve denominações do XP, merge/pickup/cancelamento, Mending, movimento/idade/despawn, NBT e spawn-sync. Setter/evento que a origem ignora não pode ganhar efeito inventado.
6. Teste dois jogadores, tracking tardio, chunk unload, save/reload, dimensões, desconexão, morte e ausência de duplicação de drops/XP.

## Aceite específico

- Trace de efeitos e invariantes antes/depois em origem/destino; quantidade total sozinha não prova conservação semântica de XP/Mending.
- Renderer vazio e dummy entidade não aceitam ClassNotFoundException; incompatibilidade comportamental bloqueia o mod.

## Limites e parada

Comportamentos vanilla profundamente alterados podem permanecer MANUAL; não reimplementar o Forge/Minecraft antigo inteiro.

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

Risco: crítico: comportamento vanilla e sincronização. Estimativa: Clumps B03: 30–50 dias-pessoa condicionais no backlog; não é estimativa de todas as entidades.

- [corpus/BACKLOG.md](../../../corpus/BACKLOG.md)
- [corpus/intake/semantic-review.md](../../../corpus/intake/semantic-review.md)
- [docs/COMPAT_LAYER.md](../../../docs/COMPAT_LAYER.md)
- [docs/NETWORK_API_BASELINES.md](../../../docs/NETWORK_API_BASELINES.md)

