# Compatibility policy

Current additive developer slices are documented separately:
[owned logic](docs/LOGIC_PRESERVING_PROFILE.md),
[lifecycle](docs/LIFECYCLE_PRESERVING_PROFILE.md), [food](docs/FOOD_ITEM_PROFILE.md)
and [recipe data](docs/DATAPACK_RECIPES_PROFILE.md). The historical initial-profile
counts below are not current whole-project totals or evidence for these slices.
The player default is still `simple-content-v1`; no universal compatibility claim.

## Current verdict

LegacyBridge `0.1.1-SNAPSHOT` implements static analysis, conservative mappings,
and one fail-closed conversion profile: `simple-content-v1`. It accepts only a
finite declarative registration/resource language, not all mods that appear
to contain simple items or blocks. See [Simple content](docs/SIMPLE_CONTENT.md)
for the exact grammar and separate runtime-validation evidence.

The general analyzer and mapping reports retain their inspection-only state:

```text
conversionStatus = NOT_IMPLEMENTED
```

Those reports are not conversion authorities. The separate conversion report
uses `CONVERTED` or `ABORTED`; a produced artifact requires the enabled
`simple-content-v1` runtime capability with exact catalog bindings and is not
automatically runtime-validated. Schema-v2 conversion reports mark uncovered
behavior `compatibilityStatus=UNSUPPORTED`, with no output artifact.

The previous `0.1.0-SNAPSHOT` example passed Forge 47.4.23 development client/server
checks for discovery, registration, crafting and client assets. That historical
evidence does not validate the new bindings or STONE mining adaptation; see
[the old execution record](docs/SIMPLE_CONTENT_VALIDATION.md) and the separate
[compatibility-layer record](docs/COMPAT_LAYER_VALIDATION.md).

For `0.1.1-SNAPSHOT`, the root build passed 257 tests without failures or skips,
and the reconverted fixture passed the dedicated-server gate on 2026-09-05 at
14:08:06 (America/Sao_Paulo), including native iron-pickaxe speed and BASEDRUM
instrument checks. The matching client gate passed at 14:09:52 with three baked
models, two atlas sprites, both languages, resolved sound and two creative tabs.
These are fresh checks of the reconverted fixture, not broader compatibility.

## Why support must be progressive

Forge 1.16.5/36.x and Forge 1.20.1/47.x are not binary-compatible platforms.
Differences extend beyond package or method names and include lifecycle,
registries, mappings, networking, rendering, resource/data formats, JVM level,
Forge internals, and ModLauncher behavior. Automatic text replacement or a
large set of guessed legacy stubs would produce artifacts that appear valid
while failing at linkage time or, worse, corrupting game state.

LegacyBridge therefore uses deny-by-default capability coverage. A mod becomes
eligible only when every instruction, declaration and resource is covered by
its selected profile. The following general conditions are necessary, but do
not independently authorize conversion:

- code owned by the mod itself;
- a Java/JVM facility valid on Java 17;
- an exact API symbol verified to remain valid on the target; or
- an exact, tested LegacyBridge transformation capability.

Unknown requirements block conversion. A lack of detected problems is not
equivalent to compatibility.

## Support tiers

### Tier 0 - inspection foundation

The project structure, immutable report states, target runtime metadata, typed
capability catalog, fixtures, documentation, logging, and tests.

### Tier 1 - static analysis

The `analyze` command safely inventories a bounded untrusted
JAR, parses metadata, inspects classfiles, classifies namespaces and feature
markers, applies a conservative policy, and emits a deterministic report. It
does not transform or execute mods. Opaque custom classfile attributes and
nested JAR contents are not interpreted.

### Tier 2 - metadata/resource-only conversion

Eligible inputs must be proven to contain no executable legacy dependency that
requires rewriting. Metadata and resources may be repackaged only after exact
target validation. A code-free-looking archive is not automatically eligible;
language providers, services, nested JARs, signatures, and unusual loaders must
also be resolved.

### Tier 3 - exact bytecode recipes without runtime shims

Each accepted rule matches a complete source symbol and invocation shape and
rewrites to an existing, verified target API. Bare method-name, descriptor-only,
or namespace-wide substitutions are prohibited.

### Tier 4 - exact recipes backed by runtime adapters

A conversion may call an adapter in `legacy-runtime` only when the adapter has
a stable capability ID, documented semantics, target-side tests, and a version
constraint inserted into the converted mod's metadata.
The current contract additionally requires exact runtime version, revision,
class, method and descriptor bindings, checked before registration. Every
transformation references an implemented catalog operation. Reconvert old
unbound output; missing or mismatched bindings are unsupported, not tolerated.

The implemented `simple-content-v1` path belongs here: static extraction proves
capture-free `DeferredRegister` suppliers for plain items, blocks, BlockItems,
and limited sound events; concrete adapters recreate native registrations and
the two fixture creative categories. Registered sound events have synthetic,
not live gameplay, motivation. Every other behavior is refused. It does not
enable a general Tier 2 or Tier 3 conversion fallback.

Current properties are restricted to COMMON items, zero durability, no fire
resistance and stack size 1..64; plain STONE blocks with STONE sound and bounded
strength; no correct-tool/noOcclusion flags; and absent, MISC or BUILDING_BLOCKS
membership. MISC to INGREDIENTS is a disclosed category migration, not identical
tab presentation. Other previously speculative presets are now unsupported.
All source vanilla tag declarations reject. A named adapter emits an additive
target pickaxe-mining tag for STONE, because material-based mining became
tag-based; this necessary new resource is recorded separately from preserved
source bytes. See [actual API evidence](docs/COMPAT_API_BASELINES.md).

### Later tiers

Broader families such as lifecycle, registry, capability, networking, client
rendering, and saved-data migration are considered independently. Passing one
family does not imply support for another.

## Concrete binary break: registries

Direct inspection of the pinned artifacts already proves a binary break in the
registry family: `DeferredRegister.register` returns
`net.minecraftforge.fml.RegistryObject` on 1.16.5 but
`net.minecraftforge.registries.RegistryObject` on 1.20.1;
`net.minecraft.util.ResourceLocation` moves to
`net.minecraft.resources.ResourceLocation`; and
`net.minecraftforge.event.RegistryEvent$Register` is redesigned as
`net.minecraftforge.registries.RegisterEvent`. The old
`IForgeRegistryEntry#setRegistryName` model is absent from the target.

Arbitrary use of these APIs remains unsupported. The finite content profile
replaces proven declarative registration graphs instead of preserving old
registry API calls or supplying replacement platform classes. See
[docs/API_BASELINES.md](docs/API_BASELINES.md) for the exact verified anchors.

## Capability acceptance gate

Every future capability requires all of the following:

1. Exact source owner, name, descriptor, access, and mapping context verified
   against Minecraft 1.16.5 / Forge 36.2.42 artifacts.
2. Exact target symbol or adapter verified against Minecraft 1.20.1 / Forge
   47.4.23.
3. A deterministic transformation recipe with no class loading.
4. Positive, negative, malformed-input, and already-targeted fixtures.
5. A report finding that identifies the recipe and evidence used.
6. Package verification after transformation.
7. Target-side tests appropriate to the behavior, including dedicated-server
   and client/manual validation when the feature crosses those boundaries.

Build success alone cannot satisfy items 6 or 7.
Each implemented operation must also document `legacyApi`, `targetApi`,
`transformation`, `behaviorDifferences` and `knownLimitations`. An unimplemented
area must remain unsupported rather than registering a stub. The current
catalog and eight-area organization are in [Compatibility layer](docs/COMPAT_LAYER.md).

## Initially unsupported or manual-review features

The following are outside the initial conversion subset:

- Java or JavaScript coremods;
- Mixins targeting Minecraft, Forge, ModLauncher, or third-party internals;
- access transformers whose targets changed or cannot be verified exactly;
- custom ModLauncher transformation services or language providers;
- JNI/native libraries;
- signed JARs;
- encrypted, malformed, path-ambiguous, or limit-exceeding archives;
- nested JARs and dependency extraction;
- multi-release JAR behavior not modeled for Java 17;
- reflection, method handles, generated bytecode, or string-built class names
  that prevent complete static resolution;
- client-only references whose physical-side safety cannot be proven;
- direct dependencies on unsupported third-party mods;
- entities, TileEntity/BlockEntity, custom renderers, world generation,
  dimensions, packets, capabilities, custom GUIs, shaders, and custom creative tabs;
- source `data/minecraft/tags/**`, nondefault unproven material/item properties,
  local Ogg/file sounds, animation metadata and custom model behavior;
- automatic migration of worlds, registries, saved data, networking protocols,
  configs, or capabilities.

Detection is conservative. Some flagged mods may be manually portable, but
that does not make them safe for automatic conversion.

## Artifact production policy

The current release produces an output mod JAR only after the complete selected
content grammar succeeds. It refuses artifact production if:

- analysis is partial or rejected;
- any required capability is unknown or unsupported;
- a signature would become invalid without an explicit documented policy;
- transformed bytecode cannot be verified;
- target metadata cannot express all required dependencies; or
- the output would misleadingly resemble a runnable release.

A future experimental override, if one is ever added, must be opt-in, visibly
mark its artifact and report as incomplete, and must not be the normal path.

## Meaning of command success

An `analyze` exit code of zero means only that static analysis completed without
an `ERROR` or `BLOCKER` finding. It does not mean the mod can be converted or
run. Conversion and execution require separate explicit states and validation
evidence.

A `convert` exit code of zero means a validated subset artifact and conversion
report were produced, not that Forge discovery, crafting, assets, or broader
gameplay were tested for that particular mod. Exit code `1` reports a subset
refusal and produces no mod JAR. Existing output JARs are never overwritten.
