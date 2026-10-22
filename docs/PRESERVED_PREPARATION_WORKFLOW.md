# Preserved preparation workflow

Implemented slice: **LB-01 / PRODUCT-COMPOSED / PRESERVED_PREPARATION_WORKFLOW**.
Review: 2026-09-19. This is developer-only integration, not public profile promotion.
The existing public `simple-content-v1` Convert/Apply route is unchanged.

## What is implemented

The opt-in [developer screen](PRESERVED_DEVELOPER_SCREEN.md) now has a preparation
screen. It accepts an original in `legacybridge/input`, its explicit SHA-256,
and a full local mapping bundle plus the reviewed manifest SHA-256. Choose:

- Convert the original with the **existing** `logic-preserving-v1` engine.
- Inspect an already converted candidate in `legacybridge/staging`, with its
  explicit SHA-256. This does not trust the candidate's old reports.

The shared worker analyzes the original, optionally converts it, independently
inspects the candidate, and prepares a private admission record. Original mod
classes are never executed. Mapping rules, profile eligibility, API inventories,
ASM/linkage validation and actual transformation operations are reused, not
implemented in widgets or copied into another converter. Unsupported input
remains refused; none of the six existing profiles is expanded or composed.

`READY` means the admission and operation reports were completed. It does not
mean installed, activated, behaviorally equivalent in a game, or approved by the
player. `installationAllowed=false`, `safeToClose=false` and
`activation=NOT_OBSERVED` remain explicit. Use the separate **Use admission**
action to populate the previous developer review screen. That action performs
no review or installation. A fresh review, current session checks and separate
single-use confirmation are still required before Apply.

## Typed boundary and lifecycle

`PreservedPreparationRequest`, `PreservedPreparationView` and
`PreservedPreparationForm` live on the common/JDK-only side. Widgets only edit
bounded selections and submit operations to `ConversionController`. No CLI,
stdout parsing, `System.exit`, filesystem access, future waits or Minecraft
queries on worker threads are introduced.

The default controller cannot submit preparation. The existing explicitly
enabled developer factory is required. The client host publishes immutable
loader/world snapshots on its thread, including while the preparation child
screen is open. An open world or missing host refuses submission; a changed
session before admission blocks completion. Original/candidate paths must be
canonical regular descendants of their workspace areas and cannot alias an
actually loaded mod. No original, loaded mod or unrelated output is overwritten.

Only one preparation is active, using the existing coordinator and engine worker;
there is no new executor or unbounded queue. The admission handle is single-use,
with the existing 64 MiB input, 128 MiB expanded-work and 64 MiB capture budgets
and two-minute cooperative deadline. Existing journal limits, directory guards,
disk-space checks and installation lock still apply. These limits do **not**
claim process isolation, a hard heap/RSS cap, or a sandbox against hostile Java.

Progress comes from the active engine operation or admission control, with
explicit analyzing/converting/inspecting/admitting/report-writing states. No
fabricated percentage is assigned where total work is unknown.

## Cancellation and recovery

Accepted cancellation cannot yield a `READY` selection. Engine cancellation
retains its existing complete-output publication semantics. A candidate that
was completely converted before a later cancellation can remain in staging;
it is not installed or automatically admitted. Only empty operation staging
directories are cleaned automatically.

Admission preparation now exposes a controlled one-shot handle. Cancellation
before its final publication barrier retains any private recovery material as
`RECOVERY_REQUIRED`; it does not grant admission readiness. Cancellation after
the barrier returns false. An existing bound journal can be freshly inspected
and explicitly resumed through the established admission-store recovery API;
no saved `PASS` is replayed. Corrupt payloads or missing pins are not repaired.

Back/close requests cancellation without waiting on the render thread. Returning
to the parent keeps the same controller and any final result; leaving the entire
developer session closes it non-blockingly. Reopening never rehydrates consent
or a ready selection from historical files. Editing a form invalidates its
operation binding even if the previous text is subsequently restored.

## Reports and data

An owned report directory contains at most five JSON files:

| File | Origin |
| --- | --- |
| `analysis.json` | Existing versioned CompatibilityReport serializer |
| `conversion.json` | Existing conversion result, only when conversion was attempted |
| `transformation-log.json` | Existing versioned TransformationLog, when provided by that attempt |
| `inspection.json` | Existing independent inspection report, including structured refusals |
| `preparation.json` | Schema 1, `LEGACY_PRESERVED_PREPARATION` operation summary |

The summary binds the operation ID, selected original/data/candidate hashes,
admission plan hash, terminal state, report paths, structured error and explicit
non-installation/runtime status. Paths are informational, not permission to open
or apply arbitrary saved files. Existing candidates receive analysis, inspection
and summary, not an invented transformation history. Early refusal before an
owned report directory exists is represented in the typed error only.

Reports are atomically published with no replacement of unknown files. A summary
write failure revokes `READY`, even if a complete candidate/admission already
exists; those recovery files remain visible in the result. Cancellation does not
delete diagnostic evidence. Reports are not yet indexed by the separate saved
report browser; this screen presents their paths and allows copying details.

The full bundle is an explicit local dependency, reviewed by manifest hash. There
is no hidden download, Gradle-cache lookup, included-data fallback, old-game JAR
embedding or permission to redistribute the private bundle. Public simple-content
still uses its existing included finite data. See [data policy](PORTABLE_MAPPING_DATA.md)
and the [independent admission contract](PRESERVED_ARTIFACT_ADMISSION.md).

## Evidence and remaining gates

[Artifact-bound ledger](evidence/preserved-preparation-2026-09-19.json) records the
real authored original/candidate/data pins, focused positive/negative checks,
serial compilation/package/CLI checks, archive comparison and actual failures.
Tests exercise the actual engine, inspector, admission store and installer;
installation tests use inert platform metadata, not a running Forge instance.
An exact converted-fixture hash match is static evidence, not gameplay proof.

The screen includes en_us/pt_br, native focus/edit/list widgets, pagination by
tabs, scrolling and resize preservation. Client, dedicated server, GUI usability,
discovery, activation, behavior, multiplayer and current-artifact cacheless game
validation are **NOT_RUN**. No Minecraft process was launched in this slice.

No public promotion, composed/cohort admission, preserved managed updates through this screen,
automatic restart, world migration, licensing decision, billing, new mod
acquisition or publication is included. The follow-up slice was
**LB-05 / INSTALL-PROMOTION / PRESERVED_MANAGED_UPDATES**:
its [separate developer API](PRESERVED_MANAGED_UPDATES.md) now implements exact
owned/unloaded replacement with backup and deterministic interruption handling.
Connecting it to the coordinator is still a separate follow-up.
All native and acceptance prerequisites remain required; the project stays Beta.
