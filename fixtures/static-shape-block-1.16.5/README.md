# Original static bottom-half shape fixture

Project-authored Forge **1.16.5 / 36.2.42**, version 1.0.0, Java 8 bytecode.
Two owned classes register `legacybridge_static_shape_block:bridge_block`
and its late-resolved native BlockItem. Entry registration is the existing
STONE strength 2/6 and stack32 graph. The public final standalone Block
subclass stores a private static final VoxelShape produced by
`Block.box(0D, 0D, 0D, 16D, 8D, 16D)`. Its constructor only calls super;
both getShape and getCollisionShape return that same immutable field.

Actual source signatures were checked in the local Forge36.2.42 sources:
AbstractBlock callbacks accept BlockState, IBlockReader, BlockPos and
ISelectionContext; Block.box divides each coordinate by16 before native
VoxelShapes.box. No fixture class is loaded during archive tests/conversion.

The authored block model has one bottom-half element [0,0,0] to [16,8,16].
All six faces reference the vanilla stone texture via #all; the upper face
does not cull against a neighboring block. No game assets are copied.
The default blockstate, item model, one shapeless recipe and both languages
remain bounded. No loot or self-drops are invented.

No state-dependent/contextual shape, combination, outline/collision mismatch,
slab placement, stacking, waterlogging, rotation, networking or block entity
behavior is admitted by this fixture. It is not a vanilla slab implementation.
Static shape geometry, solidity/occlusion, selection, collision and item
placement still require native source/target comparisons before promotion.

Build serially with Java17 from repository root:
`gradlew.bat -p fixtures/static-shape-block-1.16.5 build --offline --max-workers=1`.
Four standalone tests inspect archive metadata/class headers/resources only.
This is input to a private candidate; native/client/server/multiplayer gates
remain NOT_RUN, and no public conversion/Apply or world migration is claimed.
