# Job-to-installation receipts (headless implementation slice)

Contract cut: **LB-05 / JOB-INSTALL-RECEIPTS**. On 2026-09-07 the user explicitly
prioritized work that does not open Minecraft. This correction does not accept
LB-06, enable another profile or change the platform/runtime adapter ABI.

## Observed defect and implemented boundary

`ConversionController.start` flushed only `activeRecord`. Apply/recovery ran
after conversion had cleared that field, so a successful transaction could leave
only a `CONVERTED` job receipt with null installation/transaction fields on disk.
The in-memory job and the actual installation journal were separate evidence.

The coordinator now retains the bounded list of affected job records for an
installation. Before exposing idle state and releasing its shutdown lease, it
appends one outcome receipt per affected job, including refused and unexpected
outcomes. Closing a screen does not discard that list or cancel the accepted
installation. No additional worker, render-thread I/O or polling loop is added.

Apply binds each selected job to the returned entry index, exact mod ID/version
and converted hash. Recovery requires the stored transaction and entry index,
then rechecks that identity/hash. Another conversion of the same mod ID does
not inherit the receipt. Unknown/mismatched outcomes do not grant installation
authority, a claimed installed path or permission to close automatically.

Each file remains independently observed: a `PARTIAL` transaction can contain
an `INSTALLED` entry and a `PREPARED` entry. It must not be flattened into a
successful batch. Restore clears the new installed path, not the staged result.
Neither `COMMITTED` nor `PENDING_RESTART` proves activation or gameplay.

## Receipt schema 2 (not the transaction journal schema)

Files remain append-only `reports/<operation>/job-<uuid>.json`. Existing schema-1
receipts are neither rewritten nor upgraded. ConversionReport, CompatibilityReport,
TransformationLog and transaction-journal schemas are unchanged.

Schema 2 retains the original job ID/state, original/result hashes, paths and
structured error, adding:

- `sequence`: monotonic per in-memory job; failed write attempts can leave gaps.
- `event`: `CONVERSION` or `INSTALLATION`.
- `installation`: null for conversion, otherwise the installation operation UUID,
  `APPLY`/`RESUME`/`RESTORE`, observed transaction path/status, entry index/state,
  and the transaction's own `transactionSafeToClose` result. Unknown fields are
  null, never invented as committed. This boolean is not an overall UI-close grant.
- `activation`: always `NOT_OBSERVED` in this static controller receipt.

The top-level `transaction`/`installed` describe the last linked per-file
observation; `installation` describes this attempt. A recovery refusal can retain
the last known installed path while this attempt has no successful outcome.

Receipt write failure remains a visible `JOB_REPORT_WRITE_FAILED` installation
and global diagnostic, even when the journal is committed. Other jobs' receipts
are still attempted. The committed JAR, original, staging and earlier receipts
are not rolled back, deleted or overwritten to hide that failure. The existing
TransactionScreen close guard already requires an empty installation error.
Refreshing transaction inventory does not clear that failed-operation diagnostic
or turn journal inspection into successful receipt persistence.

## Authority, API and filesystem limits

These JSON files are inert diagnostics, not signatures or install authorization.
There is no history reader/reconciliation in this cut. A fresh controller still
has an empty job list; explicit transaction inventory/recovery remains available.
No editable receipt can silently rehydrate an approved install selection.

Subsequent slice: [JOB-HISTORY-READ](JOB_HISTORY_READ.md) now implements bounded
read-only observation. It does not change this writer's scope or turn historical
jobs into approvals; native history UI/reconciliation remain separate.

The implementation reuses the inspected `CanonicalReportWriter`, real-directory
checks, existing controller executor and installation shutdown barrier. It changes
no source/target Minecraft or Forge call or mapping recipe. The existing
[source/target API audit](COMPAT_API_BASELINES.md) and
[native integration baseline](P4_UI_API_BASELINES.md) remain applicable to those
unchanged calls. Filesystem operations and executor semantics use the actual
[Java 17 Files API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html)
and [ThreadPoolExecutor API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.html).

No additional OS sandbox or universal physical-power-loss durability is claimed.
A crash between the authoritative installer commit and this presentation receipt
can still leave a missing receipt: future bounded reconciliation must consult the
validated journal, not manufacture activation or blindly trust editable JSON.
The user-requested no-game/no-screen restriction remains in force.

## Acceptance and separate evidence

Tests must use actual static conversion and actual temporary-directory commits
for Apply/partial/resume/restore; installer seams may inject a precise synthetic
I/O failure or malformed return and must be labeled. They must cover immutable
prior receipts, every approved batch entry, unselected/reconverted jobs, refusal,
unknown outcome, write failure, world-open refusal and closing during Apply.
No original or converted class is loaded/executed by these tests. The platform
mod declaration is inert, not the public JAR running in Forge.

Build/tests, current/baseline public hashes and remaining gates are recorded in
[the slice ledger](evidence/job-install-receipts-2026-09-07.json). A changed public
JAR requires separate native requalification; old native evidence is historical.

Historical integrity can be checked without claiming the new JAR passed:

```powershell
.\gradlew.bat '-Djavax.net.ssl.trustStore=NONE' '-Djavax.net.ssl.trustStoreType=Windows-ROOT' :legacy-corpus:verifyPublicEvidence '--args=.. build/job-install-receipts-2026-09-07-run1/baseline-index.json' --no-daemon --max-workers=1
```

Run from the repository root. The JavaExec task's working directory is the
`legacy-corpus` module, so its explicit `..` argument names the repository root.
Only E190's file location changes in the archived index. The original index,
expected old public hash and all 61 assertions remain unchanged. This command
does not certify the receipt correction or change any contract status.

Next headless work: bounded read-only receipt history with explicit schema-1/2
handling, malformed/duplicate/oversized/unsafe-path refusals and journal
reconciliation separated from installation authority. Native recovery, UI,
networking and broader conversion remain pending under their existing IDs.
