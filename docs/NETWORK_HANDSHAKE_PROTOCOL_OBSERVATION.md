# Initial-send protocol observation (QA only)

2026-09-07. Continuation of [read-interest tracing](NETWORK_HANDSHAKE_READ_TRACE.md).
This extends only the separate developer debugger helper and its receipt checks;
the converter, runtime, public JAR and installed Forge QA probe are unchanged.
Pins remain Minecraft 1.16.5 / Forge 36.2.42 to 1.20.1 / Forge 47.4.23, Java 17.

## Verified source and declaration differences

The actual pinned source archives show different read scheduling. Source
NetworkManager.sendPacket enqueues disabling autoRead, and setProtocol enables it
directly. Target Connection.sendPacket disables it on the calling thread, while
setProtocol stores the protocol and enqueues enabling autoRead. On the target,
channelActive initializes HANDSHAKING; ConnectScreen waits on the channel future
and sends intention then hello from its connector thread. These source facts alone
do not prove the ordering taken by a particular native connection.

The installed Netty bytecode also verifies that fulfillConnectPromise calls
trySuccess at BCI 14 before fireChannelActive at BCI 36. Waiting for connect success
therefore does not guarantee completion of Minecraft's activation handler. A new
bounded real-Netty loopback test holds an owned activation handler and verifies
that distinction, then releases it and shuts down normally. This is a library
ordering test, not a captured failing game schedule or a platform repair.

Netty 4.1.82.Final's [config setter](https://raw.githubusercontent.com/netty/netty/netty-4.1.82.Final/transport/src/main/java/io/netty/channel/DefaultChannelConfig.java)
updates the flag atomically, while its callback and
[NIO clear scheduling](https://raw.githubusercontent.com/netty/netty/netty-4.1.82.Final/transport/src/main/java/io/netty/channel/nio/AbstractNioChannel.java)
are separate work. The earlier controlled library tests and native selector
mismatch remain evidence, not a new platform patch or full causal proof.

Importantly, the installation contains both vanilla SRG and Forge-patched classes.
Javap on the **executed forge-1.20.1-47.4.23-client.jar** verifies sendPacket as
`m_129520_(Packet,PacketSendListener)V`, 130 bytecodes, and these local declarations:

| Local | Meaning | Slot / live range |
| --- | --- | --- |
| connectionprotocol | Protocol required by the outgoing packet | 3 / BCI 5 through 129 |
| connectionprotocol1 | Protocol captured by getCurrentProtocol before disabling | 4 / BCI 11 through 129 |

Both descriptors are `Lnet/minecraft/network/ConnectionProtocol;`. The unpatched
vanilla SRG JAR instead has $$2/$$3 locals and a 127-byte method. Its metadata must
not be substituted for the actually executed patched class. No game source or
class is copied into the trace helper, converted fixture or public artifact.

## Diagnostic schema 2 and boundaries

SendProtocolObservation inspects only those two non-argument locals in the exact
sendPacket frame during setAutoRead(false). It compares native enum references to
the four verified static enum constants. Null becomes explicit UNSET; unexpected
types, constants, locals, duplicate frames or signatures fail closed. It never
reads packet fields, caller arguments, enum strings, auth values or calls a target
method. Absence of a send frame means not sampled, not an invented protocol value.
The prior local is historical state at getCurrentProtocol, not a fresh channel read.

Trace schema 2 is required by the updated controller; historical schema 1 files
remain immutable. Protocol observations must be attached to a disabling config
sample with the exact sendPacket line 206 stack entry, with a known non-null and
changed packet protocol. Unknown values and missing halves are rejected. Zero
observations never becomes proof of a protocol or a successful handshake.

The debugger's existing bounds, privileged loopback-only endpoint, event-thread
suspension, cooperative cleanup and no-I/O-mutation contract still apply. Reading
the locals adds debugger latency; this mode cannot certify normal acceptance.
Eight new inert Java mirror tests allow only the declared metadata accesses and
refuse any unexpected JDI call. Ten PowerShell cases cover the receipt boundary.
These tests do not execute Minecraft or prove it followed a particular schedule.

## Native outcome and continuation

Two fresh ordinary target installations were prepared locally and run serially:

| Run | Mode / result | Actual evidence |
| --- | --- | --- |
| handshake-cp-protocoltrace1 | READ_INTEREST_TRACE / DIAGNOSTIC_ORACLE_PASSED, 64 s | Eight samples, 24 processed events; first send UNSET -> HANDSHAKING, second HANDSHAKING -> LOGIN; exact native refusal |
| handshake-cp-protocol-pair1 | DEFAULT / FAILED_OR_INCOMPLETE | 22 server handshake dispatches; no client LoginWrapper entry and no exact native version/channel/termination oracle |

The first run's null prior local is now observed fact for that instrumented
connection. It does not retroactively inspect earlier failures. The selector clear
again observed autoRead=false, followed by OP_READ restoration; status remains
OBSERVED_NO_WITNESS for the faulty stale-clear pattern. Event-thread suspension
changes timing, so the diagnostic result is not normal-mode acceptance.

The paired default run had no JDWP, trace, socket snapshot or extra logging flags.
It reached a generic DisconnectedScreen with zero logins/effects, which is **not**
the required protocol refusal. The native server saved; the runner passed its
exit-code checks before failing the exact log oracle. No successful aggregate
receipt or independent per-process exit-code receipt is invented for this failure.
The persistent failure journal and independent process/port audit show all owned
actors closed. SERVER_ABSENT and SERVER_PROTOCOL were not prepared or launched.

The [new ledger](evidence/network-handshake-protocol-2026-09-07.json) binds both
attempts, executed-input archives, tests and baseline hashes. The helper hash is
`07eebef30e6f1a1bd1e8738e5252fe7d770a23f846ad483be4f7391f447115c9`;
its actual schema-2 trace hash is
`8d4779e5684135a87f40b1008dcbdd1acba1e9cf967fca8e6047fe6a3fed4322`.
The public JAR, original, converted fixture and in-game probe retain their prior
hashes. No platform patch, forced read/flush, version/security change, dependency
removal, original execution, new famous-mod acquisition or publication occurred.

35 inert/library Java tests passed and root checks ran 181 handshake, 186 earlier
diagnostic and 81 installation PowerShell cases; root Java tests were UP-TO-DATE.
Native runs are separate evidence, never implied by these counts. Next capture
the stale clear/queued re-enable in the now-identified initial path with lower
observation perturbation. The normal CLIENT_PROTOCOL gate and all later gates
remain open; the initial null observation is not a complete causal diagnosis.
