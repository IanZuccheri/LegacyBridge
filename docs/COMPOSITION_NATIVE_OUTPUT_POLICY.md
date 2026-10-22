# COMPOSE-01: bounded native output layout, without native execution

Scope: **LB-08 / COMPOSE-01, PARTIAL**, developer-only static infrastructure.
The six conversion profiles, adapters, runtime identity, public JAR and player
installation policy are unchanged. No Minecraft process, probe arming, screen
automation, original mod execution, new acquisition or publication is authorized.

Subsequent separate API: [finite native configuration review and necessary
output inventory](COMPOSITION_NATIVE_CONFIG_REVIEW.md). It strengthens only the
new content review; this original layout API/report remains unchanged and does
not validate configuration payloads or prove native execution.

## Implemented API and authority boundary

`CompositionNativeOutputPolicy.prepare(snapshot, cancelled)` accepts only the
private-constructor snapshot from `CompositionStagedRunVerifier`. It rechecks
the complete **unarmed** stage/current selectors and binds a policy to its
canonical plan hash, receipt hash, original/copy/JDK pins and generated properties.
The exact native argv remains bound through that plan; it is never executed.
A test-only synthetic snapshot is not an alternative public selector.

The private-constructor Policy exposes its canonical `toTree()` and `sha256()`.
`reviewRecordedLayout(cancelled)` performs read-only bounded scans, original and
staged immutable-pin checks, alias checks, fixed-property checks and comparison
of two file/directory inventories. Its immutable report is schema 1,
`LEGACY_COMPOSITION_NATIVE_OUTPUT_LAYOUT`, `BOUNDED_LAYOUT_REVIEW_ONLY`.
The policy itself is `PLANNED_LAYOUT_ONLY`. Both explicitly keep
`executionAllowed`, `gameExecutionVerified`, `nativeContentVerified`,
`nativeLaunchContractVerified` and `installationAllowed` **false**.

This is a quiescent-tree review, not observation of a process stopping, an
approved post-exit session or proof of who wrote the files. Even a completely
unchanged stage can pass this review. `exitObserved=false`, `runtimeValidation=NOT_RUN`.
No output is published, deleted, repaired, installed, replayed or adopted.
Cancellation and I/O failures abort the whole result with a sanitized
`COMPOSE_OUTPUT_*` diagnostic. Neither arbitrary paths nor property values are
included in error text. Reports expose paths/hashes, not captured file contents.

The authorization marker remains an **unknown forbidden file**. Integrating a
real, separately authorized armed session will require a separate contract;
this static tool must not become a launcher by relaxing that check.

## Finite path and content decisions

All paths below are relative to the selected SOURCE/TARGET working directory.
Stage journals, receipts, plans, libraries, mods, EULA and original inputs remain
byte-pinned; they are not writable output merely because they sit under `build/`.

| Category | Recognized paths | What is checked |
| --- | --- | --- |
| Properties | `server.properties` | Required; all explicitly planned keys retain exact effective values; finite role-specific known keys; bounded parsing; duplicate effective keys refused |
| Configuration | `config/fml.toml`, `config/forge-common.toml`, `world/serverconfig/forge-server.toml` | Filename, regular-file/alias, size and digest only; TOML content **NOT_VALIDATED** |
| Logs | `logs/latest.log`, `logs/debug.log`, valid-date `logs/yyyy-MM-dd-<1..99>.log.gz`, `logs/debug-<1..5>.log.gz` | Filename/date, size and digest only; logging correctness, gzip and exit meaning **NOT_VALIDATED** |
| Player/admin lists | `banned-players.json`, `banned-ips.json`, `ops.json`, `whitelist.json`, `usercache.json` | Only exact JSON empty arrays with JSON whitespace; any player/admin entry blocks this no-player slice |
| World | `world/level.dat`, `world/level.dat_old`, `world/session.lock`; finite region/data paths below | Size and digest only; NBT, region contents and successful save **NOT_VALIDATED** |
| Observation | `composition-observations/<bound-runId>.json` | Exact bound filename, size and digest; its schema, trace and native provenance **NOT_VALIDATED** by this tool |

Dimension bases are exactly `world`, `world/DIM-1`, `world/DIM1`.
Each permits `region/r.<x>.<z>.mca` and `poi/r.<x>.<z>.mca`; only TARGET permits
`entities/r.<x>.<z>.mca`. Coordinates use canonical decimal names, no negative
zero/leading zero, at most seven digits. This is a filename/resource bound,
not proof that a chunk should have been accessed.

Per-dimension `data/capabilities.dat` is ordinary native Forge world output;
recognizing this filename does **not** implement mod capability conversion.
Raids are `world/data/raids.dat`, `world/DIM-1/data/raids.dat`, and
`world/DIM1/data/raids_end.dat`. Optional overworld outputs are `scoreboard.dat`
and, on TARGET only, `random_sequences.dat` under `world/data`.

Only exact corresponding directories are recognized. `defaultconfigs`,
`world/datapacks` and `world/playerdata` may be empty; their children remain
unsupported. Arbitrary descendants under a recognized directory are not allowed.
Custom dimensions, arbitrary `.toml`/`.dat`/JSON, new library siblings, new mods,
player files, symlinks/junctions, temporary/witness/backup files, crash reports,
oversized-chunk `.mcc` files and unknown directories fail closed. Partial probe
publication filenames remain blockers and are retained, never silently removed.

Every recognized opaque file remains visible in `contentUnchecked`; the review
does not turn a digest or filename into schema or semantic compatibility.
Properties also explicitly say that added known values are not fully validated.

## Properties: real serialization differences, not a migration

SOURCE's `PropertyManager.loadFromFile/store` uses InputStream/OutputStream
Properties semantics (ISO-8859-1 plus escapes). TARGET's `Settings.store` uses a
UTF-8 Writer. This checker accepts SOURCE Latin-1 and requires strict TARGET
UTF-8 post-save bytes. TARGET's actual **loader** falls back to Latin-1 when UTF-8
is malformed; refusing that fallback in post-save review is deliberately narrower,
not a claim that Minecraft rejects every such input.

The finite key vocabularies come from the exact source declarations: 52 SOURCE,
58 TARGET keys read by native property/world-generation setup, including two
legacy keys consumed/removed during load. Unknown keys and duplicate effective
keys (including Unicode-escaped aliases) are rejected. Java property syntax
handles escaping/continuations; keys remain case-sensitive. Reordering, comments
and canonical escape differences do not count as drift if fixed values agree.
[Java 17 Properties contract](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Properties.html).

The complete explicit plan currently fixes loopback IP, role-specific port,
`level-name=world`, RCON/query disabled, one-player limit, whitelist enforcement,
view distance, structure generation, and TARGET simulation distance. Every one
of those values stays fixed, not just the three required routing keys. The
synthetic test seam can exercise smaller plans; public selector admission cannot.

Native startup adds defaults and rewrites the file. Therefore post-write byte
equality is not appropriate for this one mutable file. The original stage
verifier remains byte-exact before arming; no existing verifier was relaxed.
Other known native values may be reported without acceptance of their full
semantics: no world generator, URL, secret, config routing or network policy is
inferred from mere membership in the known key set. In particular, native CLI
world/port/universe overrides and Forge config-driven routing require the exact
launch plan plus additional future pre-execution semantic gates.

## Bounds, aliases and limits of observation

Up to 4,096 tree entries, depth 12, 256 MiB total mutable/generated bytes;
64 KiB properties, 8 KiB per empty player list, 2 MiB per configuration or
observation, 8 MiB per log, 64 MiB per world file. Immutable inputs retain the
prior stage limits. These are refusal thresholds, not OS disk/heap quotas.
For example native debug-log rollover is 200 MB; this stricter 8 MiB per-file
review deliberately blocks that large log. Dates are valid ISO dates; the total
entry/byte budget, not a promised Log4j date-retention bound, limits old rotations.

Reads never follow symbolic links or noncanonical aliases. File identities are
compared against selected originals, copies and other recorded files; missing
file keys use same-file checks. External hardlinks outside that set, same-byte
replacement and a hostile concurrent writer are not comprehensively excluded.
Two matching inventories are not a filesystem snapshot, lock or process sandbox.
Cancellation checks are cooperative between bounded file operations, not hard
interruptible OS I/O. No archive/NBT/TOML decompression of opaque output occurs.

## Audited local baselines

The source archives are audit inputs only, not redistributed or runtime cache
dependencies. The baseline pins remain those of
[the previous session ledger](evidence/compose-sessions-2026-09-08.json):
SOURCE mapped sources `bbe2d148f96694271c43fc2ba584463d75d9ffc490abd0469ae66b831293d376`;
TARGET mapped sources `4bd6656a7cbe3809da68f3b709ca0f2fa186575f58538668c58ce30cb38c4932`;
TARGET fmlloader sources `7917b9e169ed8d1f34299ebd6c8136f310a176754b52b1e492b86638aa059667`.
Additional TARGET fmlcore sources SHA-256:
`fc6df13c54c211ea9c0220ec76a2ebe1e573187112467cfc1397a25fee191ec1`.

Key source anchors (SOURCE / TARGET):

- `server/dedicated/PropertyManager.java:29-44,75-80` /
  `server/dedicated/Settings.java:34-60,91-96`: properties load/save, logged I/O
  failures, legacy key removal. Native save returning is not durable-write proof.
- `server/dedicated/ServerProperties.java:13-95` plus
  `world/gen/settings/DimensionGeneratorSettings.java:184-196` /
  `server/dedicated/DedicatedServerProperties.java:49-118`: exact key vocabulary.
- `server/management/PlayerList.java:90-93` / `server/players/PlayerList.java:108-111`,
  both `server/dedicated/DedicatedPlayerList.java:18-29`: administrative JSON names.
- `world/server/ChunkManager.java:125,141` / `server/level/ChunkMap.java:148,175`
  and `ServerLevel.java:207`: region/POI and separate TARGET entities storage.
- `world/storage/SaveFormat.java:348-354` / `world/level/storage/LevelStorageSource.java:474-480`:
  level metadata replacement and temporary files. Leftovers are not success.
- Forge `WorldCapabilityData.java:15,51-53` / `LevelCapabilityData.java:14,49-51`:
  native capability saved-data name and always-dirty state.
- Forge `ForgeMod.java:137-139` / `ForgeMod.java:454-456`: common/server/client
  config registration; dedicated server does not load the client config.
  TARGET `ConfigFileTypeHandler.java:34-38,84-103`: config writes/correction backups.
- Pinned `log4j2.xml`, SOURCE lines 39-53 (entry SHA-256
  `cdfbc4767de759f1c87b9f73d7eb8daa08bb531fec71fd4f7984cdb375357c72`),
  TARGET lines 40-54 (`b51029523df8874beca56c09ce4310bdae7f0885dc1412f8830f62f04c19db83`):
  exact filenames/rotations. No global `*.log` permission is inferred.

The [separate native lookup audit](COMPOSITION_NATIVE_LOOKUP_AUDIT.md) distinguishes
two conditional SOURCE absent-sibling lookups from TARGET's inspected module
route. Those library paths must not be admitted as generated output. This is not
a demonstrated Minecraft startup failure or complete classloader certification.

## Explicit read-only developer command

The [validation ledger](evidence/compose-output-policy-2026-09-08.json) records
the successful serial full build: 934 corpus cases, 927 passed, seven existing
symbolic-link privilege skips, zero failures/errors; 241 new cases all passed.
Other module tests were UP-TO-DATE, not rerun. Both actual unarmed stages passed
read-only review: SOURCE 111 immutable inputs / 71 files / 85 directories;
TARGET 223 / 147 / 176. No native-generated output existed, so their empty
`contentUnchecked` lists do not prove successful writes or validated content.
Independent receipt rehashes found no drift, marker, world or native logs.
Public JAR remains byte-identical; corpus code and test helpers are not bundled.

`reviewCompositionOutputs` is opt-in and not part of normal `check`. It requires
the existing unarmed stage and all reviewed selector/hash arguments; it emits a
policy and a current-tree review, without creating a new stage or output files.

```powershell
.\gradlew.bat '-Djavax.net.ssl.trustStore=NONE' '-Djavax.net.ssl.trustStoreType=Windows-ROOT' :legacy-corpus:reviewCompositionOutputs `
  '-PcompositionRunRole=SOURCE' '-PcompositionRunId=<existing-run-id>' `
  '-PcompositionJavaHome=C:/Program Files/Java/jdk-17' `
  '-PcompositionReviewedPlanSha256=<reviewed-plan-sha256>' `
  '-PcompositionReviewedReceiptSha256=<reviewed-receipt-sha256>' `
  --no-daemon --max-workers=1
```

Next, same **LB-08 / COMPOSE-01**: implement finite content/semantic checking for
the recognized configurations and required successful-output set, with inert
fixtures, then independently bind it to authorized native process/arming/exit
evidence. SOURCE late lookups and Java 17 qualification remain explicit gates.
Original/converted behavior comparison and all native tests remain unrun and
unauthorized. Do not accept COMPOSE-01, unlock LINKAGE-GENERAL, promote the
composition into installation/UI or count newly compatible mods from this work.
