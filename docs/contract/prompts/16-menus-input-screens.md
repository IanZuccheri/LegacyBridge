# LB-16 — Menus, inventários, telas, input e HUD legados

Número canônico: 16. Estado autoritativo: [status.json](../status.json), entrada `LB-16`.

## Prompt para executar

Trabalhe apenas no projeto existente LegacyBridge. Leia integralmente o
[contrato comum](../README.md), seu status atual e as referências abaixo antes de agir.
Execute um recorte verificável deste prompt, respeitando as dependências de cada corte;
não presuma que o pedido autoriza completar toda a família numa única mensagem.
Se este não for o cursor ativo, confirme a mudança de prioridade no contrato antes de implementar.

## Ponto de partida da revisão 2026-09-07

O botão de conversão é UI própria do LegacyBridge. Nenhuma GUI de outro mod passou a ser convertível por isso.

## Recortes e dependências

- `CLIENT-LOCAL`: Tela local/keymapping/HUD finito, input/foco e isolamento físico. Requer: `COMPOSE-01`, `LIFE-EVENTS`.
- `MENU-SLOTS`: Menu server-authoritative com slots, abertura/validade/sincronização e consumo. Requer: `BE-PERSIST`, `CAP-INVENTORY`, `NET-C2S`.
- `MENU-ANTIDUPE`: Shift-click/drag/concorrência, close/relog e dois jogadores. Requer: `MENU-SLOTS`, `CLIENT-LOCAL`.

Os estados mutáveis ficam somente no JSON. Um corte aceito prova apenas o escopo
da sua descrição/evidência, não todo o prompt nem artefatos posteriores.

## Trabalho solicitado

1. Separe tela puramente cliente de menu com estado autoritativo servidor. Comece com um caso do corpus e não copie o screen inteiro via stub.
2. Audite Container/Menu/Screen, factories, tipos, identificadores, slot layout, player inventory, dados sincronizados e abertura/validade/distância nas fontes reais.
3. Menus: click normal, quick-move, drag, hotbar swap, double-click, cursor stack, retirada ao fechar e falha de abertura. O cliente não decide conteúdo/quantidade.
4. Client-only: keybindings, remapeamento/conflito, foco, teclado/mouse, escala/redimensionamento/narração e hooks de HUD/screen. Preserve vanilla em telas não afetadas.
5. Controlling com ATs exige provar rota pública equivalente em LB-20; não remover os ATs e assumir que o menu segue funcional.
6. Teste dois usuários abrindo o mesmo host, estado stale, clique inválido, distância, dimensão, disconnect e save/reload; servidor dedicado não resolve classes cliente.

## Aceite específico

- Contagem/NBT de itens conservados com input concorrente e retransmissão; sem abrir inventário de terceiro ou acessar chunk indevido.
- Screenshots reais + ações de teclado/mouse e validação server-side independentes. Aparência por si só não prova anti-dupe.

## Limites e parada

Sem converter GUI por screenshot, sem click automation substituir comportamento do mod, sem acesso privado liberado por patch de plataforma.

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

Risco: crítico em containers; alto em UX/lados. Estimativa: Controlling B04: 20–35 dias-pessoa condicionais; menus server-side são outro recorte.

- [docs/P4_UI_API_BASELINES.md](../../../docs/P4_UI_API_BASELINES.md)
- [docs/NETWORK_API_BASELINES.md](../../../docs/NETWORK_API_BASELINES.md)
- [corpus/BACKLOG.md](../../../corpus/BACKLOG.md)

