# Original plain-block fixture

Project-authored Forge **1.16.5 / 36.2.42**, version 1.0.0; one Java-8 class.
`legacybridge_plain_block:bridge_block` is a native `Block` constructed with
`AbstractBlock.Properties.of(Material.STONE).strength(2.0F, 6.0F)`. Its native
`BlockItem` resolves the block through `RegistryObject.get()` only inside the
item supplier and has a stack limit of 32. The constructor attaches the block
DeferredRegister before the item DeferredRegister to the same mod event bus.
Registry references are not eagerly resolved during static initialization.

One shapeless iron-ingot recipe, a default-only blockstate, block/item models,
and en_us/pt_br translations are authored here. The model references the
allowlisted vanilla stone texture; **no Minecraft textures or code are copied**.
There are no custom state properties, subclasses, callbacks, creative tabs,
loot tables, tags, block entities, fluids, entities, networking or worldgen.
In particular, no drops are promised without a loot table.

Build from the repository root using Java 17, serially:
`gradlew.bat -p fixtures/plain-block-1.16.5 build --offline --max-workers=1`.
The four standalone tests inspect the reobfuscated archive as data. They never
reference, load or execute the original fixture class.

This fixture is input for a private, fail-closed code-preservation candidate;
it does not expand the public conversion profiles. Legacy material behavior
requires an exact implemented operation and audited target mining-tag handling,
not merely a class rename. Build, bytecode/linkage validation and native behavior
comparison are separate gates. Native/client/server/multiplayer validation is
NOT_RUN until explicitly recorded by the parent project.
