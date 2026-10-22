# Next: an integrated, explicitly owned helmet successor

LB-10 / ITEM-EQUIPMENT / IRON_HELMET_INTEGRATED_SUCCESSOR is the next finite
headless development slice. The independent empty-slot item, player HEAD effect
and closed dispenser operation are implemented. Do not repeat their independent
inspection as a delivery, or mark all ITEM-EQUIPMENT complete.

## Integrated implementation

Introduce a new final item/factory and explicit versioned operation identities.
Preserve the existing 69 catalog descriptors and previous item/effect/dispenser
bytes. In particular, the old helpers deliberately accept LegacyIronHelmetItem;
do not broaden those guards to accept a successor or arbitrary ArmorItem.

Compose the finite use path around actual ownership (`held.getItem() == this`),
native hand lookup, empty HEAD, one copy, player sound before the armor write,
clear of the original received stack and the original sided result/alias.
Occupied HEAD remains FAIL without swap. Document unsupported receiver/stack
preconditions and exceptions without inventing a successful outcome or rollback.

Register a dispenser behavior only for the new owned item after its superclass
construction. Review the real registration lifecycle, callback signature and
native no-target fallback, including callback return and input identity. Never
replace behavior globally or for existing Minecraft/Forge items. The callback
must use this item's ownership proof; unknown first receivers must not become
false/drop fallback. Compose silent Zombie and player HEAD paths explicitly.

Shared private implementation or a structural differential proof may avoid
duplicating already-reviewed method bodies. Neither option authorizes an open
receiver/type list. Every changed public descriptor needs an actual operation.

## Evidence and closure

Extend authored source fixture motivation only as necessary, with exact source
and target API/archive hashes. Inspect actual compiled/reobfuscated successor
bytes for construction, owned registration, use, callbacks and failure paths.
Test identity/count/order, independent items/stacks/receivers, no-target fallback,
unknown receiver before consumption, occupied slot and thrown effects.
Run build/tests/package serially, preserve prior candidates and six profiles,
update evidence and the next cursor. No game/server/screen or new mod download.

This is still a runtime integration contract, not permission to lower arbitrary
armor constructors. Exact ServerPlayer/Zombie support excludes native ArmorStand
and other valid vanilla receivers. Native world query ordering can differ.
Before a constructor recipe or candidate is admitted, explicitly close or block
those paths, inherited Equipable entrypoints, registration/cohort assumptions,
copy hooks, rendering, side/thread and native comparison. A throwing unsupported
callback is not a qualified automatic port. Public armor conversion stays
UNSUPPORTED, simple-content-v1/Apply and prior ABI/license remain unchanged.
