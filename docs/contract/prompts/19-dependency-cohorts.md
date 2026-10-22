# LB-19 — Dependências, bibliotecas e composição entre mods

Número canônico: 19. Estado autoritativo: [status.json](../status.json), entrada `LB-19`.

## Prompt para executar

Trabalhe apenas no projeto existente LegacyBridge. Leia integralmente o
[contrato comum](../README.md), seu status atual e as referências abaixo antes de agir.
Execute um recorte verificável deste prompt, respeitando as dependências de cada corte;
não presuma que o pedido autoriza completar toda a família numa única mensagem.
Se este não for o cursor ativo, confirme a mudança de prioridade no contrato antes de implementar.

## Ponto de partida da revisão 2026-09-07

Existe grafo de metadados com lados/versões/ordem, mas conversão é mod único. Não há coorte executável fechada de dependências convertidas nem suporte genérico JiJ.

## Recortes e dependências

- `DEPS-METADATA`: Verificador metadata-only documentado; não prova APIs/binários externos. Requer: `GOV-01`.
- `DEPS-COHORT`: Biblioteca+mod consumidor autorais, inventário de API/ownership, fechamento e versões reais. Requer: `COMPOSE-01`, `DEPS-METADATA`.
- `DEPS-LIBRARIES`: Bibliotecas/JiJ/services/relocation e integrações opcionais por coorte autorizada. Requer: `DEPS-COHORT`, `DATA-EXPAND`.

Os estados mutáveis ficam somente no JSON. Um corte aceito prova apenas o escopo
da sua descrição/evidência, não todo o prompt nem artefatos posteriores.

## Trabalho solicitado

Checkpoint de implementação 2026-09-12: [FINITE_TARGET_PAIR](../../DEPENDENCY_COHORT_TARGET.md)
acrescenta a SOURCE_COHORT_INSPECTION_V1 a montagem real/reabertura da dupla
privada de destino, classes byte-idênticas, adapters metadata exatos e validação
conjunta de dependências/ASM/linkage por JAR. Não publica/instala candidatos;
DEPS-COHORT permanece PARTIAL pela comparação nativa adiada, não dispensada.
Próximo desenvolvimento headless no cursor: **LB-01 / COHORT_TARGET_PREVIEW**,
ligar o plano privado ao produto. Não retornar automaticamente à expansão de
harnesses de lançamento nem habilitar bibliotecas arbitrárias.

1. Execute DEPS-COHORT cedo, logo após COMPOSE-01, apesar de seu número 19. Número identifica área; dependências/cursor definem execução.
2. Fixture mínimo: biblioteca obrigatória e consumidor com chamada observável, variantes de versão/lado/opcional/ordem/ciclo e falta de método. Não remover uma dependência para conseguir carregar.
3. Planeje coorte inteira antes de converter; declare ownership por artefato/pacote, APIs origem/destino e se a dependência será convertida ou usará port nativo realmente compatível.
4. Preserve lados/lifecycle/ordem aplicável e modId/registry IDs; versão textual ou nome idêntico não implica ABI/semântica equivalente entre versões.
5. Audite libs embutidas/JiJ/shaded, duplicatas, services, classloader/módulos e reflexão concreta. Só transforme classe de dependência em operação própria autorizada, nunca como se pertencesse ao mod selecionado.
6. Integrações opcionais: presente/ausente e versões rejeitadas, carga lazy no lado certo; APIs de mods externos como JEI/Curios/GeckoLib não são equivalentes presumidos.
7. Conecte admissão e journal em LB-05, com atualização segura de consumidores e recusa enquanto descoberta/recuperação não forem comprovadas.

## Aceite específico

- DEPS-COHORT exige biblioteca + consumidor convertidos e comparados em ambientes isolados de origem/destino, incluindo negativos; o grafo mostra todas as arestas obrigatórias e seus contratos. Não exige ainda Apply pelo instalador público atual.
- A instalação pública final dessa coorte é aceite posterior de INSTALL-PROMOTION e PUBLIC-PROMOTED-E2E. Assim a promoção pode depender da coorte comprovada sem um ciclo de aceites.
- Coorte com qualquer contrato desconhecido falha sem publicar subconjunto aparentando instalação completa.

## Limites e parada

Não baixar dependências silenciosamente, reescrever o arquivo original nem mexer em classes de plataforma.

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

Risco: crítico: fechamento, linkage e descoberta. Estimativa: Coorte mínima antes de estimar bibliotecas populares; grafo metadata-only não é compatibilidade.

- [docs/DEPENDENCY_GRAPH.md](../../../docs/DEPENDENCY_GRAPH.md)
- [docs/PORTABLE_MAPPING_DATA.md](../../../docs/PORTABLE_MAPPING_DATA.md)
- [docs/PACKAGING_ARCHITECTURE.md](../../../docs/PACKAGING_ARCHITECTURE.md)
- [docs/TRANSACTIONAL_INSTALLATION.md](../../../docs/TRANSACTIONAL_INSTALLATION.md)
