# Owned attributes with durability

LB-10 / ITEM-EQUIPMENT / OWNED_ATTRIBUTE_DURABILITY implements one independent
**private static candidate**, not a seventh conversion profile or public support.
The original Forge 36.2.42 fixture has two Java-8 classes and seven methods.
The target is Forge 47.4.23 / Minecraft 1.20.1 / Java 17. No original class is
executed during inspection, remapping or readback.

## Admitted program

The entry registers one final owned Item subclass through DeferredRegister and
a zero-capture factory. That factory passes fresh native Properties followed by
exactly durability(128). The item preserves its constructor and two native
overrides: a fixed immutable MAINHAND modifier map and the second-argument
iron-ingot repair predicate. Damage is 2.0D and speed is -1.0D, both ADDITION;
the two inherited native UUID objects remain distinct and are not recreated.
All other slots receive the empty immutable map.

Capacity is 128 and stack size is 1. This program does **not** spend durability
on hit/mining, implement break notifications, or qualify anvil cost and material
consumption. Native NBT, equipment events and tooltip behavior remain the target
platform's behavior; they are not replaced with fabricated emulation.

The [source/API audit](ITEM_ATTRIBUTE_DURABILITY_API_AUDIT.md) records exact
sources and hashes. Guava 21.0 and 31.1-jre inventories are explicit selected
mapping inputs; neither the converter classpath nor an implicit Gradle-cache
lookup supplies missing data. No old Minecraft/Forge or fixture is bundled.

## Independent admission and transformation

ItemAttributeDurabilityContract checks the original whole graph before ASM
dataflow allocation: two final classes, seven bounded methods, exact properties
order/literal, repair argument and branches, immutable modifier initialization,
declarations and metadata. Unknown effects, annotations, properties, callbacks,
dependencies and resources block the operation.

Only after proving the extra factory call and repair method, a deep admission
view removes those already-checked elements to reuse the attribute grammar.
That view is never returned to the remapper, serialized, or substituted for the
original. The report labels its nested attribute proof as an admission view.
Original instructions, debug information and resources remain the conversion
inputs. Source, mapped and reopened composition proofs must match.

compileItemAttributeDurability is a distinct opt-in compiler route. It requires
the whole composition proof and exact official/SRG/declaration recipes, both
native virtual callbacks, UUID ownership/access and reviewed Guava ABI.
It does not combine the existing attribute-only and durable-only switches.
The independent ITEM_ATTRIBUTE_DURABILITY offline linkage policy rechecks
declarations, access, invocation kinds, fields and external references without
trusting a successful compiler result. No HIGH rule becomes VERIFIED merely by
renaming it.

The pipeline preserves bodies, applies approved symbol/descriptor remapping,
validates ASM and linkage, freezes complete entry ownership and hashes, serializes
a private ZIP, reopens it through the bounded reader, and repeats semantic,
resource and linkage checks. Cancellation removes scratch output. The public
result contains only proof/hash; it cannot export, install, Apply or activate
the candidate.

## Implemented operations and service

LegacyEngineService.planItemAttributeDurability uses the existing bounded,
cancellable worker and checks the selected original hash. The request must use
the unchanged logic base token, but ordinary conversion through that public
profile still rejects the combined fixture.

ItemAttributeDurabilityArtifactAdapter implements two OFFLINE operations:
item.attribute-durability-manifest.v1 and
item.attribute-durability-bindings.v1. They generate the distinct private
candidate identity and schema-versioned adapter references. They are metadata
generators, not gameplay shims. Existing adapter descriptors and runtime identity
legacybridge_runtime / 0.1.1-SNAPSHOT stay unchanged.

CompatibilityReport and TransformationLog retain schema versions. Every mapping
and metadata rewrite names an implemented operation. Accepted recipes, models,
translations and other admitted resources remain byte-identical; only reviewed
metadata is rewritten. No bootstrap projection replaces the owned classes.

## Verification and remaining gates

Structural, mappings, independent linkage, artifact-ownership, service parity,
six-profile refusal, selected-hash drift, malformed resources/dependencies and
cancellation tests are separate from native qualification. Exact counts,
commands and artifact hashes belong to the
[delivery ledger](evidence/item-attribute-durability-candidate-2026-10-01.json).

Native client/server, six-slot observations, equip/unequip accumulation,
NBT/event interaction, tooltip, ordinary-stack repair, save/load/relog and
multiplayer remain NOT_RUN. The candidate is not publicly installable, and
ITEM-EQUIPMENT remains PARTIAL with its prerequisite gates intact.

The attribute-only and durable-only candidates remain independent. Tools,
armor, arbitrary modifiers, durability consumption, full repair/anvil behavior,
mending and world migration remain outside this contract. The iron sword
diagnostic remains UNSUPPORTED; this composition does not remove its blockers.
