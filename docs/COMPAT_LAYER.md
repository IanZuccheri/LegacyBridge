# Implemented compatibility layer

Runtime/converter contract: `0.1.1-SNAPSHOT`, catalog schema 1, adapter revision 1.
The public installation path still admits only the finite
[simple-content profile](SIMPLE_CONTENT.md). The catalog also records developer
offline operations, the fixed [iron helmet empty-slot operation](IRON_HELMET_EMPTY_SLOT_ADAPTER.md)
the separate [guarded HEAD effect](IRON_HELMET_HEAD_EFFECTS.md), and the
[closed dispenser operation](IRON_HELMET_DISPENSER.md).
The dispenser is not registered for an item. These operations have no composed use/constructor recipe or public installation permission.
It is not a loader trick that supplies empty classes or silently drops behavior.
Unsupported behavior rejects conversion before a JAR is emitted.

## Eight areas, not eight implied capabilities

The Java namespace is `dev.legacybridge.runtime.compat`. Source is split between
`legacy-runtime/src/main/java` (Forge-dependent implementations) and
`legacy-compat-api/src/main/java` (JDK-only contracts/offline operations).

| Area | Implemented boundary |
|---|---|
| `registry` | Proven native content registration, basic items/BlockItems, limited SoundEvent, metadata and manifest operations. |
| `event` | Active mod constructor identity/version and native mod-bus attachment only. |
| `world` | Plain STONE properties and generated target pickaxe-mining tag. No worldgen or dimensions. |
| `client` | The two fixture creative categories and validated resource-pack version adaptation. |
| `entity` | Unsupported; package documentation only. |
| `networking` | Unsupported; package documentation only. |
| `capability` | Unsupported; package documentation only. |
| `rendering` | Unsupported; package documentation only. Vanilla asset loading is not custom renderer emulation. |

The four unsupported areas have no catalog registrations or callable dummy
adapters. There is no general RegistryEvent translation, packet/capability shim,
custom entity/GUI/renderer adapter, or arbitrary legacy-code execution.

## Catalog and concrete operations

`AdapterCatalog` is the shared source of truth; its generated documentation is
packaged in the player JAR at `META-INF/legacybridge/adapters.json`.
Every entry provides these five mandatory, nonblank documentation fields:

- `legacyApi`: source API/contract.
- `targetApi`: actual target API/contract.
- `transformation`: operation implemented.
- `behaviorDifferences`: intentional/non-equivalent behavior changes.
- `knownLimitations`: explicit unsupported boundary.

It also records fixture motivation, execution phase and exact
`id`, `revision`, `implementationClass`, `method`, `descriptor`. There are
69 entries after the additive dispenser operation; all 68 previous descriptors are unchanged.
The table below records the original eleven simple-content entries, not the full
current catalog. Metadata and manifest are distinct operations on the same
`ModMetadataAdapter` class. Consult the generated catalog for the complete list.

| Catalog ID | Concrete class and method | Phase |
|---|---|---|
| `registry.simple-content.v1` | `registry.SimpleContentAdapter.register` | Runtime |
| `registry.item-properties.v1` | `registry.ItemPropertiesAdapter.create` | Runtime |
| `registry.block-item.v1` | `registry.BlockItemAdapter.create` | Runtime |
| `registry.sound-event.v1` | `registry.SoundEventAdapter.create` | Runtime |
| `event.mod-bus.v1` | `event.ModEventBusAdapter.requireContext` | Runtime |
| `world.stone-properties.v1` | `world.BlockPropertiesAdapter.create` | Runtime |
| `client.creative-tabs.v1` | `client.CreativeTabAdapter.attach` | Runtime |
| `world.stone-mining.v1` | `world.BlockMiningAdapter.targetTag` | Offline |
| `registry.mod-metadata.v1` | `registry.ModMetadataAdapter.targetRanges` | Offline |
| `registry.mod-manifest.v1` | `registry.ModMetadataAdapter.targetManifest` | Offline |
| `client.resource-pack.v1` | `client.ResourcePackAdapter.targetFormat` | Offline |

SoundEvent registration is motivated by an existing synthetic fixture. The real
example exercises a sounds.json vanilla-event alias, not registered-event
gameplay or arbitrary playback/network calls. No local Ogg/file sounds are accepted.

## Binding a transformation to an implementation

`TransformationBindings` first rejects properties without fixture-backed
adapters, then selects the exact required catalog entries. Schema-v2
`LEGACY_CONVERSION` reports contain structured `transformations`: each operation
has its adapter reference, phase, source/target API, differences, limitations and
fixture evidence. The report's `adapterBindings` is also packaged as
`META-INF/legacybridge/<modId>/adapters.json` beside the content plan.

The generated bootstrap invokes the cataloged `SimpleContentAdapter` entrypoint,
not an invented target API. The runtime verifies schema, exact runtime version,
adapter identities/revisions/entrypoints and complete plan coverage before
registering content. Class/method names in binding JSON are inert comparisons
against the compiled catalog: they are never reflectively loaded or invoked.
Missing, extra, duplicate, unknown or mismatched bindings are rejected.

The old `SimpleContentRuntime` facade delegates to the checked implementation;
it does not bypass bindings. **Reconvert old unbound JARs.** The declared Forge
dependency range does not override the exact `0.1.1-SNAPSHOT` binding check.

## Scope narrowed to proven behavior

Plain Item/BlockItem properties allow stacks 1..64, COMMON rarity, durability 0
and fire resistance false. Creative membership is absent, MISC or BUILDING_BLOCKS
only. MISC becomes INGREDIENTS by explicit migration policy; legacy tab identity,
icon and ordering are not retained. Blocks use STONE material/STONE sound and
bounded strength; other materials, noOcclusion and correct-tool flags reject.

STONE needs two explicit adaptations: target map color/instrument BASEDRUM and
the generated additive `minecraft:mineable/pickaxe` block tag. Legacy pickaxes
used Material.STONE directly; target pickaxes consult that tag. The new tag is a
necessary generated resource, not a claim that all input bytes are unchanged.
Accepted source assets, recipes, translations and custom tags remain identical.
All source `data/minecraft/tags/**` declarations reject: e.g. legacy WOOL tag
membership changes note-block instrument selection and cannot be guessed safe.
See [actual API evidence](COMPAT_API_BASELINES.md).

Generic [mapping reports](MAPPINGS.md) now mark missing direct-call adapters
`UNSUPPORTED`. A whole-plan registry adapter does not prove equivalence for an
arbitrary registry/capability/rendering call, and no generated mapping permits
automatic bytecode rewriting.

## Artifact and validation boundaries

The converter distribution includes `legacy-compat-api-0.1.1-SNAPSHOT.jar`,
containing JDK-only contracts/offline code without game classes. It is **not a mod
to install**. Players use the single public `LegacyBridge.jar`, which contains
the concrete Forge adapters, engine and shared contract, with converted mods.
The separate runtime JAR is a development artifact, not a second required player mod.

Build checks audit catalog entrypoints, JDK-only compatibility-API boundaries,
bindings and unsupported cases. The adapter-bound 0.1.1 fixture passed 257 root
tests and fresh Forge server/client probes, including mining speed and note-block
instrument checks in its historical validation. See [that evidence](COMPAT_LAYER_VALIDATION.md)
for its artifact hashes; it is not native qualification of later public JARs.
The current [helmet ledger](evidence/iron-helmet-adapter-2026-10-05.json) separates
compiled-body checks from native equipment, effects and multiplayer, all NOT_RUN.
