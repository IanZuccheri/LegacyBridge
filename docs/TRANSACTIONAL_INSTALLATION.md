# P5: bounded transactional installation and explicit recovery

2026-09-13: [independent preserved-artifact readback](PRESERVED_ARTIFACT_ADMISSION.md)
is now available in the engine and developer CLI. Its result is inert scoped
evidence, not installation authority. The installer below and journals V1/V2
still admit only simple-content-v1. The new
[admission binding/recovery API](PRESERVED_ADMISSION_JOURNAL.md) now prepares private
preserved artifacts and revalidates them, but has no installation authority or
V1/V2 migration. The separate [preserved add-only developer API](PRESERVED_ADD_ONLY_INSTALL.md)
now implements explicit installation/resume/restore for logic-preserving-v1.
It has its own approval and journal, not a relaxation of the installer below or
public Apply. Native acceptance remains pending. A separate
[preserved managed-update API](PRESERVED_MANAGED_UPDATES.md) now supports a finite
owned/unloaded add-only predecessor with backup and recovery. Its own journal is
not interpreted by V1/V2. The [developer update coordinator](PRESERVED_UPDATE_COORDINATOR.md)
now supplies explicit review/consent, worker/lease, results and inert receipts;
native update-form integration and runtime acceptance remain pending.

Headless continuation: [per-job installation receipts](JOB_INSTALL_RECEIPTS.md)
fix presentation persistence after Apply/recovery. The authoritative installer
journal and adapter ABI remain unchanged. The subsequent [history reader](JOB_HISTORY_READ.md)
is read-only and cannot authorize Apply. Native history UI, reconciliation and
artifact-bound runtime acceptance remain separate gates.

This milestone extends the implemented installer, not `simple-content-v1`.
Source remains Minecraft 1.16.5 / Forge 36.2.42; target remains Minecraft
1.20.1 / Forge 47.4.23 / Java 17. Adapter/runtime identity and all existing
conversion/report schemas are unchanged. Installation journals introduce V2;
the V1 `AddOnlyInstaller.apply` six-argument API and `inspect` remain available.

## Implemented API

`dev.legacybridge.product.install.TransactionalInstaller` provides:

```java
TransactionResult apply(Path gameDir, List<InstallSelection> selections,
                        InstallSession session) throws InstallException;
TransactionResult inspect(Path gameDir, Path transaction,
                          InstallSession session) throws InstallException;
List<TransactionResult> list(Path gameDir, InstallSession session)
        throws InstallException;
TransactionResult recover(Path gameDir, Path transaction, InstallSession session,
                          RecoveryAction action) throws InstallException;
```

`InstallSelection` carries original/staged paths and both engine-produced
SHA-256 fingerprints. An optional `replaceTransaction` identifies an existing
managed receipt, not an arbitrary installed filename. `InstallSession` carries
the actual loaded-ID and loaded-file snapshots plus whether a world is open.
The trusted controller supplies this data; a mod JAR or editable compatibility
report cannot authorize installation. Construction of these records performs
no I/O.

`TransactionResult` reports one existing transaction path, status, indexed
per-entry results, and an optional structured `EngineError`. `InstallException`
is used when no valid owned transaction exists, such as invalid workspace,
open world, invalid request size, or a lock conflict before directory creation.
Per-entry IDs/versions/hashes identify the requested converted artifacts;
restored prior artifacts are described in the journal's `previous` records.

`safeToClose()` is true only for `COMMITTED` or `RESTORED` with no error. A
published JAR plus a failed receipt is not permission for automatic close.
`COMMITTED` means installed for a later launch, not activated or gameplay-tested.
`PREPARED`, `PARTIAL`, `MANUAL`, and `RECOVERY_REQUIRED` never qualify.

All operations run outside the render thread. Apply/recovery are not
cancellable once started. A controller must obtain explicit confirmation,
prevent conflicting installation/world actions, and preserve the operation
when a screen closes. These APIs neither call `System.exit` nor restart a
launcher, register new content, execute original mod code, or add discovery
services. Inspect/list do not change JARs or journals (they acquire or create
the cooperative lock file); recovery is always explicitly selected.

## Four independent candidates, not a multi-file atomic transaction

At most four candidates are accepted. Every candidate must independently pass
the existing generated-profile metadata, exact bootstrap recipe, offline ASM,
adapter binding, runtime plan, and resource checks. Profile outputs depend only
on the pinned platform and implemented runtime; new cross-candidate conversion
features are not enabled.

Under the same cooperative JVM/filesystem lock used by V1, the installer:

1. Rehashes the original and staged result and verifies canonical containment.
2. Creates independent complete private payload copies. An update also creates
   a separately allocated backup of the prior managed artifact, forces it,
   rehashes it, and validates its bytecode/resources/bindings.
3. Preflights **all** selected candidates and the projected installed set before
   any JAR is added or retired. Duplicate IDs, platform/runtime mismatch,
   incompatible versions, missing required known dependencies, unknown range
   comparisons, and dependency cycles refuse the operation.
4. Publishes the complete immutable V2 plan before beginning installation.
5. Revalidates fingerprints, loaded-file guards, directory identity guards, and the
   installed set; then publishes/updates entries in deterministic index order.
6. Stops at the first failure. Already completed publications remain recorded
   and visible; no automatic rollback or blind retry occurs.

The known dependency graph follows Forge's owning-JAR nodes, not separate nodes
for IDs sharing a JAR. Mandatory requirements and present optional requirements
are checked. Both sides are conservatively considered. Inbound ordering other
than `NONE` is still refused rather than broadening the supported ordering
contract. Unknown qualified versions/ranges, language loaders and nested
dependency closures remain manual/refusal cases. The exact audited LegacyBridge
`srgutils` JiJ remains the one recognized nested-dependency exception.

This is bounded checking of the represented metadata grammar, not a universal
simulation of arbitrary Forge feature predicates, custom locators, services,
or third-party runtime behavior. Existing conservative direct-`mods` layout
rules remain: ordinary subdirectories, symlinks/junctions, unreadable archives,
and truncated inventories refuse installation. Normal Forge dependency/discovery
validation on the next launch is still a separate check.

## Managed updates and restoration boundary

Updates are permitted only when the selected V1/V2 receipt owns the same mod ID,
the exact prior hash/profile is valid, and the installed file still shares file
identity with its managed payload. A byte-identical independent replacement is
**not** sufficient proof of ownership. Both loaded IDs and loaded file paths/
aliases are checked. Loaded or unknown artifacts are never retired, overwritten,
or repaired. A strictly newer numeric version is required; same-version/no-op,
downgrade and uncomparable qualified-version updates are `MANUAL`.

Before removing an old installed directory entry, its independent backup is
complete and forced, and a `retired` hard link preserves the owned old identity.
The exact old destination is then removed, and the new JAR is linked under a
new UUID/index-based name. No replace-capable move is used. Because there is a
crash window between retiring the old name and publishing the new one, an
update with an existing **mandatory inbound consumer is refused**. Such a set
needs a separately validated pre-discovery/offline recovery mechanism. This
milestone adds none.

`RESTORE` removes only matching, still-owned, unloaded new publications and
reinstates matching old owned identities where an update retired them. It
checks the projected prior dependency set first; removing a required dependency
or creating a mandatory-consumer update gap is refused. Restoration is
repeatable. A restored transaction cannot later be silently resumed as a new
installation.

The independent backup preserves old bytes even if another hard-link name is
lost. Automatic restoration also requires the previous managed payload identity
to remain valid. If that identity is lost/replaced/corrupted, recovery is manual:
the backup is retained and unknown files are not overwritten. This intentionally
does not claim arbitrary damaged-disk reconstruction or full backup management.

## Journal, checkpoints, and truthful recovery

An owned transaction UUID contains:

```text
plan-v2.json                         immutable schema-2 plan
payload-<index>.jar                  complete validated new artifact
backup-<index>.jar                   independent old bytes, updates only
retired-<index>.jar                  old owned identity, updates only
checkpoint-<state>-<index>.json      immutable per-entry observations
checkpoint-committed--1.json         completed installation receipt
checkpoint-restored--1.json          completed restoration receipt
pending-<uuid>.json                  non-authoritative private write/tail
```

Plan and checkpoint writes first create and force a complete private file,
then publish a new hard-link name without replacing anything. A partial
`pending` tail is never parsed as a plan/checkpoint or mistaken for success.
Schemas, exact field sets, profile/platform/runtime versions, bounded paths,
fingerprints, contiguous indexes, and UUID-owned names are checked. Checkpoints
reference the plan hash. A checkpoint already present must match exactly; it
is never overwritten.

The plan includes original/staged paths relative to their respective reserved
areas, both fingerprints, requested identity/version, derived destination name,
and optional prior managed receipt/schema/index/hash/version. Recovery cannot
select a destination outside the derived operation-owned filenames.

Inspection derives state from actual file identities and hashes, not merely a
state string. A publication with a missing final receipt remains `PARTIAL` and
cannot close automatically. Explicit `RESUME` validates the complete projected
set, skips proven completed entries, finishes the remainder, and records commit.
Explicit `RESTORE` validates and restores the previous safe set. Invalid journals
or file-identity changes remain visible errors; no automatic filesystem repair
is performed while listing.

A missing predecessor owned by a still-live, validated V2 update is reported as
`MANUAL / ARTIFACT_SUPERSEDED`, pointing to the successor journal. Resolve that
successor first; recovering an earlier transaction must not undo or misreport
the later approved operation. Lookup is bounded and non-recursive. V1 receipts
whose destinations have been retired remain conservative inspection diagnostics
until their V2 successor is restored; they are not silently rewritten.

V1 records remain visible through read-only listing with their actual validated
version and installed path. They are managed update candidates, but they are
not presented as V2 recovery transactions. The original V1 inspection remains
available. A V2 update can use a V1 receipt without rewriting it.

## Filesystem limits and validation gates

New JAR names are hard links to complete private payloads outside `mods`.
Providers without same-filesystem hard-link support are refused; there is no
copy/overwrite fallback. Directory keys are checked where the provider exposes
them. This exact Windows JDK returns no directory file key: the fallback creates
unique operation-owned, non-JAR canaries with retained hard-link references,
checks canonical paths and canary identity around commit boundaries, and removes
only matching owned canaries. It does not invent a native Windows directory ID.
Normal completion cleans those markers; interrupted operations retain references
for explicit recovery cleanup. An unexpectedly moved/replaced marker is a visible
refusal, not permission to remove an unknown file. No canary is written in a world.
Windows sharing violations, access denial, disk exhaustion, and interrupted
receipts are reported; LegacyBridge does not unlock or kill other processes.

Each input/managed JAR is capped at 64 MiB; at most four inputs are selected.
Private new payloads are capped at 256 MiB and total payload/backup reservations
at 512 MiB. Cumulative archive work is capped at 2 GiB inflated / 1 GiB captured
per operation, with a five-minute cooperative pre-publication deadline. The
underlying 64 MiB staged-capture and per-entry archive limits still apply.
These are payload-work limits, not a hard JVM heap or OS-call timeout. Directory
inventory remains bounded to 1,024 entries / 256 JARs, 4,096 dependency records,
32 transaction directories, and 128 direct entries per transaction. Journals
are at most 64 KiB. Recovery evidence and private tails are retained pending an
explicit review/cleanup policy, not silently treated as cache.

JVM/file locks coordinate cooperating installers only. Java does not provide
an atomic identity-checked unlink across arbitrary hostile filesystem changes;
revalidation is not a sandbox against an uncooperative process replacing files
between checks and calls. Forced file contents do not provide a universal
parent-directory fsync or physical power-loss guarantee. `ATOMIC_MOVE` is not
a substitute for no-replace semantics: its handling of an existing target is
provider-specific. See [Java 17 Files](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html)
and [FileChannel.force](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/FileChannel.html#force(boolean)).

Small inert unit fixtures cover batch preflight, partial publication, repeated
resume/restore, managed V1/V2 upgrades, independent backups, loaded/unknown/
identical-copy refusals, version/closure/ordering refusal, partial journal tails,
directory replacement and simulated I/O boundaries. Simulated exceptions do not
prove a physical disk-full condition. Actual Windows sharing-denial tests,
separate-JVM halt/recovery tests, ordinary packaged Forge discovery, client
behavior and manual launcher relaunch must be reported separately with their
executed evidence. No test declaration in this document marks a gate passed.

Not supported: loaded updates, unrelated-file replacement, dependent multi-JAR
atomic commit, automatic startup repair, automatic restart, hot activation,
world migration, arbitrary JiJ, universal modpack semantics, or new conversion
categories. Publication/redistribution and mapping-data gates are unchanged.
