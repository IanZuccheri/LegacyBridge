# Native network runtime probe — test only

Exact baseline: Minecraft 1.20.1, Forge 47.4.23, Java 17. Separate QA mod
`legacybridge_network_probe` 1.0.0; never ship this JAR in LegacyBridge or convert
the probe. It depends on the separately converted `legacybridge_network` 1.0.0.

The dedicated server has no dependency on Minecraft client classes. Only the
Forge `Dist.CLIENT` subscriber uses `Minecraft`, native `ConnectScreen`, and the
native disconnect path (`ClientLevel.disconnect()` before `Minecraft.clearLevel`).
Synthetic offline clients are `LegacyNetA` and `LegacyNetB`, each in a fresh game
directory. Connections must be real loopback sockets, not integrated-memory
connections. No real launcher account or profile is consulted.

The closed test-control mailbox schedules operations onto the native server or
client thread. One daemon worker per process performs all JSON/file I/O; one
command is in flight, at most 96 commands/process, 2 KiB input and 16 KiB output.
Responses use CREATE_NEW, force, then atomic move. The STOP response is forced
before scheduling native shutdown. An error is printed and recorded, never
reported as passing. A failed/partial directory remains for inspection.

The controller files are **not** the network feature being tested. Only the
fixture's real `SimpleChannel.send(PacketDistributor.PLAYER...)` delivers positive
snapshots. The probe never calls a decoder/handler/apply method. Reflection reads
only the known, already running fixture's public state accessors, or invokes its
native `sendTo(ServerPlayer,int,int)` endpoint. Native raw custom-payload packets
are bounded to 32 bytes for negative cases. Their caller-owned Netty buffer is
released by the native send completion callback, never before encoding finishes.

Planned observations: two-client isolation; valid zero value; application on the
actual client main thread; duplicate/gap, malformed width/trailing/range rejection;
unknown discriminator (Forge logs and ignores, not a forced disconnect); native
disconnect, zero visible state while closed, reconnection starting at sequence 1;
wrong-direction C2S disconnect by Forge before the S2C handler. Cumulative counters
do not reset across reconnect. No direct client state writes are used.

The test does not prove arbitrary packet support, C2S world mutation, login-phase
packets, custom serializers, GUI interaction, real-account authentication, WAN,
protocol compatibility between game versions, soak/load bounds, or graphics QA.
`NETWORK_CLOSED_CONTEXT` race, two simultaneously live connections in one client,
and maximum-int saturation are not claimed from this finite scenario. Unit tests
only check inert parsing/archive structure; runtime remains NOT_RUN until actual
receipt/log hashes exist. No screenshots are fabricated or implied.

Root-only serialized build:

```powershell
.\gradlew.bat -p fixtures/network-runtime-probe-1.20.1 build --no-daemon
```

After a passing build and validated converted fixture, use the separately reviewed
`scripts/Prepare-NetworkRuntime.ps1` and `scripts/Test-NetworkRuntime.ps1`. No
downloads occur in preparation. This is local test material, All Rights Reserved;
no binary redistribution permission is inferred from the user's local QA request.
