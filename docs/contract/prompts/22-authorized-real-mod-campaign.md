# LB-22 — Campanha final autorizada com mods reais e fechamento

Número canônico: 22. Estado autoritativo: [status.json](../status.json), entrada `LB-22`.

## Prompt para executar

Trabalhe apenas no projeto existente LegacyBridge. Leia integralmente o
[contrato comum](../README.md), seu status atual e as referências abaixo antes de agir.
Execute um recorte verificável deste prompt, respeitando as dependências de cada corte;
não presuma que o pedido autoriza completar toda a família numa única mensagem.
Se este não for o cursor ativo, confirme a mudança de prioridade no contrato antes de implementar.

## Ponto de partida da revisão 2026-09-07

Aquisição nova foi adiada pelo usuário até encerrar o escopo planejado e seus gates de qualidade. Não há autorização para redistribuir privados/publicar. Os três releases existentes não são três ports.

## Recortes e dependências

- `REAL-INTAKE`: Selecionar e obter releases autorizados 1.16.5 sem port 1.20.1 localizado, com verificação datada. Requer: `RELEASE-QUAL`, `CORPUS-PRIORITY`.
- `REAL-BEHAVIOR`: Conversão e comparação por release/coorte, cliente/servidor/comportamento/MP e bloqueios transparentes. Requer: `REAL-INTAKE`.
- `REAL-CLOSEOUT`: Relatório final de cobertura da amostra e plano de próxima versão, sem publicação automática. Requer: `REAL-BEHAVIOR`.

Os estados mutáveis ficam somente no JSON. Um corte aceito prova apenas o escopo
da sua descrição/evidência, não todo o prompt nem artefatos posteriores.

## Trabalho solicitado

1. Antes de qualquer download, comprove gates/escopo concluídos ou revisão explícita aprovada. Não interpretar número de mensagens repetidas como avanço de etapas.
2. Defina coorte representativa por famílias, dependências e uso real. Investigue fontes oficiais de autor/publicação e direitos para cada arquivo exato; não usar espelhos não autorizados.
3. Verifique ausência de port 1.20.1 em fontes consultadas com data. Não prometa ausência universal: fork não oficial, arquivos arquivados e nomes diferentes são limitações de busca.
4. Baixe somente quando permitido, registre hash/loader/versões/deps e proveniência em snapshot novo. Direito de teste local não equivale a redistribuir original, convertido ou metadados privados.
5. Primeiro análise/plano estáticos; só resultados inteiramente admitidos entram em QA runtime. Original autorizado executa apenas no ambiente isolado de referência, nunca no analyzer/converter.
6. Compare origem/destino em cliente/servidor, funções relevantes, persistence/reload, dois jogadores/relog/dimensão/anti-dupe/soak conforme contrato. Determine se port nativo de dependência realmente satisfaz o consumidor.
7. Quando um mod falhar, mantenha o resultado bloqueado/failed e backlog com toda a cadeia de bloqueios. Novo escopo requer revisão versionada, não enfraquecer regras para produzir uma demonstração.
8. Entregue números por release exato e por estágio, limitações e próximos recortes. Publicação só após autorização específica do usuário e direitos de distribuição.

## Aceite específico

- Nenhum original alterado; nenhum arquivo privado redistribuído; cada resultado rastreável a bytes/dados/regras/artefato.
- Compatibilidade observada da amostra não é probabilidade de funcionar em todo modpack nem promessa quase universal.

## Limites e parada

Não incluir automaticamente outras versões/loader, migração de saves, runtime Java8 antigo ou patches de plataforma.

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

Risco: alto: direitos, seleção e expectativa de cobertura. Estimativa: B07 indica 2–4 dias-pessoa por intake; port completo depende dos bloqueios e não cabe nessa estimativa.

- [corpus/README.md](../../../corpus/README.md)
- [corpus/BACKLOG.md](../../../corpus/BACKLOG.md)
- [docs/PACKAGING_RIGHTS.md](../../../docs/PACKAGING_RIGHTS.md)
- [docs/PROJECT_STATUS.md](../../../docs/PROJECT_STATUS.md)

