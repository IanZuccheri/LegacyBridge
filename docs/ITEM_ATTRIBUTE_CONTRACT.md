# Owned item attribute candidate

LB-10 / ITEM-EQUIPMENT / OWNED_ATTRIBUTE_MODIFIERS implements one private,
body-preserving candidate. It does not introduce a public conversion profile,
installation permission, equipment simulation or a runtime adapter for arbitrary
items. Native comparison remains NOT_RUN.

## Exact admitted program

The original project-owned Forge 36.2.42 fixture contains two Java-8 classes and
six methods. Its entry registers one final Item subclass through a zero-capture
static supplier on the mod event bus. The factory creates fresh Item.Properties;
the item constructor only delegates to super.

One static final immutable multimap contains two ADDITION modifiers: native
ATTACK_DAMAGE with amount 2.0D and native ATTACK_SPEED with amount -1.0D. Each uses
the corresponding inherited native Item UUID object. The single override returns
this map for MAINHAND and an empty immutable multimap for every other slot.
There is no mutable per-player or per-stack state. Registry/mod identity, owned
names, bounded modifier name literals and accepted resource bytes are preserved.

The [pinned API audit](ITEM_ATTRIBUTE_API_AUDIT.md) explains why equal UUID values
are insufficient: native tooltip code compares these UUID references by identity.
The recipe therefore preserves protected inherited field reads; it never creates
replacement UUIDs or substitutes a name-matched material, tier or attribute.

## Implemented pipeline

ItemAttributeContract checks the entire finite source graph, including access,
generic signatures, annotations, constructors, supplier handle, exact branch
targets and literal effects. Stack/local bounds are checked before ASM dataflow
allocates frames; oversized declarations are refused at admission. The contract is repeated after remapping and after
serialization. Its target ContentPlan is only a resource inventory: no bootstrap
or admission projection replaces any original method.

VerifiedLogicMappings.compileItemAttributes composes official/SRG data and
verifies actual declarations before creating scoped rules. Unmapped external
symbols are blocked. The owned override and inherited UUID aliases resolve to
the actual Item declarations, with exact access and invocation kind. Existing
profiles do not acquire this recipe implicitly.

Guava 21.0 and 31.1-jre are explicit API inputs, with reviewed binary hashes.
Only Multimap, ImmutableMultimap and its Builder are referenced by the program;
their actual ancestors are included for offline linkage. The real hierarchy
differs across versions, so unchanged package names do not establish compatibility.
Only builder(), put(Object,Object), build() and of() are admitted. No Guava
binary, Minecraft source, fixture or private API bundle is added to the product.

OfflineLinkageVerifier.CallbackPolicy.ITEM_ATTRIBUTES independently rechecks
the graph, required public virtual callback, protected/static/final UUID fields,
constant attributes/enums, Guava declarations and opcodes. ASM frames and access
checks use inventories, not Class.forName or original mod execution.

CompositionCandidateBuilder accounts for every source/output entry, writes
private metadata, reopens a bounded temporary JAR and repeats resources, ASM,
linkage and contract checks. Source/target/readback proofs must agree. The
temporary is removed on success or cancellation. TransformationLog records the
exact rule, confidence and implemented operation for each changed symbol/resource.

## Service and operation identity

LegacyEngineService.planItemAttributes(ConversionRequest) uses the existing
bounded worker, progress, cancellation and selected-input hash checks. The
request must explicitly use the unchanged LOGIC_PRESERVING_V1 base, but ordinary
conversion with that profile still rejects this program.

The result is CompositionCandidate: immutable proof/report and candidate hash,
never bytes or an installation receipt. The prospective output path is not
written. This operation does not call CLI.main, parse stdout, load game classes
or modify the session's loaded mods.

ItemAttributeArtifactAdapter implements two real OFFLINE metadata operations:
item.attributes-manifest.v1 and item.attributes-bindings.v1. The manifest marks
item-attributes-candidate-v1 and STATIC-CANDIDATE-ONLY; bindings use schema 1 and
runtime legacybridge_runtime / 0.1.1-SNAPSHOT. These generators are not empty
runtime shims. Existing adapter descriptors and the six public profiles remain
unchanged.

## Validation and limits

[Artifact-bound evidence](evidence/item-attributes-candidate-2026-10-01.json)
records executed checks, original/result hashes, regressions and package audit.
Positive tests inspect real reobfuscated fixture bytes; negative tests change
structure, instructions, API declarations, mapping data, resources, dependencies
and cancellation stage. Renamed owned symbols verify that admission is structural,
not a fixture hash whitelist.

Static success does not establish native equipment swapping, actual damage,
attack cooldown, tooltip display, NBT override precedence, mod event interaction,
save/reload or multiplayer equivalence. These comparisons need separately
authorized original and target game environments.

Unsupported: other attributes/slots/operations/amount expressions, random or
recreated UUIDs, mutable multimaps, retained builders, stack-sensitive hooks,
additional callbacks, arbitrary helpers, tool/armor inheritance, tier/material
migration, durability/repair composition, external mods and unknown resources.
The sword diagnostic remains UNSUPPORTED for its distinct semantic blockers.
