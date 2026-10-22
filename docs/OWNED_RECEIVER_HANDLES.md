# LB-08: finite this-bound receiver handles

Private headless implementation of LINKAGE-GENERAL / OWNED_RECEIVER_HANDLES.
The six existing profiles and ordinary Convert/Apply are unchanged. No candidate
exporter, UI button, installation admission or runtime adapter was enabled.
COMPOSE-01/LINKAGE-GENERAL remain PARTIAL; native comparison remains NOT_RUN.
[Versioned evidence](evidence/owned-receiver-2026-09-19.json).

## Implemented path

LogicPreservingConverter.planOwnedReceiverHandles and LegacyEngineService's
same-named typed bounded-worker operation return only CompositionCandidate
report/hash. Shared scanning, exact mapping, body preservation, resource checks,
private serialization/readback, cancellation and scratch cleanup remain in use.
There is no CLI.main invocation, output scraping, new executor or System.exit.

OwnedReceiverHandles derives one bound instance implementation site from owned
class declarations. Its helper must be public/final, directly extend Object,
have no interfaces, descendants or static initializer, and declare one public
()V constructor. The factory is public/non-static ()IntUnaryOperator; the exact
declared public implementation is (I)I, referenced by H_INVOKEVIRTUAL, not an
interface or inherited/special target. Names are discovered, not fixture names.
SAM name/type, instantiated type and single helper capture must match exactly.
No boxing, widening, unbound handle, extra capture or alternate metafactory.
Previously reviewed static lambdas/Item constructor Suppliers retain their gates.

The independent OfflineLinkageVerifier.verifyOwnedReceiverHandles rereads and
rederives the proof from source, target and reopened bytes. It resolves declarations,
access/staticness/owner kinds, and uses initialization/dataflow frames to prove the
captured stack value is initialized **this**, not just a same-type reference.
Local this aliases pass; null, another new instance, a field, overwritten local 0,
and a control-flow merge with null fail. Constructor-state checks remain active.
The factory's evaluation/capture/invocation instruction order is not rewritten.
The helper is final, so virtual selection cannot dispatch to an unknown subclass.

Source proof remapped through exact rules must equal target proof; reopened proof
must equal the prepared proof. ASM and offline access/linkage additionally inspect
all bodies/references, including unresolved external references. A valid structural
proof alone cannot authorize invalid bytecode or unknown dependencies.
Standard LOGIC/LIFECYCLE/NETWORK verifiers and older private candidates retain
their prior refusal of instance handles. These separate contracts are not
implicitly combined with constructor delegation, Item hierarchies or defaults.

Bounds: 32 classes, 128 methods/class, 256 aggregate methods, 4096 instruction/debug
nodes per method and 32768 aggregate, plus the existing archive/worker/deadline
limits. Cancellation cannot publish a partial result. This is not hard heap isolation.

## Real API evidence and implemented operations

Legacy API: Java 8 classfiles and the already reviewed exact Forge 36.2.42 /
Minecraft 1.16.5 Item/Properties/ItemStack/registration contracts.
Target API: the pinned Forge 47.4.23 / Minecraft 1.20.1 contracts, Java 17.
Transformation: retain bodies, reuse existing verified production mappings and
validate the exact this-bound metafactory path independently on both sides.
Behavior differences: build provenance changes; callback allocation/identity and
native Item behavior are not claimed equivalent by these static checks.
Known limitations: one finite integer receiver path, not arbitrary method handles.

Both [Java 8](https://docs.oracle.com/javase/8/docs/api/java/lang/invoke/LambdaMetafactory.html)
and [Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/invoke/LambdaMetafactory.html)
metafactory contracts require a non-null captured instance receiver. This gate
accepts only proven this, preserving capture timing without adding null checks.
The genuine reobfuscated fixture was inspected with javap; no extra JDK/native
API recipe or confidence promotion was needed. A reference like variable::method
with a different receiver/null-check contract is still unsupported.

The exact local source archives were rechecked at the existing Item.isFoil
boundary: both delegate to ItemStack.isEnchanted(). This is not proof of all native
Item behavior. Sources SHA-256: 1.16.5/36.2.42
bbe2d148f96694271c43fc2ba584463d75d9ffc490abd0469ae66b831293d376;
1.20.1/47.4.23 4bd6656a7cbe3809da68f3b709ca0f2fa186575f58538668c58ce30cb38c4932.
Full mappings/APIs remain explicit local development inputs, not redistributed
game/Forge code or operational implicit Gradle-cache discovery.

ReceiverArtifactAdapter implements two real OFFLINE operations:
logic.receiver-manifest.v1 and logic.receiver-bindings.v1. They generate
owned-receiver-candidate-v1 / revision 1 / STATIC-CANDIDATE-ONLY provenance and
bindings. All changes have schema-1 TransformationLog entries with VERIFIED
rules/implemented operations. No fake executable shim or return value was added.
Runtime identity/ABI remains legacybridge_runtime / 0.1.1-SNAPSHOT.

## Evidence and next development

The [authored original fixture](../fixtures/owned-receiver-1.16.5/README.md) has
three classes/nine methods, one registered item, per-helper counter, bound
IntUnaryOperator and recipe/tag/model/translations. Assets/data retain their exact
bytes. Tests cover real source/target/readback, service parity, original integrity,
structured blocking, invalid receiver/access/opcode/contracts and cancellation.
No Minecraft/client/server/UI or original mod class was executed. Discovery,
activation, behavior, multiplayer and final-artifact native/cacheless runtime are
NOT_RUN. Tests/symbols do not count as compatible mods.

The next implementation cursor moves to **LB-09 / LIFE-EVENTS /
EVENT_PRIORITY_CANCELLATION**: audit corpus/source-backed event patterns and build
one owned cancelable-event fixture with two explicit priorities/receiveCanceled,
correct bus and enqueued setup, plus wrong-side/early-registry negatives. Reuse the
static foundations without accepting COMPOSE-01 or generalizing existing profiles.
Do not repeat Java-handle/UI/backend harnesses, add broad event allowlists or open
Minecraft. Native comparison and public promotion remain separate required gates.
