# Networking corpus and admission audit

Review date: **2026-09-06**. Source baseline: Minecraft **1.16.5 / Forge 36.2.42**;
target: Minecraft **1.20.1 / Forge 47.4.23 / Java 17**. This is a read-only audit
and a proposed narrow contract, not an enabled networking backend or a runtime
validation receipt. No original mod was loaded or executed, no new JAR was
downloaded, and no build or game was run by this auditor.

The smallest defensible next slice is an **explicitly S2C-only, fixed-size,
connection-scoped snapshot into owned client observation state**. The current
real corpus motivates investigating synchronization, but **none of its three
releases already satisfies that proposed closure**. An S2C send call is not proof
that the registered receiver rejects C2S traffic.

## Exact corpus evidence

The existing authorized binaries were hashed before and after read-only
inspection; all hashes remained equal. Exact publisher/source/license associations
are retained in [rights-candidates.json](../corpus/intake/rights-candidates.json),
with complete MIT notices for Clumps/Controlling and Unlicense for AppleSkin.
Those rights permit the reviewed local analysis; no third-party binary
redistribution was selected. Original JARs remain ignored local build inputs.

| Exact release | Bytes | SHA-256 | Observed network concern |
| --- | ---: | --- | --- |
| [Clumps 6.0.0.28](https://modrinth.com/mod/clumps/version/UutzgwbZ) | 18,397 | `f69abc1d44277b495004fd6d67d0f95f2574581bb26e55543f2dcd2153cd92d0` | Forge entity spawn packet with variable additional spawn data |
| [Controlling 7.0.0.29](https://modrinth.com/mod/controlling/version/Z7UMwzSN) | 51,888 | `b2eb85d83bcd953d37f0d629013bd1f386b1927e2a46c70df155060c8d072cdc` | Display/peer-compatibility extension; no application packet codec found |
| [AppleSkin 2.5.1+mc1.16.4](https://modrinth.com/mod/appleskin/version/633NTwhH) | 47,721 | `6dd1d2ef1daa6871b79f5ca2ac1e757ec731f94958f12397b43670b188091b62` | One SimpleChannel with two float synchronization messages |

The exact immutable GitHub trees were fetched again, without truncation. All
6/15/18 Java source files, respectively, were searched as bounded text for
channel/codec/send/direction/handler/lifecycle sites; relevant implementations
were reread in context. JDK `javap` independently inspected the existing AppleSkin
registration, handler and sided-player bytecode and the Clumps spawn codec;
disassembly does not define or execute those classes. No source/binary reproducible
build was performed.

The source commits are Clumps `34c57ca0bd068655a63d99eabd215219432c32d4`, Controlling
`1975807fd663f62d5631f94b9dee19372a5a2d08`, and AppleSkin
`5bedbcc501848f3a22a650f1f661e57d6973af83`. Source build evidence is
1.16.5/36.1.16, 1.16.5/36.0.0, and **1.16.4/35.1.4**, respectively. AppleSkin's
publisher also advertises 1.16.5, but that does not establish a Forge-36 binary
build or remove the separate source-profile decision.

| Pattern counted by exact release | Observed releases |
| --- | ---: |
| Application SimpleChannel/registered message codecs | 1 of 3 |
| Fixed float-valued application messages | 1 of 3 |
| Explicit PLAY_TO_CLIENT send calls | 1 of 3 |
| Explicit direction restriction at those application registrations | 0 of 3 |
| Entity additional-spawn serialization | 1 of 3 |
| Application sequence number/session-generation guard in the reviewed codecs | 0 of 3 |
| Complete proposed bounded S2C observation contract | 0 of 3 |

These are occurrence counts, not compatibility percentages. Two AppleSkin message
classes are not two supported mods. The frozen corpus conversion/runtime statuses
remain unchanged; this audit did not attempt a new conversion.

### AppleSkin: outgoing S2C does not establish a receive-side invariant

[SyncHandler.java](https://github.com/squeek502/AppleSkin/blob/5bedbcc501848f3a22a650f1f661e57d6973af83/java/squeek/appleskin/network/SyncHandler.java)
creates `appleskin:sync`, protocol string `1`, with both accepted-version
predicates returning true. Registrations use discriminators 1 and 2 and the
five-argument `registerMessage`, with no explicit receive direction. Sends use
PLAY_TO_CLIENT. Server-player saturation/exhaustion caches are keyed by UUID,
updated on living updates, and cleared for a player on login; this file does not
contain corresponding logout eviction. Exhaustion changes use a 0.01 threshold.

[MessageSaturationSync](https://github.com/squeek502/AppleSkin/blob/5bedbcc501848f3a22a650f1f661e57d6973af83/java/squeek/appleskin/network/MessageSaturationSync.java)
and [MessageExhaustionSync](https://github.com/squeek502/AppleSkin/blob/5bedbcc501848f3a22a650f1f661e57d6973af83/java/squeek/appleskin/network/MessageExhaustionSync.java)
each read/write one float and enqueue a captured message/context supplier. Neither
codec checks exact remaining bytes, finite/ranged values or a sequence number.
Neither queued body proves that the connection/player is still the one active
when the message was accepted. Saturation writes FoodStats; exhaustion calls a
reflection-backed helper, not an owned display-only snapshot.

The critical additional dependency is
[NetworkHelper.java:10-12](https://github.com/squeek502/AppleSkin/blob/5bedbcc501848f3a22a650f1f661e57d6973af83/java/squeek/appleskin/network/NetworkHelper.java#L10):
PLAY_TO_SERVER selects `ctx.getSender()`, while other directions select the
Minecraft client player. Combined with the unrestricted registration and the
handler writes, this is an unreviewed server-mutation path. This audit did not
send adversarial traffic or establish an operational exploit; it establishes
that the proposed S2C-only admission proof is absent. A package rename or an
outgoing-direction search must not admit these handlers.

[HungerHelper.java](https://github.com/squeek502/AppleSkin/blob/5bedbcc501848f3a22a650f1f661e57d6973af83/java/squeek/appleskin/helpers/HungerHelper.java)
reflectively accesses exhaustion and writes it on the selected player's FoodStats.
Replacing that with a neutral HUD field changes the observable contract. Tightening
the existing protocol, adding wire fields, suppressing the server branch or
changing null/disconnect behavior would also be explicit migrations, not pure
renames. The whole release additionally needs its adjacent baseline, HUD/tooltip,
config/file I/O and event behavior reviewed and implemented.

### Clumps: entity spawn state is a different protocol/lifetime

[EntityXPOrbBig.java:208-245](https://github.com/jaredlll08/Clumps/blob/34c57ca0bd068655a63d99eabd215219432c32d4/src/main/java/com/blamejared/clumps/entities/EntityXPOrbBig.java#L208)
uses `NetworkHooks.getEntitySpawningPacket` and additional spawn data: XP int,
an old compatibility int, entry-count int, then each map entry as int plus long.
The reader allocates a map and loops on the received count without an explicit
application bound. This is not a one-float SimpleChannel message. The denominator
map supports later merge/pickup/mending and NBT behavior, and its lifecycle is an
entity's spawn/tracking/destruction lifecycle. A client snapshot adapter that
keeps only total XP would not preserve the release. Keep entity networking and
its renderer/persistence/semantic closure out of the first snapshot profile.

### Controlling: compatibility metadata is not a message codec

The [entrypoint](https://github.com/jaredlll08/Controlling/blob/1975807fd663f62d5631f94b9dee19372a5a2d08/src/main/java/com/blamejared/controlling/Controlling.java#L20)
registers DISPLAYTEST with IGNORESERVERONLY and a permissive predicate. That
describes peer/display compatibility, not a snapshot encoder or receiver. No
application channel/codec was found in its exact source tree. Networking support
does not remove its custom screen, keybinding or access-transformer requirements.

Source text SHA-256 values from this pass:

| Pinned source file | SHA-256 |
| --- | --- |
| AppleSkin `network/SyncHandler.java` | `dcd0e4817dceec9a7698c4f385f26d5b0c26e5130239741e32cddaf96c740dd9` |
| AppleSkin `network/MessageSaturationSync.java` | `e85c405f7aa090de383db7de164b1ea31f8e31b8e7fdea61c609d0009b07369e` |
| AppleSkin `network/MessageExhaustionSync.java` | `49cca4ea3c00b818594ccb75ef24a1718e2389a21e7e0eafdb4b16689b8892bc` |
| AppleSkin `network/NetworkHelper.java` | `709477934d75fc648f74557476e3df6ac70478b3985c60686c1923ca3d91e198` |
| AppleSkin `helpers/HungerHelper.java` | `b740d541262efdd91b94c86efb4ada386ba6c8db73435a96c80364d01b505122` |
| Clumps `entities/EntityXPOrbBig.java` | `6c8f29840de2120829b924bc03130f56d7119e9f00f3f4f77eb097592e5d979f` |
| Controlling `Controlling.java` | `31e07ce8932808ced8d3b0e37b44e8a22f757bce9dc0048a37e646d774242a34` |

## Relevant native constraints

The local pinned merged source archives were rehashed and inspected as text:
source SHA-256 `bbe2d148f96694271c43fc2ba584463d75d9ffc490abd0469ae66b831293d376`,
target `4bd6656a7cbe3809da68f3b709ca0f2fa186575f58538668c58ce30cb38c4932`.
Their filenames/locations are already identified in
[LOGIC_API_BASELINES.md](LOGIC_API_BASELINES.md). No old game/Forge implementation
or these local sources may be embedded to simulate portability.

Source owners use `net.minecraftforge.fml.network`, target owners
`net.minecraftforge.network`. The review of `SimpleChannel`, `IndexedMessageCodec`
and `NetworkEvent.Context` confirms constraints important to admission:

- The five-argument registration delegates to an empty direction Optional in
  both versions. Require the reviewed explicit-direction form, not a name match.
- The indexed codec stores discriminator keys after masking to eight bits.
  Finite admission must reject negative/out-of-range values, duplicates and
  aliases such as 1/257 before runtime. Do not assume native registration catches
  them. The frame discriminator is an unsigned byte, not a guessed VarInt.
- `consume` checks the registered direction before calling the decoder. An
  unknown discriminator logs and returns in the inspected codec; it does not
  itself prove a disconnect. Trailing-byte exhaustion is not enforced by the
  generic decoder wrapper. Test actual outcomes rather than inventing them.
- Context enqueueWork runs inline when already on the receiving game thread,
  otherwise schedules there. It is not an always-later executor. Reentrancy,
  reservations and session guards must be correct in both paths.
- Target builder `consumerMainThread` adds scheduling/handled behavior. It is
  not automatically equivalent to a legacy consumer that already enqueues work.
  Preserve only an independently reviewed callback route.
- `getNetworkManager()` exposes the context's connection identity; `getSender()`
  is not a universal receiving-player accessor. A client-only snapshot consumer
  must not obtain a server sender or mutate game-owned player/world state.

These source facts do not validate Netty ownership, exception propagation,
connection teardown, actual client-class loading or multiplayer delivery.
Those need their own pinned API audit and execution evidence.

## Proposed smallest vertical: owned observation, not gameplay authority

This is a proposed authored fixture/proof contract. It must not be silently
retrofit into existing third-party protocols merely to make them pass.

1. **Closed registration.** One owned final channel with one literal local
   ResourceLocation, one bounded protocol literal and exact equality predicates
   on both peers. Require the channel on both endpoints; no absent-channel,
   vanilla-client or wildcard negotiation in the first slice. One literal unique
   discriminator in 0..255, registered exactly once for PLAY_TO_CLIENT during
   the proven lifecycle. Reject re-registration, dynamic IDs, login packets,
   reply handlers and C2S send/receive paths.
2. **Fixed immutable data.** One final owned DTO with primitive final fields and
   a pure constructor and explicit value validation before application. The
   selected authored fixture uses positive int sequence plus int value 0..1000:
   exactly 8 application bytes after the discriminator. This is not an existing
   corpus wire format. Encoder
   and decoder must prove identical field order/width. Check exact readable bytes
   before reading/allocating, reject truncation/trailing bytes and invalid values.
   No arrays, strings, NBT, compression, collections, buffers stored in fields,
   reference counting or dynamic payload-sized allocation. A future float needs
   an explicit finite/range and NaN/infinity policy.
3. **Provenance and effects.** The receive callback gets its context from the
   native supplied argument, validates the direction and schedules only the
   linked owned apply body. Never capture a live PacketBuffer, mutable DTO,
   arbitrary supplier, world/player object or opaque callback. The apply body
   updates only bounded owned observation state. No FoodStats, inventory,
   capability, entity, world, command or server-authoritative mutation is allowed.
   The sender may read audited server state and maintain its own bounded protocol
   counters; that is not permission for a client-originating mutation route.
4. **Bounded work.** Payload size alone does not bound a stream of valid packets.
   Specify a finite pending-work limit or reviewed one-slot coalescing mailbox,
   with thread-safe reservation before enqueue and guaranteed release/clear on
   every completion/refusal/disconnect. Its semantics must exist in the source
   fixture or a separately implemented documented adapter. Never infer queue
   bounds from expected player behavior or a game's nominal tick rate.
5. **Observable unknown state.** Before the first valid snapshot and after
   disconnect, expose absence/not-received, not a fabricated zero. No persistence,
   tokens or launcher arguments are needed. Keep each client process/connection's
   data isolated and clear all references and pending state at session teardown.

If the original lacks these required semantics, remain MANUAL/UNSUPPORTED unless
an explicit, implemented migration preserves the selected contract and has its
own evidence. Tightening handshake policy or adding sequence/epoch bytes is a
protocol change, not a package-only rewrite. No hash-based exemption is allowed.

### Session, duplicates and ordering

Treat a connection/session as a capability, not a player name or UUID. Bind a
snapshot to the actual native connection identity plus a local generation token.
Capture that token at acceptance and check **again inside the game-thread apply
operation**. Increment/invalidate it on logout and replacement login; clear
presence, last sequence and pending work. A queued message from an old connection
must never recreate that session or write into a newly connected one, including
when the same player UUID reconnects immediately.

Within one active session, specify sequence semantics exactly. The selected
fixture requires initial 1 and thereafter exactly previous+1; gaps, duplicates
and older snapshots leave both sequence and value unchanged. A different
strictly-increasing/gaps-allowed protocol is not automatically equivalent.
Diagnose refusals with bounded counters/log sampling. Sequence and value commit
together only after every check. Do not calculate `incoming-last` to compare
signed integers: subtraction can overflow. Either disallow wrap and
renew the session explicitly, or implement a different fully specified protocol;
do not guess serial-number arithmetic.

Client/server TCP normally supplies an ordered stream; adversarial duplicate and
out-of-order tests verify the application guard, not a claim that Forge normally
reorders packets. Session replacement and queued work are a distinct stale-message
risk. A lower sequence in a genuinely new session must be admissible after reset;
an old queued high sequence must not suppress it. Forbid a network callback from
implicitly declaring a new session before the validated join lifecycle.

Coalescing, if selected, must account for inline enqueueWork and concurrent
receive/disconnect: an old task's cleanup cannot clear a new generation's pending
slot, and a scheduling exception cannot leak a permanent reservation. One task
per bounded slot is preferable to an unbounded future list, but that specific
algorithm needs its own tests. The application-level bound does not replace
native transport allocation/packet-size limits before the decoder is invoked.

## Structural proof strategy, independent of artifact hashes

The existing `VerifiedLogicMappings`, profile shape validators and
`OfflineLinkageVerifier` were reread. They supply useful layers, not an existing
network admission proof:

- The mapping compiler verifies every scanned external type/member against a
  finite recipe and exact source/target declarations and invocation kind. Generic
  HIGH candidates are not promoted without that independent semantic recipe.
- Existing `LogicProfileShape` and `LifecycleProfileShape` prove fixed ownership,
  registration/phase envelopes and linked callbacks. They do not recognize a
  network DTO, buffer effect or connection lifetime.
- Existing dynamic-site gates only admit their finite Supplier/IntUnaryOperator
  and opt-in lifecycle callback forms. Networking Function/BiConsumer, context
  suppliers and captured queued Runnables require **separately scoped** proof in
  both compiler and offline verifier. Do not broaden ReviewedLifecycleLambda or
  accept every SAM with matching arity.
- Offline linkage establishes scoped declaration/access/dataflow validity, not
  that a decoder is bounded, a consumer runs on the right thread or a packet
  targets only the intended player. Owned methods are not automatically safe
  merely because their external calls were mapped.

Recommended new admission gate:

1. Derive class/method/field roles from the complete ASM scan and exact linked
   call/handle references. Require one unique registration root, DTO, encoder,
   decoder, receiver, apply body and bounded session owner. Permit documented
   owned renames/debug metadata/literal values within bounds; reject ambiguous
   roles, surplus initializers, hidden methods and mutable state escapes.
2. Require closed instruction/CFG envelopes for protocol construction, registration,
   codec and phase transitions. Use symbolic stack/local provenance or ASM
   dataflow to prove the **same** buffer, DTO fields, context and connection are
   used. Merely finding a length check or a PLAY_TO_CLIENT constant somewhere in
   a method is insufficient. Every buffer read must be dominated by its length
   guard, every state commit by bounds/direction/session/sequence guards, with
   no exception handler, helper call or alternate edge bypassing them.
3. Check exact callback SAM descriptor, captured tuple, static owned implementation,
   registration role and method-handle provenance. Forbid free handles, alternate
   metafactories, reflection, overridden/receiver dispatch and unreviewed helper
   calls from codecs/consumers. Do not discard unaccounted-for legacy methods.
4. Restrict writes by phase: registration only at its root, DTO fields only in
   its constructor, current snapshot only in the guarded client apply/reset
   routes, sender counters only in the audited server producer. Reject aliases,
   field shadowing, extra subscribers and calls into apply/reset from constructors,
   unlinked helpers or the wrong physical/logical side.
5. Keep all existing profiles' acceptance contracts unchanged. Add an explicit
   new profile/callback policy; default CLI/API/UI behavior must not accidentally
   opt in. Bind every changed descriptor/call to its real implemented operation,
   retain schema-versioned reports and validate original/target bytecode and
   linkage before publication. Hashes identify tested artifacts, not admission.

Some semantically equivalent source forms will be refused by these finite
envelopes. That is preferable to claiming a general proof of arbitrary network
programs. A transformation log should distinguish unchanged wire fields from
necessary class/member/descriptor changes and any explicit protocol migration.

## Adversarial and runtime acceptance matrix

| Area | Required positive/negative evidence |
| --- | --- |
| Registration | Exact discriminator; duplicate ID/class; -1/256/257 aliases; dynamic increment; missing/wrong direction; extra registration; callback substitution |
| Protocol | Exact peer version succeeds; mismatch, absent channel and vanilla peer refuse under the selected strict policy; no wildcard hidden in a helper |
| Decode | Exact-size payload succeeds; all truncation lengths, trailing byte, oversized buffer, swapped fields, huge/negative sequence and value bounds refuse without state change |
| Float extension, if selected | Finite endpoints, negative zero policy, NaN, infinities and extreme values; no accidental Float-comparison bypass |
| Control flow | Guard in dead/unrelated branch, guard on another buffer, inverted comparison, catch-and-continue, helper/handle bypass and partial state write are rejected statically |
| Effects | C2S registration/send/getSender branch, server/world/player mutation, retained buffer, dynamic collection/allocation and unbounded work list remain unsupported |
| Queue | Inline and delayed execution, capacity reached, concurrent receives, scheduling exception, cleanup failure and disconnect while pending do not leak or overrun reservations |
| Sequence | Initial 1 and exact successor accepted; gap/duplicate/older rejected; signed-int boundary and wrap rejected; failed value validation cannot advance sequence |
| Session | Before-join packet; logout with pending task; reconnect same UUID; stale high sequence after fresh low sequence; repeated reset; old cleanup cannot clear new pending state |
| Isolation | Two distinct real clients A/B receive only their selected snapshots; disconnect/reconnect A does not alter B or server gameplay state |
| Distribution | Dedicated server loads the same final public artifact without resolving client-only classes; client executes the real registered network route, not a direct probe-only handler call |

Malformed-packet outcomes must be stated precisely: refusal without mutation,
logged drop, decoder failure and native disconnect are not interchangeable. An
unknown-discriminator direct codec test does not prove what the complete channel
handler does afterward. No tests should target a public server or other players.

Compile/ASM/linkage checks, synthetic codec tests, ordinary server discovery,
real client delivery, cross-client isolation and reconnect behavior are separate
evidence stages. The previous title-screen/resource and dedicated-server probes
do not certify any of this new networking path. Record final artifact hashes,
endpoint identities, independent client/server logs and exact observed state;
keep unperformed lifecycle/thread/MP gates NOT_RUN rather than extrapolating.

## Priority and remaining gaps

First implement one authored fixed snapshot and its full bounded/session proof.
This has one corpus synchronization analogue (AppleSkin) and **zero verified
whole-mod unlocks**. Next investigate actual harmless UI-observation receivers
with direction and lifetime semantics in a better authorized cohort. Separately,
Clumps entity spawn state requires entity/serialization/tracking/persistence and
rendering work; it is not the second primitive added to the same snapshot codec.

Remain unsupported: C2S actions, gameplay mutations, login negotiation payloads,
optional-peer negotiation, arbitrary packet distributors/broadcast/tracking,
unbounded data/NBT/collections, entity spawn data, capability sync, custom compression,
arbitrary schedulers, stateful protocol migrations and unproven client lifecycle
events. No corpus status was changed by this audit. Compiler/fixture implementation
and its validation are separate follow-up work, not evidence produced here.

### Selected fixture versus stronger recommendations above

The subsequently authored two-class `NetworkSnapshot` / `SnapshotMessage` source
was read without executing it. It uses the explicit six-argument S2C registration,
fixed eight-byte payload, a negative invalid-decode sentinel rejected before state
application, int sequence starting at 1, value 0..1000, connection-open checks,
and refusal of a different connection while the stored connection remains open.
Its Runnable captures exactly message then Context. A separate PLAYER destination
Supplier captures and returns the same ServerPlayer; this requires its own narrow
SAM recipe, not general captured-Supplier support. The native final artifact and
runtime results still require separate evidence.

This fixture does **not** implement every stronger recommendation above. It has
no login/logout event subscribers or explicit generation token. Visibility is
checked passively through connection.isConnected; a new session is adopted only
on a valid sequence-1 message after the prior connection closes. It does not
compare against Minecraft's currently selected client connection. Sequence 0
denotes no visible snapshot, distinguishing that from a legitimate value 0.
Acceptance/rejection counters and last handler thread ID are cumulative process
observations, not reset-per-session values. The old connection reference is
retained until replacement. Each handled message enqueues native work without
an application pending-work bound; limiting payload size and rejection logging
does not establish flood/backpressure safety. Keep these limitations explicit
unless a separately reviewed implementation actually closes them; do not claim
full session reset, explicit epoch isolation or bounded pending work on the basis
of a successful ordinary delivery/reconnect test.
