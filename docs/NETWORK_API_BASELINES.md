# Networking: exact API and rejection baseline

Audit date: 2026-09-06. Source is Minecraft **1.16.5 / Forge 36.2.42**;
target is Minecraft **1.20.1 / Forge 47.4.23**, Java 17. This document is a
read-only source audit and a proposed finite snapshot contract, not evidence that
the networking profile, a particular mod, or multiplayer behavior already works.
No original mod class was loaded or executed, and no build or game was run by
this audit. Implementation and runtime evidence must be reported separately.

## Provenance

The following merged, patched ForgeGradle development-source archives were read
as ZIP data. Paths are relative to the local
`minecraft_user_repo/net/minecraftforge/forge` audit cache. These sources are not
redistributable mapping data and must not be embedded in the product. Existing
[portable-data](PORTABLE_MAPPING_DATA.md) and
[packaging-rights](PACKAGING_RIGHTS.md) requirements remain unchanged.

| Exact archive | SHA-256 |
| --- | --- |
| `1.16.5-36.2.42_mapped_official_1.16.5/forge-1.16.5-36.2.42_mapped_official_1.16.5-sources.jar` | `bbe2d148f96694271c43fc2ba584463d75d9ffc490abd0469ae66b831293d376` |
| `1.20.1-47.4.23_mapped_official_1.20.1/forge-1.20.1-47.4.23_mapped_official_1.20.1-sources.jar` | `4bd6656a7cbe3809da68f3b709ca0f2fa186575f58538668c58ce30cb38c4932` |
| `net.minecraftforge:eventbus:4.0.0:sources` | `88af63462fd2880bb12ed3a9c8fa98cff0e0b2195dd60001175f717e12a03f43` |
| `net.minecraftforge:eventbus:6.2.33:sources` | `6973f199d644a1ba81d8f703c8599ddff07448f2f62c5c0c664d765c3ead16a6` |

EventBus versions also match the installed source/target validation libraries.
Line references below refer to these exact local archives, not a newer branch.
Primary upstream corroboration: [source indexed codec](https://github.com/MinecraftForge/MinecraftForge/blob/1.16.x/src/main/java/net/minecraftforge/fml/network/simple/IndexedMessageCodec.java),
[target indexed codec](https://github.com/MinecraftForge/MinecraftForge/blob/1.20.1/src/main/java/net/minecraftforge/network/simple/IndexedMessageCodec.java),
[source client patch](https://github.com/MinecraftForge/MinecraftForge/blob/1.16.x/patches/minecraft/net/minecraft/client/network/play/ClientPlayNetHandler.java.patch),
[target client patch](https://github.com/MinecraftForge/MinecraftForge/blob/1.20.1/patches/minecraft/net/minecraft/client/multiplayer/ClientPacketListener.java.patch).
Branch URLs are not immutable provenance and do not replace archive hashes.
The [SimpleImpl guide](https://docs.minecraftforge.net/en/1.20.1/networking/simpleimpl/)
is useful context, but its current examples must not override pinned signatures.

## Owners and exact relevant signatures

Forge networking moves from `net.minecraftforge.fml.network` to
`net.minecraftforge.network`; `simple` remains a subpackage. Source method names
below are the development namespace. Production recipes must obtain actual
descriptors/names from the verified inventories, not copy this table as a global
rename permission.

| Source | Target | Meaning in the proposed slice |
| --- | --- | --- |
| `net.minecraft.network.PacketBuffer` | `net.minecraft.network.FriendlyByteBuf` | Buffer wrapper; no permission for its whole API |
| `net.minecraft.network.NetworkManager` | `net.minecraft.network.Connection` | Connection identity and explicit disconnect only |
| `net.minecraft.entity.player.ServerPlayerEntity` | `net.minecraft.server.level.ServerPlayer` | Explicit recipient, not permission to mutate players |
| `net.minecraft.util.ResourceLocation` | `net.minecraft.resources.ResourceLocation` | Preserve the owned channel identifier |
| `net.minecraft.util.text.StringTextComponent(String)` | `net.minecraft.network.chat.Component.literal(String)` | Constructor-to-static-factory operation, not a rename |
| `NetworkRegistry.newSimpleChannel(ResourceLocation, Supplier<String>, Predicate<String>, Predicate<String>)` | Same generic signature with target owners | Both return their `SimpleChannel` |
| `SimpleChannel.registerMessage(int, Class<MSG>, BiConsumer<MSG, PacketBuffer>, Function<PacketBuffer, MSG>, BiConsumer<MSG, Supplier<Context>>, Optional<NetworkDirection>)` | Same generic signature with `FriendlyByteBuf` | Both return `IndexedMessageCodec.MessageHandler<MSG>`, not `void` |
| `SimpleChannel.send(PacketDistributor.PacketTarget, MSG)` | Same generic signature with target owners | Void; target direction comes from the distributor |
| `PacketDistributor.PLAYER.with(Supplier<ServerPlayerEntity>)` | `PacketDistributor.PLAYER.with(Supplier<ServerPlayer>)` | **Supplier in both pins**, not `.with(player)` |
| `Context.enqueueWork(Runnable)` | Same | `CompletableFuture<Void>` |
| `Context.setPacketHandled(boolean)` | Same | Void, transport-handled flag only |
| `Context.getDirection()` | Same | Actual `NetworkDirection` |
| `Context.getNetworkManager()` | Same method name returning `Connection` | Do not invent `Context.getConnection()` |
| `NetworkManager.isConnected()` | `Connection.isConnected()` | Current channel-open observation, not proof of active world/session ownership |
| `NetworkManager.disconnect(ITextComponent)` | `Connection.disconnect(Component)` | Native explicit close; not called by the first fixture, whose content-error policy is counted rejection |

`NetworkRegistry.newSimpleChannel` is at line 102 in both archives. Registration
of the same channel name twice, or after `NetworkRegistry.lock()`, throws
`IllegalArgumentException` (`createInstance`: source 132, target 131).
Creating a channel invokes its protocol supplier at runtime
(`NetworkInstance`: source 37, target 33); the converter must only inspect its
bytecode and must never evaluate the original supplier.

`registerMessage` has **both** five- and six-argument overloads
(`SimpleChannel`: source 74/78, target 71/75). Five arguments delegate to
`Optional.empty()`: sending only S2C in observed code does not make this safe
against an inbound C2S packet. Require the six-argument overload with a statically
proven `Optional.of(NetworkDirection.PLAY_TO_CLIENT)` for this slice.

### Protocol and builder incompatibilities

Both version predicates receive strings. A constant protocol `"1"` with both
predicates exactly `"1"::equals` accepts that protocol only; missing-channel and
vanilla sentinel strings are rejected. Preserve the constant and both checks.
Do not replace broad/unknown predicates with exact predicates and call that
semantic preservation. Such inputs require a reviewed migration or rejection.

`NetworkRegistry.ABSENT` is **String in 36.2.42**, but
**`ServerStatusPing.ChannelData` in 47.4.23** (source 47, target 48).
Target internals pass `ABSENT.version()` to the predicates. `ACCEPTVANILLA`
remains a String. A direct `GETSTATIC ABSENT` is not a package-only mapping.
`acceptMissingOr(...)` deliberately accepts missing/vanilla peers and is outside
the exact-required-channel contract.

`messageBuilder(Class, int)` and `(Class, int, NetworkDirection)` exist in both
pins. Source `MessageBuilder.consumer(BiConsumer)` corresponds in scheduling to
target **`consumerNetworkThread(BiConsumer)`**, not `consumerMainThread`.
Target `consumerMainThread` additionally calls `enqueueWork` and marks handled
(`SimpleChannel`: target 269). The boolean-returning custom consumer overload
also changes its name to `consumerNetworkThread`.
Prefer the six-argument `registerMessage` vertical slice first; login builders,
login index handlers, `markAsLoginPacket`, `noResponse` and replies are excluded.
Their existence is not permission to call reflective `Class.newInstance` used by
the native login machinery during conversion.

## Codec and direction behavior

The actual `IndexedMessageCodec` uses **one unsigned discriminator byte**, not a
VarInt. Registration stores `index & 0xff`; encoding writes that same masked
byte. Duplicate message classes/IDs overwrite internal map entries rather than
being rejected there. The profile must independently require a unique constant
ID in `0..255`, unique message class and exactly one registration. ID 0 is the
proposed first fixture. Packet/channel IDs must be preserved, not renumbered.

Both buffer wrappers declare these descriptors:

```text
readableBytes()I
readInt()I
writeInt(I)Lio/netty/buffer/ByteBuf;
```

The nested `IndexedMessageCodec.MessageHandler` returned by public
`registerMessage` is package-private in both pinned compiled APIs. The authored
fixture immediately discards this result with POP; it never casts, stores,
constructs or invokes that inaccessible type. This legal opaque-return use needs
a separate narrow offline-linkage recipe, not falsified public access flags or
a blanket relaxation of descriptor/class access validation.

Source `PacketBuffer`: 464, 788, 904; target `FriendlyByteBuf`: 912, 1236, 1352.
The writes return **Netty ByteBuf**, not the Minecraft wrapper. They delegate
to the underlying buffer. A fixed-width integer advances four bytes; too few
readable bytes throws. See the [Netty ByteBuf contract](https://netty.io/4.1/api/io/netty/buffer/ByteBuf.html).
The slice must forbid swapped byte order, custom buffer creation, slices,
retention, arrays, strings, NBT, variable-sized loops and registry-dependent
codecs. Ordinary channel-created buffers use the normal wire byte order.

The currently proposed snapshot has two fixed-width signed integers:
positive `sequence`, then `value` in `0..1000`. Its body is **8 bytes**, and its
SimpleChannel payload is **9 bytes including the ID**. This is a proposal for a
finite fixture, not general support for arbitrary integers/DTOs/serializers.
The decoder must check **exactly 8 remaining bytes**, not merely at least 8.

| Situation | Actual pinned Forge behavior | Required interpretation/gate |
| --- | --- | --- |
| Known ID, wrong direction with explicit expected direction | `NetworkHooks.validatePacketDirection` disconnects and throws before decoding | Reject before any handler effect; exercise the real opposite-direction packet |
| Known ID, no expected direction | Direction check accepts the observed direction | Five-argument registration is not eligible |
| Unknown ID | Codec logs `Received invalid discriminator byte` and returns; no decoder/handler and no explicit codec disconnect | Observable native rejection, **not** a claim of disconnection; assert unchanged state and log evidence |
| Truncated/extra body for a known ID | Native codec performs no whole-body length check | Implement/require exact-width validation; never accept a valid prefix |
| Decoder returns null | Optional mapping skips the consumer | Not an acceptable error protocol for this slice |
| Decoder/consumer throws | Codec does not catch it; event bus rethrows | Do not assume the same disconnect policy on both versions |
| Empty, non-null outer payload | Source attempts `readUnsignedByte`; target logs and can mark handled without decoding | Difference must remain explicit; decoder cannot validate a missing discriminator |
| Unknown outbound message class | Encoder logs and throws `IllegalArgumentException` | Never dynamically send unregistered DTO types |

`NetworkHooks.validatePacketDirection`: source 88, target 96. Its condition is
`packetDirection != expectedDirection.orElse(packetDirection)`; the disconnect
occurs before `IllegalStateException`. `PLAY_TO_CLIENT` originates on logical
SERVER and is received on logical CLIENT in both `NetworkDirection` enums.
`PacketDistributor.PLAYER` is explicitly `PLAY_TO_CLIENT`; its `.with(Supplier)`
obtains the recipient when sending (`PacketDistributor`: target 187/202).
`sendToServer`, `SERVER`, replies, login packets, tracking entities/chunks,
dimension/all-player broadcasts and arbitrary `sendTo` directions are excluded.

For raw negative probes, target constructors are
`ClientboundCustomPayloadPacket(ResourceLocation, FriendlyByteBuf)` and
`ServerboundCustomPayloadPacket(ResourceLocation, FriendlyByteBuf)`.
Source equivalents are `SCustomPayloadPlayPacket(ResourceLocation, PacketBuffer)`
and `CCustomPayloadPacket(ResourceLocation, PacketBuffer)`; the latter source
constructor is client-only. Native inbound outer payload limits are 1,048,576
bytes S2C and 32,767 bytes C2S, **not** the proposed nine-byte message limit.
A ten-byte packet already violates the fixture contract while being perfectly
legal under the outer native limit. Probes must distinguish these two checks.

## Threading, explicit failures and session lifetime

Do not infer a universal networking thread rule from a newer documentation page.
The exact source `ClientPlayNetHandler.handleCustomPayload:1687` calls
`PacketThreadUtil.ensureRunningOnSameThread` before the Forge hook at 1905;
source `ServerPlayNetHandler:1411` does likewise. Target
`ClientPacketListener:1862` invokes the Forge hook **before** its native
thread switch; target `ServerGamePacketListenerImpl:1680` directly invokes it.
This is a meaningful patched call-site difference.

`Context.enqueueWork` has the same algorithm in both pins (source 194, target
205): select the logical reception-side work queue, submit asynchronously when
off-thread, or run immediately and return an already-completed future when
on-thread. The shared effect envelope must therefore require pure bounded
decoding and explicit enqueuing before a client snapshot is changed. Marking
handled is not an acknowledgement, task completion or proof of a state change.
Never enqueue the borrowed buffer itself; decode immutable primitive data first.

`NetworkInstance.handleError` is empty in both versions, but this **does not
swallow exceptions**: exact EventBus 4.0.0 `post:305` and 6.2.33 `post:314` call
the handler and rethrow. Downstream behavior differs: the source main work-loop
`ThreadTaskExecutor.doRunTask:134` catches Exceptions and logs a fatal task error;
target Netty `Connection.exceptionCaught:125` attempts to disconnect. Errors in
an enqueued `CompletableFuture` are a separate path. A throwing decoder is not
evidence of a uniform, explicit malformed-packet disconnect policy.

The original [snapshot fixture](../fixtures/network-snapshot-1.16.5/README.md)
uses an immutable invalid DTO sentinel when width is wrong, followed by an
explicitly enqueued **counted, logged rejection without disconnecting**. Valid
width with an invalid sequence/value range also follows that rejection path.
The sentinel is never applied as a snapshot and is not a fictional adapter
result; it is explicit original fixture behavior. Its preservation depends on
closed shape validation and negative native execution, not on pretending that
Forge implements that policy itself. Counter increments saturate at
`Integer.MAX_VALUE`; 20 reason lines and one rate-limit notice are emitted per
bound session. Native unknown-ID logging is not bounded by this fixture.

`ITextComponent.nullToEmpty(String)` and `Component.nullToEmpty(String)` do exist
(source 108, target 137), but the source declaration is `@OnlyIn(Dist.CLIENT)`.
The first common-class fixture deliberately avoids it and the constructor-to-
factory text migration. Wrong-direction disconnection remains native Forge
behavior; body errors are not falsely reported as disconnects.

`Context.getSender()` is source `ServerPlayerEntity` / target `ServerPlayer`,
and returns a sender only when the listener is the server play listener. It is
nullable and normally null for S2C; it is not a way to obtain the client player.
The proposed handler must not call it or mutate world/player/capability state.

A snapshot cache must also address queued work across disconnect/reconnect.
`isConnected()` alone is not an active-session identity check. A proposed
bounded, common-class-only fixture may retain one connection identity, refuse
work from a different still-live connection, admit a replacement only after the
old connection is closed and the new sequence starts at 1, and expose no cached
value while its connection is closed. Check connection state again **inside**
the queued task. Every subsequent sequence must be **exactly** `lastSequence + 1`,
with a separate exhausted-sequence guard to prevent integer overflow. Gaps,
duplicates and older sequence numbers do not alter state; diagnostics are
bounded. This requires real relog testing and is not a
generic session adapter or authentication/replay-protection guarantee.

If explicit client lifecycle events are later needed, actual types are source
`ClientPlayerNetworkEvent.LoggedInEvent` / `LoggedOutEvent` and target
`LoggingIn` / `LoggingOut`. Source accessor is `getNetworkManager`; target is
`getConnection`. Logout may also fire while creating an integrated server, and
its player/connection may be null. These are client-only event integrations,
not safe common-class imports or automatically admitted mappings.

## Acceptance boundaries for the proposed vertical slice

Static gates must prove the channel name/protocol, unique registration, explicit
direction, exact fixed codec, immutable DTO, admissible captured values and every
effect in the handler/send path. Unknown fields, buffer operations, branches to
C2S/world state, unknown predicates or unproved method handles are `MANUAL` or
`UNSUPPORTED`, not retained as unknown legacy code. Preserve source bodies only
where exact mapping/implemented operations and offline linkage prove the finite
contract. This audit does not itself add a mapping or adapter.

Runtime validation needs a real matching client and dedicated server, not just
FakePlayer: its test connection is not a normal player network connection.
Separate evidence is needed for handshake success/mismatch, actual S2C delivery,
main-thread handling, per-connection order, disconnect/relog reset, and malformed
width/range, unknown ID and wrong-direction rejection. Capture source and target
results against exact original/converted/public hashes. Static codec round trips,
bytecode validation, server startup, and screenshots alone do not prove these.

Queue exhaustion, adversarial packet floods, cross-version client/server wire
interoperability, variable payloads, C2S authority, gameplay replication,
multi-channel mods, login protocol changes, entity tracking and generalized
networking conversion remain outside this audit's proposed contract.
