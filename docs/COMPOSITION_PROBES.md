# Composition capture probes (developer QA only)

LB-08 / COMPOSE-01 remains **PARTIAL**. This continuation implements and compiles
two version-specific capture mods plus a shared JDK-only recorder. No Minecraft
client or server was launched. No original fixture class was executed. There is
no new conversion rule, adapter, profile, installer admission or public UI change.

These are **not additional player dependencies**. Only `LegacyBridge.jar` is the
mandatory public LegacyBridge artifact. Probe projects, shared collector, tests
and developer corpus tools must never be packaged in it.

## Implemented boundary

| Component | Responsibility |
| --- | --- |
| `fixtures/composition-runtime-probe-1.16.5` | Real Forge 36.2.42 / Minecraft 1.16.5 entrypoint; Java 8 classfile format, Java 17 QA runtime required |
| `fixtures/composition-runtime-probe-1.20.1` | Real Forge 47.4.23 / Minecraft 1.20.1 entrypoint; Java 17 |
| `fixtures/composition-probe-common` | Artifact binding, one-shot session, delegated observations, bounded serialization and no-overwrite writes; no game imports |
| `legacy-corpus` test sources | Synthetic recorder-to-checker protocol tests; shared code is compiled into **test classes only**, never the corpus/product JAR |

The probes depend on native Forge/Minecraft at compile/runtime, not embedded game
classes. They contain only owned QA code and two metadata files. They retain the
repository's All Rights Reserved license; no third-party source redistribution
or relicensing is introduced. Test libraries are not included in probe archives.
Forge's supplied Gson is used only to inspect the native ingredient JSON tree;
no JSON/logging implementation, ASM copy, nested JAR, bootstrap or service is bundled.

Both exact ForgeGradle builds use 6.0.54 and the repository wrapper. Build one at
a time from the repository root (these commands **do not start a game**):

```powershell
.\gradlew.bat -p fixtures/composition-runtime-probe-1.16.5 build --no-daemon --max-workers=1
.\gradlew.bat -p fixtures/composition-runtime-probe-1.20.1 build --no-daemon --max-workers=1
```

The local validation uses the Windows certificate store through the existing
`-Djavax.net.ssl.trustStore=NONE -Djavax.net.ssl.trustStoreType=Windows-ROOT`
options. This selects a trust provider; it does not disable TLS verification.
Development builds use the explicitly provisioned local Forge development data.
This is not a new cacheless/offline execution claim or a player data dependency.

## What a future authorized capture actually measures

In the actual server-started event on the owning dedicated server thread, each
probe reads the loaded mod cohort, versions and actual ModList artifact paths.
It refuses unknown mods, wrong versions, clients, wrong logical side/thread,
non-regular/development-directory artifacts, aliases and changed bindings.
Original/candidate, target runtime and export receipt have reviewed exact SHA-256
pins. The probe's own loaded archive hash is observed and recorded. Every artifact
is rehashed after capture; the authorization marker is checked before and after.
SOURCE requires null runtime/receipt bindings and rejects a late source receipt.

The item is resolved from the real Forge registry. The actual subclass and its
exact public `calls(): int` instrumented helper accessor are checked. This is
reflection on the **authored, already loaded fixture during separate native QA**,
never original execution during analysis or conversion. The collector invokes
native `ItemStack.hasFoil()` for counts 1, 2, 3 and 4, and for an enchanted stack
of count 2, recording before/after helper counters and observed values.
It does not substitute the expected predicate when results are wrong.

The native recipe manager, single-tag ingredient, loaded tag members, cooking
duration and XP metadata are read. A detached real furnace inventory starts with
one iron ingot and one coal. The collector samples it at tick 0 and after each of
120 explicit native tick calls. The independent
[observation checker](COMPOSITION_OBSERVATIONS.md) evaluates the expected result;
the collector itself records observations, including incorrect behavior.

### Native furnace isolation

1.16.5 uses `FurnaceTileEntity()`, `setLevelAndPosition` and `tick()`;
1.20.1 uses `FurnaceBlockEntity(BlockPos, BlockState)`, `setLevel` and
`AbstractFurnaceBlockEntity.serverTick`. These are native calls in QA, **not new
furnace conversion mappings**.

The old tick method changes the world's block LIT property on ignition. A
detached tile at AIR would fail. Therefore the driver verifies already-loaded
spawn chunk air at Y=200, temporarily creates a native **empty** furnace support
block and binds a separate, never-world-registered furnace inventory to it.
Only the detached instance owns input/fuel/output. Synchronous execution before
the normal server loop prevents interleaved world ticking of that inventory.
Every sample/tick checks server thread, zero players, support identity and empty
support inventory. Cleanup clears owned detached stacks, restores original air
and verifies absence of a block entity. An unknown/replaced support is never
overwritten as a fallback; failures remain explicit.

This tests the actual machine tick method under a controlled driver, not normal
20-TPS scheduling, chunk lifecycle, XP payout, drops, saving, client visuals,
reload or multiplayer. Failure/crash can leave an owned support block in the
disposable world. Native execution has not yet validated this driver.

## Arming is a guard, not an automatic run command

There are deliberately no `runClient`/`runServer` configurations in the probes.
Do not install them into a player profile or invoke them on an existing world.
Future execution requires a separately reviewed disposable launcher/collector
and fresh user authorization; none was performed by this continuation.

The constructor requires all of the following, without creating them itself:

- Exact canonical layout `composition-probe-runs/<run-id>/<source|target>`, with
  current working directory equal to that game directory. Run IDs match
  `run-[a-z0-9][a-z0-9-]{0,40}`. Use different IDs for source and target records.
- Explicit system property `legacybridge.compositionProbe.run=<run-id>` and
  existing `composition-probe-authorized.txt` containing exactly `<run-id>\n`
  as UTF-8, with no BOM/CRLF or extra text.
- No existing `world` file/directory; existing canonical `mods` and
  `composition-observations` directories; no prior `<run-id>.json` result.
- Bounded, duplicate-key-free `server.properties`: `level-name=world`,
  `server-ip=127.0.0.1`, `enable-rcon=false`, `enable-query=false`.
- TARGET has the exact selected `composition-receipt.json`; SOURCE has none.

At server-started, the actual native world path must still equal the selected
`gameDir/world` and the loaded cohort must be exactly Minecraft + Forge + fixture
+ probe (+ LegacyBridge runtime on TARGET). This gate is before **probe mutation**.

**Important launch limitation:** real `Main` in both versions handles `--world`
and `--universe` in local options and creates/reads/saves world data before any
public server lifecycle event. Even AboutToStart is too late to prevent that.
Constructor arming proves only the selected cwd/world was absent, not that
arbitrary launcher overrides cannot load another world first. No suitable public
pre-load event was found. The pending runner must own the exact bounded launch
argument list and cwd, exclude arbitrary overrides, check fresh canonical paths
and limit the mod cohort. The current probes are not a sandbox for an unknown
launcher. No ModLauncher service/bootstrap is introduced to conceal this limit.
Do not copy or persist authentication tokens or sensitive launcher arguments.

## Publication, failure and shutdown

The shared recorder enforces a single-use session and schema version 1. JSON
contains only bounded identifiers, finite numbers, booleans/null, bounded lists
and objects. Limits: 16 nesting levels, 10,000 nodes, approximately 1 MB serialized
text (plus a bounded final token), 256 elements/list, 32 entries/object, 256
characters/identifier. File publication independently caps UTF-8 at 2 MiB;
configuration reads cap at 8 KiB and streamed artifact hashing at 128 MiB/file.

Publication forces and re-reads a private file, then uses a same-parent move
without replacement. A hard-link witness proves cleanup identity on Windows
even when Java file keys are null. Unsupported hard links block publication.
An unknown existing/replaced file is not overwritten/deleted. A crash or late
move/cleanup failure may leave private or uncertain final files, reported
explicitly; this is not directory-fsync durability, hostile-writer protection or
automatic crash recovery. Use a quiescent trusted local directory.

Only after complete publication/hash verification is this exact marker emitted:

```text
LEGACYBRIDGE_COMPOSE_CAPTURE_OK run=<run-id> sha256=<observation-sha256>
```

Capture/write/logging failures are explicit, propagated and followed by a
shutdown request. `server.halt(false)` requests normal native shutdown without
joining its own server thread; no `System.exit` is used in the probe. A failing
shutdown is logged and propagated, retaining any preceding exception as well.
Already published complete observations are not deleted to hide a later failure.

**CAPTURED is not PASS.** The marker precedes completion of native save/close and
process exit. The pending external collector must inspect complete stdout/stderr,
exit status, stop/failure evidence and post-exit hashes. Exit code alone is not
enough: native server catch/finally paths can report a failure in logs. A later
`LEGACYBRIDGE_COMPOSE_*` failure invalidates an earlier success in the checker.
Matching records still mean only RECORDED_MATCH_ONLY, never execution attestation
or installation permission.

## Source audit and current evidence

Read-only source/bytecode inspection used the exact mapped source archives:

- 1.16.5: `bbe2d148f96694271c43fc2ba584463d75d9ffc490abd0469ae66b831293d376`.
- 1.20.1: `4bd6656a7cbe3809da68f3b709ca0f2fa186575f58538668c58ce30cb38c4932`.

Reviewed `Main`, `MinecraftServer`, native furnace/ingredient/tag/registry/item
methods, Forge server events, `ModList`, `ModFileInfo`/`IModFileInfo`, actual
`ModFile.getFilePath`, `ForgeVersion` and `FMLPaths`. Native mod constructors run
before ordinary world creation (source Main 95; target Main 125). Server-started
occurs before the normal `while(running)` loop. `halt(false)` does not self-join.
This audit and compilation do not prove startup or behavior.

Exact builds, probe/source/artifact/log hashes, test counts and skips are in the
[headless implementation ledger](evidence/compose-probes-2026-09-07.json).
The real [selected index](evidence/compose-observation-index-2026-09-07.json)
retains `runs: null`; synthetic test records are never entered as native evidence.

Continuation: the [read-only run planner and recorded exit verifier](COMPOSITION_RUN_PLANNING.md)
now implement the fixed launch directives and checks over supplied complete
logs/exit records. They do not stage or execute a run, observe actual EOF/exit,
or associate a real process with an approved plan. Next in this same cut is
owned create-new staging and bounded live collection, tested with inert helper
JVMs only. Native original/target comparison remains a separate authorized gate;
the older source tests on Java 8 do not prove the composition Java 17 contract.
Do not accept COMPOSE-01 or unlock LINKAGE-GENERAL, public composition, UI or installation.
