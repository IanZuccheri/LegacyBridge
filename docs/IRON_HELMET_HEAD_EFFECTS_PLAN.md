# Iron helmet head equipment effects plan

Historical implementation plan: **LB-10 / ITEM-EQUIPMENT / IRON_HELMET_HEAD_EQUIP_EFFECTS**.
The [guarded operation is implemented](IRON_HELMET_HEAD_EFFECTS.md), without
composition, constructor recipe or native qualification. The current next step
is the [closed dispenser path](IRON_HELMET_DISPENSER_PLAN.md).
The empty-slot runtime operation is implemented. Do not repeat its inspection,
factory or direct-use interpreter as the next deliverable.

## Implement the next finite operation

Close the HEAD equipment-write effect reached by the authored iron helmet,
starting from the pinned native source rather than substituting target setItemSlot.
The source PlayerEntity setter calls protected LivingEntity.playEquipSound before
writing inventory.armor. Target Player.setItemSlot first verifies the item, then
writes, then invokes onEquipItem. That target hook adds firstTick, spectator and
silent gates and can emit GameEvent.EQUIP. They are not interchangeable bodies.

The source sound helper selects the armor material's equip sound and calls the
virtual player.playSound with volume and pitch 1. Both pinned Player classes
forward that call to their level using the player as the excluded listener;
neither player override adds Entity's silent gate. Target Player.getInventory is
public, Inventory.armor is public final, and HEAD has index 3 in both versions.
These actual APIs permit investigation of a concrete restricted operation without
patching platform classes, reflection, access transformers or raw NBT.

Implement a versioned, fixed HEAD effect operation for a proven copy of the exact
iron helmet: preserve sound-before-write ordering and copied-stack identity using
real target APIs. Compare the compiled operation with source setter/sound bodies
as data, including exceptional exits, empty-stack handling, two players and both
logical sides. Do not add native target verify/onEquip/GameEvent behavior and call
it equivalent. Do not simulate sound with a counter in production.

An owned helper cannot inherit a custom player's protected playEquipSound or
setItemSlot override. Target getInventory is virtual too. Define and verify the
closed caller/receiver and stack preconditions before composing this effect into
a use operation; do not silently bypass unknown overrides or an equipment-slot
hook returning a non-HEAD result. If closure is unavailable, keep that route
UNSUPPORTED with the exact remaining blocker. Preserve the existing operation's
revision/behavior; changing a catalog contract requires an explicit new identity
or migration, not silently editing its meaning.

## Remaining independent gates

Source ClientPlayerEntity and target LocalPlayer use different Forge sound-event
types. The virtual sound dispatch must remain native and those event differences
must be documented; no arbitrary listener/cohort equivalence is implied. Do not
resolve physical-client classes from the shared adapter.

The fixed constructor also registers native dispenser behavior. Dispenser entity
selection, automatic equipment, Equipable entrypoints and rendering/trim paths
still require their own implemented finite contracts or an explicitly reviewed
semantic migration. This head-effect operation does not close them.

Only after these admitted paths have exact implemented contracts may a contextual
constructor recipe replace the original IRON/HEAD/fresh-properties allocation.
Do not lower an unknown call merely to produce a candidate or inflate progress.
No new eligible JAR, profile or Apply until those gates are met. Native sound,
equipment, persistence, synchronization and multiplayer comparison remains a
separate later authorization; no Minecraft/client/server/screen in this stage.
