# Boolean block interaction API audit

Status: **private implementation delivered**; [scope and limits](BOOLEAN_TOGGLE_BLOCK_CONTRACT.md).
This audit is not public-profile or native gameplay acceptance.
Continuation belongs to LB-10 / BLOCK-STATE. This does not create another contract
cut, satisfy its prerequisites, or authorize Minecraft execution/public Apply.
The completed fixed-shape/self-drop slices are documented separately in
[their contract](STATIC_SHAPE_BLOCK_CONTRACT.md).

## Closed first fixture

Use an authored final direct Block subclass with one BooleanProperty named active,
default false, and the existing native Block/BlockItem registration pattern.
The complete graph should contain two classes/eight methods. Its only use
callback returns SUCCESS on the logical client without writing the world.
On the server branch it cycles that property and invokes exactly one
setBlock(callbackPosition, cycledCallbackState, 3), then returns CONSUME.

World, state and position must be the actual callback arguments. No other position,
chunk lookup, player inventory, ItemStack mutation, packet, async task, sound,
GameEvent, custom neighbor callback, counter or static mutable gameplay state is
admitted. A consumed action is not proof that setBlock succeeded; the native
boolean result is ignored in this exact source pattern.

Two complete active=false/true visual variants must expose the state difference.
Audit both vanilla texture/model references before admitting them. Start from a
plain full cube, not a composition with horizontal orientation, the half-shape,
self-drop, redstone or a BlockEntity. No fixture hash allowlist.

## Exact native declarations inspected

The same pinned source archives were inspected read-only:

- Forge 1.16.5-36.2.42 mapped official sources:
  SHA-256 bbe2d148f96694271c43fc2ba584463d75d9ffc490abd0469ae66b831293d376.
- Forge 1.20.1-47.4.23 mapped official sources:
  SHA-256 4bd6656a7cbe3809da68f3b709ca0f2fa186575f58538668c58ce30cb38c4932.

Prepared API declaration JARs were also inspected with javap -p -s, without
loading fixture classes. Production names are composed from exact official/SRG data by the private
compiler; independent source/target declaration checks remain mandatory.

| Operation | Legacy owner/type | Target owner/type |
| --- | --- | --- |
| create(String) | net/minecraft/state/BooleanProperty | net/minecraft/world/level/block/state/properties/BooleanProperty |
| isClientSide:Z | net/minecraft/world/World | net/minecraft/world/level/Level |
| cycle(Property):Object | net/minecraft/state/StateHolder | net/minecraft/world/level/block/state/StateHolder |
| setBlock(BlockPos,BlockState,int):boolean | net/minecraft/world/World | net/minecraft/world/level/Level |
| SUCCESS / CONSUME | net/minecraft/util/ActionResultType | net/minecraft/world/InteractionResult |
| use callback declaration | net/minecraft/block/AbstractBlock | net/minecraft/world/level/block/state/BlockBehaviour |

isClientSide is a public final instance field, not a method. cycle's erased
return is Object in both versions: preserve the BlockState CHECKCAST. A bytecode
owner may reference inherited members through BlockState, so resolution/access
must be proved rather than replacing an arbitrary owner name.

Source use descriptor:

```text
(Lnet/minecraft/block/BlockState;Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/util/Hand;Lnet/minecraft/util/math/BlockRayTraceResult;)Lnet/minecraft/util/ActionResultType;
```

Target use descriptor:

```text
(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;
```

BooleanProperty contains two values; StateHolder.cycle returns setValue of the
next allowed value. World/Level's three-argument setBlock delegates with update
limit 512; flags 3 request neighbor notification and client updates in each
inspected implementation. This does not prove identical effects in every world.

The vanilla Lever uses the client SUCCESS/server CONSUME division and
cycle/setBlock(...,3), but also has sounds, particles and neighbor logic that
are deliberately outside this fixture. Target Lever adds activation GameEvents;
do not copy that call into a converted mod that did not have it.

## Context differences and unsupported paths

The intended caller is the native use dispatch, not a new networking adapter.
ServerPlayNetHandler / ServerGamePacketListenerImpl and
PlayerInteractionManager / ServerPlayerGameMode apply native main-thread,
reach, mayInteract, spectator and Forge RightClickBlock cancel/use gates.

Target dispatch additionally has sequence acknowledgements, feature checks and
different hit/interaction constraints. InteractionResult adds CONSUME_PARTIAL
and stat distinctions. Only exact SUCCESS/CONSUME are admitted by this candidate. No cross-version
wire protocol or custom call to this callback is covered.

Target Level.markAndNotifyBlock invokes onBlockStateChange. The inherited
IForgeBlock default inspected here is empty; any custom implementation must
block this finite contract. Native neighbor reactions and other mods remain
outside a static equivalence claim.

## Implementation and validation sequence

1. Build the small real Forge 36.2.42 fixture and archive-only tests serially.
2. Add a separate complete source/target structural proof and scoped mappings,
   preserving fields, branch, receiver/argument provenance and invocation opcodes.
   Check required native override declarations even when a generic JVM override
   walk would skip absent/private methods.
3. Prove two boolean states, false default and exactly one server-side local write.
   Unknown callbacks, external references, reflection, concurrency and context
   changes must block the whole candidate. Audit literal Boolean.FALSE or the
   exact boxing operation actually emitted, not an assumed compiler shape.
4. Validate the complete two-variant resource graph, artifact ownership, metadata,
   ASM/linkage/access and reopened proof. Retain debug information and original
   resource bytes; no public profile/export/Apply change.
5. Add negatives for reversed branch, write before side guard, duplicate write,
   displaced position, wrong world/state/property, flags other than 3, missing
   CHECKCAST, wrong access/descriptor, unknown methods and missing/extra resources.
   Check renamed-owned variants, bounded service parity and safe cancellation.
6. Run focused checks and existing regressions, assemble/audit one public JAR,
   record hashes and update the canonical cursor. Counts are not supported mods.
7. Keep native checks NOT_RUN until separately authorized: one/two toggles,
   two players, reconnect/save-load, main/offhand, sneak, creative/survival/
   spectator, Forge cancel/DENY, reach/protection/write failure, synchronization
   and inventory/drop invariants.

No game, server, world, installation or library was changed by this audit.

