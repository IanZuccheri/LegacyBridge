# LB-08 — Lógica preservada, linkage e composição de contratos

Número canônico: 8. Estado autoritativo: [status.json](../status.json), entrada `LB-08`.

## Prompt para executar

Trabalhe apenas no projeto existente LegacyBridge. Leia integralmente o
[contrato comum](../README.md), seu status atual e as referências abaixo antes de agir.
Execute um recorte verificável deste prompt, respeitando as dependências de cada corte;
não presuma que o pedido autoriza completar toda a família numa única mensagem.
Se este não for o cursor ativo, confirme a mudança de prioridade no contrato antes de implementar.

## Ponto de partida da revisão 2026-09-07

O motor preserva corpos dentro de formatos fechados. Os seis perfis são mutuamente exclusivos; não há planejador que una registro, recursos, callbacks e efeitos. Receitas de métodos exatos não constituem suporte a código arbitrário.

## Recortes e dependências

- `LOGIC-BASE`: Fixture fechado com helpers/subclasse/interfaces, ASM/linkage e comparação nativa limitada registrados. Requer: `GOV-01`.
- `COMPOSE-01`: Plano imutável de operações compatíveis; primeiro fixture combinando dois contratos já existentes sem perder corpos. Requer: `LOGIC-BASE`.
- `LINKAGE-GENERAL`: Expandir prova de ownership, hierarquia/acesso/invocação e admissão independente do layout do compilador. Requer: `COMPOSE-01`.
- `COMPOSE-MIXED`: Validar combinações com dependência real e conflitos de contratos, não apenas perfis isolados. Requer: `LINKAGE-GENERAL`, `DEPS-COHORT`, `LIFE-EVENTS`.

Os estados mutáveis ficam somente no JSON. Um corte aceito prova apenas o escopo
da sua descrição/evidência, não todo o prompt nem artefatos posteriores.

## Trabalho solicitado

O recorte [OWNED_RECEIVER_HANDLES](../../OWNED_RECEIVER_HANDLES.md) foi
implementado privadamente com fixture real, receptor this provado por dataflow,
assinaturas/captura exatas e leitura independente source/target/readback. Não
habilita perfil/export/instalação nem combina contratos automaticamente.
O cursor de desenvolvimento segue para **LB-09 / LIFE-EVENTS /
EVENT_PRIORITY_CANCELLATION**; COMPOSE-01/LINKAGE-GENERAL/requires e comparações
nativas permanecem PARTIAL. Não repetir este harness como próximo automático.

Escopo histórico executado após OWNED_INTERFACE_DEFAULTS (2026-09-19):
**LINKAGE-GENERAL / OWNED_RECEIVER_HANDLES**. Provar um caminho finito de
this::method ligado a IntUnaryOperator num helper próprio final, com receptor,
acesso/opcode, captura, retorno e despacho exatos. Preservar avaliação/captura e
timing de nulidade; não inferir suporte a qualquer method handle, boxing ou
altMetafactory. Fixture real alternativo e negativos de acesso/receiver/capture/
contrato inválidos. Auditar qualquer API adicional antes de criar receita.
Usar candidato privado/readback, preservar seis gramáticas e não unir recortes
implicitamente. Não repetir harness default-interface nem ampliar conteúdo.
Prova estática não é comparação nativa; COMPOSE-01/LINKAGE-GENERAL/requires PARTIAL.

Recorte mais recente entregue: [OWNED_INTERFACE_DEFAULTS](../../OWNED_INTERFACE_DEFAULTS.md),
interface plana/default/hook, helper final/override/Interface.super e lambda
estática com captura exata. Permissão do verificador é separada e rederivada dos
bytes; receptor super deve ser this. Sem perfil público novo ou exportação.

Recorte anterior entregue: [OWNED_HIERARCHY_DISPATCH](../../OWNED_HIERARCHY_DISPATCH.md),
uma base Item abstrata e folha final própria, prova de despacho virtual/super
source/target/readback, acesso entre packages e fixture real. Sem perfil público
novo ou publicação do candidato. Não unir automaticamente esse recorte ao de
construtores nem inferir autorização de instalação.

Recorte anterior entregue: [OWNED_CONSTRUCTOR_DELEGATION](../../OWNED_CONSTRUCTOR_DELEGATION.md).
A admissão pública em
`LogicProfileShape.itemConstructor` exige um único construtor público `()V` e
prefixo literal de Properties/super. Foi implementada prova privada separada de uma
cadeia finita/acíclica de construtores próprios `this(...)`, com fixture autoral
de layout alternativo, argumentos/efeitos preservados, chamadas super e linkage
exatos; com negativos de ciclo, acesso/opcode, inicialização e chamadas
externas desconhecidas. Preservar as seis gramáticas existentes: não apagar o
guard, copiar código desconhecido ou habilitar novo perfil/Apply por conveniência.
Usar as bases estáticas de composição/linkage já implementadas, sem expandir
harnesses nativos. COMPOSE-01 continua PARTIAL; sua comparação nativa/requires
nunca são satisfeitos por essa preparação headless. Primeiro delimitar a prova
e o candidato privado, sem promessa de construtores Java arbitrários.

1. Preserve os seis perfis de regressão intactos. Separe admissão semântica, mappings exatos, operações implementadas, política de recursos e geração do plano; não crie apenas mais um switch ou uma união de allowlists.
2. Primeiro fixture: registro + helper observável e dois contratos existentes compatíveis, com layout diferente e mesmo comportamento. Compare original/destino; negativo em que um bootstrap apagaria lógica deve abortar.
3. Cada operação declara precondições, ownership, efeitos preservados, fase/ordem/bus/lado/thread, transformações conflitantes, versão dos dados e ABI. Resolva a composição inteira antes de publicar qualquer arquivo.
4. Generalize somente uma construção por corte: métodos próprios, campos/estática, construtores/super/delegação, overrides/dispatch/default interfaces, exceções/fluxo, lambdas/handles. Examine annotations, descriptors, signatures, nests e metadados de acesso quando encontrados.
5. Indy/condy, geração dinâmica, native e reflexão não reconhecidos bloqueiam; métodos desconhecidos não passam porque ClassRemapper não encontrou regra.
6. Verifique linkage offline além do ASM: proprietário/resolução, presença, acesso, staticness, opcode, inicialização, construtor, override e fronteiras de módulo/classloader. Testes de acesso devem usar inventários reais, não carregamento do original.
7. Não inferir VERIFIED de HIGH ou nomes parecidos. Mudança de contrato exige receita independente com diferenças documentadas.

## Aceite específico

- Original e convertido preservam efeito observável do fixture, não só instruções/símbolos; novos layouts válidos passam e quase-iguais não suportados bloqueiam.
- Toda classe original é contabilizada como preservada/regravada por operação ou removida com prova de grafo substituído; código desconhecido não sobrevive fingindo compatibilidade.
- Composição incompatível gera relatório sem saída parcial. Promoção pública depende de admissão de instalação LB-05.

## Limites e parada

Sem promessa de equivalência para todo programa Java; não substituir corpos arbitrários por valores fictícios.

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

Risco: crítico: semântica arbitrária e despacho. Estimativa: Arquitetura e primeiro fixture combinado antes de estimar generalização; não estimar por quantidade de mappings.

- [docs/LOGIC_PRESERVING_PROFILE.md](../../../docs/LOGIC_PRESERVING_PROFILE.md)
- [docs/LOGIC_API_BASELINES.md](../../../docs/LOGIC_API_BASELINES.md)
- [docs/ASM_PIPELINE.md](../../../docs/ASM_PIPELINE.md)
- [docs/MAPPINGS.md](../../../docs/MAPPINGS.md)
- [docs/DEPENDENCY_GRAPH.md](../../../docs/DEPENDENCY_GRAPH.md)
