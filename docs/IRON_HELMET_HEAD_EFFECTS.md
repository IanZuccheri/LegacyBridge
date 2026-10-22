# Iron helmet head equipment effects

LB-10 / ITEM-EQUIPMENT adds a separate, guarded runtime operation for the fixed
helmet's HEAD equipment write. It preserves the sound-before-write sequence;
it does not enable armor conversion or alter the previous helmet item operation.
The fixture still returns UNSUPPORTED and no converted helmet JAR is emitted.

## Implemented contract

Legacy API: Forge 36.2.42 PlayerEntity.setItemSlot(HEAD, stack), whose armor
branch invokes LivingEntity.playEquipSound before writing inventory.armor.
For the fixed iron helmet, the sound helper selects the iron material sound and
invokes the player's virtual playSound with volume and pitch 1.

Target API: Forge 47.4.23 Player.playSound, ArmorMaterials.IRON.getEquipSound,
Player.getInventory, Inventory.armor and EquipmentSlot.HEAD.getIndex. HEAD is
index 3 in both pinned versions. Target Player.setItemSlot is not equivalent:
it verifies the stack, writes it, then calls onEquipItem, with new gates and
GameEvent.EQUIP effects.

Transformation: item.iron-helmet-head-equip-effects.v1 is a new revision-1 RUNTIME
operation, implemented by LegacyIronHelmetHeadEquipEffects.equip. It requires
non-null arguments and the actual HEAD slot. The receiver must be exactly
ServerPlayer, or exactly LocalPlayer/RemotePlayer on the physical client.
Unknown subclasses, including FakePlayer, are rejected before sound or mutation.
A nonempty stack must contain one instance of the final LegacyIronHelmetItem;
empty stacks are accepted and their identity is retained.

After admission, a nonempty stack causes the native virtual sound call, followed
by a single armor-list write of the received stack. An empty stack skips sound,
but is still written. No copy, normalization to ItemStack.EMPTY, held-stack
consumption, stat, packet, GameEvent or target equipment callback is added.
An exception from sound prevents the equipment write; an exception from the
write is propagated. No exception is silently ignored.

Behavior differences: this deliberately avoids target setItemSlot and its new
effects. Guard failures are explicit unsupported invocations, not legacy
outcomes. The operation preserves native sound dispatch, not the implementation
of the old sound system. Source server PlaySoundAtEntityEvent and target
PlayLevelSoundEvent.AtPosition differ, including application of modified pitch;
the target also uses seeded sound. The local client changes to
PlayLevelSoundEvent.AtEntity. External listeners and sound cohorts are not
qualified by a matching call sequence.

Known limitations: no current recipe or use override calls this operation.
The guards do not prove that the argument is an independently owned copy, that
the caller runs on the correct native thread, or that a platform transformer has
not changed native behavior. Exact class identity closes the reviewed native
hierarchy only. Construction, dispenser behavior, inherited Equipable calls,
slot hooks, rendering/trims, save/load, synchronization and multiplayer remain
independent obligations. No constructor lowering or public Apply is permitted.

## Class loading and state isolation

The shared operation has no direct net.minecraft.client references. Exact
client class identities live in a separate nested project class reached only
after the physical Dist.CLIENT check. ServerPlayer is also accepted in an
integrated server; physical side is not substituted for logical side.
No class-name strings, reflective class loading or fake player fallback are used.
The [Forge side documentation](https://docs.minecraftforge.net/en/1.20.1/concepts/sides/)
provides the side-isolation guidance; the exact native bodies and declarations
are checked against the pinned local artifacts, not inferred from that guide.

The adapter holds no mutable state or player references. Each invocation writes
only the admitted player's inventory, using the supplied stack reference.
It does not copy data between logical client and server. Preserving an operation
on both sides is not proof of native prediction or inventory synchronization.

## Validation boundary

Tests inspect the compiled reobfuscated adapter and pinned source/target class
bytes as data. They compare the source HEAD setter and armor sound branch with
the implemented operation under finite synthetic contexts. They also check
receiver/stack/slot rejection, identities, exceptional ordering, independent
players, declaration/access contracts and the isolated client helper.

These tests do not execute the original mod, load Minecraft classes or establish
audible playback, equipment rendering, native classloader behavior or multiplayer
correctness. Native client/server/behavior/multiplayer remain NOT_RUN. The
[versioned ledger](evidence/iron-helmet-head-effects-2026-10-05.json) records the
actual selected tests, artifact delta, failures and remaining gates.

The previous LegacyIronHelmetItem bytes and its catalog descriptor remain intact.
The new operation is additive; existing profiles, runtime identity/version,
CompatibilityReport and TransformationLog schemas, and simple-content-v1
Convert/Apply are unchanged. Catalog presence never upgrades mapping confidence
or approves a caller's installed runtime.

## Next implementation

The subsequent [closed dispenser path](IRON_HELMET_DISPENSER.md) now implements
that independent caller without adding player sound to the silent source Mob
setter. Neither operation is registered/composed with the item. Next development
is the [explicitly owned successor](IRON_HELMET_INTEGRATED_ITEM_PLAN.md);
previous operations remain versioned and unchanged.
