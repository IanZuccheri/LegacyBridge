# Bounded, read-only job history

Contract slice: **LB-05 / JOB-HISTORY-READ**. This is a headless product API,
not a new conversion profile, native history screen or installation recovery
implementation. The user's no-game/no-screen restriction remains in force.

## Implemented boundary

`JobHistoryReader.read(trustedGameDir, cancelled)` reads only direct
`legacybridge/reports/<canonical UUID>/job-<canonical UUID>.json` receipts.
It uses the existing bounded JSON parser without loading classes from any JAR.
Other report types and writer temporaries are not parsed as jobs; all enumerated
entries still consume the scan budget. Invalid job-like names and operation
directories produce diagnostics. It never recursively walks arbitrary folders.

`ConversionController.refreshHistory()` schedules this on the existing single
bounded coordinator and refuses another action while busy. `history()` returns
an immutable snapshot with no filesystem I/O. The constructor does no history
I/O. There is no additional executor, CLI invocation, stdout protocol or render
thread wait. Cancellation/close and publication are mutually exclusive under
the controller gate: accepted cancellation keeps the previous snapshot and
exposes CANCELLED through the operation view. A direct cancelled reader returns
no jobs. Closing remains nonblocking.

The reader does not create the workspace, write/migrate/repair receipts, clean
temporary files, open original/result JARs, read transaction journals, consult
Forge discovery or populate actionable `jobRecords`. Stored UUIDs cannot be
selected/approved for Apply in a fresh controller. Explicit transaction recovery
is still a different operation with its existing independent checks.

## Typed observations and ordering

`JobHistory` distinguishes NOT_READ, COMPLETE, PARTIAL, CANCELLED and FAILED.
Its jobs contain receipts, ordering and an optional `highestRecorded` observation.
Reader `Issue` codes are separate from untrusted `RecordedError` text originating
in an editable receipt. Display text is bounded and strips controls, format
controls and Minecraft section-sign formatting markers. Unpaired Unicode
surrogates are rejected, including JSON escape sequences.

| Receipt input | Read-only interpretation |
| --- | --- |
| Schema 1 | LEGACY_UNORDERED; no sequence/event/activation invented, no rewrite. Multiple receipts retained, no latest selection. |
| Schema 2, unique contiguous sequence | Order by recorded sequence, never file mtime or random filename UUID. Highest is only the highest receipt observed in this scan. |
| Duplicate sequence, gap, mixed schemas or conflicting job input/result identity | AMBIGUOUS and a diagnostic; never last-wins/deduplicate into a successful result. |
| Malformed, unknown schema/field, unsafe, unreadable or limit reached | Explicit diagnostic and incomplete status. No job in that snapshot receives `highestRecorded`, since an unreadable receipt could hide a later outcome. |

Strict schemas require known keys and types: no string-to-number/boolean coercion,
fractional sequence, unknown state/event/action or fabricated activation flag.
Schema 2 installation observations retain the per-attempt operation/action,
transaction/entry state and recorded safe-to-close boolean. Nullable refusal
fields remain nullable; a refusal may retain an earlier top-level linked
transaction. No semantic equivalence is inferred from the batch status.

All observations expose activation **NOT_OBSERVED**, even when the recorded state
is PENDING_RESTART. A receipt is not signed, authoritative or proof of current
files. COMPLETE means this bounded scan finished without a reader diagnostic,
not that every historical receipt exists or an installation/gameplay check passed.

## Filesystem and resource limits

Default upper bounds, which callers may only tighten:

- 1,024 processed directory entries and 128 examined receipt candidates.
- 64 KiB per receipt and 2 MiB aggregate payload I/O.
- 64 retained diagnostics, plus an explicit omitted-diagnostic count.
- 10-second cooperative deadline and cancellation checks during enumeration,
  chunked reading, alias comparison and path inspection. One bounded JSON document
  is parsed at a time with the existing parser's depth/node/string limits.

There are at most 128 retained receipts/jobs. Limits do not mean the newest 128
are selected: directory enumeration order is unspecified. A partial result never
pretends to be a complete recent history. The reader does not delete old reports
to make them fit; pagination/retention is future work.

The explicitly trusted game directory is resolved once by `BridgeWorkspace`.
Reserved paths and existing ancestors of each opened file must remain real at
their expected locations. Receipt opens use NOFOLLOW_LINKS; before/after checks
compare available identity, size and modification time. Recorded paths are
accepted only within their exact staging/mods/transaction layout. Traversal,
cross-instance/relative paths, observed symlinks/junctions and unsafe Windows
components (ADS, device names, trailing dots/spaces) are rejected. Returned paths
are inert installation-relative strings, not permission to open/install them.
Missing staged/installed files are legitimate historical references: their bytes,
hashes, existence and current ownership are **not** certified by this reader.

The first regression run found that Windows returned no usable file key for a
real hard-link pair. Detection now uses bounded `Files.isSameFile` comparisons
when keys are absent, not just an assumed key. This detects aliases **within the
examined receipt set**; it cannot find every hard link outside that set.

These are read-only snapshot checks, not an OS sandbox or atomic multi-file
snapshot against a malicious concurrent filesystem writer. No hard wall-clock
guarantee exists for blocked OS calls, heap isolation or physical-power-loss
durability. Concurrent appends may require another explicit refresh.

## APIs and evidence

No Minecraft/Forge invocation, mapping, adapter, profile admission or report
writer schema changes in this slice. Existing [source/target API audits](COMPAT_API_BASELINES.md)
remain the baseline for unchanged conversion/runtime behavior. New filesystem
and decoding code uses the real Java 17 [Files API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html)
and [CodingErrorAction API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/charset/CodingErrorAction.html).

The [slice ledger](evidence/job-history-read-2026-09-07.json) separates targeted
tests, root build/package checks, exact public hash and native NOT_RUN dimensions.
Positive integration tests use the actual engine on authored inert Java-8 ASM
fixtures plus actual temporary-directory Apply/Restore. The platform declaration
is synthetic, not Forge execution. Pure reader tests use synthetic JSON, real
NTFS hard links/junctions and Windows deny-read handles; timeout/cancellation
barriers are explicit test seams. No original class is executed.

The old public artifact is retained at
`build/job-history-read-2026-09-07-run1/baseline-LegacyBridge.jar` with SHA-256
`6e168fd729b7d3e45b45d95ca4e161d7d7db83c4ee054516d2ab8c17b030efd1`.
Its earlier headless receipt evidence and the separate `3749da...` native evidence
do not qualify a changed JAR.

Follow-up 2026-09-10: [the native history widget](UI_HISTORY.md) is implemented,
with focused static checks; keyboard/scale/resize validation remains NOT_RUN.
Still pending:
journal/discovery reconciliation after restart, archive pagination/retention,
cross-instance import/migration, independent admission of preserved/composed
profiles, and all existing public-client/networking gates. This API does not
provide automatic restart, activation, broader compatibility or a recovery oracle.
