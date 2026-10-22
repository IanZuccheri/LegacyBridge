# Original lifecycle/config/command fixture

This is a separate, project-authored Forge **36.2.42 / Minecraft 1.16.5** fixture,
not a rewrite of the existing `simple-content-v1` or logic-preservation fixture.
ForgeGradle **6.0.54** uses official 1.16.5 mappings and a Java 17 compiler with
`--release 8`; `reobfJar` produces the actual Java 8 production-SRG input.
No game/Forge/Brigadier classes, libraries, private data or runtime probe are
bundled. Local work is authorized by the project task; the repository's All Rights
Reserved license does not grant publication of this fixture binary.

## Finite fixture contract

The four owned classes remain ordinary original mod code:

- `LifecycleContent`: `@Mod("legacybridge_lifecycle")`, one DeferredRegister,
  one direct item constructor supplier, COMMON config registration, one explicit
  class-token mod-bus listener, and deferred common-setup observations.
- `LifecycleItem`: plain Item subclass, stack size 16, no overridden native hook.
- `LifecycleConfig`: boolean `enabled=true`, integer `factor=3` in `[1,8]`, native
  ForgeConfigSpec only. It does not read a value while building/registering the spec.
- `LifecycleCommands`: static Forge-bus subscriber, one native registration event
  and `/bridgeprobe <value>` with integer bounds `[0,100]`, permission level 2.
  The command returns `factor * value` when enabled and 0 when disabled.

Registry identity is `legacybridge_lifecycle:lifecycle_token`. The owned model
references the installed vanilla iron-ingot texture; there is no copied PNG.
English and Brazilian Portuguese translations are included. No recipe, creative
tab, GUI, chat-component output, player modification or world modification is added.

The entrypoint first attaches ITEMS using a fresh FML mod-bus lookup, then registers
the config, then uses the actual four-argument IEventBus overload:
`addListener(EventPriority.NORMAL, false, FMLCommonSetupEvent.class, LifecycleContent::commonSetup)`.
Its static handler enqueues a zero-capture Runnable. The **only** RegistryObject.get
call is in that queued work, which records the resolved item and loaded config.
No lifecycle or command body is to be executed by analysis/conversion.

## Probe-facing observations and independent oracle

Public entrypoint methods: `setupEventCount():int`, `queuedWorkCount():int`,
`observedItem():Item`, `observedEnabled():boolean`, `observedFactor():int`.
After a fresh successful native startup the expected event and work counts are
1; the item observation must be the same object as the native registry item.
These expectations are **not a runtime result until a separate probe observes them**.

Public config methods: `isEnabled():boolean`, `factor():int`. A probe must use them
only after native config loading, never during static conversion.

Public command observations: `registrationCount():int`, `executionCount():int`,
`lastInput():int`, `lastResult():int`. Command registration may occur again when the
game recreates its dispatcher; record actual counts and compare the relevant
controlled scenario rather than assuming registration is globally once per process.
The permission predicate has no side effects because Brigadier can call it a
different number of times during parsing/suggestions.

For fresh default config, native dispatcher execution by a permission-2 source of
`bridgeprobe 0`, `bridgeprobe 7`, `bridgeprobe 100` must return `[0,21,300]` and
increment the execution counter exactly three times. A permission-1 source, values
`-1`/`101`, and non-integer input must be rejected before the handler executes.
For a separate fresh installation with `enabled=true, factor=5`, results become
`[0,35,500]`; with `enabled=false`, they are `[0,0,0]`. Prepare these TOML inputs
explicitly in the disposable server before startup; the mod performs no manual I/O.

COMMON config lives in the native global config directory and is **not synced** to
clients. CLIENT/SERVER scopes, save-specific config, reload callbacks/hot reload,
custom config filenames and config synchronization remain outside this fixture's
claim. The command is server-side; no client GUI or multiplayer equivalence is
established by a dedicated-server check.

## Build and separate execution gate

From the LegacyBridge root, run serially with the pinned project JDK:

```text
gradlew -p fixtures/lifecycle-content-1.16.5 build --no-daemon --max-workers=1
```

The output is `build/libs/legacybridge-lifecycle-1.16.5-1.0.0.jar` inside this
fixture project. Its five tests inspect archive bytes without loading originals;
they are not five supported mods, runtime proof, or a replacement for converter
negative tests. Build status and runtime evidence belong in separate dated
validation receipts. At creation of this fixture no build, conversion or runtime
success is claimed here. See [audited API baselines](../../docs/CONFIG_COMMAND_API_BASELINES.md).
