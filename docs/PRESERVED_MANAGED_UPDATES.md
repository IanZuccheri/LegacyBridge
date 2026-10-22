# Preserved managed updates: finite developer API

Slice **LB-05 / INSTALL-PROMOTION / PRESERVED_MANAGED_UPDATES**, 2026-09-19.
This implements actual file replacement, backup and explicit recovery for one
unloaded `logic-preserving-v1` artifact previously installed by the preserved
add-only installer. It is not a new conversion profile or public Apply option.
Native acceptance remains pending; no Minecraft process is needed or launched.
Follow-up: [the update coordinator](PRESERVED_UPDATE_COORDINATOR.md) now connects
this backend to explicit developer review/consent, workers, receipts and history.
The native update form is still pending; public Apply remains unchanged.

## API, authority and reuse

`PreservedUpdateInstaller` is synchronous, worker-only and non-cancellable once
accepted. The trusted caller obtains **new explicit consent**, supplies a stable
actual `InstallSession`, excludes world/load/exit actions and waits for safe
completion. The API does not create a worker, controller, shutdown hook, restart
provider or discovery service. Coordinator integration is a separate implemented
slice linked above; native developer-screen integration remains pending.

```java
var approval = new PreservedUpdateApproval(newAdmissionApproval,
                                          priorInstallDirectory, priorPlanSha256);
var result = updates.apply(gameDir, approval, reviewedFullData, currentSession);
var observed = updates.inspect(gameDir, updateDirectory, updatePlanSha256,
                               approval, reviewedFullData, currentSession);
var restored = updates.recover(gameDir, updateDirectory, updatePlanSha256,
                               approval, reviewedFullData, currentSession,
                               RecoveryAction.RESTORE); // or explicit RESUME
```

An approval UUID records the caller's consent, not authentication or a signature.
An old report or admission does not mint new consent. The same nonce cannot be
used for another preserved add-only/update transaction, even after restoration.
Inspection/recovery require the selected update plan hash and original approval.
No authority is rehydrated by reading history.

The implementation reuses the existing admission store/independent inspector,
hash/identity checks, unloaded-file guards, bounded installed-set inventory,
installation lock, transaction copy/link/JSON/directory guards and numeric-version
grammar. It does not call the simple-content installer or weaken its bootstrap
validation to admit preserved code. Existing transformation rules, mappings,
adapters, ABI, CompatibilityReport and TransformationLog schemas are unchanged.

## Preconditions

- The predecessor must be a committed preserved **add-only schema-1** operation,
  with no restore intent, selected by canonical transaction path and exact hash.
  Simple V1/V2 predecessors and update-of-update chains are refused in this slice.
- Both old and incoming admissions are independently re-read against original,
  staged and private bytes plus the same explicit reviewed full mapping bundle.
  Required old inputs cannot be discarded just because a backup exists.
- The ID must match; version must be strictly newer in the implemented numeric
  grammar and output bytes must differ. Same version, downgrade or incomparable
  qualifiers are refused. No inferred migration of saved worlds/configuration.
- Loaded IDs, loaded-file aliases, open worlds, unknown replacements, changed
  plan/ABI/data, mixed schemas, unsafe canonical paths and unsupported installed
  layouts block the operation. Identical bytes alone do not prove ownership.
- Projected dependency versions, both sides, ordering and duplicate IDs are
  checked using the unchanged installed-set validator. **Any mandatory inbound
  consumer blocks an update**, because retirement creates a possible missing-ID
  window before the next publication. An ordinary screen cannot repair a Forge
  instance that fails discovery before reaching that screen.

## Ordered commit, not a multi-file atomic transaction

The operation reserves a new owned directory and preflights the installed set.
Before any old publication is removed it makes and forces two independent copies:
the new candidate payload and an old-byte backup. Hashes are rechecked; neither
copy may alias source/staging/admission/payload files. The old managed hard-link
identity remains separately available in its original private payload.

It then publishes the immutable update plan, records **RETIRE_STARTED**, retains
the old owned identity through a `retired` link, removes only the exact unloaded
old destination, publishes the complete new payload under a new derived name,
and records COMMITTED. There is no overwrite-capable move or copy into `mods`.
Inputs, predecessor journals and unrelated JARs are never rewritten. The backup
is independent storage; the retired link is identity evidence, not another backup.

Directory identity/canary guards, source/data/plan hashes, installed-set snapshot
and loaded-file checks surround mutation boundaries. A failure stops immediately
and leaves recovery evidence. The API does not silently roll back or retry.

## Separate schema and explicit recovery

```text
transactions/<update UUID>/
  preserved-update-v1.json                 immutable exact update plan
  update-payload.jar                       independent new bytes
  update-backup.jar                        independent old bytes
  update-retired.jar                       old managed identity (when retired)
  preserved-update-retire_started-v1.json   durable intent before unlink
  preserved-update-committed-v1.json
  preserved-update-restore_started-v1.json  durable intent before restore unlink
  preserved-update-restored-v1.json
  pending-*.json / guard-*.ref              retained private recovery material
mods/legacybridge-preserved-update-<modId>-<UUID>.jar
```

`LEGACY_PRESERVED_MANAGED_UPDATE`, schema 1, binds predecessor ID/plan hash,
new admission ID/hash, approval UUID, both versions/hashes, exact profile/recipe,
baselines/ABI and derived names. Checkpoints have their own exact type/fields and
bind the update plan hash. This is not migration/reinterpretation of preserved
add-only or simple V1/V2 records; mixed/unknown contracts are refused even when
a caller explicitly supplies the altered document's new digest.

| Observed condition | Result / explicit action |
| --- | --- |
| Incomplete copies or no complete plan | RECOVERY_REQUIRED; orphan retained, old publication untouched |
| Plan ready, old publication intact, no retirement intent | PREPARED; resume or restore |
| Retirement intent, old/new transition incomplete | PARTIAL; resume or restore after fresh readback |
| Owned new publication and commit checkpoint | COMMITTED, pending next launch |
| Restore intent, new removal or old relink incomplete | RESTORING; RESUME is refused before guard cleanup |
| Old owned publication restored, new absent, matching checkpoint | RESTORED; repeat restore is idempotent |
| Lost/corrupted/unknown identities or contradictory checkpoints | RECOVERY_REQUIRED; never reconstruct blindly |

Restore first checks the projected old dependency set and records restore intent.
It removes only the owned unloaded new destination and re-links the proven old
identity. It does not overwrite an unknown file occupying either destination.
Automatic restoration requires the old private payload/retired identity to remain
valid; if that identity is lost, the independent backup is retained for manual
recovery, not treated as permission to invent ownership.

An active update (including PREPARED) blocks earlier predecessor recovery and a
second update of that predecessor. Resolve the successor first. Only a matching
restored successor with no new publication releases that restriction. Old journal
bytes are unchanged. Add-only inspection now diagnoses the live successor rather
than presenting the old operation as safe to restore.

`PreservedInstallResult` is reused: `installed` names the **new candidate** only;
RESTORED has no new installed path, while the old path is derived from its pinned
predecessor. `safeToClose` is true only for error-free COMMITTED/RESTORED after
guard cleanup. It does not initiate shutdown or attest next-launch activation.
Errors may leave an installed file despite an empty result path: inspect the
retained transaction instead of blindly applying again.

## Bounds, source audit and validation

One candidate, 64 MiB per artifact, 1 GiB cumulative inflated work / 512 MiB
capture and five-minute cooperative pre-publication deadline. Existing stricter
archive/inspector limits still apply; no extra queue/thread is introduced. The
existing limits of 32 transactions, 128 files per journal, 64 KiB JSON and bounded
direct-mod/dependency inventory remain. No automatic recovery-evidence cleanup.

Exact local source audit rechecked Java 17.0.12 `Files.createLink/delete` provider
delegation (`src.zip` SHA-256
`24fee80297ef15a886e283a31ac4eca18888ca8e942a21afb3f60a8535dc573b`) and Forge 47.4.23
`ModsFolderLocator.scanCandidates` / `ModSorter.verifyDependencyVersions`
(`fmlloader` sources SHA-256
`7917b9e169ed8d1f34299ebd6c8136f310a176754b52b1e492b86638aa059667`). Direct JAR discovery
and mandatory/present optional dependency checks explain the conservative gap
refusal. No new source-version Minecraft/Forge call is remapped in this slice.

Same-volume hard links are required, without overwrite fallback. File-content
force is not a portable directory fsync or physical power-loss guarantee. Java
does not provide an atomic identity-checked unlink against arbitrary hostile
external changes; cooperative locking/rechecks are not an OS sandbox. Cooperative
deadlines/byte budgets are not a hard heap/RSS bound or OS-call timeout.

[Artifact-bound evidence](evidence/preserved-updates-2026-09-19.json) distinguishes
actual original-to-1.0.1 conversion, installer tests, injected write failures,
real Windows sharing denial, helper-process termination/recovery, package checks
and tests not run. Synthetic installed metadata is not a running Forge instance.
No fixtures/helpers/private mapping data are packaged into the public JAR.

Client/server discovery, GUI, behavior/multiplayer, public-artifact external
cacheless runtime, physical disk-full/power-loss and launcher qualification are
**NOT_RUN** here. No release promotion, acquisition, monetization or license change.
At this backend checkpoint, next development was **LB-01 / PRODUCT-COMPOSED / PRESERVED_UPDATE_COORDINATOR** —
fresh review/consent, typed update selections/results/recovery/receipts, shared
worker and shutdown lease; keep native UI integration and QA explicitly separate.
