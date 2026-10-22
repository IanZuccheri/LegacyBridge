# LB-08: finite owned Item hierarchy and dispatch

Implemented as a **private static candidate**, not a seventh conversion profile.
All six existing admission grammars and public Convert/Apply remain unchanged.
`LINKAGE-GENERAL` and `COMPOSE-01` remain PARTIAL; neither native equivalence nor
publication authority follows from these checks. [Evidence ledger](evidence/owned-hierarchy-2026-09-19.json).

## Implemented boundary

`LogicPreservingConverter.planOwnedHierarchy` and
`LegacyEngineService.planOwnedHierarchy` share the existing bounded conversion
worker, mappings, cancellation, body preservation, resource validation, ASM and
offline linkage/access machinery. The result is a `CompositionCandidate`: inert
report/hash only, never a returned/published JAR, install approval or runtime receipt.
No CLI command, UI button, exporter, native harness or new profile was added.

The finite shape is one public abstract owned class directly extending native
Item, and one public final registered owned leaf extending that base. Both have
exactly one no-argument constructor: protected in the base, public in the leaf.
The leaf must start with `ALOAD 0; INVOKESPECIAL Base.<init>()V`. The base uses
the already reviewed literal `new Item.Properties().stacksTo(1..64)` / native
super prefix. Base/leaf interfaces, static initializers, extra descendants and
constructor overloads are not admitted in this candidate. Other owned helpers
remain subject to all existing body/reference/linkage rules, not a blind copy.

`OwnedItemHierarchy` records each relevant base/leaf/native-Item method call in
the two owned Item classes, with caller, executable instruction index, opcode,
symbolic owner, resolved declaration and selection **conditional on a non-null
registered-leaf receiver**. Virtual calls select the leaf override when present;
direct-super calls select the resolved direct-super member. This is not receiver
dataflow analysis or general JVM method selection. Only public/protected instance
slots are admitted. One owned virtual-dispatch witness and one direct owned-super
witness are required. Helpers/other call owners are still checked by the separate
closed mapping and linkage gates; they are not claimed as dispatch-proof sites.

Bounds: 32 owned classes, 128 methods per class (256 aggregate in shared shape
admission), 4,096 ASM instruction nodes per method, 32,768 aggregate nodes, plus
existing archive/engine limits. Cancellation
checkpoints apply; these limits are not a hard shared-heap isolation guarantee.

The proof is derived independently from source, transformed and reopened ZIP
bytes. Source proof remapped using exact rules must equal target proof; reopened
proof and content admission must equal the prepared candidate. A proof alone
does not validate method bodies or access. All original/target/reopened classes
also pass the independent ASM and offline linkage/access checks, including
protected receivers across packages, final/abstract/static overrides, constructors
and unknown external references. Mod classes are never defined or initialized.

## APIs, mappings and real implemented operations

Legacy API: Minecraft 1.16.5 / Forge 36.2.42 Item, Item.Properties and ItemStack.
Target API: Minecraft 1.20.1 / Forge 47.4.23 equivalents from exact local inventories.
Transformation: reuse verified constructor/properties/count/foil recipes, native
override renaming and owned inherited-member alias rules; retain owned bodies,
descriptors where unchanged, exception/control flow and debug information.
Behavior differences: platform-dependent Item behavior still requires isolated
native comparison; no new runtime dispatch shim attempts to emulate Minecraft.
Known limitations: finite hierarchy only; no arbitrary Java/API or native support.

Reviewed source archives (local development evidence, not packaged data):

| Exact archive | SHA-256 |
| --- | --- |
| `forge-1.16.5-36.2.42_mapped_official_1.16.5-sources.jar` | `bbe2d148f96694271c43fc2ba584463d75d9ffc490abd0469ae66b831293d376` |
| `forge-1.20.1-47.4.23_mapped_official_1.20.1-sources.jar` | `4bd6656a7cbe3809da68f3b709ca0f2fa186575f58538668c58ce30cb38c4932` |

Both reviewed `Item.isFoil(ItemStack)` implementations return
`stack.isEnchanted()`. This narrow observation does not prove all enchantment or
Item behavior equivalent. Existing production rules map `func_77636_d` to
`m_5812_`, `func_190916_E` to `m_41613_`, and `func_200917_a` to `m_41487_` with
exact owners/descriptors. No HIGH mapping was promoted and no new external API
recipe was guessed. Dispatch distinction follows the real
[JVMS method overriding and selection rules](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.4.5),
restricted to this reviewed shape. Full mapping/API data is still an explicit
local input for this private candidate; no additional data is redistributed.

`HierarchyArtifactAdapter` implements two additive OFFLINE operations:
`logic.hierarchy-manifest.v1` and `logic.hierarchy-bindings.v1`. They generate
actual metadata identifying `owned-hierarchy-candidate-v1`, hierarchy revision 1,
and `STATIC-CANDIDATE-ONLY`, with bindings to implemented operations. Other
metadata/pack rewrites reuse existing operations. Every change has a VERIFIED
rule and adapter binding in schema-1 TransformationLog. Compatibility/report
schemas, runtime identity/version and existing executable adapters are unchanged.
These operations are metadata generation, not empty runtime compatibility shims.

## Fixture, validation and exclusions

The [original authored Forge fixture](../fixtures/owned-hierarchy-1.16.5/README.md)
has four classes/13 methods, an abstract base and final leaf in different packages,
protected final base field, private mutable leaf field, an abstract hook, virtual
dispatch, native override/super chain and an inherited symbolic-owner alias.
One registry item, helper, smelting recipe, tag, model and two translations remain.
Executable bodies are not replaced; assets/data bytes remain unchanged. Ordinary
profiles and the older private constructor/composition routes reject this layout.

Static tests cover real reobfuscated input, deterministic candidate serialization,
reopening, direct/service agreement, unchanged original, absent output, cancellation
and negative shapes/access/opcodes/unknown APIs. Separate inert structural tests
are not real Minecraft API evidence. Exact counts/hashes/logs are in the ledger.
No Minecraft, server, GUI or original class was run. Client/server discovery,
behavior, multiplayer, classloader/JPMS behavior and cacheless native execution
of the final public artifact remain **NOT_RUN**. One fixture is not real-mod coverage.

Unsupported here: interface/default-method dispatch, diamonds, arbitrary ancestry,
multiple registered leaves, general constructors/properties, unknown external
contracts, new lifecycle/network/capability/resource features. The constructor
delegation candidate and this hierarchy candidate are separate; their union is
not automatically admitted. Existing public runtime support does not expand.

Historical next implementation, now delivered privately:
[LB-08 / LINKAGE-GENERAL / OWNED_INTERFACE_DEFAULTS](OWNED_INTERFACE_DEFAULTS.md).
Prove one finite owned interface/default-method path in an owned helper, with
explicit override and `Interface.super` selection, exact handle/access/opcode
validation and a different real fixture. Start without diamonds or new external
APIs. Keep the candidate private and all six public grammars intact; do not repeat
this hierarchy harness or count native gates as passed.
