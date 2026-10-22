# LB-04 — Interface nativa e promoção segura de perfis

Número canônico: 4. Estado autoritativo: [status.json](../status.json), entrada `LB-04`.

## Prompt para executar

Trabalhe apenas no projeto existente LegacyBridge. Leia integralmente o
[contrato comum](../README.md), seu status atual e as referências abaixo antes de agir.
Execute um recorte verificável deste prompt, respeitando as dependências de cada corte;
não presuma que o pedido autoriza completar toda a família numa única mensagem.
Se este não for o cursor ativo, confirme a mudança de prioridade no contrato antes de implementar.

## Ponto de partida da revisão 2026-09-07

A tela funciona na implementação e possui evidência histórica. No novo hash com dados embutidos houve inicialização, mas não nova conversão/aplicação pela tela; o Windows recusou foco. Este é o cursor atual.

Continuação de 2026-09-07: esse ponto de partida é histórico. O
[aceite nativo finito](../../EMBEDDED_SIMPLE_NATIVE_UI_VALIDATION.md) comprovou
campo vazio, continuar, Apply/close, nova descoberta e comportamento do fixture
no mesmo hash. `status.json` agora aponta a LB-06 para negativos/qualificação;
UI-PROMOTION e a matriz ampla de interface não foram aceitos por esse resultado.

## Recortes e dependências

- `DATA-SIMPLE-01-NATIVE_UI`: Converter com campo de mappings vazio pela interface real; continuar, aprovar aplicação, fechar e confirmar nova descoberta. Requer: `DATA-SIMPLE-01`, `PACKAGE-BASE`.
- `UI-PROMOTION`: Expor só combinações com admissão implementada e resultados persistentes/reabríveis. Requer: `PRODUCT-COMPOSED`, `INSTALL-PROMOTION`, `PACKAGE-PROMOTION`.
- `UI-HISTORY-READ`: Exibir recibos persistentes, busca, detalhes paginados e diagnósticos, sem reidratar Apply nem inferir ativação. Requer: `JOB-HISTORY-READ`, `DATA-SIMPLE-01-NATIVE_UI`. Implementação headless documentada em [UI_HISTORY](../../UI_HISTORY.md); aceite nativo permanece separado/PARTIAL. Não expõe novos perfis.
- `UI-SAVED-REPORTS`: Reabrir relatórios associados a novos jobs por índice schema 1 e hashes, com leitura limitada e páginas de texto inerte. Requer: `JOB-HISTORY-READ`, `UI-HISTORY-READ`. [Contrato finito](../../SAVED_REPORTS.md); não reidrata Apply nem reconstrói vínculos antigos. Aceite nativo separado.

Os estados mutáveis ficam somente no JSON. Um corte aceito prova apenas o escopo
da sua descrição/evidência, não todo o prompt nem artefatos posteriores.

## Trabalho solicitado

Entrega headless 2026-09-19: **PRESERVED_UPDATE_SCREEN**, documentada em
[interface de updates](../../PRESERVED_UPDATE_SCREEN.md), liga seleção dos dois
lados, versões/hashes, confirmação própria, recuperação e histórico ao mesmo
host developer. Não habilita Apply público nem satisfaz QA nativa/requires.
O desenvolvimento segue **LB-08 / LINKAGE-GENERAL / OWNED_CONSTRUCTOR_DELEGATION**,
mantendo UI-PROMOTION PARTIAL. Não repetir backend/coordenador/tela ou abrir jogo.

Escopo do recorte entregue **PRESERVED_UPDATE_SCREEN**, após o
[coordenador de updates](../../PRESERVED_UPDATE_COORDINATOR.md) implementado.
Estender a interface developer opt-in com seleção explícita do antecessor/plan pin
e da admissão nova/dados, comparação observada de versões/hashes, confirmação
própria de update, recuperação RESUME/RESTORE e histórico somente leitura.
Reutilizar host/snapshot de sessão, guardas de navegação/fechamento e worker
existentes; nenhuma leitura de disco no render, executor novo ou instalador
duplicado. Não confundir o operationId retornado com reviewId para consentimento.
Não derivar autorização do histórico, ativar hot-load, atualizar carregados,
promover Convert/Apply público ou inventar reinício. Integrar en_us/pt_br,
teclado/foco/resize e negativos headless; aceite visual/runtime fica NOT_RUN
até nova autorização para abrir Minecraft. Não repetir a preparação/add-only.
UI-PROMOTION e seus requires continuam PARTIAL/não satisfeitos para promoção.

Entrega headless anterior de 2026-09-19: [PRESERVED_DEVELOPER_SCREEN](../../PRESERVED_DEVELOPER_SCREEN.md).
Host/tela implementados com opt-in explícito, seleção limitada, revisão fresca,
consentimento de uso único, sessão thread-safe, recuperação/histórico inerte,
guardas de navegação e fechamento manual, en_us/pt_br. Não é promoção pública
nem aceite nativo. A tela começa em uma admissão já preparada; não converte ou
prepara originais automaticamente. Evidência/build/hash estão no ledger ligado.
Naquele checkpoint, próximo desenvolvimento: **LB-01 / PRODUCT-COMPOSED / PRESERVED_PREPARATION_WORKFLOW**,
para ligar as operações existentes de preparação ao produto developer; não
repetir o host/tela ou abrir Minecraft para avançar o cursor de implementação.

Próximo recorte headless após LB-01 (2026-09-13):
**PRESERVED_DEVELOPER_SCREEN** dentro de UI-PROMOTION. Consumir a
[API de coordenação preservadora](../../PRESERVED_INSTALL_COORDINATOR.md) pronta
num host/tela developer opt-in separado do caminho público. Selecionar admissão
e hashes/dados explicitamente, revisar, obter confirmação e mostrar resultado e
histórico inerte. Capturar o conjunto carregado/mundo em APIs reais do cliente,
com snapshot não bloqueante/thread-safe, sem consultar Minecraft no worker.
Garantir navegação/fechamento próprio e impedir entrada em mundo pela UI durante
operação aceita, mantendo os limites explícitos para QuickPlay/OS/direct APIs.
Sem CLI, novos workers, duplicar o instalador ou reidratar consentimento do JSON.
Não abrir Minecraft/testar telas nesta política headless; implementar en_us/pt_br,
foco/teclado/resize e checks estáticos, deixando o aceite visual/runtime NOT_RUN.
UI-PROMOTION não fica aceito por esse opt-in: promoção pública ainda depende de
PRODUCT-COMPOSED, INSTALL-PROMOTION, PACKAGE-PROMOTION e qualificação nativa.
Os passos de execução abaixo só podem ocorrer depois de nova autorização.

1. Retome o cliente descartável documentado, com opções de QA corrigidas; só inicie quando a execução estiver autorizada. Peça foco ao usuário se a automação for recusada. Não repetir launches sem hipótese/ação útil.
2. Com somente LegacyBridge em mods, coloque fixture genuíno em input; identifique versão pelos dados limitados, não pelo nome. Campo de mappings vazio usa o provider finito.
3. Converta pela tela, capture screenshot real, consulte CompatibilityReport/TransformationLog. Confirme que continuar deixa o fixture fora do conjunto carregado.
4. Peça/aprove explicitamente Apply; confirme staging, journal e fechamento seguro. Na nova inicialização confirme descoberta, item/bloco/recipe/assets/translations; guarde originais/hash.
5. Teste inventário corrompido, duplicados, dependência ausente, seleção múltipla, pesquisa, fila cheia, cancelamento, fechar/reabrir tela, teclado/foco, escala/redimensionamento e en_us/pt_br.
6. Para perfis futuros, seleção/diagnóstico devem refletir a admissão real do instalador, sem apenas adicionar itens a um dropdown. Defina retenção/exportação recuperável de jobs/reports.

## Aceite específico

- ANALYZED, BLOCKED, CONVERTING, CONVERTED, FAILED, CANCELLED e PENDING_RESTART distintos. CONVERTED nunca significa comportamento comprovado.
- Sem screenshots falsas, invocação direta do controller no lugar da UI, ou testemunho histórico transferido para JAR novo.
- Fechar a tela não deixa saída parcial nem permite encerrar uma instalação aceita antes do estado seguro.

## Limites e parada

Esta UI pertence ao LegacyBridge; não constitui suporte a converter GUIs de outros mods.

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

Risco: alto na distinção conversão/ativação. Estimativa: Gate atual depende de foco/execução assistida; duração não é prazo garantido.

- [docs/CLIENT_MENU_UI.md](../../../docs/CLIENT_MENU_UI.md)
- [docs/P4_UI_API_BASELINES.md](../../../docs/P4_UI_API_BASELINES.md)
- [docs/NATIVE_UI_VALIDATION.md](../../../docs/NATIVE_UI_VALIDATION.md)
- [docs/EMBEDDED_SIMPLE_CONTENT_DATA.md](../../../docs/EMBEDDED_SIMPLE_CONTENT_DATA.md)
