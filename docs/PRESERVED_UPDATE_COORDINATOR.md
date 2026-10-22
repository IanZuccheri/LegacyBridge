# Preserved update coordinator: explicit developer flow

Follow-up: [PRESERVED_UPDATE_SCREEN](PRESERVED_UPDATE_SCREEN.md) now consumes this
API in the existing developer host. The coordinator-only boundary below describes
its own delivery; neither checkpoint promotes public Apply or proves native QA.

Slice **LB-01 / PRODUCT-COMPOSED / PRESERVED_UPDATE_COORDINATOR**, 2026-09-19.
This integrates the existing [managed-update backend](PRESERVED_MANAGED_UPDATES.md)
into the bounded product controller. It does not wire a new native form or enable
updates through public Convert/Apply. `simple-content-v1`, all six conversion
profiles and the runtime/adapter ABI remain unchanged.

## Selection, preview and one-use confirmation

Use the existing `ConversionController.forPreservedDevelopment(gameDir, session)`
factory. The normal public constructor refuses update review, confirmation and
update-history reading. No saved receipt, JVM property or data selection enables
this route. The trusted host must provide actual immutable loaded-ID/file/world
snapshots and exclude navigation/world/exit during accepted filesystem work.

`PreservedUpdateSelection` explicitly names:

- the incoming `PreservedInstallSelection`: admission directory/hash and reviewed
  full mapping-data manifest pin;
- the owned predecessor's add-only transaction directory and exact plan SHA-256;
- optionally, recovery of a selected **update** transaction: its plan pin, prior
  approval UUID and RESUME or RESTORE. Recovery never targets the predecessor.

`reviewPreservedUpdate(selection)` reserves the shared worker and shutdown lease.
It independently rechecks the incoming admission, then calls the backend's new
read-only `preview` for an initial update. That preview reuses the exact old/new
admission, identity, version, loaded-file and projected dependency checks; it
does not reserve an update directory, publish a plan, copy a backup or change
`mods`. The existing cooperative installation lock may be opened; read-only here
does not promise zero OS metadata/lock activity.

`PreservedUpdatePreview` carries the observed mod ID, old/new versions and old/new
artifact hashes, or a structured refusal. It is not a signed proof or consent.
For recovery, the existing update inspector independently re-reads the selected
journal and actual bytes instead of trying to preview a fresh replacement of an
already retired predecessor. Restoration intent prevents RESUME.

Only after the review receipt is saved does the current controller retain a
confirmable review. Obtain separate explicit player approval, then call
`confirmPreservedUpdate(reviewId)`. It accepts only the current controller's
current update review, once, with the same actual session snapshot. Add-only
confirmation cannot consume an update review; update confirmation cannot consume
an add-only review. Superseding reviews and preparation invalidate prior consent.
Changing files after review is not bypassed: the real installer repeats preflight.

The UUID returned by review/confirm identifies the scheduled **operation**;
`preservedUpdate().observation().reviewId()` is the confirmation identity. The
atomic `PreservedUpdateView` includes explicit selection, optional initial
preview and the shared `PreservedInstallView` observation. Recovery has no fresh
replacement preview. Hosts must display/confirm the selected operation, not infer
one from historical order. No simple bootstrap `JobRecord` is manufactured.

## Worker, safe completion and closing

There is no new executor or unbounded queue. Add-only/update/public installation
share the existing reservation gate, bounded coordinator and shutdown lease.
No installer, mapping reader or receipt writer runs on the render thread. The
host provider is sampled on acceptance and again at dispatch; confirmation also
checks equality with the reviewed session. The host still owns native world/exit
exclusion; an immutable snapshot alone does not veto external game actions.

Once accepted, review/installation is deliberately non-cancellable. `close()` is
nonblocking and does not interrupt commit or release its lease early. Busy and
installing remain true through both the real result and receipt publication.
History reading is cooperatively cancellable; cancelled reads publish no new
snapshot. All existing byte/deadline limits remain cooperative, not OS time/RSS
isolation. No automatic evidence cleanup or background recovery is added.

PENDING_RESTART means a committed disk update, not discovered/activated/working
content. RESTORED means the old owned publication was restored; the result's
`installed` field continues to describe the new candidate only. Receipt failure
retains the real installer result, reports a structured failure and blocks this
operation's `safeToClose` even if the new file is already COMMITTED. Reading
history cannot clear that failure or roll back a publication.

No launcher restart provider, token/argument capture, startup service, hot
activation or world migration is introduced. The host must still use the manual
launcher fallback only after the exact operation is idle and safely complete.

## Separate receipts and inert history

Update receipts are schema **1**, reportType
`LEGACYBRIDGE_PRESERVED_UPDATE_JOB`, stored under:

```text
reports/<operation UUID>/preserved-update-job-<operation UUID>.json
```

The exact envelope binds `previousInstallationId`, `previousPlanSha256` and an
embedded existing schema-1 `LEGACYBRIDGE_PRESERVED_JOB` observation (action,
operation/review/admission/data pins, optional approval/result/input hashes,
terminal state/error, activation NOT_OBSERVED). The envelope is not an add-only
receipt; each reader scans only its distinct prefix. Existing job schemas,
journals, CompatibilityReport and TransformationLog are not migrated.

The filesystem publication/read transport is shared with preserved add-only
receipts, not duplicated. Schema validators remain distinct. It preserves fresh
no-overwrite directories, forced files, canonical/NOFOLLOW checks, alias checks,
strict UTF-8 and limits: 1,024 direct entries, 128 receipts, 64 KiB/file, 2 MiB
aggregate and ten-second cooperative deadline. Unknown or malformed selected
receipts yield visible PARTIAL history, never silent success. Bounded schema
diagnostics do not claim to authenticate editable JSON.

`refreshPreservedUpdateHistory` schedules read-only work on the same coordinator.
Every returned record, including its embedded observation, has
`installationAllowed=false`, `safeToClose=false`, `activation=NOT_OBSERVED`.
UUID sorting is presentation only. History cannot reconstruct a selection,
restore a consumed review or authorize recovery; explicit pins and new consent
are required even in a newly opened controller. No arbitrary source/data path,
launcher argument or credential is persisted.

## Scope, validation and next work

No source or target Minecraft/Forge symbol is remapped in this slice. The actual
authored 1.16.5 fixture and production converter reproduce the previously pinned
1.0.1 candidate; original code is never loaded. JDK17-only coordination reuses the
existing source-audited ThreadPoolExecutor, Files and shutdown-lease contracts.
Exact loader/discovery limits remain those in [API baselines](PRODUCT_API_BASELINES.md)
and the backend's source audit, not a newly invented loader hook.
This turn also re-read Java 17.0.12 `ThreadPoolExecutor.execute` and `AbortPolicy`
from local `lib/src.zip` (SHA-256
`24fee80297ef15a886e283a31ac4eca18888ca8e942a21afb3f60a8535dc573b`): the existing
controller uses one worker, bounded queue and rejection, never CallerRunsPolicy
or a render-thread execution fallback. No new Minecraft/Forge entry point is added.

[Artifact-bound evidence](evidence/preserved-update-coordinator-2026-09-19.json)
records 94 focused regression cases, including 37 new cases, all passing without
failures/errors/skips, plus serial compilation/assembly, public/runtime archive
audits and the installed CLI smoke. Temporary
installed platform metadata is synthetic, not Forge running. Actual client,
server, UI, activation, gameplay and multiplayer validation remain **NOT_RUN**.
The artifact remains experimental/Beta. Local full data remains explicit and
not approved for redistribution; no new acquisition, billing or license change.

Still unsupported: loaded/unknown predecessors, update-of-update chains,
simple-content predecessors on this route, mandatory inbound consumers,
composed/cohort updates and automatic pre-discovery repair. Full old/new pinned
inputs remain necessary for recovery.

Next: **LB-04 / UI-PROMOTION / PRESERVED_UPDATE_SCREEN**. Connect explicit
predecessor/incoming/recovery selection, observed version/hash comparison and
separate confirmation to this completed coordinator using the existing
developer host/session/closing guard. Preserve the public route. Native visual
and runtime acceptance are separate pending gates; do not reopen Minecraft
without new authorization or repeat the installer/coordinator instead of that UI.
