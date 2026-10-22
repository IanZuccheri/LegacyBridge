# Compatibility adapter API evidence

Read-only semantic audit on 2026-09-05, against actual ForgeGradle-generated
Mojang-named source JARs. A name mapping or matching descriptor alone does not
establish equivalent behavior. The examples below motivate explicit adapters
and unsupported cases; they do not constitute universal API emulation.

## Exact local source baselines

The inspected cache root on the validation machine was:

```text
${DEVELOPER_HOME}/.gradle/caches/forge_gradle/minecraft_user_repo/net/minecraftforge/forge/
```

The exact source artifacts relative to that root were:

```text
1.16.5-36.2.42_mapped_official_1.16.5/forge-1.16.5-36.2.42_mapped_official_1.16.5-sources.jar
1.20.1-47.4.23_mapped_official_1.20.1/forge-1.20.1-47.4.23_mapped_official_1.20.1-sources.jar
```

These are locally generated development sources, not redistributed files or
publisher-authenticated source archives. Member names below are Mojang development
names. The genuine example input is compiled against Forge 36.2.42, then
`reobfJar` produces Java 8 bytecode with production SRG member names. The offline
extractor must normalize these namespaces using the pinned mapping/API inputs;
it must not assume the source fixture JAR contains the names seen in this audit.

## STONE material: mining requires an explicit target adaptation

In the legacy source `net/minecraft/item/PickaxeItem.java:17-28`,
`isCorrectToolForDrops` accepts Material.STONE (or the Forge harvest-tool path),
and `getDestroySpeed` uses the tier speed for STONE regardless of a mining tag.
The source fixture uses that material and does not declare a vanilla mining tag.

In the target source `net/minecraft/world/item/PickaxeItem.java:6-7`, the
constructor selects `BlockTags.MINEABLE_WITH_PICKAXE`.
`net/minecraft/world/item/DiggerItem.java:34-35` returns the tool speed only when
the block is in its mining tag; otherwise it returns 1.0. Correct-tool checks
also consult the tag and Forge's tier sorting (`DiggerItem.java:64-81`).

Therefore copying the fixture's custom block tag is insufficient: STONE needs
an explicit target mining-tag adapter. Any generated
`data/minecraft/tags/blocks/mineable/pickaxe.json` must be reported as a necessary
transformation, not as an unchanged source resource. A runtime hardness check
alone does not validate mining speed; check a real native pickaxe too.

WOOD has a related unaudited migration: legacy `net/minecraft/item/AxeItem.java:20`
and `:28-30` check material membership rather than target mining tags. This does
not authorize automatic WOOD support merely because a map color can be copied.

## Note-block instruments: global tag writes can change meaning

Legacy `net/minecraft/state/properties/NoteBlockInstrument.java:45-77`
checks `BlockTags.WOOL` before falling back to Material.STONE. A plain stone
block added to that vanilla tag produces GUITAR, not BASEDRUM. With no such tag,
the STONE fallback at lines 69-71 is BASEDRUM.

Target `net/minecraft/world/level/block/NoteBlock.java:45-53` obtains instruments
from block-state properties. The default is HARP in
`net/minecraft/world/level/block/state/BlockBehaviour.java:998`; the explicit
`Properties.instrument` setter is at lines 1268-1270. An adapter selecting
BASEDRUM for STONE cannot also preserve arbitrary source WOOL-tag behavior.

Accordingly, the source resource validator refuses **all**
`data/minecraft/tags/**` declarations, with an unsupported-semantics diagnostic.
Own-namespace and Forge custom item/block tag declarations remain subject to
the existing reference/cycle validator. Reading an audited vanilla tag reference
is distinct from overwriting/appending a vanilla tag declaration. Generated
target mining tags are emitted after source validation by an explicit adapter.

## Creative categories are lifecycle adaptations, not pure renames

Legacy `net/minecraft/item/Item.java:412-414` stores an ItemGroup on properties;
`net/minecraft/item/ItemGroup.java:17-22` declares BUILDING_BLOCKS, and lines 41-45
declare MISC with its legacy presentation. Target membership is populated with
`BuildCreativeModeTabContentsEvent` on the mod event bus, including `accept`.
This target contract is documented by [Forge's creative-tab documentation](https://docs.minecraftforge.net/en/1.20.1/items/#creative-tabs).

MISC to INGREDIENTS is an explicit migration policy exercised by the fixture,
not equivalent legacy title/icon/ordering. BUILDING_BLOCKS uses the corresponding
native target tab. Untested legacy/custom categories must not acquire implicit
support through a broad switch statement.

## Simple setters and metadata boundaries

The basic `stacksTo`, `defaultDurability` and `durability` setter rules match in
legacy `net/minecraft/item/Item.java:388-403` and target
`net/minecraft/world/item/Item.java:432-447`: damageability prevents subsequent
stack-size configuration, and durability sets stack size to one. This supports
bounded, explicitly modeled property handling, not arbitrary Item subclasses.

Loader/dependency updates, pack format 6 to 15, generated bootstrap/content plan,
native registration, creative categories and generated mining tags must each
have an explicit compatibility-adapter relationship in the conversion trace.
Resource byte preservation, fixture client/server success and structural API
matches are separate evidence; none independently proves all material, tool,
datapack, multiplayer or mod-interaction semantics.
