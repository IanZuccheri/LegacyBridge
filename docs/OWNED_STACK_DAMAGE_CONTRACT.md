# Owned stack damage conversion contract

LB-10 / ITEM-EQUIPMENT now has a separate private inspection and conversion
candidate for the authored guarded stack helper. The engine preserves two owned
Java-8 classes and five methods, including all branches, return values, literals,
the registry supplier and the original helper body. This is not arbitrary item
behavior, a new public profile or permission to install the candidate.

The [source audit](OWNED_STACK_DAMAGE_API_AUDIT.md) records the real Forge
36.2.42 and 47.4.23 contracts and their provenance. The
[delivery ledger](evidence/owned-stack-damage-candidate-2026-10-04.json) separates
implementation, static validation, original archive checks and native QA.

## Admitted program

The entry registers one item through DeferredRegister on its mod event bus.
The private static zero-capture supplier constructs a final direct native Item
subclass. Its constructor passes fresh native Properties.durability(128) to
super. There are no item fields, interfaces, hook overrides or other callbacks.

The single public helper receives one ItemStack. Its exact control flow rejects
null, foreign item identity, native non-damaged state, observed damage at or below
zero, and damage above the native maximum. Only then it writes observed damage
minus one once to the same stack and returns true. Rejected paths return false
without writing. Owned class, field, factory and helper names can change; admission
does not compare a fixture hash or require its package name.

A helper name that collides with any inherited declaration is refused in both
source and target inventories. In particular, renaming repairOne to isDamaged
must not turn an owned helper into an accidental Forge callback.

There is no gameplay trigger. The helper does not read a player, world or inventory,
alter count, consume repair materials, copy or replace stacks, spawn drops, schedule
work, or synchronize clients. A future caller needs its own side, authority and
thread contract; this endpoint alone establishes none of them.

## Proof and transformation sequence

1. Read the bounded original archive without loading its classes.
2. Check the complete original graph and method bounds before dataflow allocation.
3. Compose exact mappings from the explicitly selected official/SRG data and API
   inventories. Verify native declarations, class kinds, opcodes and Forge hooks.
4. Run independent source linkage/access checks with the dedicated policy.
5. Remap only owned mod classes with the implemented preserved-logic operation.
   Preserve control flow, instruction roles and debug information.
6. Re-prove the target program and compare its canonical proof with the original.
   Validate target resources, ASM and independent linkage.
7. Serialize a private candidate with explicit entry ownership, hashes and metadata
   operations, reopen it, and repeat proof, resource, ASM and linkage checks.
8. Delete scratch and return a structured report/hash only. Cancellation at any
   stage must not publish bytes or leave a partial result.

The runtime adapter ID and version remain legacybridge_runtime / 0.1.1-SNAPSHOT.
The new owned-stack-damage-candidate-v1 identity has two implemented OFFLINE
operations: manifest generation and adapter-binding metadata. Native damage calls
use verified mappings tied to the existing preserved-logic operation. No empty
adapter, classloader fallback, raw-NBT emulation or old-game library is introduced.

## API boundaries

The native damage APIs dispatch through IForgeItem hooks. The contract requires
the final native ItemStack, the reviewed Item and Properties shapes, unshadowed
default hooks and the native final zero-argument Item.getMaxDamage declaration.
Inventory drift or a missing external declaration blocks the whole candidate.
Mapping confidence becomes VERIFIED only for an exact reviewed recipe inside
the admitted graph, never merely because two method names resemble each other.

The native setter clamps negative damage to zero, not to maximum durability.
The explicit upper guard must survive. Native numeric NBT coercion remains native;
this helper is not a validator for arbitrary malformed tags. Persistence does not
prove synchronization, and static body equality does not prove native behavior.

## Service and public product

LegacyEngineService.planOwnedStackDamage uses the existing bounded cancellable
worker and typed ConversionRequest/CompositionCandidate contracts. It requires
the unchanged logic-preserving base and an explicit mapping-data selection.
The direct engine and service must return equivalent candidate reports.

All six public profiles continue rejecting this program. Public Convert/Apply and
simple-content-v1 are not widened. Existing private attribute, durability and
attribute-with-durability routes remain independent. Candidate operations cannot
be combined by setting another metadata flag.

## Validation still required

Static tests cover source/target/readback proof, renamed owned symbols, damaged
metadata, missing dependencies/resources/APIs, changed native access and dispatch,
guard and mutation changes, oversized frame bounds, hash drift, cancellation and
output absence. The ledger records actual runs, not merely planned tests.

Native client/server/multiplayer behavior remains NOT_RUN. Later isolated QA must
compare source and target for damage 0, 1, 127 and 128, negative/out-of-range values,
null/empty/foreign/unbreakable stacks, two independent instances, count invariance,
save/load and reconnect. Automatic repair, wear/break notifications, mending,
enchantments, anvil costs, broad tools/armor and old-world migration remain outside
this contract. No Minecraft process or original mod class is executed by conversion.
