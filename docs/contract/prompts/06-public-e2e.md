# LB-06 — Validação integral do artefato público

Número canônico: 6. Estado autoritativo: [status.json](../status.json), entrada `LB-06`.

## Prompt para executar

Trabalhe apenas no projeto existente LegacyBridge. Leia integralmente o
[contrato comum](../README.md), seu status atual e as referências abaixo antes de agir.
Execute um recorte verificável deste prompt, respeitando as dependências de cada corte;
não presuma que o pedido autoriza completar toda a família numa única mensagem.
Se este não for o cursor ativo, confirme a mudança de prioridade no contrato antes de implementar.

## Ponto de partida da revisão 2026-09-07

Há E2E histórico, paridade estática e smoke dedicado recente. O novo artefato não recebeu aceite de conversão/Apply/crafting/assets/translations pela interface.

Atualização após a continuação nativa de 2026-09-07: o
[positivo do mesmo hash](../../EMBEDDED_SIMPLE_NATIVE_UI_VALIDATION.md) agora
foi aceito por LB-04. Reutilizar essa cadeia e executar os negativos restantes;
o parágrafo acima preserva o ponto de partida, não descreve o cursor atualizado.

Continuação headless: [consolidação do hash atual](../../PUBLIC_CURRENT_QUALIFICATION.md)
e índice verificável juntam positivo, negativos finitos e servidor/cacheless.
Não reexecutam os jogos nem encerram as pendências nativas. A restrição atual
proíbe tela/jogos até nova autorização explícita; o cursor continua neste corte.

## Recortes e dependências

- `PUBLIC-CURRENT-E2E`: Consolidar o fluxo de dez passos no hash atual e negativos, sem substituir o visual por harness. Requer: `DATA-SIMPLE-01-NATIVE_UI`.
- `PUBLIC-PROMOTED-E2E`: Repetir fluxo completo para cada perfil promovido e combinação selecionada. Requer: `UI-PROMOTION`, `INSTALL-PROMOTION`, `PACKAGE-PROMOTION`.

Os estados mutáveis ficam somente no JSON. Um corte aceito prova apenas o escopo
da sua descrição/evidência, não todo o prompt nem artefatos posteriores.

## Trabalho solicitado

Reutilize evidência válida já produzida por LB-04 somente quando artefato, entrada,
ambiente relevante e oracle forem os mesmos. Consolide a cadeia e execute os
gates faltantes; não repetir toda a UI inalterada apenas por mudar o número do prompt.

1. Execute a sequência: só JAR público → menu → original em input → inventário UI → converter UI → continuar sem ativação → Apply/close → nova descoberta → item/bloco/recipe/assets/traduções → original intacto.
2. Separe evidências de build/test, inspeção de JAR, ASM/linkage, inicialização, ações reais, multiplayer e recuperação. Nada de chamar matches/assemble e declarar uma máquina inteira funcionando.
3. Execute corrompido, contrato não suportado, dependência ausente, modId duplicado, cancelamento, falha de escrita e interrupção/recuperação. Servidor recebe o mesmo artefato.
4. Prepare instalação comum antes de desconectar rede; se offline for reivindicado, efetivamente execute offline. Ausência de caches deve ser comprovada sem fontes/classes de desenvolvimento.
5. Registre hashes, versões, contexto, logs, capturas reais e esperado/observado por gate. Documente dependência de foco/memória/autorização em vez de inventar PASS.

## Aceite específico

- Cada observação aponta ao hash/coorte exatos e resultado por dimensão. Um erro mantém o gate aberto mesmo que o jogo alcance o menu.
- Nenhum upload ao CurseForge. Beta enquanto houver gates obrigatórios pendentes.

## Limites e parada

Não repetir smokes inalterados para acumular testes nem lançar clientes não autorizados.

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

Risco: alto: falsos positivos de QA. Estimativa: Campanha por hash/coorte; não herdar prazos ou PASS de um hash anterior.

- [docs/PUBLIC_ONLY_END_TO_END_VALIDATION.md](../../../docs/PUBLIC_ONLY_END_TO_END_VALIDATION.md)
- [docs/PRODUCTION_CLIENT_LAUNCH.md](../../../docs/PRODUCTION_CLIENT_LAUNCH.md)
- [docs/PLAYER_INSTALLATION.md](../../../docs/PLAYER_INSTALLATION.md)
