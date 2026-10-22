# LB-05 — Instalação, atualização, recuperação e encerramento

Número canônico: 5. Estado autoritativo: [status.json](../status.json), entrada `LB-05`.

## Prompt para executar

Trabalhe apenas no projeto existente LegacyBridge. Leia integralmente o
[contrato comum](../README.md), seu status atual e as referências abaixo antes de agir.
Execute um recorte verificável deste prompt, respeitando as dependências de cada corte;
não presuma que o pedido autoriza completar toda a família numa única mensagem.
Se este não for o cursor ativo, confirme a mudança de prioridade no contrato antes de implementar.

## Ponto de partida da revisão 2026-09-07

Há journal persistente, commit por arquivo, resume/restore e upgrade gerenciado não carregado. O validador e journal aceitam apenas simple-content-v1/bootstrap; isso bloqueia promoção automática dos outros perfis.

## Recortes e dependências

- `INSTALL-BASE`: Instalador finito implementado, testes e limitações documentados; não qualifica falha de energia física. Requer: `PRODUCT-BASE`.
- `JOB-INSTALL-RECEIPTS`: Corrigir persistência append-only dos jobs de Apply/recovery, vínculo por índice/identidade/hash, erros e barreira de encerramento. Aceite headless delimitado; não inclui leitor de histórico nem aceite da tela. Requer: `INSTALL-BASE`.
- `JOB-HISTORY-READ`: Histórico somente leitura com limites, schemas 1/2, diagnóstico de arquivos inválidos e nenhuma autoridade de Apply herdada. Requer: `JOB-INSTALL-RECEIPTS`. Integração visual e reconciliação com descoberta real têm aceite separado.
- `INSTALL-PROMOTION`: Admissão independente de corpos preservados/composições, dependências e migração explícita de journal. Requer: `COMPOSE-01`, `DEPS-COHORT`.
- `INSTALL-RECOVERY-QA`: Qualificar interrupção nativa, armazenamento/permissões e restauração no artefato final. Requer: `INSTALL-BASE`.

Os estados mutáveis ficam somente no JSON. Um corte aceito prova apenas o escopo
da sua descrição/evidência, não todo o prompt nem artefatos posteriores.

## Trabalho solicitado

Recorte desta retomada: [PRESERVED_MANAGED_UPDATES](../../PRESERVED_MANAGED_UPDATES.md).
API developer de atualização individual, backup independente, journal exato,
retirement/restore intent e recuperação foram implementados. Só predecessor
preservado add-only não carregado; cadeias update-of-update e integração UI não
estão habilitadas. Evidência/limites ficam no ledger vinculado, não neste título.
Integração seguinte entregue: [PRESERVED_UPDATE_COORDINATOR](../../PRESERVED_UPDATE_COORDINATOR.md),
com revisão/consentimento para os dois pins, resultados/recuperação/recibos tipados,
worker e lease de encerramento existentes. Próximo **LB-04 / UI-PROMOTION /
PRESERVED_UPDATE_SCREEN**, consumindo essa API sem repetir o backend/coordenador
ou promover Apply público. Requires e aceites nativos continuam pendentes.

Escopo executado: **PRESERVED_MANAGED_UPDATES**. A
[preparação developer](../../PRESERVED_PREPARATION_WORKFLOW.md) agora liga original,
conversão/revalidação e admissão à revisão/instalação add-only existentes.
Desenvolver atualização somente de artefato preservado gerenciado e não carregado:
ownership e hashes exatos, aprovação nova, dependências/ABI, backup recuperável,
journal separado/versionado e retomada/restauração determinísticas por fronteira.
Recusar carregado/desconhecido, alias, journal sem contrato ou backup divergente.
Não reinterpretar V1/V2 simples ou recibos antigos, promover composição/coorte,
instalar automaticamente, adicionar bootstrap ou abrir jogo. Fixtures e falhas
headless primeiro; gates nativos/requires continuam pendentes. Primeiro alvo:
atualização individual `logic-preserving-v1`, sem prometer lote atômico.

Continuação anterior: [PRESERVED_ADD_ONLY_INSTALL](../../PRESERVED_ADD_ONLY_INSTALL.md)
implementa instalação add-only real, aprovação explícita, preflight fresco,
ownership e retomada/restauração com journal separado. V1/V2 e UI simples intactos.
Naquele checkpoint o desenvolvimento seguiu **LB-01 / PRODUCT-COMPOSED / PRESERVED_INSTALL_COORDINATOR**:
integrar worker/revisão/recibos/histórico por rota developer, não liberar Apply
público ou repetir a camada de disco. INSTALL-PROMOTION permanece PARTIAL para
admissão de composição/coorte, updates preservados e gates nativos/de promoção.

Continuação anterior: [JOURNAL_ADMISSION_BINDING](../../PRESERVED_ADMISSION_JOURNAL.md)
implementado com cópia privada e recuperação fresca; não instala, migra V1/V2
ou herda autorização de PASS. Próximo **PRESERVED_ADD_ONLY_INSTALL**: rota
developer add-only explícita, sessão/dependências/ownership/commit e recuperação,
sem promover UI pública/composição/coorte antes dos aceites próprios.

Continuação anterior 2026-09-13: [INDEPENDENT_LOGIC_READBACK_V1](../../PRESERVED_ARTIFACT_ADMISSION.md)
implementado e validado headless com o par real e negativos. API/CLI de inspeção
não concedem Apply e não alteram journals. Próximo recorte finito dentro de
INSTALL-PROMOTION: **JOURNAL_ADMISSION_BINDING**, pins exatos e recuperação
versionada com inspeção fresca. Não repetir o verificador nem promover perfis
antes dos gates de instalação, dependências e aceites próprios.

Prioridade headless autorizada em 2026-09-07: executar os recortes de recibos e
histórico sem abrir jogos. [Contrato e limites](../../JOB_INSTALL_RECEIPTS.md).
O [leitor delimitado](../../JOB_HISTORY_READ.md) não reidrata autorização de Apply.
Esses recortes não eliminam INSTALL-RECOVERY-QA nem habilitam novos perfis.

1. Antes de Apply revalide bytes/hash original/resultado, plano, bytecode/refs, adapters/ABI, dependências/lados/versões, IDs duplicados, identidade real e aliases/symlinks.
2. Implemente verificação independente por perfil; não remova as verificações do bootstrap para aceitar qualquer classe. Versione journal/metadados e teste leitura/recusa/migração segura de estados antigos.
3. Preserve instalação apenas de resultados aprovados. Nunca sobrescreva carregado/desconhecido. Mantenha backup recuperável de upgrade gerenciado não carregado.
4. Teste cada fronteira de escrita/force/rename/link/journal, lote parcial, falha de permissão, disco cheio simulado e quando possível real isolado, Windows no-share e interrupção em processo separado. Resultado é recuperação determinística, não atomicidade de lote.
5. Não permita atualização de dependência carregada ou de consumidores obrigatórios sem arquitetura offline validada; pre-discovery recovery precisa de prova e decisão própria.
6. Fallback literal: Aplicar e fechar — abra novamente pelo launcher. Sem mundo aberto, tokens persistidos ou encerramento antes de COMMITTED/RESTORED seguro.

## Aceite específico

- Compare inventário/hashes antes/depois de cada falha; recuperável não significa rollback automático nem durabilidade universal contra perda de energia.
- Restore não toca mundo nem arquivo alheio. Continuar não instala; fechar não recebe o rótulo reinício concluído.
- Gate por perfil inclui UI e nova descoberta; origem intacta sempre.

## Limites e parada

Restart automático não é obrigatório sem launcher validado; loaded updates, uninstall genérico e reparo pré-descoberta continuam fora até contrato próprio.

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

Risco: crítico: integridade e recuperação. Estimativa: Admissão por perfil e recuperação ampla precisam de recortes separados.

- [docs/TRANSACTIONAL_INSTALLATION.md](../../../docs/TRANSACTIONAL_INSTALLATION.md)
- [docs/TRANSACTIONAL_INSTALLATION_VALIDATION.md](../../../docs/TRANSACTIONAL_INSTALLATION_VALIDATION.md)
- [docs/ADD_ONLY_INSTALLATION.md](../../../docs/ADD_ONLY_INSTALLATION.md)
