# Iron helmet: closed dispenser operation

LB-10 / ITEM-EQUIPMENT / IRON_HELMET_DISPENSER_CLOSED_RECEIVERS implements
`item.iron-helmet-dispenser.v1`, revision 1, RUNTIME. Its exact entrypoint is
`LegacyIronHelmetDispenser.dispense(BlockSource, ItemStack): boolean`.
It is packaged, but is not registered for any item and has no conversion recipe.
Armor conversion and the fixture remain **UNSUPPORTED**. This is not general
mob equipment support or a converted mod.

## Legacy API

The pinned Forge 36.2.42 / Minecraft 1.16.5 `ArmorItem.dispenseArmor`
(`func_226626_a_`) selects the first eligible LivingEntity in the adjacent
facing unit AABB. It uses NO_SPECTATORS and the native armor selector, resolves
the equipment slot, splits one item, equips it, and for Mob sets drop chance
to 2.0F and persistence. No eligible result returns false.

Player eligibility is an empty matching slot; it does not require pickup loot.
The Mob/Zombie predicate also requires `canPickUpLoot`. The source Mob setter
is a silent armor-list write; the player setter has a sound-before-write effect.

## Target API

Forge 47.4.23 / Minecraft 1.20.1 supplies BlockSource, ServerLevel,
EntitySelector.MobCanWearArmorEntitySelector, ItemStack.split, EquipmentSlot,
ServerPlayer, Zombie and Mob.getArmorSlots/setDropChance/setPersistenceRequired.
Their declarations, access and inherited owners are reviewed against pinned
local archives and mappings. No platform class is transformed or bundled.

The target Mob setter adds verification/equipment effects. The closed Zombie
path instead obtains the real armor list from the public getter, verifies exact
NonNullList and size four, then writes HEAD directly. The previous guarded
[player HEAD operation](IRON_HELMET_HEAD_EFFECTS.md) handles the exact ServerPlayer.

## Transformation and guards

The runtime operation requires non-null inputs and a nonempty stack whose item
is the final LegacyIronHelmetItem. It retains the native query predicates and
the order of the returned list. It does not sort, skip or substitute receivers.

Only an exact first ServerPlayer or exact Zombie and resolved HEAD are admitted.
Unknown receivers (including ArmorStand and mod subclasses), wrong slots or
an unknown Zombie armor-list layout throw before split. Unknown is not false:
it must not be converted into a fallback item drop. No-result alone returns false.

Both branches split exactly one. The player receives that same new reference;
the Zombie receives it silently, followed by drop chance and persistence.
No mutable state is shared between invocations. The item and HEAD helpers from
the prior stages and all 68 previous catalog descriptors are unchanged.

## Behavior differences

- The exact-type/stack/list checks are an explicit unsupported-invocation boundary,
  not emulated success. They may throw where native armor accepts other objects.
- Zombie list access/shape checking occurs before split. The reviewed native
  getter is pure; a changed layout is rejected without consumption.
- Exceptions after split propagate with any already-performed effects; there is
  no fictitious rollback or retry.
- Source World queries chunks directly; target EntityGetter delegates through
  EntityTypeTest. Retaining the first returned result does **not** prove identical
  spatial enumeration between versions.
- Player sound uses the previous helper. Native sound/event contracts differ
  between versions; arbitrary external listeners are not qualified.

## Fixture and evidence

`fixtures/iron-helmet-dispenser-1.16.5` is a new authored original, not a
third-party release. Its original helper delegates directly to the native
1.16.5 ArmorItem method. Artifact tests inspect the reobfuscated Java 8 bytes,
metadata and resources without loading the original. Seven authored native
scenario declarations are test-only, excluded from the JAR and all NOT_RUN.

Engine tests interpret actual pinned method bytes and the compiled/reobfuscated
runtime operation over finite synthetic endpoints. They cover first receiver,
eligibility, six facings, split identity/count, independent calls, exceptions,
unsupported inputs and deliberate instruction mutations. They do not execute
the mod, load Minecraft classes, or simulate a whole world. Synthetic copied
payload markers are not NBT/capability-copy qualification. Counts greater than
one test the split expression; they do not claim a valid vanilla armor max stack.

[Artifact-bound ledger](evidence/iron-helmet-dispenser-2026-10-05.json) records
hashes, commands, test outcomes and the exact native-validation boundary.

## Known limitations and next development

No registration, constructor lowering, candidate, new public profile or Apply.
The existing three-method helmet inspection remains finite; the new four-method
fixture is not silently added to that grammar. Original fixtures stay intact.
No Minecraft client, dedicated server, screen or multiplayer test was run.
World query ordering, chunk access, native copying, thread/lifecycle, hooks,
fallback drops, other receivers, Equipable and rendering remain unqualified.

Next is [IRON_HELMET_INTEGRATED_SUCCESSOR](IRON_HELMET_INTEGRATED_ITEM_PLAN.md):
compose a new owned item identity without weakening these old contracts.
ITEM-EQUIPMENT and LB-10 remain PARTIAL; there is no compatibility percentage.
