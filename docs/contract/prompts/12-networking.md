# LB-12 — Networking e contratos de multiplayer

Número canônico: 12. Estado autoritativo: [status.json](../status.json), entrada `LB-12`.

## Prompt para executar

Trabalhe apenas no projeto existente LegacyBridge. Leia integralmente o
[contrato comum](../README.md), seu status atual e as referências abaixo antes de agir.
Execute um recorte verificável deste prompt, respeitando as dependências de cada corte;
não presuma que o pedido autoriza completar toda a família numa única mensagem.
Se este não for o cursor ativo, confirme a mudança de prioridade no contrato antes de implementar.

## Ponto de partida da revisão 2026-09-07

network-snapshot-v1 preserva dois corpos exatos S2C. NET-QA-01 passou em fixture com dois clientes. CLIENT_PROTOCOL não tem oracle normal aprovado; passes instrumentados não corrigiram a falha.

## Recortes e dependências

- `NET-QA-01`: Cenário finito de dois clientes e servidor registrado; não equivalência de origem nem networking geral. Requer: `LOGIC-BASE`.
- `NET-QA-02-HANDSHAKE`: Resolver CLIENT_PROTOCOL no modo normal e executar SERVER_ABSENT/SERVER_PROTOCOL. Requer: `NET-QA-01`.
- `NET-QA-02-COMPARISON`: Comparar fixture original e convertido em ambientes isolados. Requer: `NET-QA-02-HANDSHAKE`.
- `NET-QA-03`: Sessões/epochs, pending-work, saturação, reconexão e fechamento. Requer: `NET-QA-02-COMPARISON`.
- `NET-C2S`: Primeiro contrato C2S autorizado; expandir canais/codecs/destinatários somente por fixture. Requer: `NET-QA-03`, `COMPOSE-01`, `LIFE-EVENTS`.

Os estados mutáveis ficam somente no JSON. Um corte aceito prova apenas o escopo
da sua descrição/evidência, não todo o prompt nem artefatos posteriores.

## Trabalho solicitado

1. Leia o último ledger e observação de clear-return, não a tabela antiga de CLIENT_ABSENT. Este último passou DEFAULT; CLIENT_PROTOCOL continua aberto.
2. Antes de novo run, formule hipótese discriminante e instrumento verificado nas fontes/bytecode fixados. Não repetir breakpoints que tornam a corrida invisível; não forçar read/flush, sleeps artificiais ou flexibilizar oracle.
3. Se a solução demandar plataforma modificada/versão diferente, pare e peça decisão; o contrato atual não autoriza isso. Desconexão ou timeout não comprovam recusa por protocolo.
4. Depois de handshake, execute comparação original/alvo e NET-QA-03, sem reclassificar networking geral a partir de um snapshot.
5. Para C2S concreto, preserve canal/versão/IDs/codec/direção/destinatário e enqueue/contexto. Valide tamanho, ID, rate/ordem/duplicata quando aplicável, sender real, permissão, distância, mundo, menu/estado e disponibilidade do chunk.
6. Não carregar/gerar chunks por coordenadas enviadas pelo cliente nem mutar mundo na thread de rede. Se o original é inseguro e equivalência segura não existe, bloqueie ou proponha alteração semântica explícita para revisão.
7. Teste servidor dedicado e dois clientes, versões/peer ausente, reconexão, mensagens inválidas, antigas/duplicadas/fora de ordem e execução na thread certa.

## Aceite específico

- Aceite normal não instrumentado e efeito observado esperado, sem handlers indevidos; negatives não contam como PASS por queda genérica.
- Documente limites de protocolo e estados; pacote desconhecido é recusado/relatado, nunca silenciosamente ignorado.

## Limites e parada

Não conecta cliente 1.16.5 com servidor 1.20.1. Ambos rodam no destino; sem patch em Forge/Netty/Minecraft.

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

Risco: crítico: autoridade, thread e protocolo. Estimativa: Investigar falha antes de estimar expansão; sem prazo garantido para hipótese de transporte.

- [docs/NETWORK_API_BASELINES.md](../../../docs/NETWORK_API_BASELINES.md)
- [docs/NETWORK_HANDSHAKE_CLEAR_RETURN.md](../../../docs/NETWORK_HANDSHAKE_CLEAR_RETURN.md)
- [docs/NETWORK_HANDSHAKE_PLATFORM_CONTROL.md](../../../docs/NETWORK_HANDSHAKE_PLATFORM_CONTROL.md)
- [docs/NETWORK_RUNTIME_TEST_PLAN.md](../../../docs/NETWORK_RUNTIME_TEST_PLAN.md)
- [docs/NETWORK_SNAPSHOT_PROFILE.md](../../../docs/NETWORK_SNAPSHOT_PROFILE.md)

