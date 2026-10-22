# Experimental network-snapshot-v1

Source: Minecraft 1.16.5 / Forge 36.2.42. Target: Minecraft 1.20.1 / Forge
47.4.23, Java 17. This opt-in profile does not change simple-content-v1,
logic-preserving-v1, lifecycle-preserving-v1, food-item-v1 or datapack-recipes-v1.

## Exact scope, not general networking support

The first authored fixture is `legacybridge_network` 1.0.0 with two Java 8
classes and no items, world access or C2S behavior. Its original executable
bodies are retained. Approval currently requires its exact reviewed executable
method recipes: the original class/member identities, declaration/access/field
layout, instructions and operands, constants, exception regions, branch targets,
annotations and method handles. Digests are a compact encoding of those reviewed
recipes, not a JAR allowlist or automatic semantic proof. Debug lines and local
names may differ; debug preservation and ASM/linkage are separately checked.
Another compiler output or behavior requires another review. There is no generic
shape inference and no real corpus mod is newly claimed compatible.

The recipe was reviewed against the original source and the reobfuscated javap
instruction listing on 2026-09-06. Never regenerate approvals just to make a
negative fixture pass. Neither fixture classes nor game/Forge sources are
embedded as compatibility data. Static conversion never defines input classes.

## Preserved wire and processing contract

| Property | Exact contract |
| --- | --- |
| Channel | `legacybridge_network:snapshot` |
| Protocol | Literal `1`, exact equality in both predicates; not optional |
| Registration | One message, ID 0, six-argument registerMessage, explicit PLAY_TO_CLIENT |
| Payload | Big-endian int sequence followed by int value; exactly 8 bytes after the discriminator |
| Values | Sequence positive; value 0..1000; first sequence 1, then exact successor |
| Recipient | One explicitly selected native server player via PLAYER.with(Supplier) |
| Decode | No world or side access; immutable DTO; wrong width yields an explicit invalid sentinel |
| Handle | Resolve Context once; enqueue immutable DTO + Context; mark transport handled |
| Apply | Native reception-side game thread, connection-open check inside the task, connection identity and sequence guards |
| Errors | Malformed values/width, duplicates/gaps, stale closed context and competing live context do not apply; counted and logged |
| Unknown ID | Native Forge ERROR rejection before the decoder; not a claimed disconnect |
| Wrong direction | Native Forge direction validation before decoding; runtime behavior must be tested independently |

The decoder never retains its borrowed buffer. Content-error diagnostics are
bounded to 20 reason lines plus one rate-limit notice per bound session, with a
saturating counter retained afterward. Valid and invalid process counters do not
reset on reconnect. A disconnected snapshot is hidden (`sequence()==0`); the next
connection is adopted only with sequence 1 after the old channel closes. The old
connection reference remains until replacement. There is no explicit epoch or
comparison to Minecraft's current connection, and no flood/backpressure claim:
the native work queue is not application-capacity-limited by this fixture.

No packet changes the server, reads a world/chunk or mutates a player. Therefore
permissions, distance, chunk loading and server-state preconditions are **not
implemented C2S validations**: any such behavior remains UNSUPPORTED. This is a
client-side snapshot, not an authenticated action or anti-replay framework.
Sequence exhaustion refuses further messages; it never wraps or resynchronizes
silently. Logging a rejected packet is not an acknowledgement or disconnection.

## Mapping and publication

Real official/SRG mappings compose Minecraft owner and member names. Exact
declarations, access, invocation kinds, reviewed native contracts and the complete
original method recipes are additional independent gates; HIGH is not promoted
automatically. See [pinned API audit](NETWORK_API_BASELINES.md) and
[explicit mapping inputs](NETWORK_MAPPING_DATA.md).

`SnapshotContractAdapter` is an implemented, game-free OFFLINE descriptor
operation in the shared runtime ABI, not a placeholder runtime networking shim.
Every changed class/member/descriptor and metadata resource is bound to a real
catalog operation. Source and target offline linkage and ASM checks precede
publication; cancellation before publication cannot leave a converted artifact.
Before ASM, the network-only `NetworkClassLimits` cursor bounds each Java 8
class to 64 KiB, constant-pool/member/attribute counts and Code containers, and
max_stack/max_locals to 64 slots. Annotation values have a combined 1,024-node
budget and depth 16; type/parameter/default annotations remain outside this
closed recipe. `RawRecipeAdmission` then checks the reviewed
instructions before scanner frame expansion or Analyzer allocation. These are
targeted allocation safeguards, not a whole-process heap sandbox or a general
class-file verifier; the later ASM and linkage gates are still mandatory.
For example, rejecting every malformed duplicate singleton class attribute is
not proved by these allocation tests; multiple legal debug tables must not be
confused with that separate future parser-hardening task.
CompatibilityReport schema 2 and TransformationLog schema 1 remain intact.
The runtime identity and version remain `legacybridge_runtime` / 0.1.1-SNAPSHOT.

```text
legacy-converter convert original.jar --conversion-profile network-snapshot-v1 --mapping-bundle local-data --output converted.jar --report conversion.json
```

The GUI/default installer still supports its existing simple-content profile.
This experimental profile is CLI/service-only; test installation is manual in a
stopped disposable instance, never hot registration or a changed live mod set.
Both communicating endpoints use the target game and the same converted mod
set. There is no 1.16.5-client / 1.20.1-server connection promise.

The profile-independent `analyze`/`mappings` inventory remains conservative and
does not authorize these networking contracts: it may report NOT_IMPLEMENTED
or MANUAL before an explicitly selected profile plan. Only that plan's exact
reviewed recipes and independent linkage checks authorize conversion. No generic
mapping confidence is raised just to match the profile's admission result.

## Validation gates

Static tests and a successful build do not prove multiplayer. The native test
stage must exercise two real target clients with distinct identities and isolated
game directories, the dedicated server and the final public LegacyBridge JAR.
Positive traffic must use the registered channel, not direct handler invocation.
Capture per-client values, receiver thread identity, isolation, disconnect/reset,
reconnect, malformed bodies/IDs, duplicate/out-of-order messages and wrong
direction. No FakePlayer or mock transport substitutes for those gates.

Until fresh evidence is recorded, native multiplayer, source/target runtime
comparison, protocol-mismatch peers, adversarial flood/queue exhaustion and
disconnect while work is pending remain NOT_RUN. A successful ordinary reconnect
does not prove the latter race. See the separate validation record for actual
executed checks, hashes, failures and remaining limitations.

Unsupported: arbitrary SimpleChannel registrations, five-argument registration,
message builders, dynamic/duplicate IDs, optional protocols, login messages,
replies, C2S actions, broadcasts/tracking, variable strings/NBT/collections,
custom buffer factories, world/entity/capability synchronization, generalized
callbacks and unknown dependencies. The [corpus audit](NETWORK_CORPUS_AUDIT.md)
explains why one similar synchronization use does not unlock AppleSkin or Clumps.
