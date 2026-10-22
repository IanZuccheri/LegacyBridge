# COMPOSE-01: controlled developer QA export

This is the next finite headless continuation of LB-08 / COMPOSE-01, still
**PARTIAL**. It turns the already inspected private candidate into a retained,
hash-bound **developer QA artifact**. No composition profile, Apply button,
installer admission, gameplay adapter or game launch is enabled.

## Separate distribution and authority

`legacy-converter/src/compositionQa` is a separately compiled developer source
set. Its `CompositionQaExport` and `CompositionQaFiles` classes do not enter the
engine JAR, installed CLI distribution, runtime or public `LegacyBridge.jar`.
Both engine and product archive audits explicitly reject those classes.
The opt-in `exportCompositionQa` Gradle task is not a dependency of `build`;
normal builds run its tests, not an export or Minecraft.

The main engine adds only a package-private synchronous borrowing seam. It runs
the same complete composition preparation, including the reopened ZIP and
offline linkage gates, before giving trusted developer code a temporary byte
array. The borrowed array is cleared in `finally`, including consumer failures
and cancellation. Neither public service nor CLI exposes that seam; their
`CompositionCandidate` still exposes only proof/hash and denies publication.
This package/classpath boundary is not a sandbox against hostile code already
running in the developer JVM. No reflection or input class loading is used.

All six ordinary profiles, exact transformation rules, adapter IDs/revisions,
runtime identity, included simple-content data and candidate bytes are unchanged.
The direct ordinary logic conversion must still refuse this combined fixture.
The inspection proof retains `publicationAllowed=false`, `output=null` and
`transformationsCommitted=false`: a QA export is not a product conversion receipt.
The engine's private buffer is discarded even when QA retains a separate file.

## Explicit export

First inspect the candidate and independently review its source/candidate hashes.
Run from the repository root, one Gradle process at a time, using explicit local
mapping/API data. The default embedded simple-content vocabulary is insufficient.
For the unchanged authored fixture and current finite recipe contracts:

```powershell
.\gradlew.bat '-Djavax.net.ssl.trustStore=NONE' '-Djavax.net.ssl.trustStoreType=Windows-ROOT' `
  :legacy-converter:exportCompositionQa `
  '-PcomposeInput=fixtures/composed-logic-recipes-1.16.5/build/libs/legacybridge-composed-logic-recipes-1.16.5-1.0.0.jar' `
  '-PcomposeSourceSha256=873013592b97449b9fffd3f569cc7b7fc3309fcc9b1d241f5a230d1d45731212' `
  '-PcomposeCandidateSha256=328c475e98e80c022aeb044d55cdf1b375e62db89c0602da8fc3c00fb495eca2' `
  '-PcomposeMappingProfile=build/logic-preserving-v1/mapping-profile.properties' `
  '-PcomposeRunId=run-my-qa-01' --no-daemon --max-workers=1
```

`Windows-ROOT` is the existing Windows trust provider, not disabled TLS; omit
those two JVM properties on other systems. The task uses Java 17, a 512 MiB heap,
one synchronous job and existing cooperative operation budgets. It does not
search a cache, download mapping data or launch any game. Mapping-profile paths
are explicitly supplied local inputs under the existing local-use-only policy.

The task fixes the destination root to `build/composition-qa`. A new `run-*` ID
is required; no existing run is reused, overwritten, repaired or trusted. The
tool recreates and proves the candidate, compares both selected hashes, rehashes
the original again immediately before handoff, and leaves the original intact.
Changed hashes, unsafe paths, unsupported source metadata/code/resources,
corruption or failed static proof abort the export. Unsupported semantic input
returns a typed `BLOCKED` result with the existing proof diagnostics; operational
I/O failures remain explicit exceptions with stable `QA_*` reason prefixes.

Successful layout:

```text
build/composition-qa/run-my-qa-01/
  candidate.jar
  proof.json
  receipt.json
```

`proof.json` is the canonical schema-1 composition inspection report, including
the existing schema-1 TransformationLog. The separate schema-1
`LEGACY_COMPOSITION_QA_EXPORT` receipt pins source, candidate and proof hashes,
lists all three files and states `EXPORTED_FOR_QA`, `installationAllowed=false`,
`runtimeValidation=NOT_RUN`. These unsigned records are local evidence, not
installation authority, certification, an activation record or a runtime result.

## Filesystem, cancellation and failure scope

The Java entrypoint additionally requires absolute normalized paths, the exact
`composition-qa` leaf name, a bounded run ID, and existing real parent directories.
Symlinks/junctions/reparse points and game/installation path components such as
`mods`, `staging`, `input`, `transactions` and `saves` are refused. The tool creates
only the QA root leaf if absent; it never creates arbitrary parent hierarchies.

Files are first written to an operation-owned `.pending-*` directory. Every file
is closed/forced, reread and hash-checked, then the exact complete set is checked
again before handoff. Individual files are limited to 128 MiB; inherited mapping,
archive, capture and deadline budgets still apply. These do not guarantee a hard
allocation ceiling for the shared library API; the explicit task has its own JVM.

JDK 17 on this Windows returns `null` for `BasicFileAttributes.fileKey()`.
Cleanup therefore uses real hard-link witnesses and `Files.isSameFile`, including
a directory canary. Matching bytes alone never authorize removal of a replacement.
Filesystems without hard-link support are unsupported by this export tool;
there is no unsafe copy fallback. Unknown entries/replacements are left intact,
and cleanup errors are attached to the failure rather than silently ignored.

The final same-parent directory move uses neither `REPLACE_EXISTING` nor
`ATOMIC_MOVE`. As specified by [Java 17 Files](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html#move(java.nio.file.Path,java.nio.file.Path,java.nio.file.CopyOption...)),
portable atomicity and durability are not assumed. Existing destinations block.
Only a complete named `run-*` directory returned by a successful operation is a
handoff. Cancellation before publication produces no completed export; cancellation
after the publication boundary is refused, not presented as a successful cancel.

Ordinary pre-commit failure/cancellation cleans files whose ownership is still
proved. After witness release, a move/cleanup failure or racing cancellation may
leave a complete but untrusted `.pending-*` directory; the exception identifies
that remainder. A process crash, full disk, unsupported filesystem or locked file
can also leave `.pending-*`/`.qa-owner-*.ref`. Never treat their embedded receipt as
completion, scan them as mods or automatically delete/promote them. There is no
crash recovery command or hostile-local-process sandbox in this finite tool.
Directory fsync/power-loss durability is not guaranteed. This is deliberately
separate from the product's transactional installation and recovery contracts.

## Acceptance and continuation

The [export ledger](evidence/compose-export-2026-09-07.json) separates synthetic
filesystem tests, actual Forge fixture conversion, archive audits and regression
hashes. Injected write failures are not claims of a physically full disk. No
original/converted class was executed during export or its static tests.

Client/server discovery, cooking consumption/time/XP, foil/helper/super behavior,
resource reload, visuals, translations and multiplayer remain **NOT_RUN** for
this composition. The original fixture's behavior oracle is unchanged. The next
headless work in COMPOSE-01 is the original/target comparison harness and strict
artifact-bound observation checker; native execution still needs authorization.
Do not advance COMPOSE-01 to ACCEPTED, unlock LINKAGE-GENERAL or promote the
composition into public installation based on this developer export.
