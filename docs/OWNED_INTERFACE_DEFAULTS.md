# LB-08: finite owned interface/default dispatch

Private headless implementation of `LINKAGE-GENERAL / OWNED_INTERFACE_DEFAULTS`.
This is **not a new public profile**. The six existing grammars, Convert/Apply,
runtime ABI and prior private candidates retain their admission boundaries.
`COMPOSE-01` and `LINKAGE-GENERAL` remain PARTIAL. [Versioned evidence](evidence/owned-interface-2026-09-19.json).

## Implemented language and proof

`LogicPreservingConverter.planOwnedInterfaceDefaults` and the same-named bounded
`LegacyEngineService` operation return an immutable `CompositionCandidate`
report/hash. They serialize and reopen a real private ZIP, validate it and remove
the scratch; they never return/publish/install that ZIP or an activation receipt.
No new CLI command, UI control, exporter or native harness was added.

`OwnedInterfaceDefaults` proves exactly one public flat owned interface, no
parents or fields, containing one public default `(I)I` method and one public
abstract `()I` hook. Names are derived from declarations, not fixture class names.
Exactly one public final owned helper directly extends Object and implements
only that interface. Both slots must have public concrete declared overrides.
The helper has one public `()V` constructor, no static initializer or descendant.
The Item registration/property/resource envelope is the existing reviewed one;
constructor delegation and the earlier two-level Item hierarchy are not combined.

For all calls whose symbolic owner is this interface, the proof records caller,
executable instruction index, opcode, exact symbolic member and selection
conditional on a **non-null instance of that helper**. `INVOKEINTERFACE` selects
the helper implementation; the helper's direct `Interface.super` call selects
the default declaration. A default-body call back to the abstract hook is also
required. All three witnesses must exist. Debug nodes do not alter site indices.
This is conditional finite selection, not whole-program receiver/points-to analysis.
Other calls remain subject to the existing exact mapping and linkage checks.

`OfflineLinkageVerifier.verifyOwnedInterfaceDefaults` re-derives the proof from
each supplied byte map after ownership checks. Callers cannot authorize defaults
by passing a saved proof. It allows only the proved declaration/direct interface
call; the `invokespecial` receiver must additionally be proven **this**, not merely
an object with a compatible type. Abstract requirements, public access, staticness,
opcodes, constant-pool owner kinds, constructors, instruction frames and every
external reference are independently checked. Standard verifier entrypoints
retain `DEFAULT_INTERFACE_DISPATCH_UNSUPPORTED`. General interface resolution in
`LinkageHierarchy` is unchanged; inherited defaults and diamonds remain rejected.

Source proof remapped through exact mapping rules must equal target proof; the
reopened proof must equal the prepared proof. Existing body preservation checks
retain owned executable bodies, fields, initialization, instruction/control-flow
order and debug data. ASM plus offline linkage/access validate source, transformed
and reopened bytes. No original class is loaded, linked or initialized.

The positive fixture includes a captured `IntUnaryOperator` with javac's **owned
static** implementation and exact interface capture type, plus the existing Item
constructor Supplier. These use the existing reviewed metafactory contract.
Instance/interface method handles are not newly enabled: handles targeting the
proved interface/helper are rejected, and general handles/boxing/altMetafactory/
condy still fail the existing gates. Capturing an interface reference is not
permission for arbitrary implementation handles.

Bounds: 32 classes, 128 methods/class, 256 aggregate methods, 4,096 instruction/
debug nodes per method and 32,768 aggregate, plus shared archive/worker/deadline
limits. Cancellation does not publish output. These are not hard heap isolation.

## Real APIs and operations

Legacy API: owned Java 8 classfiles plus reviewed Forge 36.2.42/Minecraft 1.16.5
Item, Properties, ItemStack, registration and JDK functional-interface contracts.
Target API: the corresponding pinned Forge 47.4.23/Minecraft 1.20.1 contracts,
target Java 17. No additional external API mapping or runtime emulation was added.
Transformation: preserve default/override/helper bodies; reuse exact production
symbol/descriptor mappings and independently verify selection/access on both sides.
Behavior differences: metadata records the target/candidate identity; native Item
and classloader behavior still requires separate execution comparison.
Known limitations: the finite shape above only, not arbitrary interface semantics.

The actual local source archives were inspected again for the native Item foil
boundary; both bodies delegate to `ItemStack.isEnchanted()`. This does not prove
all native Item/enchantment behavior equivalent. Archive hashes:

| Version | Source archive SHA-256 |
| --- | --- |
| 1.16.5 / 36.2.42 | `bbe2d148f96694271c43fc2ba584463d75d9ffc490abd0469ae66b831293d376` |
| 1.20.1 / 47.4.23 | `4bd6656a7cbe3809da68f3b709ca0f2fa186575f58538668c58ce30cb38c4932` |

These are local development sources, not embedded game/Forge code or redistributable
generic mappings. The private operation still uses explicit full mapping/API data.
Production namespace recipes and confidence separation remain in
[LOGIC_API_BASELINES](LOGIC_API_BASELINES.md); no HIGH-to-VERIFIED promotion.
The finite checks follow [Java 8 interface resolution](https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-5.html#jvms-5.4.3.4)
and [Java 17 method selection](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.4.6),
with a deliberately narrower accepted language. The genuine javac/reobf fixture
also supplies actual interface/special/captured-lambda bytecode, not invented opcodes.

`InterfaceArtifactAdapter` implements `logic.interface-manifest.v1` and
`logic.interface-bindings.v1`, both OFFLINE. It emits
`owned-interface-candidate-v1`, revision 1 and `STATIC-CANDIDATE-ONLY` plus exact
implemented operation bindings. Existing metadata and pack rewrites are reused.
Every transformation retains schema-1 logging and a VERIFIED rule/real adapter.
No dispatch shim, empty runtime adapter or fictitious return was added.
`legacybridge_runtime` / `0.1.1-SNAPSHOT` and prior catalog entries are unchanged.

## Fixture, evidence and exclusions

[Authored original fixture](../fixtures/owned-interface-1.16.5/README.md): four
classes/12 methods, one item and helper counter, interface/default/override/super/
abstract-hook path, exact captured lambda, recipe/tag/model/en_us/pt_br. Candidate
resources retain their original bytes; only four explicit metadata/pack/binding
operations rewrite or generate entries. Ordinary profiles and all older private candidate routes
reject this input. Original and candidate hashes and counts are in the ledger.

New checks include independent private-vs-standard verifier admission, malformed
bodies even with valid shape, incompatible default/override/constructor/access/
opcode/owner flags, missing ancestry, extra implementations, interface fields,
unknown external API, handles/capture mismatch, a same-type non-this super receiver,
deterministic source/target/readback proof, service parity and cancellation.
Inert structural tests are distinguished from the genuine Forge input tests.

No Minecraft/client/server/UI or original mod class was executed. Discovery,
activation, behavior, multiplayer, JPMS/classloader wiring and final-artifact
cacheless native runtime are **NOT_RUN**. The source behavior oracle is specified
for future isolated comparison, not claimed as observed gameplay. Counts are not
numbers or percentages of compatible real mods.

Still unsupported: inherited defaults without concrete helper override, interface
parents/diamonds/multiple defaults or hooks, multiple implementations, static
interface state, instance handles, arbitrary ancestry, new content/lifecycle/
network/capability/resource APIs and automatic union of private candidates.

Next headless construction: **OWNED_RECEIVER_HANDLES**, one finite bound
`this::method`/`IntUnaryOperator` path on an owned final helper, with exact receiver,
access/opcode/capture/return and dispatch proof. Preserve null/evaluation timing;
do not automatically admit general handles, boxing or alternate bootstrap methods.
Use a real alternate fixture, negative cases and private source/target/readback
checks; keep six profiles/public installation closed and native comparison pending.
