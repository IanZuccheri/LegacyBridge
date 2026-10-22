# LB-10 — Itens e blocos com comportamento

Número canônico: 10. Estado autoritativo: [status.json](../status.json), entrada `LB-10`.

## Prompt para executar

Trabalhe apenas no projeto existente LegacyBridge. Leia integralmente o
[contrato comum](../README.md), seu status atual e as referências abaixo antes de agir.
Execute um recorte verificável deste prompt, respeitando as dependências de cada corte;
não presuma que o pedido autoriza completar toda a família numa única mensagem.
Se este não for o cursor ativo, confirme a mudança de prioridade no contrato antes de implementar.

## Desenvolvimento headless (2026-10-05)

**BLOCK-STATE / BOOLEAN_TOGGLE_USE** entregue como
[candidato privado finito](../../BOOLEAN_TOGGLE_BLOCK_CONTRACT.md):
duas classes/oito métodos, propriedade booleana/default false, SUCCESS cliente sem
escrita e uma escrita no servidor no estado/posição/world recebidos com flags 3.
Preserva CHECKCAST, CONSUME, recursos dos dois estados e os corpos originais.
Source/target/readback, ASM/linkage e dispatch dos seis perfis públicos verificados.
[Evidência](../../evidence/boolean-toggle-block-2026-10-01.json).
Os recortes horizontais, half-shape e self-drop continuam independentes/intactos.

**IRON_SWORD_SEMANTIC_CONTRACT** agora tem [inspeção implementada](../../IRON_SWORD_INSPECTION.md)
no serviço/CLI, fixture original, provas estruturais/ASM/linkage/APIs e recursos.
A conversão permanece UNSUPPORTED com três razões específicas, sem candidato.
[Evidência](../../evidence/iron-sword-inspection-2026-10-01.json): 2.475 regressões
PASS, 312 novas, quatro testes do fixture e empacotamento/CLI PASS.

**OWNED_ATTRIBUTE_MODIFIERS** agora entregue como [candidato privado](../../ITEM_ATTRIBUTE_CONTRACT.md):
duas classes/seis métodos, corpos preservados, mapa MAINHAND imutável e UUIDs
nativos herdados; contrato source/target/readback, mappings, Guava, ASM e linkage.
[Evidência](../../evidence/item-attributes-candidate-2026-10-01.json): 3.147 regressões
PASS, 672 novas, quatro testes do original rerodados e assemble/auditorias/CLI PASS.
Sem novo perfil público ou autoridade de instalação.

**OWNED_ATTRIBUTE_DURABILITY** entregue como [composição privada](../../ITEM_ATTRIBUTE_DURABILITY_CONTRACT.md):
duas classes/sete métodos, capacidade128/stack1, atributos nativos e predicado de
ferro. Mappings/linkage e provas original/alvo/readback independentes, sem union
de flags ou emissão da view de admissão. [Evidência](../../evidence/item-attribute-durability-candidate-2026-10-01.json):
3.786 regressões PASS (639 novas), dois fixtures com quatro testes cada PASS;
assemble/auditorias/CLI PASS. Candidatos anteriores intactos e sem novo Apply.

**OWNED_STACK_DAMAGE** entregue como [candidato privado](../../OWNED_STACK_DAMAGE_CONTRACT.md):
duas classes/cinco métodos, helper com guards e única escrita damage-1 sobre
a stack recebida. Mappings/hooks nativos, ancestralidade/linkage/ASM e provas
original/alvo/readback independentes. [Evidência](../../evidence/owned-stack-damage-candidate-2026-10-04.json):
5.012 regressões PASS (1.226 novas), quatro testes de cada original e
assemble/auditorias/CLI PASS. Candidatos anteriores byte-idênticos; sem novo Apply.

**OWNED_STACK_REPAIR_USE** entregue como [candidato privado](../../OWNED_STACK_REPAIR_USE_CONTRACT.md):
duas classes/seis métodos, lookup único da mão recebida, SUCCESS cliente sem
mutação e helper servidor seguido de CONSUME/PASS na mesma stack. Contrato
separado, sem alargar helper-only; mappings/dispatch/ASM/linkage/readback.
[Evidência](../../evidence/owned-stack-repair-use-candidate-2026-10-05.json):
6.944 regressões PASS (1.932 novas), duas falhas iniciais corrigidas; original
idêntico/quatro testes PASS; assemble/auditorias/CLI PASS. Native NOT_RUN.

**IRON_HELMET_SEMANTIC_INSPECTION** entregue como
[diagnóstico read-only](../../IRON_HELMET_INSPECTION.md): fixture autoral,
serviço/CLI, grafo/ASM/linkage/APIs e recursos finitos. UNSUPPORTED mesmo com
construtor reconhecido; tipo contextual e slot vazio versus troca não têm
receita equivalente. [Evidência](../../evidence/iron-helmet-inspection-2026-10-05.json).
Nenhum candidato, adapter ou Apply novo; anteriores intactos.

**IRON_HELMET_EMPTY_SLOT_ADAPTER** entregue como
[operação real restrita](../../IRON_HELMET_EMPTY_SLOT_ADAPTER.md): item final,
fábrica fixa, override de slot vazio/cópia/limpeza/alias nos dois lados lógicos.
Um descriptor RUNTIME novo, 66 anteriores preservados; nenhum candidato/receita/Apply.
[Evidência](../../evidence/iron-helmet-adapter-2026-10-05.json) distingue os bytes
compilados e o modelo sintético dos efeitos herdados e nativos ainda pendentes.

**IRON_HELMET_HEAD_EQUIP_EFFECTS** entregue como
[operação RUNTIME independente](../../IRON_HELMET_HEAD_EFFECTS.md): HEAD,
receivers nativos exatos, stack vazia ou item final/count1; som antes da escrita
da mesma referência. Isolamento físico de classes cliente; 67 descriptors e
item anterior intactos. Não compõe use nem autoriza receita/candidato/Apply.
[Evidência](../../evidence/iron-helmet-head-effects-2026-10-05.json) registra
bytes e efeitos sintéticos separadamente de nativo NOT_RUN.

**IRON_HELMET_DISPENSER_CLOSED_RECEIVERS** entregue como
[operação runtime separada](../../IRON_HELMET_DISPENSER.md): consulta nativa,
primeiro ServerPlayer/Zombie elegível, split1, escrita player com efeito HEAD
anterior ou Zombie silencioso, drop chance2/persistência. Receiver desconhecido
recusado antes de consumir, nunca convertido em false/fallback ou outro alvo.
Um descriptor novo, 68 anteriores e bytes dos helpers/item preservados.
Fixture original adicional; sem registro da operação, receita, candidato ou Apply.
[Evidência](../../evidence/iron-helmet-dispenser-2026-10-05.json) não confunde
seleção na lista recebida com ordenação espacial nativa igual entre versões.

Próximo desenvolvimento: **ITEM-EQUIPMENT / IRON_HELMET_INTEGRATED_SUCCESSOR**.
[Plano finito](../../IRON_HELMET_INTEGRATED_ITEM_PLAN.md): novo item/factory com
ownership explícito, use e callback dispenser integrado; não ampliar guards
antigas. Outros receivers vanilla, fallback, Equipable/rendering/coortes/requires
e comparação nativa continuam gates antes de constructor recipe/candidato.

ITEM-EQUIPMENT mantém o candidato de [durabilidade/predicado de ferro](../../DURABLE_ITEM_CONTRACT.md),
não reparação completa, dano/quebra gerais, ferramentas/armaduras/atributos gerais ou aceite nativo.

Partes estáticas de COMPOSE-01/LIFE-EVENTS sustentam desenvolvimento, não
satisfazem seus requires de aceite. ITEM-USE-QA e QA de LB-09 ficam pendentes,
sem iniciar jogos ou automação de tela. Implementação não equivale a aceite nativo.

## Ponto de partida da revisão 2026-09-07

Conteúdo simples por bootstrap e alimento literal preservador são perfis separados. O comportamento completo de uso/última unidade/relog e as famílias abaixo não foram aceitos genericamente.

## Recortes e dependências

- `FOOD-BASE`: Fixture literal de alimento com comparação de endpoints registrada, não uso completo/multiplayer. Requer: `LOGIC-BASE`.
- `ITEM-USE-QA`: Uso real, duração, última unidade, consumo/efeitos e reconexão do alimento existente. Requer: `FOOD-BASE`.
- `ITEM-EQUIPMENT`: Durabilidade/reparo primeiro; ferramentas/armaduras/atributos em cortes independentes. Requer: `COMPOSE-01`, `LIFE-EVENTS`.
- `BLOCK-STATE`: Bloco preservador, propriedade/orientação, forma/colisão e interação/drop separados. Requer: `COMPOSE-01`, `LIFE-EVENTS`.
- `BLOCK-TICKS`: Ticks/redstone, plantas/crescimento e consumo/drops por contratos de contexto. Requer: `BLOCK-STATE`, `DATA-LOOT`.
- `ITEM-EFFECTS`: Uso com efeitos, encantamentos e brewing em cortes separados, com consumo/tempo/lados. Requer: `ITEM-EQUIPMENT`, `NET-C2S`.
- `CREATIVE-TABS`: Agrupamento, ordenação e identidade visual de stacks; diferenças da API alvo explicitadas. Requer: `LIFE-EVENTS`, `DATA-ASSETS`.

Os estados mutáveis ficam somente no JSON. Um corte aceito prova apenas o escopo
da sua descrição/evidência, não todo o prompt nem artefatos posteriores.

## Trabalho solicitado

1. Use padrões do corpus, sem inventar frequência com três releases. Adicione fixture por comportamento e variantes negativas próximas.
2. Feche alimento em uso real incluindo duração nativa, lado, última unidade, fome/saturação/efeitos, save/relog. Não usar só chamada de endpoint como evidência de uso completo.
3. Implemente durabilidade, limites de stack, dano/reparo e atributos; depois ferramentas/tiers/armaduras/equipar; depois efeitos/encantamentos/brewing. Preserve IDs de modificadores e contexto, não aproximação por nome.
4. Blocos: registro preservado → estado/propriedade/orientação → shapes/colisão → interação/ferramenta/drop → random/scheduled ticks, redstone e crescimento. Cada seta exige evidência própria.
5. Audite diferenças de material, destruição, tool tags, luz/fricção/força, loot e flags de atualização. Não igualar materiais/propriedades semanticamente diferentes.
6. Creative tabs: agrupamento/ordenação e identidade dos stacks nas APIs reais do destino, reload e lado cliente; não afirmar uma ordenação que a plataforma não garante.

## Aceite específico

- Compare efeito real original/destino, preservando consumo, ferramenta efetiva, drops e ausência de duplicação nos dois lados.
- Teste composição de item/bloco com recursos/callbacks aceitos; UI/instalação só após gates de promoção.

## Limites e parada

BlockEntities, entidades, máquinas e custom renderers não entram implicitamente nesta família.

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

Risco: alto a crítico: conservação e diferenças vanilla. Estimativa: Usar recortes IB do corpus; ferramentas/redstone exigem auditoria antes de estimativa.

- [docs/ITEM_BLOCK_CORPUS_AUDIT.md](../../../docs/ITEM_BLOCK_CORPUS_AUDIT.md)
- [docs/FOOD_API_BASELINES.md](../../../docs/FOOD_API_BASELINES.md)
- [docs/FOOD_ITEM_VALIDATION.md](../../../docs/FOOD_ITEM_VALIDATION.md)
- [docs/SIMPLE_CONTENT.md](../../../docs/SIMPLE_CONTENT.md)
