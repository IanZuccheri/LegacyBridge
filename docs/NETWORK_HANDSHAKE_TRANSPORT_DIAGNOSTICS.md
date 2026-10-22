# NET-QA-02: JFR and selector diagnostics

Date: 2026-09-07. This is target-only QA against Minecraft 1.20.1 / Forge
47.4.23 / Java 17.0.12 / Netty 4.1.82.Final. No converter, runtime adapter,
source fixture, converted fixture or public product bytes are changed. The
initial [handshake](NETWORK_HANDSHAKE_VALIDATION.md) and
[snapshot ledger](evidence/network-handshake-diagnostics-2026-09-07.json) are
historical evidence, not normal-mode approval. Current prompt cursor remains
NET-QA-02-HANDSHAKE / CLIENT_ABSENT investigation.

## Implemented, separate observations

`-PacketRecording` is explicit and cannot combine with DEBUG, packet markers or
socket snapshots. It always reports diagnostic-only status. The existing control
worker starts a narrowly configured native JFR recording before READY; client
connection waits for readiness without blocking the render thread. No `/jfr start`
broad configuration, custom Java agent, platform bytecode patch or channel handler
injection is used. JFR's own JVM-managed facilities are separate from converter ASM.

The actual target sources establish different observation boundaries:

- `PacketEncoder.encode` calls native `JvmProfiler.onPacketSent` after `Packet.write`
  returns. The earlier DEBUG encoder entry occurs before serialization.
- `PacketDecoder.decode` calls `onPacketReceived` after construction, but before the
  remaining-byte check. Neither event alone approves gameplay or a handshake.
- `JfrProfiler` registers its custom events and commits PacketSent/Received when
  their EventTypes are enabled, independently of its broad `/jfr` configuration.
- Pinned `PacketEvent` fields are protocolId, packetId, remoteAddress and bytes.
  No payload field is recorded. The exact local Java 17 sources confirm native
  SocketRead/Write events for SocketChannelImpl, including gathering writes.

Only those four event names and DataLoss are enabled. Every already registered
event type is first explicitly disabled: empty Recording settings alone honor
custom @Enabled(true) defaults. The first native run exposed this distinction
through `minecraft.ServerTickTime`; the collector refused that unexpected event.
A regression now covers pre-registered default-enabled custom events. Unexpected
later event types and JFR data loss still fail interpretation.

Limits: native retention 8 MiB/two minutes, 120-second deadline, 16 MiB export/read
gate, 4096 events, 64 packet groups and <=16 KiB aggregate JSON. JFR retention is
chunk-granular, not a strict JVM allocation bound. The existing process heap/free
RAM gates remain unchanged. Fresh owned temporary dumps are deleted even on parser
failure; deletion failure propagates. JDK socket events for other endpoints are
counted separately without exporting addresses. No payload, JVM arguments or real
account credentials are exported. This is an isolated-fixture diagnostic, not a
general profiler for arbitrary mods. Native JFR behavior/settings are described by
[Java 17 Recording](https://docs.oracle.com/en/java/javase/17/docs/api/jdk.jfr/jdk/jfr/Recording.html).

The controller now validates typed aggregate identities, booleans, counters,
groups and reconciliation and binds recording reports in future success receipts.
The older jfr2 success receipt predates that binding: the additional ledger hashes
both files explicitly, and they passed the new validator offline. That offline
validation does not turn the run into a normal-mode pass or rewrite its receipt.

## Controlled selector reproduction

The exact local Netty JAR was inspected with javap, not inferred from latest APIs.
AbstractNioChannel.selectionKey/readPending/readInterestOp have verified types.
Its queued clearReadPendingRunnable calls clearReadPending0, which removes the
read interest without rechecking autoRead. The source/reference at
[Netty AbstractNioChannel](https://netty.io/4.1/xref/io/netty/channel/nio/AbstractNioChannel.html)
is explanatory; the executed 4.1.82.Final bytecode and tests are the pinned proof.

Two bounded Java/Netty loopback tests compare schedules on an owned connection:
same-event-loop disable then enable retains OP_READ; queue enable, then disable
from outside the blocked event loop, then release it, and the queued clear runs
after enable, leaving autoRead=true but interestOps=0. Both tests close their own
channel/event loop and have a deadline. They do not execute Minecraft or claim it
took this schedule. They change only their own test channel, never a native game
connection, platform class or converted call site.

The native `-SocketSnapshot` observer additionally reads the selector's valid flag,
interestOps, readyOps, readPending and OP_READ membership on the existing Netty
event loop. It never changes any of them. An earlier autoRead=true observation
alone is insufficient to establish an active selector read subscription.

## Execution evidence

The [new ledger](evidence/network-handshake-transport-2026-09-07.json) records
exact artifacts, sources, build logs and native outcomes; older ledgers are retained.

| Run | Result |
| --- | --- |
| handshake-ca-jfr1 | FAILED observer: unexpected default-enabled minecraft.ServerTickTime; temporary dump removed; no JFR summary/overall pass |
| handshake-ca-jfr2 | DIAGNOSTIC_ORACLE_PASSED: real ABSENT rejection. Server wrote/client read 1075 bytes; client wrote/server read 294 bytes. Three LOGIN query packets serialized and received. This is the successful diagnostic run, not the stalled normal run. |
| handshake-ca-selector1 | FAILED native oracle, under default logging with read-only snapshot. Client selector valid, autoRead=true, interestOps=0, readInterested=false, readPending=false; server interestOps=1/readInterested=true. Actual stalled connection, no native version refusal. |

The native sample now **localizes the client-side missing read subscription**,
which the earlier config flag alone did not expose. Client readyOps=8 is recorded
as the selector's observed ready mask, not a history of all I/O. Both sides were
active/open in LOGIN. No buffer, flag, key or read operation was changed by the
observer. Both actors closed normally; the failed oracle is retained.

This matches the controlled-library reproduction's inconsistent state. The exact
Minecraft/Forge task interleaving that caused it is not directly traced: do not
turn a matching state into proof of the full trigger. The pinned Connection code
and native asynchronous enable/off-loop disable paths are the next audit target.
Any remedy must respect the prohibition on rewriting platform classes and cannot
be an unannounced library/Forge/Java replacement, forced read/flush or timeout change.
The overall handshake gate is still open; neither this diagnostic nor its test
constitutes a production workaround.

The final QA artifact is
`58328c4b728cfc0e092c9a27dee4c7ab420a7b52d6f403636b9361088106c353`.
It has 16 isolated tests (8 scenario/archive, 2 prefix, 4 JFR, 2 Netty selector),
not 16 game tests or supported mods. The root checks add three recording-mode and
14 typed recording-receipt cases; existing root Java tests were UP-TO-DATE.

Jfr2 also observed 93 client background socket events, counted but not exported
with endpoint addresses. No offline-game claim is made. Original execution in
analysis/conversion and native source-version comparison were not performed.

The default-mode failure remains unclosed. Do not infer a repair from JFR success,
or the native trigger from a controlled Netty schedule. No new profile, platform
upgrade, forced read/flush or security-setting change is authorized by these
observations. Protocol/missing-server negatives and source/target comparison remain
pending, followed by NET-QA-03 and capabilities. Famous-mod intake remains deferred.
