# LB-17 — Renderização, modelos dinâmicos, partículas e áudio

Número canônico: 17. Estado autoritativo: [status.json](../status.json), entrada `LB-17`.

## Prompt para executar

Trabalhe apenas no projeto existente LegacyBridge. Leia integralmente o
[contrato comum](../README.md), seu status atual e as referências abaixo antes de agir.
Execute um recorte verificável deste prompt, respeitando as dependências de cada corte;
não presuma que o pedido autoriza completar toda a família numa única mensagem.
Se este não for o cursor ativo, confirme a mudança de prioridade no contrato antes de implementar.

## Ponto de partida da revisão 2026-09-07

Texturas/modelos simples preserváveis não significam suporte a custom renderer/shader. Os namespaces compat de rendering permanecem explicitamente não implementados.

## Recortes e dependências

- `RENDER-ENTITY`: Renderer real mínimo de entidade, pose/buffer/textura/estado com isolamento cliente. Requer: `ENTITY-CORE`, `DATA-ASSETS`, `CLIENT-LOCAL`.
- `RENDER-DYNAMIC`: BlockEntity/item renderer e modelos dinâmicos em recortes separados. Requer: `RENDER-ENTITY`, `BE-PERSIST`.
- `RENDER-EFFECTS`: Partículas/áudio e viabilidade de shader concreto; sem shader genérico automático. Requer: `RENDER-DYNAMIC`.

Os estados mutáveis ficam somente no JSON. Um corte aceito prova apenas o escopo
da sua descrição/evidência, não todo o prompt nem artefatos posteriores.

## Trabalho solicitado

1. Audite mudanças de Matrix/Pose, buffers, vertex formats, RenderType, model baking/layers, texturas e render states; nome semelhante não prova contrato de draw.
2. Implemente renderer mínimo visível, iluminação/transparência, culling e pose. Depois item/BlockEntity renderer e baked/dynamic model em cortes separados.
3. Partículas/sons: factories, contexto/lado, atlas, volume/pitch/distância, registry/resource IDs, reload e limpeza. Não aceitar som ausente só porque sons vanilla tocam.
4. Custom shader: leia formato/versão/includes/uniforms e estado esperado. Se o pipeline não admitir equivalente sem alterar plataforma, MANUAL/UNSUPPORTED. Não alegar suporte a OptiFine/Iris ou renderizadores externos sem coorte própria.
5. Teste em cliente real assets/reload, F3+T, redimensionamento/escala, mundo/relog e reinício. Sirva mesmo JAR em dedicado sem classloading cliente.
6. Compare comportamento visual com captura real origem/destino; não exigir pixels idênticos entre motores se diferenças aceitáveis forem explicitamente aprovadas.

## Aceite específico

- Sem missing texture silenciosa, vazamento de estado gráfico, renderer vazio ou stub para satisfazer linkage.
- Mudanças visuais necessárias ficam descritas e vinculadas a regra. FPS/heap/recarga medidos nas máquinas de QA.

## Limites e parada

Não emular pipeline gráfico 1.16.5 inteiro; compatibilidade com mods de shaders é contrato externo separado.

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

Risco: alto: pipeline gráfico, recursos e lado físico. Estimativa: AppleSkin B06: 30–50 dias-pessoa condicionais; renderers gerais precisam de auditoria.

- [docs/COMPAT_LAYER.md](../../../docs/COMPAT_LAYER.md)
- [docs/RESOURCE_API_BASELINES.md](../../../docs/RESOURCE_API_BASELINES.md)
- [corpus/BACKLOG.md](../../../corpus/BACKLOG.md)

