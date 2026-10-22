# Owned attributes plus durability: finite composition audit

2026-10-01; LB-10 / ITEM-EQUIPMENT / **OWNED_ATTRIBUTE_DURABILITY**.
This source audit preceded the now implemented [private static composition](ITEM_ATTRIBUTE_DURABILITY_CONTRACT.md).
The audit itself is not conversion or native evidence; the delivery ledger records
its distinct compiler, candidate, tests and OFFLINE metadata operations. No public
profile or Apply permission was added. The native iron sword remains UNSUPPORTED.

## Concrete gap and authored original

The separate owned-attribute contract requires fresh, unmodified Item.Properties
and its one attribute callback. The separate durable-item contract does not admit
the static multimap or attribute callback. Neither proves the combined graph.
Simply enabling both flags would defeat those independent finite guards.

`fixtures/item-attribute-durability-1.16.5` contains exactly two Java-8 classes
and seven expected methods. DeferredRegister registers
`legacybridge_attr_durable:bridge_durable_attribute_item`, version 1.0.0. The
zero-capture factory constructs the final owned Item subclass with
`new Item.Properties().durability(128)` in that order. Its inherited base class
is Item, never SwordItem, TieredItem, ArmorItem or a custom tool hierarchy.

The subclass keeps the same finite immutable MAINHAND multimap: ATTACK_DAMAGE,
native BASE_ATTACK_DAMAGE_UUID identity, 2.0D, ADDITION; ATTACK_SPEED, distinct
native BASE_ATTACK_SPEED_UUID identity, -1.0D, ADDITION. Other slots receive an
empty immutable map. The second override evaluates only
`ingredient.getItem() == Items.IRON_INGOT` using the second argument. It never
reads or writes the damaged argument, inventory, world or player state.

One shapeless recipe, en_us/pt_br translations and a generated-item model with
a vanilla iron-ingot texture reference accompany the original. No native assets
or dependencies are copied. Four archive-only tests include exact class/method
counts. Build/test, hashes and runtime evidence must be recorded separately;
writing this source alone is not a successful conversion or game test.

## Exact sources and observations

Re-read locally without loading the game or mod: official-mapped Forge
1.16.5-36.2.42 and 1.20.1-47.4.23 source archives, entries Item.java and
ItemStack.java. Full attribute/Guava source provenance is retained in
[ITEM_ATTRIBUTE_API_AUDIT](ITEM_ATTRIBUTE_API_AUDIT.md); repair declarations in
[DURABLE_ITEM_CONTRACT](DURABLE_ITEM_CONTRACT.md).

| Evidence archive | SHA-256 |
| --- | --- |
| Source Forge sources | `bbe2d148f96694271c43fc2ba584463d75d9ffc490abd0469ae66b831293d376` |
| Target Forge sources | `4bd6656a7cbe3809da68f3b709ca0f2fa186575f58538668c58ce30cb38c4932` |
| Source API declarations | `1a3e71d6ccb8461fe9d94f4c63551fe52a8c0c01116572c49a0c3253d0506b03` |
| Target API declarations | `b820f2243c37f060b17678f3a23c868da14d35ebf82ee47b4035b1718c0952ee` |
| Guava 21.0 binary | `972139718abc8a4893fa78cba8cf7b2c903f35c97aaf44fa3031b0669948b480` |
| Guava 31.1-jre binary | `a42edc9cab792e39fe39bb94f3fca655ed157ff87a8af78e1d6ba5b07c4a00ab` |

As orientation, the official [Forge 1.16 item guide](https://docs.minecraftforge.net/en/1.16.x/items/items/)
and [Forge 1.20.1 item guide](https://docs.minecraftforge.net/en/1.20.1/items/)
describe durability capacity and the non-stackable damageable-item restriction.
Those guides do not establish wear or full cross-version behavior; the pinned
local source inspection below is the recipe evidence.

Both native `Properties.durability(int)` write maxDamage and maxStackSize=1
and return the same properties. Item's constructor reads those two fields and
canRepair. On both Forge baselines `isRepairable(stack)` returns
`canRepair && isDamageable(stack)`, independently of the ingredient callback.
Native `isValidRepairItem` defaults false; the owned exact override must retain
dispatch, not be replaced by that base implementation.

The source ItemStack getter uses emptyCacheFlag/delegate-null checks; the target
uses isEmpty before its registry delegate. This supports only the previously
reviewed ordinary native stacks, not malformed stacks or arbitrary registry
replacement. Production symbols still require recomposition from selected data:
`durability`: func_200918_c / m_41503_; `isValidRepairItem`: func_82789_a /
m_6832_; `getItem`: func_77973_b / m_41720_; iron ingot: field_151042_j /
f_42416_. These observations are not an additional mapping database.

The attribute and repair overrides do not invoke one another or store per-stack
state in the authored graph. Native ItemStack's attribute query first honors
AttributeModifiers NBT, otherwise delegates to the item, then calls ForgeHooks.
The conversion must keep that native route and not synthesize NBT or bypass
events. UUID reference identity remains important for the native tooltip path.

Most importantly, native Item `hurtEnemy` and `mineBlock` return false in BOTH
inspected versions. Defining durability capacity does **not** implement wear on
hit/mining, durability consumption or break notification. This fixture has no
such override; it is not a supported weapon merely because it has attack stats.
The target Item constructor also has its native feature/client initialization;
this composition does not erase or claim to emulate all platform differences.

## Original build evidence

The original build and all four archive tests passed serially and offline after
the current candidate regression. The 7,119-byte original JAR has SHA-256
`2f921f9dbf644cb866e32b60397b2011f1982e676c8d9e3dc56bfeff6b7f5d16`.
It contains two Java-8 classes/seven methods. See
[the current ledger](evidence/item-attributes-candidate-2026-10-01.json) for the
exact build log. No original class or game was executed; this is not evidence of
a converted combined candidate. The later candidate proof and limitations are
recorded separately in [the composition delivery](ITEM_ATTRIBUTE_DURABILITY_CONTRACT.md).

## Implementation obligations and qualification gates

1. Implement a distinct closed composition proof: two classes/seven methods,
   one literal 128 durability call, the exact two-entry immutable initialization,
   MAINHAND branch and second-argument iron-ingot predicate. Keep existing
   attribute-only and durable-only contracts unchanged.
2. Add an independent opt-in compiler route, approved only after both exact
   declaration/namespace/access/dispatch checks; retain the real Guava hierarchy
   and pinned explicit source/target inventories, never converter-classpath data.
3. Preserve every original body/resource; compare source, transformed and reopened
   proof; validate ASM, linkage, protected native UUID field access, all external
   references, descriptor changes and explicit implemented operations. Unknown
   methods/data block the whole operation. Do not emit a partial JAR.
4. Add negatives for property order/defaultDurability/stacksTo/noRepair, wrong
   damaged/ingredient argument, reversed repair branch, unknown ingredients,
   altered UUID/attribute pairing, changed doubles, shared mutable state, retained
   builder, added wear callbacks and missing/swapped API/Guava data. Cancellation
   and original preservation remain mandatory.
5. Native qualification is separate and currently NOT_RUN: six slots and native
   UUID identity, stack1/maxDamage128, repair eligibility on ordinary stacks,
   equip/unequip/swap without accumulation, native NBT/events, tooltip,
   save/load/relog and multiplayer. Anvil cost, repair material consumption,
   enchantment behavior, actual wear/breakage and world migration stay outside
   this composition until separately implemented and qualified.

The corpus has not acquired a new third-party mod and no compatibility metric
is derived from this fixture. No game, server or original class has been executed.
