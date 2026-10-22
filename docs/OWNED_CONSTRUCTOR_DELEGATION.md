# LB-08: finite owned constructor delegation

This is a private static candidate, **not another public conversion profile**.
The six existing grammars and public Convert/Apply route are unchanged.
`LINKAGE-GENERAL` is PARTIAL; native comparison and promotion remain required.
Current evidence: [versioned ledger](evidence/owned-constructors-2026-09-19.json).

## Implemented contract

`LogicPreservingConverter.planOwnedConstructors` and the bounded worker method
`LegacyEngineService.planOwnedConstructors` share the existing conversion engine.
They return `CompositionCandidate`: an immutable report and candidate hash,
never a publishable archive, installation approval or runtime-success receipt.
There is no new CLI conversion option, UI button, native harness or output exporter.

The existing registration/resource envelope is retained. One direct native Item
subclass may have 2–5 constructors, forming one finite acyclic chain covering
every constructor. The public entry is exactly `()V`; the others are private,
with one to four `int` arguments. `this(...)` must be the first invocation on
`ALOAD 0`, with literal integers or unchanged integer parameters, using
`INVOKESPECIAL` with an exact owned target. No pre-delegation computation is
interpreted, moved or replaced. The terminal constructor must use the existing
reviewed `new Item.Properties().stacksTo(limit)` / direct `super(properties)`
prefix, with a statically resolved limit in 1..64.

The constructor graph is derived independently from the actual source bytes,
remapped bytes and reopened candidate. Source/target graph and arguments must
agree. Offline initialization checking allows `this(...)` for only this proved
owner; ordinary verifier entrypoints retain their original restriction. ASM,
access/staticness/opcode checks and closed external-reference mappings still
apply. The graph alone never authorizes bytecode or a transformation.

Method bodies, post-super field assignments, constructor unwind order, helper
calls, control flow, literals and debug data are checked by the existing body
preservation verifier. Frames/maxima are computed offline. Unknown external
members, invalid initialization, private access violations, cycles, unreachable
overloads, unsupported arguments/properties and self-reconstruction in the Item's bodies block the
whole candidate. Nothing defines or initializes a source mod class.

## Exact APIs and implemented operations

Legacy API: Forge 36.2.42 / official Minecraft 1.16.5
`Item(Properties)` and `Properties.stacksTo(int)`.
Target API: Forge 47.4.23 / Minecraft 1.20.1 equivalents, using existing exact
production mappings (`func_200917_a` → `m_41487_`, not guessed spelling).
Transformation: remap approved platform symbols/descriptors; retain owned
constructor calls and all executable bodies. No invented runtime shim.

Both reviewed source implementations assign `maxStackSize` from properties;
both `stacksTo` methods reject a nonzero damage setting, assign the supplied
limit and return the same builder. This proof admits only fresh default
properties plus this one call, not other builder combinations or general Item
equivalence. Target-specific initialization/feature/client hooks still exist;
static inspection does not prove their native behavior.

Local development source archives, inspected again for this slice:

| Pinned archive | SHA-256 |
| --- | --- |
| `forge-1.16.5-36.2.42_mapped_official_1.16.5-sources.jar` | `bbe2d148f96694271c43fc2ba584463d75d9ffc490abd0469ae66b831293d376` |
| `forge-1.20.1-47.4.23_mapped_official_1.20.1-sources.jar` | `4bd6656a7cbe3809da68f3b709ca0f2fa186575f58538668c58ce30cb38c4932` |

Constructor-state rules use the real [JVMS 17 verification rules](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html#jvms-4.10.1.9)
and [Java 8 explicit constructor invocation rules](https://docs.oracle.com/javase/specs/jls/se8/html/jls-8.html#jls-8.8.7.1),
with a deliberately narrower supported language. These archives are local
evidence, not redistributed contents or an implicit runtime cache dependency.
This candidate still requires explicit full local mappings/API data.

`ConstructorArtifactAdapter` implements two additive OFFLINE operations:
`logic.constructor-manifest.v1` and `logic.constructor-bindings.v1`. It emits
identity `owned-constructors-candidate-v1`, revision 1 and
`STATIC-CANDIDATE-ONLY`; existing metadata/pack operations perform the other
necessary rewrites. Each rewrite is logged with VERIFIED provenance and a real
catalog operation. No executable runtime adapter behavior or ABI was changed.
The runtime remains `legacybridge_runtime` / `0.1.1-SNAPSHOT`; existing adapter
entries remain unchanged. This is metadata generation, not an empty shim.

## Fixture and evidence boundary

The [authored Forge fixture](../fixtures/owned-constructors-1.16.5/README.md)
contains 3 classes and 11 methods, integer constructor delegation, private final
and mutable fields, an owned helper, a native override/super call, method-handle
registration, one smelting recipe, a tag, model and two translations.
Expected native observations are stack limit 16, constructor trace 123 and the
documented count-dependent foil behavior. These observations were **not run**.

Static checks serialize/reopen the actual candidate, prove resource byte
preservation, preserve the original hash, compare service/direct results, reject
the fixture through every existing profile and exercise cancellation. The ZIP
scratch is deleted; requested output remains absent. Client/server/discovery,
UI, behavior, multiplayer and actual classloader linkage remain NOT_RUN.

The opt-in real-inventory test worker uses a bounded 1 GiB heap. Initial 512 MiB
runs hit the existing MEMORY_PRESSURE guard; neither the engine reserve nor its
runtime limits were reduced. No automatic GC or retry was added. This is not a
claim that full data fits any player's available shared heap.

## Still unsupported / next implementation

Reference/wide/computed constructor arguments, multiple public entries,
branching constructor graphs, arbitrary properties or registry forms, aliases,
pre-initialization exception/control-flow patterns and general Java constructors
are not admitted. Data/lifecycle/network/capability contracts are not expanded.
The small authored fixture does not unlock a measured number of real mods.

Historical next slice, now implemented privately:
[OWNED_HIERARCHY_DISPATCH](OWNED_HIERARCHY_DISPATCH.md), a separate finite proof for
an owned intermediate Item superclass and concrete registered leaf, with exact
inherited constructor/override/dispatch/access checks. Preserve all six public
grammars; add an alternate original fixture and negatives. Do not repeat the
constructor harness or promote this candidate. Static prerequisites permit
implementation, not acceptance of COMPOSE-01 or LINKAGE-GENERAL.
