# COMPOSE-01: finite native configuration review and necessary output inventory

Scope: **LB-08 / COMPOSE-01, PARTIAL**, developer-only headless continuation.
No conversion rule/profile, adapter ABI, player runtime, public installation
admission or public JAR capability is expanded. No Minecraft client/server,
original mod, native probe arming, screen automation, acquisition or publication
is authorized by this work.

## Implemented boundary

`CompositionNativeOutputPolicy.Policy.reviewRecordedContent(cancelled)` is the
public typed entry. Only the existing private-constructor policy from a verified
**unarmed** stage supplies paths, role, plan and receipt identity. The new
`CompositionNativeContentReview` performs two complete layout reviews around
bounded configuration reads, verifies their bytes against the recorded digests,
and rejects changed inventories, immutable pins, aliases or content.

`CompositionNativeConfigValidator` is package-private and entirely inert. It
does not instantiate Forge, a mod, a TOML file watcher, a correction handler or
a save routine. No file is changed, normalized, repaired, deleted or adopted.
No new library is required. The old layout review and its report/schema remain
unchanged; recognized opaque files still pass that separate **layout-only** API.

New report schema 1: `LEGACY_COMPOSITION_NATIVE_CONFIG_REVIEW`, status
`BOUNDED_CONFIG_REVIEW_ONLY`; policy `native-config-review-v1`.
Configuration decisions are `RECORDED_NATIVE_DEFAULTS_ONLY`, with policy
`native-config-defaults-v1`. Reports contain hashes, paths, counts and concerns,
not the configuration values or captured contents. All collections are immutable.
I/O/cancellation/parser failures abort the entire result with sanitized
`COMPOSE_CONTENT_*`, `COMPOSE_CONFIG_*` or inherited `COMPOSE_OUTPUT_*` diagnostics,
without raw exception causes. Cooperative cancellation never returns a partial
review; it is not an OS I/O deadline.

Every review retains `runtimeValidation=NOT_RUN`, `exitObserved=false`, and
`executionAllowed`, `installationAllowed`, `gameExecutionVerified`,
`nativeContentVerified`, `nativeLaunchContractVerified` **false**. The necessary
file set is explicitly **not** a sufficient successful-output set.

## Exact finite configuration contracts

| Relative path | SOURCE Forge 36.2.42 | TARGET Forge 47.4.23 |
| --- | --- | --- |
| `config/fml.toml` | Four root defaults | Fourteen root defaults |
| `config/forge-common.toml` | `general.defaultWorldType="default"` | Presence rejected, including empty/comment-only files |
| `world/serverconfig/forge-server.toml` | Ten defaults in `server` | Eight defaults in `server` |

The constants are independently audited native defaults, not conversion mappings
or an implementation of mod configuration migration. Required values cannot be
omitted, changed or supplemented. Erroring entity/tile/block-entity removal stays
false, preserving crash visibility rather than silently deleting faulty objects.
SOURCE world type is the literal `default`; TARGET permission handler is exactly
`forge:default_handler`. The complete independent positive documents live in
`CompositionNativeConfigValidatorTest`, not in a production schema factory.

TARGET Common defines no values. Its registration is skipped by
`ModLoadingContext.registerConfig` when `spec.isEmpty()`. Therefore accepting an
empty target common file as a natively registered default would be misleading.
The stricter content reviewer rejects it; the older layout-only API still lists
that recognized path as opaque without any content claim.

The packaged FML default resource is incomplete in both versions; corrected
defaults come from exact code, not that resource or filename inference.

This deliberately narrow generated-TOML dialect supports UTF-8, LF/CRLF,
comments, horizontal ASCII whitespace, reordered bare keys, and exactly one
expected bare table when applicable. Values are the exact generated boolean,
decimal integer, simple double literal (`0.1`, `0.05`), unescaped double-quoted
string, or empty single-line list. Quoted/dotted keys, repeated tables, duplicate
keys, escapes, multiline values, arbitrary arrays/tables, coercions, alternate
numeric spellings, unknown keys and all non-default values are refused, even
when a general TOML parser or native Forge would accept them. Nothing is
silently corrected. Native-valid custom configurations remain outside this cut.

Bounds before parsing: 64 KiB per config, 512 lines, 4,096 characters per line;
strict UTF-8 and control rejection. No recursive TOML parse, decompression,
external include, watcher or network access occurs. Layout aggregate, file,
directory, depth, alias and immutable-pin limits remain unchanged.

## Correct native defaults are not safe launch settings

The review explicitly exposes these pending launch concerns:

- `GLOBAL_VERSION_CHECK_ENABLED`: both native defaults permit Forge's version
  checker, whose implementation can make HTTP requests.
- `NATIVE_LOADING_THREADS_AUTOMATIC`: `maxThreads=-1` uses available processors,
  not the bounded converter worker count or a hard native concurrency quota.
- `LAN_ADVERTISEMENT_ENABLED`: TARGET `advertiseDedicatedServerToLan=true`
  starts a LAN pinger that sends multicast UDP to port 4445 independently of
  the dedicated server's bound IP. Loopback properties alone do not isolate it.

These settings are reported, never disabled or approved for execution here.
Pinning `defaultConfigPath="defaultconfigs"` prevents an alternative config
routing value from passing this content gate. Other `server.properties` values
are still only checked by the older finite-key/fixed-value policy, not fully
qualified for world generation, URLs, secrets, network behavior or launch safety.
TARGET early-window selection is gated to `forgeclient*` by its real loader;
default early-window values do not imply a dedicated launch opens a screen.

## Necessary file presence, not successful execution

The `native-necessary-files-v1` inventory requires these files in both roles:
`server.properties`, `config/fml.toml`, `world/serverconfig/forge-server.toml`,
`banned-players.json`, `banned-ips.json`, `ops.json`, `whitelist.json`,
`usercache.json`, `world/level.dat`, `world/session.lock`, `logs/latest.log`,
`logs/debug.log`, and `composition-observations/<bound-runId>.json`.
SOURCE also requires `config/forge-common.toml`: **14 paths SOURCE / 13 TARGET**.
`server.properties` is already a staged baseline file, not evidence newly written.

Any absence yields `requiredOutputs.status=MISSING` and the exact missing paths;
no file is synthesized. All present yields only `PRESENT_ONLY`, always with
`successfulOutputSetVerified=false`. An untouched real stage therefore reports
13/12 missing files; this is the expected unrun condition, not a game failure.
An empty `configurations` or `launchConcerns` list on such a stage means no
generated config was inspected, not that future native defaults are isolated.

Despite no players, startup calls the profile-cache save through successful
old-user conversion, whose helpers return true when the old lists are absent.
Thus `usercache.json` belongs to this finite clean-write inventory. The existing
no-player JSON policy requires all five admin/cache lists to be empty arrays.
Native catches/logs some writes: startup alone is not clean-write proof.

`level.dat_old` remains optional in this minimum presence check, although
repeated successful metadata saves should create it; a later persisted-save
gate must address it explicitly. Finite allowed region/POI/entities/data files
and log rotations remain optional here. No mandatory chunk coordinates are
invented. Arbitrary world/config files and partial/temporary files still block.

World NBT, region storage, session-lock meaning, log/gzip content, and observation
schema/trace remain in `contentUnchecked`. A fabricated world/log/observation can
pass the presence inventory and must never become runtime evidence. Probe JSON
is written before requesting shutdown, so even genuine capture is not exit proof.
The report removes only the explicitly validated config paths from that list.

## Exact source audit and provenance

Already-local sources/binaries were read and rehashed; no platform source,
bytecode or config file is copied into the public artifact. Native keys/defaults
are finite facts expressed in authored policy and test code, not old Forge shims.
General [Forge configuration documentation](https://docs.minecraftforge.net/en/1.20.1/misc/config/)
is context only; these decisions use the exact pinned implementation.

Pins remain in [the preceding session ledger](evidence/compose-sessions-2026-09-08.json):
SOURCE mapped sources `bbe2d148f96694271c43fc2ba584463d75d9ffc490abd0469ae66b831293d376`;
TARGET mapped sources `4bd6656a7cbe3809da68f3b709ca0f2fa186575f58538668c58ce30cb38c4932`;
TARGET loader sources `7917b9e169ed8d1f34299ebd6c8136f310a176754b52b1e492b86638aa059667`;
TARGET fmlcore sources `fc6df13c54c211ea9c0220ec76a2ebe1e573187112467cfc1397a25fee191ec1`.
SOURCE launcher SHA `dfeab9c7d582fce5b1112e9d3038fa0e7075296e467031ce1e2c0ba9fdcf441f`;
its `FMLConfig.class` entry SHA `4cd82eba6dac99423662a99b1b92a5c7030a09f8277d73634505363aecd4b69b`.
The SOURCE loader's defaults were read with JDK `javap`, without initialization.

Key source anchors (SOURCE / TARGET where applicable):

- `ForgeConfig.java:39-98,108-119` / `35-84,97-102`: server/common schemas.
- TARGET fmlcore `ModLoadingContext.java:107-115`: empty config registration skipped.
- TARGET loader `FMLConfig.java:27-41,61-68,80-83,98-118,132`: defaults/types,
  thread selection, correction/save and routing. SOURCE `FMLConfig.<clinit>`
  offsets 26-67, `loadFrom` 80-127, `loadingThreadCount` 27-37, `load` 133-145.
- Both `ConfigFileTypeHandler.java:29,69-77`: config resolution/default copies;
  watcher/autosave/correction/backup routines are never invoked by this reviewer.
- TARGET `DedicatedServer.java:196-198`, `LanServerPinger.java:37-41`: LAN sends.
- SOURCE `VersionChecker.java:120-125`; TARGET fmlcore `110-116,173-180`: HTTP.
- TARGET loader `ImmediateWindowHandler.java:33-35`: physical launch-target gate.
- `DedicatedPlayerList.java:18-28` / `20-30`: four admin-list writes.
- `DedicatedServer.java:153-154,425-481` / `151-152,416-472`: profile-cache save;
  `PreYggdrasilConverter.java:116-117` / `OldUsersConverter.java:117-118`.
- `Main.java:163` / `209`; `SaveFormat.java:339-354` /
  `LevelStorageSource.java:465-480`: metadata save/replacement and caught failures.
- `SessionLockManager.java:22-37,55-67` / `DirectoryLock.java:20-32,50-62`:
  lock-file create/force/release without deletion; existence does not prove exit.
- Pinned Log4j configs SOURCE39-53,64-65 / TARGET40-54,65-66: current logs;
  [previous layout audit](COMPOSITION_NATIVE_OUTPUT_POLICY.md) holds entry hashes.
- Authored `ProbeSession.java:112-125`: capture before shutdown request.

## Developer command and continuation

Serial full build passed in 29m20s: **1,164 corpus cases, 1,157 passed, seven
existing Windows symbolic-link privilege skips, zero failures/errors**. The
230 new cases (221 pure config / nine tree integration) all passed without
skips. Other module test tasks were UP-TO-DATE, not rerun. An initial checked
IOException compile error in report digest construction was fixed before the
successful focused/full runs; its failed log is retained in the ledger.

Both real unarmed stages passed read-only review: SOURCE reports 13 missing
generated paths, TARGET 12, with no configurations/generated outputs to inspect.
All native authority flags stay false. The five observation-index inputs were
independently rehashed (1,402,016 bytes), unchanged, with `runs: null`.
Public JAR remains byte-identical at 1,280,333 bytes / 526 entries, SHA-256
`4e74a86169bb05d8ce299c000d4945c1bcf3746f53c8b3684ee37fe67a9b1b84`;
the changed corpus artifact remains developer-only and contains no test helper.

`reviewCompositionContent` is opt-in, never a normal `check` dependency. It uses
the same six explicit arguments and unarmed stage selectors as
`reviewCompositionOutputs`, but returns the new finite content review.

```powershell
.\gradlew.bat '-Djavax.net.ssl.trustStore=NONE' '-Djavax.net.ssl.trustStoreType=Windows-ROOT' :legacy-corpus:reviewCompositionContent `
  '-PcompositionRunRole=SOURCE' '-PcompositionRunId=<existing-run-id>' `
  '-PcompositionJavaHome=C:/Program Files/Java/jdk-17' `
  '-PcompositionReviewedPlanSha256=<reviewed-plan-sha256>' `
  '-PcompositionReviewedReceiptSha256=<reviewed-receipt-sha256>' `
  --no-daemon --max-workers=1
```

Evidence and actual build outcomes are tracked in the
[continuation ledger](evidence/compose-config-review-2026-09-08.json).
This is a quiescent-tree review, not a filesystem snapshot, hostile-writer
sandbox, hard heap/disk/I/O quota, proof of authorship or successful native save.

Next same **LB-08 / COMPOSE-01**: establish a finite no-network configuration /
native-property launch contract with inert positive/negative fixtures, without
arming anything; qualify the required content/save/log/observation validators and
decide SOURCE conditional lookups. Exact-native-default review is not that safer
launch contract. Native arming/session, SOURCE Java 17 and original/target
comparison remain independently required and NOT_RUN. Do not accept COMPOSE-01,
unlock LINKAGE-GENERAL, promote installation/UI, or count a newly compatible mod.
