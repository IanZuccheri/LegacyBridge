# COMPOSE-01: artifact-bound recorded-observation checker

This finite headless continuation originally implemented the **checker before
the native capture harnesses**. The [probes](COMPOSITION_PROBES.md) have since been
compiled and the [read-only planner / recorded exit checker](COMPOSITION_RUN_PLANNING.md)
implemented. LB-08 / COMPOSE-01 remains PARTIAL. No Minecraft process was started
by those continuations. The current
[reviewed index](evidence/compose-observation-index-2026-09-07.json) deliberately
has `runs: null`: actual observations do not exist yet. Synthetic test records
are created only inside temporary test directories and are never versioned as
gameplay evidence.

`CompositionObservationVerifier` belongs to the development-only `legacy-corpus`
module. It is not a product/runtime dependency, converter rule, adapter, launcher,
installation gate, or extension of any of the six conversion profiles. It never
loads classes, opens an artifact as a classpath, modifies a JAR, downloads data,
uses a Gradle cache, writes evidence, or starts a game.
The public archive audit explicitly rejects the entire `dev/legacybridge/corpus/`
package, including this checker.

## Read-only invocation and results

From the repository root, with the selected local artifacts already present:

```powershell
.\gradlew.bat '-Djavax.net.ssl.trustStore=NONE' '-Djavax.net.ssl.trustStoreType=Windows-ROOT' `
  :legacy-corpus:verifyCompositionObservations `
  '-PcompositionObservationIndex=docs/evidence/compose-observation-index-2026-09-07.json' `
  --no-daemon --max-workers=1
```

The trust flags select the existing Windows root provider, not disabled TLS;
omit them on other systems. This opt-in task is not a dependency of `build` and
uses a separate Java 17 / 256 MiB JVM. Normal builds run only its synthetic tests.
The callable typed entrypoint is `verify(Path repository, String indexPath)`.

The schema-1 `LEGACY_COMPOSITION_OBSERVATION_CHECK` result distinguishes:

| Status | Meaning |
| --- | --- |
| `NOT_RUN` | All five selected static artifacts and their receipt/proof bindings were checked; neither runtime observation is supplied. |
| `RECORDED_MATCH_ONLY` | Both complete, hash-bound records and their log markers match the finite oracle. It does **not** attest that their author actually ran the game. |
| `BLOCKED` | The command rejected a missing file, unsafe path, digest/schema/baseline/cohort mismatch or behavioral assertion. Typed calls throw `IOException`; the command also prints structured error JSON and exits unsuccessfully through the exception. |

Successful verification always reports `gameExecutionVerified=false` and
`installationAllowed=false`. A zero process exit code means the requested check
completed, including NOT_RUN. Never interpret Gradle success as native success.
Independent review of actual run logs/captures remains necessary; unsigned local
records and hashes are not proof of execution or authenticity. The checker does
not update the cursor, corpus, export receipt, conversion proof or acceptance.

## Index schema 1

Exactly `schemaVersion`, `reportType`, `oracle`, `artifacts`, `runs` are allowed.
`reportType` is `LEGACY_COMPOSITION_OBSERVATION_INDEX`; `oracle` is
`composed-foil-cooking-v1`. `artifacts` contains exactly `original`, `candidate`,
`proof`, `receipt`, `runtime`. Each file selection contains exactly a relative
`path`, lowercase `sha256`, and positive integer `bytes`.

The candidate, proof and receipt must be the three exact files in a completed
`composition-qa/run-*` export. `.pending-*`, unexpected entries and mixed exports
are refused. The receipt pins source/candidate/proof, explicitly denies
installation, and remains NOT_RUN even after separate runtime QA. The checker
checks the proof's identity, baseline, counts/validation declarations and selected
digests; it does not rerun ASM, linkage or ZIP-entry validation. That proof was
produced by the [separate static candidate pipeline](COMPOSITION_CANDIDATE.md).
It is a projection of the existing proof schema, not a new replacement schema.

When available, `runs` must contain **both** `source` and `target`. Each run has
exactly `id`, `probe`, `observation`, `log`. These three selections use the same
path/hash/size shape. The two bounded `run-*` IDs must differ. Missing half-pairs,
unknown fields, unknown schema versions and duplicate JSON keys block, not skip.
The reviewed index must be assembled independently from real captures; the tool
does not manufacture a passing record, infer paths or refresh pins automatically.

## Observation schema 1: contract for the next harnesses

Exactly these fields are required:

- `schemaVersion: 1`, `reportType: LEGACY_COMPOSITION_OBSERVATION`,
  `oracle: composed-foil-cooking-v1`, selected `runId`, `role: SOURCE|TARGET`,
  `status: CAPTURED`.
- `environment`: `minecraft`, `forge`, `javaMajor`, `physicalSide`, `logicalSide`,
  `serverThread`. Exact source is 1.16.5 / 36.2.42; target 1.20.1 / 47.4.23.
  Both controlled QA runtimes use Java 17. Side is DEDICATED_SERVER / SERVER,
  with `serverThread: true`. Java 8 classfiles do not imply Java 8 was tested.
- `before` and `after`: each has `fixtureSha256`, `probeSha256`, `runtimeSha256`,
  `receiptSha256`. They must match the selected files on both boundaries.
  Source runtime/receipt are explicitly null; the original installation does not
  contain LegacyBridge or a converted candidate.
- `loadedMods`: exact modId-to-version map containing `minecraft`, `forge`,
  `legacybridge_composed: 1.0.0`, `legacybridge_composition_probe: 1.0.0`, plus
  `legacybridge_runtime: 0.1.1-SNAPSHOT` on the target only. Unexpected mods block.
  This is a mod cohort, not a complete proof of all platform libraries/classloaders.
- `behavior`: exact nested schema described below. No client, reload or
  multiplayer fields are silently accepted as additional support claims.

The future harnesses must observe actual loaded artifact locations and baselines,
not echo expected hashes/versions passed as arguments. They must retain actual
observed values; mismatches fail instead of replacing values with this oracle.
They should use a disposable dedicated installation with no existing worlds,
players, extra mods, hoppers or concurrent furnace tick driver. Capture the native
tick sequence exactly once, on the server thread, and do not execute original
classes during static preparation. Compiling probes is not permission to run them.

After the observation is completely written and independently hashed, the run
log must contain exactly one terminal marker (a native logger prefix is allowed):

```text
LEGACYBRIDGE_COMPOSE_CAPTURE_OK run=<selected-id> sha256=<observation-file-hash>
```

Missing/duplicate/stale markers and any other `LEGACYBRIDGE_COMPOSE_*` marker
(including a later failure) block. This is a finite capture marker, not a generic
Minecraft-log error classifier or proof of orderly shutdown. A future runner
must separately capture exit status and inspect native discovery/fatal errors.
Do not persist authentication tokens, launch arguments or credentials in traces.

## Finite behavioral oracle

This applies only to the authored `composed-logic-recipes-1.16.5` fixture version
1.0.0 and the independently selected candidate. It is not a mod-family oracle.
Revised fixture semantics require explicit review/revision, not new arbitrary pins.

`behavior` contains exactly:

- `registryId: legacybridge_composed:count_token`,
  `itemClass: dev.legacybridge.fixture.composed.ComposedItem`,
  `maxStackSize: 16`, `initialCalls: 0`.
- `foil`: four ordered objects with `count` 1, 2, 3, 4; `foil` true, false, true,
  false; `callsBefore` 0, 1, 2, 3 and `callsAfter` 1, 2, 3, 4. The call being
  observed is native `ItemStack.hasFoil()`, not a copy of the fixture predicate.
- `enchanted`: `count: 2`, `enchantment: minecraft:unbreaking`, `level: 1`,
  `isEnchanted: true`, `foil: true`, `callsBefore: 4`, `callsAfter: 4`.
  Native `super` returning true must short-circuit the owned helper.
- `cooking`: `recipeId: legacybridge_composed:count_token`,
  `recipeType: minecraft:smelting`,
  `ingredientTag: legacybridge_composed:token_material`,
  `tagItems: [minecraft:iron_ingot]`, `recipeTicks: 40`, `recipeExperience: 0`,
  and exactly 121 ordered `samples`.

Each sample has exactly `tick`, `inputId`, `inputCount`, `fuelId`, `fuelCount`,
`outputId`, `outputCount`. Empty stacks are normalized to minecraft:air/count 0.
Tick 0 is the initialized but unticked furnace with one iron ingot, one coal and
empty output. Samples 1–39 still contain the input and no output; coal has been
consumed. Sample 40 consumes the input and has exactly one token. Samples 41–120
must retain exactly that output with no new input/fuel: bounded post-completion
duplication detection. Fuel burn duration and private cooking counters are not
compared. `recipeExperience` is recipe metadata, **not spawned XP/payout validation**.
The checker independently applies this oracle to both records; two equally wrong
results never pass merely because they agree.

## Real-source audit and bounds

The two pinned local source archives were rehashed and inspected as ZIP text:

| Archive | SHA-256 |
| --- | --- |
| Forge 1.16.5-36.2.42 mapped-official sources | `bbe2d148f96694271c43fc2ba584463d75d9ffc490abd0469ae66b831293d376` |
| Forge 1.20.1-47.4.23 mapped-official sources | `4bd6656a7cbe3809da68f3b709ca0f2fa186575f58538668c58ce30cb38c4932` |

`Item.isFoil` (source line 230 / target 284) delegates to `isEnchanted`;
`ItemStack.hasFoil` (760 / 804) dispatches through the item. Furnace source
`AbstractFurnaceTileEntity.tick` (218, progress/completion 247–254) and target
`AbstractFurnaceBlockEntity.serverTick` (236, 273–282) consume the recipe on the
completed tick; `setItem` initializes its recipe duration (source 393–395,
target 418–420). The native APIs differ; future probes must call their actual
version-specific APIs. This audit introduces no conversion mappings for furnaces.
The fixture's original recipe/tag bytes supply the 40-tick, zero-XP, iron-input
expectation. Existing [logic](LOGIC_API_BASELINES.md) and
[recipe](RESOURCE_API_BASELINES.md) contracts still govern conversion.

Native tests remain a separate execution gate; [Forge's Game Test documentation](https://docs.minecraftforge.net/en/1.20.1/misc/gametest/)
describes in-game behavior tests, not something performed by this JSON checker.
No shared GameTest API across both pinned versions is assumed. No game source,
old Forge binary or private mod is copied or redistributed.

Limits: exactly five static files, or eleven with paired observations; 128 MiB
per file, 512 MiB aggregate, 2 MiB per JSON, 8 MiB/100,000 lines per log, existing JSON depth/node
bounds. Artifact hashes are streamed and checked again at the end; parsed JSON
and log bytes are separately hash-checked at capture. Paths are restricted
repository-relative ASCII components, with no traversal/absolute paths, streams,
symlinks/junctions or duplicate hard-link aliases between selected artifacts.
Use a quiescent local tree: this is not a hostile-writer sandbox or filesystem
snapshot. Index pins are trusted local review inputs, not publisher authentication.

## Remaining work

The subsequent [probe continuation](COMPOSITION_PROBES.md) implements and compiles
the separate original/target capture JARs, no-overwrite writing, loaded artifact
bindings, synchronous native tick drivers and explicit capture/shutdown failures.
Their shared collector/checker protocol passed synthetic tests; no native capture
was performed and the real index retains `runs: null`.

Next in COMPOSE-01: implement/test the disposable launch planner and post-exit
collector headlessly. It must own bounded arguments/cwd: public Forge lifecycle
events cannot prevent native `--world`/`--universe` overrides before world loading.
A capture marker requests, but does not prove, completed native shutdown.
Do not change the preserved original fixture merely to satisfy the oracle.
Then, only with authorization, run isolated native QA and
review discovery, behavior and original integrity. Client rendering/translations,
resource reload and multiplayer remain separate unperformed checks.

The [implementation ledger](evidence/compose-observations-2026-09-07.json) records
actual build/test and static-index verification results. COMPOSE-01 remains
PARTIAL; LINKAGE-GENERAL, public composition/UI/installer promotion and release
qualification are not unlocked by this checker.
