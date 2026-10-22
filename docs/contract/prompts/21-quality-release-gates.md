# LB-21 — Qualificação cumulativa, segurança e prontidão de distribuição

Número canônico: 21. Estado autoritativo: [status.json](../status.json), entrada `LB-21`.

## Prompt para executar

Trabalhe apenas no projeto existente LegacyBridge. Leia integralmente o
[contrato comum](../README.md), seu status atual e as referências abaixo antes de agir.
Execute um recorte verificável deste prompt, respeitando as dependências de cada corte;
não presuma que o pedido autoriza completar toda a família numa única mensagem.
Se este não for o cursor ativo, confirme a mudança de prioridade no contrato antes de implementar.

## Ponto de partida da revisão 2026-09-07

Há muitos testes e observações locais, mas nenhum aceite de ampla compatibilidade. A campanha cumulativa precisa cobrir contratos combinados e o mesmo artefato público.

## Recortes e dependências

- `QUALITY-SECURITY`: Arquivos adversariais, budgets, fuzzing, acesso a paths, cancelamento e limites agregados. Requer: `ENGINE-ISOLATION`, `LINKAGE-GENERAL`.
- `QUALITY-COMPOSED`: Coortes autorais/existentes com famílias combinadas; comportamento/MP/persistência/anti-dupe e soak. Requer: `COMPOSE-MIXED`, `ITEM-EQUIPMENT`, `BLOCK-TICKS`, `DATA-LOOT`, `DATA-ASSETS`, `NET-C2S`, `CAP-FLUID`, `PERSIST-SAVEDDATA`, `BE-MACHINE`, `ENTITY-CORPUS`, `MENU-ANTIDUPE`, `RENDER-EFFECTS`, `WORLD-DIMENSION`, `DEPS-LIBRARIES`, `HOOKS-PUBLIC-ROUTE`, `ITEM-EFFECTS`, `CREATIVE-TABS`, `DATA-ADV-FUNCTIONS`, `ITEM-USE-QA`, `CORPUS-PRIORITY`, `LIFE-CONFIG`, `LIFE-COMMANDS`.
- `RELEASE-QUAL`: Mesmo JAR final, direitos, dados, UI/instalação/servidor, documentação e revisão de qualidade antes de intake final. Requer: `QUALITY-SECURITY`, `QUALITY-COMPOSED`, `PACKAGE-RIGHTS`, `PUBLIC-CURRENT-E2E`, `PUBLIC-PROMOTED-E2E`, `INSTALL-RECOVERY-QA`.

Os estados mutáveis ficam somente no JSON. Um corte aceito prova apenas o escopo
da sua descrição/evidência, não todo o prompt nem artefatos posteriores.

## Trabalho solicitado

1. Antes de executar esta etapa, confira todas as dependências no status.json. Se um caso não tem rota permitida, registre MANUAL/UNSUPPORTED; não apague o gate. Fechar o plano com exclusão exige revisão de escopo aprovada, sem alegar suporte.
2. Segurança: ZIP bombs/paths/duplicatas, classes/resources enormes/cíclicos/truncados, descritores malformados, parser/ASM graphs, budgets, filas, OOM evitável, cancelamento em chamadas opacas e saída temporária. Conversão estática não torna seguro executar mod de terceiro.
3. Teste combinações de contratos, colisões entre mods/adapters/libs, save/reload, death/clone, dimensão, disconnect/relog, dois clientes/dedicado e anti-dupe. Use invariantes por recurso, não apenas boot.
4. Meça tempo/heap/alocações, retenção de jobs/reports, render responsiveness, p95 quando amostra permitir, throughput e soak. Se necessário, isole o worker antes de elevar limites.
5. Reexecute o fluxo público e falhas de instalação para o mesmo hash final; arquivo em staging/bytecode válido não é suporte em jogo.
6. Feche notices/direitos/fontes necessárias, instruções inglês/pt, página CurseForge em inglês e matriz de suporte/exclusões. Restart manual é resultado válido; mundos antigos não são promessa.
7. Produza relatório de escopo selecionado entregue versus explicitamente excluído, sem chamar scaffold de completo e sem herdar PASS de artefato diferente.

## Aceite específico

- A revisão de prontidão explicita zero/muitos gates pendentes, hashes, amostra, hardware e falhas; nenhum percentual universal de compatibilidade.
- Só após RELEASE-QUAL aceito e escopo reconciliado pode iniciar aquisição de LB-22. Publicar ainda é uma autorização separada.

## Limites e parada

Não publicar nem baixar novos mods famosos para esconder pendência; não declarar qualidade por total de testes.

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

Risco: crítico: produto final versus testes isolados. Estimativa: Por coorte/hardware/hash; direitos, soak e QA humana não têm prazo automático.

- [docs/SECURITY.md](../../../docs/SECURITY.md)
- [docs/PUBLIC_ONLY_END_TO_END_VALIDATION.md](../../../docs/PUBLIC_ONLY_END_TO_END_VALIDATION.md)
- [docs/PACKAGING_RIGHTS.md](../../../docs/PACKAGING_RIGHTS.md)
- [docs/REPORT_SCHEMA.md](../../../docs/REPORT_SCHEMA.md)
- [corpus/BACKLOG.md](../../../corpus/BACKLOG.md)

