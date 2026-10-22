# Native read-interest trace (QA only)

2026-09-07. Cursor remains NET-QA-02-HANDSHAKE / CLIENT_PROTOCOL missing a normal
native refusal oracle. Pins are unchanged: Minecraft 1.16.5/Forge 36.2.42 source;
Minecraft 1.20.1/Forge 47.4.23, Java 17.0.12 and Netty 4.1.82.Final target.
No conversion rule, adapter, public artifact, in-game probe or platform class was
changed. This is diagnostic infrastructure and a native observation, not a fix.

## Verified implementation boundary

The standalone handshake fixture builds an additional JDK-only `traceJar` from
`src/trace`, kept out of its Forge mod JAR and the public product. The runner's
explicit `-ReadInterestTrace` switch starts it after verifying that the exact
owned client PID alone listens on 127.0.0.1:25592. The client uses JDWP `suspend=n`;
no failed attach can suspend startup waiting for a debugger. Ordinary mode and
the dedicated server receive no debugger flag. Modes cannot be combined.

JDI is privileged local debugging, not sandboxing. Only synthetic, disposable QA
profiles are eligible. The endpoint stays open until the owned client exits; the
controller changes no firewall, launcher, token, library or security configuration.
The separate helper loads Gson 2.10 from the already prepared ordinary client
installation. Its heap is 16/128 MiB; the mode adds 256 MiB to the existing 5 GiB
free-memory gate. It neither downloads data nor requires Gradle at native runtime.

Actual installed Netty bytecode was inspected with javap, including its local
variable table. The pinned signatures are:

| Observation | Exact member / location |
| --- | --- |
| Completed config setter | DefaultChannelConfig.setAutoRead `(Z)Lio/netty/channel/ChannelConfig;`, ARETURN bytecode index 60, method length 61 |
| Previous setter state | boolean `oldAutoRead` local, live at index 60 |
| Selector mutation about to occur | SelectionKeyImpl.interestOps `(I)Ljava/nio/channels/SelectionKey;`, method entry |
| Scalar state | DefaultChannelConfig.autoRead `I`; AbstractNioChannel.readPending `Z`; SelectionKeyImpl.interestOps `I` |

The controller verifies those members and the return bytecode before installing
breakpoints. It selects one NioSocketChannel only from a config call whose real
stack contains Minecraft Connection, using config.channel and channel.selectionKey
to bind subsequent records. Unrelated channels do not become part of the trace.
It reads setter arguments, the pinned oldAutoRead local and the three fields; it
never invokes target methods, changes target fields, redefines classes, forces
returns, sets protocol or calls read/flush. Breakpoints suspend the event thread
and resume in a finally block. VM disposal detaches, not terminates the target.
See the official [JDI breakpoint contract](https://docs.oracle.com/en/java/javase/17/docs/api/jdk.jdi/com/sun/jdi/request/BreakpointRequest.html)
and [VirtualMachine.dispose](https://docs.oracle.com/en/java/javase/17/docs/api/jdk.jdi/com/sun/jdi/VirtualMachine.html#dispose()).

Limits are 120 cooperative seconds after attach, 4096 events, 64 selected samples,
20 inspected/eight exported allowlisted frames, and a 64 KiB output. Three seconds
without selected-channel events, a witness, VM disconnect or the owned stop marker
ends collection. A hung JDI/JVM call is not subject to a hard execution deadline.
The controller requests detach before native shutdown and reports failure to
detach; it does not hide failures by killing another process. Publication uses a
forced CREATE_NEW temporary and a no-replace move after detach.

No packet bodies, authentication fields or arguments are inspected. Retained
frame identities can include JVM-generated lambda suffixes, not just stable
source names. Cross-thread field snapshots are not atomic: a setter argument or
oldAutoRead value can differ from the currently observed autoRead. Do not interpret
that difference as evidence that the setter ignored its argument. BEFORE_WRITE
records are not assertions that the selector mutation has already completed.

## Native run: diagnostic refusal, no failing witness

Fresh `handshake-cp-readtrace1` was prepared by local copying only, then run once
with CLIENT_PROTOCOL and -ReadInterestTrace. It completed in 64 seconds with
DIAGNOSTIC_ORACLE_PASSED. The native server recorded protocol 2 rejection for
legacybridge_network:snapshot and terminated the handshake. No login or handler
effect occurred. Client PID 32372, server PID 29612 and helper PID 6408 exited zero;
the native server saved normally. This does not supersede the failed DEFAULT
`handshake-client-protocol1` or close the intermittent transport investigation.

The trace contains 23 processed events and eight selected samples, with status
OBSERVED_NO_WITNESS. All observations below refer to the actual native connection:

| Samples | Observation |
| --- | --- |
| 1, 3 | Off-loop setAutoRead(false) calls from Connection.sendPacket, reached by ConnectScreen initial intention (line 119) and hello (line 120) sends |
| 2 | A queued event-loop begin-read requested OP_READ |
| 4 | Queued setProtocol callback requested true; oldAutoRead was false |
| 5 | clearReadPending0 requested interestOps=0 while observed autoRead was false and previous OP_READ was set |
| 6, 7 | begin-read restored OP_READ and a false-to-true setter completed |
| 8 | A subsequent already-true enable observed OP_READ still set |

The clear did **not** observe autoRead=true, so the stale-clear witness did not
fire. The connection progressed and refused the incompatible protocol correctly.
The first disable came from the initial intention send, before converted codecs;
the prior Connection protocol was not inspected. A null/uninitialized prior
protocol is only a hypothesis to check against activation ordering, not a recorded
fact or a proven cause. Breakpoint suspension changes scheduling; neither this
diagnostic pass nor the unit classifier proves normal behavior or a repair.

## Reproducibility and remaining gates

The [versioned ledger](evidence/network-handshake-read-trace-2026-09-07.json) binds
scripts, sources, executed helper, logs, receipts and immutable baselines. Key hashes:

- Public LegacyBridge.jar: `8daae3478a1b59482c438b843311c4160b5bdc8e4721f8e6067a1a87b81bbf88` (unchanged).
- In-game probe: `be3cf5f3506b7b07451730978cd1c34c68a4a0edff31f3a1b6c06bb22b288c91` (unchanged).
- Separate trace helper: `c6e906e07f15c40b87fc6fb0b4fdf564903f778e8ce06c2805839c38532d8b63`.
- Native trace (5732 bytes): `fa8dfc29e4a6d644f204337019317d8d36e6b0407c71628a79035da0e18002a3`.

The standalone fixture build ran 26 inert Java tests, including seven trace
classifier cases and an archive boundary check. Root validation ran 171 handshake,
186 earlier diagnostic and 81 installation PowerShell cases; root Java tests were
UP-TO-DATE. Builds and native execution ran serially. Test counts are not mod counts.

Next investigate the protocol before the first send/activation and the concurrent
OP_READ removal without forcing I/O or patching platform classes. Then complete
normal CLIENT_PROTOCOL, SERVER_ABSENT and SERVER_PROTOCOL acceptance; the latter
two remain NOT_RUN. Original/converted native comparison, NET-QA-03, capabilities
and the remaining [prompt sequence](PROMPT_CONTINUATION.md) stay separate and open.
No new normal retry, original execution, famous-mod acquisition, public-only UI
acceptance, offline-game certification or publication occurred in this round.
