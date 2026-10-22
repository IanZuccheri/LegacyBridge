# Iron helmet empty slot adapter plan

This historical plan was implemented as
**LB-10 / ITEM-EQUIPMENT / IRON_HELMET_EMPTY_SLOT_ADAPTER**.
See the [operation and remaining limits](IRON_HELMET_EMPTY_SLOT_ADAPTER.md),
then follow the current contract cursor rather than repeating this stage.
The [inspection](IRON_HELMET_INSPECTION.md) is implemented and should not be
repeated as the next deliverable. Armor conversion remains UNSUPPORTED.

## Finite implementation to deliver

Implement a real target runtime operation for the authored iron helmet, based
on the pinned source body rather than the target native swap helper. Start with
a restricted ArmorItem subclass using native IRON and HELMET. Audit and pin
constructor consequences before choosing the exact adapter signature; do not
silently admit arbitrary properties, material, slot or subclass callbacks.

Its use override must preserve the legacy sequence on the received player,
world and hand:

1. Read the held stack once and resolve its native equipment slot.
2. Read the current stack in that slot once.
3. If occupied, return FAIL with the original held stack and no write.
4. If empty, equip a copy, set the original held stack count to zero and return
   sided success containing that same cleared held stack.

The source body performs its mutation on both logical sides; do not copy the
server-only policy from the separate repair-use fixture. Do not add the target
native Binding Curse/stack-match checks, occupied-slot swap or direct ITEM_USED
award to this operation and then call it equivalent. Effects of native callers
remain a distinct contract.

Use the original fixture and finite synthetic stack/caller models to check exact
call owners, descriptors, invocation kinds, receiver/argument flow, ordering,
copy versus alias identity, result and write/stat counts. Compare actual compiled
target adapter bytes with the pinned legacy body as data. No original class,
Minecraft client or server may execute during this headless development.
Add the required Legacy API / Target API / Transformation / Behavior differences /
Known limitations documentation. No empty adapter, invented return or ignored
exception is acceptable.

## What this operation does not establish

The target's Equipable interface exposes additional call paths. Inspect their
callers before admitting a contextual constructor transformation. A global
HEAD-to-HELMET rename is always wrong: HEAD remains an EquipmentSlot on target.
Every later NEW/constructor replacement must bind to the exact implemented
operation and validated allocation graph, with independent target linkage.

Native constructor facts agree locally on durability 165, defense 2, toughness
and knockback 0, head modifier UUID, iron repair material and equip sound.
Those observations do not qualify dispenser entity selection/equipment hooks,
target rendering or trim integration, enchantments, anvil, wear or lifecycle.
Target client armor-model APIs differ from the legacy hook route.

Initially deliver the real runtime operation and its headless contract without
enabling a conversion recipe or eligible candidate. Keep the diagnostic's
missing-contract blockers until all admitted constructor/inherited call paths
have an implemented finite recipe. Record any remaining blocker precisely;
an unused implemented operation is not converted-mod support.

Six public profiles, simple-content Convert/Apply and previous candidates remain
unchanged. Do not promote a new adapter ABI without its versioned descriptor,
source/target access checks and regression coverage. Native client/server
equipment, consumption/duplication, sounds/rendering, save/load and multiplayer
comparison remains a later separately authorized gate.
