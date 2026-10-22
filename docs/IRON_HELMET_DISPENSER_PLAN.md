# Iron helmet closed dispenser path

This plan was implemented headlessly on 2026-10-05 as
[IRON_HELMET_DISPENSER_CLOSED_RECEIVERS](IRON_HELMET_DISPENSER.md).
It remains a separate, unregistered runtime operation, not a conversion recipe.
Next: [IRON_HELMET_INTEGRATED_SUCCESSOR](IRON_HELMET_INTEGRATED_ITEM_PLAN.md).
The original plan below records scope, not native acceptance.

## Implement a separate dispenser contract

The fixed armor constructor registers a dispenser behavior, independently of
right-click use. Compare the pinned source and target ArmorItem.dispenseArmor
bodies and their actual selection, split, equipment and post-equipment calls.
Start with a finite authored fixture covering the first eligible native player
and exact Zombie receiver; include no-result and unknown-first-receiver negatives.
Do not count a new catalog entry as a converted fixture.

Preserve query bounds, native eligibility, first-result selection, split(1),
equipment order, mob drop chance and persistence. An unsupported selected receiver
must be rejected before consuming the input stack. Do not silently skip it, select
another entity or return false in a way that converts unsupported behavior into
dropping an item. False/no eligible target and unsupported must stay distinct.

Player and Mob setters are not interchangeable. The pinned source Mob setter
writes without the player's equip sound; the target Mob setter adds verification
and equipment effects. Investigate a concrete silent HEAD write using the public
getArmorSlots path and an exact native receiver/list contract. Reusing the player
operation would add a sound absent from the source. Check real access, index,
dispatch and exception ordering before admitting a rule.

The current player HEAD operation accepts only the final LegacyIronHelmetItem
for nonempty stacks. Preserve that versioned contract. Any integrated successor
item/factory or expanded accepted item type requires a new explicit identity and
tests, not silently broadening the existing guard or changing the old item bytes.
Likewise, do not overwrite native dispenser behavior globally: any future
registration must belong to the new owned item and have a reviewed lifecycle.

## Evidence and remaining gates

Inspect compiled target operations and pinned source bytes as data. Test two
receivers, two input stacks, selected order, split identity/count, thrown effects,
drop chance/persistence and rejection without consumption. Preserve the six
existing profiles, previous candidates and descriptors; build/test serially.
No game, server, screen, download or publication is authorized.

This is a finite implementation step, not general mob equipment support.
Other receivers, ArmorStand, external hooks/listeners, dispenser fallback details,
Equipable entrypoints, rendering and native comparison remain separate gates.
The next integrated item must compose use and dispenser with explicit owned-stack,
slot and receiver proofs before a constructor recipe can emit a candidate.
Until then the helmet fixture stays UNSUPPORTED; public Apply stays unchanged.
