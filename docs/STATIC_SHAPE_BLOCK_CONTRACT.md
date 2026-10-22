# Preserved fixed block geometry and self drop

LB-10 / BLOCK-STATE implements two separate private resource variants over one
closed native Block subclass: STATIC_BOTTOM_HALF_SHAPE and
STATIC_SHAPE_SELF_DROP. Neither changes the six public conversion profiles,
public Apply, runtime identity or the existing horizontal/plain-block contracts.
Static proof is not native behavior qualification.

## Owned program and transformation

Both authored fixtures contain two classes and eight methods: the registration
entry and a final direct Block subclass. The subclass has one private static
final VoxelShape, initialized only by Block.box(0D,0D,0D,16D,8D,16D). Its
constructor only calls super(Properties). Both getShape and getCollisionShape
return exactly that field and never inspect state, world, position or context.

Class names, registry names, field names and bounded strength/stack literals are
data, not a fixture-hash allowlist. Additional callbacks, state, inheritance,
geometry, dynamicShape, offset, noCollision, noOcclusion, predicates, side effects,
handles or external dependencies are refused. Negative zero, NaN, infinity,
reversed bounds and near-identical bounds do not match the literal proof.

StaticShapeBlockContract re-reads the complete source and target graphs. Its
deep registration projection only reuses the existing admission checks and is
never emitted. The actual subclass, fields, constructor, callbacks, literals and
debug information are preserved. Only the exact Material.STONE/of pair changes
instruction shape; Material is not given a fabricated target class.

A new OFFLINE properties operation qualifies that five-call expansion for the
half-cuboid. It reuses the implemented canonical calls, but does not reuse the
previous full-cube descriptor's semantic permission. All existing catalog
descriptors stay unchanged. Native renames use exact reviewed mappings and
declarations; the offline linkage policy independently repeats the whole-program
proof. Missing, private, final, static or wrong-descriptor native declarations
cannot be treated as ordinary owned methods.

The shared bounded service exposes planStaticShapeBlock. It serializes a private
candidate, reopens it, validates ASM/linkage/access, resources, metadata and byte
ownership, then discards it. Cancellation cannot publish a partial result.
The caller receives proof/hash, not an export, installation receipt or loaded mod.

## Geometry and native defaults

The exact pinned source archives remain Forge 36.2.42 for Minecraft 1.16.5 and
Forge 47.4.23 for Minecraft 1.20.1. Names alone do not establish equivalence:

- Legacy box construction normalizes reversed AABB bounds; target construction
  rejects them. Degeneracy and rounding rules also differ.
- Legacy findBits uses floor and a fixed tolerance; target uses round and a
  scale-adjusted tolerance. The admitted coordinates normalize to exact
  0, 0.5 and 1; no nearby or arbitrary geometry is admitted.
- Legacy solidity comes from Material; target solidity uses cached collision
  geometry. For this exact box, average extent is 5/6, above the target
  0.7291666666666666 threshold. The source inspection predicts solid=true after
  cache initialization, not a general result for partial shapes.
- Source inspection predicts non-full collision and solid-render, false
  conductor/suffocation/view-blocking, LAND/AIR pathfindability, skylight and
  zero light blocking for the finite default state. Real lighting, pathfinding,
  support, rendering and world interaction still need native comparison.
- Target BlockItem emits BLOCK_PLACE; this native difference is retained.

The bounded API audit follows trusted bytecode as data. Requests to allocate/fill
a shape grid and modeled cache inputs are explicit boundaries, not execution of
Minecraft, mod initializers or the native shape cache. Unknown operations block
the audit. Passing it is not proof of movement/collision in a running game.

## Matching visual resources

The resource graph contains exactly the local registered block model and its
BlockItem model. The block model uses minecraft:block/block, one element from
[0,0,0] to [16,8,16], six faces and the audited stone texture. Five boundary faces
have matching cullface; the top at Y=8 has no cullface. The item inherits exactly
that block model. The blockstate has only the unrotated default variant.

No alternate model, multipart, element rotation, custom UV, tint, shade,
forge_data or geometry override is admitted. Existing resource grammars are
unchanged. Resources are preserved byte for byte; the generated additive pickaxe
mining tag remains an explicit logged operation, not a silent global override.

Both pinned model parsers produce identical omitted UVs: top/bottom
[0,0,16,16], sides [0,8,16,16]. The native block parent and slab reference models
are byte-identical between the inspected client archives. They were inspected,
not copied into the fixture or product. AO inheritance and Forge face metadata
changed outside this finite default case.

Official [model provider documentation](https://docs.minecraftforge.net/en/1.20.x/datagen/client/modelproviders/)
describes element/culling controls; exact local parser sources govern this
contract, not the current documentation of another version.

## Optional existing self drop

The separate fixture static-shape-drop-1.16.5 supplies one existing table at
data/<modId>/loot_tables/blocks/<registryName>.json. StaticShapeLootContract
accepts only type minecraft:block, one pool with numeric rolls=1, one local
minecraft:item entry referring to the proved BlockItem, and one
minecraft:survives_explosion condition. Omitted bonus_rolls is zero in both
pinned parsers. The table's path/hash/contract are independently compared after
serialization.

Absent tables stay absent. Other tables, functions, references, conditions,
weights, counts, NBT, random_sequence, global loot modifiers and external IDs
remain unsupported. Parsing is size-bounded, strict UTF-8 and duplicate-key
rejecting. No loot data is generated or silently removed. Unchanged loot bytes
are represented by the complete artifact ownership plan, not a fictitious
RESOURCE_REWRITE.

This does not promise identical explosion drops or random sequences across
versions. The condition uses nextFloat() <= 1/radius, or true when radius is
absent, in both versions. However, context/RNG construction changed: the target
distinguishes DESTROY from DESTROY_WITH_DECAY, and its TNT explosion-drop-decay
game rule defaults to false. Those platform differences are recorded, not hidden
with a replacement loot system.

The optional self-drop contract does not accept DATA-LOOT/DATA-CONDITIONAL or
make arbitrary loot composition available. Survival/creative breaking, tools,
explosions with/without decay, reload, conservation and multiplayer remain
separate native gates. No old-world migration is claimed.

## Evidence and continuation

[Versioned results and hashes](evidence/static-shape-block-2026-10-01.json)
separate fixture archive tests, private conversion, static validation, packaging,
and native NOT_RUN results. Original mod classes are never executed during
analysis or conversion. Complete explicit local mapping/API inputs remain
required for these advanced private operations; no new cache-free installation
claim or redistribution of private inventories is made.

BLOCK-STATE remains PARTIAL. Next, implement the separately audited
[BOOLEAN_TOGGLE_USE interaction](BLOCK_INTERACTION_AUDIT.md); do not repeat static geometry or silently broaden it into
stateful slabs, placement merging, fluid behavior, ticks/redstone, BlockEntities,
worldgen or general loot. Native acceptance and prerequisite gates stay pending.
