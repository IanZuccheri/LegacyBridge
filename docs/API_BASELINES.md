# API baselines

## Purpose

LegacyBridge must derive compatibility rules from real, pinned Minecraft Forge
artifacts. It must not invent methods, infer descriptors from tutorials, or
assume that similarly named classes across versions have equivalent semantics.

This document records the project anchors and a small verified API spot-check.
The implemented [mapping system](MAPPINGS.md) also builds declaration indexes
from local development JARs and composes official/MCP symbol identities. These
are not a complete semantic API diff and do not authorize transformation.

## Pinned anchors

| Role | Minecraft | Forge | Java | Mapping/build context |
|---|---:|---:|---:|---|
| Legacy source reference | 1.16.5 | 36.2.42 | Java 8 / classfile major 52 | Baseline verification, source fixture build and static extraction |
| Runtime target | 1.20.1 | 47.4.23 | Java 17 / classfile major 61 | ForgeGradle with official 1.20.1 mappings |

The checked-in wrapper is Gradle `8.8`. The scaffold pins ForgeGradle `6.0.54`.

`legacy-converter` resolves non-transitive `universal` Forge artifacts for both
exact lines in dedicated test-only baseline configurations. They are not
implementation dependencies and are not placed on the converter's runtime
classpath. `legacy-runtime`, conversely, compiles against the exact Maven
coordinate `net.minecraftforge:forge:1.20.1-47.4.23`.

The target `mods.toml` accepts Forge `47.4.23` up to, but not including, major
48 and Minecraft `1.20.1` up to, but not including, `1.20.2`. That runtime
metadata range is distinct from the exact coordinate used to compile and test.

## Verified API spot-check

The following facts were checked directly against the exact cached mapped JARs
for `1.16.5-36.2.42` and `1.20.1-47.4.23`. The automated baseline test also
checks the key registry owners and descriptors in the exact `universal`
artifacts resolved by Gradle. These facts demonstrate why compatibility must be
based on full symbols rather than names alone.

### Bootstrap and lifecycle anchors that persist

These owners exist on both pinned lines:

- `net.minecraftforge.fml.common.Mod`
- `net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext`
- `net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent`
- `net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent`

On the target, the JavaFML entrypoint classes are supplied by the matching
`javafmllanguage` artifact rather than the main mapped Forge JAR. Persistence of
an owner is only an inventory fact; it does not establish method or lifecycle
semantic equivalence.

### Representative family inventory

The following owner-presence checks were made against the exact cached
`mapped_official` JARs for the coordinates above. They are representative
anchors for the analyzer's feature taxonomy, not source-to-target rewrite
recipes.

| Family | Observed in 1.16.5 / Forge 36.2.42 | Observed in 1.20.1 / Forge 47.4.23 | Migration implication |
|---|---|---|---|
| Lifecycle | `net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent` | same owner | Owner persistence does not prove callback or threading equivalence. |
| Registries | `net.minecraftforge.event.RegistryEvent$Register`, `net.minecraftforge.fml.RegistryObject`, `net.minecraftforge.registries.IForgeRegistryEntry` | `net.minecraftforge.registries.RegisterEvent`, `net.minecraftforge.registries.RegistryObject`; no `IForgeRegistryEntry` | Registration is structurally redesigned; see the descriptor check below. |
| Events | `net.minecraftforge.event.world.WorldEvent` | `net.minecraftforge.event.level.LevelEvent` | Package and domain terminology changed; individual event subclasses still need exact inspection. |
| Networking | `net.minecraftforge.fml.network.simple.SimpleChannel`, `net.minecraftforge.fml.network.NetworkHooks`, `net.minecraftforge.fml.network.IContainerFactory` | `net.minecraftforge.network.simple.SimpleChannel`, `net.minecraftforge.network.NetworkHooks`, `net.minecraftforge.network.IContainerFactory` | Even a clear owner move is not enough to authorize descriptor replacement or packet-semantic adaptation. |
| Capabilities | `net.minecraftforge.common.capabilities.CapabilityInject`; also `net.minecraftforge.common.capabilities.CapabilityManager`, `ICapabilityProvider`, and `net.minecraftforge.event.AttachCapabilitiesEvent` | `net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent` and `CapabilityToken`; `CapabilityManager`, `ICapabilityProvider`, and `AttachCapabilitiesEvent` remain present under those same packages | Annotation injection versus token/event registration requires semantic review. |
| Entities | `net.minecraft.entity.EntityType` | `net.minecraft.world.entity.EntityType` | The Minecraft package model moved; constructors, serializers, spawning, attributes, and networking remain separate concerns. |
| Block entities | `net.minecraft.tileentity.TileEntity`, `net.minecraftforge.common.extensions.IForgeTileEntity` | `net.minecraft.world.level.block.entity.BlockEntity`, `net.minecraftforge.common.extensions.IForgeBlockEntity` | This is a terminology, owner, lifecycle, persistence, ticker, and renderer migration, not a global rename. |
| GUI | `net.minecraft.client.gui.screen.Screen`, `net.minecraft.inventory.container.Container`, `net.minecraftforge.client.event.GuiScreenEvent`, `net.minecraftforge.client.event.GuiOpenEvent` | `net.minecraft.client.gui.screens.Screen`, `net.minecraft.world.inventory.AbstractContainerMenu`, `net.minecraftforge.client.event.ScreenEvent` | Screens, menus, event shapes, and client/server opening flow require separate handling. |
| Rendering | `net.minecraft.client.renderer.tileentity.TileEntityRenderer`, `net.minecraftforge.fml.client.registry.RenderingRegistry` | `net.minecraft.client.renderer.blockentity.BlockEntityRenderer`, `net.minecraftforge.client.event.EntityRenderersEvent$RegisterRenderers` | Renderer registration and block-entity rendering are not pass-through compatible. |
| World generation | `net.minecraft.world.gen.ChunkGenerator`, `net.minecraftforge.event.world.BiomeLoadingEvent`, `net.minecraftforge.common.world.ForgeWorldType` | `net.minecraft.world.level.chunk.ChunkGenerator`, `net.minecraftforge.common.world.BiomeModifier`; no equivalent nominated here for `ForgeWorldType` | Code and data-driven world generation changed substantially; path detection alone cannot validate it. |
| Dimensions | `net.minecraft.util.RegistryKey`, `net.minecraft.world.DimensionType`, `net.minecraft.world.gen.DimensionSettings` | `net.minecraft.resources.ResourceKey`, `net.minecraft.world.level.dimension.DimensionType`, `net.minecraft.world.level.levelgen.NoiseGeneratorSettings` | Dimension identity and generation settings require registry/data semantics, not owner substitution. |
| Recipes | `net.minecraft.item.crafting.IRecipe`, `net.minecraft.item.crafting.IRecipeSerializer` | `net.minecraft.world.item.crafting.Recipe`, `net.minecraft.world.item.crafting.RecipeSerializer` | JSON presence and interface-owner presence do not establish recipe compatibility. |
| Loot | `net.minecraft.loot.LootTable`, `net.minecraftforge.common.loot.GlobalLootModifierSerializer`, `net.minecraftforge.common.loot.IGlobalLootModifier` | `net.minecraft.world.level.storage.loot.LootTable`, `net.minecraftforge.common.loot.IGlobalLootModifier`; no `GlobalLootModifierSerializer` owner | Loot tables, conditions, codecs, and global modifiers need independent validation. |

The analyzer recognizes these families conservatively from bytecode owners,
members, descriptors, hierarchy edges, and resource paths. It deliberately
does not convert any owner in this table.

### Registry API breaks already confirmed

`DeferredRegister.register` changes its return owner:

```text
1.16.5 / 36.2.42
<I extends T> net.minecraftforge.fml.RegistryObject<I>
register(String, Supplier<? extends I>)

1.20.1 / 47.4.23
<I extends T> net.minecraftforge.registries.RegistryObject<I>
register(String, Supplier<? extends I>)
```

The generic class bound also changes. On 1.16.5, `DeferredRegister<T>` requires
`T extends net.minecraftforge.registries.IForgeRegistryEntry<T>`; on 1.20.1 it
is unbounded.

`ResourceLocation` moves between real Minecraft owners:

```text
1.16.5: net.minecraft.util.ResourceLocation
1.20.1: net.minecraft.resources.ResourceLocation
```

The registration event is redesigned rather than merely renamed:

```text
1.16.5: net.minecraftforge.event.RegistryEvent$Register<T>
1.20.1: net.minecraftforge.registries.RegisterEvent
```

The 1.16.5 event is generic over `IForgeRegistryEntry<T>` and exposes its
registry through `getRegistry()`. The 1.20.1 event is non-generic and registers
against a `ResourceKey` through `register(...)` overloads.

Finally, the 1.16.5 interface
`net.minecraftforge.registries.IForgeRegistryEntry` defines:

```text
setRegistryName(net.minecraft.util.ResourceLocation)
getRegistryName()
getRegistryType()
```

That interface is absent from the pinned 1.20.1 target API. The old
`IForgeRegistryEntry#setRegistryName` registration model therefore cannot be
treated as a pass-through call.

LegacyBridge currently has no registry transformation recipe and no registry
shim. All of these references remain unsupported.

The `verifyForgeApiBaselines` build task checks that the expected legacy and
target registry classes are present or absent in the correct exact artifacts.
`ForgeApiBaselineTest` additionally reads `DeferredRegister.class` with ASM and
asserts the two real `register` descriptors. This is a selected regression
anchor, not a complete source-to-target API diff.

## Authoritative public anchors

- Forge's 1.16 documentation identifies Java 8 as its development runtime and
  Forge major 36 for Minecraft 1.16.5:
  <https://docs.minecraftforge.net/en/1.16.x/gettingstarted/>.
- Forge 1.16 documents `META-INF/mods.toml`, `javafml`, and the `@Mod`
  entrypoint contract:
  <https://docs.minecraftforge.net/en/1.16.x/gettingstarted/structuring/>.
- Forge 1.16 documents its registry, event-lifecycle, SimpleImpl networking,
  and capability models:
  <https://docs.minecraftforge.net/en/1.16.x/concepts/registries/>,
  <https://docs.minecraftforge.net/en/1.16.x/concepts/lifecycle/>,
  <https://docs.minecraftforge.net/en/1.16.x/networking/simpleimpl/>, and
  <https://docs.minecraftforge.net/en/1.16.x/datastorage/capabilities/>.
- Forge 1.16 documents data generation as a build-time API rather than proof
  that packaged data is valid:
  <https://docs.minecraftforge.net/en/1.16.x/datagen/intro/>.
- Forge 1.20.1 requires Java 17:
  <https://docs.minecraftforge.net/en/1.20.1/gettingstarted/>.
- Forge 1.20 documents the target `mods.toml` structure and JavaFML entrypoint:
  <https://docs.minecraftforge.net/en/1.20.x/gettingstarted/modfiles/>.
- Forge 1.20.1 documents target-side registries, screens, recipes, and resource
  layout separately:
  <https://docs.minecraftforge.net/en/1.20.1/concepts/registries/>,
  <https://docs.minecraftforge.net/en/1.20.1/gui/screens/>,
  <https://docs.minecraftforge.net/en/1.20.1/resources/server/recipes/>, and
  <https://docs.minecraftforge.net/en/1.20.1/concepts/resources/>.
- Forge documents the production access-transformer location as
  `META-INF/accesstransformer.cfg`:
  <https://docs.minecraftforge.net/en/1.20.x/advanced/accesstransformers/>.
- The JVM specification maps Java 8 classfiles to major 52 and Java 17
  classfiles to major 61:
  <https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html>.

These facts permit metadata and classfile classification. They do not provide
a general migration map and do not demonstrate that a mod is runnable.

## Baseline-generation requirements

Before the first transformation recipe is implemented, a reproducible baseline
process must:

1. Resolve exact, checksum-verified source and target artifacts from official
   repositories.
2. Record every input coordinate, SHA-256, mapping namespace, extraction tool
   version, and policy version.
3. Inventory class owners, access, inheritance, fields, methods, descriptors,
   annotations, and relevant generic signatures without loading classes.
4. Sort and serialize the index canonically.
5. Keep source and target indexes separate; never merge by simple class name.
6. Distinguish Minecraft, Forge/FML, ModLauncher, bundled libraries, and Java
   runtime symbols.
7. Verify generated indexes in tests and fail if an exact pinned artifact
   changes unexpectedly.

Minecraft or third-party bytecode must not be committed merely to make a
baseline convenient. Generated symbol indexes require a separate license and
redistribution review before publication.

## Mapping discipline

A bytecode reference is identified by at least:

```text
owner internal name
member name
JVM descriptor
field/method/invocation kind
source mapping namespace
source platform coordinate
```

The target side must carry the equivalent target coordinate and mapping
context. A match on a simple class or method name is insufficient.

Official mappings in the target development build do not prove that a
production 1.16.5 mod JAR uses the same names. The analyzer must preserve raw
names from the input and establish the actual mapping domain before applying a
recipe. No global package rename, fuzzy member match, or descriptor guess is
allowed.

## Recipe review record

Each future capability should add a review record containing:

- capability ID and version;
- exact source and target symbols;
- artifact evidence used to verify them;
- semantic differences and adapter behavior, if any;
- fixture IDs and expected findings;
- transformation and verification tests;
- required `legacy-runtime` version;
- client, server, multiplayer, persistence, and manual QA requirements.

Until such a record and its tests exist, the symbol remains unsupported.

## Implemented mapping evidence and remaining limits

The mapping loader reads both official client/server mapping tables, source
TSRG1 and target TSRG2, plus ASM declarations from the exact locally generated
official-mapped Forge JARs. Optional matching target FML artifacts extend that
inventory. Default mapping files are SHA-256 pinned; locally built API JARs and
explicit profile inputs have recorded hashes but are not authenticated against
a publisher's API hash. Source/target namespaces remain separate.

The `mappings` command generates exact structural matches and unresolved
reports, including methods, fields, constructors, and descriptor components.
It has no inherited-member search, general Forge migration map, semantic API
diff, transformation recipe, or authority to select a runtime adapter. Same-simple-name
package candidates require manual review. Structural matches are `HIGH`, not
`VERIFIED`, and never authorize a bytecode rewrite.

`-PverifyMappingEnvironment=true` enables an integration test against the real
local pinned inputs; missing inputs fail that test. The portable build keeps
this environment-specific test explicitly skipped. [Mappings](MAPPINGS.md)
records provenance, the `ResourceLocation` composition, profile format, and
current parser limitations. The runtime catalog separately enables only
`simple-content-v1`; this does not change mapping-rule confidence or automatic
rewrite gates.

## First semantic adapter baseline

[Simple content](SIMPLE_CONTENT.md) documents the implemented finite lowering.
Its extractor checks source owners, descriptors, member aliases and declarations
against the same real 1.16.5/36.2.42 inputs. The target adapter compiles against
1.20.1/47.4.23 and uses target-native registrations. This path explicitly handles
the removed item-properties creative-tab setter, Material-based block properties,
and registry-object package change through a validated content plan, not a large
manual rename map. Arbitrary constructors, properties, callbacks, subclasses and
registry usages remain unsupported.
