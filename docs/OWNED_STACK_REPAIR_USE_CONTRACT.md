# Owned stack repair through item use

LB-10 / ITEM-EQUIPMENT / **OWNED_STACK_REPAIR_USE** is a separate private
preserving route for the authored Forge 1.16.5 fixture. It extends the previous
helper scenario with one native item-use override without widening the
five-method helper grammar or any public conversion profile.

The [API audit](OWNED_STACK_REPAIR_USE_API_AUDIT.md) records the exact
36.2.42 and 47.4.23 declarations and native caller differences. Static verification
does not establish client, server, behavior or multiplayer qualification.

## Admitted program

Two final Java-8 owned classes contain exactly six methods. The entry registers
one item through the existing finite DeferredRegister graph. The item constructor
only supplies native durability 128, implying stack size one.

The helper rejects null, a foreign item, an undamaged stack, nonpositive damage
or damage above the native maximum before one damage-minus-one write. The use
override obtains the received player's received-hand stack exactly once.
On the logical client it returns native SUCCESS without calling the helper.
On the logical server it calls the helper once on this, then returns CONSUME on
true or PASS on false. Every result holder contains that same stack.

No copying, count mutation, second hand lookup, inventory replacement, scheduling,
custom packet, block access, loop, retained state or repair ingredient is admitted.
Renaming owned classes, registration fields, factory and helper is permitted
within the proven grammar; this is not a whitelist of one fixture hash.

## Independent verification

The structural contract checks the entire original graph, limits, access,
opcodes, branch destinations, exact generic callback signature and debug/frame
references before creating a deep admission-only helper view. Removing the
fully checked use method from that view allows reuse of the older helper proof.
The view is never transformed or emitted. All six original executable bodies
remain in the actual source, target and reopened candidate.

Mappings compose the explicitly selected official/MCP/API data, rather than
promoting a likely rename to VERIFIED. Exact recipes cover Item.use, the
Player-owned callsite declared by LivingEntity, the public final logical-side
field and the three static erased result factories. Native hierarchy, interfaces,
declaration access and invocation kinds are checked, including inherited helper
collisions and default Forge damage hooks. Unknown external references block.

Offline linkage independently repeats declaration/access/dispatch checks and
the whole-program proof. Source and target proofs must match. After serialization,
the bounded safe ZIP reader reopens the private JAR, checks complete byte
ownership, CRCs, resources, ASM and linkage, then re-proves the target graph.

## Service and artifact boundary

The typed service operation is
`LegacyEngineService.planOwnedStackRepairUse(ConversionRequest)`.
It uses the existing bounded cancellable worker with explicit mapping data and
selected-original hash. The result contains structured proof and candidate hash,
not candidate bytes, an output path, an install receipt or an Apply capability.

Two implemented OFFLINE operations generate a distinct private manifest and
adapter-binding metadata. Their identity is `owned-stack-repair-use-candidate-v1`.
The runtime mod identity and ABI remain `legacybridge_runtime` /
`0.1.1-SNAPSHOT`. Existing adapters and six public profiles retain their scope.
The player Convert/Apply path remains simple-content-v1.

The temporary archive is deleted on completion or cancellation. Original bytes,
modId, registry identity, recipes, model and translations are preserved. Required
metadata rewrites are recorded in the versioned TransformationLog. No original
mod class or native class is executed during these operations.

## Headless validation

The [2026-10-05 ledger](evidence/owned-stack-repair-use-candidate-2026-10-05.json)
records 6,944 passing engine cases, including 1,932 new cases, with no failures
or skips in the corrected full regression. Two initial compiler shadow-negative
failures were fixed without weakening the tests; 24 ancestor/interface negatives
were added. The original rebuilt with the same hash and four passing archive
tests. Assemble, package audits and installed CLI checks passed serially.

All 64 preceding adapter descriptors and three earlier private candidates remained
unchanged. The new private candidate has two classes/six methods and 75 logged
transformations, including four metadata rewrites; its bytes do not escape the
service. These checks do not execute the fixture or establish native behavior.

## Behavior limits

Client SUCCESS is intentional prediction: it consumes the ordinary hand loop
and requests a swing even if the server later declines the repair. Replacing
it with PASS would change off-hand fallthrough. The guarantee is one helper call
per qualifying server callback, not one repair per arbitrary click or packet.

Both pinned native creative callers restore the previous damage. Native
spectator, cooldown and Forge cancellation paths can bypass the callback.
Block or entity interaction can consume input first. The native game-thread,
prediction, sequencing and inventory-sync paths remain unmodified; the callback
does not establish thread, world or player authority for arbitrary external callers.

Native comparison remains **NOT_RUN**. Required later observations include
survival air-use, creative restoration, both hands, main-hand consumption,
client swing with rejected repair, cancellation/cooldown/spectator bypass, two
independent players, repeated input, save/load/reconnect and visible damage
synchronization without count change or duplication.

Wear, break notifications, repair costs, anvil, mending, tools/armor, generic NBT,
world migration and arbitrary item-use behavior remain outside this route.
