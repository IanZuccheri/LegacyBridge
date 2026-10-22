# Native lifecycle comparison probe: Minecraft 1.16.5

Test-only Forge 36.2.42 mod, ID `legacybridge_lifecycle_probe`, version 1.0.0.
It requires the exact original-fixture identity `legacybridge_lifecycle` 1.0.0.
Run with the genuine original Java 8 fixture in an isolated Forge 36.2.42 server using Java 8 x64.
Do not bundle this probe in the product, run it in the converter, or use the
wrong-version original/converted mod on the other side. The original fixture is
executed only during this separately authorized native runtime comparison.

From the LegacyBridge root, build serially:

```text
gradlew -p fixtures/lifecycle-runtime-probe-1.16.5 build --no-daemon --max-workers=1
```

Artifact: `build/libs/legacybridge-lifecycle-probe-1.16.5-1.0.0.jar` inside this
probe project. ForgeGradle remains 6.0.54 with official 1.16.5 mappings and a Java 17
compiler emitting Java 8 classfiles. Six tests inspect the neutral writer,
expectation parser and artifact boundary without loading original mod/game classes.

## Controlled native scenario

On the real FMLServerStartedEvent, the probe verifies its own
execution is on the native server thread, fixture discovery/version and original
entrypoint/item class names. It reads the actual registry object, native stack
limit 16, common-setup count 1, queued-work completion count 1, and reference
identity between the queued observation and the registry item. It **does not
claim to observe the queued task's thread**: that earlier task's thread is not
recorded by this fixture. The server-thread observation applies only to the probe.

Reflection is confined to exact public fixture-owned observation methods and
the public `LifecycleConfig.SPEC` field. Entry comes from the loaded Forge
ModContainer; exact config/command class lookups use that loader and
`Class.forName(name, false, loader)`, with no access bypass. Runtime-only calls
cannot be reused as a static-analysis technique.

Native `ForgeConfigSpec.isLoaded()` must be true. Both config getters and the
earlier setup snapshot must match explicit test expectations. The probe never
calls ConfigValue.set, installs fake config data, or writes config files.
By default expectations are `enabled=true`, `factor=3`. To test another scenario,
prepare `config/legacybridge_lifecycle-common.toml` explicitly in a fresh owned
server directory before startup:

```toml
enabled = true
factor = 5
```

Then pass only these non-sensitive test JVM properties, before the Java main/JAR:

```text
-Dlegacybridge.probe.expectedEnabled=true -Dlegacybridge.probe.expectedFactor=5
```

For disabled config use `enabled=false` in TOML and the matching expectedEnabled
property. Properties express assertions only; they do not set Forge config.
Reject unknown boolean text or a factor outside 1..8.

The native dispatcher and real server command sources exercise:

- permission 1: `bridgeprobe 7` must throw CommandSyntaxException before the mod handler;
- permission 2: `bridgeprobe -1`, `bridgeprobe 101`, `bridgeprobe text` must throw
  CommandSyntaxException without incrementing the execution counter;
- permission 2: `bridgeprobe 7`, `bridgeprobe 0`, `bridgeprobe 100` return
  `[21,0,300]` for default config, `[35,0,500]` for enabled/factor5, or
  `[0,0,0]` when disabled, with exactly one handler call per valid command.

The static Forge-bus registration count must be at least 1 and remain unchanged
during this controlled command sequence. The trace records the actual count;
it does not assume the event can only occur once over a whole process lifetime.

## Evidence and shutdown

Success writes a new forced `lifecycle-trace.json` with bounded primitive JSON and
prints `LEGACYBRIDGE_LIFECYCLE_BEHAVIOR_OK` plus the environment version.
An existing trace is never overwritten. Any failed assertion or write prints
`LEGACYBRIDGE_LIFECYCLE_BEHAVIOR_FAILED` and the real exception, then propagates
failure. Orderly `server.execute(() -> server.halt(false))` is requested on both
success and failure; no System.exit, RCON, gameplay command or world mutation is
used to stop the server.

Create fresh source/target working directories per configuration scenario.
A supervising runner must require the success marker, no failure marker, complete
trace, orderly shutdown, process result and pinned artifact/log hashes. It must not
accept an old trace left by an earlier invocation or treat merely starting Forge
as success. The paired JSON contains no baseline/path/time so equivalent measured
runs can be compared byte-for-byte; baseline versions remain in separate logs.
A registration-count mismatch is reported, not normalized away.

COMMON is local and not client-synchronized. These probes do not certify client
rendering, player command UX, multiplayer, CLIENT/SERVER config scope, live reload,
dispatcher recreation, arbitrary commands, networking or general event support.
No build or native run success is claimed by this source fixture alone. Preserve
existing P6/logic server worlds and evidence; do not run here in place over them.
The probes and original fixture remain project-local under All Rights Reserved.
