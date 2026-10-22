# Separate logic runtime probe: 1.16.5

This **test-only** Forge 36.2.42 mod observes the genuine logic fixture in a
separate authorized runtime test. It is neither a conversion adapter nor a
player dependency. Do not include it in LegacyBridge.jar, the original fixture,
the converted fixture, or the versioned corpus binary payload.

Build serially from the LegacyBridge root with Java 17:

```powershell
.\gradlew.bat -p fixtures/logic-runtime-probe-1.16.5 build --no-daemon --no-parallel --max-workers=1 --console=plain
```

The standalone build uses ForgeGradle 6.0.54, official 1.16.5 mappings,
`javac --release 8` and production `reobfJar`. Output:
`build/libs/legacybridge-logic-probe-1.16.5-1.0.0.jar`.
Four tests cover deterministic bounded JSON, rejected values, no overwrite and
the archive boundary. They load neither Minecraft nor original fixture classes.

Runtime mod ID/version: `legacybridge_logic_probe:1.0.0`. Declared dependencies
are only the exact fixture `legacybridge_logic:1.0.0` and the matching
Minecraft/Forge ranges. Run beside the original fixture, without LegacyBridge or converted code. This is the explicitly separate step in which original fixture execution is authorized.

The probe registers the actual native FMLServerStartedEvent listener through
`MinecraftForge.EVENT_BUS.addListener`. It obtains the item from the real
registry and refuses anything except the original class name
`dev.legacybridge.fixture.logic.LogicItem`. Runtime-only reflection addresses
only that already loaded class's documented public primitive methods. No
`Class.forName`, accessibility bypass, original input loading or reflection
support is added to analysis/conversion.

It checks the independent ordered score oracle, counters, seed and class-init
count in the [original fixture README](../logic-preservation-1.16.5/README.md).
It invokes native `ItemStack.hasFoil()`, which dispatches into the item override
in the real game. A stack enchanted through `enchant(Enchantments.UNBREAKING,1)`
also verifies the preserved superclass branch and short-circuit behavior.

After all assertions, it writes observed primitive values to **a new**
`logic-trace.json` in the supervisor-owned working directory, flushes the file,
prints `LEGACYBRIDGE_LOGIC_BEHAVIOR_OK`, and schedules orderly native server
shutdown. Existing traces are refused and never replaced. Failure prints
`LEGACYBRIDGE_LOGIC_BEHAVIOR_FAILED` plus the cause, schedules clean shutdown,
and throws. The external supervisor must require a fresh complete trace,
success marker, absence of failure markers, normal world saving and exit;
neither JVM exit nor an existing JSON file alone establishes success.

The two version-native probes intentionally share the same neutral trace schema
and independent oracle. Their code differs only in actual native imports,
server-started event type and environment marker. Version/toolchain/source/output
hashes belong in separate external receipts, not the compared behavior payload.
The comparison is a test oracle, not duplicated conversion rules.

Limitations: deliberately oversized in-memory stacks exercise a method, not
legal inventory acquisition. Counters measure controlled server invocation;
they do not govern visual state or certify rendering call frequency. Dedicated
server traces do not prove visible glint, assets, GUI, multiplayer or relog.
The probe never changes blocks, inventories, worlds or player state; server
startup/save and ephemeral native item stacks are the only game work.

See [isolated runtime plan](../../docs/LOGIC_FIXTURE_RUNTIME_PLAN.md). Repository
All Rights Reserved licensing remains unchanged; no binary redistribution grant.
