# Iron sword constructor and inherited behavior audit

This read-only audit prepares the next LB-10 / ITEM-EQUIPMENT slice after the
private boolean-toggle block. No sword conversion rule, runtime adapter or public
profile has been enabled. A constructor/attribute match alone does not authorize
conversion of an item whose inherited gameplay differs.

## Exact declarations

The reviewed sources are the locally prepared official-mapped Forge archives:

- Source 1.16.5-36.2.42: SHA-256
  `bbe2d148f96694271c43fc2ba584463d75d9ffc490abd0469ae66b831293d376`.
- Target 1.20.1-47.4.23: SHA-256
  `4bd6656a7cbe3809da68f3b709ca0f2fa186575f58538668c58ce30cb38c4932`.

SwordItem.java, TieredItem.java, ItemTier.java/Tiers.java, Item.java,
PlayerEntity.java/Player.java and target ForgeHooks.java/ToolActions.java were
inspected. Declaration JARs were read with javap without loading game or mod
classes. Source API SHA-256 is
`1a3e71d6ccb8461fe9d94f4c63551fe52a8c0c01116572c49a0c3253d0506b03`;
target API SHA-256 is
`b820f2243c37f060b17678f3a23c868da14d35ebf82ee47b4035b1718c0952ee`.

The public constructor changes from `(IItemTier,int,float,Item.Properties)`
to `(Tier,int,float,Item.Properties)`. Both classes extend TieredItem and Item;
the marker interface changes from IVanishable to Vanishable. Production symbols
must still be composed and verified from the supplied mapping/API inputs, not
hardcoded as a generic migration database.

## Finite constructor facts

A proposed authored fixture starts with native IRON, integer 3, float -2.4F and
fresh Item.Properties, with no subclass, callbacks, custom tier, tab or additional
property calls. This matches the native iron sword's attack literals, not all of
its registry or creative-tab behavior.

IRON has level 2, uses 250, speed 6F, attack bonus 2F, enchantability 14 and an
iron-ingot repair ingredient. The constructor computes attackDamage as 5F and
creates two ADDITION modifiers only for MAINHAND: damage 5D and speed equal to
the double conversion of the float -2.4F. Do not replace it with the mathematically
exact decimal double -2.4.

The damage UUID is `CB3F55D3-645C-4F38-A497-9C13A33DB5CF`; speed UUID is
`FA233E1C-4180-4865-B01B-BCCE9785ACA3` in both inspected versions. TieredItem uses
defaultDurability on the fresh properties, yielding max damage 250 and stack 1.
The repair predicate tests the tier ingredient before the native superclass
fallback. These facts do not prove anvil cost, consumption, Unbreaking or breakage.

## Semantic differences that block a pure rename

Legacy SwordItem.getDestroySpeed checks COBWEB first, then Material.PLANT,
REPLACEABLE_PLANT, CORAL, VEGETABLE and the LEAVES tag. The target checks COBWEB
then the reloadable SWORD_EFFICIENT tag. The respective returned speeds are
15F, 1.5F and 1F. Equal signatures and some equal vanilla outcomes do not equate
these predicates for arbitrary blocks or datapacks.

A source-based counterexample is a target datapack replacing sword_efficient
with an empty tag: wheat then selects target 1F while legacy wheat's PLANT
material still selects 1.5F. This is an inference from the inspected code, not
an executed test. No claim of a vanilla-default mismatch is made without a
complete inventory. The target vanilla sword_efficient JSON hash is
`c0df9191317f615355983dd39608e0d7572a6cc550fb2cf27632794751a19dd2`.

Target SwordItem also exposes DEFAULT_SWORD_ACTIONS, including SWORD_DIG and
SWORD_SWEEP. Player sweeping changes from an instanceof SwordItem check to a
canPerformAction query. Forge integrations and datapack reload behavior therefore
need their own contract. Target tiers additionally expose tool tags and sorting;
that does not turn the sword's cobweb-only correct-tool predicate into pickaxe
behavior.

## Original implementation boundary and current disposition

The original cursor was **ITEM-EQUIPMENT / IRON_SWORD_SEMANTIC_CONTRACT**:

1. Build the small original fixture and establish a complete inherited behavior
   inventory, with negative custom-tier/property/callback variants.
2. Prove constructor, attribute identity, hierarchy, durability and repair facts
   using exact data. Keep this proof separate from conversion eligibility.
3. Decide whether a scoped, implemented semantic recipe can preserve the affected
   mining and tool-action contracts. Any approved target-native behavior change
   needs an explicit migration contract; it is not silently equivalent.
4. If no implemented recipe covers those differences, return MANUAL/UNSUPPORTED
   with structured reasons and no serialized eligible candidate. Do not use a
   constructor-only proof to bypass inherited behavior.
5. Add source/target/readback and negative checks only for the actually implemented
   recipe. Preserve existing profiles and adapters.

Never mutate Minecraft/Forge classes, globally overwrite vanilla tags, invent a
Material-to-tag equivalence, or emit a fake adapter. Native slot effects, combat,
durability, repair, mining/reload, save/load and multiplayer require a separate
authorized comparison. No gameplay test was run during this audit.

The subsequent [implemented read-only inspection](IRON_SWORD_INSPECTION.md)
now supplies the original fixture, source structure/ASM/linkage checks, real API
contract checks, bounded resources and structured UNSUPPORTED reasons through
the service and developer CLI. Native bytecode-as-data tests verify the local
constructor/repair/mining-predicate facts; synthetic predicates are not a real
tag reload. No complete inherited behavior inventory, semantic recipe, converted
sword or native acceptance is claimed. This diagnostic milestone is not an excuse
to approve the constructor alone or repeat the same audit on every continuation.

Next independent implementation: [OWNED_ATTRIBUTE_MODIFIERS](ITEM_ATTRIBUTE_API_AUDIT.md),
a plain owned Item endpoint without SwordItem mining/ToolAction inheritance.
