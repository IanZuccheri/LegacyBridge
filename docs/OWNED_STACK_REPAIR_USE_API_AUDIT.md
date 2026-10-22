# Owned stack repair through native item use

The LB-10 / ITEM-EQUIPMENT slice is **OWNED_STACK_REPAIR_USE**.
It adds one finite native `Item.use` override to the independently proved
[owned stack damage helper](OWNED_STACK_DAMAGE_API_AUDIT.md). The
original has two final owned Java-8 classes and six methods. This audit establishes
source observations, not native behavior. Its separate
[implementation contract](OWNED_STACK_REPAIR_USE_CONTRACT.md) now defines the
private mapping/graph/linkage route; qualification remains independent.
The original was built serially and offline on 2026-10-04:
four archive-only tests passed, with no failures or skips. The 6,687-byte JAR has
SHA-256 420f710dadb3134c500dc1c1fc7077ed183758db6531416093454cca72b90d2a.
A read-only javap check confirms the original native callback, Player-owned
inherited hand-lookup callsite, logical-side branch and same-stack returns.
See the [delivery ledger](evidence/owned-stack-damage-candidate-2026-10-04.json).
This build is not a converted candidate or runtime observation.

The scope is a deliberate authored free-repair action, not an anvil operation,
repair-material consumption, mending or arbitrary item-use compatibility.
It does not repair tools by intercepting their existing damage hooks.

## Finite callback

The override receives the native world, player and hand. It obtains exactly
`player.getItemInHand(hand)` once and retains that same reference. On the logical
client it returns `ActionResult.success(stack)` without calling the repair helper
or writing any stack state. On the logical server it calls `repairOne(stack)`
once on `this`, returns `consume(stack)` when the helper returns true and
`pass(stack)` otherwise. All three result holders contain that same hand stack.

The existing helper retains its null, own-item identity, native damaged-state,
positive-damage and upper-bound guards before one `damage - 1` write. The
constructor still supplies only native `Properties.durability(128)`. No copy,
count mutation, inventory replacement, second hand lookup, loop, delayed task,
custom packet, block/chunk access or repair ingredient enters the owned program.

Client SUCCESS is intentional prediction, not evidence that the server repaired
anything. In both inspected clients the input loop stops on a result that
consumes the action. Thus when this item reaches the main-hand callback, its
client SUCCESS stops the ordinary loop before off-hand use and requests a local
swing even if the server later returns PASS. Replacing that return with PASS
would be a semantic change: the same click could continue to the off hand.
The oracle is one helper call per qualifying server callback, not a universal
guarantee about callback counts across arbitrary input mods or repeated packets.

## Exact source and binary evidence

The existing official-mapped Forge archives were read as ZIP text and the
corresponding binary declarations were inspected with `javap -p -s`. No original
mod or Minecraft class was executed. The archive identities are:

| Baseline | Source archive SHA-256 | Binary archive SHA-256 |
| --- | --- | --- |
| 1.16.5 / Forge 36.2.42 | `bbe2d148f96694271c43fc2ba584463d75d9ffc490abd0469ae66b831293d376` | `1a3e71d6ccb8461fe9d94f4c63551fe52a8c0c01116572c49a0c3253d0506b03` |
| 1.20.1 / Forge 47.4.23 | `4bd6656a7cbe3809da68f3b709ca0f2fa186575f58538668c58ce30cb38c4932` | `b820f2243c37f060b17678f3a23c868da14d35ebf82ee47b4035b1718c0952ee` |

Each archive has the established name
`forge-<minecraft>-<forge>_mapped_official_<minecraft>[-sources].jar`.
The selected local development profile remains an explicit input, not data
silently borrowed from a player's Gradle cache or redistributed in the mod.

Relevant ZIP entries and source line spans are:

| Concern | Legacy entry and lines | Target entry and lines |
| --- | --- | --- |
| Native callback declaration | `net/minecraft/item/Item.java`, 109–124 | `net/minecraft/world/item/Item.java`, 139–154 |
| Hand lookup declaration | `net/minecraft/entity/LivingEntity.java`, 1764–1772 | `net/minecraft/world/entity/LivingEntity.java`, 1872–1880 |
| Logical-side field | `net/minecraft/world/World.java`, 90 | `net/minecraft/world/level/Level.java`, 106 |
| Result factories | `net/minecraft/util/ActionResult.java`, 20–30 | `net/minecraft/world/InteractionResultHolder.java`, 20–30 |
| Server use and creative restoration | `net/minecraft/server/management/PlayerInteractionManager.java`, 277–313 | `net/minecraft/server/level/ServerPlayerGameMode.java`, 284–323 |
| Client use dispatch | `net/minecraft/client/multiplayer/PlayerController.java`, 315–338 | `net/minecraft/client/multiplayer/MultiPlayerGameMode.java`, 352–385 |
| Native packet entry and thread check | `net/minecraft/network/play/ServerPlayNetHandler.java`, 975–988 | `net/minecraft/server/network/ServerGamePacketListenerImpl.java`, 1083–1097 |
| Forge right-click cancellation | `net/minecraftforge/common/ForgeHooks.java`, 799–804 | `net/minecraftforge/common/ForgeHooks.java`, 771–776 |
| Client input and off-hand fallthrough | `net/minecraft/client/Minecraft.java`, 1331–1405 | `net/minecraft/client/Minecraft.java`, 1654–1734 |
| Consumption and swing predicates | `net/minecraft/util/ActionResultType.java`, 9–18 | `net/minecraft/world/InteractionResult.java`, 10–23 |

`Item.use` is public, virtual and concrete on both baselines. Its erased legacy
descriptor is `(Lnet/minecraft/world/World;Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/util/Hand;)Lnet/minecraft/util/ActionResult;`;
the target descriptor is
`(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResultHolder;`.
`getItemInHand` is declared on **LivingEntity**, not Player, and remains public
virtual. A Player-owned call-site alias must resolve to that exact inherited
declaration, rather than being accepted by its spelling alone.

`World/Level.isClientSide` is a public final boolean field. The holder factories
are public static methods with erased `(Object) -> holder` descriptors. Their
implementations retain the supplied object and select the corresponding enum
value. SUCCESS and CONSUME consume the action on both versions; only SUCCESS
requests a swing. The target additionally defines CONSUME_PARTIAL, which this
fixture must not introduce.

## Mapping observations

The selected official `client_mappings.txt` files and MCP `config/joined.tsrg`
entries were inspected for 1.16.5-20210115.111550 and
1.20.1-20230612.114412. These are observed production names, **not a manual mapping
database**; exact recipes are independently checked against selected inputs:

| Official role | Legacy production name | Target production name |
| --- | --- | --- |
| `Item.use` | `func_77659_a` | `m_7203_` |
| `LivingEntity.getItemInHand` | `func_184586_b` | `m_21120_` |
| `World/Level.isClientSide` | `field_72995_K` | `f_46443_` |
| Holder `success(Object)` | `func_226248_a_` | `m_19090_` |
| Holder `consume(Object)` | `func_226249_b_` | `m_19096_` |
| Holder `pass(Object)` | `func_226250_c_` | `m_19098_` |

The compiler composes declarations, owners, descriptors and invocation
kinds from the selected inputs, proves the whole owned graph and attaches exact
implemented operations. Existing helper-only and other conversion paths must
continue to reject this additional callback; only the independent route admits it.

## Platform behavior that must remain visible

Both native server use paths reject spectator use and cooldowns before invoking
the owned callback. Forge `RightClickItem` can cancel and return a cancellation
result before that callback. A successful direct helper call does not prove
that an ordinary input reached the helper. Block/entity interaction can also
consume input before the item-use fallback; the first native oracle should use
an unobstructed air-use scenario.

Both server paths record the original count and damage before `ItemStack.use`.
In creative mode they restore the prior damage after an otherwise successful
repair of this nonempty damageable stack. The expected ordinary survival action
is a one-point repair; the expected final creative damage is unchanged. This
must not be “fixed” by changing the native caller or claiming the helper's true
return is proof of persistent creative repair.

The source server assigns the returned hand stack in its changed-result branch;
the target only replaces it when the object reference differs. The fixture
returns the same reference. Native synchronization also differs: source calls
`refreshContainer(inventoryMenu)` and target calls
`inventoryMenu.sendAllDataToRemote()` when the player is not using an item.
Those source observations motivate a sync test; they do not prove that a client
received or rendered the new damage value.

Both inspected packet handlers require execution on the native game thread and
obtain the actual player's hand stack. The target additionally acknowledges an
input sequence and checks the item's enabled features. The target client runs
use through prediction and a sequenced use packet, whereas the source sends its
ordinary use packet directly. These platform paths are not converted, replaced
or newly authorized networking APIs. Only clients and servers on the destination
version are contemplated; cross-version networking is not supported.

The callback itself does not establish thread authority for arbitrary
callers passing unrelated worlds or players. There is no general “any method
that receives a player is safe” rule.

## Implementation and qualification gates

Create and inspect the original bytecode first, then implement the separate
six-method whole-graph contract. Re-prove the helper, one exact hand lookup,
logical-client early return without mutation, one server helper invocation on
`this`, and the same stack in every return. Preserve original branches, return
values, local debug information and resources.

Negative fixtures must cover swapped or constant hands, a second lookup, foreign
or copied stacks, a client helper call, inverted side branch, discarded helper
result, unconditional server success/consume, super.use, asynchronous work, additional
inventory writes, callback/helper collisions and unknown external references.
Check native callback dispatch and inherited hand lookup independently in both
mapping compilation and offline linkage. ASM success alone does not establish
either semantic contract.

Native qualification remains **NOT_RUN**. Required observations include direct
client/server endpoints, survival air-use through ordinary input, creative
damage restoration, each hand separately, main-hand consumption stopping the
normal off-hand loop, client swing with a server-rejected repair, cancellation,
cooldown and spectator bypass, two players with independent stacks, repeated
inputs, save/load/reconnect and visible damage synchronization without count
change or duplication. Record expected and observed states separately.

This audit does not implement wear, break notifications, repair costs, anvil
rules, mending, custom packet validation, generic NBT migration or old-world
migration. No game, server or UI was launched, and no new public profile or Apply
authority follows from this static inspection.
