# Product cohort target preview — bounded advanced inspection

2026-09-12, LB-01 / PRODUCT-COMPOSED / COHORT_TARGET_PREVIEW.
Implementation delivered; native acceptance/public promotion remain PARTIAL.
[Artifact-bound evidence](evidence/product-cohort-preview-2026-09-12.json).

Data follow-up: [FINITE_COHORT_DATA](FINITE_COHORT_DATA.md) now supplies included
data for the exact pair. The original 34-test/JAR ledger below is historical;
the follow-up has its own current artifact and 167-case validation ledger.

## Implemented player flow

**Group... / Conjunto...** appears beside the single-mod advanced preview.
Check 1–4 inventory rows explicitly: a focused row alone is not a selection.
Search-hidden checked rows are included. The child freezes and displays every
selected path, observed identity and SHA-256 before **Inspect / Inspecionar**
starts work. Opening/resizing does not submit a request; a fresh screen never
inherits the previous group's result. Reviewed rows must still belong to the
current inventory when submitted.

The mapping-bundle field may be blank for this action: it selects the included
finite cohort contract, not simple-content data. A nonblank field selects the
full local bundle; missing/relative/excessive/unreadable explicit paths fail,
with no fallback, Gradle-cache discovery or automatic download. Only the **exact authored
library/consumer pair** has an implemented [target recipe](DEPENDENCY_COHORT_TARGET.md).
Allowing 1–4 rows enables missing/extra-provider diagnostics, not arbitrary
multi-mod conversion. The built-in simple player profile is unchanged.

The result shows sources, declared dependencies (mandatory/range/side/order),
class counts, whether linkage was examined, diagnostics and remediation. Checked
linkage is not independently a pass: consult individual findings. A successful
static result shows two private candidate hashes/sizes, not output paths/bytes.
The engine already discarded those temporary JARs. Errors remain retryable.

## Operation and authority boundaries

- `CohortInspectionView` is immutable display evidence, never a `JobRecord`,
  `InstallSelection`, staged artifact or receipt. Its permission methods always
  return false, including on a constructed successful display object.
- `ConversionController.inspectCohort` uses the existing bounded coordinator and
  `LegacyEngineService.planCohort` worker. No CLI.main/stdout, original/target
  execution, duplicate recipe, new mappings or adapters.
- Real engine stages/counters are displayed. The busy gate excludes concurrent
  refresh, preview, conversion enqueue and Apply. There is no invented percentage.
- Accepted cancel/close before publication produces no successful pair/report.
  Once the report-publication barrier wins, cancellation refuses. Back retains
  work inside the same converter session; leaving closes/cancels the controller
  without a render-thread join. No Minecraft process is closed/restarted here.
- No mods file, staging JAR, installation journal or world is created. Existing
  ordinary jobs/installation state are unchanged. Trying this pair through
  ordinary simple conversion is still BLOCKED; a preview pass is not admission.

Distinct states: REVIEW, RUNNING, STATIC_PAIR_VALIDATED, BLOCKED, FAILED,
CANCELLED. No CONVERTED or PENDING_RESTART state is produced by this preview.

## Reports and limits

`reports/<new-operation-UUID>/cohort-preview.json` uses schema 1 and report type
`PRODUCT_COHORT_PREVIEW`: frozen observations, unchanged runtime identity, fixed
promotion gates and the **unchanged engine result tree** under `engineReport`.
That includes source/target graphs, scoped ASM/linkage proof and the versioned
TransformationLog. Existing CompatibilityReport/TransformationLog schemas remain
unchanged. This report is not a CompatibilityReport success or install receipt;
the existing history reader ignores it as a non-job report.

Create-new, atomic-per-file publication uses existing path revalidation and a
2 MiB UTF-8 report cap. Originals/unknown obstructions are never overwritten;
only empty failed operation directories are cleaned. Display bounds: 4 sources,
2 targets, 64 dependencies per source, 32 diagnostics plus an omitted count,
512 code points per untrusted text and 1,024 wrapped visible lines. Full technical
details stay in the report. Copy details explicitly includes local paths; the
screen does no filesystem I/O or automatic path opening. These are bounded
workloads/cooperative cancellation, not a hard JVM heap-isolation guarantee.

## Real APIs and physical side

Exact local source archives inspected, not packaged:

- Forge 36.2.42 / official 1.16.5 sources SHA-256
  `bbe2d148f96694271c43fc2ba584463d75d9ffc490abd0469ae66b831293d376`:
  `GuiScreenEvent.InitGuiEvent.addWidget(Widget)` and `Post`.
- Forge 47.4.23 / official 1.20.1 sources SHA-256
  `4bd6656a7cbe3809da68f3b709ca0f2fa186575f58538668c58ce30cb38c4932`:
  `ScreenEvent.Init.addListener(GuiEventListener)`, `Init.Post`,
  `Screen.setInitialFocus`, `resize`, `removed`, `onClose`,
  `Button.Builder.bounds` and `AbstractSelectionList` scroll accessors.

Forge's primary [screen documentation](https://docs.minecraftforge.net/en/1.20.1/gui/screens/)
provides lifecycle/widget guidance, checked against those exact sources.
Only target APIs are used; no cross-version screen adapter or bootstrap service.
The screen/nested widgets are physical-client-only. Common projection/controller
do not depend on client classes. en_us/pt_br, focus/narration, Page Up/Down,
Home/End, scroll preservation and resize reconstruction are implemented.
Compilation/bytecode/locale checks do not prove native keyboard/layout behavior.

## Validation and remaining gates

34 focused checks passed, zero failures/errors/skips at final recheck; 15 new
tests plus extended physical-side checks. They cover the real authored pair
through controller/service, exact hash equality, no receipt/Apply inheritance,
missing provider, intact pair plus unknown third artifact, changed original,
corruption/duplicates/stale rows, invalid data, actual accepted cancellation,
coordinator exclusion, report-path obstruction and existing conversion jobs.
The first invocation used a wrong historical fixture path: three regressions
failed with NoSuchFileException; corrected invocation and final recheck passed.

Report-path obstruction is a real filesystem negative, not physical disk-full,
OS ACL denial or Windows locked-file recovery coverage. All modules assemble
and the final public JAR/runtime/API/engine/CLI audits pass separately. Existing
validation probes compile but are not executed. Full regression is deferred.
Client/server, behavior comparison, multiplayer, native scale/focus and final
packaged game launch: **NOT_RUN**. No new compatible mod or complete port claim.

## Next development, not acceptance

The original **LB-02 / DATA-EXPAND / FINITE_PREVIEW_DATA** next step delivered
[FINITE_COHORT_DATA](FINITE_COHORT_DATA.md). Current next step is
**COMPOSITION_DATA_SCOPE** for the individual preview, without broadening this
annotation-only contract. The original task was to identify minimal source/target
data required by the finite previews, version scope/hashes/provenance, audit
redistribution and implement only a permitted included or explicit-data route.
Never embed/relabel the full local-only bundle. Unknown external references
remain blocked. Implemented static parts of COMPOSE-01/PRODUCT-COMPOSED may
support headless development; their acceptance/promotion gates remain required.
