# LB-05: preserved admission binding and explicit recovery

Slice `INSTALL-PROMOTION / JOURNAL_ADMISSION_BINDING`, 2026-09-13.
Follow-up: [PRESERVED_ADD_ONLY_INSTALL](PRESERVED_ADD_ONLY_INSTALL.md) now provides
a separate explicit developer installation API. This admission API and its inert
results still never confer Apply authority by themselves; the handoff below is historical.
This extends the [independent inspector](PRESERVED_ARTIFACT_ADMISSION.md) with
real private payload preparation, persistent exact-input binding, read-only
inspection and explicit recovery. It does **not** install preserved code, migrate
V1/V2 installation journals, grant Apply, or enable another conversion profile.
Current evidence: [versioned ledger](evidence/journal-admission-2026-09-13.json).

2026-09-19 follow-up: [preparation workflow](PRESERVED_PREPARATION_WORKFLOW.md)
adds a bounded single-use `Preparation` handle to this same store, exposing real
progress and cooperative cancellation. The complete observation precedes an
explicit publication barrier: accepted cancellation never yields ready admission,
while a bound interrupted journal remains recoverable. Journals/recipes are not
migrated, and preparation still grants no installation authority.

## Worker API and authority

`PreservedAdmissionStore` is in the public product module and has three synchronous
worker-only operations (no CLI/stdout bridge or render-thread work):

```java
PreservedAdmissionResult prepare(Path gameDir, PreservedArtifactRequest selection);
PreservedAdmissionResult inspect(Path gameDir, Path transaction,
                                String expectedPlanSha256, MappingData reviewedData);
PreservedAdmissionResult resume(Path gameDir, Path transaction,
                               String expectedPlanSha256, MappingData reviewedData);
```

The caller explicitly selects the completed session result, its original and both
hashes. Data must be `MappingData.reviewedBundle(directory, manifestSha256)`.
No included-data, unpinned bundle, Gradle profile/cache or download fallback exists.
Resume/inspect require a separately selected journal hash and the reviewed data pin;
the journal never chooses a data path or imports a persisted PASS into the engine.
Relocating the explicitly selected bundle is permitted only with identical pinned
manifest and validated component bytes. Private mapping/API data is not copied into
the journal or public JAR. Existing rights/distribution limits remain unchanged.

Every successful call reloads the actual reviewed API/mapping bundle and invokes
the existing `PreservedArtifactInspector` against the original and private payload.
Original, staged and private files are rehashed and must be independent. The store
does not call conversion or duplicate transformation rules. Its declared profile,
recipe, schema, target/source baseline, mod identity and runtime ABI must match.
Semantic refusals retain the inspector's structured error code.

Construction of records or edited JSON does not confer approval/ownership of an
installed file. All results return `installationAllowed=false` and
`safeToClose=false`. There is no destination filename, predecessor, uninstall,
restart/exit call, loaded-set mutation or new Forge discovery mechanism.

## Persisted format (separate from installation V1/V2)

```text
legacybridge/transactions/<uuid>/
  admission-payload.jar          independent, bounded, complete candidate copy
  admission-v1.json              schema-1 exact binding, no PASS authority
  admission-checked-v1.json      schema-1 observation bound to plan hash
  pending-<uuid>.json            private partial/complete publication tail
  guard-<uuid>-<index>.ref        retained only when guard cleanup was interrupted
```

`admission-v1.json` has report type `LEGACY_PRESERVED_ADMISSION_BINDING` and fixes
`logic-preserving-v1`, `INDEPENDENT_LOGIC_READBACK_V1`, inspection schema 1,
Minecraft 1.16.5/Forge 36.2.42 → Minecraft 1.20.1/Forge 47.4.23/Java 17,
`legacybridge_runtime`/`0.1.1-SNAPSHOT`, identity/version, original/staged paths
relative to their reserved areas, both JAR hashes, fixed payload name, and reviewed
data-manifest hash. No arbitrary absolute source/destination/data path is imported.
Unknown fields, schema/recipe/ABI/baseline, traversal, mixed legacy journals and
identity mismatches are refused. The observation contains the transaction, plan
hash and candidate hash; it is not consulted instead of fresh validation.

The shared installer JVM/file lock serializes these operations with V1/V2; it does
not queue them. Concurrent callers get a structured refusal. Run from the existing
product coordinator, not inside another OperationControl scope or the engine worker.
This API is not yet wired to player controls. No shutdown barrier is claimed for
this preparation API: interruptions retain evidence and never publish into mods.

Copy uses CREATE_NEW and force; complete JSON is forced privately and linked to a
new immutable name through the existing `TransactionFiles` implementation. There
is no replace-capable move or copy fallback. Only matching owned directory guard
markers are cleaned; no admission canary is written in `mods`. Guards use fixed
indexes for game/root/input/staging/transactions/reports/transaction. Their mapping
differs deliberately from installation's mods guard and is not interchangeable.

## Recovery states and missing-file policy

| Observation | Result and explicit action |
| --- | --- |
| Private copy or JSON tail failed before binding | RECOVERY_REQUIRED; retained orphan is not resumable. Review privately; no blind rewrite/deletion. |
| Complete binding/payload, missing observation | Fresh inspection returns INCOMPLETE without journal writes; explicit resume rechecks and adds the missing observation. |
| Complete matching observation | Inspect/resume still reload data and verify actual bytes; RECHECKED_NOT_INSTALLABLE, not installed/activated. |
| Missing/changed original, staged file, payload, data or journal | RECOVERY_REQUIRED; no overwrite, payload replacement or invented reconstruction. |
| Changed complete observation | Refusal, not replacement or presumed success. |
| Valid retained directory guards after process interruption | Explicit resume checks identities and cleans matching markers before creating new guards. |
| Legacy V1/V2 or mixed journal | Refusal; old APIs remain responsible for their own schema. No implicit migration. |

Repeated successful resume adds no new payload or duplicate observation. Read-only
inspection does not alter the journal (the cooperative lock file may be created).
Do not delete originals, staging or reviewed data on the assumption that a private
payload makes them optional. There is no restore operation here because admission
has not installed/retired a file; real install/restore requires a separate contract.
The old installer/history may diagnose this new journal as unrecognized; they
cannot reinterpret it as a managed installed artifact. UI routing is not promoted.

## Bounds and actual filesystem limitation found

One candidate per operation; 64 MiB maximum copied/hashed artifact, 128 MiB inflated
and 64 MiB captured cumulative archive budgets, two-minute cooperative deadline.
The inspector's stricter 8 MiB capture and finite class/method limits still apply.
At most 32 retained transactions, 128 direct transaction entries and 64 KiB JSON.
Partial recovery files count toward retention; there is no automatic evidence GC.
These are work/payload bounds, not a hard retained-heap/RSS or blocking-I/O timeout.

The isolated public-JAR probe exposed a real long-path limitation in JDK 17.0.12:
`WindowsPath` adds the extended prefix beyond 247 characters;
`WindowsFileStore.equals` compares root spellings, so a long guard file's
`\\?\C:` and its shorter directory's `C:` compare unequal. The unchanged existing
same-filesystem guard therefore refused before finishing preparation. Exact JDK
sources (`lib/src.zip`, SHA-256
`24fee80297ef15a886e283a31ac4eca18888ca8e942a21afb3f60a8535dc573b`)
and an inert read-only probe confirmed this, not an actual cross-volume request.

The new admission API now checks all derived Windows guard paths against that
reviewed 247-character boundary **before any transaction/guard write**, with an
explicit request for a shorter game directory. It does not reinterpret volume
identity, bypass the link check or change V1/V2. Long-path admission remains
unsupported. The corrected disposable probe uses a shorter directory.

This is not an OS sandbox: external non-cooperating file replacement can still race
Java checks/calls. File force does not establish a portable parent-directory fsync,
multi-file atomic transaction or universal power-loss guarantee. See the consulted
primary [Java 17 Files contract](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html)
and [FileChannel.force](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/FileChannel.html#force(boolean)).

## Validation and next implementation

Final serial run: 71 focused product cases passed (28 new), zero failures/errors/
skips. Separately, 75 contract cases passed and two Windows symlink-privilege cases
were skipped. All modules assemble and public/runtime/engine/CLI audits pass.
The helper halted with expected exit 81; two fresh JVM resumes exited 0, verified
six classes/25 methods, removed 14 retained guard names and left mods/caches empty.
Repeated resume changed no files. These are not supported-mod or native QA counts.

Small real authored fixtures exercise binding, revalidation, semantic tampering
with matching new hashes, modified data components, aliases, unknown schemas/ABI,
write faults, late changes, mixed journals and repeated recovery. A separate
developer-only child JVM probe uses the actual public JAR, deliberately halts
after a complete binding, then reopens/resumes in fresh JVMs. It is not a mod or
game process and is never packaged; executed results and exact hashes are in the
ledger. Unit fault injection, process interruption and native behavior are separate.

Next slice: **LB-05 / INSTALL-PROMOTION / PRESERVED_ADD_ONLY_INSTALL**. Implement
an explicit developer-only add-only route for this exact profile, with player/session
approval provenance, current installed-set/dependency/loaded-file checks, versioned
commit ownership and recovery. Keep the public UI's simple profile unchanged until
its own promotion gates pass. Do not repeat binding/readback, generalize composed
or cohort Apply, retire loaded files, introduce automatic restart or assume native
acceptance. COMPOSE-01, DEPS-COHORT, source/target behavior and final-artifact native
QA remain required; this slice does not complete prompt 5 or expand conversion.
