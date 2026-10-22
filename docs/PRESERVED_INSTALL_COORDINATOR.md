# Preserved installation coordinator (developer opt-in)

Contract slice: **LB-01 / PRODUCT-COMPOSED / PRESERVED_INSTALL_COORDINATOR**.
This integrates the [existing add-only installer](PRESERVED_ADD_ONLY_INSTALL.md).
Follow-up: [preserved update coordination](PRESERVED_UPDATE_COORDINATOR.md) adds
separate typed selection/confirmation and receipts while reusing this worker,
reservation gate and shutdown lease. The add-only receipt schema is unchanged.
It does not add a transformation, change an adapter, expose public preserved
Convert/Apply, or accept native execution. No Minecraft instance was started.

## Typed integration and explicit authority

`ConversionController.forPreservedDevelopment(gameDir, currentSession)` creates
the explicit developer route. The ordinary constructor used by ConversionScreen
keeps it DISABLED; no saved file, JVM property or mapping selection enables it.
The existing single bounded coordinator performs every readback, install and
receipt write. No second engine, CLI/main/stdout bridge or installer is introduced.

1. Select a `PreservedInstallSelection`: exact admission directory/hash and explicit
   reviewed full MappingData. Recovery additionally selects the install transaction,
   plan hash, prior approval UUID and RESUME or RESTORE. UUIDs are identities, not
   authentication credentials. History cannot choose this selection automatically.
2. `reviewPreservedInstall` schedules fresh admission readback. Recovery also
   inspects the actual install journal through PreservedAddOnlyInstaller. A typed
   REVIEWED result is issued only after its presentation receipt is written.
   An incomplete admission is blocked. Review never writes into mods.
3. The caller displays the selection and obtains consent, then calls
   `confirmPreservedInstall(reviewId)`. Only this controller's current review is
   accepted, once. A new review replaces the old one; an accepted confirmation
   consumes it even on failure. Reopened controllers and history never regain it.
4. Apply creates a fresh approval nonce; recovery uses the explicitly supplied
   prior binding. The existing installer independently repeats all source/result/
   mapping, ABI, dependency, loaded-file, ownership and publication checks. Review
   is neither an installability promise nor a bypass of this final preflight.

The view distinguishes REVIEWING, REVIEWED, BLOCKED, INSTALLING, PENDING_RESTART,
RESTORED and FAILED. It retains the **real PreservedInstallResult**, including
transaction/hash and errors; it does not manufacture a simple TransactionResult
or actionable bootstrap JobRecord. PENDING_RESTART means a committed disk result,
not discovery, activation or correct behavior. Activation is always NOT_OBSERVED.

## Session, thread, cancellation and closing boundaries

The trusted host supplies a **thread-safe, nonblocking current session provider**.
It must contain the actual loaded IDs/files and world/connection state, not a
snapshot reconstructed from metadata/history. It is sampled at acceptance and
again on worker dispatch; confirmation must match the reviewed snapshot. Changes,
an open world or an unavailable provider refuse the operation. No Forge singleton
is resolved on the worker by this API.

Both installer routes share the existing reservation gate, single coordinator and
InstallationShutdownBarrier. Review also holds the lease because the underlying
synchronous readback owns bounded filesystem/lock work. New conversion, inventory,
installation and history work cannot overlap it on this controller. It deliberately
does not offer cancellation after accepting this review/install operation; other
engine/history cancellation remains unchanged. The existing three-minute cooperative
installer/readback budgets still apply, not hard OS-call or heap limits.

`close()` remains nonblocking. It cannot cancel an accepted preserved operation;
the lease and busy/installing state survive through result **and receipt** completion.
Receipt failure remains visible and prevents this operation from granting safe close,
even if the actual journal is COMMITTED. Refreshing history does not clear that
failure. A caller must check idle plus the current preserved view's safeToClose,
not the old simple-install result or a receipt's recorded state.

The host must exclude world/connect/exit actions while isInstalling is true. The
existing native hooks only guard owned-screen navigation; they are not a universal
QuickPlay/direct-world API veto. This turn does **not** wire the developer controller
into those screens or claim native host enforcement. That is the next implementation
slice. Orderly JVM shutdown waits on the shared lease; Runtime.halt, process kill,
power loss and hostile concurrent filesystem changes still require recovery.
There is no launcher restart API, token/argument capture, hot activation or world edit.

## Independent receipt schema and bounded inert history

New receipts are `reports/<operation UUID>/preserved-job-<operation UUID>.json`,
schema **1**, reportType **LEGACYBRIDGE_PRESERVED_JOB**. Existing job schemas 1/2,
CompatibilityReport, TransformationLog and both install-journal families are unchanged.
The old job reader ignores this separate prefix rather than importing its authority.

Each receipt binds operation/review/action, admission UUID/hash, full-data manifest
hash, optional approval and installation UUID/hash, source/candidate hashes when
observed, terminal controller state and a bounded structured error code. It stores
no arbitrary executable path, data path, launcher arguments or authentication data.
Original/result hashes on a failed confirmation describe the reviewed evidence,
not proof that changed files passed again. Full current error/remediation stays in
the typed operation view; authoritative disk details remain in installer journals.

Publication uses a fresh canonical reports directory and the existing no-overwrite
JSON writer, then forces the file before releasing the lease. Failure does not
delete or roll back an installed JAR. A missing admission must still be reportable:
the first focused run found the writer unnecessarily required that path to exist;
its protected path is now the trusted root, with destination safety independently
provided by the new canonical operation directory/no-overwrite publication. No
input or unknown destination is opened for writing. Directory/power-loss atomicity
is not promised, and crash-before-receipt recovery still uses the actual journal.

`refreshPreservedHistory` uses the same coordinator. The reader is strictly
schema-specific, read-only and bounded: 1,024 direct entries, 128 receipts,
64 KiB/file, 2 MiB aggregate and ten-second cooperative deadline. It checks canonical
ancestors, NOFOLLOW opens, before/after metadata, bounded pairwise hard-link aliases,
strict UTF-8, known keys/types/UUIDs/hashes/states and activation NOT_OBSERVED.
First bad/unknown/over-budget input yields PARTIAL with an explicit diagnostic.
Cancellation publishes no new snapshot. No cleanup, migration or journal replay.

History has no "latest successful install" inference; UUID sorting is presentation
order, not chronology. Every historical record has installationAllowed=false and
safeToClose=false. A COMPLETE scan is not proof of current files or completeness
of all historical operations. More than the bounded history needs future pagination/
retention work; no automatic evidence deletion is introduced.

## APIs, evidence and next cut

No source/target Minecraft or Forge symbol changes. The exact baselines and actual
conversion recipes remain those audited in [independent admission](PRESERVED_ARTIFACT_ADMISSION.md)
and [native integration](P4_UI_API_BASELINES.md). Existing ClientMenuHooks and
TransactionScreen were reread to distinguish their real owned-navigation/session
boundary from universal world or shutdown exclusion. New code is JDK17-only and
uses the real [Files API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html)
and [ThreadPoolExecutor API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.html).

The [artifact-bound ledger](evidence/preserved-coordinator-2026-09-13.json) records
focused tests, build/package audits, exact hashes and initial failures separately.
Tests use the actual authored original/converted pair and existing installer in
temporary directories; installed platform metadata is synthetic, **not Forge
running**. Failure/close barriers are explicit test seams. Original classes are
never loaded/executed. Native client/server/UI/behavior/multiplayer remain NOT_RUN.
Full mappings stay explicit/local and unredistributed. License/billing are unchanged.

Next development: **LB-04 / UI-PROMOTION / PRESERVED_DEVELOPER_SCREEN**: a separate
explicit developer screen/host adapter for this API, with safe render-captured
session snapshots, owned navigation/closing guards and explicit selection/consent.
Do not route it through ordinary public Convert/Apply, reuse history as approval,
duplicate the installer, open Minecraft or mark native/package promotion accepted.
The static parts support development, not satisfaction of remaining requires/gates.
