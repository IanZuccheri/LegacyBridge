# Corpus validation — 2026-09-06

Outcome: corpus **2026.09.06.1** contains **three authorized real releases** and
**six project fixture cases** (five valid JAR identities plus one corrupt
non-mod input). All three real releases are analyzable; **none is convertible**.
Three fixture identities convert and pass output bytecode checks. Only the
original finite-content fixture carries scoped historical client/server startup
and behavior proof. No record claims multiplayer validation.

This stage implements corpus infrastructure, provenance, evidence validation
and prioritization, not new conversion support. [Corpus](../corpus/README.md),
[schema](../corpus/SCHEMA.md), [next three slices](../corpus/BACKLOG.md) and the
[machine-readable validation ledger](../corpus/validation/2026.09.06.1-static.json)
record the scope and reproducible hashes. Nothing was uploaded or published.

## Build and tests

Java 17, Gradle 8.8, source engine baseline MC 1.16.5 / Forge 36.2.42, target
MC 1.20.1 / Forge 47.4.23, runtime/adapter version 0.1.1-SNAPSHOT. Exact legacy
publisher releases retain their separately observed source-build versions.

Serial commands:

```text
gradlew.bat build :legacy-corpus:installDist -PverifyMappingEnvironment=true --rerun-tasks --no-daemon --max-workers=1
gradlew.bat build :legacy-corpus:installDist -PverifyMappingEnvironment=true --no-daemon --max-workers=1
```

The forced full build passed in 1m43s (64 executed tasks). The final code build
passed in 28s (27 executed, 37 up-to-date), rerunning all corpus tests after a
diagnostic-label fix and two added regressions. Logs and their SHA-256 values
are in the validation ledger. Both builds verified the actual pinned Forge API
baselines and the final product/runtime archive checks.

| Module | Current tests | Passed | Failed | Skipped |
| --- | ---: | ---: | ---: | ---: |
| legacy-converter | 358 | 357 | 0 | 1 |
| legacy-corpus | 84 | 83 | 0 | 1 |
| legacy-product | 82 | 82 | 0 | 0 |
| legacy-runtime | 30 | 30 | 0 | 0 |
| legacy-workflow | 32 | 32 | 0 | 0 |
| Total | 586 | 584 | 0 | 2 |

The two skipped cases require native Windows symlink creation privileges. They
are not reported as passed; other unsafe-path/alias cases ran. Existing pinned
Forge deprecation/removal warnings remain and are not hidden. Test totals are
automated case counts, **not a count of supported mods**.

New tests cover bounded UTF-8/JSON, duplicate keys/hashes/IDs, record locks and
stale versions, missing/changed evidence, wrong source/output bindings, invalid
stage chains, authorization sources, unsafe paths, unknown SPDX expressions,
no-overwrite, changed original identity, corrupt JARs, explicit missing mappings,
no inherited runtime proof and success warnings versus actual refusal blockers.

## Static engine rerun and CLI equivalence

The final installed development runner executed all nine exact inputs serially,
in separate Java 17 JVMs with `-Xmx768m`, using the existing explicitly selected
LOCAL_USE_ONLY mapping bundle. It reused `LegacyEngineService`; no original
classloader, game, installer, launcher or downloader was invoked.

Final receipts are under:

```text
build/corpus-runs/2026.09.06.1-service-final/<recordId>/observation.json
```

- Eight analyses completed; the corrupt non-ZIP failed with `MALFORMED_ZIP` and
  conversion was not attempted.
- Three fixture outputs reproduced their exact historical SHA-256 values and
  reported bytecode validation PASSED. Success scope warnings are diagnostics,
  not blockers.
- Two negative fixtures and all three real mods remained blocked without output.
  Worldgen and missing-third-party-dependency cases were not made permissive.
- All nine originals retained their exact hashes. No existing output or versioned
  record was overwritten, no result installed, and all new runtime stages remain
  NOT_RUN.
- All six real-mod report pairs (analysis and conversion for each of the three
  releases) were byte-identical between the earlier installed CLI and this typed
  service runner. The original baseline fixture's CLI/service reports and output
  also reproduce exactly. This does not turn an equal refusal into compatibility.

The first auxiliary service run exposed a presentation bug: successful finite
scope warnings were copied into `blockers`. It was corrected only in the corpus
runner, with positive/negative regression tests. The final `-service-final`
receipts supersede that first observation batch; raw engine reports, conversion
rules and output hashes did not change. Old private audit directories are kept,
not overwritten or silently deleted.

## Corpus integrity, rights and packaging

The locked catalog verifies nine unique input identities and three distinct real
projects. At this checkout, all referenced local historical evidence hashes are
available and match (`missingLocalEvidence: []`). On a checkout without those
private/local logs, their absence is explicitly reported, not treated as a new
runtime pass. The verifier does not automatically download evidence or legally
certify the content of URLs.

Clumps and Controlling retain exact MIT notices; AppleSkin retains its Unlicense
notice. Official publisher versions, SHA-512 values, acquired SHA-256 values,
release-associated source commits, and source build evidence are pinned. Binary
source reproducibility was not established. AppleSkin was built against
1.16.4/Forge 35.1.4 according to its release source while the publisher lists
1.16.5 support; it remains an adjacent/ambiguous source case.

Only local authorized acquisition/static work was performed. Third-party JARs,
the local mapping bundle, private inputs and raw game logs are ignored; corpus
metadata and full upstream license notices are the versioned material. The
fixture directory's old unconditional redistributable wording was corrected to
match the repository's All Rights Reserved license. No license was silently
changed and no Minecraft/Forge code was embedded.

The public JAR remains byte-identical before/after:

```text
LegacyBridge.jar (975498 bytes)
00cc7ebde1279c1ac3cc6ac0a54498e4598ca4f45700a6799f915ce9dddea991
```

`legacy-corpus`, its fixtures, licenses, real samples, reports and development
logging backend are not included in that artifact. Existing package audits pass.
No player needs a new or second JAR for this development evidence work.

## Remaining limits and next steps

No new client/server/gameplay tests were run in this corpus task: it changed no
runtime behavior, and P6 proof is retained only for the exact unchanged public
and converted fixture hashes. Native P6 screenshots remain real historical
evidence, not regenerated mockups. No original 1.16.5 behavior baseline or
multiplayer parity has been established for the real cohort.

The current converter stops at its first safety refusal. Metadata fixes alone
would unlock zero whole real mods. Source review shows lifecycle/events in all
three, configuration only in AppleSkin, and substantial different downstream
requirements. The selected backlog is B01 metadata/side admission, B02 finite
lifecycle delivery, B03 the complete exact-Clumps XP behavior cluster after its
entity/state/spawn/render prerequisites. Their reach, dependencies, semantic
risks, effort estimates and acceptance gates are explicit and provisional.

The three-project sample cannot estimate ecosystem coverage. No broad feature
was enabled to improve metrics. Source rights, mapping setup, target behavior
and multiplayer gates remain independent. **Status: experimental/Beta.**
