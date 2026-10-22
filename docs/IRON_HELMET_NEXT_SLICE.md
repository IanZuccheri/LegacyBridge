# Original iron helmet inspection plan

This was the plan for **IRON_HELMET_SEMANTIC_INSPECTION**, after the private
owned-stack repair-use candidate. The [read-only inspection](IRON_HELMET_INSPECTION.md)
is now implemented; it is not an armor converter. The next development cursor
is [IRON_HELMET_EMPTY_SLOT_ADAPTER](IRON_HELMET_EMPTY_SLOT_ADAPTER_PLAN.md).

The existing corpus review has no armor-producing release. Use an authored
fixture, without acquiring third-party mods or claiming an ecosystem unlock.
Start with one native iron helmet and its exact registration/resource graph.
Follow the existing IronSword inspection pattern: bounded typed service and
developer CLI, source structure/ASM/linkage checks, explicit semantic blockers,
no candidate or public installation until real equivalence is implemented.

## Confirmed API difference to investigate

Read-only inspection on 2026-10-05 used the same pinned source archives and
hashes as the [item-use audit](OWNED_STACK_REPAIR_USE_API_AUDIT.md):
legacy `net/minecraft/item/ArmorItem.java`; target
`net/minecraft/world/item/ArmorItem.java` and `Equipable.java`.

The constructor changes from IArmorMaterial plus EquipmentSlotType to
ArmorMaterial plus ArmorItem.Type. HEAD to HELMET is contextual construction,
not permission to remap every equipment-slot expression.

The legacy native use body only equips into an empty equipment slot; an occupied
slot returns FAIL. The target delegates to Equipable.swapWithEquipmentSlot,
which can swap occupied equipment under Binding Curse and stack-match guards.
It also awards an ITEM_USED statistic on the logical server. Therefore a
constructor rename alone cannot claim the legacy behavior was preserved.

Recheck the exact binaries, official/SRG data, constructor consequences,
durability/attributes/repair, dispenser registration, equipment sound and inherited
callbacks before admitting any recipe. This note does not prove their equivalence.

## Scope of the delivered inspection

Create the small original and positive/negative archive fixtures; implement a
read-only diagnostic with explicit blockers and unchanged original bytes. Unknown
materials, slots, subclass hooks, extra references and incomplete declarations
must be rejected, not copied as compatible. Preserve existing private candidates,
six public profiles, adapter ABI and simple-content-v1.

A later conversion route needs an implemented semantic operation for legacy
empty-slot use and contextual construction, not an empty adapter or a native
behavior claim based on static tests. Client/server/equipment/anti-duplication
comparison remains a separate authorized gate. Do not launch Minecraft.
