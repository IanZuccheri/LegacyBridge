# LegacyBridge

LegacyBridge is a free, open-source experiment in converting **Minecraft Forge
1.16.5 mods to Forge 1.20.1**. The goal is to make more abandoned content
portable through reviewed transformations and compatibility adapters, with
clear reports when an automatic port is not implemented.

**Experimental Beta: this is not a universal mod converter.** The player
workflow currently accepts only the narrow `simple-content-v1` language.
Most real mods, including many apparently simple content mods, will be refused.
The latest release candidate has not received a fresh native client/server
gameplay qualification; earlier successful fixture runs belong to their
recorded artifact hashes.

[Português](SUMMARY_PT_BR.md) · [Contributing](CONTRIBUTING.md) ·
[Project status](docs/PROJECT_STATUS.md) ·
[Development contract](docs/contract/README.md) ·
[Issues](https://github.com/IanZuccheri/LegacyBridge/issues)

## What it does today

- Adds **Convert mods / Converter mods** to the client title screen.
- Inventories candidate JARs by metadata, size and SHA-256; analyzes their
  bytecode with ASM without loading or executing the original mod classes.
- Converts only fully covered inputs, writes a CompatibilityReport and
  TransformationLog, and refuses unknown behavior without producing a partial JAR.
- Keeps converted results in staging until the player approves installation.
  Installation rechecks identity, dependencies and adapter bindings, with
  journals and explicit recovery for supported operations.
- Uses one required `LegacyBridge.jar`, including the runtime, engine, client
  interface and finite data needed by the public simple-content profile.
  Players do not need a second LegacyBridge JAR or a separately prepared
  mapping bundle for that profile.

The accepted source code is a strictly checked DeferredRegister registration
pattern, not arbitrary Java behavior. Within it, the public profile covers
plain items, plain STONE blocks and BlockItems, bounded basic properties,
vanilla shaped/shapeless recipes, restricted tags, translations, static PNG
textures, simple models/blockstates and aliases to selected vanilla sounds.
Creative membership is limited to absent, MISC or BUILDING_BLOCKS; legacy MISC
becomes the Ingredients tab.

The converter preserves accepted IDs, registry names, resource locations and
resource bytes where the contract permits. It replaces the limited registration
graph with a target bootstrap; it does **not** preserve arbitrary mod logic.
Metadata, pack format, adapter bindings and a required STONE pickaxe tag change
explicitly. See the [exact profile](docs/SIMPLE_CONTENT.md).

## What it does not do

The public profile does not support custom item/block subclasses, tools or armor,
entities, TileEntity/BlockEntity, world generation, dimensions, networking,
capabilities, custom GUIs/renderers/shaders, mixins, coremods, reflection,
arbitrary event handlers, general dependencies, custom recipe serializers,
loot tables, animated textures or custom audio.

There is no hot loading, universal launcher restart, old-world migration,
general Forge 1.16.5 emulation or cross-version multiplayer. A `CONVERTED`
result means static conversion finished, not that the mod works in a game.
Analysis, bytecode validation, installation, discovery and gameplay are separate
outcomes. Risk scores and test counts are not compatibility percentages.

Five additional profiles are developer experiments, **not extra player
Convert/Apply support**: `logic-preserving-v1`, `lifecycle-preserving-v1`,
`food-item-v1`, `datapack-recipes-v1` and `network-snapshot-v1`.
They require explicit local development data and have fixture-specific limits.
The [contract and status](docs/PROJECT_STATUS.md) retain their evidence,
unfinished work and native validation requirements.

## Requirements and installation

| Role | Minecraft | Forge | Java |
| --- | --- | --- | --- |
| Source reference | 1.16.5 | 36.2.42 | Usually 8 |
| Target | 1.20.1 | **47.4.23** | **17** |

Other Forge patches, Fabric and NeoForge are not certified targets.

1. Use a disposable Forge 1.20.1 / 47.4.23 instance. Back up worlds before later
   experiments; do not begin with an important world.
2. Put only the public `LegacyBridge.jar` in its `mods` folder. Do not also
   install the standalone runtime, developer module JARs or validation probes.
3. Put original candidate mods directly in `<gameDir>/legacybridge/input`,
   **not in `mods`**. Forge can reject legacy JARs before the menu opens.
4. Open **Convert mods**, refresh, inspect and select candidates. Leave the
   optional mapping field blank for the included simple-content data.
5. Convert and read the individual results. Continue converting if desired;
   staging does not change the installed or loaded mod set.
6. Explicitly approve **Apply** for selected results. Activation requires the
   next Minecraft launch. **Apply and close — reopen through your launcher**
   waits for the approved operation, then closes; you must relaunch yourself.

Converted mods remain separate JARs and depend on LegacyBridge at runtime.
The public artifact includes server-side runtime support, but the menu exists
only on the client; current-hash server qualification remains pending. Use
matching converted mods and runtime on both sides when the content requires it.

| Directory under `<gameDir>/legacybridge` | Purpose |
| --- | --- |
| `input` | Originals, never loaded by Forge from this folder |
| `staging` | Converted results awaiting explicit approval |
| `reports` | Analysis, conversion and transformation reports |
| `cache` | Regenerable working data |
| `transactions` | Installation journals and recoverable backups; keep these |

An invalid explicitly selected data bundle is an error, not a hidden fallback.
Conversion does not download mappings or scan developer caches. Full local
mapping/API bundles used for advanced development are not redistributable
release assets. Fully offline gameplay is not certified.

Read the [player guide](docs/PLAYER_INSTALLATION.md) for cancellation, managed
updates and recovery. Do not force-close during installation. No live mod,
unknown file or world is overwritten to force a successful result.

## Build and contribute

Use a 64-bit **JDK 17** and the checked-in **Gradle 8.8** wrapper. Run builds
serially; the normal build does not launch Minecraft:

```powershell
.\gradlew.bat build --no-daemon --no-parallel --max-workers=1
```

The public candidate is `legacy-product/build/libs/LegacyBridge.jar`.
Dependency acquisition during development is separate from runtime conversion.
Environment-dependent tests can be skipped when their explicit inputs are
absent; a passing default build is not the full release qualification.

The internal modules are `legacy-converter` (shared engine and developer CLI),
`legacy-compat-api` (adapter contracts), `legacy-runtime` (target behavior),
`legacy-workflow` (workflow contracts), `legacy-product` (UI and packaging),
and `legacy-corpus` (development evidence tools).

Useful starting points:

- [Architecture](ARCHITECTURE.md), [ASM pipeline](docs/ASM_PIPELINE.md) and
  [mapping rules](docs/MAPPINGS.md).
- [Contributing](CONTRIBUTING.md), [fixtures](fixtures/README.md) and
  [versioned corpus](corpus/README.md).
- [Validation](VALIDATION.md), [security limits](docs/SECURITY.md) and
  [historical public-only gameplay evidence](docs/PUBLIC_ONLY_END_TO_END_VALIDATION.md).

Help is especially welcome with small reproducible fixtures, exact API audits,
negative tests, independent client/server comparisons, clearer diagnostics and
documentation. A compatibility request is useful even when its current result
is UNSUPPORTED. Please provide public source links or authorized minimal
reproductions, not private mod JARs.

## License

LegacyBridge's original code, documentation and authored fixtures are
**GPL-3.0-only with a narrow Minecraft/Forge linking exception**. It is offered
free of charge. Third-party components
retain their own terms.

See [LICENSE](LICENSE), [licensing scope](LICENSING.md),
[linking exception](LINKING_EXCEPTION.md) and
[third-party notices](THIRD_PARTY_NOTICES.md). Modified distributions must follow
the applicable source and notice obligations. Converting a mod does not grant
permission to redistribute its code or assets, nor automatically change its license.
