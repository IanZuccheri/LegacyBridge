# LB-02 — Motor reutilizável, mappings e dados distribuíveis

Número canônico: 2. Estado autoritativo: [status.json](../status.json), entrada `LB-02`.

## Prompt para executar

Trabalhe apenas no projeto existente LegacyBridge. Leia integralmente o
[contrato comum](../README.md), seu status atual e as referências abaixo antes de agir.
Execute um recorte verificável deste prompt, respeitando as dependências de cada corte;
não presuma que o pedido autoriza completar toda a família numa única mensagem.
Se este não for o cursor ativo, confirme a mudança de prioridade no contrato antes de implementar.

## Ponto de partida da revisão 2026-09-07

CLI e UI usam a mesma API tipada. O simple-content-v1 já inclui dados finitos; os cinco perfis de desenvolvimento ainda exigem dados completos explícitos. Não existe mapping genérico embutido para todo Minecraft.

## Recortes e dependências

- `DATA-SIMPLE-01`: Dados finitos incluídos: verificação de APIs, equivalência CLI/serviço e conversão sem bundle/cache registradas, não aceite visual. Requer: `PRODUCT-BASE`.
- `DATA-EXPAND`: Vocabulários versionados por operação, proveniência/direitos e fluxo explícito para dados não redistribuíveis. Requer: `DATA-SIMPLE-01`, `COMPOSE-01`.
- `ENGINE-ISOLATION`: Qualificar limites agregados e decidir isolamento de processo se for necessária garantia rígida de memória. Requer: `COMPOSE-01`.

Os estados mutáveis ficam somente no JSON. Um corte aceito prova apenas o escopo
da sua descrição/evidência, não todo o prompt nem artefatos posteriores.

## Trabalho solicitado

Checkpoint mais recente: [revisão de dados e limites agregados](../../PROMPT_02_HEADLESS.md).
COMPOSITION_DATA_SCOPE foi entregue pela rota full local explícita após auditar
ownership/hierarquias, com revisão tipada/hash no CLI/produto. ENGINE-ISOLATION
tem implementação inicial: 1 ativo/33 admitidos por classloader, métricas e freio
cooperativo, sem limite rígido. 101 testes focados passaram/um symlink SKIP;
seis perfis e duas prévias isoladas passaram. **Não é aceite de todo LB-02**:
retenção exata de ASM/index/imagens, RSS/adversarial/carga, COMPOSE-01 e nativo
continuam pendentes. Desenvolvimento segue LB-03 técnico; não repetir a matriz
como única próxima entrega. Os parágrafos seguintes registram o escopo anterior.

Entregue em 2026-09-12: [FINITE_COHORT_DATA](../../FINITE_COHORT_DATA.md), contrato
incluído de 537 bytes para a dupla autoral exata, produto/CLI/serviço sem bundle
nesse recorte e sem promover Convert/Apply. 167 casos focados e probe isolado
sem cache passaram; QA nativa e aceite de DATA-EXPAND continuam pendentes.

Cursor headless anterior, agora implementado: **COMPOSITION_DATA_SCOPE**. Auditar os dados mínimos
realmente consumidos pela prévia individual de lógica+recipes já implementada,
incluindo ownership, métodos/descriptors e receitas exatas. O inventário de uma
anotação da dupla não serve para isso. Separar contratos/inventários de dados
completos, versionar hashes/escopo/proveniência e auditar direitos.
Implementar somente uma rota de dados permitida: incluída se justificada, ou
preparo/importação local explícito caso não seja redistribuível. Nenhum download
oculto, bundle LOCAL_USE_ONLY embutido ou referência desconhecida admitida por
falta de regra. Preservar o built-in simples e o fluxo CLI completo.
Usar a parte estática existente de COMPOSE-01 como base de desenvolvimento;
seu requires continua obrigatório para aceite. Não abrir Minecraft/servidor/tela.

1. Preserve contratos de inventário, análise, planejamento, progresso, cancelamento e erros. UI não chama CLI.main, stdout ou System.exit.
2. Componha dados reais de namespaces de origem e destino sem supor identidade de nomes ofuscados entre versões. Separe rename/package/descriptor/contrato removido/adapter/manual; HIGH não vira VERIFIED.
3. Para cada nova operação, guarde fonte exata, namespace, hash, licença/proveniência e cobertura/incompletude. Não embuta classes antigas nem transforme uma tabela extraída em autorização presumida.
4. Se redistribuição não for permitida, implemente consentimento e aquisição/preparo explícitos, integridade, falha offline legível e cache regenerável; sem downloads ocultos.
5. Meça retenção de árvores ASM, tamanho inflado, grafos e imagens. Um worker fora do render e deadline cooperativo não limitam rigidamente o heap; não aumente limites sem medições.
6. Teste paridade CLI/serviço, cache vazio, dados ausentes/corrompidos/incompatíveis, concorrência, timeout e cancelamento na fronteira de publicação.

## Aceite específico

- Mesma entrada/dados/regras produzem o mesmo plano, reports e saída; falta de dados bloqueia antes de gerar JAR parcial.
- Relatório diferencia escopo finito e inventário completo. Exportar o primeiro como o segundo é recusado.
- Nenhuma alegação offline do jogo com base só em conversão sem acesso à rede.

## Limites e parada

Sem distribuir bundle completo LOCAL_USE_ONLY ou simular independência embarcando Minecraft/Forge antigos.

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

Risco: alto: contratos, limites e direitos. Estimativa: Dados simples implementados; vocabulários novos estimados por recorte após auditoria.

- [docs/EMBEDDED_SIMPLE_CONTENT_DATA.md](../../../docs/EMBEDDED_SIMPLE_CONTENT_DATA.md)
- [docs/FINITE_COHORT_DATA.md](../../../docs/FINITE_COHORT_DATA.md)
- [docs/PORTABLE_MAPPING_DATA.md](../../../docs/PORTABLE_MAPPING_DATA.md)
- [docs/MAPPINGS.md](../../../docs/MAPPINGS.md)
- [docs/ENGINE_EXECUTION_CONTROLS.md](../../../docs/ENGINE_EXECUTION_CONTROLS.md)
- [docs/ENGINE_SERVICE_VALIDATION.md](../../../docs/ENGINE_SERVICE_VALIDATION.md)
