# LB-09 / LIFE-CONFIG / CLIENT_CONFIG_SCOPE

Implemented 2026-09-19 as a **private, static-only candidate**, not a seventh
conversion profile, player-facing conversion, installation authority or native
compatibility claim. The six existing grammars and public Convert/Apply are unchanged.

## Finite contract

The authored Forge 36.2.42 fixture has four Java 8 classes and fourteen methods:
a normal deferred Item registration, a plain Item subclass, a native boolean/int
ForgeConfigSpec, and a helper holding a client-startup snapshot.

The entry constructor registers the item on both physical sides. Only the exact
`FMLEnvironment.dist == Dist.CLIENT` forward branch reaches config installation.
Its non-client branch skips the entire config/helper path. The helper registers
`ModConfig.Type.CLIENT`, then an explicit NORMAL / receiveCancelled=false MOD-bus
`FMLClientSetupEvent` listener. This listener only enqueues a zero-capture Runnable.
Only that work reads the native getters and updates three private observations.

Defaults are enabled=true and factor=3 with native range 1..8. Keys, valid changed
defaults and range literals are retained and reported, not substituted with a
canned value. The existing lifecycle schema checker is reused. No COMMON/SERVER
substitution, pre-load read, client game call, reload listener or sync is invented.

## Real API audit

Exact source: Minecraft 1.16.5 / Forge 36.2.42 / EventBus 4.0.0.
Exact target: Minecraft 1.20.1 / Forge 47.4.23 / EventBus 6.2.33 / Java 17.0.12.

| Contract | Source and target evidence |
| --- | --- |
| Physical side | FMLEnvironment.dist is public static final Dist; CLIENT is a real enum field, not logical world side. |
| CLIENT loading | Source ModLoader.loadMods loads CLIENT on physical client then COMMON before COMMON_SETUP/SIDED_SETUP. Target net.minecraftforge.fml.core.ModStateProvider orders CONFIG_LOAD -> COMMON_SETUP -> SIDED_SETUP and selects FMLClientSetupEvent for CLIENT. |
| Config registration | Source registerConfig(Type,ForgeConfigSpec); target registerConfig(Type,IConfigSpec). The existing exact lifecycle descriptor operation is reused under a separate CLIENT effect proof. |
| Read timing | Target pre-load get behavior differs from the source; only a post-load enqueued startup read is admitted. |
| Queue | FMLClientSetupEvent extends ParallelDispatchEvent in both; enqueueWork(Runnable) returns CompletableFuture. No getMinecraft/parallel-thread access is admitted. |
| Storage/sync | Native CLIENT uses the client config directory and is not server synchronization. No manual filesystem or network shim. |

Mapped source archive SHA-256:
`bbe2d148f96694271c43fc2ba584463d75d9ffc490abd0469ae66b831293d376`.
Mapped target source archive:
`4bd6656a7cbe3809da68f3b709ca0f2fa186575f58538668c58ce30cb38c4932`.
These exact pinned local sources, javap declarations and production fixture
bytecode were inspected without defining or initializing mod classes.

Additional explicit development inventory inputs:
- Forge 36.2.42 launcher: `dfeab9c7d582fce5b1112e9d3038fa0e7075296e467031ce1e2c0ba9fdcf441f`.
- mergetool 1.0.9 API: `46d9d0b4a42ab220a2baa25807bc7a076c2cb51594fa6c013c595f43b92956b3`.
- mergetool 1.1.5 API: `39d9a3d9277c1402b8e7b97f917ce278241cf1796312aec85b87f19a376dd99a`.
The target version is also present in the pinned ordinary Forge installation's
library arguments; it was not selected by searching for a newer version.

The local profile is `build/client-config-20260919/mapping-profile.properties`.
It explicitly extends the existing lifecycle inventory with the declarations
above. This is developer input, not a hidden cache lookup in the engine, bundled
old Forge/Minecraft, redistributable data claim or expanded public embedded dataset.
Public mapping-data packaging and rights gates are unchanged.

Primary documentation consulted as orientation, not a replacement for pinned code:
[Forge configs](https://docs.minecraftforge.net/en/1.20.1/misc/config/) and
[physical/logical sides](https://docs.minecraftforge.net/en/1.20.1/concepts/sides/).

## Implementation and boundaries

`ClientConfigContract` independently proves role separation, the exact branch
destination, the common registry prefix/initializer, the Item-only supplier,
schema/getters, correct bus, phase and queue, and every observation effect.
Renaming owned classes does not change admission. Unknown code/dependencies abort.

`VerifiedLogicMappings.compileClientConfig` gates all extra class/member/SAM
recipes on that proof and verifies actual API declarations, access and opcodes.
Structural HIGH is recorded separately; it is not automatically promoted.
`OfflineLinkageVerifier.CallbackPolicy.CLIENT_CONFIG` re-derives the proof from
actual bytes rather than trusting an asserted policy or caller certificate.

`LogicPreservingConverter.planClientConfig` and the bounded
`LegacyEngineService.planClientConfig` share the actual mapping and conversion
pipeline. Source, target and reopened candidate proofs must agree. All owned
methods/debug information go through existing body-preservation validation.
Projection used for resource admission is never emitted. ASM, linkage/access,
complete archive ownership and safe re-open precede returning proof/hash.
No public output file is written; cancellation deletes the private temporary.

`ClientConfigArtifactAdapter` implements two additive OFFLINE metadata operations,
`logic.client-config-manifest.v1` and `logic.client-config-bindings.v1`.
It binds the existing real descriptor, dependency and pack operations explicitly.
There is no new runtime stub or executable adapter ABI. Identity/version remains
legacybridge_runtime / 0.1.1-SNAPSHOT; report and transformation schemas stay 1.

## Validation and limitations

See [artifact-bound evidence](evidence/client-config-2026-09-19.json) for commands,
counts, hashes and retained failures. Tests exercise source/target/readback,
service parity, deterministic private bytes, unchanged original/resources, renamed
classes, non-default valid schema literals, old-profile rejection, wrong-side and
early reads, invalid schema, unsupported dependencies/calls, callback/branch
mutations and cancellation during real temporary lifetime.

**NOT_RUN:** source/target clients, dedicated classloading, file correction/reload,
real phase delivery, thread observations, GUI, multiplayer, fresh installation
and offline gameplay. The oracle in the report is an expectation, not execution.

A native comparison must use fresh isolated source/target clients and inspect:
defaults; prewritten alternate config; invalid bool/int/range handling; exactly
one queued startup snapshot; unchanged snapshot after native file reload; and
dedicated-server absence of CLIENT registration/read. No test may load the
original during static analysis/conversion.

Still unsupported here: SERVER config; custom filenames/specs/validators;
arbitrary schemas/types; Loading/Reloading listeners; cache invalidation/reload
semantics; client-server sync; integrated/dedicated lifecycle composition; client
game APIs; config-driven gameplay; arbitrary external dependencies and public
promotion. No runtime equivalence or world migration is claimed.

LIFE-CONFIG is PARTIAL, not ACCEPTED. LIFE-EVENTS/COMPOSE-01 requires remain pending.
Next development slice: **LB-09 / LIFE-CONFIG / SERVER_CONFIG_SCOPE**. Audit exact
world-scoped loading/unloading and synchronization separately; start with a
small isolated fixture, without widening existing public profiles or opening a game.
