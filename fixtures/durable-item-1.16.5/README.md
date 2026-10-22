# Original durable-item fixture

Project-authored Forge **1.16.5 / 36.2.42**, version 1.0.0; two Java-8 classes.
`legacybridge_durable:token`: native Item with durability 128, stack limit 1,
and an owned virtual repair predicate accepting the native iron-ingot item.
One shapeless iron-ingot recipe and en_us/pt_br translations. Model references
vanilla gold_ingot as a placeholder; no game textures or dependencies bundled.
There are no damage writes, custom use hooks, tools, armor or state.

Build from the repository root using Java 17, serially:
`gradlew.bat -p fixtures/durable-item-1.16.5 build --offline --max-workers=1`.
Tests only inspect the reobfuscated JAR; they never load original fixture classes.

Engine integration tests statically prepare/reopen a private target candidate.
The developer service is `LegacyEngineService.planDurableItem(ConversionRequest)`
with the unchanged LOGIC_PRESERVING_V1 base and explicitly supplied full mapping
inputs. It returns proof/hash, not a publishable JAR or an install receipt.
The public six conversion profiles still reject this fixture.

[Oracle, exact API evidence and limitations](../../docs/DURABLE_ITEM_CONTRACT.md).
All native/game, actual anvil, persistence and multiplayer comparisons NOT_RUN.
