# First supported category: simple-content-v1

This is a finite conversion language, **not compatibility with every mod that
adds items and blocks**. An unsupported instruction, declaration, dependency or
resource rejects the entire mod. The input JAR is read statically using ASM;
no input class loader, initializer, constructor or method is executed.

## Conversion architecture and preservation

The source baseline is Minecraft 1.16.5 / Forge 36.2.42. The output requires
Minecraft 1.20.1 / Forge 47.4.23 and Java 17 with `legacybridge_runtime` version
`0.1.1-SNAPSHOT`. Older unbound output must be reconverted, not merely reinstalled.
The included finite contract (or explicit full official/MCP inputs) normalizes
source names and checks selected, real-source-verified declarations. It is not
a complete API/linkage inventory. Generic `mappings` confidence is not permission
to rewrite calls. See [included data and its limits](EMBEDDED_SIMPLE_CONTENT_DATA.md).

The extractor interprets only the accepted declaration language into a bounded
content plan. The converter removes its fully accounted-for legacy classes and
generates one Java 17 `@Mod` bootstrap bound to `compat.registry.SimpleContentAdapter.register`.
The runtime uses native target DeferredRegister and creative-tab events; it does
not inject transformers or load the removed legacy code.

The mod ID, version, registry names and resource locations are retained. Accepted
recipes, tags, translations, textures, models and sound definitions are copied
byte-for-byte, with hashes recorded in the conversion report. Necessary changes
are explicit: target loader/dependency ranges, runtime dependency, pack format
6 to 15, generated bootstrap/plan/bindings, and an additive target
`minecraft:mineable/pickaxe` tag retaining the STONE material's tool behavior.
Each transformation names its concrete adapter; see [Compatibility layer](COMPAT_LAYER.md).

Conversion now runs through the [ASM pipeline](ASM_PIPELINE.md), including input
CheckClassAdapter checks, exact own-entrypoint remapping, finite graph lowering,
descriptor processing, concrete adapter insertion and frame/max computation.
Target bytes are validated again immediately before JAR serialization. The JSON
report embeds a versioned `transformationLog`; an aborted attempt has
`transformationsCommitted=false` and no output JAR. This does not enable generic
API call rewriting or load original mod classes.

## Accepted registration grammar

- One `javafml` mod declaration with a matching `@Mod` class and public no-argument
  constructor; dependencies restricted to Minecraft and Forge.
- Plain registration-holder classes extending Object, static final constants,
  DeferredRegisters and RegistryObjects; straight-line static initialization.
- `DeferredRegister.create`, named registrations with capture-free static
  Supplier lambdas/helpers, and mod-bus attachment from the mod constructor.
- Native Item, Block and BlockItem constructors only; no custom subclasses.
- Item properties: stack size 1..64, default COMMON rarity, zero durability and
  no fire resistance; only absent, MISC or BUILDING_BLOCKS creative membership.
- Block properties: STONE material and STONE sound, bounded hardness/resistance;
  nondefault material, sound, correct-tool and no-occlusion flags reject.
- Simple SoundEvent declarations are allowed only when their resource definition
  resolves through aliases to an audited vanilla event; this registered-event
  adapter is motivated by synthetic tests, not live fixture gameplay.

Unknown APIs, user event handlers, branches, loops, exceptions, reflection,
arbitrary helpers, captured lambdas and unaccounted-for behavior reject. This
intentionally also rejects some otherwise innocuous coding styles.

The initial example uses STONE, stack size 32, hardness 2 and resistance 6.
Previous speculative presets are now explicitly unsupported. Source MISC becomes
target INGREDIENTS, while BUILDING_BLOCKS retains its corresponding target tab.
No exact legacy tab ordering or custom creative-tab semantics are promised.

## Accepted resource language

- Assets belong to the original mod namespace; language JSON has string values.
- Models use local parents or audited `item/generated`, `item/handheld`,
  `block/cube_all` vanilla templates. Referenced textures/parents must exist.
  Custom loaders, overrides, predicates, geometry and display transforms reject.
- Plain-block blockstates have the default variant only, or unconditional
  multipart applications. Custom block state properties/conditions reject.
- Static PNG textures require valid CRCs, bounded dimensions/pixel count and
  successful decoding. Animation metadata/APNG are not supported.
- Recipes are vanilla shaped/shapeless with checked ingredients, symbols and
  result IDs. Source item/block tags may use own or Forge namespaces; all source
  `data/minecraft/tags/**` declarations reject because global behavior can differ.
- Sounds are `type=event` aliases ending at an audited vanilla event; local alias
  cycles and missing references reject. All local Ogg files and `type=file`
  entries reject until bounded audio decoding is implemented.
- A deliberately small audited vanilla resource/ingredient/tag allowlist is
  used. Unknown vanilla and third-party dependencies are not guessed valid.

Missing local assets, parent/texture/tag/sound cycles, malformed JSON and unknown
resource paths reject. Loot tables, worldgen, dimensions, functions, structures,
custom serializers, shaders, GUIs, entities, TileEntity/BlockEntity, packets,
capabilities, mixins, access transformers and coremods are outside this profile.

## Build and convert the actual legacy fixture

Run sequentially from the LegacyBridge root using JDK 17:

```powershell
.\gradlew.bat -p fixtures/simple-content-1.16.5 clean build --no-daemon --no-parallel
.\gradlew.bat build :legacy-converter:installDist --no-daemon --no-parallel
New-Item -ItemType Directory -Force build/conversion/asm-v1
.\legacy-converter\build\install\legacy-converter\bin\legacy-converter.bat convert `
  .\fixtures\simple-content-1.16.5\build\libs\legacybridge-example-1.16.5-1.0.0.jar `
  --output .\build\conversion\asm-v1\legacybridge-example-1.20.1.jar `
  --report .\build\conversion\asm-v1\report.json
```

The fixture is compiled against real Forge 36.2.42, Java 8 class version 52,
and reobfuscated to production SRG names. Its build has four artifact tests.
It defines mod `legacybridge_example` version `1.0.0`: item `bridge_ingot`, block
and BlockItem `bridge_block`, and one recipe using nine ingots for one block.

Default simple conversion uses its included finite source contract, with no
separate data preparation or Gradle-cache lookup. An optional explicit
`--mapping-bundle`/`--mapping-profile` still takes precedence and must validate;
see [Mappings](MAPPINGS.md) and the unchanged full-bundle
[redistribution limitations](PORTABLE_MAPPING_DATA.md). Development comparison
against the real inputs is a separate opt-in validation, not this player's
operational dependency. The converter never downloads data. Output parents must exist;
the output JAR must not exist. `--overwrite` overwrites only a report. Exit 0
means conversion completed, 1 means a reported subset refusal; it is not runtime
proof. Schema-v2 `LEGACY_CONVERSION` records CONVERTED/ABORTED, structured adapter
references/bindings and precise UNSUPPORTED diagnostics, without a partial JAR.

## Isolated runtime gates

The adapter-bound `0.1.1-SNAPSHOT` fixture passed both gates on 2026-09-05,
including the mining-speed and note-block instrument checks. See the
[current validation evidence](ASM_PIPELINE_VALIDATION.md) for exact artifacts and
remaining gates; the [0.1.0 record](SIMPLE_CONTENT_VALIDATION.md) is historical.

```powershell
.\gradlew.bat :legacy-runtime:runServer -PbridgeValidation=true -PbridgeFixtureJar=build/conversion/asm-v1/legacybridge-example-1.20.1.jar --no-daemon --no-parallel
.\gradlew.bat :legacy-runtime:runClient -PbridgeValidation=true -PbridgeFixtureJar=build/conversion/asm-v1/legacybridge-example-1.20.1.jar --no-daemon --no-parallel
```

These tasks stage the converted fixture into separate `run-validation/server`
and `run-validation/client` mod directories. The matching runtime and validation
harness are development source sets; the harness is excluded from release JARs.
The server's EULA must be accepted explicitly for that isolated environment.

Server success requires `LEGACYBRIDGE_SERVER_VALIDATION_OK`: Forge discovery,
identity, native registration, properties, tags, mining speed/instrument and an
actual RecipeManager crafting match plus an incomplete-grid negative check.
Client success requires `LEGACYBRIDGE_CLIENT_VALIDATION_OK`: three baked models,
two atlas sprites, translations, resolved sound resource and both creative tabs.
The probes stop their own test instances. A Gradle exit code alone is insufficient;
inspect the corresponding `logs/latest.log` for success and failure markers.
Builds and source fixtures do not substitute for these runtime gates. No new
category should be started while either fixture gate remains unsatisfied.
