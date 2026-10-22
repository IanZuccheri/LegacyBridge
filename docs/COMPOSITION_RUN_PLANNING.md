# COMPOSE-01: read-only run planning and recorded exit checks

Historical checkpoint. The subsequent [unarmed stager and inert collector](COMPOSITION_STAGING_COLLECTION.md)
add separate developer mechanics; this planner/checker itself remains read-only.

LB-08 / COMPOSE-01 remains **PARTIAL**. This finite developer-only continuation
implements a disposable run **plan**, not its execution, and validates supplied
post-exit records, not a live process. No Minecraft client or server was started.
There are no changes to conversion rules, adapters, the six profiles, installation
admission or the public UI. The tools are in `legacy-corpus`, excluded from the
public `LegacyBridge.jar` by the existing package audit.

## Delivered and still pending

| Component | Implemented result | Not established |
| --- | --- | --- |
| `CompositionRunPlanner.plan(repository, Request)` | Immutable, hash-bound copy/argument/property directives; `PLANNED_ONLY` | Staging, EULA acceptance, process start, enforcement of declared process limits |
| `CompositionExitVerifier.verify(repository, observationIndex, exitIndex)` | Bounded checks of paired observations, logs and exit records; `RECORDED_EXIT_ONLY` | That the records came from a real process, stream EOF or a real exit |
| Existing observation checker | Finite fixture behavior assertions over supplied records | Native execution or installation authority |

All reports keep `gameExecutionVerified=false` and `installationAllowed=false`.
The planner also returns `executionAllowed=false`; the exit checker returns
`launchContractVerified=false`. There is deliberately no real exit index yet.
The [reviewed observation index](evidence/compose-observation-index-2026-09-07.json)
retains `runs: null` and returns **NOT_RUN**. Synthetic records are only test data.

## Planning against the actual prepared installations

The schema-1 [platform inventory](evidence/compose-platform-inventory-2026-09-07.json)
contains paths, sizes, SHA-256 values and local provenance, **not game binaries,
sources, mappings or dependency contents**. It selects 30 source platform files
and 66 target platform files. The public planner pins the inventory itself, both
launch recipes, the five reviewed original/candidate/proof/receipt/runtime
artifacts and the two previously compiled probe archives. Changed pins block;
they are not refreshed from whichever file happens to exist.

The already prepared templates are `build/logic-preserving-v1/source-server` and
`build/logic-preserving-v1/target-server`. They contain historical worlds and logs.
Only the enumerated platform files and a bounded **previously accepted** EULA are
selected. No worlds, logs, player data, configs, installed mods, sources or whole
directory trees are copied or even proposed as copy inputs. Missing local data
blocks explicitly: there is no download, implicit cache lookup, new player
dependency, distribution-rights grant or claim of standalone provisioning.

The launcher contracts were inspected from the exact local Forge files:

- Source `forge-1.16.5-36.2.42.jar`: main class
  `net.minecraftforge.server.ServerMain`, its manifest `Class-Path` and exact
  `ServerLaunchArgs`; `ServerMain` prepends those arguments. Additional server-srg,
  server-extra and Forge server/universal dependencies come from the actual
  server locator, not invented manifest entries.
- Target `libraries/net/minecraftforge/forge/1.20.1-47.4.23/win_args.txt`: exact
  ordered 16-line recipe for `cpw.mods.bootstraplauncher.BootstrapLauncher` /
  `forgeserver`, module/class paths and the dynamic server/Forge/language files
  identified in `CommonServerLaunchHandler` / `ForgeServerLaunchHandler`.
- Every selected JAR's main manifest section is bounded and checked for additional
  class-path references. Undeclared paths, URLs, traversal and duplicate entries
  fail. Signed per-entry digest sections need not be decompressed. The actual
  `jopt-simple` 5.0.4 main manifest ends after a complete newline without an extra
  blank line; that valid EOF is accepted, but an unterminated final line is not.

Java's launcher can prepend `JDK_JAVA_OPTIONS` and expand argument files; see the
[official Java 17 launcher specification](https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html).
The plan uses a closed argument vector, not a shell string or arbitrary `@file`.
It specifies removal of `JAVA_TOOL_OPTIONS`, `_JAVA_OPTIONS`, `JDK_JAVA_OPTIONS`
and `CLASSPATH`, and a disposable user home, temporary directory and absent Gradle
home. Planning does not read those environment values or apply these directives.
The future process collector must enforce and revalidate them.

The selected Java executable and `release` file are hashed; metadata must declare
Java 17. This does **not** execute `java -version` or prove Forge 36 runs on that
JVM. Earlier successful source logic tests used **Java 8**, as recorded in
`scripts/Test-LogicRuntime.ps1` and `LOGIC_PRESERVING_VALIDATION.md`; they are not
Java 17 evidence. The composition probe contract still requires Java 17, with its
source-runtime compatibility gate explicitly pending.

### Read-only command

From the root, with the reviewed private artifacts and templates already present:

```powershell
.\gradlew.bat :legacy-corpus:planCompositionRun `
  -PcompositionRunRole=SOURCE -PcompositionRunId=run-example-source `
  '-PcompositionJavaHome=C:/Program Files/Java/jdk-17' --no-daemon --max-workers=1
```

Repeat serially with `TARGET` and a different fresh run ID. The task runs the
planner JVM only, not the planned command. The local validation also uses the
existing Windows-ROOT trust-provider flags documented in [the probe guide](COMPOSITION_PROBES.md).

The entire `build/composition-probe-runs/<run-id>` must not exist. Its future game
directory is `<source|target>` beneath it. Canonical path/ancestor checks reject
unsafe aliases, junctions, symlinks, traversal, alternate streams and device names;
selected hard-link aliases are rejected. Selected files are rehashed before the
plan is returned. No destination is reserved, created, reused or deleted.
These checks are ordinary drift protection on a quiescent trusted filesystem,
not an atomic snapshot or a sandbox against a hostile concurrent writer.

Plans declare 2 GiB maximum Java heap, one game at a time, a 240-second deadline,
60-second stop grace and 8 MiB per output stream. Input planning caps are 512
files / 512 MiB total, 128 MiB per file, 256 platform files per role, 64 arguments,
64 KiB main manifest/target argument file and 8 KiB EULA/Java release metadata.
The read limits are implemented; process limits remain declarations until the
owned executor exists. A heap limit is not a total process/native memory limit.

The source copy plan includes original + source probe, not the converted JAR.
The target includes candidate + target probe + public runtime + export receipt.
Generated properties select the owned `world`, loopback-only ports 25597/25598,
no RCON/query, whitelist and bounded distances. `simulation-distance` is target
only. Generated authorization text is a directive, not present permission to run.

## Recorded post-exit protocol (schema 1)

`LEGACY_COMPOSITION_EXIT_INDEX` has exactly `schemaVersion`, `reportType`,
`oracle` (`composed-foil-cooking-v1`), `observationIndexSha256` and `runs`.
`runs.source` and `runs.target` each have `id`, `exit`, `stderr`; file entries have
exactly `path`, `sha256`, `bytes`. Stdout comes from the existing observation
index's selected `log`, preventing a second unrelated successful log from being
substituted for that observation.

Each `LEGACY_COMPOSITION_PROCESS_EXIT` record has exactly these fields:

```text
schemaVersion, reportType, runId, role,
stdoutSha256, stderrSha256, observationSha256,
processExited, exitCode, stdoutComplete, stderrComplete,
timedOut, cancelled, forcedTermination, elapsedMillis
```

The checker requires paired `RECORDED_MATCH_ONLY` observations first; matching
roles/run IDs/hashes, exit zero, positive bounded duration, recorded complete exit
and both streams, and no timeout/cancellation/forced termination. Those flags are
unsigned assertions. Checks do not observe or prove actual EOF or process death.

The actual source/target native shutdown methods/logs were inspected separately.
The checker requires native `Done` before the one exact capture marker, then
`Stopping server`, `Saving players`, `Saving worlds` and saved chunks for `world`,
`DIM-1`, `DIM1`, using the version's real logger. Source uses `ChunkManager` and
can repeat chunk-close lines; target uses `MinecraftServer` and additionally
requires `All dimensions are saved`. It rejects failure/severity/exception
markers on either stream, including failures **after** a successful capture/save.
Exit zero alone is insufficient. Unknown worlds do not count as the owned world.
This is a finite fixed-launch log recipe, not a general Minecraft log parser.

The 17 selected files are size/hash checked and rechecked, with canonical-path and
alias checks. Empty stderr is allowed; empty required records/stdout are not.
Limits: 128 MiB/file, 512 MiB aggregate, 64 KiB exit record, 8 MiB/log,
100,000 lines/log and 64 KiB UTF-8/line. Malformed UTF-8/JSON, stale hashes,
unexpected fields and selected sensitive argument/token patterns block with
sanitized codes, without echoing the captured content. This is not a universal
secret detector or log scrubber; it never changes or deletes existing evidence.
The future collector must prevent sensitive data from being captured at all.

The opt-in `:legacy-corpus:verifyCompositionExit` task requires explicit
`-PcompositionObservationIndex=<relative-path>` and
`-PcompositionExitIndex=<relative-path>`. It writes no receipt or evidence index.
Neither new task is a build dependency; normal tests use inert ZIPs/metadata and
synthetic logs, never class-load the fixture or start the game.

## Acceptance and next continuation

The [ledger](evidence/compose-planning-2026-09-07.json) separates synthetic test
results, actual-template read-only plans, build/package verification and native
**NOT_RUN**. No real exit record is fabricated to satisfy the protocol.

Next, in the same COMPOSE-01 cut: implement owned **create-new staging** and a
bounded live collector tied to the selected plan, with inert helper JVM tests for
EOF, exit, timeout/cancellation, partial writes, unknown files and post-exit
rehashing. Do not start games for those tests. No automatic cleanup of unknown
files or directories, new EULA acceptance or game execution may be inferred from
a plan. Bind the approved plan, actual launch and post-exit records before any
native qualification is considered. Windows locks/crash leftovers need explicit
failure handling, not false successful collection.

Independent native source/target comparison (including the source Java 17 gate),
normal tick scheduling, clients/assets/translations, resource reload and
multiplayer remain pending and require separate authorization. Do not accept
COMPOSE-01, unlock LINKAGE-GENERAL or promote composition into installation/UI.
