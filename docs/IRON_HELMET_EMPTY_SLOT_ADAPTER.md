# Iron helmet empty slot runtime operation

LB-10 / ITEM-EQUIPMENT now has a concrete target runtime operation for the authored
iron helmet. It is not an enabled armor conversion profile. The original fixture
still produces UNSUPPORTED, and no converted helmet JAR is emitted or installed.

## Implemented API and behavior

Legacy API: Forge 36.2.42 native ArmorItem with IRON, HEAD and fresh Item.Properties,
and its use(World, PlayerEntity, Hand) body.

Target API: Forge 47.4.23 ArmorItem with ArmorMaterials.IRON, ArmorItem.Type.HELMET
and fresh Item.Properties. The final LegacyIronHelmetItem has no fields or static
initializer. Its no-argument constructor admits no caller-supplied properties,
material or slot. The public static create factory is the stable catalog entrypoint.

Transformation: the runtime operation is implemented, but no converter recipe
invokes it yet. Catalog identity is item.iron-helmet-empty-slot.v1, revision 1,
RUNTIME phase. Its use override reads the received player's held stack once,
resolves the equipment slot from that stack, and reads the equipped stack once.
An occupied slot returns FAIL with the original held stack and no writes. An
empty slot receives a copy, then the original held stack has its count set to
zero and is returned through sidedSuccess.

Behavior differences: this deliberately preserves the legacy body on both logical
sides. It does not delegate to target occupied-slot swapping, add Binding Curse
or stack-match guards, use copyAndClear, or directly award ITEM_USED. Slot resolution
is not replaced with a HEAD constant: source MobEntity declares the static lookup,
whereas target Mob inherits its declaration from LivingEntity. Native target
equipment hooks remain native, not automatically qualified by body agreement.

Known limitations: inherited Equipable entrypoints, dispenser behavior, sound and
game-event hooks, rendering and trims, enchantment/anvil/wear, persistence, caller
threads, prediction and multiplayer remain separate contracts. The constructor
still registers native dispenser behavior. A use override alone does not adapt
that behavior. The operation cannot authorize an unreviewed constructor replacement.

## Evidence boundary

Headless tests read the actual reobfuscated runtime class and pinned source/target
native bytecode as data. Structural checks cover the factory, fixed constructor,
override descriptor, invocation owners and opcodes, branches and absence of extra
state. Finite synthetic stack/player models compare call ordering, copies, result
aliases and writes. No original mod or Minecraft classes are loaded for this proof.
This is not a native equipment, sound, synchronization or anti-duplication test.

The [inspection](IRON_HELMET_INSPECTION.md) records the available catalog operation
separately from its three conversion blockers. It explicitly does not authenticate
an installed runtime artifact. Missing construction and inherited contracts remain
UNSUPPORTED; no mapping confidence is promoted and no TransformationLog is fabricated.

All 66 previous catalog descriptors remain intact; this is one additive descriptor.
Runtime identity/version and report schemas are unchanged. Catalog availability is
not a new allowed profile: six existing profiles and simple-content-v1 Convert/Apply
remain unchanged. The factory is not invoked at LegacyBridge startup and no item is
registered merely by packaging the adapter.

Build results, failures if any, hashes and remaining gates are recorded in the
[artifact-bound ledger](evidence/iron-helmet-adapter-2026-10-05.json). Native client,
server, behavior and multiplayer qualification remain NOT_RUN.

## Next development

[IRON_HELMET_HEAD_EQUIP_EFFECTS](IRON_HELMET_HEAD_EFFECTS.md) now implements a
separate guarded sound-before-write operation for the finite native HEAD path.
It is not wired into this existing item's use override; this version stays intact.
Source PlayerEntity plays its equip sound before writing armor; target Player
verifies, writes and calls onEquipItem, with additional sound gates and GameEvent.EQUIP.
Do not schedule a constructor lowering before those semantic obligations are closed.
