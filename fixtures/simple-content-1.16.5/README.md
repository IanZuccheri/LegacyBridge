# Real Forge 1.16.5 simple-content fixture

This standalone build compiles against **Forge 36.2.42 / Minecraft 1.16.5**, with
Mojang official development names. ForgeGradle's `reobfJar` produces the actual
Java 8 / production SRG input JAR. The Java 17 compiler uses `--release 8`.
This is not a hand-written bytecode imitation or a fixture compiled against
LegacyBridge shims.

From the LegacyBridge root, using its pinned Gradle wrapper:

```powershell
.\gradlew.bat -p fixtures/simple-content-1.16.5 clean build --no-daemon --no-parallel --max-workers=1
```

The input artifact is
`build/libs/legacybridge-example-1.16.5-1.0.0.jar`. `build` runs artifact tests
after reobfuscation. It never starts Minecraft.

The mod ID and version are `legacybridge_example` and `1.0.0`.

- `bridge_ingot`: plain item, stack limit 32, vanilla miscellaneous creative tab.
- `bridge_block`: plain stone-material block, hardness 2, resistance 6, with a
  block item in the vanilla building-blocks creative tab.
- `bridge_block` recipe: a full 3x3 of bridge ingots crafts one bridge block.
- Original 16x16 procedural test textures, vanilla-parent models, blockstate,
  English and Brazilian Portuguese localization, one item tag and one block tag.
- `bridge_ping` sound resource aliases the vanilla note-block pling event; no
  copied audio or custom sound implementation is packaged.

The fixture intentionally has no custom subclasses, event listeners, packets,
capabilities, block entities, entities, GUIs, renderers, shaders or worldgen.
Only normal DeferredRegister mod-bus attachment is used.

The source and generated test patterns are original LegacyBridge contributions
licensed under the repository license. No Minecraft assets or mapping files are
redistributed. Mapping metadata and game libraries are obtained by ForgeGradle.
