# Fixtures

`iron-helmet-dispenser-1.16.5` is a separate authored one-class/four-method
original whose helper calls native ArmorItem.dispenseArmor. Five artifact tests
inspect Java 8 bytes, metadata/resources and seven NOT_RUN native scenario
declarations (test-only, not in the JAR). It motivates the
[closed dispenser operation](../docs/IRON_HELMET_DISPENSER.md), not a conversion
recipe or new real-mod qualification. It is not bundled in the product.

`iron-helmet-1.16.5` is an authored one-class/three-method native iron-helmet
fixture for the [read-only equipment inspection](../docs/IRON_HELMET_INSPECTION.md).
It has one recipe, generated-model/native-texture references and two translations.
Its four archive tests and the engine's structural/API/linkage checks never
execute the original. The result remains UNSUPPORTED: contextual slot/type
construction and inherited use need semantic recipes. It is not included in
LegacyBridge.jar and does not alter the frozen real-mod corpus.
It also motivates the [fixed empty-slot runtime operation](../docs/IRON_HELMET_EMPTY_SLOT_ADAPTER.md).
Tests compare compiled native/adapter bodies as data; that operation does not
make the original convertible or qualify inherited equipment effects.

`static-shape-block-1.16.5` and `static-shape-drop-1.16.5` are separate authored
fixtures for exact bottom-half outline/collision geometry and its optional existing
local self-drop table. Both preserve two classes/eight methods. Their archive tests
never execute a mod; bytecode/resource checks do not prove collision or loot behavior
in a game. The source-derived TNT/RNG differences remain explicit. Neither fixture,
its tests nor native game sources are bundled in the product.
[Private contract and evidence](../docs/STATIC_SHAPE_BLOCK_CONTRACT.md).

`horizontal-block-1.16.5` and `horizontal-transforms-1.16.5` are separate authored
fixtures for private horizontal state/placement and the complete native
rotation/mirror pair. Each retains two classes; the variants contain eight and
ten methods respectively. Four facing resource variants are validated without
executing original code. They are not new public conversion profiles and are
not packaged in LegacyBridge.jar. [Contract and evidence](../docs/HORIZONTAL_BLOCK_CONTRACT.md).

`owned-constructors-1.16.5` is the authored finite `this(...)`/`super(...)`
fixture for a private static candidate, not a new public profile. It retains
three classes/eleven methods, helper state and resources; no original is executed
during inspection. [Contract and limitations](../docs/OWNED_CONSTRUCTOR_DELEGATION.md).

`network-snapshot-1.16.5` is an original two-class, fixed-eight-byte S2C fixture
with a strict protocol/direction and ordered connection-bound snapshots. The
`network-runtime-probe-1.20.1` module is separate native QA, never public contents
or conversion-time execution. [Contract](../docs/NETWORK_SNAPSHOT_PROFILE.md),
[separate validation](../docs/NETWORK_SNAPSHOT_VALIDATION.md).

`datapack-recipes-1.16.5` is the authored six-recipe fixture for the opt-in
[versioned resource slice](../docs/DATAPACK_RECIPES_PROFILE.md). The independent
`datapack-runtime-probe-1.16.5` and `datapack-runtime-probe-1.20.1` observe native
recipe matching/assembly and two full resource reloads, including local tag
replacement and selection restoration. None is a player dependency or public
LegacyBridge payload. Tests use `-PverifyDatapackEnvironment=true` and
`-PdatapackMappingProfile=<explicit-local-properties>`. Runtime execution is
separate from static conversion; see [evidence](../docs/DATAPACK_RECIPES_VALIDATION.md).

Only project-authored test sources belong in this directory. Local fixture use
is authorized by the project task; the repository's All Rights Reserved license
is not a public binary redistribution grant. Generated JARs stay under ignored
build directories. See the [versioned corpus](../corpus/README.md).

`food-item-1.16.5` is a new authored two-class native food constructor fixture.
The separate `food-runtime-probe-1.16.5` and `food-runtime-probe-1.20.1` projects
observe server-side native consumption/drop/pickup endpoints and are never
converted or packaged in the product. Genuine conversion negatives use inert
bytecode mutations. Opt-in tests use `-PverifyFoodEnvironment=true` and
`-PfoodMappingProfile=<explicit-local-properties>`. See the
[closed profile](../docs/FOOD_ITEM_PROFILE.md) and
[separate evidence](../docs/FOOD_ITEM_VALIDATION.md); no real corpus mod is unlocked.

The new `lifecycle-content-1.16.5` and source-form variant
`lifecycle-inferred-1.16.5` are **one authored mod**, with native deferred item
registration, late lookup, COMMON setup/config and a permissioned integer command.
Two `lifecycle-runtime-probe-*` projects are test-only runtime observers, never
converted or included in the public product. Negative JARs are generated from
the original fixture's inert bytes by the integration tests. See
[profile](../docs/LIFECYCLE_PRESERVING_PROFILE.md) and
[runtime evidence](../docs/LIFECYCLE_PRESERVING_VALIDATION.md).

- `synthetic-forge-1.16.5/` contains project-authored source metadata used to
  assemble tiny Forge-shaped JARs in temporary test directories. The tests
  generate Java 8 classfiles with ASM; no fixture class is loaded.
- The basic generated JAR covers `mods.toml`, Forge `@Mod` bytecode, hashing,
  source-profile evidence, CLI stdout/file output, and the non-execution
  boundary.
- The feature-rich generated JAR adds symbolic Forge/Minecraft references,
  a declared third-party dependency, manifest data, Mixin JSON, the canonical
  access transformer, `coremods.json`, assets, recipe, loot, worldgen, and
  dimension paths. These are inert analyzer inputs, not a runnable mod.
- Additional tests generate malformed, wrong-case, oversized, truncated, or
  adversarially shaped entries in temporary directories; such artifacts are
  intentionally not kept as repository binaries.
- Mapping tests construct small synthetic ProGuard/TSRG1/TSRG2 inputs and ASM
  source/target API JARs in temporary directories. They cover exact symbols,
  constructors, descriptors, removed members, adapters, ambiguous aliases,
  package candidates, staticness, and disabled automatic rewriting. They do
  not copy Minecraft bytecode or a complete mapping dataset into the repo.
- `legacy-converter:generateMappingSmokeFixture` creates an inert feature-rich
  JAR under the module's `build/mapping-smoke/` directory for installed CLI
  checks. It is an analysis fixture, not a runnable compatibility example.
- The real mapping environment test is opt-in through
  `-PverifyMappingEnvironment=true`. It reads local pinned development
  artifacts; those artifacts are not redistributable test fixtures here.
- `simple-content-1.16.5/` contains an original, actually Forge-compiled Java 8
  example with one ordinary item, one plain block and its BlockItem, and one
  crafting recipe. Its build produces a production-SRG source JAR; Forge/Minecraft
  classes are not bundled. The fixture build has four resource/source tests.
  Conversion integration reads this JAR without loading its classes. Build it
  before using `-PverifyMappingEnvironment=true` for the converter integration
  suite; commands and runtime checks are in [Simple content](../docs/SIMPLE_CONTENT.md).
- Simple-content tests also construct inert ASM registration graphs and resource
  JARs covering successful lowering, unsupported behavior, malformed content,
  deterministic output and refusal without an artifact. These are separate from
  the broad feature-rich analyzer fixture, which remains non-convertible.
- `local-private/` is ignored and reserved for legally obtained local mod JARs.
- `logic-preservation-1.16.5/` is a separate genuine Forge 36.2.42 / Java 8 fixture:
  one native item subclass, owned helpers/interface/overrides/state/control flow
  and method handles. It does not replace the simple-content fixture.
- `logic-runtime-probe-1.16.5/` and `logic-runtime-probe-1.20.1/` are isolated
  runtime-only test mods. They observe the original and converted fixture in
  their respective real Forge servers; they are never converter dependencies or
  public-JAR contents. Static tests never load the original fixture classes.
- Opt-in genuine logic conversion tests use `-PverifyLogicEnvironment=true` and
  `-PlogicMappingProfile=<explicit-local-properties>` after building that fixture.
  See [profile](../docs/LOGIC_PRESERVING_PROFILE.md) and
  [separate evidence](../docs/LOGIC_PRESERVING_VALIDATION.md). These new fixtures do
  not change the locked earlier corpus records or certify additional real mods.

- `client-config-1.16.5/` and `server-config-1.16.5/` are separate original,
  production Forge 36.2.42 fixtures for private configuration candidates. The
  latter has four classes/sixteen methods, native SERVER schema and observable
  started/stopped snapshots. Neither is a new public conversion profile or
  evidence of game behavior. Integration requires
  `-PverifyCompositionEnvironment=true` and explicit `-PlogicMappingProfile`.
  See [CLIENT](../docs/CLIENT_CONFIG_SCOPE.md) and
  [SERVER](../docs/SERVER_CONFIG_SCOPE.md). No new third-party corpus release
  is admitted and no original mod classes execute in static checks.

Do not commit third-party mod JARs without explicit redistribution permission.
