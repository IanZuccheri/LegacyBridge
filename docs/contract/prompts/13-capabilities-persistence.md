# LB-13 — Capabilities e persistência por instância

Número canônico: 13. Estado autoritativo: [status.json](../status.json), entrada `LB-13`.

## Prompt para executar

Trabalhe apenas no projeto existente LegacyBridge. Leia integralmente o
[contrato comum](../README.md), seu status atual e as referências abaixo antes de agir.
Execute um recorte verificável deste prompt, respeitando as dependências de cada corte;
não presuma que o pedido autoriza completar toda a família numa única mensagem.
Se este não for o cursor ativo, confirme a mudança de prioridade no contrato antes de implementar.

## Ponto de partida da revisão 2026-09-07

Há auditoria/recusas, mas nenhum provider ou adapter capability implementado. Diretório e package-info não são implementação. A capability do catálogo de adapters não é Forge Capability.

## Recortes e dependências

- `CAP-ENERGY`: Primeiro provider de energia por ItemStack, sem máquina/mundo. Requer: `NET-QA-03`, `COMPOSE-01`.
- `CAP-INVENTORY`: Inventário pequeno por instância/face, simulação, serialização e invalidação. Requer: `CAP-ENERGY`.
- `CAP-FLUID`: Tanque finito, tipo/quantidade/NBT, simulação e persistência. Requer: `CAP-INVENTORY`.
- `CAP-PLAYER`: Dados por jogador, clone/morte/respawn/dimensão/relog; sync como corte separado. Requer: `CAP-ENERGY`, `LIFE-EVENTS`.
- `PERSIST-SAVEDDATA`: SavedData por mundo/nível, dirty-state e política explícita de serialização/reabertura. Requer: `CAP-PLAYER`.

Os estados mutáveis ficam somente no JSON. Um corte aceito prova apenas o escopo
da sua descrição/evidência, não todo o prompt nem artefatos posteriores.

## Trabalho solicitado

1. Audite fonte/target reais de registro, obtenção, provider, attachment, LazyOptional, invalidation e serialização. Cada implementação documenta Legacy API/Target API/Transformation/Behavior differences/Known limitations.
2. Comece com energia por stack; teste consulta/receber/extrair/simulate, cópia/split/save/load/invalidate. Nunca reutilize um estado mutável entre stacks.
3. Depois inventário por face/instância e tanque de fluidos; valide limites, NBT/tipo, simulação sem mutação, reabertura e conservação.
4. Dados de jogador: identidade/attachment, morte e clone, respawn, retorno do End versus morte, dimensão, desconexão, save/reload e invalidação. Não copie estado duas vezes.
5. Sync é um contrato dependente de networking, não consequência da persistência. Teste observadores novos/reconexão sem compartilhar estado entre jogadores.
6. SavedData em recorte próprio: escopo por mundo/nível, marcação de mudança e crash/save/reload; mantenha formatos originais quando semanticamente possível e documente schema convertido.

## Aceite específico

- Compare original/convertido em ambientes isolados; save/load, dimensão, morte/clone, respawn, desconexão, invalidação e reabertura conforme o host.
- Negativos: referência LazyOptional inválida, provider ausente, estado compartilhado, face errada, simulação mutante e NBT corrompido.

## Limites e parada

Sem migração de mundo 1.16.5 e sem afirmar suporte de máquina/BlockEntity apenas com um provider.

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

Risco: crítico: ciclo de vida, perda e duplicação. Estimativa: Energia, inventário, fluido e jogador separados; estimar após auditoria de cada host.

- [docs/COMPAT_LAYER.md](../../../docs/COMPAT_LAYER.md)
- [docs/COMPAT_API_BASELINES.md](../../../docs/COMPAT_API_BASELINES.md)
- [docs/PROJECT_STATUS.md](../../../docs/PROJECT_STATUS.md)

