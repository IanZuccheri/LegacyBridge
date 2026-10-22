# LB-09 integrated finite lifecycle: implementation closeout

This delivery completes the selected **CONFIG_RELOAD_SCOPE + TYPED_COMMAND_TREE**
implementation and composes them with the existing late registry and owned-event
contracts. It is NOT completion of all Forge lifecycle patterns. LB-09 acceptance
remains PARTIAL: native original/target comparison and prerequisite acceptance
are not replaced by headless checks.

## Implemented boundary

A new private `planLifecycleSuite` service uses the shared bounded worker,
cancellation, explicit API inputs, verified mappings, ASM and offline linkage.
It admits the whole seven-class graph, derives proof independently before and
after remapping and after serialization/readback, then returns proof/hash only.
Every original class/method remains. The admission-only event/registration
projection is never emitted or executed. Unknown effects block the whole candidate.

Included together:

- Native DeferredRegister item and queued late RegistryObject.get.
- MOD common setup; Forge owned cancelable event with explicit HIGH/LOW listeners,
  receiveCancelled and original posting result/trace.
- Native COMMON boolean/int spec, defaults and range validation.
- Loading and Reloading on MOD bus, NORMAL/non-cancelled; exact owned spec identity
  AND COMMON type filters before any reads or writes.
- Separate initial-load/reload counters and volatile primitive observations.
- Default NORMAL/non-cancelled Forge RegisterCommandsEvent subscriber, matching
  modId, both physical sides; a fresh tree on each callback.
- Two subcommands: bounded integer (non-negative, max <= 1,000,000) and boolean;
  native permission levels 1..4, linked handlers, current config read per integer
  execution, owned counters/result only. Command words/range/names are not tied
  to the fixture's class names.

CLIENT startup and SERVER world-start/stop candidates remain independent. Their
previous grammars are unchanged. The suite does not claim CLIENT/SERVER reload,
remote config synchronization, Unloading, world writes, cached dispatchers,
arbitrary listeners, result events, third-party handlers or command effects.

## Real pinned API audit

Source: Minecraft 1.16.5 / Forge 36.2.42 / EventBus 4.0.0 / Brigadier 1.0.17.
Target: Minecraft 1.20.1 / Forge 47.4.23 / EventBus 6.2.33 / Brigadier 1.1.8.

Read the actual pinned source archives / javap bytecode, not similarly named
methods guessed from documentation:

| Artifact | SHA-256 |
| --- | --- |
| Forge source mapped sources | bbe2d148f96694271c43fc2ba584463d75d9ffc490abd0469ae66b831293d376 |
| Forge target mapped sources | 4bd6656a7cbe3809da68f3b709ca0f2fa186575f58538668c58ce30cb38c4932 |
| Target fmlcore sources | fc6df13c54c211ea9c0220ec76a2ebe1e573187112467cfc1397a25fee191ec1 |
| Brigadier 1.0.17 binary | 296b69b63f3d2a2092bf94fa12add4deb89e7d1f977157f7ee1b6b6d0fd52a58 |
| Brigadier 1.1.8 binary | cf65afa612fffbe4dc976115dac0312f48b9c84b3ddaf58bec8018a9454440d7 |

Local source archive locations are development inputs, not player dependencies.

Source ModConfig.Loading/Reloading inherit ModConfig.ModConfigEvent.getConfig.
Target events live in fml/event/config/ModConfigEvent. The compiler checks the
exact declarations through the respective real ancestors, never arbitrary
inheritance. The native events remain on the MOD bus.
[Primary orientation](https://docs.minecraftforge.net/en/1.20.1/misc/config/).

ModConfig.getSpec returns ForgeConfigSpec in source and IConfigSpec in target.
The new implemented OFFLINE `logic.config-spec-identity.v1` operation changes
only this exact call descriptor after whole-graph proof. It is not a global
ForgeConfigSpec alias, adapter stub, or HIGH-to-VERIFIED promotion.
Prior lifecycle descriptors remain untouched.

Both pinned ConfigFileTypeHandler.ConfigWatcher implementations load/correct
the native file, invoke spec.afterReload, and dispatch Reloading. No invented
server-main-thread dispatch is added: callbacks may run on the file watcher.
Volatile fields retain visibility per field; an atomic snapshot, counter
transaction or serial ordering of independently concurrent reloads is not
claimed. The callbacks only read native values and change owned primitives.
Persistence, reload, restart and client synchronization remain separate.

Both BoolArgumentType versions use native StringReader.readBoolean and typed
CommandContext.getArgument. Their suggestions differ internally (target cached
lowercase); suggestions/localization are not included in the equivalence claim.
Existing command literal/argument/permission recipes are composed from exact
mapping/API inventories, not hand-written obfuscated names.

## Validation and residual obligations

See [artifact-bound ledger](evidence/lifecycle-suite-2026-09-19.json). Static
source/target/readback, ASM/access/linkage and byte ownership are distinct from
game startup. API-only Brigadier probes use isolated JVMs containing only the
owned probe and the explicitly supplied Brigadier JAR: not original mod execution,
not Forge command registration, not evidence for console versus real players.

Pending before LB-09 acceptance or public promotion:

1. Real original/target lifecycle execution, config edit + watcher reload,
   unrelated-spec filtering, invalid-file handling and setup ordering.
2. Real dedicated server console/player permission and command execution,
   dispatcher recreation, side/thread checks, disconnect/reload and composition.
3. Existing CLIENT/SERVER native gates, COMPOSE-01 and DEPS-COHORT prerequisites.
4. Patterns outside the finite implementations remain UNSUPPORTED/MANUAL until
   separately implemented and qualified; this does not erase contractual scope.

No game launch, original code execution during conversion, public profile,
Apply change, corpus acquisition, license change or publication is authorized
by this implementation closeout. Runtime identity/ABI remains unchanged.
