# NET-QA-02: independent platform control

Date: 2026-09-07. Target remains Minecraft 1.20.1 / Forge 47.4.23 /
Java 17.0.12 / Netty 4.1.82.Final. Source remains 1.16.5 / Forge 36.2.42.
This is QA isolation, not new conversion support or a platform repair.
The earlier [selector evidence](NETWORK_HANDSHAKE_TRANSPORT_DIAGNOSTICS.md)
and its ledger remain historical and unchanged.

## Source-backed hypothesis, not a native trace

The pinned target Connection source has three relevant paths:

- channelActive invokes setProtocol(HANDSHAKING), which queues autoRead=true;
- sendPacket can set autoRead=false from the connector thread before queuing send;
- doSendPacket on the event loop can queue another protocol enable.

The [Forge 1.20.1 patch](https://raw.githubusercontent.com/MinecraftForge/MinecraftForge/1.20.1/patches/minecraft/net/minecraft/network/Connection.java.patch)
also shows the queued enable. The locally hashed mapped archive is the exact
version reference; a moving branch is not a substitute for that pin.
The [Netty 4.1.82.Final source](https://raw.githubusercontent.com/netty/netty/netty-4.1.82.Final/transport/src/main/java/io/netty/channel/nio/AbstractNioChannel.java)
confirms an unconditional queued clear of pending read/OP_READ.

One possible order is enable -> stale clear -> later enable. In the existing
bounded Netty-only reproduction, the last enable sees autoRead already true and
does not restore selector interest. A third regression now explicitly tests that
last step. All operations in that test target its own library connection, not a
Minecraft connection. This proves a possible schedule, not that the game followed
it. No patched platform bytecode, extra read/flush, protocol override, library
replacement or timing workaround was added.

Both pinned NetworkRegistry source archives were also read: the newSimpleChannel
factory takes the real ResourceLocation, version supplier and two predicates.
The platform control uses this API directly with a QA-owned name and no codec;
it does not execute, translate or impersonate the original fixture.

## Implemented separation

`PLATFORM_CLIENT_ABSENT` is distinct from the five conversion acceptance cases.
The server registers only `legacybridge_handshake_probe:required`, protocol 1 with
strict predicates. The client has no such channel. Both ordinary installations
contain only the QA JAR. Native ModList must equal minecraft, forge and the probe;
extra/missing IDs fail. The runtime and converted fixture must be absent.

Only the QA JAR's runtime dependency became optional to permit this control.
Every ordinary scenario still enforces runtime 0.1.1-SNAPSHOT and the exact fixture
inventory. Converted metadata, mandatory dependencies, product and original bytes
are unchanged. The runner distinguishes baseline hashes from installed artifacts.

The baseline permits default logging and optional read-only socket sampling,
not DEBUG/JFR combinations. Its maximum success status is
PLATFORM_BASELINE_ORACLE_PASSED with normalModePassClaimed=false. Native oracles
use separate exact channels and refuse cross-case evidence. No timeout or generic
disconnect is accepted as the required channel/version rejection.

## Native observations

`handshake-platform-ca1`: PLATFORM_BASELINE_ORACLE_PASSED in 60 seconds.
Both processes loaded only the QA artifact. The server produced exactly one
required version refusal, channel rejection and handshake termination; no player
entered play. Both exits were zero and the server saved normally. No client
selector snapshot was produced: rejection completed before the five-second sample.
A requested diagnostic is not proof that a sample exists.

This run did **not** reproduce the earlier stall without LegacyBridge. It neither
establishes a platform-only cause nor clears the converted mod. A comparison using
the same rebuilt QA artifact and the untouched converted/public artifacts is
recorded separately; no result is inferred from the baseline's success.

`handshake-ca-platform-pair1`: CLIENT_ABSENT **PASSED in DEFAULT mode**, 61 seconds,
with the unchanged public JAR and converted fixture on the required roles. No extra
DEBUG, JFR, packet markers or selector sampler was enabled. The server produced
the exact three-step native refusal, both actors exited zero and the server saved.
This is the first recorded normal pass of this negative, not proof of a repair.
The rebuilt QA inventory checks/metadata can change timing; no production code
changed, so previous failed attempts must not be erased or called fixed.

Based on that exact normal oracle, the next finite negative was attempted before
further causal tracing. This explicitly revises the earlier plan to complete a
transport repair before collecting any remaining negative evidence: observing an
independent case is safe, but does not close the overall gate or promote stability.

`handshake-client-protocol1`: CLIENT_PROTOCOL **FAILED in DEFAULT mode**. The
client showed DisconnectedScreen, neither side entered play or applied fixture
effects, but the native version/channel/termination oracle was absent. The server
logged 22 handshake sends and the client no LoginWrapper reception. No selector
snapshot was enabled, so do not claim OP_READ=0 for this run from those logs alone.
The runner reached the oracle only after checking clean exits and server save;
the failure receipt records no remaining owned processes. The serial batch stopped.
SERVER_ABSENT and SERVER_PROTOCOL were not even prepared, and remain NOT_RUN.

The [artifact-bound ledger](evidence/network-handshake-platform-2026-09-07.json)
records these three executions, their exact scripts/JARs, observations and logs.

The QA artifact is `be3cf5f3506b7b07451730978cd1c34c68a4a0edff31f3a1b6c06bb22b288c91`.
Its 19 isolated tests pass: 10 scenario/archive, 4 JFR, 3 selector and 2 prefix.
Seven added PowerShell cases test platform inventory/modes and channel separation;
the 158 earlier handshake cases remain. These are not counts of supported mods.
Root build passed serially; existing root Java tests were UP-TO-DATE.

## Remaining gate

No production fix is established. Cursor is NET-QA-02-HANDSHAKE / CLIENT_PROTOCOL;
the shared intermittent transport investigation remains open. The actual native
read-interest removal trigger is still untraced. Remaining negatives, source/target comparison
and NET-QA-03 retain separate gates; capabilities and final famous-mod acquisition
do not advance merely because this control works. No publication was performed.
