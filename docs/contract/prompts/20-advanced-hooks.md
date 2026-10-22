# LB-20 — Reflexão, access transformers, mixins e coremods

Número canônico: 20. Estado autoritativo: [status.json](../status.json), entrada `LB-20`.

## Prompt para executar

Trabalhe apenas no projeto existente LegacyBridge. Leia integralmente o
[contrato comum](../README.md), seu status atual e as referências abaixo antes de agir.
Execute um recorte verificável deste prompt, respeitando as dependências de cada corte;
não presuma que o pedido autoriza completar toda a família numa única mensagem.
Se este não for o cursor ativo, confirme a mudança de prioridade no contrato antes de implementar.

## Ponto de partida da revisão 2026-09-07

Reflexão desconhecida e hooks invasivos são bloqueados. Proibir alteração de Minecraft/Forge/JDK torna certos mods incompatíveis por política, não somente por falta de mappings.

## Recortes e dependências

- `HOOKS-AUDIT`: Classificar hooks concretos do corpus em API pública equivalente, MANUAL ou UNSUPPORTED. Requer: `LINKAGE-GENERAL`, `DEPS-COHORT`.
- `HOOKS-PUBLIC-ROUTE`: Provar substituição de um hook concreto por operação pública existente, ou documentar impossibilidade sem habilitar suporte. Requer: `HOOKS-AUDIT`, `LIFE-EVENTS`.

Os estados mutáveis ficam somente no JSON. Um corte aceito prova apenas o escopo
da sua descrição/evidência, não todo o prompt nem artefatos posteriores.

## Trabalho solicitado

1. Inventarie acesso reflexivo literal/dinâmico, AT, mixin/coremod, services de transformação, código gerado/native e dependências que patcham plataforma. Não executar scripts/classe original.
2. Para reflexão literal limitada, compare acesso, resolução, fase, exceptions e efeito; só substituir quando houver contrato exato. Nomes calculados/desconhecidos não passam por heurística.
3. Procure extensão pública real que produza o mesmo efeito. Um access transformer legado não autoriza modificar target fields/métodos da plataforma; apenas acesso público semanticamente adequado pode ser opção.
4. Para mixin/coremod que exige patchar Minecraft/Forge/Java, registre MANUAL/UNSUPPORTED com evidência do ponto de injeção/efeito. Não adicionar ModLauncher service disfarçado de adapter.
5. Trate portas assistidas por source como alternativa separada: autorização/licença, revisão humana e build/QA; não rotular edição manual como conversão automática.
6. Um recorte pode encerrar sua auditoria com recusa comprovada; isso não é suporte ao mod e não pode inflar métricas. Mudar a restrição principal exige decisão explícita e versão nova do escopo.

## Aceite específico

- Negativos para acesso privado, target removido, reflexão calculada e patch de plataforma nunca geram saída utilizável.
- Rota pública, se existir, tem comportamento comparado e conflitos/lados/ordem testados, não só ausência de crash.

## Limites e parada

Não prometer conversão automática de qualquer mixin/coremod nem usar um adapter vazio para contornar o bloqueio.

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

Risco: crítico: limites permanentes da política. Estimativa: Auditoria por hook; alguns casos não têm implementação permitida neste produto.

- [docs/ANALYZER_LIMITATIONS.md](../../../docs/ANALYZER_LIMITATIONS.md)
- [docs/SECURITY.md](../../../docs/SECURITY.md)
- [corpus/intake/semantic-review.md](../../../corpus/intake/semantic-review.md)

