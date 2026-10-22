# LB-00 — Contrato, baselines e rastreabilidade

Número canônico: 0. Estado autoritativo: [status.json](../status.json), entrada `LB-00`.

## Prompt para executar

Trabalhe apenas no projeto existente LegacyBridge. Leia integralmente o
[contrato comum](../README.md), seu status atual e as referências abaixo antes de agir.
Execute um recorte verificável deste prompt, respeitando as dependências de cada corte;
não presuma que o pedido autoriza completar toda a família numa única mensagem.
Se este não for o cursor ativo, confirme a mudança de prioridade no contrato antes de implementar.

## Ponto de partida da revisão 2026-09-07

Esta consolidação cria uma numeração canônica nova LB-00–LB-22. Não afirma recuperar o texto perdido de todos os prompts antigos. Nenhuma regra de conversão é habilitada por este contrato.

## Recortes e dependências

- `GOV-01`: Versionar o contrato, auditar o estado, validar IDs/cursor/dependências e preservar os ledgers. Requer: nenhum.

Os estados mutáveis ficam somente no JSON. Um corte aceito prova apenas o escopo
da sua descrição/evidência, não todo o prompt nem artefatos posteriores.

## Trabalho solicitado

1. Leia README.md, status.json e o prompt ativo deste diretório; depois PROJECT_STATUS, arquitetura, implementação e ledger específico. A aba do editor não define o projeto.
2. Antes de qualquer mudança, confronte evidência com código/artefato. Atualize o corte com resultados separados de build, ASM, linkage, inicialização, comportamento e multiplayer.
3. Não renumere IDs existentes. Uma revisão de escopo deve listar inclusões, exclusões, motivo e aprovação; adicione subcortes em vez de declarar uma família inteira pronta.

## Aceite específico

- O verificador rejeita IDs repetidos, arquivos ausentes, ciclos, cursor inválido e ACCEPTED sem evidência. O build executa esse verificador sem iniciar jogos.
- Um prompt grande pode consumir várias retomadas. Atualizar o contrato nunca altera por si só os perfis aceitos pelo produto.

## Limites e parada

Não contar esta governança como progresso de conversão de mods.

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

Risco: processual. Estimativa: Manutenção contínua; não é trabalho de compatibilidade concluído.

- [docs/PROJECT_STATUS.md](../../../docs/PROJECT_STATUS.md)
- [docs/PROMPT_CONTINUATION.md](../../../docs/PROMPT_CONTINUATION.md)

