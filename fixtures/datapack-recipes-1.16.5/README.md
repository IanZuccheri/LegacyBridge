# Original native datapack recipe fixture

Project-authored Forge 36.2.42 / Minecraft 1.16.5 fixture, mod ID
`legacybridge_datapack`, version 1.0.0. This is a separate fixture, not an edit to
the proven simple-content mod. Its one entrypoint deliberately follows that
fixture's finite ordinary DeferredRegister item/block/BlockItem pattern.

Original test source and procedural texture generator were derived from the
project-owned simple-content fixture with a new namespace; no Minecraft textures,
game/Forge classes or third-party mod bytes are copied into the artifact. All
Rights Reserved applies: local QA permission is not public binary redistribution.

Build serially from the project root:

```text
gradlew -p fixtures/datapack-recipes-1.16.5 build --no-daemon --max-workers=1
```

Artifact: `build/libs/legacybridge-datapack-recipes-1.16.5-1.0.0.jar` inside this
fixture. ForgeGradle 6.0.54, official 1.16.5 mappings and Forge 36.2.42 are fixed.
Java 17 compiles Java 8 bytecode, then ForgeGradle reobfuscates the production JAR.
Five tests inspect inert artifact bytes, resources and generated textures only.
They never initialize the original entrypoint and are not five supported mods.

## Closed content and independent recipe oracle

The fixture contains ordinary `bridge_ingot` (stack 32), `bridge_block` and its
BlockItem; one 3x3 shaped recipe consumes the ingot ingredient and declares one
block result. Assets, translations, block/item tags and a vanilla sound reference
retain the small project-authored source pattern.

Five additional recipes all use `#legacybridge_datapack:processing_inputs`, which
references `#legacybridge_datapack:processing_leaf`; the leaf contains only the
owned `bridge_ingot`. All outputs are the owned `bridge_block` item.

| Serializer | Declared result count | Cooking ticks | Experience |
|---|---:|---:|---:|
| minecraft:smelting | 1 | 200 | 0.25 |
| minecraft:blasting | 1 | 100 | 0.25 |
| minecraft:smoking | 1 | 100 | 0.25 |
| minecraft:campfire_cooking | 1 | 600 | 0.25 |
| minecraft:stonecutting | 2 | N/A | N/A |

These deliberately artificial item/block recipes isolate serializer and tag
semantics; they are not a survival-balance recommendation or a machine simulation.
Cooking JSON uses a string result, explicit cooking time/experience and no result
count or target-only category. Stonecutting uses string result plus explicit count.

The separate native probes compare manager lookup, ingredient matching, assemble
result identity/count and declared cooking parameters before/after actual native
resource reload. A higher-priority test-owned datapack replaces the nonempty local
leaf with `bridge_block`: ingot must stop matching and block must start. Restoring
the original pack selection and reloading must restore the original matches.
Neither the original JAR nor its converted counterpart is modified by this QA pack.

Assembly does not consume ingredients, award XP, execute furnace/campfire ticks or
prove menu/recipe-book behavior. Target category defaults, client assets/UI,
multiplayer, cross-mod/optional/empty tags, custom ingredients/serializers,
conditions, loot, advancements and actual processing machines remain separate
gates. New source files alone are not a build, conversion or runtime pass.

See the paired `datapack-runtime-probe-1.16.5` and `datapack-runtime-probe-1.20.1`
projects. They are runtime-only observers and must never enter the public product.
