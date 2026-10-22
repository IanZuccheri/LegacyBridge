# Native network QA plan — network-snapshot-v1

This is a **separate runtime experiment**, not a conversion step and not an
installed-product feature. A successful static build/ASM validation is not evidence
of packet delivery. No original input code is executed by analysis or conversion.

## Exact baseline and inputs

- Source fixture: `fixtures/network-snapshot-1.16.5`, genuine Java 8 Forge 36.2.42
  build, Minecraft 1.16.5, `legacybridge_network` 1.0.0.
- Target: ordinary Minecraft 1.20.1, Forge 47.4.23, Java 17.
- Three runtime JARs, identical across dedicated server/client A/client B:
  `legacy-product/build/libs/LegacyBridge.jar`,
  `build/network-snapshot-v1/converted.jar`, and the **test-only**
  `fixtures/network-runtime-probe-1.20.1/build/libs/legacybridge-network-probe-1.20.1-1.0.0.jar`.
  The probe is not a product requirement and is never published with LegacyBridge.
- `build/network-snapshot-v1/conversion.json` must bind the original/output hashes,
  CONVERTED status, committed transformations, passed bytecode checks without mod
  class loading, exact S2C profile and matching public adapter version.

Local preparation reuses only ordinary, already installed libraries and assets:
`build/p6-public/client` and `build/logic-preserving-v1/target-server/libraries`.
It does not reuse prior mods, saves, configs, options, username/account caches,
launch-arguments, private conversion mappings, or launcher profiles.

Audited client metadata SHA-256:
`154923d5c1a51b669bf8a981693f068021312f2495a13bf87b277bc702d95c7b`.
Audited preparation receipt SHA-256:
`1eed6c7fcff6e81ae082b3014c46dcd3f5205658de84db56c0aeaa64145c3fb0`.
Asset index 5 SHA-1: `37929153495e2e44c1bdd1927f519eaa0f4b7bda`,
3,575 unique objects. Every selected library, asset and copied installation file
is rehashed during preparation; runtime rechecks the preparation inventory.
Server argument file is pinned to the reviewed ordinary-installation SHA-256
`659e71b783f09de7cfc05591472191b73ea5321864412ce63f49013c266781b8`.
These are local installation inputs, not redistributable product data.

The client bootstrap library list is not the complete Forge discovery inventory.
`NetworkRuntime.Installation.ps1` also reads bounded `install_profile.json` data
from the already-local official installer (SHA-256
`75cfcb11f60cc641dc83757c1357c08c9bdba255ec529feb115c5e89a59cf75a`).
It requires the exact four FML libraries (`fmlcore`, `javafmllanguage`,
`lowcodelanguage`, `mclanguage`) and `forge:universal`, including canonical paths,
official SHA-1 and bounded integer sizes. They are copied from the audited P6
installation, not added to a fabricated bootstrap classpath or downloaded.
The native preflight independently requires their inventory entries and verifies
their files before launching any actor. Pure installation-inventory tests run in
root `check` on Windows. The installer itself is never executed here.

Fresh preparation now inventories 7,465 files, ten more than the historical
7,455-file preparations (five discovery libraries per client). Regenerable library
and asset copies are closed and hash-verified without a forced disk barrier per
file; the final receipt remains durably flushed. Runtime rechecks all input
hashes. This does not change the product installer's transactional write barriers.

## Isolation and execution

```text
build/network-snapshot-v1/<fresh RunLabel>/
  preparation-receipt.json
  server/       # new world, only three approved JARs
  client-a/     # synthetic offline LegacyNetA
  client-b/     # synthetic offline LegacyNetB
  control/
    server/     # sequential commands and forced/atomically published receipts
    a/
    b/
```

Each process has its own empty user home, empty Gradle home, native/temp directory
and game directory. Preparation never accepts an EULA: it requires an already
accepted isolated-test EULA. Existing/partial RunLabels are refused and retained.
All path ancestors must be plain directories/files, not symlinks or junctions.

Only this explicitly authorized test runs three cooperating game processes at
once; builds and other owned Java operations must finish first. The server binds
`127.0.0.1:25591`, disables RCON/query, uses offline mode and two synthetic names.
No real account credentials are read. Offline UUIDs use the actual vanilla
name-based UUID convention. Identity arguments, including the synthetic dummy
access token required by the native launcher, exist only in the new process's
argument string, never in the persisted safe argument file or controller receipt.
Captured launcher-identity log lines are redacted. This is not an authentication
test or a supported universal launcher restart mechanism.

The default two-client runner requires at least 6 GiB free RAM before launch. Heaps are bounded to
1,536 MiB server and 1,792 MiB/client; client resolution is 960×540, FPS 20 and
client render distance 2 and simulation distance 5 (the pinned client's minimum).
The dedicated server independently uses view/simulation distances 2. Fresh native options set
`onboardAccessibility:false`, `skipMultiplayerWarning:true` and
`pauseOnLostFocus:false`; no user settings are copied. These keys and startup
behavior were verified in the exact `Options`/`Minecraft` sources. Native connect
and disconnect do not require OS foreground/focus or desktop click automation.

```powershell
# Run builds serially first; no game is started by preparation.
.\gradlew.bat -p fixtures/network-runtime-probe-1.20.1 build --no-daemon
.\scripts\Prepare-NetworkRuntime.ps1 -RunLabel fresh-label
# Explicitly starts the three authorized, isolated native game processes:
.\scripts\Test-NetworkRuntime.ps1 -RunLabel fresh-label
```

When the three-process RAM gate is unavailable, a separately labeled **server-only
discovery/JVM test** can run without lowering that gate:

```powershell
# Uses a fresh prepared RunLabel, or one rejected before any runtime files/processes were created.
.\scripts\Test-NetworkRuntime.ps1 -RunLabel comparison1 -ServerOnly
```

`-ServerOnly` requires at least 3 GiB free RAM and uses the same 1,536 MiB server
heap and approved artifacts. It copies those three JARs only into `server/mods`;
no client process, client mod installation or client options file is created.
It shares the normal hash/path/adapter preflight, native server launcher and
cleanup. The only runtime sequence is `ServerStarted` readiness, the actual
fixture's zero counters/no players observed on the native server thread, then a
forced STOP receipt, native server save and zero process exit. No snapshot packet
is sent, and no handler behavior is claimed.

Its output is **`network-server-receipt.json`**, status `SINGLE_SERVER_ONLY`, with
`nativeTwoClientS2c`, `packetDelivery`, `behavior` and client main-thread application
all `NOT_RUN`. It cannot emit the full two-client pass banner or receipt.
The RunLabel becomes consumed by the native server world/logs/receipts; a later
full network run requires a fresh preparation such as `comparison2`.

RAM rejection prints `NETWORK_QA_PRESTART_BLOCKED` with the mode, required/observed
free KiB and `gameStarted=false`. A preflight rejection is not a runtime failure,
does not create a fabricated native receipt, and never closes the user's programs
or weakens either RAM gate. The earlier `comparison1` full attempt was rejected
below 6 GiB before any game started; that fact is not network-behavior evidence.

## What the native scenario asserts

The server probe invokes only the known running fixture's public
`sendTo(ServerPlayer,int,int)` endpoint. Positive snapshots pass through the real
SimpleChannel encoder, socket transport, explicit direction check, decoder and
`enqueueWork` handler. The client reads only public fixture observations. It
compares `lastThreadId()` with its actual native client main thread. The control
files are test orchestration, **not S2C transport or a purported C2S acknowledgment**.

The finite scenario validates:

1. Two actual dedicated-server player connections on separate native loopback
   sockets; A and B receive distinct values without cross-player mutation.
2. A valid zero value with nonzero sequence, duplicate and gap rejection without
   applying the rejected message.
3. Native raw payloads: truncated body, trailing byte, negative/out-of-range value,
   zero sequence; each yields the exact bounded diagnostic and unchanged state.
4. Unknown discriminator 255 yields Forge's real error log; a subsequent ordered
   valid message proves the connection remains usable. No disconnect is invented.
5. Actual disconnect clears visible session state; B continues receiving. A then
   reconnects in the same client process and must start a new session at sequence 1.
6. A sends a raw packet in the wrong direction; Forge closes that connection before
   the S2C handler. Server handler counters remain zero, and B still receives.
7. Forced receipts precede native shutdown, all three exit codes are zero, and
   dedicated-server save/stop logs exist. Original and installed JAR hashes remain
   unchanged.

Exact API evidence is the Forge/Minecraft target source archive SHA-256
`4bd6656a7cbe3809da68f3b709ca0f2fa186575f58538668c58ce30cb38c4932`:
`ConnectScreen.startConnecting(Screen,Minecraft,ServerAddress,ServerData,boolean)`,
`ClientLevel.disconnect`, `Connection.isMemoryConnection/isConnected`,
`PacketSendListener`, custom payload packet constructors, `IndexedMessageCodec`
unsigned-byte discriminator and `NetworkHooks.validatePacketDirection`.
Client classes remain behind a real `Dist.CLIENT` Forge subscriber; the common
entry point/server observer contains no Minecraft client imports.

The 2026-09-07 observer also records the native screen/overlay class names,
client tick count and whether a connection was requested. These are bounded
observations in success/deadline snapshots, not connection replacements or
extra packet transport. It does not change the native tick phase or deadlines.
Its separate artifact hash must be recorded rather than inheriting the old
probe's startup evidence. The invalid historical client simulationDistance=2
was corrected to the source-verified minimum 5; no causal connection to a
handshake timeout is inferred from correcting that independent option error.

The native diagnostic oracle is separately unit-tested by
`scripts/Test-NetworkRuntimeDiagnostics.ps1` (also part of root `check` on Windows).
For one discriminator **255** injection on `legacybridge_network:snapshot`, it
requires this exact ordered sequence from A: one `IndexedMessageCodec` ERROR on
`Netty Client IO #n`, one ERROR on `Render thread`, then one `ClientPacketListener`
WARN `Unknown custom packet identifier` on `Render thread`. The Forge hook runs
before `PacketUtils.ensureRunningOnSameThread`; an unhandled discriminator is
therefore inspected again when the packet is dispatched on the main thread.
Each call receives a new buffer copy. This is one injected packet, not two.

It also requires exactly one native
`LegacyNetA lost connection: Illegal packet received, terminating connection`
message from the server after the wrong-direction injection. The unknown-ID
sequence requires known logger/thread/severity fields: bare terminal text is
insufficient. Similar IDs, channels, player names, suffixes and wrong-case
messages do not satisfy the oracle. Wrong role/window/order, repeated stages or
extra exact diagnostics fail. Windows remain armed through shutdown drainage.
This is pinned to `ClientPacketListener` 1862-1864/2061, `PacketUtils` 17-35,
`ClientboundCustomPayloadPacket` 65-71, `NetworkInstance` 65-69,
`IndexedMessageCodec` 146-150, `NetworkHooks` and
`ServerGamePacketListenerImpl` in the archive above, not an arbitrary count
allowance or a dependency on DEBUG stack traces. The nested native diagnostic
receipt has schema version 2 and records all three stages separately. New outer
success receipts also use schema 2 for the scoped identity statements below;
conversion/report schemas are unchanged. It binds orchestrator
and diagnostic-helper hashes. These tests validate the QA oracle, not additional
mods or network behavior; only native execution can close the runtime gate.

## Evidence and explicit remaining gates

Only an actual completed **two-client** run publishes `network-receipt.json`. It binds source,
converted, public runtime and probe hashes; preparation/conversion reports;
per-role PID/start time/zero exit code; redacted console logs; and every native
step receipt. Failures publish `network-failure.json` with partial receipts and
any still-running exact owned PIDs. Cleanup requests native stop/close only,
never a broad kill. A timeout is not a pass; retain the run and inspect before
another launch. No screenshots are generated or implied by this probe.

Identity evidence is scoped: argument files exclude identity flags/values, and
captured console launcher-argument lines are redacted. Native Forge debug logs
may persist the synthetic QA player names/UUIDs (the native token field observed
in comparison5 was masked). No real launcher accounts are read and no real
authentication credentials are provided. New success receipt schema 2 replaces
the overbroad `identityArgumentsPersisted` field with five explicit booleans from
`Get-NetworkRuntimeIdentityEvidence`. This metadata-only correction does not
alter packet transport, native deadlines or acceptance conditions. The immutable
comparison5 schema-1 receipt has a documented erratum; it is not rewritten or
presented as an actual schema-2 native run. Failure receipt schema stays 1.

The runner leaves original-version runtime comparison, missing peer mod,
protocol mismatch, close-during-queued-work race, counter saturation, simultaneous
live connections in one client, graphics/UI, WAN and soak **NOT_RUN**. It does not
prove C2S gameplay, login-phase payloads, variable codecs, arbitrary channels,
cross-version multiplayer, offline operation or universal mod compatibility.
Do not transfer prior P6 screenshots/runtime hashes to this new artifact, and do
not describe the QA scaffolding itself as multiplayer validation.

A **separate** [handshake runner and record](NETWORK_HANDSHAKE_VALIDATION.md) now
uses `Prepare-NetworkRuntime.ps1 -SingleClient` (3,785 files, one client/server,
5 GiB free RAM; default two-client preparation/gate unchanged). The matched
handshake control passed, client-absent negotiation failed twice without the
native refusal decision, and three other negatives remain unexecuted. This is
not a retroactive change to the two-client runner or its immutable receipt.
