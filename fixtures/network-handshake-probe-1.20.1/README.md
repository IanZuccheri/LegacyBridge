# Native handshake peer/observer (QA only)

This standalone Forge 47.4.23/Java 17 artifact is not a player dependency,
converted mod, compatibility adapter or public distribution. It reuses the
existing test-only `FixtureAccess`, `RawTransport` and its parser type through
Gradle source inclusion; no converter rules or fixture method bodies are copied.
The prior two-client probe/JAR and its hash-bound NET-QA-01 evidence are unchanged.

In the five conversion cases, the **unmodified converted fixture** makes every tested refusal. An adversarial
peer may advertise protocol 2 while accepting remote protocol 1, so that its own
predicate does not preempt the real converted validator. It registers no packet
codec and cannot deliver gameplay. A missing peer registers no snapshot channel.
Its mod ID remains `legacybridge_handshake_probe`, never `legacybridge_network`.

| Scenario | Server fixture | Client fixture | Adversarial channel | Required rejecting side |
| --- | --- | --- | --- | --- |
| MATCHED | Yes | Yes | None | Neither; one real S2C snapshot 1/427 |
| CLIENT_ABSENT | Yes | No | None | Server, missing channel sentinel |
| CLIENT_PROTOCOL | Yes | No | Client advertises 2, accepts 1 | Server, incoming 2 |
| SERVER_ABSENT | No | Yes | None | Client, missing channel sentinel |
| SERVER_PROTOCOL | No | Yes | Server advertises 2, accepts 1 | Client, incoming 2 |

`PLATFORM_CLIENT_ABSENT` is a separate causal control, not a sixth conversion
acceptance case. Both installations contain only this QA JAR; native ModList must
contain exactly minecraft, forge and legacybridge_handshake_probe. The server
registers `legacybridge_handshake_probe:required` with strict protocol 1 and no codec;
the client has no such channel. Neither the public runtime nor any converted or
original fixture is installed or executed. The runner still hashes their unchanged
local baselines, which must not be confused with installation.

Run it explicitly with `-Scenario PLATFORM_CLIENT_ABSENT -SocketSnapshot` on a
fresh single-client preparation. Only default logging and the optional read-only
snapshot are allowed. Its strongest result is PLATFORM_BASELINE_ORACLE_PASSED,
never a conversion PASS. Oracles refuse each other's channel IDs. Generic timeout
remains a failed handshake; a snapshot can independently localize a transport state.

The QA metadata's runtime dependency is now optional solely to permit this control.
Every ordinary scenario still requires the exact runtime version and its fixture
inventory at runtime, with regressions for missing runtime and wrong fixture.
No dependency in a converted JAR or the public product has been removed.

Use `scripts/Prepare-NetworkRuntime.ps1 -RunLabel <fresh> -SingleClient`, then
explicitly `scripts/Test-NetworkHandshake.ps1 -RunLabel <fresh> -Scenario <case>`.
Each case gets a new ordinary installation/world, one server and one client.
The separate two-process gate is 5 GiB free RAM (1536/1792 MiB heaps); the existing
three-process test's 6 GiB requirement is unchanged. No downloads, EULA acceptance,
launcher-account access, user-world changes or implicit game launches occur at build.

Exactly one daemon worker per native process handles bounded receipt/marker I/O.
At most one READY and one result are retained; each JSON is <=16 KiB, written
with CREATE_NEW, force and atomic move. Only fixed one-byte finish/abort markers
are read. Client connects through the real native screen on the main thread,
observes a native disconnect screen or a real delivered fixture snapshot, then
closes **after** its receipt is published. Server observes disconnection on its
own ticks, publishes its result, and the runner requests ordinary stdin stop.
No blocking filesystem operation runs on the render thread. Missing fixture
observations omit counters instead of fabricating zero values.

The controller writes fixed markers into exclusive CREATE_NEW temporary files,
forces and closes them, then renames in the same directory without replacement.
Readers therefore never observe a marker while the writer still holds a Windows
exclusive handle. Existing markers/temporaries are refused and preserved.

Inert tests validate the closed case inventory and archive, not networking.
Native acceptance additionally requires exact Forge logger/thread/version/channel
rejection in order, zero negative logins/effects, positive control delivery,
clean process exits, server save markers and all input/install hashes unchanged.
A timeout, refused socket, ordinary error screen or generic error log is not enough.

## Explicit diagnostics (not normal acceptance)

`-ReadClearTrace` is a separate, mutually exclusive lower-intervention experiment.
It reuses the explicit owned JDWP endpoint/helper, but installs only the pinned
AbstractNioChannel.clearReadPending0 RETURN breakpoint (BCI 15). It does not pause
the sender, config setter, protocol locals or selector setter. A bounded 32-context
pipeline walk verifies exactly one native Connection handler before selecting the
channel; unknown declarations/context forms fail the diagnostic.

At most 16 return samples include autoRead, interestOps, readPending and key validity.
After at least three seconds without another sampled clear, a second field reading
does not suspend a thread. Both readings are non-atomic across fields; matching bad
states do not prove the state persisted continuously or that this clear changed a
previously-set bit. No packet data, handler data, endpoint strings or target methods
are read/invoked. Metadata field lookup is cached with a 64-field bound. The separate
schema-1 `read-clear-trace.json` never upgrades old trace reports or certifies normal
gameplay. See [clear-return observations](../../docs/NETWORK_HANDSHAKE_CLEAR_RETURN.md).

`-ReadInterestTrace` is a separate developer-only JDI diagnostic, mutually exclusive
with the other diagnostics and platform control. Only the owned disposable client
opens JDWP on `127.0.0.1:25592`, with `suspend=n`; the controller verifies the exact
listening PID before attaching. This is a privileged local debug endpoint, not a
player/launcher feature. It remains open until that client exits. Do not use it with
account-bearing profiles. No firewall, authentication or system settings change.

The separate `traceJar` is not a Forge mod, installed probe dependency or public
artifact. Its helper uses a 16/128 MiB heap and Gson 2.10 from the prepared client's
libraries, not a Gradle runtime cache. This mode alone raises the two-process free
RAM gate from 5 to 5.25 GiB. Two pinned breakpoints observe Netty's setAutoRead return
and SelectionKeyImpl.interestOps before its write. Suspending the event thread
changes timing: even successful refusal is only DIAGNOSTIC_ORACLE_PASSED.

Trace schema 2 additionally reads the two pinned sendPacket protocol locals when
the config setter is disabling reads. It compares their native enum singleton
identities with HANDSHAKING/PLAY/STATUS/LOGIN static fields; a null prior value is
explicit UNSET, not an inferred default. Unknown/missing declarations fail the
diagnostic. It does not read packet arguments or enum strings, call name/toString
or invoke getCurrentProtocol. Old schema-1 reports are retained, not upgraded.
The executed Forge client JAR has named protocol locals; the vanilla SRG JAR has
different local names and is not an interchangeable declaration source. See
[protocol observations](../../docs/NETWORK_HANDSHAKE_PROTOCOL_OBSERVATION.md).

It reads three transport scalar fields, the setter argument, the oldAutoRead boolean,
the two protocol locals described above and bounded stack identities. No target methods are invoked and no target fields,
bytecode, protocol or channel I/O are written. Cross-thread snapshots are not atomic.
Bounds: cooperative 120 seconds after attach, 4096 events, 64 selected samples,
20 inspected/eight exported frames per sample, 64 KiB report; three seconds of
selected-channel inactivity or a stop marker requests detach. These are not a hard
deadline for a hung JVM/JDI call. Detach precedes normal shutdown; cleanup failures
are surfaced, not concealed by killing the target. See the exact contract and native
evidence in [read-interest tracing](../../docs/NETWORK_HANDSHAKE_READ_TRACE.md).

`-PacketRecording` is a separate, non-combinable diagnostic using the pinned
Minecraft `minecraft.PacketSent`/`PacketReceived` events and Java 17 native
`jdk.SocketRead`/`SocketWrite`/`DataLoss` JFR events. It does not install an agent,
inject a pipeline handler, patch game classes, or enable the broad `/jfr start`
configuration. The existing control worker starts it before READY; the client
waits for that worker before connecting. Normal runs never start a recording.
All already registered event types are explicitly disabled before enabling the
five names: empty settings alone do not disable custom @Enabled(true) events.
The target profiler's events are registered before this selection. An unexpected
later event still causes an explicit refusal, not a permissive export.
JDK-only tests verify actual four-byte loopback I/O, settings restricted to the
five event names, unknown-event refusal and removal of the owned temporary dump.
They do not certify a Minecraft handshake.

The recorder uses native JFR chunk retention (8 MiB / two minutes), a 120-second
recording deadline, a 16 MiB dump/read gate, at most 4096 events and 64 packet
groups. Retention is chunk-granular, not a strict JVM memory allocation cap;
the runner's existing process heaps/free-memory gate remain unchanged. Internal
JFR threads/repository are JVM-managed. Only the existing control worker exports
bounded aggregate JSON, with no render/Netty-thread file I/O. Stack traces,
JVM information/arguments and payload events are not enabled. JDK socket metadata
can include unrelated background endpoints in the temporary recording; aggregate
output counts such events but does not export their addresses. Raw payloads and
authentication arguments are not recorded by these event types. The freshly
created owned dump is deleted after parsing; deletion failure fails the observer.
No raw JFR dump is published or bundled in LegacyBridge.jar.

`PacketSent` occurs after Packet.write returns; it is serialization evidence,
not proof of peer delivery. `PacketReceived` is before PacketDecoder's final
remaining-byte check, not final semantic acceptance. Native JDK socket events
record read/write byte counts, not gameplay handling; JFR data loss or unknown
event types abort interpretation. The result remains diagnostic-only even if the
native handshake oracle passes. Source evidence is in the actual pinned archives;
the API contract is [Java 17 Recording](https://docs.oracle.com/en/java/javase/17/docs/api/jdk.jfr/jdk/jfr/Recording.html).

The runner accepts `-MojangDebug`, `-PacketMarkers` (requires `-MojangDebug`),
and `-SocketSnapshot`. These opt-ins never turn a diagnostic result into a normal
PASS: a satisfied oracle reports `DIAGNOSTIC_ORACLE_PASSED` with
`normalModePassClaimed=false`. The default invocation adds none of their JVM flags.
The exact pinned FML loader log4j2.xml supplies the two logging properties;
Mojang DEBUG can enable extra code paths and change timing. PACKET_SENT is logged
before `Packet.write`, so that entry alone does not prove serialization or delivery.

`-SocketSnapshot` can be used without extra logging to observe a stalled connection
under default logging. After five seconds still on the native ConnectScreen it
requests at most one bounded snapshot on the connection's actual Netty event loop.
It observes autoRead, protocol, open/active/writable flags, pending outbound bytes,
handler names and decoder buffered-byte counts. A maximum three-byte absolute
frame-prefix read classifies the length without consuming or retaining payloads.
It does not inject handlers, read/flush the channel, set protocol/autoRead, mutate
buffer indices/refcounts, or inspect mod state or credentials. This observer is
test-only pinned reflection against ConnectScreen's unique Connection field and
Netty 4.1.82.Final ByteToMessageDecoder.cumulation; mismatch/access failure fails
the diagnostic explicitly. It is not a compatibility adapter or generic Netty API.
The dedicated-server counterpart uses the actual `getConnection().getConnections()`
inventory, synchronizes on its native list only to select the single connection,
and requests one event-loop sample after five seconds observing an active peer.
The shared transport observer references no client class; the archive boundary
has an inert test, separate from native execution. It does not hold the list
monitor while sampling, queue unbounded work, or inspect other processes/sockets.
The sample now also inspects the pinned AbstractNioChannel selector key, readPending
and readInterestOp fields on the actual event loop. autoRead=true alone does not
prove OP_READ is registered. A separate bounded Netty-only test reproduces a stale
queued clear after enable; it does not prove Minecraft took that exact schedule.
Native results and the missing client read subscription are recorded in the
[transport investigation](../../docs/NETWORK_HANDSHAKE_TRANSPORT_DIAGNOSTICS.md).

The existing one-worker control path publishes at most one `diagnostic.json`
(<=16 KiB, forced temporary write and atomic move), never performing its file I/O
on the render/Netty thread. Fast completion can precede the five-second sample:
the flag being present is not proof that a sample ran. Inspect and hash the actual
file separately, including for failed runs; it is not a handshake acceptance receipt.
Current target-only runtime observations and the unclosed normal gate are recorded
in the linked validation document and the
[clear-return attempt](../../docs/NETWORK_HANDSHAKE_CLEAR_RETURN.md). That attempt
passed only with debugging; its VM ended before a later settled observation and
the faulty state was not captured. Do not rerun this mode as a normal acceptance
test. The JDK-only prefix parser tests do not prove
the reflective observation executed on a native connection.

Source/target native behavior comparison, source-version handshake execution,
cross-version play, queued-close races, flooding/backpressure, GUI accessibility,
offline game, WAN and soak are separate gates. Synthetic names/UUIDs may appear in
native debug logs; launcher argument files exclude identity and captured argument
lines are redacted. No real credentials are used. See
[NET-QA-02 handshake record](../../docs/NETWORK_HANDSHAKE_VALIDATION.md).
