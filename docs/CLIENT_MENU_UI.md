# P4 native client interaction contract

The physical-client classes are under `dev.legacybridge.product.client`. They
use the pinned Minecraft 1.20.1 / Forge 47.4.23 APIs: `ScreenEvent.Init.Post`,
`ObjectSelectionList`, `Checkbox`, `EditBox`, `ConfirmScreen` and `GuiGraphics`.
No source-mod code, report JSON or filesystem metadata is loaded by rendering,
filtering, keyboard input or screen initialization. The common controller owns
inventory, engine jobs, path validation, report writing and installation.

Follow-up 2026-09-10: [read-only persisted History](UI_HISTORY.md) adds a separate
screen, not another queue tab. It never rehydrates Apply authority from receipts.
This implementation has focused headless checks; native input/layout qualification
of its changed artifact is still NOT_RUN. Existing P4 evidence remains historical.

## Inventory and selection

The input tab uses a native scrollable list with four lines per entry:

1. Filename and available mod display names.
2. Available mod IDs/versions and evidence-based inventory classification.
3. Observed source profile and byte length.
4. SHA-256 from the bounded inventory snapshot, or explicitly unavailable.

Long text is clipped to the row width. Hover narration/tooltip and the technical
details screen expose the full bounded fields, including the complete digest.
The source candidate label does not assert that conversion is supported.
Invalid, already-converted, duplicate and unconfirmed-source entries remain
visible but cannot be checked for conversion. Valid entries can still be
analyzed independently; the engine makes the final conversion decision.

Search is an in-memory filter over the current inventory's filename, mod ID and
display name, bounded to 128 characters. It does not rescan disk. A row hidden
by search is not silently used by the single-selection action fallback.
Explicitly checked rows remain checked across filters: their keys combine the
observed path and SHA-256, and a refreshed/changed fingerprint removes the check.

- Click the native checkbox, or focus a row and press Space/Enter, to toggle it.
- With the list focused, Ctrl+A checks up to 32 filtered eligible candidates.
- Tab and arrow keys use native focus/navigation and narration.
- Convert queues checked rows, or the visible selected candidate if no row is
  checked. It never installs results as a side effect.
- An explicit absolute mapping-bundle directory is bounded to 4096 characters.
  Missing data produces the controller/engine diagnostic; no UI download or
  developer-cache discovery is introduced.

## Per-job outcomes and application

The jobs tab presents independent job summaries. Selecting one does not change
another job's outcome. Job state, progress, diagnostics, artifact and report
paths come from immutable typed `JobView` values, not parsed stdout or JSON.
The 32-job limit includes retained results; Clear done only removes bounded UI
summaries and never deletes the files they describe.

The technical details screen provides separately enabled buttons for the job's
CompatibilityReport, TransformationLog, conversion report and result folder.
It never opens a JAR using the operating system's executable-file association.
All opening actions are explicit clicks, route through controller path
validation on its bounded auxiliary worker, and call the target's real
`Util.OS.openFile` there. That API logs errors rather than returning launch
success, so the UI says only that the request was sent, with a copy-details
fallback; it does not claim an editor/file manager opened successfully.

Apply is disabled until a converted artifact and all three report paths are
present and the controller is idle. A real `ConfirmScreen` explains add-only
installation and the next-launch requirement. Only its affirmative action
submits the selected job ID plus the platform's currently loaded mod IDs to the
installer controller. The controller still validates hashes, reports, identity,
ownership and collisions; button state is not installation authority.

Applied results display `PENDING_RESTART`, never activated or game-tested.
There is no automatic restart button or launch-command reconstruction. The
player must close Minecraft and reopen through their launcher.

## Lifecycle, layout and limits

The layout targets at least 320x240 GUI pixels. At that minimum, one expanded
inventory row fits completely; the native list scrolls to the remaining rows.
Larger windows expose more rows. At narrow widths, long names/digests and some
status prose rely on tooltips/details/narration rather than overflow into other
widgets. The details list retains at most 256 wrapped lines; individual inert
presentation fields remain bounded by the existing 512-codepoint sanitizer.

Resize preserves search/mapping text and caret, marked rows, active tab,
per-tab list scroll and the focused control. Filter rebuilds clear stale entry
focus; details rewrap preserves the selected line index and action focus.
The details screen supports arrows, Page Up/Down, Home/End and wheel scrolling.
Technical engine diagnostics and some field labels remain English; interface
actions and lifecycle explanations are provided in English and Portuguese.

Escape/back from details or confirmation returns to the conversion screen
without closing the shared controller. Escape/back from the main conversion
screen closes it, requesting queued/active cancellation without blocking the
render thread. The service publication barrier remains authoritative: closing
after a completed commit is not reported as cancellation of that artifact.
Unexpected navigation away from an owned child screen also closes the session.
The existing title-screen lease prevents repeatedly reopening cancelled workers
before the previous controller actually stops.

This document describes implemented interaction, not runtime evidence. Build,
native focus/resize checks, negative fixtures, per-job conversion and actual
next-launch activation must each be reported separately by the production QA.
It does not establish universal accessibility, every GUI scale/input method,
all platform file associations, crash recovery or compatibility beyond the
unchanged `simple-content-v1` profile.
