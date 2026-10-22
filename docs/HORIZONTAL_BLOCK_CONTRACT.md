# Preserved horizontal block state contracts

LegacyBridge LB-10 / BLOCK-STATE now has two private implementation slices:
HORIZONTAL_FACING and HORIZONTAL_ROTATE_MIRROR. They extend the
[preserved plain-block foundation](PLAIN_BLOCK_CONTRACT.md), not any of the six
public conversion profiles. Both preserve owned bytecode rather than replacing
registration and callbacks with a bootstrap. Native behavior remains NOT_RUN.

## Implemented variants

The original fixtures are separate Forge 1.16.5 / 36.2.42 projects:
`fixtures/horizontal-block-1.16.5` and
`fixtures/horizontal-transforms-1.16.5`. Each contains one entry class and one
final direct Block subclass, a deferred native BlockItem, one recipe and
en_us/pt_br translations. The first preserves eight methods; the second ten.
They reference the vanilla stone texture and contain no copied Minecraft assets.

The common contract has a single static final property referring to native
HORIZONTAL_FACING, a NORTH default registered after super construction,
a protected state-definition override adding exactly that property, and placement
using the context's opposite horizontal direction. The deferred BlockItem looks up
the registered block only in its supplier. Owned names and bounded strength/stack
literals are data, not a fixture-hash whitelist.

The second variant adds exactly both native `rotate(state, rotation)` and
`mirror(state, mirror)` overrides. Each reads the input state's FACING, applies
the supplied native enum operation, and returns the state's setValue result.
One override alone, ignored arguments, a different property, changed casts,
extra effects or unreviewed enum methods block the whole candidate.
The common eight-method variant keeps inherited no-op rotation/mirror;
the converter does not add behavior absent from the original.

## Mappings and access verification

Exact local official/SRG mappings and API inventories independently resolve
every owner, member, descriptor, opcode and override declaration. The structural
contract's production spellings are guards, not substitute mapping data.
Generated HIGH candidates are not automatically promoted to VERIFIED.

StateContainer becomes StateDefinition and the source BlockItemUseContext becomes
BlockPlaceContext. Calls emitted on BlockState resolve to the actual StateHolder
declaration; horizontal direction resolves to ItemUseContext/UseOnContext.
The two transform overrides are declared on AbstractBlock/BlockBehaviour, not
Block itself. The compiler checks the real inherited declaration, visibility,
static/final/native/abstract flags and descriptor before admitting an alias.

Protected stateDefinition and registerDefaultState access still pass receiver
dataflow checks. A dedicated HORIZONTAL_BLOCK linkage policy independently
re-reads the complete source and serialized target contract. This complete-contract
gate is requested only by the private horizontal route; existing compiler/profile
admission remains unchanged. The final Direction, Rotation and Mirror
enum declarations are checked where used.

Only the exact STONE-properties pair already audited by the plain-block slice
changes instruction shape. HorizontalBlockContract uses a checked deep projection
solely to reuse registration validation. That projection is never emitted:
the actual NEW and constructor still target the original owned subclass.
Everything else retains its body under exact remapping, including debug data.
ASM, source linkage, target linkage, whole-graph equality, ownership/hash checks
and reopened linkage are separate gates.

## Resource contract

The private resource gate requires exactly facing=north/east/south/west variants,
one shared local block model and y rotations 0/90/180/270 respectively. North may
omit its zero rotation. Multipart, additional properties, unknown models,
alternative arrays and extra variant fields are outside this finite language.
The existing public default-state resource gate remains unchanged.

Admitted source assets and data are byte-identical. The only added gameplay
resource is the explicit, logged additive pickaxe mining tag from the existing
implemented adapter. Original global Minecraft tag overrides remain refused.
No loot table, drops, creative membership, renderer or shape is invented.

The fixture uses cube_all, so screenshots cannot show which direction is active.
Future native QA must inspect the actual facing property, not infer it from the
symmetric texture. Translation files are validated when present, not required.

## Reviewed native semantics

The exact source archives used for inspection are unchanged:

| Minecraft and Forge | Source archive SHA-256 |
| --- | --- |
| 1.16.5 / 36.2.42 | bbe2d148f96694271c43fc2ba584463d75d9ffc490abd0469ae66b831293d376 |
| 1.20.1 / 47.4.23 | 4bd6656a7cbe3809da68f3b709ca0f2fa186575f58538668c58ce30cb38c4932 |

Both Block constructors invoke createBlockStateDefinition before assigning the
stateDefinition field. The admitted override uses only the initialized static
property, never instance state. Both context APIs fall back to NORTH without a
player, making this opposite placement SOUTH.

StateHolder.setValue changes from identity comparison to equals. This contract
uses native Direction enum singletons only; no equivalence for arbitrary
Comparable values or user-defined properties is inferred. Native enum additions
such as target CODEC, serialized IDs and component symbols are not admitted.

The reviewed orientation matrix uses input order N, E, S, W:

| Operation | Output order |
| --- | --- |
| Rotation NONE | N, E, S, W |
| CLOCKWISE_90 | E, S, W, N |
| CLOCKWISE_180 | S, W, N, E |
| COUNTERCLOCKWISE_90 | W, N, E, S |
| Mirror NONE | N, E, S, W |
| LEFT_RIGHT | S, E, N, W |
| FRONT_BACK | N, W, S, E |

The dedicated API audit test follows bounded symbolic paths through the actual
native bytecode, with explicit enum-table assumptions and refusal of unreviewed
operations. This is static evidence, not class loading, native execution or
behavioral qualification. It never invokes original mod classes or enum initializers.

Pinned source spans: legacy Block 151–158 and target Block 168–173 (construction);
legacy StateHolder 100–116 and target 102–114 (setValue); legacy Rotation 65–80
and target 70–85; legacy Mirror 34–40 and target 43–49. Official
[1.16 state documentation](https://docs.minecraftforge.net/en/1.16.x/blocks/states/)
and [1.20.1 state documentation](https://docs.minecraftforge.net/en/1.20.1/blocks/states/)
provide lifecycle context; exact local sources govern the recipes.

Target BlockItem still emits BLOCK_PLACE, absent from source placement.
This necessary native difference is documented, not hidden by platform patches.

## Service and qualification boundaries

`LegacyEngineService.planHorizontalBlock` shares the bounded worker, queue,
selected-input hash, stage progress and cancellation infrastructure.
It invokes the reusable engine, never CLI.main, stdout parsing or System.exit.
`LogicPreservingConverter.planHorizontalBlock` returns proof and candidate hash;
the temporary serialized JAR is reopened then discarded. Neither variant creates
a public output, staging/install authority or a loaded mod.

The report distinguishes HORIZONTAL_FACING from HORIZONTAL_ROTATE_MIRROR and
records source, target and reopened proofs. Two new OFFLINE operations generate
the private manifest and bindings; executable runtime adapters and old descriptors
are unchanged. No fixture, old platform code or private API inventory is packaged.

Advanced planning still requires explicit complete local mapping/API inputs.
No hidden Gradle-cache lookup, download or new cache-free product claim is added.
No original mod was executed, no Minecraft/server/UI was launched, and no mod was
installed, acquired or published during these slices.

[Artifact-bound evidence and actual test outcomes](evidence/horizontal-block-2026-10-01.json)
are authoritative for this implementation. Full unfiltered suites and native
client/server, placement, persistence, structure rotation, multiplayer, asset
loading and drops remain pending independently of build/static success.

## Next implementation

Continue BLOCK-STATE with STATIC_BOTTOM_HALF_SHAPE: prove the literal
Block.box(0,0,0,16,8,16) outline/collision cuboid and matching visual model,
including removed Material and target solidity/default predicates.
Do not generalize the full-cube proof to arbitrary geometry. Interaction/drop,
ticks/redstone, plants, additional properties, BlockEntities, worldgen and general
callback composition remain separate unsupported contracts. Native acceptance and
existing requires remain pending; BLOCK-STATE is PARTIAL, not completed.
