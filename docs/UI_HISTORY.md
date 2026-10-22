# Read-only history in the public product

LB-04 / `UI-HISTORY-READ`, implementation review 2026-09-10. This is a native
LegacyBridge screen, not support for converting another mod's GUI. The public
conversion subset, six regression profiles, mappings, adapters and runtime ABI
are unchanged. Acceptance remains PARTIAL until separate native UI checks.

## Delivered interaction

Open **Convert mods → History**. First opening requests a bounded asynchronous
receipt scan through `ConversionController.refreshHistory()`. If the coordinator
is busy, the screen remains usable and asks for an explicit Refresh later.
Subsequent visits retain the snapshot; Refresh is explicit, not a disk poll.
Search covers recorded input paths, job UUIDs, original/result hashes, ordering,
events and recorded states. Receipts do not store verified mod metadata, so the
screen does not invent mod IDs or names from filenames.

Rows display the input, UUID, number of receipts and ordering. Only the reader's
`highestRecorded` can supply the highest observed state. Ambiguous, legacy or
incomplete histories show no trusted latest result. Even PENDING_RESTART has
activation NOT_OBSERVED, and COMPLETE refers only to completion of the bounded
scan. Conversion, installation, current discovery and gameplay remain separate.

Select a row and choose Details (or Enter/Space on its focused row). Receipts are
paged individually, including recorded errors, original/result hashes, schema,
sequence and installation outcomes. Diagnostics separately shows reader issue
codes, scan counters and omitted-diagnostic count. Details capture an immutable
snapshot; going back and refreshing is required to read new receipts. Copy
details copies bounded literal text from that page, not executable/clickable
components. Historical paths cannot be opened or used for Apply, restore or
shutdown. There is no 'open result' action based on an old unverified path.

The previous snapshot stays visible while reading. Cancellation reports whether
the operation accepted the request; accepted cancellation retains that snapshot.
Reader failures/partial scans have explicit status/diagnostics, not an inferred
success from old results. The history Cancel button acts only on its own active
history read, not an already-running conversion. Back to conversion keeps the
session and its bounded work alive; leaving the owned screen family closes the
controller through its existing safe cancellation/cleanup path.

## Implementation and resource bounds

- `HistoryPresentation` is a pure-Java projection of typed, already bounded
  reader results. It never opens a path, loads a class, hashes a file or grants
  installation authority. Controls, formatting and unpaired surrogates are
  removed from display values; the 1,024-code-point cap appends an ellipsis.
- `HistoryScreen` and `HistoryDetailsScreen` live only in the physical-client
  package. Existing Forge title hooks and owned-screen navigation are reused;
  the common bootstrap does not acquire a new client reference.
- No worker or queue was added. The reader's 128-receipt, 64 KiB/file, 2 MiB
  aggregate and 10-second cooperative bounds still apply. Search is over the
  immutable in-memory snapshot only. No filesystem I/O runs on rendering/tick.
- Details display one receipt at a time, not an unbounded receipt feed. Wrapped
  render rows cap at 2,048 plus an explicit limit notice; copying that bounded
  page still includes its remaining fields. Diagnostics are bounded by the
  reader's 64-issue limit. Disk history is not pruned or repaired.
- English and Brazilian Portuguese labels/warnings are packaged together.
  Native EditBox/list/button focus, arrows/Tab/Enter/Space, Escape, page scrolling
  and resize reconstruction are implemented. Search/cursor, job selection and
  scroll are retained across reconstruction. Native input behavior is NOT_RUN.

## Exact API evidence

Re-read target `Screen` and `ObjectSelectionList` from the mapped official
1.20.1 / Forge 47.4.23 source archive on 2026-09-10. Its SHA-256 is unchanged:
`4bd6656a7cbe3809da68f3b709ca0f2fa186575f58538668c58ce30cb38c4932`.
The native `init`, `resize`, `removed`, `setInitialFocus`, `getNarration` and
selection behavior are the existing APIs documented in
[P4_UI_API_BASELINES](P4_UI_API_BASELINES.md) and
[PRODUCT_API_BASELINES](PRODUCT_API_BASELINES.md). Existing 1.16.5 input contracts
are not changed, so no new cross-version semantic equivalence is claimed.

## Evidence and remaining checks

Current commands/hashes and actual outcomes are recorded in
[the UI-history ledger](evidence/ui-history-2026-09-10.json).
Focused checks cover projection/search/pagination/diagnostics/sanitization,
bytecode-inspected client boundaries and actual controller receipt
read/cancel/apply/restore isolation. These are not Minecraft execution.

Native acceptance, deferred at the user's request: inspect en_us and pt_br at
minimum logical GUI dimensions and multiple GUI scales; search/select with
keyboard; resize while reading/on receipt details; open empty/corrupt/ambiguous
history; cancel/back/reopen during a read; inspect diagnostics and copied text;
verify no Apply-from-history and dedicated-server startup of this exact JAR.
No game process or screen automation was used for this delivery. No screenshots
or native PASS are claimed. Journal/discovery reconciliation, retention/import,
verified historical path opening and reapproval after restart remain separate
unimplemented features. Extensive full-corpus testing was not repeated.

## Artifact identity and next development

Continuation 2026-09-12: [UI-SAVED-REPORTS](SAVED_REPORTS.md) adds a separate
index for new jobs and a paged, inert report viewer reached from History. Old
receipts and their no-Apply boundary remain intact. Current hashes/cursor are in
the new ledger and contract; the following paragraph records the older delivery.

The previous public JAR was hash-verified and archived locally in
`build/ui-history-20260910-baseline/LegacyBridge-4e74a861.jar` before packaging.
Old ledgers remain unchanged and bound to their original artifacts. In particular,
the old COMPOSE observation index points at a mutable build path: after this UI
build it must not be used as proof for the new bytes. Its native runs were already
null. Future composition QA must explicitly prepare/review bindings again;
old receipts are never silently rewritten to accept the new public hash.

Implementation of this bounded history UI can hand off while native acceptance
is pending. Next product work is LB-01 / PRODUCT-COMPOSED: reuse the existing
static composition plan for a typed preview/admission design, keeping composed
conversion and Apply blocked until their required equivalences and gates pass.
Do not return to extending the COMPOSE QA harness merely to occupy a no-game turn.
