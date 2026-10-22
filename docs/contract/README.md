# Contrato de evolução do LegacyBridge

Prioridade em 2026-10-22: [publicação gratuita do código e entrega experimental](../PUBLICATION_2026-10-22.md).
GPL-3.0-only com exceção Minecraft/Forge aprovada; sem royalties. O proprietário
fará a submissão CurseForge. O cursor de desenvolvimento permanece registrado,
mas esta entrega não conclui os recortes nem substitui validações nativas.

Versão do contrato: `2026.09.10.1`. Schema: `1`. Revisão inicial: 2026-09-07.
Fonte mutável de estados, dependências e cursor: [status.json](status.json).
Auditoria que originou este plano: [AUDIT_2026-09-07.md](AUDIT_2026-09-07.md).

## Objetivo e limite honesto

Construir cobertura progressivamente ampla de mods Forge 1.16.5 em Forge 1.20.1,
medida por releases/coortes e comportamento verificável. “Quase universal” é uma
aspiração, não um critério que este plano possa garantir. Um programa Java pode
usar comportamento externo/dinâmico que a análise estática não prova equivalente;
alterações profundas da plataforma podem não ter rota permitida.

A regra de não modificar classes Minecraft/Forge/Java exclui patches arbitrários
de coremods/mixins contra essas classes. Não se trata apenas de “faltam mappings”.
Alguns resultados permanecerão MANUAL/UNSUPPORTED, inclusive ao fim deste contrato.
A auditoria de uma impossibilidade pode ser concluída; o mod continua não suportado.

Esta é uma **nova consolidação canônica LB-00 a LB-22**: 23 entradas contando o
prompt zero. Os temas 0–13 retomam a sequência identificável de produto/engine/UI/
QA/corpus/lógica/conteúdo/network/capabilities; 14–22 formalizam agora o restante.
Não afirmamos recuperar literalmente uma lista histórica completa que não está
no repositório. `P1–P6` antigos são marcos internos do produto, não outra contagem
de todos os prompts. IDs existentes `DATA-SIMPLE-01-NATIVE_UI`, `NET-QA-01`,
`NET-QA-02-HANDSHAKE` e `NET-QA-03` continuam preservados.

## Baseline e invariantes obrigatórios

- Projeto: `legacy-bridge`, qualquer que seja a aba aberta no IDE. Não recriar o projeto.
- Origem Minecraft 1.16.5 / Forge 36.2.42; destino Minecraft 1.20.1 / Forge 47.4.23 / Java 17. Preservar as versões fixadas, incluindo ferramentas.
- Um JAR público obrigatório. Identidade `legacybridge_runtime` / `0.1.1-SNAPSHOT`; mudanças de ABI/versão exigem migração explícita e regressões.
- Preservar trabalho do usuário e os seis perfis de regressão. O `simple-content-v1` público permanece intacto enquanto um novo perfil não passar sua promoção completa.
- Ler instruções, arquitetura, código, APIs/mappings/fontes reais das duas versões e ledger do corte. Documentação oficial orienta; assinatura/equivalência exige fonte/bytecode das versões exatas, hashes e revisão.
- Análise/conversão nunca executam classes/scripts originais. Execução de originais autorizados só na QA isolada, etapa distinta.
- Transformar apenas classes cujo ownership pertence ao artefato autorizado da operação. Dependência é outra operação explícita; Minecraft, Forge, Java e bibliotecas da plataforma nunca são reescritos.
- Cada mudança usa regra exata com operação/adapter implementado e documentação Legacy API / Target API / Transformation / Behavior differences / Known limitations. Sem stubs, valores fictícios, exceções ignoradas ou HIGH promovido automaticamente a VERIFIED.
- Preservar modId, registry names, ResourceLocations, lógica e bytes dos recursos quando possível. Diferenças necessárias são explícitas. Nenhum código/recurso desconhecido é mantido como se fosse compatível, nem removido silenciosamente.
- CompatibilityReport e TransformationLog mantêm schemas versionados e erros estruturados. Validação ASM + referências/linkage/acesso no escopo anunciado antes de publicar saída; falha cancela o JAR completo, não apenas a classe que falhou.
- Originais nunca sobrescritos. Pastas input/staging/reports/cache/transactions separadas; instalação exige aprovação, hashes, dependências/ABI, journal e recuperação por arquivo. Sem hot activation ou alterações em mundos do usuário.
- Trabalho pesado limitado fora do render; cancelamento não publica parcial. Um worker/deadline cooperativo não garante isolamento rígido do heap.
- Build/test serial após alterações. Jogos são QA separada, apenas quando autorizada, com capacidade de máquina e evidências reais; não reduzir gates por falta de RAM/foco nem encerrar processos alheios.
- Direitos de dados/dependências/originais auditados. Sem downloads ocultos, redistribuição privada, bootstrap invasivo ou publicação sem autorização.

## Onde estamos de verdade

**Checkpoint atual 2026-10-05:** [dispenser restrito](../IRON_HELMET_DISPENSER.md)
implementado: consulta/predicados nativos, primeiro receiver ServerPlayer/Zombie,
split1 e efeitos ordenados. Zombie recebe escrita silenciosa e drop/persistência.
Receiver desconhecido lança antes do consumo, nunca vira false/fallback ou outro
alvo. Um descriptor novo; 68 anteriores e classes do item/efeito HEAD intactos.
Sem registro da operação, recipe, candidato ou Apply.

[Evidência](../evidence/iron-helmet-dispenser-2026-10-05.json) separa os bytes
compilados/endpoints sintéticos da consulta espacial real, cópia nativa e efeitos
de jogo. O diagnóstico continua UNSUPPORTED; cliente/servidor/multiplayer NOT_RUN.

Próximo **ITEM-EQUIPMENT / IRON_HELMET_INTEGRATED_SUCCESSOR**:
[plano finito](../IRON_HELMET_INTEGRATED_ITEM_PLAN.md), novo item com identidade
explícita, ownership, use e callback dispenser integrado. Não repetir helpers
independentes nem ampliar suas guards. Outros receivers vanilla (ArmorStand),
fallback, Equipable/rendering/coortes/nativo seguem gates antes de candidato.

Contagem 15 ACCEPTED / 19 PARTIAL / dois BLOCKED / 45 NOT_STARTED,
não percentual de compatibilidade. Sem jogos, aquisição, publicação ou licença.
[Ledger anterior](../evidence/iron-helmet-adapter-2026-10-05.json) permanece histórico.

Checkpoint anterior [OWNED_STACK_DAMAGE](../OWNED_STACK_DAMAGE_CONTRACT.md):
helper privado de cinco métodos, preservado junto com seus
[5.012 testes históricos](../evidence/owned-stack-damage-candidate-2026-10-04.json).
Sua gramática continua sem callback; o novo recorte tem admissão independente.

**Checkpoint anterior 2026-09-26:** [LB-10 Block/BlockItem](../PLAIN_BLOCK_CONTRACT.md)
entregou PLAIN_BLOCK_WITH_ITEM como candidato privado com classe/métodos originais,
receita contextual de STONE, lookup tardio e tag de picareta gerada/registrada.
ASM, linkage e source/target/readback não substituem jogo. BLOCK-STATE PARTIAL;
próximo desenvolvimento **HORIZONTAL_FACING**, contrato finito de estado e placement.
Os requires permanecem; nenhuma promoção de perfil/Apply, classe do jogo alterada,
nova aquisição ou mudança de licença. [Resultados e hashes](../evidence/plain-block-2026-09-26.json).
**15 ACCEPTED / 19 PARTIAL / dois BLOCKED / 45 NOT_STARTED**; contagem de cortes,
não de mods compatíveis. Nativo continua NOT_RUN e jogos/tela não autorizados.

**Checkpoint anterior 2026-09-19:** [LB-10 Item durável](../DURABLE_ITEM_CONTRACT.md)
entregou DURABILITY_REPAIR_PREDICATE como candidato privado: duas classes/quatro
métodos, construtor nativo/stack 1, predicado de ferro e despacho virtual preservados.
631 regressões PASS (49 novas), fixture quatro testes PASS, assemble/auditorias/CLI
PASS. Nativo NOT_RUN. Seis gramáticas/Apply, 45 descriptors prévios, runtime/ABI,
dados e licença intactos; duas operações OFFLINE novas. ITEM-EQUIPMENT PARTIAL.
**15 ACCEPTED / 18 PARTIAL / dois BLOCKED / 46 NOT_STARTED**, sem dispensa de requires.
Próximo desenvolvimento **LB-10 / BLOCK-STATE / PLAIN_BLOCK_WITH_ITEM**; dano,
quebra/consumo, ferramentas, armaduras, atributos e aceites nativos ficam visíveis.
Sem jogos, aquisição ou publicação. [Ledger](../evidence/durable-item-2026-09-19.json).

**Checkpoint anterior 2026-09-19:** [LB-09 integrado](../LIFECYCLE_SUITE.md)
entregou o recorte CONFIG_RELOAD_SCOPE + TYPED_COMMAND_TREE em composição com
late registry e evento próprio: sete classes/34 métodos preservados; prova
source/target/readback, ASM/linkage/acesso e cancelamento. Comparação isolada
de Brigadier real testa APIs, não o mod original nem o jogo.
LB-09 permanece PARTIAL; não foram reduzidos requires ou famílias pendentes.
**15 ACCEPTED / 17 PARTIAL / dois BLOCKED / 47 NOT_STARTED** continuam.
Cursor de desenvolvimento avança para **LB-10 / ITEM-EQUIPMENT /
DURABILITY_REPAIR**, usando bases estáticas sem antecipar seu aceite.
Sem jogos, mudança de gramáticas/Apply, aquisição ou publicação.
[Evidência](../evidence/lifecycle-suite-2026-09-19.json).

**Checkpoint anterior 2026-09-19:** [LB-09 config SERVER](../SERVER_CONFIG_SCOPE.md)
entregou SERVER_CONFIG_SCOPE como candidato privado: quatro classes/dezesseis
métodos, schema boolean/int nativo, registro nos dois lados físicos, snapshot
em ServerStarted e limpeza em ServerStopped. Prova source/target/readback,
API/SAM/acesso e temporários sem executar classes originais. Seis gramáticas,
Apply público, identidade/ABI e dados públicos intactos.
LIFE-CONFIG segue PARTIAL; **15 ACCEPTED / 17 PARTIAL / dois BLOCKED / 47 NOT_STARTED**.
Próximo **LB-09 / LIFE-CONFIG / CONFIG_RELOAD_SCOPE**: callbacks reais Loading/
Reloading, spec/type, thread e efeitos repetidos; sem inventar Unloading antigo,
sync ou reinicialização. Sem jogos, promoção pública ou descarte de requires.
Evidência consolidada no [ledger](../evidence/server-config-2026-09-19.json).

**Checkpoint anterior 2026-09-19:** [LB-09 config CLIENT](../CLIENT_CONFIG_SCOPE.md)
entregou CLIENT_CONFIG_SCOPE como candidato privado: guarda física exata,
schema boolean/int nativo, defaults/ranges preservados, MOD client setup e
leitura enfileirada após carga. Source/target/readback, API/SAM/acesso e temporário
verificados; nenhuma classe original executada, nenhum JAR publicado.
Seis gramáticas, Apply público, ABI e dados incluídos intactos.
LIFE-CONFIG agora PARTIAL; **15 ACCEPTED / 17 PARTIAL / dois BLOCKED / 47 NOT_STARTED**.
Próximo **LB-09 / LIFE-CONFIG / SERVER_CONFIG_SCOPE**, separado de CLIENT/COMMON:
auditar carga/descarregamento por mundo, defaults e sync nativo; fixture finito
independente, sem jogos ou promoção pública. Reload permanece recorte separado.
Evidências e gates no [ledger](../evidence/client-config-2026-09-19.json).
LIFE-EVENTS/COMPOSE-01/requires não são aceitos por esta prova estática.

**Checkpoint anterior 2026-09-19:** [LB-09 eventos](../EVENT_PRIORITY_CANCELLATION.md)
entregou EVENT_PRIORITY_CANCELLATION como candidato privado: quatro classes,
doze métodos, evento cancelável, listeners HIGH/LOW/receiveCancelled, bus exato,
common setup enfileirado e prova source/target/readback. 361 checks focados e
88 finais passaram, com 410 casos distintos/41 novos; regressões corrigidas de
cancelamento e pacote no [ledger](../evidence/event-priority-2026-09-19.json).
Seis gramáticas/Apply público/ABI/dados/licença intactos. Nenhum jogo executado.
LIFE-EVENTS agora PARTIAL; **15 ACCEPTED / 16 PARTIAL / dois BLOCKED / 48 NOT_STARTED**.
Próximo **LB-09 / LIFE-CONFIG / CLIENT_CONFIG_SCOPE**: config CLIENT finita,
defaults/validação/lado físico e leitura após carga; COMMON/SERVER/reload/sync
continuam contratos separados. Não repetir harness de eventos.
COMPOSE-01/LIFE-EVENTS/requires e comparações nativas não são aceitos por essa
prova estática; ela permite desenvolver, não promover o perfil público.

**Checkpoint anterior 2026-09-19:** [LB-08 receiver handles](../OWNED_RECEIVER_HANDLES.md)
entregou OWNED_RECEIVER_HANDLES: this::method/IntUnaryOperator em helper final,
assinatura/captura/acesso exatos e prova de receptor this inicializado por dataflow.
Fixture real de três classes/nove métodos, candidato privado source/target/readback
e serviço limitado compartilhado; nenhuma classe original executada ou JAR publicado.
60 novos checks passaram; regressões, pacote e pins no
[ledger](../evidence/owned-receiver-2026-09-19.json). Seis gramáticas/Apply/ABI intactos.
Contagem permanece 15 ACCEPTED / 15 PARTIAL / dois BLOCKED / 49 NOT_STARTED.
Desenvolvimento avança a **LB-09 / LIFE-EVENTS / EVENT_PRIORITY_CANCELLATION**:
auditar APIs/padrões reais e implementar fixture finito de dois listeners com
prioridade/receiveCanceled, bus e setup enfileirado, negativos de lado/registry.
Não repetir harness de handles nem ampliar allowlists genéricas.
COMPOSE-01/LINKAGE-GENERAL/requires e comparações nativas continuam PARTIAL;
bases estáticas permitem desenvolvimento, não aceite ou promoção. Nenhum jogo.

**Checkpoint anterior 2026-09-19:** [LB-08 interfaces/default](../OWNED_INTERFACE_DEFAULTS.md)
entregou OWNED_INTERFACE_DEFAULTS: interface própria plana com default/hook e
helper final com override/Interface.super. Gate privado rederiva prova dos bytes,
exige receptor this para super e preserva restrições dos verificadores anteriores.
Fixture real de quatro classes/12 métodos, lambda estática com captura e prova
source/target/readback. 214 checks focados e 47 regressões passaram, 48 casos novos;
hashes/pacote/limites no [ledger](../evidence/owned-interface-2026-09-19.json).
Sem promoção dos seis perfis/Apply, jogos, aquisição ou mudança de direitos.
Contagem inalterada 15/15/2/49; COMPOSE-01/LINKAGE-GENERAL continuam PARTIAL.
Próximo **LB-08 / LINKAGE-GENERAL / OWNED_RECEIVER_HANDLES**: um caminho finito
this::method / IntUnaryOperator em helper próprio final, receptor/capture/retorno/
acesso/opcode exatos e timing preservado. Não liberar handles arbitrários, boxing
ou altMetafactory; não repetir harness default-interface. Requires/nativo pendentes.

**Checkpoint anterior 2026-09-19:** [LB-08 hierarquia própria](../OWNED_HIERARCHY_DISPATCH.md)
entregou OWNED_HIERARCHY_DISPATCH: base Item abstrata própria e folha concreta
final registrada, seleção virtual/super exata e acesso protegido entre packages.
Prova source/target/readback e fixture real de quatro classes/13 métodos; candidato
privado pelo worker compartilhado, sem publicação. Corpos/resources preservados,
regressões e hashes no [ledger](../evidence/owned-hierarchy-2026-09-19.json).
Seis gramáticas e Apply público intactos; sem jogos/dados novos/licença/aquisição.
Contagem continua 15 ACCEPTED / 15 PARTIAL / dois BLOCKED / 49 NOT_STARTED.
Próximo **LB-08 / LINKAGE-GENERAL / OWNED_INTERFACE_DEFAULTS**: um caminho finito
de interface própria/default em helper, override e Interface.super, sem diamonds
ou APIs externas novas. Não repetir harness de hierarquia ou promover suporte
arbitrário. COMPOSE-01/LINKAGE-GENERAL/requires e comparação nativa pendentes.

**Checkpoint anterior 2026-09-19:** [LB-08 construtores próprios](../OWNED_CONSTRUCTOR_DELEGATION.md)
entregou OWNED_CONSTRUCTOR_DELEGATION: prova finita/acíclica separada, serviço
limitado compartilhado e candidato privado serializado/reaberto sem publicação.
Fixture real Forge 36 com três classes/11 métodos, corpos e recursos preservados;
negativos, cancelamento e regressões dos perfis no
[ledger](../evidence/owned-constructors-2026-09-19.json). Seis gramáticas e Apply
público preservados; sem jogos, licença, aquisição ou dados novos embutidos.
LINKAGE-GENERAL passa de NOT_STARTED para PARTIAL, não ACCEPTED; contagem
15 ACCEPTED / 15 PARTIAL / dois BLOCKED / 49 NOT_STARTED (não mods compatíveis).
Próximo **LB-08 / LINKAGE-GENERAL / OWNED_HIERARCHY_DISPATCH**: prova privada de
superclasse Item intermediária própria, folha registrada e despacho/acesso
exatos. Não repetir o harness de construtores nem promover suporte arbitrário.
Requires de COMPOSE-01 e gates nativos continuam pendentes.

**Checkpoint anterior 2026-09-19:** [LB-04 tela de updates](../PRESERVED_UPDATE_SCREEN.md)
entregou PRESERVED_UPDATE_SCREEN no mesmo host developer: seleção ADD/UPDATE e
dos dois lados, versões/hashes observados, confirmação própria, recuperação e
histórico inerte. 115 casos e 39 rechecagens finais PASS, sobrepostos, 23 novos,
sem falhas/erros/skips. Assemble/auditorias/CLI PASS; hash/delta no
[ledger](../evidence/preserved-update-screen-2026-09-19.json). Sem jogos, promoção
pública, dados novos, licença ou aquisição. UI-PROMOTION PARTIAL, nativo NOT_RUN.
Cursor de desenvolvimento: **LB-08 / LINKAGE-GENERAL / OWNED_CONSTRUCTOR_DELEGATION**.
Retomar o motor com prova privada de uma cadeia finita de construtores próprios
e fixture de layout alternativo; não remover o guard atual de construtor único
nem mudar seis perfis para simular cobertura. As bases estáticas de COMPOSE-01
permitem desenvolvimento, não satisfazem requires/aceites nativos. Não repetir
tela/backend/harnesses. Contagem permanece 15/14/2/50 em 81 cortes; não é métrica
de mods compatíveis. Gates nativos/promoção continuam obrigatórios.

**Checkpoint anterior 2026-09-19:** [LB-01 coordenador de updates](../PRESERVED_UPDATE_COORDINATOR.md)
entregou PRESERVED_UPDATE_COORDINATOR: seleção dos dois lados por pins, preview
read-only, confirmação própria de uso único, worker/gate/lease compartilhados,
update/resume/restore reais, recibos e histórico inertes separados. 94 regressões
focadas passaram (37 novas), zero falhas/erros/skips; assemble/auditorias/CLI
passaram. Hash/delta/escopo no [ledger](../evidence/preserved-update-coordinator-2026-09-19.json).
Cursor: **LB-04 / UI-PROMOTION / PRESERVED_UPDATE_SCREEN**, formulário nativo
developer usando seleção/revisão/consentimento e recuperação da API pronta.
Reutilizar host/sessão/guardas/worker; não repetir backend/coordenador/preparação.
Sem Minecraft, promoção pública, aquisição ou mudança de licença; nativo NOT_RUN.
Contagem inalterada: 15 ACCEPTED / 14 PARTIAL / dois BLOCKED / 50 NOT_STARTED.
Requires continuam obrigatórios para aceite; nenhum percentual de compatibilidade.

**Checkpoint anterior 2026-09-19:** [LB-05 updates preservadores](../PRESERVED_MANAGED_UPDATES.md)
entregou PRESERVED_MANAGED_UPDATES como backend developer: antecessor add-only
gerenciado/não carregado, revalidação antiga/nova, backup independente, journal
separado, resume/restore e bloqueio de sucessor conflitante. 50 casos novos
cobertos em execuções seriais; regressão 145 teve três falhas no classpath do
helper, corrigidas e rechecadas com nove casos existentes (12/12). Não se afirma
repetição integral limpa de 145. Assemble/auditorias/CLI passaram, falhas retidas,
hash/delta e limites no [ledger](../evidence/preserved-updates-2026-09-19.json).
Cursor: **LB-01 / PRODUCT-COMPOSED / PRESERVED_UPDATE_COORDINATOR**, para integrar
seleção explícita de antecessor/admissão, revisão/consentimento frescos, worker/
gate/lease existentes, recuperação/recibos tipados e histórico inerte. API não
equivale a update já disponível na tela. UI de updates, cadeias/coortes e aceites
nativos pendentes; sem Minecraft, promoção pública ou alteração de licença.
Não repetir backend/preparação, não afrouxar requires. Contagem inalterada:
15/81 ACCEPTED, 14 PARTIAL, dois BLOCKED e 50 NOT_STARTED; não mede compatibilidade.

**Checkpoint anterior 2026-09-19:** [LB-01 preparação preservadora](../PRESERVED_PREPARATION_WORKFLOW.md)
entregou PRESERVED_PREPARATION_WORKFLOW: seleção de original/dados ou candidato
existente por hashes, motor finito reutilizado, inspeção independente e admissão
privada, worker/progresso/cancelamento, erros e relatórios. A ligação à revisão
não concede consentimento. 196 regressões passaram (23 novas), assemble/auditorias/
CLI passaram; falha inicial de compilação corrigida e retida. Rechecagens finais,
hash e delta no ledger. Sem jogo, perfis novos, promoção pública ou licença.
Cursor: **LB-05 / INSTALL-PROMOTION / PRESERVED_MANAGED_UPDATES** para substituição
gerenciada não carregada com backup e recuperação determinística; nada de
sobrescrever carregados/desconhecidos, migração implícita ou bootstrap.
Não repetir preparação/host/tela/instalador add-only. Requires/aceites continuam.
Contagem: 15/81 ACCEPTED, 14 PARTIAL, dois BLOCKED, 50 NOT_STARTED; 18,5% de
cortes sem ponderar esforço não é percentual de mods compatíveis ou avanço/modelo.

**Checkpoint anterior 2026-09-19:** [LB-04 tela developer preservadora](../PRESERVED_DEVELOPER_SCREEN.md)
entregou PRESERVED_DEVELOPER_SCREEN: opt-in explícito, formulário limitado,
sessão real thread-safe, revisão/consentimento, recuperação/histórico inerte,
en_us/pt_br e fechamento manual seguro. 103 checks focados passaram (18 novos),
assemble/auditorias/CLI passaram; duas falhas iniciais do teste ASM conservador
foram corrigidas e preservadas. Engine/seis perfis/runtime/ABI/instalador/dados
intactos; hash/delta no ledger. UI-PROMOTION PARTIAL; nativo NOT_RUN, Beta.
Cursor: **LB-01 / PRODUCT-COMPOSED / PRESERVED_PREPARATION_WORKFLOW** para ligar
motor/preparação existentes ao fluxo developer: hoje a tela começa em admissão
preparada separadamente. Não repetir tela/host/instalador, abrir jogo ou promover
Apply público. Requires/aceites nativos e política de aquisição permanecem.
Contagem atual: 15/81 cortes aceitos (18,5% sem ponderar esforço), 14 parciais,
dois bloqueados e 50 não iniciados; não mede compatibilidade ou avanço por modelo.

**Checkpoint anterior 2026-09-13:** [LB-01 coordenador preservador](../PRESERVED_INSTALL_COORDINATOR.md)
entregou PRESERVED_INSTALL_COORDINATOR: factory developer opt-in, revisão fresca,
confirmação de sessão consumível, worker/gate/lease compartilhados, resultados do
instalador real e recibos/histórico inertes por schema. 83 checks de produto passaram
(27 novos); assemble/auditorias/CLI/verificador do contrato passaram. Falha inicial
de recibo ausente corrigida; ciclo com seis timeouts retido e repetição integral
passou sem mudar limites. Engine/seis perfis/runtime/ABI/instaladores/UI públicos
mantiveram seus bytes; novo hash e delta no ledger. Nativo NOT_RUN, Beta.
Cursor: **LB-04 / UI-PROMOTION / PRESERVED_DEVELOPER_SCREEN** para ligar o host/tela
developer à API pronta, com captura real de sessão, guardas e consentimento.
Não repetir coordenador/instalador, abrir jogo ou habilitar Apply público. Partes
estáticas permitem desenvolvimento; requires/aceites nativos não foram satisfeitos.

**Checkpoint anterior 2026-09-13:** [LB-05 instalação preservadora](../PRESERVED_ADD_ONLY_INSTALL.md)
entregou PRESERVED_ADD_ONLY_INSTALL por API developer explícita: aprovação/pins,
preflight fresco, sessão/dependências, ownership/journal separado e resume/restore.
112 checks de produto passaram (41 novos); contrato 75 passaram/dois skips;
assemble/auditorias/CLI passaram. JAR público em processos Java isolados comprovou
halt/resume/restauração/idempotência, originais intactos e caches configurados
vazios. Falha inicial de limpeza antecipada de guardas corrigida e documentada.
V1/V2, seis perfis, runtime/ABI e UI pública simples intactos; nativo NOT_RUN.
O cursor de desenvolvimento avança a **LB-01 / PRODUCT-COMPOSED /
PRESERVED_INSTALL_COORDINATOR**: integrar revisão/approval, jobs limitados,
sessão/mundo/fechamento, recibos e histórico por rota developer explícita.
Não repetir instalador/binding ou promover Apply público, composições/coortes.
INSTALL-PROMOTION continua PARTIAL, com requires/gates nativos preservados.

**Checkpoint anterior 2026-09-13:** [LB-05 journal de admissão](../PRESERVED_ADMISSION_JOURNAL.md)
entregou JOURNAL_ADMISSION_BINDING: payload privado, pins/perfil/ABI/schema exatos
e recuperação com nova validação dos bytes/dados. 71 checks passaram, 28 novos;
build/auditorias passaram. Helper do JAR público interrompido após binding e duas
JVMs novas comprovaram retomada/idempotência/limpeza das guardas, sem mods/cache.
Limite de caminhos longos do JDK17 Windows agora recusado antecipadamente;
V1/V2, engine, perfis, runtime, UI e dados anteriores intactos. Nativo NOT_RUN.
Próximo **LB-05 / INSTALL-PROMOTION / PRESERVED_ADD_ONLY_INSTALL**: rota developer
explícita com aprovação/sessão/dependências/commit/ownership e recuperação, não
promoção pública. Não repetir binding nem relaxar requires/QA; LB-05 PARTIAL.

**Checkpoint anterior 2026-09-13:** [LB-05 inspeção independente](../PRESERVED_ARTIFACT_ADMISSION.md)
entregou INDEPENDENT_LOGIC_READBACK_V1 via API/CLI, sem instalar ou executar o mod.
Fixture original/convertido real: seis classes/25 métodos e hashes intactos.
71 checks passaram, 22 novos; assemble/auditorias e paridade do motor do JAR
público/CLI em JVMs separadas com cache configurado vazio passaram. Dados full
continuam locais explícitos, não embutidos; nativo NOT_RUN, LB-05 PARTIAL.
Próximo **LB-05 / INSTALL-PROMOTION / JOURNAL_ADMISSION_BINDING**: vínculo
versionado de perfil/pins/admissão/recuperação, sem confiar em PASS persistido,
alterar V1/V2 simples ou promover Apply genérico. Não repetir a leitura já
implementada. Requires COMPOSE-01/DEPS-COHORT continuam de aceite; nenhum jogo,
publicação, cobrança ou mudança de licença autorizado.

**Checkpoint anterior 2026-09-12:** [LB-04 relatórios salvos](../SAVED_REPORTS.md)
entregou UI-SAVED-REPORTS: índice schema 1, vínculo explícito/hash e consulta
paginada inerte após reabrir o controlador. 100 checks passaram/um symlink SKIP;
fixture legado real manteve bytes convertidos/original, build/auditorias passaram.
Novo hash no ledger/status, perfis/motor/runtime/instalador intactos. Nativo NOT_RUN,
LB-04 PARTIAL. Cursor **LB-05 / INSTALL-PROMOTION** para admissão independente
finita; requires COMPOSE-01/DEPS-COHORT continuam de aceite, não satisfeitos por build.
Usuário adiou explicitamente monetização/licença (inclusive avaliar 100% gratuito)
para continuar desenvolvimento. Não cobrar, mudar LICENSE, publicar ou pedir
essa decisão novamente como bloqueio desta implementação. PACKAGE-RIGHTS segue
gate antes de release; nenhum jogo/tela/servidor autorizado.

**Checkpoint anterior 2026-09-12:** [LB-03 relinking](../SRGUTILS_RELINKING.md)
entregou TECHNICAL_RELINK_V1: prepare/build/replace dev-only, fontes exatas,
receita independente Java17/release8, gate ABI/ASM de 41 classes e cópia privada.
20 testes focados passaram; biblioteca modificada observada, motor do JAR público
equivalente no fixture simples e recusa de corrompido. CLI fora do projeto/cache
reproduziu hashes entre JVMs. JAR público/LICENSE/perfis intactos, sem jogos.
Naquele momento o cursor era **LB-03 / PACKAGE-RIGHTS / OWNER_TERMS_DECISION**;
o usuário posteriormente adiou a escolha. Parte técnica entregue não é
autorização jurídica, reprodução upstream nem aceite Forge JiJ/JPMS nativo.
PACKAGE-RIGHTS BLOCKED / LB-03 PARTIAL; não repetir a infraestrutura concluída.
Outros recortes podem ser repriorizados explicitamente sem apagar esse bloqueio.

**Checkpoint anterior 2026-09-12:** [LB-02 headless](../PROMPT_02_HEADLESS.md)
entregou revisão tipada de dados/hash no CLI/produto e admissão agregada/métricas/
freio cooperativo do motor. Composição mantém rota full local explícita; dados
incluídos/perfis/adapters preservados. 101 casos focados passaram/um symlink SKIP;
seis perfis e duas prévias passaram sem cache/rede em Java isolado. Nativo NOT_RUN.
LB-02 continua PARTIAL: COMPOSE-01/QA nativa e retenção exata/RSS/adversarial
pendentes, sem garantia rígida de heap. Avançar desenvolvimento a **LB-03 /
PACKAGE-RIGHTS, preparação técnica de fonte/relinking**. A decisão de termos
do titular permanece BLOCKED; não mudar licença nem publicar. Não converter
este avanço de implementação em aceite ou repetir a matriz como único trabalho.

**Checkpoint anterior 2026-09-12:** [FINITE_COHORT_DATA](../FINITE_COHORT_DATA.md)
entrega dados incluídos de 537 bytes para a prévia da dupla autoral exata,
com APIs reais/proveniência, escopo incompleto explícito e gate no hash real
antes do scan. CLI/serviço/produto equivalentes; 167 casos focados e probe
Java17 isolado sem bundle/cache/rede passaram. Pacote auditado; nativo NOT_RUN.
Desenvolvimento segue **LB-02 / DATA-EXPAND / COMPOSITION_DATA_SCOPE**: dados
mínimos separados da prévia individual lógica+recipes somente quando permitidos.
DATA-EXPAND agora PARTIAL; recorte da dupla implementado, família/aceite não
concluídos. Sem promoção Convert/Apply ou dispensa de requires/QA/direitos.

**Checkpoint anterior 2026-09-12:** [COHORT_TARGET_PREVIEW](../PRODUCT_COHORT_PREVIEW.md)
entregue no produto: revisão de seleção explícita, planCohort compartilhado,
progresso/cancelamento e relatório por artefato sem JAR/recibo/Apply. 34 checks
focados passaram e pacote auditado; nativo NOT_RUN. Cursor de desenvolvimento
avança a **LB-02 / DATA-EXPAND / FINITE_PREVIEW_DATA**: dados mínimos versionados,
proveniência/direitos e rota permitida, nunca embutir bundle LOCAL_USE_ONLY.
PRODUCT-COMPOSED/DEPS-COHORT/COMPOSE-01 permanecem PARTIAL no aceite/promoção.
Não repetir harnesses ou abrir jogos/tela. Os próximos abaixo são históricos.

**Checkpoint anterior 2026-09-12:** [FINITE_TARGET_PAIR](../DEPENDENCY_COHORT_TARGET.md)
monta/reabre dois candidatos privados, mantém as três classes e a dependência,
valida target/linkage por JAR e registra seis mudanças metadata reais. Serviço e
CLI têm a mesma prova, sem saída pública ou Apply. 136 checks focados passaram;
aceite nativo de DEPS-COHORT continua PARTIAL. Próximo desenvolvimento:
**LB-01 / PRODUCT-COMPOSED / COHORT_TARGET_PREVIEW**, seleção múltipla/prévia
tipada ligada ao motor, sem duplicar regras ou promover instalação. Cursor avança
pela política headless, não por dispensa de QA. Sem Minecraft/servidor/tela.

**Checkpoint anterior 2026-09-12:** LB-19 / DEPS-COHORT iniciou sua implementação
com [SOURCE_COHORT_INSPECTION_V1](../DEPENDENCY_COHORT_SOURCE.md): dois JARs Forge
autorais, ownership/API e linkage por artefato, closure nos dois lados e serviço/CLI.
Não converte a dupla ainda. Próximo desenvolvimento: **FINITE_TARGET_PAIR** dentro
do mesmo corte; receitas exatas, preservação da dependência e validação conjunta
de candidatos privados. Nativo e promoção continuam pendentes; sem jogos/tela.
O ledger distingue checks estáticos de comportamento e não avança o aceite.
O checkpoint seguinte é histórico, não é mais o trabalho a iniciar.

**Checkpoint 2026-09-12:** [PRODUCT-COMPOSED inspeção](../PRODUCT_COMPOSITION_INSPECTION.md)
entregou prévia tipada, tela nativa, relatório e gates fixos sem Convert/Apply.
23 checks focados passaram, inclusive o fixture real como dados estáticos e
paridade com o serviço. O próximo desenvolvimento é **LB-19 / DEPS-COHORT**:
biblioteca+consumidor autorais, ownership e contrato de API/linkage finito.
PRODUCT-COMPOSED e COMPOSE-01 continuam PARTIAL para promoção/aceite nativo;
o cursor avança pela política de implementação já registrada, não por aceites
inventados. Nenhum Minecraft/servidor/tela ou aquisição/publicação autorizados.
Os “próximos” abaixo são históricos e não substituem esse cursor.

**Prioridade revista em 2026-09-10: desenvolver o produto, sem abrir Minecraft.**
O usuário adiou as campanhas extensas/nativas, não a obrigação de registrar que
faltam. Compilação, checagens focadas de segurança e pacote continuam seriais.
LB-04 / `UI-HISTORY-READ` implementa uma lacuna do jogador sobre a API já aceita;
[entrega e limites](../UI_HISTORY.md). Seu aceite nativo fica PARTIAL, mas não
prende novamente o cursor à infraestrutura de testes.

O próximo desenvolvimento é LB-01 / `PRODUCT-COMPOSED`: integrar uma prévia
tipada/revisão de admissão com o plano estático existente. `COMPOSE-01` permanece
PARTIAL e não é promovido. Seu candidato estático é uma base de implementação,
não autorização de conversão pública ou Apply. Novos contratos continuam
bloqueados no produto até validação/equivalência/admissão próprias.
Os checkpoints abaixo são históricos; textos antigos de “próximo COMPOSE” não
substituem essa prioridade. Não ampliar harnesses de QA como próximo automático.

Prioridade autorizada em 2026-09-07: **avançar implementações e testes headless**,
sem aguardar permissão para abrir Minecraft. O cursor mutável fica no JSON;
`JOB-INSTALL-RECEIPTS` corrige persistência de Apply/recovery e
`JOB-HISTORY-READ` implementa a API de leitura limitada sem jogos. Esses recortes têm
aceite estático próprio; não concluem a UI, recuperação nativa ou networking.
[Contrato dos recibos](../JOB_INSTALL_RECEIPTS.md) e [leitor de histórico](../JOB_HISTORY_READ.md).
A sequência headless iniciou `COMPOSE-01`, cujo pré-requisito `LOGIC-BASE` já foi aceito.
A [prévia estática de composição](../COMPOSITION_PREVIEW.md) permanece intacta.
A continuação de [candidato privado](../COMPOSITION_CANDIDATE.md) já serializa,
reabre e valida o ZIP com metadados/bindings próprios e ownership completo.
O serviço público retorna apenas prova/hash e remove o temporário; nenhum perfil
público ou instalação foi liberado. A [exportação exclusiva de desenvolvimento](../COMPOSITION_QA_EXPORT.md)
agora retém JAR/prova/recibo para QA, fora dos artefatos do jogador e sem autoridade
de instalação. Os 96 casos de composição passaram (40 novos), incluindo junção real
do Windows. O recorte continua PARTIAL. Naquele checkpoint, o próximo era harnesses e
observações original/alvo ligadas aos artefatos; comparação nativa e demais
pré-requisitos permanecem. Restos privados após crash não são exports aprovados.
O [verificador das observações](../COMPOSITION_OBSERVATIONS.md) já foi implementado
no módulo dev-only corpus: 77 testes sintéticos passaram, sem skips, e o índice
real confere cinco arquivos com resultado NOT_RUN. A continuação de
[probes de captura](../COMPOSITION_PROBES.md) agora implementou/compilou os dois
probes separados: 123 testes por versão, sem falhas/skips, e cinco testes de
protocolo coletor/checker. Build completo aprovado; corpus fresco 288 aprovados
e quatro skips prévios, demais suítes UP-TO-DATE. JAR público intacto e sem QA.
A continuação de [planejamento e saída registrada](../COMPOSITION_RUN_PLANNING.md)
implementou o planejador somente leitura e o checker pós-saída: planos reais das
duas instalações PLANNED_ONLY, 146 testes novos aprovados sem skips, build completo
aprovado e corpus fresco 434 aprovados/quatro skips prévios. Demais suítes
UP-TO-DATE; JAR público intacto. [Ledger](../evidence/compose-planning-2026-09-07.json).
Essas ferramentas não criam staging, executam ou observam processo; registros
correspondentes de saída são só RECORDED_EXIT_ONLY, sem prova de EOF/exit real.
A continuação de 2026-09-08 implementou [staging desarmado e coletor interno](../COMPOSITION_STAGING_COLLECTION.md):
create-new/journal/cópias independentes com marker retido; JVMs auxiliares inertes
para EOF/exit/limites/cancelamento/hashes. O coletor não verifica proveniência do
digest do plano nem autoriza um launcher nativo. [Ledger](../evidence/compose-staging-2026-09-08.json).
Essa integração agora existe como [sessão fechada estritamente inerte](../COMPOSITION_INERT_SESSIONS.md):
verificador read-only recompõe seleção/receipt/journal, sessão usa somente helper
de testes conhecido e publicação create-new tem inspeção de recuperação sem replay.
O helper não utiliza argv nativo e não é empacotado. A correção do `hsperfdata` do
HotSpot não amplia a árvore permitida; limites do coletor permanecem intactos.
Build/pacote aprovados: corpus 693 casos/686 aprovados/sete skips, zero falhas.
Origem/destino revalidados somente leitura, 111/223 pins; JAR público byte-idêntico.
[Ledger final desta continuação](../evidence/compose-sessions-2026-09-08.json).
A [política finita de layout](../COMPOSITION_NATIVE_OUTPUT_POLICY.md) agora implementa
pins imutáveis, propriedades explícitas preservadas e limites por caminho/arquivo.
Config/NBT/logs/observações continuam com conteúdo não validado; apenas layout.
A [auditoria dos launchers exatos](../COMPOSITION_NATIVE_LOOKUP_AUDIT.md) distingue
duas buscas condicionais na origem da rota modular inspecionada do destino.
Build/pacote aprovados: corpus 934 casos/927 aprovados/sete skips existentes,
zero falhas; 241 novos aprovados sem skips. SOURCE/TARGET revisados somente leitura,
111/223 pins e sem mutação; JAR público byte-idêntico. [Ledger](../evidence/compose-output-policy-2026-09-08.json).
Naquele checkpoint, próximo headless: semântica finita de configuração/conteúdo
e conjunto obrigatório de saídas, sem arming/executor nativo. A comparação nativa,
incluindo origem Java 17 (o teste antigo usava Java 8), continua NOT_RUN.
Manter PARTIAL, não executar jogos, aceitar COMPOSE-01 ou liberar LINKAGE-GENERAL.
Helpers observados provam apenas a execução inerte controlada. Nenhum checker
confere autoridade nativa ou concede aceite/instalação.

Checkpoint anterior: [configurações nativas finitas e presença necessária](../COMPOSITION_NATIVE_CONFIG_REVIEW.md).
Defaults versionados são revisados sem correção/execução; TARGET common não
registrado é recusado. Presença necessária (14/13 caminhos com properties de
baseline) não valida save/exit. Version check, threads automáticas e anúncio LAN
continuam pendências de lançamento; o próximo headless é esse contrato finito
mais conteúdo/save, sem arming. Resultados de build/test ficam no
[ledger atual](../evidence/compose-config-review-2026-09-08.json), sem aceite de
COMPOSE-01, LINKAGE-GENERAL, instalação ou UI.

Continuação atual: [proposta restrita de configuração](../COMPOSITION_RESTRICTED_SETTINGS.md),
ligada ao staging revalidado sem materialização, arming ou execução. Valores
explícitos anteriores não podem ser sobrepostos; vocabulário ativo 50/56,
template server em defaultconfigs, receitas e hashes imutáveis. Build completo
serial aprovado em 33m17s: corpus 1.620 casos/1.613 aprovados/sete skips existentes,
zero falhas/erros; 456 novos aprovados sem skips. Demais suítes UP-TO-DATE, não
repetidas. 682 casos focados passaram antes dos quatro adicionais de baseline.
Comando SOURCE/TARGET real read-only: 111/223 pins, 10/11 properties preservadas;
sem mundo/config/template/marker. Público byte-idêntico e índice com cinco pins
intactos/`runs: null`. [Ledger](../evidence/compose-restricted-settings-2026-09-08.json).
Não afirmar isolamento de rede por configuração: authlib faz lookup de chaves
fora do checker Forge. Próximo headless no mesmo COMPOSE-01: materialização/
recuperação ligada e conteúdo pós-save, depois lookup/containment. Native NOT_RUN,
PARTIAL e demais gates permanecem; nenhum jogo, instalação ou recurso novo.

Cursor nativo adiado, não concluído: **LB-06 / PUBLIC-CURRENT-E2E**. A continuação nativa de
2026-09-07 aceitou somente **LB-04 / DATA-SIMPLE-01-NATIVE_UI**, no mesmo JAR.
[Execução real e limites](../EMBEDDED_SIMPLE_NATIVE_UI_VALIDATION.md) e
[ledger](../evidence/embedded-simple-native-ui-2026-09-07.json) registram a transição.
Não houve regra, adapter, perfil ou compatibilidade nova.

O JAR público da evidência nativa histórica tem SHA-256
`3749da06606f36b7e9bcbb97e4d9558f91d0bfa1c60e5da533767fd722607df6`.
O simple-content já leva dados finitos embutidos; não pede bundle separado.
Os outros cinco perfis ainda são opt-ins de desenvolvimento com dados explícitos.
Build/paridade CLI-serviço/cache vazio e servidor dedicado foram registrados.
A nova execução comprovou UI com campo vazio, continuar sem ativação, Apply/close,
nova descoberta, crafting, assets e traduções pt_br/en_us, com original intacto.
O bloqueio de foco anterior permanece apenas no histórico. LB-04 continua PARTIAL:
promoção/tela nativa de histórico e reconciliação não prontas; LB-06 ainda exige recuperação no
cliente público durante commit e consolidação dos demais limites de qualificação.

Continuação de LB-06: [negativos finitos no mesmo hash](../PUBLIC_CURRENT_NEGATIVE_VALIDATION.md)
registram recusas de corrompido/duplicados de entrada/dependência/worldgen, escrita
real e restauração, cancelamento aceito e fechamento ativo sem parcial. Quatro
testes headless de recuperação/locks passaram separadamente. Interrupção durante
commit no cliente público e qualificação ampla continuam pendentes.
**Restrição atual do usuário: somente headless; não abrir jogos nem automatizar
a tela sem nova autorização explícita.** Os clientes anteriores já haviam saído.

A [consolidação headless](../PUBLIC_CURRENT_QUALIFICATION.md) agora inclui índice
de hashes/asserções e verificador dev-only: 192 arquivos e 61 asserções registradas,
sem executar jogos nem aprovar qualificação. O oracle externo de commit interrompido
continua sem implementação validada; não substituir essa pendência por polling/kill.

A [continuação da recuperação](../PUBLIC_NEXT_SESSION_RECOVERY_VALIDATION.md)
adiciona sete testes de processo com snapshots sintéticos da próxima sessão,
incluindo publicações já declaradas carregadas. Isso protege recusas/retomada do
instalador sem mudar o produto, mas não substitui descoberta ou interrupção nativa.

O próximo erro conhecido é **LB-12 / NET-QA-02-HANDSHAKE / CLIENT_PROTOCOL**:
falta oracle nativo normal. CLIENT_ABSENT passou posteriormente em DEFAULT;
SERVER_ABSENT/SERVER_PROTOCOL, comparação original/alvo e NET-QA-03 continuam
pendentes. Observações instrumentadas não resolvem o erro normal.

A limitação estrutural central: seis perfis fechados **não se combinam**.
Além disso, instalador/journal públicos só admitem simple-content/bootstrap;
a nova API developer preservadora não é promoção pública. Generalizar
lógica e compor operações (LB-08), fechar dependências reais (LB-19) e criar
admissão independente por perfil (LB-05) são pré-requisitos de ampla cobertura.
Somar tabelas de renomes ou liberar um dropdown não resolve esses problemas.

## Índice canônico

Tabela de abertura da revisão 2026-09-07; os estados mantidos no dia a dia são
os do JSON, não uma declaração permanente desta tabela.

| Prompt | Área | Estado inicial |
| --- | --- | --- |
| [00](prompts/00-governance.md) | Contrato, baselines e rastreabilidade | ACCEPTED |
| [01](prompts/01-product-architecture.md) | Arquitetura do produto e limites de ativação | PARTIAL |
| [02](prompts/02-engine-data.md) | Motor reutilizável, mappings e dados distribuíveis | PARTIAL |
| [03](prompts/03-packaging-rights.md) | JAR único, classloaders e direitos de distribuição | PARTIAL |
| [04](prompts/04-native-ui.md) | Interface nativa e promoção segura de perfis | PARTIAL |
| [05](prompts/05-transactional-install.md) | Instalação, atualização, recuperação e encerramento | PARTIAL |
| [06](prompts/06-public-e2e.md) | Validação integral do artefato público | PARTIAL |
| [07](prompts/07-corpus-priorities.md) | Corpus autorizado e priorização por mods inteiros | PARTIAL |
| [08](prompts/08-preserved-logic-composition.md) | Lógica preservada, linkage e composição de contratos | PARTIAL |
| [09](prompts/09-lifecycle-config-commands.md) | Registros, eventos, lifecycle, configurações e comandos | PARTIAL |
| [10](prompts/10-items-blocks.md) | Itens e blocos com comportamento | PARTIAL |
| [11](prompts/11-resources-datapacks.md) | Recursos e datapacks por formato e versão | PARTIAL |
| [12](prompts/12-networking.md) | Networking e contratos de multiplayer | BLOCKED |
| [13](prompts/13-capabilities-persistence.md) | Capabilities e persistência por instância | NOT_STARTED |
| [14](prompts/14-blockentities-machines.md) | BlockEntities, máquinas e automação | NOT_STARTED |
| [15](prompts/15-entities-ai.md) | Entidades, atributos, combate, spawn e AI | NOT_STARTED |
| [16](prompts/16-menus-input-screens.md) | Menus, inventários, telas, input e HUD legados | NOT_STARTED |
| [17](prompts/17-rendering-audio.md) | Renderização, modelos dinâmicos, partículas e áudio | NOT_STARTED |
| [18](prompts/18-worldgen-dimensions.md) | Mundo, geração, biomas, estruturas e dimensões | NOT_STARTED |
| [19](prompts/19-dependency-cohorts.md) | Dependências, bibliotecas e composição entre mods | PARTIAL |
| [20](prompts/20-advanced-hooks.md) | Reflexão, access transformers, mixins e coremods | NOT_STARTED |
| [21](prompts/21-quality-release-gates.md) | Qualificação cumulativa, segurança e prontidão de distribuição | NOT_STARTED |
| [22](prompts/22-authorized-real-mod-campaign.md) | Campanha final autorizada com mods reais e fechamento | NOT_STARTED |

Cada arquivo é um prompt copiável com ponto de partida, trabalho, recortes,
aceite, parada, evidência e exclusões. O contrato inicial contém **77 recortes**;
essa quantidade não mede compatibilidade nem tempo restante.

## Sequência e dependências

IDs são endereços estáveis, não uma obrigação de executar 0, 1, 2… até 22 sem
voltar. `requires` define pré-requisitos por corte; não exige terminar toda uma
família anterior. `resumeOrder` fixa a prioridade imediata, não é um scheduler.

1. LB-04: gate nativo finito dos dados embutidos aceito; LB-06: consolidar E2E do mesmo hash e qualificar negativos restantes, sem repetir o positivo inalterado.
2. LB-12: resolver handshake normal, negativos restantes, comparação origem/destino e filas/sessões.
3. LB-08: COMPOSE-01; LB-19: DEPS-COHORT. São fundações novas explícitas antes de ampliar APIs, sem apagar os perfis de regressão.
4. LB-13: energia → inventário → fluido; lifecycle/eventos LB-09 habilitam jogador/SavedData. As capabilities já solicitadas não são consideradas prontas.
5. Expandir lógica/linkage, eventos/config/comandos, itens/blocos e formatos/recursos conforme dependências; cada subcorte requer fixture e comparação.
6. Hosts persistentes/máquinas, entidades/AI, menus/anti-dupe, render/client e mundo/dimensões em lotes próprios. Bibliotecas/composição cruzam essas famílias.
7. Auditar hooks avançados para decidir rotas públicas versus MANUAL/UNSUPPORTED; não autorizar patches de plataforma por conveniência.
8. Promoção transversal de cada contrato: dados + JAR + instalação + UI + E2E. Depois qualificação cumulativa LB-21 e revisão de direitos.
9. Só então LB-22: nova aquisição autorizada de mods famosos/sem port localizado, campanha e fechamento por release/coorte.

Se o cursor exigir ação do usuário, não fazer retries vazios. Registrar o
impedimento, preservar a evidência e pedir a ação precisa. Investigação estática
inócua relacionada é permitida, mas mudar a prioridade ou o escopo exige registro;
não iniciar features para fingir que o gate pendente passou.

A autorização de foco no produto permite avançar implementação sobre partes
estáticas já implementadas de pré-requisitos PARTIAL, com limite registrado e
sem habilitar suas operações não qualificadas. `requires` continua sendo condição
de aceite/promoção, não é removido ou satisfeito por compilação. O cursor indica
próximo trabalho de desenvolvimento; PARTIAL pode permanecer na fila de QA sem
impedir outro recorte delimitado. Não voltar a pedir
foco/tela a cada retomada nem lançar servidor/jogo sem nova autorização. O contrato
inicial tinha 77 recortes; os dois recortes finitos de recibos/histórico adicionados
e a UI de histórico/relatórios salvos totalizam agora 81; não aumentam o número de mods compatíveis
nem alteram os 23 prompts.

## Estados e o que constitui aceite

| Estado | Significado |
| --- | --- |
| NOT_STARTED | Existe especificação/auditoria, mas o recorte não foi implementado/aceito. |
| PARTIAL | Há trabalho/evidência delimitados; falta parte obrigatória do aceite. |
| BLOCKED | Há erro ou dependência externa concreta; próximo passo e impedimento devem ser explícitos. |
| ACCEPTED | Somente o escopo descrito foi implementado e recebeu as evidências exigidas. Não herda para outro hash/coorte. |
| MANUAL | Exige port/intervenção humana ou decisão além do conversor automático. Não é suporte automático. |
| UNSUPPORTED | Sem rota implementada/permitida no escopo. Não é sucesso de conversão. |
| DEFERRED | Adiado explicitamente com motivo; não concluído. |

Pais não recebem ACCEPTED com cortes em aberto. Um corte de auditoria pode ser
aceito por demonstrar uma impossibilidade; seu entregável deve dizer isso, sem
promover a funcionalidade. Dispor MANUAL/UNSUPPORTED não elimina `requires` nem
libera o intake final. Se um objetivo não puder ser entregue sob as invariantes,
peça revisão de escopo ao usuário, registre exclusão/efeito/justificativa e versão
antes de mudar gates. Não existe “dar tudo por feito” mediante renomear estados.

Nas evidências, sempre separar: **analisável → convertível → bytecode validado →
linkage/acesso validado → inicializa cliente/servidor → comportamento validado →
multiplayer validado**. A sequência é de obrigações, não inferência automática.
Dimensão não testada é NOT_RUN/PENDING; não aplicável requer justificativa.

O corpus congelado tem três releases reais e **zero ports completos validados**.
Não usar número de testes, símbolos resolvidos, receitas de transformação ou
primeiros erros removidos como porcentagem de mods compatíveis. Uma estimativa
de mods desbloqueados deve considerar **todos** os bloqueios da versão exata.

## Protocolo de manutenção em toda retomada

1. Leia este contrato e JSON; anuncie `LB-xx / corte` antes de implementar.
2. Valide o estado contra código, hash e ledger mais recente. Preserve evidência histórica, mas não a transfira para bytes novos.
3. Prefira pré-requisitos aceitos. Na prioridade produto/headless, partes estáticas implementadas de um PARTIAL podem sustentar desenvolvimento bloqueado por admissão, com motivo explícito; nunca antecipar aceite. Novo corte recebe ID único e dependências explícitas. Após implementação delimitada, avance o cursor de desenvolvimento e mantenha QA pendente visível.
4. Escreva oracle e fixture/negativo antes de aceitar regra. Implemente, rode build/test serial e QA separada proporcional ao comportamento.
5. Em ledger versionado, registre entrada/saída/produto/dados/regras e hashes, versões/ambiente, comandos sem segredos, esperado/observado, PASS/FAIL/SKIP/NOT_RUN por dimensão e limites. Falha precisa de causa observada versus hipótese e próxima ação.
6. Atualize apenas os status demonstrados, evidências e `reviewedAt`; ao encerrar corte remova-o de `resumeOrder` e avance `current` explicitamente. O histórico anterior permanece disponível no ledger; registre a transição e seu motivo.
7. Sincronize os resumos de PROJECT_STATUS/PROMPT_CONTINUATION, rode o verificador e build após edições. Alteração de baseline/schema/contrato é revisão deliberada também no verificador; não modificar só para silenciar teste.
8. Entregue resumo com prompt/corte, implementado, testes/skips, evidência/hash, limitações e próximo ID. Nenhum gate avança porque o usuário repetiu a mensagem dez vezes.

Ao encerrar todos os prompts/cortes com ACCEPTED, o estado terminal é
`current: null` e `resumeOrder: []`. Antes disso, ambos continuam identificando
trabalho em aberto; não apagar o cursor para esconder uma pendência. Cortes
aceitos também exigem seus pré-requisitos aceitos. MANUAL/UNSUPPORTED só mudam
o escopo final mediante a revisão explícita descrita acima.

Status/evidência são revisão humana assistida. O verificador abaixo testa
consistência estrutural e caminhos seguros; **não prova que o conteúdo de um
ledger é verdadeiro/atual**, não inicia Minecraft e não altera o cursor.

## Comandos e prompt de retomada

Na raiz `legacy-bridge`, com Java 17 configurado e sem outro build concorrente:

```powershell
.\gradlew.bat :legacy-corpus:verifyPromptContract --no-daemon --max-workers=1
.\gradlew.bat build --no-daemon --max-workers=1
```

Execute os comandos serialmente. O build inclui validação deste contrato e seus
testes positivos/negativos no módulo dev-only, não no JAR do jogador.

Neste Windows, o build sem configuração adicional encontrou falha de certificado
HTTPS do Maven Forge. A repetição validada usou o repositório de raízes confiáveis
do Windows, como os scripts existentes de QA, sem desabilitar TLS ou editar Java
globalmente:

```powershell
.\gradlew.bat '-Djavax.net.ssl.trustStore=NONE' '-Djavax.net.ssl.trustStoreType=Windows-ROOT' build --no-daemon --max-workers=1
```

`NONE` com `Windows-ROOT` seleciona o provider do Windows, não confiança irrestrita.
Não aplicar essa opção em outro sistema nem usar `check.certs=false` como atalho.

Para continuar, use:

> Trabalhe no LegacyBridge existente. Leia docs/contract/README.md e status.json.
> Diga o prompt/corte ativo e valide a evidência antes de editar. Execute o próximo
> recorte permitido pelo cursor e suas dependências, preserve os perfis existentes,
> rode build/test serialmente e atualize contrato/ledger com realizado, falhas e
> próximo ID. Não pule gates nem adquira mods novos antes de RELEASE-QUAL.

## Esforço e alcance

Não há estimativa defensável de “quase universal” com esta amostra. Há grandes
famílias sem fixture/API/rota viável; dez retomadas não garantem dez entregas
completas. As estimativas existentes do [backlog](../../corpus/BACKLOG.md) são
por recorte e em dias-pessoa de engenheiro experiente, com condições. B01 3–5,
B02 8–15 e B03 30–50 não se somam num prazo de conclusão de todas as famílias.

Para novos cortes: auditar APIs/fixture → estimar implementação/negativos/QA
nativa/composição/promoção → registrar intervalo e confiança → revisar após
primeiro resultado. Separar tempo de engenharia de espera por direitos, foco,
RAM/ambiente e decisões do titular. Não publicar percentual global inventado.

## Fontes e limites da auditoria técnica

Base exata local: [API_BASELINES](../API_BASELINES.md),
[COMPAT_API_BASELINES](../COMPAT_API_BASELINES.md),
[NETWORK_API_BASELINES](../NETWORK_API_BASELINES.md),
[RESOURCE_API_BASELINES](../RESOURCE_API_BASELINES.md). Cada prompt aponta às
fontes/ledgers locais pertinentes; uma documentação de família não substitui
inspeção da versão binária fixada.

Como orientação primária consultada em 2026-09-07:

- Registro e lifecycle de capabilities mudam entre as versões; persistência não implica sync cliente. [Forge 1.16 capabilities](https://docs.minecraftforge.net/en/1.16.x/datastorage/capabilities/), [Forge 1.20.1 capabilities](https://docs.minecraftforge.net/en/1.20.1/datastorage/capabilities/).
- Handlers precisam preservar contexto/thread, protocolo e validação contra acesso indevido a chunks. [Forge 1.16 SimpleImpl](https://docs.minecraftforge.net/en/1.16.x/networking/simpleimpl/), [Forge 1.20.1 SimpleImpl](https://docs.minecraftforge.net/en/1.20.1/networking/simpleimpl/).
- Lado físico não é lado lógico; menu sincronizado não é a tela visual. [Forge sides](https://docs.minecraftforge.net/en/1.20.1/concepts/sides/), [Forge menus](https://docs.minecraftforge.net/en/1.20.1/gui/menus/).

Essas referências sustentam obrigações de auditoria/teste, não autorizam uma
equivalência automática de qualquer API nem a redistribuição dos seus dados.
