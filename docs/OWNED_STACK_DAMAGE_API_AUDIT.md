# Owned stack damage API audit

The LB-10 / ITEM-EQUIPMENT slice **OWNED_STACK_DAMAGE** now has a
[private preserving contract](OWNED_STACK_DAMAGE_CONTRACT.md), exact mapping
recipes and independent offline linkage checks. It is not a public feature or
native qualification. It continues the IB2 damage-read/write gap identified by
the [frozen corpus audit](ITEM_BLOCK_CORPUS_AUDIT.md).
Clumps consumes damage APIs, but this authored fixture does not port Clumps or
resolve its entity, mending, networking and lifecycle requirements.

## Authored original and expected behavior

The authored original has two final Java-8 classes and five methods: an entry
constructor, static initializer and factory, plus an owned Item constructor and
public repairOne(ItemStack) helper. The constructor supplies only fresh native
Properties.durability(128). There are no attribute, repair-ingredient, damage
hook or gameplay callback overrides.

The helper is a deliberate endpoint for later isolated QA; there is no hidden
right-click, command, attack/mining or automatic repair trigger. It returns false
for null, another item, a stack that native isDamaged rejects, damage <= 0 or
damage > getMaxDamage. Only after every guard, it calls setDamageValue(damage-1)
once on the same received stack and returns true. It never replaces a stack,
changes its count, accesses a player/world, schedules work or retains mutable
stack state in a static field.

Expected ordinary-stack observations, not observations already executed:

| Input | Expected result | Damage write |
| --- | --- | --- |
| Own item, damage 0 | false | none |
| Own item, damage 1 | true | 0 |
| Own item, damage 127 | true | 126 |
| Own item, damage 128 | true | 127 |
| Null, empty or foreign item | false | none |
| Own unbreakable item | false | none |
| Damage negative or above 128 | false | none |

A later caller must establish execution thread, authority and synchronization.
A helper with no world reference does not prove safe concurrent access to an
inventory stack. This is not an anvil operation or consumption of repair material.

## Pinned source inspection

Both official-mapped Forge source archives were read as ZIP text, without game
execution. Source: 1.16.5-36.2.42, SHA-256
bbe2d148f96694271c43fc2ba584463d75d9ffc490abd0469ae66b831293d376.
Target: 1.20.1-47.4.23, SHA-256
4bd6656a7cbe3809da68f3b709ca0f2fa186575f58538668c58ce30cb38c4932.
The declaration-inventory and official/SRG provenance remain those in the
[item attribute/durability audit](ITEM_ATTRIBUTE_DURABILITY_API_AUDIT.md).

ItemStack.java and IForgeItem.java establish a critical distinction: these calls
are **not raw NBT access**. getDamageValue delegates to the item's getDamage hook;
setDamageValue delegates to setDamage; getMaxDamage delegates to the per-stack
hook. isDamaged first checks damageability, then the item's isDamaged hook.
The source checks emptyCacheFlag while the target checks isEmpty.
Both consult the Unbreakable flag through the native damageability path.

For an exact final owned Item with no hook overrides, the reviewed defaults read
Damage from the native tag and write Math.max(0, damage) as an integer.
The setter does not clamp to maximum durability. The fixture's explicit upper
guard is therefore significant and must not be removed as redundant.
The source default maximum-damage hook calls getItem().getMaxDamage(), while
the target calls self().getMaxDamage(); both refer to the implementing Item.
This does not establish equivalence for third-party items or custom hooks.

| API on final native ItemStack | Legacy production name | Target production name |
| --- | --- | --- |
| getDamageValue()I | func_77952_i | m_41773_ |
| setDamageValue(I)V | func_196085_b | m_41721_ |
| getMaxDamage()I | func_77958_k | m_41776_ |
| isDamaged()Z | func_77951_h | m_41768_ |

These are audit observations, not a manual mapping database. The implemented
compiler recomposes exact declarations/descriptors from selected official/SRG
data and proves the containing program. IForgeItem defaults and their inheritance
are checked in semantic and independent offline-linkage review, not inferred
from these names. A missing or changed contract blocks the whole candidate.

Native CompoundNBT/CompoundTag getInt accepts numeric tags, not exclusively an
integer tag. Absent/non-numeric values yield zero. Thus this endpoint is
not a general malformed-NBT validator. Fractional or oversized numeric inputs
follow native numeric coercion before guards; any claim of rejecting all malformed
NBT would be false. Save/load behavior and canonicalization require separate QA.

## Original build evidence

The original was built serially and offline with ForgeGradle 6.0.54 and the pinned
Java-17 toolchain. Four archive-only tests passed with no failures or skips.
The original JAR is 6314 bytes, SHA-256
a6099857d7a2e4e3cb2db6d67ce1db23d08d2ba9c17b732c380d2d7b64f09793. A read-only javap inspection confirms the
reobfuscated helper's guarded single decrement/write and both early false returns.
Those original-build observations are source preparation, not runtime evidence.
The separate [implementation ledger](evidence/owned-stack-damage-candidate-2026-10-04.json)
records the private converted candidate, later serial checks and unchanged original hash.
The [delivery ledger](evidence/item-attribute-durability-candidate-2026-10-01.json)
records the build separately from the preceding composition.

## Implementation and negative gates

The implementation checks the original archive without loading its classes,
then applies a distinct whole-graph contract and exact compiler path. It preserves
the helper body, guards, return values and source/target/readback proof.
It rejects custom getDamage/setDamage/getMaxDamage/isDamaged/isDamageable overrides,
unknown helpers, foreign/global item lookups, writes before guards, increment,
multiple writes, count/copy/shrink operations, writes to another stack, loops,
asynchronous work and world/player access. It also rejects changed property order,
defaultDurability, stacksTo and unknown resources/dependencies.

The compiler and independent verifier check native final ItemStack, method access, declaration owners, hook dispatch,
descriptors, exact superclass/interfaces and invocation kinds. The transformed
candidate preserves debug/resources and binds each change to an implemented operation.
No public profile, Apply authority or implicit combination with attributes follows.

Native qualification remains NOT_RUN: compare original/target endpoint results,
two independent stacks, boundary values, no count change/duplication, Unbreakable,
save/load and reconnect. Inventory synchronization is a separate contract;
persistent Damage NBT alone does not prove client synchronization. Actual wear,
break notifications, mending, enchantment effects and old-world migration remain
outside this slice. No original class, Minecraft client or server was executed.
