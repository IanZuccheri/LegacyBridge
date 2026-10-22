# P5 transactional installation validation

Pinned environment: Minecraft 1.20.1, Forge 47.4.23, Java 17. Source fixture:
Minecraft 1.16.5 / Forge 36.2.42. No change to `simple-content-v1`, existing
CompatibilityReport/TransformationLog schemas or adapter ABI 0.1.1-SNAPSHOT.

This ledger separates implementation, static tests, packaged Forge execution,
activation and actual restart. Historical P4 artifact hashes are not P5 proof.

## Evidence status

The serial rerun `build/p5-transactions/build-verification.log` passed all 54
executed tasks: **502 tests, 501 passed, one existing Windows symlink-privilege
case skipped**, no failures. Modules: converter 358 (one skip), product 82,
runtime 30, workflow 32. This includes CLI smoke, exact Forge 36.2.42/47.4.23
API baselines, bytecode/profile validation and final public archive audits.
After the final UI corrections, **`build-final-rerun.log` passed the same 502-test
matrix again in 1 minute 34 seconds, all 54 tasks executed**. The CLI, subset,
runtime JAR and baseline converted output retain their original hashes.
The later handoff rerun (`build-handoff.log`) also passed all 54 executed tasks
in 1 minute 34 seconds; the public SHA-256 still exactly matches the final
ordinary-client and dedicated-server copies listed below.

```powershell
.\gradlew.bat build :legacy-product:validationJar -PverifyMappingEnvironment=true --rerun-tasks --no-daemon --no-parallel --max-workers=1 --console=plain
```

Java was Oracle 17.0.12 on Windows 11/NTFS. Gradle used the Windows-ROOT trust
store explicitly for the local machine's trusted certificates, not disabled TLS.
No builds or Minecraft processes were run concurrently. An incremental build
after the normal-client preparation-script correction also passed (22 seconds,
17 tasks executed, 37 up-to-date); it is not counted as a second execution of
all tests. Log: `build/p5-transactions/build-preparation-fix.log`.

### Failures retained, not hidden

The first build (`build-initial.log`) failed 14 of the then-19 new V2 tests.

Root cause was verified in this exact JDK's WindowsFileAttributes source:
`BasicFileAttributes.fileKey()` returns null. Requiring a non-null directory key
refused every Windows commit after preparing its plan. Do not silently skip the
identity check or infer native keys from NTFS support. The revised implementation
uses operation-owned canary hard links on this provider; the completed tests
exercise directory replacement, normal cleanup and recovery of crash-retained
guards on the actual Windows provider.

The second build (`build-windows-fix.log`) passed V1/V2 but exposed three test
helper problems: an incomplete child-JVM classpath and a test incorrectly
treating `Files.exists == false` during a real deny-read handle as deletion.
The corrected child explicitly includes its own runtime-plan/JSON classes;
the sharing test checks the actual read refusal, then existence and hash after
release. The subsequent full rerun above passed.

Two ordinary-client preparation attempts failed before mod construction, first
on missing Forge-generated srg/extra/patched outputs, then on absent FML runtime
libraries. Logs are retained in `attempt-1-missing-processor-outputs/` and
`attempt-2-missing-fml-runtime/` under `build/p5-transactions`. The preparation
helper now verifies the five direct FML/runtime libraries from the exact official
installer metadata and all three processor outputs before launch. These are
normal installed game files, never files embedded in the public product.

## Failure and recovery matrix

| Case | Executed evidence |
| --- | --- |
| New independent batch, hash/profile/ABI/metadata checks | Retained V1 cases and 21 V2 installer cases; original fixture bytes unchanged |
| Managed V1/V2 strictly newer unloaded upgrade | Independent backup and exact old managed identity restored; same-version/downgrade refused |
| Loaded/unknown/byte-identical replacement | No deletion/overwrite; loaded ID and loaded-file checks tested independently |
| Dependency closure/ordering, duplicate/reserved IDs | Whole-batch preflight refusal before publication; mandatory inbound upgrade gap refused |
| Partial batch | Failure after first publication; second remains absent; explicit repeated resume/restore deterministic |
| Real interrupted process | Separate JVM `Runtime.halt(81)` after PUBLISHED and RETIRED; another JVM performs repeated RESUME/RESTORE |
| Real Windows locked files | `NOSHARE_DELETE` and `NOSHARE_READ`; preserve old file, release handle, explicitly resume/retry successfully |
| Disk-full/access-denied/write/receipt faults | Injected I/O exceptions plus space checks; incomplete private copy/tail is never a published success |
| Ordinary JVM exit during accepted work | Seven barrier cases, including actual `System.exit` child processes; accepted work finishes before exit |
| UI approval/queue/error/world-open | Four transactional controller cases plus retained queue/cancel/navigation tests; no render-thread installation work |
| Automatic restart | No provider implemented/enabled; synthetic exact-context policy tests only, not launcher certification |

## Packaged native-client execution

An ordinary `forgeclient` BootstrapLauncher instance at
`build/p5-transactions/client` uses only the one public JAR and a separate,
explicitly QA-only probe JAR. It uses a fixed dummy local identity, not network
isolation, and does not read the player's launcher profiles, command lines,
credentials or tokens.
The empty user home/class-loading trace excludes the development Gradle classpath.
Original input classes are never loaded by the analyzer or installer.

The original fixture remains at the same `legacybridge/input` path and SHA-256.
For the update test only, the QA harness derives an isolated version-1.0.1 copy
under `input/qa-updates` (not direct inventory), changing only TOML/manifest
version fields and converting it with the real packaged engine. No new content
category or arbitrary legacy bytecode is accepted. One injected IOException at
BEFORE_PUBLISH after retirement tests the UI recovery path; this is not a power cut.

The first complete native scenario passed on 2026-09-06 02:14:15 -03:00:
`LEGACYBRIDGE_P5_CLIENT_OK`, 247 work ticks/1,077 rendered frames, source path and
bytes unchanged, loaded ModList unchanged, automaticRestart=false. Evidence is
archived in `attempt-3-native-pass-before-ui-polish/`. Screenshot inspection then
found a stale busy footer and an overwide tooltip. Those were corrected and the
new artifact was built/tested and re-executed, as recorded below. Earlier
successful bytes alone did not certify the revised artifact.

Launch command (after explicit preparation):

```powershell
.\scripts\Start-ProductionClient.ps1 -ValidationRun p5-transactions -JvmArgument @('-Dlegacybridge.product.p5=true','-Dlegacybridge.product.p5.timeoutSeconds=300','-Xlog:class+load=info:file=client-classloading.log') -Launch
```

### Final native client: passed

On **2026-09-06 02:19:58 -03:00**, the final public SHA-256 below passed
`LEGACYBRIDGE_P5_CLIENT_OK` in the ordinary Forge client, then `Stopping!` and
process exit. `client/client-stdout.log` lines 54-91 distinguish each action:
native conversion, first apply, first restore, new approved reapply, version-only
1.0.1 update interrupted after old retirement, native inspection/resume,
restoration to 1.0.0, removal of the baseline, and a new approved apply-and-close.
The update preparation/fault injection uses the packaged API; native widgets
perform conversion, approval, apply, resume, restore and final close.

The final observer verified **228 work ticks / 1,024 rendered frames**, unchanged
original path/bytes, unchanged currently loaded mods, and the exact new COMMITTED
transaction before closure. The final baseline remains pending a new launch.
No original mod class or `.gradle` class source appears in the class-loading trace.
This is an ordinary Forge installation test using dummy local credentials;
network access was not blocked and fully offline operation was not certified.
It is not a real launcher restart.

Final transaction: `b75be176-452c-4354-8d01-53425b302f0b`. There are four valid
V2 journals: three explicitly restored, one committed/not restored. The update
journal retains the independent old-byte backup. No canary/guard files remain
in those journals after normal completion. Original fixture SHA-256 remains
unchanged and the input JAR was never moved or overwritten between attempts.

Inspected real screenshots at 1280x720, GUI scale 2, `pt_br`:

- [Resumed update, current COMMITTED footer](../build/p5-transactions/client/screenshots/legacybridge-p5-resumed-update-dae44f1b-d411-433b-b340-8e2cc254f45d.png)
- [One checked result and the manual apply-and-close action](../build/p5-transactions/client/screenshots/legacybridge-p5-manual-apply-close-9e0a9290-22d0-47a4-899c-f54a9e0c1fa4.png)

The final screenshots prove the stale processing footer is gone; the tooltip
wrap uses verified native APIs but is not hovered in these final captures.
This P5 run does not claim a new complete GUI-scale, IME or screen-reader matrix.

### Separate dedicated-server activation: passed

Only after the final client exited, the same public JAR, QA probe and exact
committed `legacybridge-legacybridge_example-b75be176-452c-4354-8d01-53425b302f0b-0.jar`
were hash-verified into `build/p5-transactions/server/mods`. No separate runtime
JAR, original source mod, mapping bundle or Gradle classpath was needed to load it.
An ordinary Forge `forgeserver` bootstrap used the official installed `win_args.txt`,
Java 17, an empty user home, loopback port 25597 and a new disposable QA world.
No existing player world was opened or modified.

At **02:21:14 -03:00**, `server/server-stdout.log` recorded `Done (17.686s)!`
and **`LEGACYBRIDGE_P4_ACTIVATION_OK`**. The marker name belongs to the retained
server probe reused here; this is a new execution against the P5 hashes, not a
claim based on the historical P4 log. It proves exactly one runtime identity,
fixture version, native item/block/BlockItem binding, stack limit, hardness,
blast resistance, BASEDRUM instrument, item/block/mining tags, pickaxe behavior,
the real 9-ingot-to-1-block crafting recipe, and a rejected incomplete recipe.

The server saved its disposable world and exited normally by 02:21:21. Its
class-loading trace has no product client classes, P5 client subscriber,
original fixture class or `.gradle` class sources. This independently proves
dedicated-server loading and the tested content after a fresh discovery pass;
it is not a client asset-rendering test, player-world test or launcher relaunch.

Server probe flags are `-Dlegacybridge.product.p4=true` and
`-Dlegacybridge.product.p4.relaunch=true`, with
`@libraries/net/minecraftforge/forge/1.20.1-47.4.23/win_args.txt nogui`.
The validation-only probe is **never part of the public installation**.

## Final artifact identities

| Artifact | SHA-256 |
| --- | --- |
| Public `legacy-product/build/libs/LegacyBridge.jar` (975,498 bytes) | `00cc7ebde1279c1ac3cc6ac0a54498e4598ca4f45700a6799f915ce9dddea991` |
| Separate QA-only product probe (89,639 bytes) | `ffe729207413d34bd3ad821b8940d5ec9804439191b83d5f4d7b7cb5dfe48417` |
| Unchanged internal runtime JAR | `b03f9d0b888da7677f78d9063770cf401ad88b8c4b8a8a56752ed748adc9c428` |
| Unchanged original Forge 1.16.5 fixture (9,534 bytes) | `00dd689f53f9631ecf4a8e84da142eb21051e2ca33f1c05403e4060e15b10947` |
| Final applied 1.0.0 fixture (6,979 bytes), identical on client/server | `541cc64b44b97d0e4e79a193dd2ce3a86a5feba6e15714d0ee24ad63751492bb` |
| Version-only 1.0.1 update, resumed then explicitly restored | `6184714a6f589e18fa7bda60e3f36f08b63277217fd2aa1cdb1c0cc509bef192` |

## Explicit local preparation and provenance

`Prepare-ProductionClient.ps1 -ValidationRun p5-transactions -VanillaDirectory
<explicit-existing-Forge-instance>` reused local verified libraries and 3,575
asset objects, with **zero downloads** in this run. Its schema-2 `preparation.json`
records exact metadata and processor-output hashes. The official Forge installer
SHA-256 is `75cfcb11f60cc641dc83757c1357c08c9bdba255ec529feb115c5e89a59cf75a`.
Extra/patched processor outputs are checked against official installer hashes.
The SRG output has no published hash in that processor recipe: its local SHA-256
is recorded and must match the explicitly selected installed source, or the
official installer must be explicitly rerun with network permission flags.
The helper no longer claims complete preparation from download metadata alone.
All three PowerShell helpers also passed the PowerShell parser. Start refuses an
old/incomplete preparation receipt or a changed recorded processor-output hash.

The 35,223,698-byte/seven-file mapping bundle was explicitly copied from the
previous local development-data preparation, not discovered in a Gradle cache
at conversion time. Manifest SHA-256:
`bac4e0018cc51bd13a7e5ae19116d04cb6b4bc271d3aecfba5e4ac0d27df5c69`.
It remains `LOCAL_USE_ONLY_NOT_APPROVED_FOR_REDISTRIBUTION`; none is embedded in
LegacyBridge.jar. Mapping-data and third-party licensing release gates are unchanged.

## Reviewed API and filesystem behavior

- Exact local `fmlloader-1.20.1-47.4.23-sources.jar`, SHA-256
  `7917b9e169ed8d1f34299ebd6c8136f310a176754b52b1e492b86638aa059667`:
  ModsFolderLocator reads direct `mods/*.jar`; ModSorter verifies optional
  dependencies when present and constructs BEFORE/AFTER edges by owning JAR;
  UniqueModListBuilder may select newer grouped primary IDs, so explicit
  duplicate/version rejection is required independently of Forge discovery.
- Exact patched Minecraft sources: `Minecraft.stop` emits the non-cancellable
  GameShuttingDownEvent; screen Opening is cancellable but is not a universal
  veto for direct world-loading/network calls. Product guards its own workflow,
  not arbitrary calls by other mods.
- JDK 17.0.12 source archive SHA-256
  `24fee80297ef15a886e283a31ac4eca18888ca8e942a21afb3f60a8535dc573b`:
  Windows file channels normally share read/write/delete; a FileLock is not
  proof that Windows denies deletion. Tests must use actual no-share options.
- The [Java shutdown-hook contract](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Runtime.html)
  permits accepted daemon work to continue during ordinary System.exit, but
  hooks are not guaranteed on VM failure, forced termination or power loss.
  InstallationShutdownBarrier does not read a JVM command line or launcher data.
- [Java Files](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html)
  provides non-replacing hard-link creation on supported providers. File-content
  force is not a portable Windows directory fsync or multi-file transaction.

## Limits to keep distinct

Synthetic I/O exceptions prove boundary/error handling, not a physically full
disk or power cut. A child process killed at a known boundary proves process
crash recovery only. Native-client menu tests do not prove every GUI scale,
IME, narrator, modpack or launcher. Dedicated-server discovery is separate from
rendered client assets and fixture behavior.

No validated launcher restart implementation exists in this product. Synthetic
RestartPolicy contract tests are not a supported launcher or a relaunch test.
The implemented fallback is **Aplicar e fechar — abra novamente pelo launcher**.
It is not labelled or reported as a completed automatic restart.
