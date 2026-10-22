# LB-05: explicit preserved add-only installation

Slice `INSTALL-PROMOTION / PRESERVED_ADD_ONLY_INSTALL`, 2026-09-13.
This implements actual disk installation and explicit recovery for the exact
`logic-preserving-v1` contract. It is a **developer API**, not public profile
promotion. The native UI's Convert/Apply remain `simple-content-v1`; no conversion
rule, runtime adapter, ABI, mapping data, game class or existing installer changes.
Execution evidence is in [the artifact-bound ledger](evidence/preserved-install-2026-09-13.json).

2026-09-19 follow-up: [managed preserved updates](PRESERVED_MANAGED_UPDATES.md)
provide a separate worker-only developer API for an owned unloaded predecessor.
Add-only recovery now refuses a live update successor, and approval uniqueness
covers both preserved schemas. The old plan bytes/format and add-only semantics
are not migrated. Statements below about "no updates" describe this add-only API,
not the new separately approved update operation. Neither route is public promotion.

## API and authority

`PreservedAddOnlyInstaller` runs synchronously on a worker, outside both the render
thread and an existing engine OperationControl scope. It shares InstallationLock
with admission and the unchanged V1/V2 installers. Contenders are refused, not queued.

```java
var approval = new PreservedInstallApproval(admissionDirectory, admissionPlanSha256,
                                          UUID.randomUUID());
var result = installer.apply(gameDir, approval, reviewedMappingData, actualSession);
var observation = installer.inspect(gameDir, transactionDirectory, installPlanSha256,
                                    approval, reviewedMappingData, actualSession);
var recovered = installer.recover(gameDir, transactionDirectory, installPlanSha256,
                                  approval, reviewedMappingData, actualSession,
                                  RecoveryAction.RESUME); // or explicit RESTORE
```

The trusted caller obtains explicit consent for the selected admission/hash and
supplies the actual immutable loaded-ID/file snapshot with no open world. The UUID
records that approval, **not a signature, authentication token or proof of consent**.
The [developer coordinator](PRESERVED_INSTALL_COORDINATOR.md) now constructs it
only after a fresh review and explicit confirmation; the native public UI is
still not wired to this route. New Apply refuses a nonce already
bound to another preserved install, including one restored. Recovery requires the
same explicitly selected approval and an externally selected install-plan hash;
the journal does not choose a data path or automatically approve itself.

An admission result remains `installationAllowed=false` / `safeToClose=false`.
Its saved observation alone cannot install anything. The separate install caller
must supply authority, current session and reviewed local data. The engine's
existing independent inspector is rerun on every apply/inspect/recover: full bundle
component hashes, real original/private candidate, exact rules, bytecode, linkage,
resources, source dependencies and target metadata/adapters. Shared internal
readback is composed under the caller's lock and bounds; conversion is not repeated
and no new remapping rules or validator bypass were introduced.

The caller must keep installation/world actions mutually exclusive and wait for
safe completion. This API does not create a shutdown hook or run a launcher; no
client close is triggered. Cancellation is not offered after commit starts.
The developer coordinator now supplies bounded scheduling and the shared shutdown
lease. Native developer-screen/host integration remains the next implementation.

## Preflight and publication

Only one candidate is supported. No upgrades, dependent cohorts or compositions.
This profile's exact inspector already limits outgoing dependencies to the pinned
Minecraft/Forge/runtime contract. Its identity is passed to the existing bounded
installed-set inventory as metadata, **not** to the simple bootstrap validator.
That validator still rejects preserved code, as do V1/V2 installation journals.

Before commit and around writing boundaries, the installer checks:

- Canonical real game/workspace/input/staging/transaction/mods paths; no symlink or
  junction alias, hard-linked source/candidate/private-copy substitution, or unknown
  destination identity. Fingerprints include original, staging, admission, payload,
  install plan and reviewed data manifest. Full data readback precedes publication.
- Installed direct-JAR inventory and exact next-launch runtime metadata/ABI version;
  current loaded IDs/files, including hard-link aliases to source/private artifacts.
- Duplicate IDs/versions, required and present optional dependency versions,
  conservative side/ordering grammar and owning-JAR cycles via the unchanged
  InstalledModInventory. Unknown ranges/loaders/JiJ/layouts are refused.
- The complete installed JAR set and fingerprints again before publication and
  receipt completion; late outside additions/changes are visible failures.

The new transaction makes an **independent** complete forced copy of the admitted
payload. A forced immutable install plan is published before a new UUID-derived
name is linked into mods. No original, staging, admission file or existing JAR is
renamed/overwritten. A link and the private payload share identity, not independent
backup storage; do not edit either in place. Source/staging/admission remain intact.

The reviewed target source is `fmlloader-1.20.1-47.4.23-sources.jar`, SHA-256
`7917b9e169ed8d1f34299ebd6c8136f310a176754b52b1e492b86638aa059667`.
This turn reread `ModsFolderLocator.scanCandidates` and
`ModSorter.verifyDependencyVersions`: direct `.jar` discovery, pre-menu resolution,
and present optional dependency checks remain relevant. Installation does not
register a locator or mutate ModList. The origin and mapping recipes remain those
already audited by [independent readback](PRESERVED_ARTIFACT_ADMISSION.md).
The current upstream [ModSorter source](https://github.com/MinecraftForge/MinecraftForge/blob/1.20.x/fmlloader/src/main/java/net/minecraftforge/fml/loading/ModSorter.java)
is orientation only; pinned local bytes, not the moving branch, define this baseline.

## Separate schema and recovery

```text
transactions/<install UUID>/
  preserved-install-payload.jar      independent private copy
  preserved-install-v1.json          immutable exact schema-1 install plan
  preserved-committed-v1.json        installation receipt, pending next launch
  preserved-restore_started-v1.json  restore intent BEFORE unlink
  preserved-restored-v1.json         owned publication removed
  pending-<UUID>.json                private non-authoritative JSON tails
  guard-<UUID>-<index>.ref            interrupted directory guard references
mods/legacybridge-preserved-<modId>-<install UUID>.jar
```

This is schema 1 of `LEGACY_PRESERVED_ADD_ONLY_INSTALL`, not V3/V1 migration.
The plan binds admission UUID/hash (which transitively pins source/result/data),
approval UUID/kind, mod ID/version, candidate hash, fixed profile/recipe/ABI/baselines,
and the derived payload/destination names. No previous artifact/update fields,
launcher arguments, sensitive tokens, saved PASS or arbitrary destination paths.
Checkpoints bind the install plan hash and have exact field sets and schemas.
Unknown/mixed versions, edited fields and matching-byte independent replacements
are refused. Existing installation/history readers may diagnose these as unknown;
they do not silently import preserved approval or manage its update/removal.

| Actual observation | Result/action |
| --- | --- |
| Incomplete private copy/plan | RECOVERY_REQUIRED; retained orphan, no JAR publication or blind rewrite |
| Complete plan, no publication | PREPARED; explicitly resume or restore |
| Owned publication, missing commit receipt | PARTIAL; no automatic close; fresh resume completes receipt |
| Owned publication and matching receipt | COMMITTED; next launch only, no activation evidence |
| Durable restore intent, with/without owned destination | RESTORING; RESUME refused; explicit RESTORE continues |
| Matching restore intent/receipt, destination absent | RESTORED; repeating RESTORE adds no files |
| Committed artifact disappeared without restore intent | RECOVERY_REQUIRED; no blind reconstruction |
| Unknown identity, loaded candidate, changed/missing pins | RECOVERY_REQUIRED; retain evidence, do not close/retry blindly |

Restoration checks the projected remaining dependency set before writing its
durable intent. It deletes **only the still-owned unloaded destination link**,
after identity/hash/loaded-file checks; the private payload is retained. A mandatory
consumer blocks removal. The durable intent prevents a process crash after removal
from turning a subsequent RESUME into an unintended new installation.
Restore is not arbitrary uninstall, world rollback or repair of lost source data.
All original/staging/admission/data pins remain required, even for restoration.
If they are lost, recovery is manual; the API does not silently weaken inspection.

`safeToClose` is true only for error-free COMMITTED or RESTORED observations, after
guard cleanup. Errors carry structured codes/remediation and a known transaction
pin when available. An empty installed field on an error is **not proof that nothing
was published**: inspect the retained operation. Read-only inspection can create the
cooperative lock file but does not rewrite receipts or clean retained guards.

## Executed validation

Final code: 112 focused product cases passed, 41 new, no failures/errors/skips.
Separately, 75 contract cases passed with two Windows symlink-privilege skips.
All-module assemble, public/runtime archive audits, engine boundary and installed
CLI smoke passed serially in 6m40s. These are not supported-mod counts.

The real public JAR `8dbd54f4e6139a223e1e8d6665e2818baf3c0877189b0ec9ea607034857f3a35`
was copied outside the project and used by separately compiled Java17 helpers.
One halted after publication (81); two fresh JVMs resumed idempotently. Another
halted after owned unlink (82); invalid RESUME refused without changing files,
then two fresh RESTORE calls completed idempotently. Both interruptions retained
14 guards, subsequently cleaned by the correct recovery action. Source/result
hashes and configured empty homes/caches remained intact; no original/cache code
sources appeared in class-loading traces. No Minecraft session was started.

The first helper run found a real refinement: invalid RESUME after restore intent
refused reinstallation but cleaned retained guards before returning the refusal.
The no-files-changed oracle failed. The action-direction check now precedes guard
cleanup; a new regression and the repeated public-JAR scenario passed. The earlier
artifact/logs are retained in the ledger, not presented as current qualification.

## Limits and remaining gates

One candidate, 64 MiB per copied/hashed JAR, 512 MiB cumulative inflated / 256 MiB
captured archive work, three-minute cooperative pre-publication deadline; the
independent inspector's stricter class/method/archive bounds still apply. Existing
inventory caps: 1,024 direct mods entries / 256 JARs / 4,096 dependencies, 32 retained
transactions / 128 entries per transaction / 64 KiB JSON. No automatic evidence GC,
unbounded queue, hard heap/RSS guarantee, OS-call timeout or filesystem sandbox.

Seven fixed guard indexes match V2's game/root/input/staging/transactions/mods/tx
layout, but use a separate transaction. The admission transaction is read/pinned,
never modified. Windows paths inherit the reviewed early 247-character derived
guard boundary. Same-volume hard links are mandatory; no copy/overwrite fallback.
File-content force does not guarantee parent-directory fsync or physical power-loss
durability. These limitations follow the primary [Java 17 Files contract](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html).
Cooperating locks and repeated checks do not stop a hostile external process racing
Java's checks/calls. There is no multi-file atomic transaction claim.

Current-session IDs are supplied by a trusted caller, not cryptographically attested
against Forge internals. Runtime metadata/ABI and recognized JiJ are checked; this
is not a cryptographic attestation of every loaded implementation byte. Arbitrary
custom locators and modpack runtime behavior remain outside the static inventory.

The helper/test fixtures run Java filesystem operations only. They do not establish
Minecraft discovery, gameplay, GUI, dedicated-server or multiplayer behavior on the
new public hash, physical disk-full/power-loss survival, or a certified launcher.
Automatic restart remains unavailable; no close call is labelled completed restart.
No additional profile, adapter, private data, fixture or harness is packaged.

Next development: **LB-01 / PRODUCT-COMPOSED / PRESERVED_INSTALL_COORDINATOR**:
integrate bounded preserved-install review/jobs with the existing coordinator and
versioned history, behind an explicit developer gate, without public profile
promotion or repeating readback/binding. Native UI/Forge activation, profile
promotion, composition/cohort admission and managed preserved updates remain separate
gates. LB-05 / INSTALL-PROMOTION remains PARTIAL, not a completed universal installer.
