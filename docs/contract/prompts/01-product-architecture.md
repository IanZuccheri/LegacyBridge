# LB-01 — Arquitetura do produto e limites de ativação

Número canônico: 1. Estado autoritativo: [status.json](../status.json), entrada `LB-01`.

## Prompt para executar

Trabalhe apenas no projeto existente LegacyBridge. Leia integralmente o
[contrato comum](../README.md), seu status atual e as referências abaixo antes de agir.
Execute um recorte verificável deste prompt, respeitando as dependências de cada corte;
não presuma que o pedido autoriza completar toda a família numa única mensagem.
Se este não for o cursor ativo, confirme a mudança de prioridade no contrato antes de implementar.

## Ponto de partida da revisão 2026-09-07

Existe um artefato público obrigatório e separação engine/workflow/runtime/UI. A descoberta ocorre antes do menu; um JAR legado colocado em mods pode impedir o próprio menu de abrir.

## Recortes e dependências

- `PRODUCT-BASE`: Arquitetura inicial de artefato único e pastas separadas documentada; não equivale a aceite de execução. Requer: `GOV-01`.
- `PRODUCT-COMPOSED`: Definir promoção de contratos combinados ao produto, identidade dos adapters e fronteiras de instalação. Requer: `COMPOSE-01`.

Os estados mutáveis ficam somente no JSON. Um corte aceito prova apenas o escopo
da sua descrição/evidência, não todo o prompt nem artefatos posteriores.

## Trabalho solicitado

Entregue em 2026-09-19: [PRESERVED_UPDATE_COORDINATOR](../../PRESERVED_UPDATE_COORDINATOR.md).
Seleção tipada do antecessor/admissão, preflight read-only, confirmação separada
de uso único, worker/gate/lease compartilhados, atualização/recuperação reais e
recibos/histórico inertes. O próximo desenvolvimento é **LB-04 / UI-PROMOTION /
PRESERVED_UPDATE_SCREEN**, para ligar o formulário nativo developer à API pronta.
Não repetir coordenador/backend, abrir jogo, promover Apply público ou aceitar
requires/nativos pendentes por compilação.

Escopo executado após [PRESERVED_MANAGED_UPDATES](../../PRESERVED_MANAGED_UPDATES.md):
**PRESERVED_UPDATE_COORDINATOR**. Integrar a API de atualização preservadora já
implementada, não reescrever seu instalador: seleção explícita de nova admissão e
predecessor/plan hash, revisão fresca, confirmação consumível ligada à sessão,
resultados/erros, recovery e recibos separados. Compartilhar worker/gate e lease
de encerramento. Histórico nunca restaura autoridade. Não chamar o instalador
na thread de render, cancelar commit, atualizar carregados/desconhecidos, migrar
V1/V2 ou habilitar Apply público. Integração de formulário/tela nativa e QA são
gates seguintes explícitos; não considerar a API backend como interface pronta.

Recorte implementado em 2026-09-19: [PRESERVED_PREPARATION_WORKFLOW](../../PRESERVED_PREPARATION_WORKFLOW.md).
Análise/conversão/revalidação/admissão pelo worker e tela developer, relatórios,
cancelamento e ligação explícita à revisão foram entregues, sem novos perfis.
196 regressões passaram; rechecagens, pacote e limitações no ledger. Nativo
NOT_RUN; PRODUCT-COMPOSED não é aceito por esta ligação. Naquele checkpoint, próximo desenvolvimento:
**LB-05 / INSTALL-PROMOTION / PRESERVED_MANAGED_UPDATES**; não repetir preparação.

Escopo executado após a [tela preservadora](../../PRESERVED_DEVELOPER_SCREEN.md):
a tela developer partia de uma admissão preparada fora dela. Ligar o motor finito e a
preparação de admissão existentes a operações tipadas no coordenador limitado:
seleção explícita original/candidato/dados com hashes, progresso real, cancelamento
seguro, relatórios associados e admissão disponível somente após validação completa.
Reutilizar serviços/regras, sem CLI/main/stdout, novo executor, execução do original,
arquivo parcial visível, PASS salvo como autoridade ou instalação automática.
Começar por logic-preserving-v1 e dados full locais explícitos. Ligação para a
tela exige nova revisão/consentimento, preserva Convert/Apply público simples e
recusa qualquer composição/coorte ainda não admitida. Não repetir o instalador
ou o host entregue; requires e promoção pública/nativa continuam pendentes.

Recorte desenvolvido após LB-05 (2026-09-13):
**PRESERVED_INSTALL_COORDINATOR**, documentado em
[coordenação preservadora](../../PRESERVED_INSTALL_COORDINATOR.md). A [API add-only preservadora](../../PRESERVED_ADD_ONLY_INSTALL.md)
já instala/retoma/restaura o contrato logic-preserving-v1 com aprovação e pins
explícitos. Integrar sua revisão e resultados tipados no coordenador limitado
existente por rota developer opt-in, incluindo sessão atual, exclusão mútua de
instalação/mundo/fechamento, recibos e histórico por schema; não usar CLI/stdout
ou executar trabalho pesado no render. Não reconstruir aprovação de um PASS salvo.
Não duplicar verificadores/instalador, abrir jogos, promover composições/coortes
ou alterar o Convert/Apply público simples. Reutilizar só a implementação estática
de INSTALL-PROMOTION; seus gates/requires e aceites nativos continuam pendentes.
O produto deve explicar COMMITTED como próxima inicialização, sem inventar restart.

Desenvolvimento anterior, já entregue: **LB-04 / UI-PROMOTION / PRESERVED_DEVELOPER_SCREEN**.
Ligar a API pronta a uma tela/host developer explicitamente opt-in, sem mudar o
Convert/Apply público simples. Capturar sessão real no lado/thread correto,
manter o snapshot thread-safe e as guardas de navegação/mundo/fechamento enquanto
o coordenador estiver instalando; seleção/revisão/consentimento não vêm do
histórico. Reutilizar resultados/recibos/worker, não escrever outro instalador.
Trabalho estático sem abrir jogo; os requires de promoção e aceites nativos
permanecem pendentes. Não repetir este coordenador como próximo recorte.

Entrega anterior de 2026-09-12: [COHORT_TARGET_PREVIEW](../../PRODUCT_COHORT_PREVIEW.md)
implementado com revisão explícita de entradas marcadas, worker compartilhado,
progresso/cancelamento, diagnósticos e relatório. 34 checks focados passaram;
pacote auditado, engine/runtime/perfis/instalador preservados. Nativo NOT_RUN.
Próximo desenvolvimento: **LB-02 / DATA-EXPAND / FINITE_PREVIEW_DATA**; não outro
ciclo de prévia/QA para este corte. PRODUCT-COMPOSED permanece PARTIAL no aceite.

Recorte implementado (checkpoint anterior de 2026-09-12):
**COHORT_TARGET_PREVIEW**. O motor [FINITE_TARGET_PAIR](../../DEPENDENCY_COHORT_TARGET.md)
já monta/reabre a dupla autoral privada e retorna prova tipada por artefato.
Integrar seleção múltipla explícita com hashes, progresso real/cancelamento,
resultado legível e detalhes de bloqueio ao produto, reutilizando `planCohort`.
Não duplicar receitas, chamar CLI, expor bytes/JARs intermediários ou conceder
Apply. Não trocar o estado de conversões individuais por uma aprovação de coorte.
Dados completos continuam explícitos; built-in simples não pode fingir suportá-la.
Nativo/instalação pública continuam pendentes. Os próximos abaixo são históricos.

Próximo recorte de desenvolvimento após UI-HISTORY-READ (revisão 2026-09-10):
reutilizar o plano estático existente para prévia tipada/explicação de admissão,
sem duplicar regras no produto e sem habilitar conversão/Apply de composições.
`COMPOSE-01` permanece PARTIAL: sua parte estática implementada pode fundamentar
o desenvolvimento, mas o `requires` acima continua obrigatório para aceite e
promoção. Não expandir harnesses de QA como substituto dessa entrega.

Entrega de 2026-09-12: [inspeção tipada/tela](../../PRODUCT_COMPOSITION_INSPECTION.md)
implementada com 23 checks focados e pacote auditado. PRODUCT-COMPOSED permanece
PARTIAL para promoção/aceite nativo. Próximo desenvolvimento registrado no cursor:
DEPS-COHORT, não outro ciclo de infraestrutura de QA para a mesma prévia.

1. Preserve gameDir/legacybridge/input (originais), staging, reports, cache e transactions. Separe inventário/análise/plano/conversão/validação/instalação/ativação.
2. Defina como planos combinados declarariam operações, dados e ABI realmente necessários sem expor perfis de desenvolvimento prematuramente. Versione os contratos entre módulos.
3. Explique a impossibilidade de resgatar genericamente um loader já interrompido por mods legados em mods. Importação deve ser explícita e não destrutiva.
4. Mantenha o fallback de fechar e reabrir pelo launcher. Bootstrap/pre-discovery ou integração com um launcher específico exigem prova de necessidade e nova decisão registrada, não são requisitos presumidos.

## Aceite específico

- Continuar não altera mods instalados/carregados; aplicar só prepara a próxima inicialização.
- Um novo perfil só chega ao jogador após LB-03, LB-04, LB-05 e LB-06 para aquele perfil/artefato, sem inferir aceite a partir do CLI.

## Limites e parada

Sem hot-loading, migração de mundos ou promessa de restart universal.

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

Risco: alto em descoberta e classloaders. Estimativa: Revisão de promoção por perfil; estimar após o plano composável.

- [docs/PRODUCT_ARCHITECTURE.md](../../../docs/PRODUCT_ARCHITECTURE.md)
- [docs/PRODUCT_IMPLEMENTATION_PLAN.md](../../../docs/PRODUCT_IMPLEMENTATION_PLAN.md)
- [docs/PRODUCT_API_BASELINES.md](../../../docs/PRODUCT_API_BASELINES.md)
