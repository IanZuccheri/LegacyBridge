# LegacyBridge em português

O LegacyBridge é um projeto gratuito e de código aberto que busca ajudar a
portar mods do **Minecraft Forge 1.16.5 para Forge 1.20.1**. A ideia é converter
cada vez mais padrões reais com regras verificadas e adaptações de comportamento,
sem fingir que trocar nomes de classes resolve toda a migração.

## O que ele entrega hoje

O JAR do LegacyBridge reúne motor, runtime e interface. No cliente, o botão
**Converter mods** abre a análise dos originais em
`<gameDir>/legacybridge/input`. O processo lê o bytecode sem executar as
classes originais e apresenta resultados, bloqueios e relatórios técnicos.

O perfil público `simple-content-v1` converte um padrão limitado de registro:
itens comuns, blocos simples de STONE e seus itens, propriedades básicas,
receitas shaped/shapeless, tags restritas, traduções, texturas PNG estáticas,
modelos/blockstates simples e aliases de sons vanilla selecionados.
Os dados necessários a esse perfil já estão incluídos; o campo de mappings
pode ficar vazio. Não é necessário preparar um pacote externo para esse fluxo.

Mesmo um mod pequeno pode ser recusado se seu código ou recursos não seguirem
o padrão aceito. O perfil substitui um grafo limitado de registros por um
bootstrap no destino; não preserva lógica arbitrária do mod.

Os resultados ficam em `staging`, separados dos originais. **Aplicar** exige
aprovação e prepara os arquivos para a próxima inicialização. Continuar
convertendo não altera os mods carregados. **Aplicar e fechar — abra novamente
pelo launcher** fecha após a operação segura; não é um reinício automático.
O mod convertido continua sendo outro JAR e depende do LegacyBridge no destino.

## O que ainda não entrega

Não há conversão universal, suporte geral a mods famosos/grandes, migração de
mundos antigos ou conexão entre cliente 1.16.5 e servidor 1.20.1.

No perfil público, continuam fora do escopo ferramentas e armaduras, subclasses
com comportamento, entidades, TileEntity/BlockEntity, worldgen, dimensões,
networking, capabilities, GUIs/renderização customizadas, shaders, mixins,
coremods, reflexão, eventos arbitrários, dependências gerais, serializers
customizados, loot tables, texturas animadas e áudio próprio.

Existem cinco perfis experimentais de desenvolvimento e vários recortes
estáticos mais avançados. Eles não equivalem a funcionalidades liberadas no
botão Converter/Aplicar. Dependem de dados locais explícitos e têm limites
documentados no [contrato](docs/contract/README.md).

## Estado e requisitos

O projeto continua **experimental/Beta**. Origem de referência: Minecraft
1.16.5 / Forge 36.2.42. Destino fixado: Minecraft **1.20.1 / Forge 47.4.23 /
Java 17**. Outras versões, Fabric e NeoForge não estão certificados.

Os testes históricos demonstram o fluxo e comportamentos de fixtures em
artefatos específicos. O candidato atual não recebeu uma nova qualificação
nativa completa de cliente, servidor e gameplay. Build aprovado, bytecode
válido, JAR convertido e comportamento correto são resultados diferentes.
Não há uma porcentagem comprovada de mods compatíveis.

Use uma instalação descartável. Originais ficam em `legacybridge/input`,
não em `mods`; mantenha os journals e backups de `legacybridge/transactions`.
Não comece os testes em um mundo importante.

## Como ajudar

- Criar fixtures pequenas e originais para um comportamento ainda não coberto.
- Auditar APIs reais das duas versões e explicar diferenças semânticas.
- Melhorar validações, diagnósticos, cancelamento e recuperação.
- Comparar comportamento em instalações isoladas, registrando versões e hashes.
- Melhorar documentação, acessibilidade e traduções.

Relate a versão e o hash exatos do mod, o diagnóstico e um link público para
o código ou uma reprodução autorizada. Não envie JARs privados, tokens,
logs pessoais completos ou pacotes de mappings. Veja
[como contribuir](CONTRIBUTING.md) e o
[status detalhado](docs/PROJECT_STATUS.md).

## Gratuito e aberto

O código original do LegacyBridge, sua documentação e fixtures próprias usam
**GPL-3.0-only com uma exceção limitada de ligação ao Minecraft/Forge**.
Não há cobrança, participação de 5% ou royalties obrigatórios. Redistribuições
do código coberto devem respeitar as obrigações de código-fonte e avisos da licença.

Componentes de terceiros mantêm suas licenças. Converter um mod não dá
autorização para republicar seu código ou assets e não muda automaticamente
sua licença. Consulte [o escopo da licença](LICENSING.md).

Código e contribuições: https://github.com/IanZuccheri/LegacyBridge.
