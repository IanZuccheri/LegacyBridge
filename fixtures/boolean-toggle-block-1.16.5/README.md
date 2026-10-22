# Original boolean-toggle block fixture

Project-authored Forge **1.16.5 / 36.2.42**, version1.0.0, Java8 bytecode.
Two owned classes/eight methods register one full-cube Block and its BlockItem
at `legacybridge_boolean_toggle:bridge_block`. The sole custom state is
BooleanProperty `active`, initialized false with explicit Boolean.FALSE.

The use callback returns SUCCESS on the logical client, without world writes.
On the logical server, exactly one setBlock uses the callback's world, position
and state cycled through ACTIVE, with flags3, followed by CONSUME. The boolean
setBlock result is intentionally ignored as part of this exact source pattern:
CONSUME does not prove a successful write. There is no ItemStack/inventory
mutation, loot, packet, sound, GameEvent, redstone emission or static gameplay
counter. This does not implement Lever or arbitrary interactions.

The model graph has exactly two active variants: false references
`block/bridge_block` (stone), true `block/bridge_block_active` (cobblestone).
Both are authored cube_all models. The item parent is the false local model.
One shapeless iron-ingot recipe and en_us/pt_br translations accompany them.
Only vanilla resources are referenced, not copied. The actual 1.16.5 and 1.20.1
client archives were inspected read-only; cube_all, stone/cobblestone models
and both referenced texture PNGs have byte-identical hashes in these baselines.

Build serially with Java17 from the repository root:
`gradlew.bat -p fixtures/boolean-toggle-block-1.16.5 build --offline --max-workers=1`.
Four standalone tests inspect archive metadata, bounded resources and Java8
headers only. No fixture class is loaded or executed during conversion/tests.

Private candidate only. Native callbacks/permissions/prediction vary by version;
two-client interaction, reconnect, save/load, cancellation/protection, reach,
hands/sneak, actual writes and inventory/drop invariants remain NOT_RUN.
No public profile, installation/Apply, world migration or universal support.
