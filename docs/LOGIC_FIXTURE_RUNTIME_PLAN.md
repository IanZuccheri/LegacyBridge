# Logic fixture: separate source/target runtime comparison plan

This is a **preparation plan, not runtime validation evidence**. Environment
inspection on 2026-09-06 executed only `java -version`, bounded archive/source
reads and directory checks. No server, build, installer or download was run by
this environment audit. Never reuse existing P6 worlds, input folders or logs.

## Available toolchains and actual local baselines

- Source runtime: `${DEVELOPER_HOME}/.gradle/jdks/temurin-8-amd64-windows/jdk8u492-b09/bin/java.exe`
  reports **Temurin 1.8.0_492-b09, 64-Bit Server VM**. The separate Oracle
  `C:/Program Files (x86)/Java/jre1.8.0_431` is 32-bit; do not select it for a
  2 GiB test server. These are local environment paths, not player dependencies.
- Fixture compilation and target runtime: `C:/Program Files/Java/jdk-17/bin/java.exe`
  reports **17.0.12, 64-Bit Server VM**. Both fixture/probe builds can use this
  JDK, compiling source-version artifacts with `--release 8`.
- `build/p6-public/server/libraries/` already contains an ordinary Forge
  **1.20.1-47.4.23** server library installation and its `win_args.txt`.
  Copy only those known installation libraries to a new comparison directory;
  do not copy its worlds, player files, prior logs, old installed fixture or mods.
- An unrelated existing development smoke at
  `../addon/opwllegacy/smoke/packaged-server/` contains vanilla
  `minecraft_server.1.16.5.jar` and MCP `1.16.5-20210115.111550`
  `server-extra`, `server-slim` and `server-srg` artifacts. That server is Forge
  **36.2.34**, not 36.2.42. Its Forge-patched server JAR must not be used as an
  exact 36.2.42 baseline. Read/copy only deliberately selected verified shared
  artifacts; never launch or modify the unrelated installation.
- Exact Forge **36.2.42** launcher/universal/userdev/source artifacts are
  available in the local Gradle cache. The genuine launcher manifest declares
  `Main-Class: net.minecraftforge.server.ServerMain` and `fmlserver`, Minecraft
  `1.16.5`, Forge `36.2.42`, MCP `20210115.111550`. An exact 36.2.42 ordinary
  installer or its dedicated-server processor output was **not found** during
  this bounded audit. Joined/development binpatched JARs are not substitutes.

Forge development sources were inspected at the exact local official-mapped
36.2.42 source JAR. Source runtime lifecycle APIs are
`net.minecraftforge.fml.event.server.FMLServerStartedEvent`, inherited
`ServerLifecycleEvent.getServer()` and `MinecraftServer.halt(boolean)`.
The current target harness actually compiles against
`net.minecraftforge.event.server.ServerStartedEvent`, `event.getServer()` and
`server.execute(() -> server.halt(false))`. Do not invent a cross-version
lifecycle class with one package shared by both versions.

## Proposed isolated layout

Create a fresh operation-owned parent, for example
`build/logic-preservation-v1/run-<unique-id>/`, with independent subdirectories:

```text
source-server/
  libraries/                 exact ordinary Forge 36.2.42 installation
  forge-1.16.5-36.2.42.jar    genuine launcher, not universal JAR
  mods/                      original fixture + source-version QA probe only
  empty-user-home/
  tmp/
  server.properties
  eula.txt                   only after an accepted isolated-test EULA exists
target-server/
  libraries/                 exact ordinary Forge 47.4.23 installation
  mods/                      final public runtime + converted fixture + target QA probe
  empty-user-home/
  tmp/
  server.properties
  eula.txt
evidence/
  source-trace.json
  target-trace.json
  comparison.json
  separately named stdout/stderr/class-loading logs and SHA-256 receipts
```

Use separate new level names, loopback binding, distinct unused ports such as
25597/25598, `online-mode=false`, `enable-rcon=false`, `enable-query=false`,
view/simulation distance 2, and one allowed player. No player connection is
needed. This is not multiplayer or offline-network certification.

Exact-source installation must be an explicit setup step using the official
36.2.42 installer, recording its actual hash and verified processor outputs.
If obtaining that installer or missing runtime libraries requires downloads,
declare that preparation and use official Forge/Mojang endpoints with normal
TLS verification. Do not simulate exact-version independence using the
36.2.34 patched server or development joined game JAR. Source runtime may use a
deliberately selected Java 8 toolchain installed by Gradle; no mapping-cache
lookup is added to the engine by this runtime-test choice.

After the exact installations, EULA/configuration and hash-verified mod sets
exist, launch commands are structurally:

```powershell
# Working directory: the new source-server directory. Run only after builds stop.
& '${DEVELOPER_HOME}/.gradle/jdks/temurin-8-amd64-windows/jdk8u492-b09/bin/java.exe' `
  -Xms512m -Xmx2G '-Duser.home=empty-user-home' '-Djava.io.tmpdir=tmp' `
  -verbose:class -jar forge-1.16.5-36.2.42.jar nogui

# After source exits and saves: working directory is the new target-server directory.
& 'C:/Program Files/Java/jdk-17/bin/java.exe' `
  -Xms512m -Xmx2G '-Duser.home=empty-user-home' '-Djava.io.tmpdir=tmp' `
  '-Xlog:class+load=info:file=target-classloading.log' `
  '@libraries/net/minecraftforge/forge/1.20.1-47.4.23/win_args.txt' nogui
```

These commands are not a ready-made runner: wrap them in a bounded external
process supervisor with exact owned working directories, fresh create-new log
destinations, canonical/reparse-path checks, timeout, stdout/stderr draining,
observed success marker and process exit verification. Prefer orderly console
`stop` after failure. Mark forced termination as failed evidence. If using
`Start-Process`, use `-WindowStyle Hidden`; do not expose an interactive window.
Never run games and Gradle concurrently.

## Minimal genuine source/target probes

Keep two **test-only** probe mods, one compiled/reobfuscated against each exact
Forge version. They are not converter adapters and must never enter the public
LegacyBridge artifact. Do not reuse the existing `ServerContentProbe` unchanged:
it pins `legacybridge_example`, plain native Item/Block classes, the old content
recipe and exact old output. Its implementation is useful only as the verified
pattern for real registries, lifecycle handling, assertions and clean shutdown.

At the matching native server-started event:

1. Confirm exact loaded fixture ID/version and subclass identity from the item
   registry. In source, **only this separate runtime step** is authorized to
   execute original fixture code. In target, load only converted bytecode.
2. Run the exact ordered oracle in the new fixture README, including native
   `ItemStack.hasFoil()` to `Item.isFoil(ItemStack)` virtual dispatch. Verify method-reference/captured
   lambda results, exception path, instance-call counter and one-time static
   initialization. Do not reset or replace state to make traces match.
3. Store neutral primitive observations in a bounded deterministic JSON trace,
   generated by the probe, with separate environment/source/output/public-JAR
   hashes in the receipt. No timestamps or environment-specific paths belong
   in the compared behavior payload. The probe may use its own test-only JSON
   writer; this must not add I/O to the original fixture graph.
4. Flush the complete trace, emit an unambiguous success/failure marker, then
   schedule native `server.halt(false)` through the server executor. A failed
   assertion must be visible and cause the external test result to fail even
   if the JVM exits normally. Require normal world save and exit.
5. Compare the two fresh observed payloads and the independent expected vector.
   A matching pair of wrong results is not a passing oracle. This dedicated
   test proves only the named method/state behaviors, not client visual glint,
   GUI/assets, authenticated multiplayer, relog or other item methods.

The probe can avoid a development dependency on production-SRG fixture types by
retrieving native Item from `ForgeRegistries.ITEMS` and invoking the native
override directly. For fixture-owned counters/scores, use a separately audited
test-only reflection bridge restricted to the one exact expected fixture class
and listed primitive method signatures, or compile against an appropriately
mapped test API. Reflection by the runtime probe is not reflection support in
the converted mod and must never be invoked by analysis/conversion.

All baselines above remain proposals until their builds and separate genuine
runtime traces have actually passed. Record each stage independently.
