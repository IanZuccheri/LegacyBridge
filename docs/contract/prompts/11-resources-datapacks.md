# LB-11 — Recursos e datapacks por formato e versão

Número canônico: 11. Estado autoritativo: [status.json](../status.json), entrada `LB-11`.

## Prompt para executar

Trabalhe apenas no projeto existente LegacyBridge. Leia integralmente o
[contrato comum](../README.md), seu status atual e as referências abaixo antes de agir.
Execute um recorte verificável deste prompt, respeitando as dependências de cada corte;
não presuma que o pedido autoriza completar toda a família numa única mensagem.
Se este não for o cursor ativo, confirme a mudança de prioridade no contrato antes de implementar.

## Ponto de partida da revisão 2026-09-07

Há resources simples e perfil separado com cinco serializers vanilla adicionais e grafo local de tags. Custom ingredients/conditions/loot/GLM/advancements/functions ainda não são suporte geral.

## Recortes e dependências

- `DATA-RECIPES-BASE`: Cópia byte-exata/reescrita finita, parsers e endpoints de recipes/tags registrados. Requer: `PRODUCT-BASE`.
- `DATA-RECIPES-QA`: Máquina/stonecutter/recipe-book reais, inputs/fuel/outputs/XP e reload. Requer: `DATA-RECIPES-BASE`.
- `DATA-CONDITIONAL`: Serializers/ingredientes concretos, condições e tags interdependentes por schemas. Requer: `COMPOSE-01`, `DEPS-COHORT`.
- `DATA-LOOT`: Loot e GLM por contexto; depois advancements/triggers e funções/comandos. Requer: `DATA-CONDITIONAL`.
- `DATA-ASSETS`: Modelos/blockstates, animação de textura, áudio e referências com parsers limitados. Requer: `COMPOSE-01`.
- `DATA-ADV-FUNCTIONS`: Advancements/triggers e funções/comandos por schema/parser/contexto. Requer: `DATA-LOOT`, `LIFE-COMMANDS`.

Os estados mutáveis ficam somente no JSON. Um corte aceito prova apenas o escopo
da sua descrição/evidência, não todo o prompt nem artefatos posteriores.

## Trabalho solicitado

1. Classifique cada entrada por versão/formato: preservar bytes, reescrever por regra, MANUAL ou UNSUPPORTED. Nunca copiar desconhecido declarando suporte nem remover recurso silenciosamente.
2. Valide namespace/IDs/referências, tags replace/optional/ciclo, schemas e tipos; ramo condicional falso não significa deletar silenciosamente um erro de parser.
3. Recipes em cortes: vanilla adicionais; custom serializer + ingrediente concreto; condições no contexto de reload. Preserve consumo/resultado/XP e integração real de máquina/recipe book.
4. Loot/GLM em contexto concreto, tabelas/condições/funções, recarga e conservação; advancements com trigger real; funções com parser/versão/permissão/origem do comando, nunca substituição textual cega.
5. Assets em cortes: modelos/blockstates; mcmeta/animação/texturas com limites de descompressão; sons próprios/Ogg/referências e reload. Basear validade em parse e observação, não só extensão.
6. Registre RESOURCE_REWRITE com before/after/regra/confiança/motivo; preserváveis mantêm hash dos bytes.

## Aceite específico

- Teste ID ausente, tag substitutiva/cíclica, dependência opcional ausente, condição falsa, reload e parser truncado.
- Comportamento correto exige crafting/fornalha/stonecutter/loot acionados no jogo quando reivindicados, além de parse.

## Limites e parada

Não transformar dados de worldgen ou comandos desconhecidos só por estarem em JSON/mcfunction.

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

Risco: alto: schemas, referências e reload. Estimativa: Por serializer/formato e contexto; não tratar todos os datapacks como cópia de arquivos.

- [docs/RESOURCE_API_BASELINES.md](../../../docs/RESOURCE_API_BASELINES.md)
- [docs/RESOURCE_CORPUS_AUDIT.md](../../../docs/RESOURCE_CORPUS_AUDIT.md)
- [docs/DATAPACK_RECIPES_PROFILE.md](../../../docs/DATAPACK_RECIPES_PROFILE.md)
- [docs/DATAPACK_RECIPES_VALIDATION.md](../../../docs/DATAPACK_RECIPES_VALIDATION.md)

