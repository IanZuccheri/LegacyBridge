# Clear-return investigation (developer QA only)

2026-09-07. Continues [initial-send protocol observation](NETWORK_HANDSHAKE_PROTOCOL_OBSERVATION.md).
The cursor remains NET-QA-02-HANDSHAKE / CLIENT_PROTOCOL missing normal acceptance.
Exact source/target Forge pins, simple-content-v1, the converted fixture and public
runtime ABI are unchanged. This experiment is not a conversion rule or adapter.

## Why a separate observation mode

The previous JDI mode pauses both the connector and Netty threads at several
setters. It captured an unset initial protocol, but restored OP_READ and passed
only under debugging. The paired normal execution still stalled. This mode instead
observes only the return from the native clear operation, after its body executes.
It does not pause sendPacket, setProtocol, setAutoRead or SelectionKey.interestOps.
Fewer observation points are a design property, not proof of zero interference.

The pinned Netty 4.1.82.Final archive was inspected with javap. Its
AbstractNioChannel.clearReadPending0 `()V` has 16 bytecodes: readPending is written
at BCI 2, removeReadOp is invoked at BCI 12, and RETURN is BCI 15. Length/opcodes are
checked before installing that sole breakpoint. The actual source behavior is in
[Netty's pinned NIO implementation](https://raw.githubusercontent.com/netty/netty/netty-4.1.82.Final/transport/src/main/java/io/netty/channel/nio/AbstractNioChannel.java).
Both fixed Forge source archives were also rechecked: source queues the disable;
target disables on the caller and queues enable. No source/game classes are copied.

## Ownership, bounds and privacy

Only an exact NioSocketChannel on a Netty Client IO thread is considered. A bounded
32-context, cycle-rejecting pipeline walk requires exactly one actual
net.minecraft.network.Connection handler. Head/Tail/DefaultChannelHandlerContext
forms and declared fields must match; unknown forms fail, not pass by resemblance.
Only handler type identities are read, never handler state or packet data. Multiple
eligible channels are refused. The trace is not a generic observer for arbitrary mods.

The config, pipeline, next/context-handler and selector declarations were verified
in the installed Netty JAR. JDK 17 AbstractSelectionKey.invalid `Z` was checked with
javap; it distinguishes a valid key lacking read interest from a cancelled key.
Read fields are autoRead `I`, interestOps `I`, readPending `Z` and invalid `Z`.
A 64-entry field cache avoids repeated declaration walks after initial binding.

The new explicit -ReadClearTrace mode is mutually exclusive with all prior modes.
It uses the existing exact-owned-PID loopback JDWP endpoint, suspend=n, separate
16/128 MiB helper and prepared-install Gson, with the same 5.25 GiB free-memory gate.
No endpoint/firewall/launcher/auth settings are changed. Normal mode opens no JDWP.
The privileged endpoint must only be used in these disposable synthetic QA profiles.

At most 16 return samples, 4096 events and 64 KiB output are permitted. The existing
120-second cooperative collection deadline and explicit stop/detach flow remain;
a hung JVM/JDI command is not covered by a hard time bound. The final field reading
requires three seconds without another selected clear and does not suspend a thread.
No target invocation, field mutation, bytecode redefinition or forced read/flush
occurs. Breakpoint suspension still changes timing according to the
[JDI event suspension contract](https://docs.oracle.com/en/java/javase/17/docs/api/jdk.jdi/com/sun/jdi/request/EventRequest.html).

## Meaning of the report

The new schema-1 LEGACYBRIDGE_CLEAR_RETURN_TRACE is separate from the older
schema-2 read-interest/protocol report. An inconsistent state requires a valid key,
autoRead=true, no OP_READ and readPending=false. An inconsistency at a clear return
and again in the later reading yields DIAGNOSTIC_WITNESS; restored or unobserved
later state does not. The typed controller rechecks every classification and
rejects wrong phases, masks, identities, quiet periods, status/schema and promotion.

These are two observations, not continuous tracing. Fields across objects are not
an atomic snapshot. A return sample does not show whether OP_READ was set on entry,
nor every intervening enable/clear, nor the full causal schedule. Even a witness
does not certify normal refusal or authorize a platform patch. Empty/malformed
collection fails explicitly and cannot become a successful handshake result.

## Execution and next step

One fresh ordinary target installation, `handshake-cp-clearreturn1`, was prepared
from already available local files (one client and one dedicated server). The
CLIENT_PROTOCOL run passed only DIAGNOSTIC_ORACLE_PASSED in 65 seconds. Forge
recorded the server rejecting protocol 2 for legacybridge_network:snapshot and
terminating the handshake. The client observer recorded the actual
ModMismatchDisconnectedScreen; no screenshot is claimed from this run.

The three processed JDI events yielded one selected CLEAR_RETURN sample:
autoRead=false, interestOps=0, readPending=false, keyValid=true. This is not the
inconsistent state. The VM disconnected 390 ms after that sample, before the
required quiet period, so settledObservation.observed=false with
NO_LIVE_QUIET_PERIOD. There is no later state, no assertion that OP_READ was
restored, and no causal witness. The trace status is OBSERVED_NO_WITNESS.

The normal oracle was not relaxed. This instrumented refusal does not replace
the preceding failed DEFAULT comparison. No normal run was repeated on unchanged
inputs simply to accumulate approvals; SERVER_ABSENT/SERVER_PROTOCOL remain NOT_RUN.
Client, server and debugger exit codes are zero, server save completed, and the
owned-process/listener audit is empty. Twelve executed QA inputs were archived
under the run directory before post-run controller hardening.

The seven new inert Java tests cover state classification and pre-attach refusal;
42 fixture tests pass overall. They are not mocks of every JDI pipeline traversal:
the real run exercises the positive binding, while exhaustive mirror-negative
coverage is still absent. The 23 new controller cases include bounds, mode
isolation, no invented later state and rejected JSON scalar coercion. All 204
handshake cases pass, and the real report also passes the stricter post-run
validator. Root build results, 186 earlier diagnostic and 81 installation checks,
artifact hashes and raw receipts are recorded in the
[versioned ledger](evidence/network-handshake-clear-return-2026-09-07.json).

Next, change the investigation strategy before another native attempt: define a
discriminating hypothesis and source-verify an observation point that does not
suspend activation, sends or the clear. The breakpoint-return reduction did not
capture the normal fault and must not become a retry loop. If a safe observation
or production-compatible fix requires a platform/version change, request explicit
direction before doing so. No forced read/flush, artificial delay, weakened
acceptance, game patch, later feature family or famous-mod intake is enabled.
