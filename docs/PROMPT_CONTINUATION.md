# LegacyBridge: trilho de continuidade dos prompts

## Prioridade autorizada em 2026-10-22

Publicar o código gratuitamente no GitHub com GPL-3.0-only e a exceção restrita
Minecraft/Forge aprovada. Sem os 5% anteriormente discutidos. O proprietário
publicará no CurseForge; preparar somente o artefato experimental e os textos.
Não abrir Minecraft. Desenvolvimento de recursos fica adiado nesta entrega,
sem concluir artificialmente o contrato. Ver [registro de publicação](PUBLICATION_2026-10-22.md).

## Histórico do último recorte de desenvolvimento

Contrato canônico atual: [LB-00 a LB-22](contract/README.md), com
[status e dependências por recorte](contract/status.json). A numeração foi
consolidada em 2026-09-07; não é recuperação literal de uma lista histórica
completa. Prioridade autorizada agora: produto/headless, sem abrir Minecraft.
Última entrega: **LB-10 / ITEM-EQUIPMENT / IRON_HELMET_DISPENSER_CLOSED_RECEIVERS**.
[Operação implementada](IRON_HELMET_DISPENSER.md): consulta/predicados nativos,
primeiro ServerPlayer/Zombie elegível, split1, efeito HEAD player ou escrita
Zombie silenciosa, drop chance2/persistência. Desconhecido é recusado antes de
consumo, não vira false/fallback nem outro alvo. 68 descriptors anteriores e
item/efeito HEAD intactos; novo fixture original. Sem registro, receita/candidato.

[Evidência atual](evidence/iron-helmet-dispenser-2026-10-05.json) separa bytes e
endpoints sintéticos de query espacial, cópia e comportamento nativo. Ambas as
inspeções CLI continuam UNSUPPORTED; a gramática anterior não absorveu o helper
novo. Nenhum Minecraft/servidor/tela aberto; gates nativos NOT_RUN.

Próximo: **ITEM-EQUIPMENT / IRON_HELMET_INTEGRATED_SUCCESSOR**.
[Plano finito](IRON_HELMET_INTEGRATED_ITEM_PLAN.md): item/factory com identidade
nova, ownership, use e callback dispenser compostos. Não repetir os helpers
isolados nem ampliar seus guards silenciosamente. Outros receivers vanilla,
fallback, Equipable/rendering/coortes e nativo permanecem gates de candidato.

O [reparo por uso anterior](OWNED_STACK_REPAIR_USE_CONTRACT.md) permanece privado.
Criativo, predição, cancelamento, thread/sync e multiplayer continuam gates
nativos. Espada e capacete ficam UNSUPPORTED. Seis perfis, simple-content-v1/Apply,
ABI, licença e política de aquisição intactos. Não abrir jogos/tela/servidor.
ITEM-EQUIPMENT e requires permanecem PARTIAL.

Entregas anteriores: [interação booleana](BOOLEAN_TOGGLE_BLOCK_CONTRACT.md) e
[STATIC_BOTTOM_HALF_SHAPE/STATIC_SHAPE_SELF_DROP](STATIC_SHAPE_BLOCK_CONTRACT.md)
continuam contratos privados, separados e não qualificados nativamente.

Entrega anterior: [HORIZONTAL_FACING e HORIZONTAL_ROTATE_MIRROR](HORIZONTAL_BLOCK_CONTRACT.md),
preservadas com seu ledger histórico.

Entrega anterior LB-10: [Item durável/predicado de ferro](DURABLE_ITEM_CONTRACT.md);
dano/quebra/consumo, ferramentas/armaduras/atributos gerais e QA nativa continuam pendentes.
Entrega anterior: LB-09 entregou o [fixture integrado de lifecycle](LIFECYCLE_SUITE.md):
Loading/Reloading COMMON com filtro spec/type e thread nativa, registro tardio,
evento próprio com prioridade/cancelamento e comandos integer/boolean com
permissão e árvore nova por dispatcher. Sete classes/34 métodos preservados.
CLIENT startup e SERVER por mundo continuam candidatos separados e inalterados.
O marco delimitado de implementação está entregue; LB-09 continua PARTIAL para
aceite nativo, requires e padrões mais amplos. Não afirmar cobertura geral.
Naquele checkpoint, o próximo era **LB-10 / ITEM-EQUIPMENT / DURABILITY_REPAIR**; o cursor atual está acima.
Não repetir microcortes de LB-09 nem abrir jogos; comparar APIs reais de dano,
stack e reparação antes de habilitar qualquer regra. Ferramentas, armaduras e
atributos não entram implicitamente. Seis gramáticas/Apply intactos.
Provas e limitações no [ledger](evidence/lifecycle-suite-2026-09-19.json).
Monetização/licença foram adiadas expressamente
pelo usuário; considerar gratuidade depois não altera LICENSE agora. O prompt 2
continua PARTIAL: retenção/RSS/adversarial e aceites nativos continuam pendentes.
Direitos no prompt 3 seguem BLOCKED para decisão do titular; a entrega
técnica não autoriza mudar LICENSE ou publicar. LB-19 entregou a dupla target privada;
nenhuma destas prévias habilita JAR público da dupla ou Apply.
COMPOSE-01 permanece PARTIAL, não exige nova expansão de harnesses nesta retomada;
LB-06 / PUBLIC-CURRENT-E2E e networking LB-12 permanecem pendentes, não aceitos.
Os registros abaixo preservam o histórico; os vários textos de "próxima retomada"
antigos não substituem o cursor do contrato. O aceite nativo abaixo, não o simples
planejamento, avançou DATA-SIMPLE-01-NATIVE_UI.

Atualizado em 2026-10-01. Projeto: `legacy-bridge`, independentemente da aba aberta
no editor. Este índice organiza o escopo já solicitado; não habilita funcionalidades
nem substitui os relatórios de execução. Estado atual detalhado em
[PROJECT_STATUS](PROJECT_STATUS.md) e nos ledgers vinculados abaixo.

Entrega anterior: [LB-04 tela de updates](PRESERVED_UPDATE_SCREEN.md): mesmo host
developer, seleção explícita ADD/UPDATE/antecessor/admissão, comparação observada
de versões/hashes, consentimento separado vinculado à revisão, recuperação e
histórico inerte. en_us/pt_br e navegação nativa implementados; aceites visuais
não inferidos. 115 regressões e 39 rechecagens finais passaram, conjuntos
sobrepostos, 23 casos novos, zero falhas/erros/skips; assemble/auditorias/CLI PASS.
Hash/delta no [ledger](evidence/preserved-update-screen-2026-09-19.json).
Engine/seis perfis/ABI/coordenador/instaladores/público/dados preservados. Nenhum
jogo, promoção, aquisição ou mudança de licença; nativo NOT_RUN, Beta.
Próximo **LB-08 / LINKAGE-GENERAL / OWNED_CONSTRUCTOR_DELEGATION**: prova privada
finita de cadeia de construtores próprios e fixture com layout alternativo,
sem apagar o guard de construtor único nem alterar as seis gramáticas existentes.
Parte estática de COMPOSE-01 sustenta implementação, não seu aceite/requires.
Não repetir tela/backend/harnesses ou promover Apply. UI-PROMOTION fica PARTIAL.

Entrega anterior: [LB-01 coordenador de updates](PRESERVED_UPDATE_COORDINATOR.md):
seleção antiga/nova por pins, preview sem alterar mods, confirmação própria de
uso único, worker/gate/lease existentes, update/resume/restore reais e recibos/
histórico inertes separados. 94 regressões focadas passaram, 37 novas, sem
falhas/erros/skips; assemble/auditorias/CLI passaram. Hash/delta e limites no
[ledger](evidence/preserved-update-coordinator-2026-09-19.json). Nenhum jogo aberto,
promoção pública, mudança de licença ou aquisição; nativo NOT_RUN, Beta.
Próximo **LB-04 / UI-PROMOTION / PRESERVED_UPDATE_SCREEN**: ligar formulário
developer explícito à API pronta, comparação de versões/hashes, confirmação,
recuperação e histórico inerte; reutilizar host/sessão/guardas/worker existentes.
Não repetir instalador/coordenador/preparação, habilitar Apply público, abrir
Minecraft ou afrouxar requires. Interface nativa de updates ainda não entregue.

Entrega anterior: [LB-05 atualização preservadora](PRESERVED_MANAGED_UPDATES.md):
API developer para antecessor add-only gerenciado/não carregado, revalidação
independente old/new, backup independente, journal separado, resume/restore e
bloqueio de recuperação conflitante do antecessor. 50 casos novos cobertos entre
execuções seriais: a regressão de 145 teve 142 passes/três falhas do classpath do
helper; após correção, os três e nove casos de processos existentes passaram
(12/12). Não é repetição integral limpa de 145. Falhas e correções retidas.
Assemble/auditorias/CLI passaram; hash/delta e limites no
[ledger](evidence/preserved-updates-2026-09-19.json). Sem Minecraft ou promoção
pública; engine/seis perfis/UI/coordenador/ABI/dados/licença preservados.
Próximo **LB-01 / PRODUCT-COMPOSED / PRESERVED_UPDATE_COORDINATOR**: seleção
explícita do antecessor/admissão por pins, revisão/consentimento frescos, worker/
gate/lease compartilhados, resultados/recuperação/recibos e histórico inerte.
Não repetir backend, preparar autoridade por histórico ou habilitar Apply público.
UI de updates, updates em cadeia/composição/coortes e aceites nativos pendentes.

Entrega anterior: [LB-01 preparação preservadora](PRESERVED_PREPARATION_WORKFLOW.md):
análise/conversão finita existente, leitura independente, admissão privada,
seleção por hashes, worker/progresso/cancelamento, erros e até cinco relatórios.
Importar candidato pronto é alternativa explícita, sem confiar em PASS salvo.
Uso da admissão só preenche a revisão; instalar exige revisão fresca e aprovação
separada. 196 regressões passaram, 23 novas, zero falhas/errors/skips; assemble,
auditorias e CLI passaram. Rechecagens finais e hash/delta no
[ledger](evidence/preserved-preparation-2026-09-19.json). Falha inicial de compilação
do helper de relatório corrigida e retida. Sem jogo, regras novas ou promoção
pública; simple-content, seis perfis, ABI/dados e licença preservados.
Próximo **LB-05 / INSTALL-PROMOTION / PRESERVED_MANAGED_UPDATES**: atualização
preservadora gerenciada não carregada com backup recuperável e interrupção
determinística. Não repetir preparação/coordenador/tela nem afrouxar requires.

Entrega anterior: [LB-04 tela developer preservadora](PRESERVED_DEVELOPER_SCREEN.md):
seleção limitada de admissão/dados/recuperação, sessão real thread-safe, revisão
fresca, confirmação consumível, resultado/histórico inerte, en_us/pt_br e guardas
de navegação/fechamento. 103 checks focados passaram, 18 novos, sem skips/falhas;
assemble/auditorias/CLI passaram. Duas falhas iniciais do teste ASM excessivamente
abrangente foram corrigidas e preservadas no ledger. JAR `b1d8e46e6270bcb8e0034c80bdf00420f4469e163c8c58b5792b163a13af878e`;
625 entradas anteriores intactas/11 alteradas/11 novas. Nenhum jogo foi aberto,
não há promoção pública ou aceite nativo. A tela parte de admissão preparada;
próximo **PRESERVED_PREPARATION_WORKFLOW** integra operações já existentes para
prepará-la no produto developer. Não repetir instalador/coordenador/tela; preservar
Convert/Apply simples e todos os requires. [Ledger](evidence/preserved-developer-screen-2026-09-19.json).

Entrega anterior: [LB-05 instalação preservadora add-only](PRESERVED_ADD_ONLY_INSTALL.md):
API developer com aprovação explícita, preflight/admissão frescos, dependências/
arquivos carregados, cópia privada, journal separado e resume/restore com intenção
durável antes de remoção. 112 checks passaram, 41 novos; contrato 75 passaram/dois
skips. Assemble/auditorias/CLI passaram. JAR público em JVMs separadas: halt 81 após
publicação/resume idempotente; halt 82 após remoção/recusa de RESUME sem mutação/
RESTORE idempotente. Falha inicial de limpeza antecipada de guardas corrigida e
retida como evidência. Originais intactos, caches configurados vazios, nenhum jogo.
JAR `8dbd54f4e6139a223e1e8d6665e2818baf3c0877189b0ec9ea607034857f3a35`;
611 entradas anteriores intactas, duas de admissão recompostas/nove novas.
[Ledger](evidence/preserved-install-2026-09-13.json). V1/V2, UI, seis perfis e ABI
inalterados. Nativo NOT_RUN; Apply público permanece simples, LB-05 PARTIAL.
Próximo **PRESERVED_INSTALL_COORDINATOR** no LB-01: integração limitada de revisão/
approval, jobs, sessão/fechamento, recibos/histórico por rota developer explícita.
Não repetir a camada de disco nem promover composições/coortes ou abrir Minecraft.

Entrega anterior: [LB-05 vínculo persistente e recuperação](PRESERVED_ADMISSION_JOURNAL.md):
cópia privada independente, journal separado com hashes/perfil/ABI/dados revistos,
inspect/resume com leitura e validação frescas. Sem confiar em PASS salvo, alterar
V1/V2, instalar ou autorizar fechamento. 71 checks passaram, 28 novos, e build/
auditorias passaram. Helper Java17 do JAR público interrompido após binding saiu
81; outras duas JVMs retomaram, limparam 14 guardas e não alteraram mods/originais.
Segunda retomada idempotente, cache configurado vazio. Caminhos longos Windows
agora recusados antes de escrita após diagnóstico real de FileStore no JDK17.
JAR `4e41fd7e69eac085e9932690d7eee8e7aa68335b754ac1fbaa94979610abd545`,
605 entradas anteriores intactas/oito novas. [Ledger](evidence/journal-admission-2026-09-13.json).
Próximo **PRESERVED_ADD_ONLY_INSTALL**, rota developer explícita, dependências/
sessão/ownership/commit e recuperação. Não repetir binding, promover UI/Apply
genérico nem abrir jogos. LB-05 PARTIAL; aceites nativos continuam pendentes.

Entrega anterior: [LB-05 inspeção independente](PRESERVED_ARTIFACT_ADMISSION.md):
API tipada/CLI para conferir o original e o convertido existentes, sem chamar
o publicador, executar classes do mod ou confiar em relatórios. Recompila as
regras exatas e verifica corpos projetados, bytecode/linkage, recursos,
dependências, ABI e hashes. Fixture real: seis classes/25 métodos, bytes intactos.
71 checks passaram, 22 novos; assemble/auditorias/CLI passaram. Motor no JAR
público e CLI em JVMs separadas/cache configurado vazio geraram JSON idêntico,
com bundle local explícito não redistribuível. Nativo NOT_RUN. Novo JAR:
`903a8803f1bc2fa379648beab10c8635b527bde25f0e567e41a20b7db5cfa7be`.
[Ledger](evidence/preserved-admission-2026-09-13.json). Seis perfis, runtime,
UI, instalador/journal, dados e bibliotecas intactos. O instalador ainda recusa
o preservador. Próximo **JOURNAL_ADMISSION_BINDING**: contrato versionado de
pins/admissão/recuperação com nova leitura dos bytes, mantendo V1/V2 simples.
Não repetir a inspeção nem promover composição/coorte. LB-05 PARTIAL;
COMPOSE-01/DEPS-COHORT e aceite nativo continuam necessários.

Entrega anterior: [LB-04 relatórios salvos](SAVED_REPORTS.md): índice schema 1 para
novos jobs, leitura limitada por hash no coordenador e tela paginada de texto
inerte no histórico. Sem reidratar Apply, reconstruir vínculos de jobs antigos,
executar JAR ou abrir caminhos históricos. Cancelar/fechar impede publicação
tardia. 100 checks passaram/um symlink SKIP (101 casos, 22 novos); fixture real
manteve original e hash convertido, três relatórios reabertos sem escrita.
Assemble/auditorias/CLI passaram; nativo NOT_RUN. JAR:
`b89a4a7795309f2ff70a05348a200bcafb1afe048c3c332910bf727663b4b5d9`.
[Ledger](evidence/saved-reports-2026-09-12.json). Seis perfis, motor/runtime,
adapters, dados, instalador e bibliotecas intactos; sem decisão de licença.
Naquele checkpoint, próximo LB-05/INSTALL-PROMOTION: admissão independente de contrato exato,
preservando requires de aceite COMPOSE-01/DEPS-COHORT e bloqueio da promoção
pública ainda não qualificada. Não repetir esta UI nem pedir decisões adiadas.

Entrega anterior: [LB-03 relinking](SRGUTILS_RELINKING.md): ferramenta dev-only
prepare/build/replace com fontes exatas, javac17/release8, hashes, limites,
ABI/ASM e saída privada sem sobrescrita. 20 testes focados passaram; biblioteca
modificada observada e motor do JAR público equivalente no fixture simples,
incluindo recusa de corrompido. CLI instalado em processos separados e fora
do cache/projeto reproduziu os hashes; correções e evidências no
[ledger](evidence/relink-2026-09-12.json). JAR público e LICENSE inalterados.
Nenhuma classe de mod executada. Forge/JiJ/JPMS/cliente/servidor NOT_RUN.
O próximo passo jurídico foi posteriormente adiado pelo usuário. Não escolher
licença automaticamente nem exigir essa resposta para desenvolvimento independente.
Revisão dos materiais de release e qualificação nativa continuam pendentes.
PACKAGE-RIGHTS continua BLOCKED, LB-03 PARTIAL; revisitar antes da publicação.
Não repetir esta rota técnica ou os harnesses antigos por ausência da decisão.

Entrega anterior: [LB-02 headless](PROMPT_02_HEADLESS.md): API/CLI/UI de revisão
local com escopo/proveniência/hash, vínculo do manifesto revisto e bloqueio de
mudança. Uma execução ativa/33 admitidas por classloader, progresso de espera,
freio cooperativo de heap e métricas separadas dos reports determinísticos.
Composição individual mantém dados full locais explícitos; não virou mapping
genérico incluído. 101 testes focados passaram, um symlink SKIP, 19 novos.
Seis perfis existentes e duas prévias passaram em Java isolado/cache vazio,
originais intactos; CLI de 64 MiB recusou dados full de forma estruturada.
Assemble/auditorias passaram, nativo NOT_RUN. JAR:
`76189bcfb81012d1bce3f63c2b43a819a1ef1473b357d35df295e570c3f39c22`.
Falhas iniciais e correções estão no [ledger](evidence/prompt02-headless-2026-09-12.json).
Não há isolamento rígido de heap nem aceite integral de LB-02. Próximo: preparar
fonte correspondente/substituição/relinking no prompt 3, sem jogos/alteração de
licença/publicação; depois explicitar a decisão de direitos que ainda depende
do titular. Não continuar indefinidamente apenas repetindo os mesmos testes.

Entrega anterior: [FINITE_COHORT_DATA](FINITE_COHORT_DATA.md): 537 bytes incluídos,
declarações reais de @Mod nas duas versões, hash real da dupla antes do scan e
escopo incompleto explícito. Prévia produto/CLI/serviço sem bundle quando campo
vazio; explícito inválido nunca faz fallback. Sem perfil público da dupla/Apply.
167 casos focados passaram, 15 novos, mais probe isolado sem cache/bundle/rede
com Java17 e heap 128 MiB. Falhas iniciais de fixture negativo/heap do oracle
de teste foram corrigidas; regressões pesadas executadas separadamente, sem
aumentar limites do produto. Assemble e auditorias passaram; nativo NOT_RUN.
JAR `f494cdf56327777f7421fbb5e2a11dd18c8fc8e95a5b84d097a11627e85a1df7`.
Próximo COMPOSITION_DATA_SCOPE: minimizar separadamente dados reais da prévia
individual lógica+recipes quando direitos/semântica/ownership permitirem.
Não reutilizar o inventário @Mod em outros perfis, embutir bundle privado ou
reabrir jogos/harnesses extensos. [Ledger atual](evidence/finite-cohort-data-2026-09-12.json).

Entrega anterior: [COHORT_TARGET_PREVIEW](PRODUCT_COHORT_PREVIEW.md): revisão de
entradas marcadas com hashes, inclusive ocultas pela pesquisa; planCohort
compartilhado, progresso/cancelamento e relatório tipado por artefato. Nenhum
job/recibo/Apply nasce da prévia. 34 checks focados aprovados, zero falhas/skips
ao final; 15 novos. Primeira rodada usou caminho errado do fixture antigo, gerou
três NoSuchFileException e foi corrigida/revalidada. Assemble e auditorias passaram.
JAR `b069df666292a1d833c5c8f18f455608cbeb5961e67570032e575a63d2fc029d`;
engine/runtime/perfis/adapters/dados/instalador preservados byte a byte.
Nativo NOT_RUN. Próximo FINITE_PREVIEW_DATA: dados mínimos, escopo/hash/proveniência,
direitos e rota permitida; nunca embarcar bundle LOCAL_USE_ONLY. Não repetir
harnesses nem promover compatibilidade pública por sucesso estático.
[Ledger atual](evidence/product-cohort-preview-2026-09-12.json).

Entrega anterior: [FINITE_TARGET_PAIR](DEPENDENCY_COHORT_TARGET.md). Dois candidatos
target privados serializados/reabertos, três classes preservadas byte a byte,
dependência obrigatória retida, ASM/linkage/grafo por JAR e seis mudanças metadata
com adapters reais. 136 checks focados passaram, zero falhas/skips ao final.
Não publica ou instala a dupla; aceite nativo permanece PARTIAL. Próximo:
COHORT_TARGET_PREVIEW no produto, sem duplicar regras ou habilitar Convert/Apply.
[Ledger atual](evidence/deps-cohort-target-2026-09-12.json).

Entrega anterior: [DEPS-COHORT origem](DEPENDENCY_COHORT_SOURCE.md). Dois JARs Forge
autorais, seleção com hashes, ownership/API por artefato, grafo nos dois lados e
linkage/acesso estáticos via serviço e CLI. Não gera dupla convertida nem Apply.
Próximo FINITE_TARGET_PAIR: receitas exatas e validação conjunta de candidatos
privados; depois comparação nativa quando autorizada. Sem abrir jogos ou repetir
harnesses de QA como próximo automático. [Ledger](evidence/deps-cohort-source-2026-09-12.json).

Entrega anterior: [PRODUCT-COMPOSED inspeção](PRODUCT_COMPOSITION_INSPECTION.md).
Prévia ligada ao motor existente, operações/gates/dados explícitos, cancelamento
e JSON versionado, sem JAR nem Apply. 23 checks focados passaram, com fixture
real em análise estática; nativo NOT_RUN. Próximo DEPS-COHORT: biblioteca+consumidor
autorais e ownership/API/linkage finitos, sem promoção pública antecipada.

Entrega histórica de 2026-09-10: [UI-HISTORY-READ](UI_HISTORY.md), histórico pesquisável de recibos,
detalhes e diagnósticos, apenas leitura e sem herdar Apply. Implementação estática
verificada; aceite visual/teclado/resize/servidor do novo JAR ainda NOT_RUN. Os
checks focados não substituem QA extensa, adiada por orientação do usuário.
Próximo: prévia tipada/revisão de admissão em PRODUCT-COMPOSED, reutilizando o
plano estático existente; conversão composta e Apply continuam bloqueados.

Histórico de 2026-09-08 (não substitui o cursor acima):
Trabalho headless concluído no recorte finito: **LB-05 / JOB-HISTORY-READ**.
Leitura schemas 1/2, diagnósticos/limites/cancelamento e nenhuma autoridade de Apply
herdada. Foram 50 casos novos e 155 testes do produto, zero falhas/erros/skips,
build e pacote aprovados. O primeiro teste detectou ausência de identidade para
hard links no Windows; a correção e junções/locks reais passaram depois.
Cursor de implementação agora: **LB-08 / COMPOSE-01**, **PARTIAL**.
Continuação mais recente: [proposta restrita de configuração ligada ao staging](COMPOSITION_RESTRICTED_SETTINGS.md).
50/56 properties ativas, template em defaultconfigs e receitas exatas, sem escrever
arquivos nem sobrepor configurações explícitas já revisadas. Rechecagem de pins,
receipt e árvore antes/depois; execução/instalação/save/isolamento continuam false.
Build completo serial aprovado em 33m17s: corpus 1.620 casos/1.613 aprovados/sete
skips existentes de symlink, zero falhas/erros. Todos os 456 novos passaram sem
skips. Outras suítes UP-TO-DATE, não repetidas. Rodada focada anterior: 682 casos
aprovados sem skips; o build incluiu quatro casos adicionais de baseline.
Comando real SOURCE/TARGET aprovado somente leitura: 111/223 pins, 10/11 properties
explícitas preservadas e nenhuma criação de mundo/config/template/marker.
JAR público e cinco pins do índice intactos, `runs: null`; nenhum jogo executado.
[Ledger atual](evidence/compose-restricted-settings-2026-09-08.json).
A expressão anterior "sem rede" não descreve garantia de configuração: authlib
do destino busca chaves no startup fora do checker Forge. Próximo no mesmo recorte:
contrato ligado de materialização/recuperação e conteúdo pós-save, sem arming/jogos.

Checkpoint anterior: [conteúdo finito das configurações nativas e inventário necessário](COMPOSITION_NATIVE_CONFIG_REVIEW.md).
Defaults reais das versões exatas, TOML finito e imutável; TARGET common vazio
recusado porque o Forge não registra o spec vazio. Revisão continua sem execução
ou autoridade: flags de version check, threads automáticas e anúncio LAN são
pendências, não prova de isolamento. Arquivos presentes são PRESENT_ONLY, nunca
save/exit correto; originais/staging continuam somente leitura.
Build completo aprovado em 29m20s: corpus fresco 1.164 casos/1.157 aprovados/sete
skips existentes de symlink, zero falhas/erros. Os 230 novos passaram sem skips
após corrigir erro inicial de compile no digest. Outras suítes UP-TO-DATE, não
repetidas. Stagings reais revalidados read-only: 13/12 saídas geradas ausentes,
como esperado sem executar. JAR público e cinco entradas do índice intactos;
`runs: null`. Evidências e limitações no
[ledger atual](evidence/compose-config-review-2026-09-08.json).
Naquele checkpoint, o próximo headless era configuração/propriedades restritas
e checagens de conteúdo/save, sem arming. Gates nativos continuam NOT_RUN.

Checkpoint anterior: [política finita de layout de saídas nativas](COMPOSITION_NATIVE_OUTPUT_POLICY.md)
e [auditoria do lookup dos launchers exatos](COMPOSITION_NATIVE_LOOKUP_AUDIT.md).
Pins imutáveis, propriedades explícitas preservadas, listas vazias e saídas opacas
limitadas ficam separados. Arquivos/diretórios desconhecidos, aliases e drift
bloqueiam; reconhecer caminho/hash não valida NBT/config/log/observação.
Build/pacote aprovados: corpus fresco 934 casos/927 aprovados/sete skips existentes,
zero falhas/erros. Os 241 novos testes passaram sem skips; outras suítes UP-TO-DATE,
não repetidas. Pastas reais revalidadas somente leitura: 111/223 pins, sem drift,
marker/mundo/log nativo. JAR público byte-idêntico, sem corpus/helper. XMLs/hashes
e limites no [ledger próprio](evidence/compose-output-policy-2026-09-08.json).
Sem jogos, arming, instalação, aquisição ou perfil novo. Próximo no mesmo corte:
semântica finita de configuração/conteúdo e conjunto obrigatório de saídas.
Aceite nativo/comparação/origem Java 17 permanecem pendentes e não autorizados.

Checkpoint anterior: [sessão fechada inerte e verificação read-only do staging](COMPOSITION_INERT_SESSIONS.md).
Receipt/plano/seletores atuais/argv do helper/pins agora se ligam a uma sessão
controlada com publicação create-new e recuperação somente leitura. Não utiliza
argv nativo, não arma probes, não instala e não transforma novos recursos.
Correção do diretório HotSpot `hsperfdata` e das barreiras de cancelamento/checagem
final validada com helper inerte. O teste de concorrência precisou de tolerância
de espera própria de 60 s para I/O; os limites do coletor continuam iguais.
Origem/destino reais revalidados read-only, 111/223 pins, sem drift/marker/mundo/log;
STAGED_INPUTS_VERIFIED_ONLY, nunca aceite nativo. Build/pacote aprovados, corpus
fresco: 693 casos/686 aprovados/sete skips, zero falhas/erros. Delta: 141 casos,
139 aprovados/dois skips de symlink. Converter: 831 casos/748 aprovados/83 skips,
zero falhas/erros na primeira rodada; UP-TO-DATE na regressão final. Demais suítes
UP-TO-DATE, não repetidas. Falhas corrigidas, motivos de skips, XMLs e hashes no
[ledger atual](evidence/compose-sessions-2026-09-08.json). JAR público byte-idêntico,
sem QA/helper. Índice nativo NOT_RUN/`runs: null`, sem Minecraft aberto.
Naquele checkpoint, o próximo era a política finita de saídas nativas mutáveis e lookup tardio
de launcher, somente estáticos. Comparação nativa/Java 17 de origem segue pendente
e não autorizada. Não aceitar COMPOSE-01 ou promover LINKAGE-GENERAL/instalação/UI.

Checkpoint anterior: [staging desarmado e coletor de processos inertes](COMPOSITION_STAGING_COLLECTION.md).
Staging create-new exige digest revisado, copies independentes/journal/hashes e
não grava o marker que autoriza o probe. Falhas preservam restos sem aprová-los.
Coletor interno limita JVM/pipes/concorrência, observa exit/EOF reais e revalida
pins/cancelamento na conclusão. Testa apenas helpers inertes, nunca Minecraft.
PROCESS_COLLECTED_ONLY não verifica a associação com plano nativo aprovado nem
comportamento. [Evidências e limitações](evidence/compose-staging-2026-09-08.json).
Build completo/pacote aprovados: corpus fresco 547 aprovados/cinco skips de
symlink, zero falhas/erros. Delta: 113 aprovados/um skip; outros quatro prévios.
Demais suítes UP-TO-DATE. JAR público byte-idêntico, sem classes novas de QA.
As duas cópias locais reais passaram: origem 33 arquivos, destino 71,
STAGED_UNARMED, sem drift/marker/mundo/log ou observação nativa.
Naquele checkpoint, o próximo headless era a sessão fechada ligando receipt/argv/artefatos imutáveis e
publicação/recuperação pós-exit, primeiro com helpers inertes. Comparação nativa
origem/destino continua NOT_RUN, sem autorização para abrir jogos; seis perfis
intactos e nenhuma promoção de instalação/UI ou LINKAGE-GENERAL.

Checkpoint anterior: [planejador somente leitura e verificador de saída registrada](COMPOSITION_RUN_PLANNING.md).
Planos das duas instalações reais retornam PLANNED_ONLY, sem criar diretórios,
copiar ou executar. O checker confere registros fornecidos de logs/exit, rejeita
falhas tardias e retorna só RECORDED_EXIT_ONLY, sem provar processo/EOF real.
Os 146 testes novos passaram sem skips; build completo aprovado, corpus fresco
434 aprovados/quatro skips antigos de symlink. Outras suítes UP-TO-DATE.
JAR público byte-idêntico; índice real NOT_RUN e `runs: null`, originais intactos.
[Ledger](evidence/compose-planning-2026-09-07.json).
Naquele checkpoint, o próximo headless era staging create-new sob ownership e coletor vivo
ligado ao plano, com testes de JVMs auxiliares inertes para saída/EOF/limites,
cancelamento, falhas de escrita e hashes. Não há executor/coletor vivo ainda.
Origem Java 17 segue sem validação nativa; o teste antigo usava Java 8.
Não abrir jogos, aceitar COMPOSE-01, liberar LINKAGE-GENERAL ou instalação/UI.

Checkpoint anterior: [probes original/alvo](COMPOSITION_PROBES.md)
implementados e compilados nas versões exatas, com 123 testes aprovados em cada
baseline, zero falhas/skips. Cinco testes sintéticos adicionais ligam o coletor
real ao checker e recusam comportamento errado/falha posterior de encerramento.
Build completo aprovado: corpus fresco 288 aprovados/quatro skips prévios de
symlinks; outras suítes UP-TO-DATE. JAR público byte-idêntico, sem os probes.
O índice real continua NOT_RUN e `runs: null`; originais intactos. Nenhum jogo foi
iniciado. [Ledger](evidence/compose-probes-2026-09-07.json).
Naquele checkpoint, o próximo headless no mesmo corte era implementar/testar planejador descartável de
execução e coletor pós-saída, controlando argumentos/cwd sem tokens e validando
logs completos, encerramento e hashes. Eventos Forge não impedem overrides de
mundo antes do carregamento; marcador de captura não comprova save/close/saída.
Não aceitar COMPOSE-01, liberar LINKAGE-GENERAL ou executar jogos.

Checkpoint anterior — [verificador de observações](COMPOSITION_OBSERVATIONS.md)
somente no módulo dev-only corpus, com 77 testes sintéticos aprovados, sem skips.
O índice real conferiu cinco arquivos/1.402.016 bytes e retorna NOT_RUN; não há
capturas nativas nem novos probes de composição ainda. Registros correspondentes
seriam apenas RECORDED_MATCH_ONLY, nunca prova independente de execução/Apply.
Próximo no mesmo recorte: implementar/compilar probes original/alvo com vínculo ao
artefato realmente carregado, escrita sem sobrescrita e coleta isolada de ticks,
falhas e encerramento. Build e limites ficam no
[ledger desta continuação](evidence/compose-observations-2026-09-07.json).
Build completo/pacote aprovado: corpus fresco 283 aprovados/quatro skips prévios
de symlinks; demais suítes UP-TO-DATE, não reexecutadas. JAR público byte-idêntico.
Não abrir jogos, não aceitar COMPOSE-01 e não liberar LINKAGE-GENERAL.

Checkpoint anterior — exportação controlada:

O [contrato do leitor](JOB_HISTORY_READ.md) e [ledger](evidence/job-history-read-2026-09-07.json)
separam escrita/leitura de recibos, autoridade de instalação e aceite nativo.
COMPOSE-01 mantém a [prévia](COMPOSITION_PREVIEW.md) e o
[candidato privado](COMPOSITION_CANDIDATE.md), agora com
[exportação controlada somente para QA](COMPOSITION_QA_EXPORT.md). A tarefa dev-only
refaz a prova estática do candidato, confere hashes selecionados e retém JAR/prova/recibo separados
em `build/composition-qa/run-20260907-export1`. Os 13 arquivos foram rechecados;
quatro classes/dez métodos preservados, original intacto e nunca executado.
A API pública continua devolvendo somente prova/hash; as ferramentas de exportação
ficam fora dos artefatos públicos/CLI. Nenhuma autorização de instalação é gerada.
Os 96 casos de composição passaram (40 novos: nove de integração e 31 de arquivos),
incluindo junção real do Windows. Build completo e exportação aprovados, com
70 skips explícitos no [ledger](evidence/compose-export-2026-09-07.json).
Sete saídas nos seis perfis anteriores e o candidato composto continuam byte-idênticos.
Não há sétimo perfil público ou promoção de instalação/UI. Próximo no mesmo corte:
harnesses de comparação original/alvo e observações ligadas aos artefatos, sem
executar jogos agora. Falhas tardias/crash podem deixar restos privados não confiáveis;
o exportador não implementa recuperação automática nem substitui o instalador.
Comparação no jogo segue NOT_RUN e obrigatória; não liberar LINKAGE-GENERAL.
JAR público atual: `4e74a86169bb05d8ce299c000d4945c1bcf3746f53c8b3684ee37fe67a9b1b84`,
somente build/pacote estático; não herda aceite nativo de hashes anteriores.
Não pedir autorização de tela novamente a cada retomada: o usuário autorizou
priorizar o que não precisa de Minecraft. Novas aquisições/publicação continuam adiadas.

Gate adiado: **LB-06 / PUBLIC-CURRENT-E2E / recuperação nativa de commit pendente**.
Restrição atual do usuário: **somente headless; não abrir jogos nem automatizar
tela/foco/mouse/teclado sem nova autorização explícita**. As sessões nativas
registradas abaixo já estavam encerradas antes desse pedido.
[Implementação dos dados internos](EMBEDDED_SIMPLE_CONTENT_DATA.md) e
[aceite nativo de 2026-09-07](EMBEDDED_SIMPLE_NATIVE_UI_VALIDATION.md): no mesmo
JAR público, UI com campo vazio converteu, continuar manteve o fixture inativo,
Apply/close registrou um ADD seguro e a nova inicialização descobriu o mod.
Crafting real consumiu nove lingotes por um bloco, colocado no mundo; assets e
traduções pt_br/en_us foram observados, original intacto. Ambos os clientes saíram
zero e o mundo descartável foi salvo. Nenhum perfil foi ampliado.

Continuação: [negativos no mesmo hash](PUBLIC_CURRENT_NEGATIVE_VALIDATION.md)
comprovaram corrompido, duplicados de entrada, worldgen, dependência ausente,
falha real de escrita/restauração, Cancel aceito e fechamento da tela com trabalho
ativo sem saída parcial. As tentativas que terminaram CONVERTED não foram
renomeadas como CANCELLED. Fixture de carga limitada é só QA, não suporte novo.
Quatro testes headless de interrupção/recuperação/locks passaram separadamente;
não usaram um cliente Minecraft nem o substituem.

Nova continuação headless: [índice e verificador de integridade](PUBLIC_CURRENT_QUALIFICATION.md)
consolidam 192 arquivos e 61 asserções exatas dos registros. Recarga observada em
log permanece separada de screenshot. Integridade não promove aceite nem executa
o jogo. Quatro pendências e três dimensões não reivindicadas estão explícitas.

Continuação de recuperação: [sete novos casos headless](PUBLIC_NEXT_SESSION_RECOVERY_VALIDATION.md)
tratam a próxima sessão com parte/todo o lote declarado carregado: retomada sem
reescrever publicações comprovadas, commit ausente e recusas por ID, alias de
arquivo ou mundo aberto. São snapshots sintéticos, não descoberta pelo Forge;
nenhum código de produção/perfil mudou nem o gate nativo foi promovido.

Pendência nativa histórica (não o cursor headless atual): desenho/qualificação do oracle de interrupção/recuperação
durante commit no cliente público. O teste nativo depende de autorização renovada;
polling de arquivo seguido de kill ainda não é um oracle validado. Aproveitar
positivo e servidor/cacheless do mesmo hash, sem repetir para somar PASS. Não
confundir duplicado de entrada com conflito instalado, nem teste JVM com UI.
Naquela execução, persistência do recibo instalado/PENDING_RESTART e histórico
reabrível faltavam; o commit foi comprovado pelo journal e nova descoberta,
não pelo recibo. Os recortes headless posteriores corrigem escrita/leitura,
mas não qualificam uma tela nativa de histórico ou reconciliação de ativação.
Escala/redimensionamento e matriz ampla de UI ainda não foram requalificados.
Os cinco perfis de desenvolvimento continuam com dados explícitos; LB-04 fica
PARTIAL por UI-PROMOTION, e o produto permanece experimental/Beta.

Cursor de networking adiado, não resolvido:
**NET-QA-02-HANDSHAKE / CLIENT_PROTOCOL: oráculo nativo normal ausente**.
Última rodada: `handshake-cp-clearreturn1` passou somente no modo diagnóstico
CLEAR_RETURN_TRACE (65 s). O único breakpoint observa o retorno nativo, não os
envios/setters iniciais. Houve uma amostra: autoRead=false, interestOps=0,
readPending=false, chave válida. A VM encerrou antes da janela posterior de 3 s
(390 ms desde a amostra); não há estado posterior inventado. Resultado do trace:
OBSERVED_NO_WITNESS. A recusa Forge é real nessa execução instrumentada, mas
não prova correção nem aprovação em DEFAULT. Cliente/servidor/coletor saíram
com código zero; original, convertido, probe em jogo e produto mantêm os hashes.
O controlador agora também rejeita coerção de strings para booleanos/números;
o relatório nativo foi revalidado com essa checagem. O cursor não avançou.
[Observação e limites](NETWORK_HANDSHAKE_CLEAR_RETURN.md) e
[ledger desta rodada](evidence/network-handshake-clear-return-2026-09-07.json).

Próxima retomada: **mudar a estratégia de investigação**, não repetir estes
breakpoints nem testes positivos para acumular PASS. A observação no retorno
também não reproduziu a falha normal. Antes de novo lançamento, definir uma
hipótese discriminante e verificar, nas fontes/bytecode fixados, um ponto de
observação sem suspensão da ativação/envios/clear; se não houver meio seguro,
registrar o impedimento e pedir direção antes de trocar versões ou alterar a
plataforma. A sequência exata que perde OP_READ na falha normal continua sem
prova. Não mascarar isso com read/flush, espera artificial ou oráculo relaxado.
Depois de resolver esse gate: SERVER_ABSENT, SERVER_PROTOCOL, comparação
original/alvo, NET-QA-03 e só então os recortes de capabilities abaixo.

Rodada anterior: `handshake-cp-protocoltrace1` confirmou em jogo os valores locais
`UNSET -> HANDSHAKING` no primeiro envio e `HANDSHAKING -> LOGIN` no segundo.
O diagnóstico passou, mas novamente não capturou o clear defeituoso. A comparação
sem depurador `handshake-cp-protocol-pair1` falhou: 22 envios de handshake no servidor,
nenhuma decisão nativa do canal e nenhuma entrada LoginWrapper no cliente.
O lote parou; SERVER_ABSENT e SERVER_PROTOCOL continuam NOT_RUN. A inicialização
pendente deixou de ser mera hipótese nesta amostra, mas não é causa completa provada
do travamento. Próximo: capturar o clear tardio/reativação no caminho inicial já
identificado, com menor perturbação, sem forçar I/O nem modificar a plataforma.
[Observação e limites](NETWORK_HANDSHAKE_PROTOCOL_OBSERVATION.md) e
[ledger anterior](evidence/network-handshake-protocol-2026-09-07.json).

Rodada anterior: `handshake-cp-readtrace1` passou somente no modo diagnóstico JDI.
Oito amostras reais mostram desativações dos dois envios iniciais do ConnectScreen
e reativações no Netty. O clear observado ocorreu com autoRead=false; a leitura
foi restabelecida. O intercalamento defeituoso não foi capturado, e não houve
nova aprovação normal nem correção do produto. Próximo: examinar o protocolo
anterior no primeiro envio/ativação e a remoção concorrente de OP_READ, sem
forçar I/O nem modificar a plataforma. [Rastreamento](NETWORK_HANDSHAKE_READ_TRACE.md)
e [ledger anterior](evidence/network-handshake-read-trace-2026-09-07.json).

Na rodada anterior, `handshake-ca-platform-pair1` aprovou CLIENT_ABSENT em
modo DEFAULT (61 s), no probe atualizado, sem alterar o produto ou o convertido.
O controle sem LegacyBridge também passou, mas não provou uma causa da plataforma.
CLIENT_PROTOCOL falhou em `handshake-client-protocol1`; o lote parou antes de
SERVER_ABSENT e SERVER_PROTOCOL. A falha intermitente de transporte permanece
aberta: nenhum patch de produção ou correção de OP_READ foi aplicado.
[Controle e comparação](NETWORK_HANDSHAKE_PLATFORM_CONTROL.md) e
[ledger anterior](evidence/network-handshake-platform-2026-09-07.json).

Histórico anterior, preservado:
O controle positivo histórico passou em `handshake-matched2`; CLIENT_ABSENT
falhou novamente em modo normal (`handshake-client-absent3`). Os outros três
negativos não foram executados. Diagnóstico opt-in identificou ReadTimeoutException
em dois testes; outro passou com logging extra, mas não vale como aprovação normal.
Amostras reais de cliente e servidor durante a espera mostraram LOGIN, autoRead
habilitado e buffers vazios naqueles instantes; isso não prova a causa nem a entrega.
Na retomada seguinte, uma amostra real do seletor localizou o bloqueio:
`handshake-ca-selector1` mostrou cliente autoRead=true, mas interestOps=0 e
readPending=false; o servidor mantinha OP_READ. O mesmo estado foi reproduzido
com intercalamento controlado no Netty, sem executar Minecraft nesse teste.
JFR passou somente no modo diagnóstico; não foi aplicada uma correção de jogo.
Investigação de transporte ainda pendente: comprovar o disparador da remoção de OP_READ e avaliar uma
correção compatível com as restrições; não forçar read/flush ou alterar a plataforma.
[Evidência inicial](evidence/network-handshake-2026-09-07.json) e
[continuação diagnóstica](evidence/network-handshake-diagnostics-2026-09-07.json)
ficam separadas. O JAR público, original e convertido permanecem intactos.
[Evidência JFR/seletor](NETWORK_HANDSHAKE_TRANSPORT_DIAGNOSTICS.md) e seu
[ledger](evidence/network-handshake-transport-2026-09-07.json) registram esta retomada.
Não avançar esse gate nem aceitar desconexão/timeout genérico como recusa válida.

NET-QA-01 foi aprovado no recorte finito do fixture
em `comparison5` (57 recibos, dois clientes + servidor, shutdown limpo), conforme
[ledger de 2026-09-07](evidence/network-native-continuation-2026-09-07.json).
Não repetir esse mesmo gate sem mudança de entradas ou motivo concreto; não
herdar seu PASS para versões, perfis ou comportamento fora do recorte.

Origem fixa: Minecraft 1.16.5 / Forge 36.2.42. Destino fixo: Minecraft 1.20.1 /
Forge 47.4.23 / Java 17. Preservar `legacybridge_runtime` 0.1.1-SNAPSHOT e o
`simple-content-v1`. A interface e o instalador público continuam nesse perfil;
os outros cinco perfis são opt-ins de desenvolvimento, não uma composição de
suporte a mods com todas essas famílias de recursos.

## Como retomar a mesma mensagem

1. Ler este índice, o status, as instruções locais e o ledger do ID ativo.
2. Conferir implementação, hashes, temporários e processos reais. Evidência
   histórica não é transferida automaticamente para um novo artefato.
3. Concluir um recorte verificável ou corrigir a falha concreta encontrada nele.
   Não abrir o próximo perfil para contornar um teste falho do atual.
4. Rodar build/test serialmente após alterações; execução nativa é separada.
   Usar instâncias descartáveis e nunca executar o original na conversão.
5. Atualizar ID ativo, evidência, critérios restantes e próximo ID. Um impedimento
   externo pode permanecer pendente; registrá-lo não equivale a concluir o recurso.
6. Repetições da mensagem são retomadas, não dez aprovações automáticas. As
   estimativas existentes são esforço de engenharia, não contagem de mensagens.

Estados de evidência são independentes: analisável, convertível, bytecode/linkage
validado, inicializa, comportamento validado e multiplayer validado. Um recurso
recusado como `UNSUPPORTED` está bloqueado com segurança, não implementado.
Não calcular percentual de mods compatíveis a partir de símbolos, testes ou IDs
concluídos. O corpus congelado continua com zero ports reais completos validados.

## Índice completo do escopo registrado

| ID estável | Situação verificada / próxima obrigação | Referência |
| --- | --- | --- |
| BASE | Gradle/módulos, analisador inerte, mappings exatos, adapters implementados, pipeline ASM, relatórios versionados e recusas existem; manter regressões e validação de linkage/acesso | [Arquitetura](../ARCHITECTURE.md), [pipeline](ASM_PIPELINE.md) |
| SIMPLE | Recorte inicial item/bloco/recipe/assets validado historicamente; não substituir nem alargar silenciosamente | [Status](PROJECT_STATUS.md) |
| P1–P6 | Arquitetura de produto, serviço, JAR único, UI, instalação transacional e fluxo público implementados em recortes; aceite final e limites não desaparecem | [Plano P1–P6](PRODUCT_IMPLEMENTATION_PLAN.md) |
| CORPUS | Versão 2026.09.06.1: seis fixtures e três releases reais; separar novos ledgers de fixtures desse snapshot | [Backlog versionado](../corpus/BACKLOG.md) |
| LOGIC | Preservação de seis classes/25 métodos com comparação nativa; overrides, interfaces, construtores, lambdas e dependências fora dos envelopes continuam bloqueados | [Contrato](LOGIC_PRESERVING_PROFILE.md), [validação](LOGIC_PRESERVING_VALIDATION.md) |
| LIFE | Registros explícitos/inferidos, consultas tardias, common setup, COMMON bool/int e comando finito; ainda faltam outros eventos, prioridade/cancelamento, lados, CLIENT/SERVER config, reload e demais comandos | [Contrato](LIFECYCLE_PRESERVING_PROFILE.md), [validação](LIFECYCLE_PRESERVING_VALIDATION.md) |
| ITEM | IB1 alimento básico existe; IB2 durabilidade, IB3 bloco preservando corpos, IB4 ferramentas/armaduras e IB5 interações/estados/redstone continuam por implementar | [IB1–IB5 e estimativas](ITEM_BLOCK_CORPUS_AUDIT.md), [limites do alimento](FOOD_ITEM_PROFILE.md) |
| DATA | R1 cooking/stonecutting e tags locais existe; condições, serializers/ingredientes customizados, loot/GLM, advancements, funções, modelos/animações e demais sons exigem lotes próprios | [R1–R3](RESOURCE_CORPUS_AUDIT.md), [contrato](DATAPACK_RECIPES_PROFILE.md) |
| NET | Codec S2C fixo e corpo preservado existem; testes de rede são os IDs NET-QA abaixo, nunca suporte genérico | [Contrato](NETWORK_SNAPSHOT_PROFILE.md), [validação](NETWORK_SNAPSHOT_VALIDATION.md) |
| CAP | Auditoria somente; energia, inventário, fluidos, jogador e SavedData continuam indisponíveis | [Auditoria no status](PROJECT_STATUS.md#capability-audit-facts-not-implemented-equivalences) |
| B01–B06 | Metadata/lados → eventos completos observados → fechamento de Clumps; Controlling e AppleSkin são posteriores e condicionais, não desbloqueados pelos fixtures | [Dependências, riscos e estimativas](../corpus/BACKLOG.md) |
| RELEASE | Direitos/dados, aceite público no hash final, documentação honesta; sem publicação automática | [Direitos](PACKAGING_RIGHTS.md), [P6](PUBLIC_ONLY_END_TO_END_VALIDATION.md) |
| INTAKE-FINAL | Só após o fechamento do escopo planejado e dos gates de qualidade: selecionar/obter/testar releases famosos 1.16.5 sem release 1.20.1 encontrado nas fontes verificadas | Critérios abaixo |

Os índices ITEM/DATA resumem, mas não descartam pedidos: efeitos, encantamentos,
brewing, reparação/atributos, subclasses, orientação/formas/colisão, drops/ferramentas,
ticks/crescimento/plantas e creative tabs/ordenação continuam dentro do backlog
ITEM, sem implementação inferida. DATA inclui condições falsas, tags substitutivas,
IDs ausentes, reload, falhas de parser e preservação de bytes por formato.
Os testes de alimento ainda não certificam a sequência completa de uso/consumo
com cliente real; match/assemble das recipes não certifica máquinas, combustível,
XP ou transações de menus. Esses gates permanecem nos seus ledgers.

## Fila imediata e critérios de passagem

| ID | Aceite mínimo | Dependência / limite |
| --- | --- | --- |
| NET-QA-01 | APROVADO para o fixture exato: dois clientes alvo + servidor dedicado, entrega/isolamento/thread, payloads inválidos, duplicados/gaps, ID desconhecido, reconexão/direção; hashes intactos e shutdown limpo | comparison5, 2026-09-07; não prova estabilidade/soak nem resolve a causa da falha anterior de handshake |
| NET-QA-02 | PENDENTE; dividido nos dois subgates abaixo, sem descartar comparação nem negativos | Nenhum subgate fecha o outro; não coberto por NET-QA-01 |
| NET-QA-02-HANDSHAKE | ATUAL: CLIENT_ABSENT passou em modo normal no probe atualizado; CLIENT_PROTOCOL falhou sem oráculo nativo. SERVER_ABSENT/SERVER_PROTOCOL NOT_RUN. Investigar CLIENT_PROTOCOL e a intermitência comum antes de retomar o lote. MATCHED histórico aprovado, sem herança automática entre probes | [Controle e decisão de sequência](NETWORK_HANDSHAKE_PLATFORM_CONTROL.md), [aceite original](NETWORK_HANDSHAKE_VALIDATION.md). O PASS normal justificou coletar o próximo negativo, não alegar correção. Não forçar read/flush, trocar plataforma ou promover diagnóstico |
| NET-QA-02-COMPARISON | APÓS HANDSHAKE: comparação nativa original/alvo do mesmo contrato e casos finitos de payload/sessão; não basta um snapshot positivo | Original executado somente em instalação isolada autorizada; probe alvo não prova comportamento original |
| NET-QA-03 | Revisar trabalho pendente/fechamento, saturação e limites de fila; verificar a implementação e comportamento antes de admitir proteção de sobrecarga | Fixture atual não tem epoch explícito nem bound de trabalho pendente; WAN/soak são gates separados, não efeitos do tamanho fixo |
| CAP-API-01 | Reaplicar a cada fixture a auditoria de registro, obtenção, providers/attachment, LazyOptional, invalidação, serialização e host lifecycle nas duas versões | Checklist comum, não um adapter genérico nem suporte implementado |
| CAP-E01 | Fixture real Java 8/Forge36 por ItemStack, regras exatas e linkage, negativos; comparar save/load, cópia, faces, simulação e invalidação no runtime | Só expor perfil/adapter quando a implementação semântica e os testes existirem; não reaproveitar dispatch de lógica sem prova |
| CAP-I01 | Inventário por face/instância, serialização/invalidação, save/load e reabertura sem perda ou duplicação | Fixture independente; energia não prova inventário |
| CAP-F01 | Fluidos por face/instância, simulação/execução, persistência e ciclo de vida | Fixture independente; nenhum estado global compartilhado |
| CAP-P01 | Dados por jogador, attachment, LazyOptional, clone/morte, respawn, dimensão e desconexão | Sincronização é contrato separado; persistir não sincroniza |
| CAP-S01 | SavedData e persistência somente para padrões concretos; ciclo de save/load e isolamento | Não declarar migração de mundos antigos |
| LOGIC-QA-01 | Glint/assets e comportamento cliente/multiplayer do envelope preservador | [Gates existentes](LOGIC_PRESERVING_VALIDATION.md); servidor/ASM não provam a apresentação cliente |
| LIFE-QA-01 | Branch enabled=false, recriação do dispatcher e live reload no fixture finito | [Gates existentes](LIFECYCLE_PRESERVING_VALIDATION.md); CLIENT/SERVER config ainda exigem extensão separada |
| ITEM-QA-01 | Uso real de 32 ticks, última unidade, pickup automático, persistência/relog e isolamento | [Gates existentes](FOOD_ITEM_VALIDATION.md); invocar o endpoint de consumo não prova a sequência inteira |
| DATA-QA-01 | Máquinas reais, combustível/input/XP, stonecutter, recipe book e sincronização cliente | [Gates existentes](RESOURCE_CORPUS_AUDIT.md); match/assemble/reload não prova transações de menus |
| FOLLOWUP | Retomar gaps LOGIC/LIFE/ITEM/DATA e B01–B06 respeitando dependências e seus critérios; atualizar este índice a cada recorte | Nenhum recorte finito fecha automaticamente uma família inteira |
| RELEASE-DATA | Fluxo explícito permitido dos dados necessários sem cache Gradle do desenvolvedor | Não embutir dados sem autorização nem baixar silenciosamente |
| RELEASE-RIGHTS | Direitos de redistribuição, licença própria/LGPL e fontes correspondentes quando exigidas | Pode depender de decisão externa; build não certifica autorização |
| RELEASE-PUBLIC-QA | UI, servidor e transações P4/P5/P6 no hash público final | Usar os aceites já definidos, sem transferir provas entre hashes diferentes |
| RELEASE-QA | Reexecutar P1–P6 relevantes no artefato final, resolver direitos/dados e registrar os gates não aprovados | Kill-mid-commit público, acessibilidade ampla, offline e restart real continuam separados; não rotular encerramento manual como restart |

Não inverter uma dependência sem justificar a mudança no status. Um recorte já
verificado pode encaminhar o próximo, mas gates remanescentes conservam IDs e
continuam pendentes no encerramento geral. Não transformar o backlog B03 em
suporte genérico a entidades, rendering, worldgen ou dimensões.

Cada fixture de capability deve declarar sincronização como implementada,
não implementada ou não exigida pelo contrato verificado. Persistência nunca
autoriza inferir atualização cliente, nem dispensa prova por jogador/instância.

## Aquisição final de mods reais

A instrução de 2026-09-07 adia novas aquisições de corpus (IB0, R2 e B07) até a
fase final. Não apagar essas tarefas nem inventar frequência de padrões: usar
fixtures próprios para prova técnica e o corpus já autorizado para a relevância
observável. Se a escolha de um recorte exigir dados ainda indisponíveis, registrar
a dependência e mantê-lo sem estimativa/alcance comprovado.

Antes de baixar um candidato final, verificar fontes oficiais atuais, release
exata, loader/dependências, licença e autorização de obtenção/teste. Registrar
onde e quando se procurou versão 1.20.1; ausência em um catálogo não prova
inexistência universal. Manter binários locais, originais intactos, hashes e
proveniência. Não redistribuir privados, burlar acesso, usar downloads ocultos ou
publicar arquivos. Só então analisar → planejar → converter/recusar → validar
bytecode/linkage → executar separadamente cliente/servidor/comportamento/multiplayer.
Uma recusa clara é resultado válido; popularidade não autoriza ignorar bloqueios.
