# Original S2C snapshot fixture

This standalone Forge **1.16.5-36.2.42** project compiles Java 8 bytecode using
the pinned Java 17 toolchain and ForgeGradle 6.0.54. It contains two original
classes and no copied platform classes, assets, library JARs or runtime harness.
It is not a general networking compatibility example or a published mod.

Identity: `legacybridge_network`, version `1.0.0`, channel
`legacybridge_network:snapshot`, protocol exactly `1` required on both peers.
The mod constructor registers ID 0 once with six-argument `registerMessage` and
`Optional.of(PLAY_TO_CLIENT)`. It registers no items, blocks, events or C2S messages.

The wire payload is one discriminator byte followed by two signed four-byte
integers: positive sequence and value in `0..1000`. The body is **exactly eight
bytes**, total nine; truncated/extra bodies produce an immutable invalid DTO.
The native handler enqueues primitive-data processing and marks the packet
handled. Invalid DTOs cannot become snapshots. Content errors log explicit
codes and increment the saturating rejection counter; **they do not disconnect**.
This does not normalize native wrong-direction behavior (Forge disconnects) or
unknown-ID behavior (Forge logs and does not invoke the DTO decoder/handler).

Public static API on `dev.legacybridge.fixture.network.NetworkSnapshot`:

- `sendTo(ServerPlayerEntity, int sequence, int value)`: real server-thread send
  through `PacketDistributor.PLAYER`; the converted descriptor uses `ServerPlayer`.
- `sequence()` / `value()`: client-main-thread snapshot reads; both return zero
  if no connection is bound or its native channel is closed. Sequence zero means
  unset, even though value zero is also a valid received value.
- `accepted()` / `rejected()`: cumulative, saturating process-lifetime counters,
  not reset on reconnect. Native rejections before the handler are not counted.
- `lastThreadId()`: thread ID of the last valid application, zero initially.
  QA must compare it to the actual Minecraft main thread, not to an assumed ID.

Processing checks native connection openness inside the enqueued task. A new
connection is bound only when the previous connection is absent/closed and its
first sequence is 1. Another still-live connection cannot replace the bound one.
Every next sequence must be exactly the previous one plus one; gaps, duplicates,
older values and exhausted sequence numbers are rejected without advancing.
The snapshot values reset when a new connection is bound. A closed connection's
cached values are not exposed, although its bounded identity remains retained
until replacement. This is not a general session/authentication framework.

Handler diagnostics emit at most 20 reason lines and one rate-limit notice per
bound session; subsequent rejections remain visible in `rejected()`. Before
initial binding they share the initial bounded budget. Native Forge logging for
unknown discriminators is outside this limit. No flood-resistance or queue-bound
claim is made. No player/world/capability state is accessed or changed.

The five artifact tests read the reobfuscated JAR as ZIP/ASM data only. They do
not reference, initialize or execute its classes. Passing them is not a network
round-trip or runtime proof. A separate authorized source/target native test must
exercise a dedicated server, real clients, valid order, rejected gaps/duplicates,
wrong direction, malformed bodies, unknown IDs, disconnect/reconnect and actual
thread identity against exact artifact hashes. See
[the audited API baseline](../../docs/NETWORK_API_BASELINES.md).
