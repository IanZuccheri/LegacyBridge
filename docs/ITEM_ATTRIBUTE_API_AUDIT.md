# Owned item attribute modifiers: source audit and next finite cut

Audit date: 2026-10-01. Proposed cursor: **LB-10 / ITEM-EQUIPMENT /
OWNED_ATTRIBUTE_MODIFIERS**. This document is a read-only feasibility audit, not
an implemented mapping recipe, supported profile, candidate or native result.
No conversion rule, adapter, public grammar or installation permission is
enabled by this audit. The original fixture was subsequently prepared and built
as recorded below; it is not a converted or supported artifact. The native iron-sword inspection remains UNSUPPORTED.

## Pinned evidence

The following local artifacts were inspected without loading game/mod classes,
running builds, downloading data or opening a game. Paths are recorded as
development provenance, not an operational cache dependency for the product.

- Official-mapped Forge sources reside under
  `${DEVELOPER_HOME}/.gradle/caches/forge_gradle/minecraft_user_repo/net/minecraftforge/forge/`,
  in `<forge-version>_mapped_official_<minecraft>/forge-<same>-sources.jar`.
- Guava binary/source artifacts reside under
  `${DEVELOPER_HOME}/.gradle/caches/modules-2/files-2.1/com.google.guava/guava/`.
- Local Minecraft `minecraft_repo/versions/<version>/version.json` explicitly
  selects Guava 21.0 for 1.16.5 and 31.1-jre for 1.20.1. Both source archives and
  the exact binary declarations were read; `javap -s` does not execute classes.

| Artifact | SHA-256 |
| --- | --- |
| Forge 1.16.5-36.2.42 official-mapped sources | `bbe2d148f96694271c43fc2ba584463d75d9ffc490abd0469ae66b831293d376` |
| Forge 1.20.1-47.4.23 official-mapped sources | `4bd6656a7cbe3809da68f3b709ca0f2fa186575f58538668c58ce30cb38c4932` |
| Guava 21.0 binary | `972139718abc8a4893fa78cba8cf7b2c903f35c97aaf44fa3031b0669948b480` |
| Guava 21.0 sources | `b186965c9af0a714632fe49b33378c9670f8f074797ab466f49a67e918e116ea` |
| Guava 31.1-jre binary | `a42edc9cab792e39fe39bb94f3fca655ed157ff87a8af78e1d6ba5b07c4a00ab` |
| Guava 31.1-jre sources | `8ab1853cdaf936ec88be80c17302b7c20abafbd4f54d4fb54d7011c529e3a44a` |

Line references below refer to the files inside those exact source archives.
No source text, binary or Guava dependency was copied into the public artifact.

## Real API and dispatch contract

1. Source `net/minecraft/item/Item.java:296-299` and target
   `net/minecraft/world/item/Item.java:334-337` both declare public virtual
   `getDefaultAttributeModifiers(slot)`, returning an empty immutable multimap
   in the base class. The method is deprecated in both versions in favor of the
   stack-sensitive API; it has not disappeared or become an inert callback.
2. Source `IForgeItem.java:61-65` delegates its stack-sensitive
   `getAttributeModifiers(EquipmentSlotType, ItemStack)` to
   `getItem().getDefaultAttributeModifiers(slot)`. Target lines 60-64 delegate
   the corresponding `EquipmentSlot` overload through `self()`. Therefore an
   owned override of the older vanilla endpoint remains on the real call path.
   This is a source-derived conclusion, not an executed game comparison.
3. The erased override descriptor changes from
   `(Lnet/minecraft/inventory/EquipmentSlotType;)Lcom/google/common/collect/Multimap;`
   to
   `(Lnet/minecraft/world/entity/EquipmentSlot;)Lcom/google/common/collect/Multimap;`.
   Attribute/AttributeModifier packages and generic signatures also change.
   Production names must be composed from the explicit official/SRG inputs and
   validated against both API inventories; this table is not a manual mapping DB.
4. Source and target slot enums (`EquipmentSlotType.java:3-9`,
   `EquipmentSlot.java:3-9`) have MAINHAND, OFFHAND, FEET, LEGS, CHEST and HEAD;
   MAINHAND's native name is `mainhand` in both. The proposed method compares
   the received enum identity to MAINHAND, not ordinal or a guessed slot index.
5. Both `AttributeModifier.java:26-36` constructors accept
   `(UUID, String, double, Operation)` and retain the supplied UUID, amount and
   operation. Both `Operation` enums at lines 95-108 define ADDITION with value
   zero. The random-UUID and Supplier-name overloads are distinct and must not
   be admitted by a broad descriptor/name allowlist.
6. Both `Attributes` classes define ATTACK_DAMAGE and ATTACK_SPEED as public
   static final Attribute fields, with native IDs `generic.attack_damage` and
   `generic.attack_speed` (source lines 11-13; target 12-14). Source
   `ModifiableAttributeInstance.java:137-151` and target
   `AttributeInstance.java:134-148` sum ADDITION modifiers before the multiply
   operations. This is not proof of whole combat/cooldown/enchantment behavior.

## UUID identity must not be approximated

Source Item lines 45-46 and target Item lines 56-57 expose protected static final
`BASE_ATTACK_DAMAGE_UUID` and `BASE_ATTACK_SPEED_UUID`. Their values are
`CB3F55D3-645C-4F38-A497-9C13A33DB5CF` and
`FA233E1C-4180-4865-B01B-BCCE9785ACA3`, respectively, in both versions.

Crucially, source `ItemStack.java:626-632` and target
`ItemStack.java:684-690` compare modifier UUIDs to these fields with **identity
comparison (`==`)**, then adjust displayed amounts. Reconstructing an equal
UUID with `UUID.fromString` is not equivalent for this tooltip branch. The
fixture should read the inherited native fields directly. Resolve the emitted
owner through the owned subclass hierarchy and verify protected/static/final
access in both APIs; never map arbitrary same-named owned fields to Item fields.

The two modifiers should use distinct base fields and preserve the original
attribute-to-UUID pairing. Sharing these native identities with normal held
items is intentional; adding per-instance/random UUIDs could leave stale or
stacking modifiers during equipment changes and is outside this cut.

## Guava contract is exact, not an unchanged-package exemption

The checked declarations in both real Guava binaries are:

| Call | Opcode and erased descriptor |
| --- | --- |
| `ImmutableMultimap.builder` | INVOKESTATIC `()Lcom/google/common/collect/ImmutableMultimap$Builder;` |
| `ImmutableMultimap$Builder.put` | INVOKEVIRTUAL `(Ljava/lang/Object;Ljava/lang/Object;)Lcom/google/common/collect/ImmutableMultimap$Builder;` |
| `ImmutableMultimap$Builder.build` | INVOKEVIRTUAL `()Lcom/google/common/collect/ImmutableMultimap;` |
| `ImmutableMultimap.of` | INVOKESTATIC `()Lcom/google/common/collect/ImmutableMultimap;` |

Guava 21.0 `ImmutableMultimap.java:124-125,167-170,277-296` and 31.1-jre
lines `127-128,170-177,296-301` implement this finite builder path. They reject
null entries and build an immutable list multimap. Their internal storage is
different; no internal collection class, ordering comparator, mutation method
or arbitrary Guava operation should be admitted. In particular, `put` on the
finished ImmutableMultimap has a different boolean descriptor and is not the
builder's put operation. Pin library/API sources explicitly for linkage; do not
use the converter's classpath Guava as evidence for the mod's source API.

## Authored fixture and pending conversion contract

Two Java-8 classes, compiled/reobfuscated with Forge 36.2.42:

- A final entrypoint using the existing one-item DeferredRegister/mod-bus graph,
  named static `createItem():Item`, and one registry name such as
  `bridge_attribute_item`. The factory calls the owned subclass constructor with
  fresh `new Item.Properties()` and no other property calls.
- A final class directly extending Item. Constructor `(Item.Properties)` only
  calls super. One private static final
  `Multimap<Attribute, AttributeModifier>` field is initialized once using the
  exact immutable builder chain. No retained builder or mutable per-player state.
- The clinit creates exactly two native modifiers in fixed order: ATTACK_DAMAGE
  / inherited BASE_ATTACK_DAMAGE_UUID / `2.0D` / ADDITION, and ATTACK_SPEED /
  inherited BASE_ATTACK_SPEED_UUID / `-1.0D` / ADDITION. Each fixed printable name
  identifies the fixture; no names supplied by callbacks. Double literals are
  exact and must stay doubles, not float-to-double approximations.
- The only override is public `getDefaultAttributeModifiers(slot)`: return the
  owned immutable field for MAINHAND, otherwise `ImmutableMultimap.of()`.
  No super callback, stack reads, NBT reads, event listener or gameplay write.
- Expected shape: two classes, six methods (three entrypoint methods and
  subclass ctor/clinit/override). Confirm emitted bytecode after creating it;
  do not manufacture expectations from these proposed counts.
- Use existing finite generated-item/vanilla iron-ingot texture resources, one
  recipe and en_us/pt_br. No sword-specific model is needed; this item is
  explicitly not a sword merely because it supplies attack attributes.

This does not inherit SwordItem's Material-to-sword-efficient mining predicate
or sword ToolActions. Base Item getDestroySpeed returns 1F on both inspected
sources (105-106 / 135-136), and isCorrectToolForDrops returns false (149-150 /
201-202). Source Item's empty default toolClasses and target IForgeItem's
`canPerformAction` default false (484-486) do not authorize adding tool behavior.
The fixture has no stack-damage/durability/tier/repair logic or extra callbacks.

## Native context and differences kept outside the proof

Source `ItemStack.java:827-850` and target `868-891` give an AttributeModifiers
NBT list precedence over the default endpoint. Both then call ForgeHooks;
source `ForgeHooks.java:1059-1063` and target `996-1000` post
ItemAttributeModifierEvent on the native Forge bus. This finite converter must
retain the callback instead of bypassing native NBT/event processing. It does
not support arbitrary listeners, NBT synthesis or third-party mutations.

Source `LivingEntity.java:2275-2282` and target `2415-2422` remove the previous
equipment's modifiers and add the replacement's transient modifiers. Endpoint
equality alone does not qualify swap detection, death/clone, relog, networking,
other mods' listeners or tooltip rendering. Shared immutable constants are
intentional; player/entity state must stay entirely in the native attribute
system, not in an adapter singleton.

## Implementation plan and failure gates

Inference from the sources: this closed endpoint is a plausible next private
code-preserving candidate, unlike pure-renaming SwordItem. It is not approved
or implemented by this document.

1. Add the small fixture and source contract before rules. Retain every owned
   class/method/instruction and exact constants; use the durable-item candidate
   architecture, not its repair grammar. Do not implicitly compose attributes
   with durability, tools or food.
2. Add an opt-in branch to VerifiedLogicMappings only after checking native
   override/hierarchy/field/member/Guava declarations and opcodes in both exact
   supplied API inventories. Existing compiler recipes do not currently admit
   this full attribute/UUID/Guava path. Unknown references remain blocking.
3. Preserve source/target/reopened proofs, ASM, offline linkage and protected
   field access. Bind changes to implemented descriptor/remapping operations;
   add no fake runtime attribute adapter. The six public profiles must continue
   to refuse the fixture until an explicit later promotion.
4. Negatives must cover wrong/extra slot, reversed branch, extra override,
   SwordItem/TieredItem supertype, wrong field owner/access/staticness,
   custom/random/recreated/duplicate/swapped UUIDs, unexpected attributes,
   multiply operations, float-vs-double constants, NaN/infinity, mutable maps,
   retained builder, extra builder calls, side effects, null values,
   dependencies, missing API declarations, resources and cancelled readback.
5. Separate authorized native QA must compare MAINHAND and the other five slots,
   exact amount/operation/UUID identity, repeat-call immutability, independent
   players, equip/unequip/hand swap without accumulating modifiers, tooltip,
   NBT override, native event interaction, save/relog/death and multiplayer.
   Do not assert combat, damage, reload or world migration from static proof.

## Prepared original and next implementation

The subsequent original fixture exists at `fixtures/item-attributes-1.16.5`.
Its standalone offline build and four archive-only tests passed. The reobfuscated
JAR SHA-256 is `b7cafa19ec83bc6ac3ec5a2a0935341df3270b478e1dd765dd2ac4fa350e4c99`.
Archive inspection/javap confirmed two Java-8 classes and six methods, exactly
the proposed factory, slot branch and immutable initialization. The original
mod has not been loaded or executed.

The emitted override is `func_111205_h`; the two inherited UUID fields are
`field_111210_e` and `field_185050_h`, referenced through the owned subclass.
These are observations from this exact original, not generic approved mapping
rules. The subsequent private implementation resolves their real declaring owner
and protected access independently in source, target and reopened candidate.

An explicit developer profile was prepared at
`build/item-attributes-20261001/mapping-profile.properties`, SHA-256
`252b55f8ef1298f83cda4d7b1be956637eec98753cda4c8dca55e13ffcf1b312`.
It retains the previously selected platform/API paths and adds the two audited
Guava binaries as declarations. It is local developer input, not included player
data, implicit cache lookup, redistribution approval or semantic verification.
Do not silently use the engine's own Guava when the selected source data is absent.
The installed developer `mappings` command read the two original classes with
complete explicitly selected data and no bytecode changes. It still returned
review-required: 17 MANUAL and one UNSUPPORTED entry. Guava's unchanged packages
have no generic approved recipe, and DeferredRegister's changed return descriptor
requires the finite preserving registration recipe. These symbol counts are not
percentages of mod compatibility or approved attribute behavior.

Subsequent implementation: [the owned-attribute contract](ITEM_ATTRIBUTE_CONTRACT.md)
now supplies strict source/target proof, exact opt-in mappings, protected-field
access, explicit Guava linkage and private serialization/readback with negatives.
Its two new OFFLINE operations generate provenance/bindings, not an attribute
behavior emulator. The generic mapping command is still not approval to convert.

The candidate is not exportable/installable and does not expand public profiles.
No game, server, source-mod execution or native comparison was run. All native
dimensions remain NOT_RUN; original fixture build is a separate kind of evidence.
