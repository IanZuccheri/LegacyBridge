# Versioned compatibility corpus

Corpus **2026.10.22.1**, schema **1**. This is a development evidence set, not a
compatibility list, a modpack, a redistributable mapping bundle or a release gate
waiver. No new conversion rule or runtime adapter was enabled for this corpus.
The public product remains experimental/Beta with `simple-content-v1` unchanged.

This revision changes only the historical project-authorization evidence path:
the six fixture records and original intake now reference the exact archived
[2026-09-06 notice](licenses/legacybridge/legacy-license-2026-09-06.txt), SHA-256
`07a52114e08672700aef0163270403224f4bb61dc324124f192518a66ee1e9ef`, instead of the
mutable root `LICENSE`. The owner selected GPL-3.0 for current project sources
on 2026-10-22; see the current root license and release notices for its scope.
That decision does not rewrite prior permissions, artifact hashes, observations
or third-party licenses. The original `2026.09.06.1` lock is retained as history;
the current lock is [2026.10.22.1](snapshots/2026.10.22.1.json). No new mod was
acquired, converted or executed by this metadata migration, and no binary
redistribution grant is inferred from the unchanged historical records.

## Snapshot and counting boundary

| Cohort | Exact artifact identities | Analyzable | Convertible | Output bytecode validated | Client / server starts | Scoped client / server behavior | Multiplayer |
| --- | ---: | ---: | ---: | ---: | --- | --- | ---: |
| Authorized third-party real releases | 3 | 3 | 0 | 0 | 0 / 0 | 0 / 0 | 0 |
| Project fixture cases and adversarial controls | 6 | 5 | 3 | 3 | 1 / 1 | 1 / 1 | 0 |

Counts mean `PASSED` for the specified stage, not percentages. The six fixture
identities are one original Forge-built example and five derived/adversarial
inputs, including one corrupt non-mod file. They are **not six supported mods**.
The duplicate A/B filenames have identical bytes and occupy one record. The
cancel and duplicate control JARs convert in isolation; a cancelled UI operation
and duplicate-inventory rejection remain separate scenario results.

The three real projects are a small convenience sample, not a representative
sample of Forge mods. Analyzable means bounded static inspection completed; it
does not mean admissible to the source profile. AppleSkin is the explicit example.
No original third-party class was loaded or executed by this work. No real-mod
conversion completed, so no target runtime validation exists for those releases.

| Exact publisher release | Exact-source build evidence | Current first conversion refusal |
| --- | --- | --- |
| [Clumps 6.0.0.28](records/clumps-forge-6.0.0.28.json) | MC 1.16.5 / Forge 36.1.16 | Unapproved manifest attribute: `Implementation-Timestamp` |
| [Controlling 7.0.0.29](records/controlling-forge-7.0.0.29.json) | MC 1.16.5 / Forge 36.0.0 | Side-specific dependencies require manual review |
| [AppleSkin 2.5.1+mc1.16.4](records/appleskin-forge-2.5.1-mc1.16.4.json) | MC 1.16.4 / Forge 35.1.4; publisher also lists 1.16.5 | Side-specific dependencies require manual review; analyzer source profile is AMBIGUOUS |

These build versions are from immutable release-associated source, **not a
reproduced binary build**. Artifact size, SHA-256 and publisher SHA-512 identify
the acquired bytes. Minecraft/Forge dependency ranges in the JAR, publisher game
support, analyzer detections and exact source-build evidence are different fields.
`${file.jarVersion}` stays raw in metadata rather than being invented as an exact
resolved version. Removing the first refusal alone unlocks **zero whole mods**.
Further source-reviewed dependencies are hypotheses for the [backlog](BACKLOG.md),
not hidden later converter results.

## Files and provenance

- [catalog.json](catalog.json) selects the version and nine records. Every record
  has source identity, permission evidence, dependencies, detected features,
  actual blockers, output hash/status and eight independent validation stages.
- [SCHEMA.md](SCHEMA.md) defines evidence and stage semantics. The executable
  validator is `legacy-corpus`, a development-only module outside the public JAR.
- [intake/rights-candidates.json](intake/rights-candidates.json) pins official
  publisher releases, license URLs, source commits and acquisition hashes.
  [Full upstream notices](licenses/) are retained. MIT (Clumps, Controlling) and
  Unlicense (AppleSkin) provide the reviewed local-use/modification basis; no
  third-party binary redistribution was selected.
- [intake/fixtures-candidates.json](intake/fixtures-candidates.json) preserves
  fixture lineage and the historical P6 evidence audit. At that audit the project
  license was All Rights Reserved; its archived notice remains the evidence for
  those records. It does not override the current root project license.
- [intake/semantic-review.md](intake/semantic-review.md) links exact source code
  for lifecycle, XP, configuration, screen, networking and rendering dependencies.
- `build/corpus-intake/` and `build/corpus-runs/` hold local JARs, full reports and
  observations and are ignored. `fixtures/local-private/` and
  `corpus/local-private/` are ignored recursively, including metadata and reports.
  A private mod must not be added to this public catalog, even as a named/hash
  record, without explicit permission to disclose that metadata.

No Minecraft/Forge binaries, mappings, private logs, screenshots or third-party
mod JARs are embedded here or added to `LegacyBridge.jar`. The corpus contains
relative evidence locations and hashes, not those local files' contents.

The original fixture sources are in `fixtures/simple-content-1.16.5`; its genuine
Forge build is documented in [Simple content](../docs/SIMPLE_CONTENT.md). The
derived negative/control inputs have an owned, inert
[P6 generator and reproduction instructions](../scripts/P6_NEGATIVE_FIXTURES.md).
Generated inputs remain hash-pinned local build outputs, not public binary files.

## Reproduce without running original mods

Use Java 17 and the repository's pinned build versions. Run builds serially.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
$env:DEBUG = $null
.\gradlew.bat :legacy-corpus:check :legacy-corpus:installDist --no-daemon --max-workers=1
```

`check` validates the catalog and available local evidence hashes, as well as
negative/positive verifier tests. A clean checkout may lack historical P6 logs:
`missingLocalEvidence` lists every missing reference. That is not a fresh runtime
pass and must remain a reproducibility gate. Existing mismatched evidence fails
verification. HTTPS evidence content is not fetched or authenticated by `verify`;
license review and explicit acquisition are separate operations.

To acquire this exact public cohort, review the pinned licenses and URLs first:

```powershell
.\scripts\Acquire-PublicCorpusCandidates.ps1 -AcceptReviewedLicenses
```

This is an explicit network operation, not part of a Gradle build, corpus check,
game startup or conversion. It rejects redirects, oversized responses and hash
mismatches, uses publisher-hosted bytes and refuses existing output paths. If
the files are already present, reuse their verified paths; do not overwrite them.
A failed/interrupted acquisition without its final receipt is incomplete.

For a new static observation, create an empty parent under `build/corpus-runs`,
then choose a **new** child path:

```powershell
New-Item -ItemType Directory -Path build/corpus-runs -Force
& "$env:JAVA_HOME\bin\java.exe" -Xmx768m -cp 'legacy-corpus/build/install/legacy-corpus/lib/*' `
  dev.legacybridge.corpus.CorpusRunner observe `
  corpus/records/clumps-forge-6.0.0.28.json `
  build/corpus-intake/public/clumps/Clumps-6.0.0.28.jar `
  '<explicit permitted mapping-bundle directory>' `
  build/corpus-runs/clumps-new-observation
```

The runner uses `LegacyEngineService` directly: no `CLI.main`, stdout parsing,
mod classloader, installation or launcher. One worker, queue capacity one, the
engine's 64 MiB input / 128 MiB expanded / 64 MiB captured-data budgets and a
two-minute operation budget apply. `-Xmx768m` bounds this separate JVM; it is not
a promise that payload budgets alone bound JVM memory. A 150-second outer wait
cancels an overrun. A reviewed catalog member, expected source hash/size and an
explicit mapping bundle are mandatory. Nothing downloads automatically.

The existing pinned mapping bundle is **LOCAL_USE_ONLY** and is not supplied by
this corpus; follow [portable-data provenance and rights](../docs/PACKAGING_RIGHTS.md).
Its missing-cache reproducibility cannot be replaced by embedding old game code.
The current observation baseline uses source 1.16.5/36.2.42 and target
1.20.1/47.4.23, engine/runtime 0.1.1-SNAPSHOT. Other source-build evidence in the
records does not change these engine pins or admit an adjacent profile.

The runner writes full schema-2 reports to its new private directory, validates
source/output/report fingerprints and publishes `observation.json` last. A
directory without that completion receipt is an **incomplete observation**, even
if a complete converter output JAR exists. Nothing is installed. Existing
observations and versioned history are never overwritten. Engine temporary-file
cleanup remains the existing cancellation contract; interrupted audit directories
are retained for review, not silently promoted or recursively deleted.

All new observations set client/server/behavior/multiplayer to `NOT_RUN`.
Importing an observation into a new corpus version is a separate reviewed action.
Carry historical runtime proof only when the exact source, output, runtime JAR,
environment and tested behavior scope still match. Never inherit it by mod name.

## Versioning and evidence limits

Version `YYYY.MM.DD.revision` identifies a reviewed snapshot. The matching
`snapshots/<version>.json` lock pins the exact record set and SHA-256 of every
record; the CLI/check and observation runner verify it before accepting a run.
It is an integrity check, not a cryptographic signature or a replacement for
version-control review. Add a new exact
artifact as a new record; do not replace an artifact under an existing hash.
Change the snapshot revision when records, verdicts, provenance or evidence
bindings change. Keep old observations for comparison and use version control
to preserve prior snapshots. Do not rewrite a past result because a new engine
passes. The schema version changes when field meanings/contracts change.

The current original fixture imports existing P6 client/server proof bound to
public JAR SHA-256 `00cc7ebde1279c1ac3cc6ac0a54498e4598ca4f45700a6799f915ce9dddea991`.
Its scoped gameplay checks include actual crafting/consumption, block placement,
assets and en_us/pt_br tooltips; dedicated-server command checks are narrower.
They do not prove multiplayer, audible sounds, all properties or equivalence
against an executed 1.16.5 original. [P6 evidence](../docs/PUBLIC_ONLY_END_TO_END_VALIDATION.md)
remains historical, not a new game execution performed by the corpus verifier.

The analyzer's `unsupportedFeatures` and `assessment.conversionStatus` currently
include conservative/older labels even for the convertible fixture. Keep these
raw observations separate from actual converter diagnostics. Package inference,
reflection markers and absent declarations are not proof of dependency presence,
absence or complete semantic coverage. Preserve truncation indicators.

No supported-mod percentage, symbol-resolution percentage or test-count-to-mod
claim is produced. See [validation](../docs/CORPUS_VALIDATION.md) and the next
three [bounded priorities](BACKLOG.md).
