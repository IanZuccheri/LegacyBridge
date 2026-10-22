# LB-09 / LIFE-CONFIG / SERVER_CONFIG_SCOPE

Implemented as a private static candidate, not a seventh public profile or
installation authorization. All six existing grammars and public Convert/Apply
remain unchanged. LIFE-CONFIG remains PARTIAL. No game process is launched.

## Finite contract and observed effects

The original Forge fixture contains four Java 8 classes / sixteen methods:
native deferred item, plain Item subclass, boolean/int ForgeConfigSpec, and
a logical-server snapshot with five private static observations.

The mod registers SERVER configuration on BOTH physical sides, without a Dist
guard. Integrated servers run on the physical client too. An explicit Forge-bus
NORMAL / receiveCancelled=false listener reads the spec on ServerStarted; a
second listener clears active/enabled/factor on ServerStopped. Both handlers are
owned, static, zero-capture Consumers; no event payload, world access, queue,
network operation or file operation is introduced.

The same native boolean default and integer default/range checker used by the
existing lifecycle/CLIENT paths is reused, not copied. Valid changed defaults
and owned class names are not hardcoded to this fixture. Five pure getters make
the retained effects observable in future isolated native comparisons. Startup
failure may stop without starting, so starts==stops is not a universal invariant.

## Exact API audit

Source: Minecraft 1.16.5 / Forge 36.2.42 / EventBus 4.0.0.
Target: Minecraft 1.20.1 / Forge 47.4.23 / EventBus 6.2.33 / Java 17.0.12.
Pinned source archives and production javap/ASM bytecode were inspected; original
mod classes were never defined, initialized or executed.

| Source | Target | Exact recipe |
| --- | --- | --- |
| ModLoadingContext.registerConfig(Type, ForgeConfigSpec) | registerConfig(Type, IConfigSpec) | Existing lifecycle descriptor operation; newly proved SERVER context. |
| ModConfig.Type.SERVER | Same field | Same native scope, not an alias for CLIENT/COMMON. |
| fml.event.server.FMLServerStartedEvent | event.server.ServerStartedEvent | Exact class/descriptor remap behind whole-graph phase proof. |
| fml.event.server.FMLServerStoppedEvent | event.server.ServerStoppedEvent | Exact remap with mandatory snapshot clearing. |
| MinecraftForge.EVENT_BUS and explicit IEventBus.addListener | Same native APIs | Same bus, priority, cancellation and synchronous server phase. |

In both pinned ServerLifecycleHooks implementations, handleServerAboutToStart
loads SERVER configs from the world's serverconfig path before posting that
event. handleServerStarted posts the started event after successful initialization.
handleServerStopped posts the stopped event BEFORE unloading SERVER configs.
MinecraftServer calls these hooks in its native server run path; no scheduling
shim is added. A stopped callback is not a config-unloading callback.

Source ModConfig has Loading and Reloading, **no Unloading**. Target config
tracking adds an unloading event and changes watcher teardown ordering. These
config callbacks and failed-start lifecycle differences are excluded, not
silently mapped. Source ConfigTracker sets the spec before Loading; the target
uses IConfigSpec.acceptConfig. Native getter behavior before load differs:
all reads in this candidate occur after started, never during construction.

Native initial SERVER config synchronization remains the platform's separate
connection contract. Source ConfigTracker syncConfigs/receiveSyncedConfig and
target network.ConfigSync + ModConfig.acceptSyncedConfig transfer config file
data, not arbitrary mod observation fields. Remote receipt raises Reloading;
integrated-server handling differs from remote receipt. We do not translate wire
protocols, invent live sync, or call persistence client synchronization.
No native synchronization behavior has been tested in this turn.

Source mapped source SHA-256:
`bbe2d148f96694271c43fc2ba584463d75d9ffc490abd0469ae66b831293d376`.
Target mapped source SHA-256:
`4bd6656a7cbe3809da68f3b709ca0f2fa186575f58538668c58ce30cb38c4932`.
Target fmlcore source SHA-256:
`fc6df13c54c211ea9c0220ec76a2ebe1e573187112467cfc1397a25fee191ec1`.

[Forge config documentation](https://docs.minecraftforge.net/en/1.20.1/misc/config/)
was consulted as orientation; pinned implementations govern the recipes.
Development mapping/API inputs are explicitly supplied by
`build/server-config-20260919/mapping-profile.properties` (the existing lifecycle
inventory). No engine cache search, old game embedding, additional player
download or redistribution clearance is implied. Public embedded data is unchanged.

## Pipeline and boundaries

ServerConfigContract proves the whole graph, registration prefix, callbacks,
schema, pure getters and cleanup. VerifiedLogicMappings.compileServerConfig
checks actual declarations, access, opcode, SAM and descriptor operations.
Generic structural confidence is still reported separately; it is not promoted
to VERIFIED automatically.

OfflineLinkageVerifier.SERVER_CONFIG independently derives the proof from bytes.
Source, transformed target and reopened private JAR proofs must agree. Existing
ASM/body/debug preservation, resource schemas, dependency closure, byte ownership,
CRC/reopen, bounded-worker and cancellation checks are shared. The public typed
planServerConfig API returns proof/hash only, with no exported bytes.

ServerConfigArtifactAdapter adds two implemented OFFLINE metadata operations:
logic.server-config-manifest.v1 and logic.server-config-bindings.v1.
No executable runtime shim is added. Runtime identity/version and all previous
adapter descriptors remain unchanged. Compatibility/transformation schemas stay 1.
The shared CLIENT token/registry-envelope helpers only gain package visibility;
their rules and the CLIENT profile stay unchanged.

## Remaining acceptance gates

Private static proof is NOT runtime behavior proof. Integrated and dedicated
server starts, custom defaults/invalid-file correction, load/stop/second-world
isolation, client/dedicated classloading, remote initial sync, multiplayer,
reconnection and failed-start behavior have NOT_RUN status. No startup/reload,
handshake or behavioral comparison is claimed from test counts.

Unsupported: arbitrary config schemas, custom filenames/files, config event
Loading/Reloading/Unloading callbacks, reload-driven live snapshots, client reads,
custom sync, world/player mutation, concurrent logical servers and composition
with other private candidates. Unknown code/dependencies block the entire
candidate, leaving no output and the original intact.

Next headless slice: **CONFIG_RELOAD_SCOPE**, within LB-09/LIFE-CONFIG. Audit
source/target config event ownership, spec/type filtering, repeated load/reload,
watcher/network thread and initial-connection differences before implementing a
finite fixture. Do not emulate the target-only Unloading event in the source or
treat reload as restart. Native gates and predecessor requirements remain pending.

## Artifact-bound headless evidence

[Ledger](evidence/server-config-2026-09-19.json): 463 focused cases, a 47-case
regression run (46 passed, one omitted opt-in), then 61 final SERVER/adapter
cases all passed, including the previously skipped case. These overlap:
**510 distinct engine cases passed, 54 new SERVER cases**. They are not counts of
compatible mods. Contract: 75 passed / two existing Windows symlink privilege
skips. All-module assemble, final public/runtime JAR audits, engine consumer
boundary and installed CLI smoke passed.

Public JAR SHA-256:
`42eb62ef337fd4bc08b7aebb6f5d8e634eec682e378172c102e8a2d791e906f4`.
Original fixture:
`cc7cf3d985a64844f86c55b584aa5fb8236e246ea6a9ed1b882bf6b8f1fde3d1`.
Private candidate:
`ec7415c85b9d264ee7a1c731dd2b8a5ef88c7e71ed60f64b58a5180dc0cd4e34`.
The final archive contains no fixture/harness/game-source/private inventory.
All 40 earlier adapter descriptors and public UI/install/data/JiJ bytes stayed
unchanged. Native and full unfiltered validation remains NOT_RUN.
