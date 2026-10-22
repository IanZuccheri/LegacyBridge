# Native food endpoint probe: Minecraft 1.16.5

Test-only Forge 36.2.42 mod, ID `legacybridge_food_probe`, version 1.0.0.
The only declared mod dependencies are Forge, Minecraft and original fixture
identity `legacybridge_food` 1.0.0. The Source instance runs the genuine original mod using Java 8 x64.
Never bundle this probe into the product or run it during analysis/conversion.

Build serially from the LegacyBridge root:

```text
gradlew -p fixtures/food-runtime-probe-1.16.5 build --no-daemon --max-workers=1
```

Artifact: `build/libs/legacybridge-food-probe-1.16.5-1.0.0.jar` inside this
project. ForgeGradle 6.0.54 uses official 1.16.5 mappings and Java 17 compiler,
emitting Java 8 bytecode. Four tests inspect the neutral bounded JSON writer
and JAR contents without invoking game or original mod classes. An explicit native
`pack.mcmeta` describes this otherwise resource-empty test mod.

## What the native probe observes

The real FMLServerStartedEvent supplies the actual server.
Its own `server.isSameThread()` must be true. The probe verifies discovered mod
version, original entrypoint/item class names, registry
`legacybridge_food:ration`, max stack size 16 and native use duration 32.

A fresh FakePlayer has a fixed test-owned UUID/name, never a real account profile
or the shared FakePlayerFactory singleton. The probe does not add an authenticated
player, copy tokens, use a client or pretend to establish multiplayer. It positions
the actor only after the test-owned chunk has reached native entity-ticking readiness.

The started event requests one native forced-chunk ticket for 0,0 and returns
without running the food/drop observations. The probe samples readiness on real
`ServerTickEvent.END` events, requiring two consecutive ready samples within 200
END ticks. Source uses the public `isEntityTickingChunk(ChunkPos)`; target uses
`areEntitiesLoaded(long)` plus `isPositionEntityTicking(BlockPos)`. Target ticks
must also identify the same server. There are no sleeps, recursive/manual world
ticks, private field writes, artificial entity lookup or fallback success. Native
neighbor generation associated with the one ticket is not claimed to load only
one chunk. A fresh empty forced-chunk set is required; no existing ticket is removed.

After observations, the probe releases its one ticket through native
`setChunkForced(0,0,false)` and requires the saved forced-chunk set to be empty
before publishing a trace. Failure also attempts that cleanup, reports any cleanup
error, and shuts down natively. Removing the ticket does not claim instant chunk
unloading or delete the disposable world. A bounded readiness timeout is a failed
probe, not a converted-mod success.

Four real use/completion cases cover survival/creative with hunger 10/20 and zero
initial saturation, as documented in the [fixture oracle](../food-item-1.16.5/README.md).
Each case selects the actual native GameType via ServerPlayer.setGameMode when a
change is needed, then checks the manager's effective type, isCreative, non-spectator
status and native instabuild/invulnerable/mayfly/mayBuild abilities. No ability or
GameType field is written directly. A cancelled or redirected transition fails
instead of silently simulating the intended mode. The drop phase returns through
the native transition to SURVIVAL.

The exact source setter returns void; the target setter returns boolean and also
returns false when the mode is already unchanged. The probe requests transitions
only when needed, asserts a successful target return and always verifies effective
postconditions. Both Forge FakePlayer implementations provide their native fake
connection whose send methods discard outgoing packets. Native player-list broadcasts
still occur, so every transition also requires the server thread and zero connected
players. The probe never replaces that connection or sends to a real player.

Native NBT food-state reading supplies controlled preconditions; no food behavior
is reimplemented. After a successful use, the native active-use stack and remaining
duration 32 must be correct before directly invoking finishUsingItem. Only a refused
full-hunger survival use skips completion. Ration count, hunger and saturation must
match the oracle exactly, with no other inventory item or oversized ration stack.

Each of three native inventory drop/pickup cycles removes two from seven before
the server player's drop call, verifies five carried plus two live dropped units,
checks `world.getEntity(id)` discovers that exact entity, then performs native pickup
after removing pickup delay. The entity must be removed and inventory restored to
seven. The actor is not instructed to collect a removed entity again. A returned
entity without actual world discovery is a failure, not a fallback success.

The implementation mirrors the small test-only observation procedure between game
versions, not any production conversion rule. There is no reflection or copying
of game code. All original fixture behavior is reached through native dispatch.

## Fresh preparation and runner

The separate scripts copy only local ordinary Forge installation libraries,
source launcher/game JARs and an already accepted isolated-test EULA. They do not
download, accept a new EULA, copy old worlds/configs/logs/mods, or launch automatically.
They refuse existing destinations, symlinks/junctions and nonempty initial
mod/home/Gradle/temp directories. Every copied file is CREATE_NEW, forced and
hash-verified. The target argument file has an exact finite allowlist of ordinary
Forge server arguments and safe local library JAR paths, not launcher arguments.

After the original, probes, public artifact and separately validated
`build/food-item-v1/converted.jar` are ready, use one fresh RunLabel, serially:

```powershell
./scripts/Prepare-FoodRuntime.ps1 -RunLabel comparison3
./scripts/Test-FoodRuntime.ps1 -Side Source -RunLabel comparison3
./scripts/Test-FoodRuntime.ps1 -Side Target -RunLabel comparison3
```

Source and target directories are
`build/food-item-v1/source-default-comparison3-server` and
`target-default-comparison3-server`. Their loopback ports are 25587/25588.
The run uses a new `food-comparison-world`; earlier P6, logic and lifecycle worlds
and results are untouched. Omit RunLabel only for the first unique default pair;
never reuse an earlier prepared/partially run directory.

The preserved historical source `comparison1` receipt predates native GameType
selection. It demonstrates endpoints under ability flags only, not real creative
or survival mode. Do not reuse or overwrite it. The enhanced runner requires the
new positive `nativeGameModeValidated` field; an old trace cannot pass this gate.

Preserved `comparison2` source passed the native-mode endpoints, but target failed
the strict actual-world lookup of a live dropped entity. A synchronous FULL chunk
request in ServerStartedEvent did not prove native entity visibility readiness.
This is a probe-environment failure, not a justified claim of food conversion
failure or success. That failed stderr and the source receipt remain unchanged.
The next fresh pair additionally requires `nativeChunkReadinessValidated=true`,
`nativeReadinessPhase=SERVER_END` and `nativeForcedChunkReleased=true`.

## Trace contract and limits

Success creates a new forced `food-trace.json` and prints
`LEGACYBRIDGE_FOOD_BEHAVIOR_OK`. Failure prints the explicit FAILED marker and
real exception, requests orderly `server.halt(false)`, and propagates failure.
Existing results are never overwritten. The supervising runner requires the
marker, no failure marker, correct bounded trace, process exit and clean save;
it records hashes for exact original/converted/public/probe artifacts, prepared
libraries receipt, stdout/stderr and trace. It also rechecks the original and
installed JARs. A timeout requests native `stop`; it is not reported as success.

The four consumption arrays have fixed layout:

```text
[initialFood, initialSaturationMilli, initialStack, consumesFlag,
 finalFood, finalSaturationMilli, finalStack]
```

Saturation is asserted against its exact expected float before reporting thousandths.
`nativeGameModes` records `[0,0,1,1]` for the four cases and `dropGameMode` records 0
after the native return to survival. These are reads of the effective native manager,
not constants used to impersonate player state. `nativeGameModeValidated=true`
appears only after all mode, ability and food endpoint assertions have passed.
No time/path/environment value enters the compared trace. Environment/version
identity belongs in separate stdout and receipts.

Actual readiness wait counts (2..200 END ticks) are recorded in stdout and the
receipt, not the byte-compared trace because native generation timing can differ.
The runner rejects missing/duplicate success markers, out-of-bound wait counts,
missing readiness/cleanup assertions and any failed exact dropped-entity lookup.
Native world ticks used for chunk preparation happen before creating the test
FakePlayer; they do not validate a 32-tick eating sequence.

FakePlayer.tick is a no-op: the probe never simulates a complete duration.
`fullTickSequenceValidated=false` is mandatory. These tests observe native use/start
and completion endpoints, not the whole LivingEntity tick/Forge finish-use event
sequence. Direct native pickup is not automatic collision timing. Target native
GameEvent.EAT is not falsely equated to an old-version event.

Client rendering/eating animation, effects/remainders, full-inventory edge cases,
death drops, persistence, arbitrary anti-duplication, client/server synchronization
and multiplayer remain unvalidated. The server may attempt native update/key
network requests; `online-mode=false` is not an offline-game test. No client or
network isolation is claimed. Source code and test counts alone do not establish
a passed build, conversion or native runtime gate. Keep each claim tied to its
actual receipt and artifact hash; these probes remain All Rights Reserved local QA.
