# Product composition inspection: bounded vertical slice

2026-09-12 addition: [Data/Dados review](PROMPT_02_HEADLESS.md) inspects the
explicit prepared bundle, shows scope/counts/provenance policy and binds its
manifest hash in the controller session. Reuse revalidates every payload;
manifest drift blocks without fallback. No install permission or new profile.
The new button's native focus/layout validation is NOT_RUN, not inherited from
historical screenshots or controller tests.

LB-01 / PRODUCT-COMPOSED, 2026-09-12. Implements the player-facing **static
inspection** of the existing finite logic-plus-recipes composition. This does
not complete public composed conversion, installer admission or native acceptance.
The six existing profiles, runtime identity/ABI and simple-content default remain
unchanged. No new mapping, adapter, legacy equivalence or download is introduced.

## Player workflow

Select a unique Forge 1.16.5 candidate in Inputs. For this advanced inspection,
explicitly select an existing **full local mapping/API bundle** in the mapping
field, then choose **Preview (adv.)**. The screen shows real worker progress,
the recorded input SHA-256, operation IDs, implementations, claim counts,
proof prerequisites, required runtime identity and the remaining admission gates.
It supports Cancel, retry, copied details and return to the converter. English
and Brazilian Portuguese labels are included.

Blank mapping data still works for ordinary simple-content conversion. It is
deliberately insufficient for composition inspection and produces
COMPOSITION_MAPPING_DATA_REQUIRED, with no fallback or acquisition. A bundle is
an explicitly selected directory, never a mod/JAR/classloader. Current full data
are local-use only; neither they nor old Minecraft/Forge implementations are
added to the public artifact. See [portable data](PORTABLE_MAPPING_DATA.md).

The only inspected combination is the engine's existing owned-body logic plus
datapack recipe/resource contracts. PREVIEW_VALIDATED means the finite in-memory
proof passed; it is **not CONVERTED**. BLOCKED means that proof refused the input.
FAILED covers input/data/path/I/O errors; CANCELLED is an accepted cancellation.
No generic profile picker, converted JAR, candidate export or Apply action exists
in this screen. The original selection cannot be inferred from an old receipt.

## Reused pipeline and boundaries

`CompositionScreen → ConversionController.inspectSelectedComposition →
LegacyEngineService.previewLogicWithRecipes` is the only inspection route.
The existing [composition engine](COMPOSITION_PREVIEW.md) performs all metadata,
ownership, mappings, body preservation, resources, ASM and scoped offline-linkage
checks. The product does not duplicate these rules or call CLI.main/stdout.

The controller revalidates the observed input path and supplies the inventory's
exact SHA-256 to the engine. Only a candidate actually inventoried in this
instance is eligible. Duplicate, invalid, already-converted and unconfirmed
inputs are refused. Explicit missing/relative/oversized/invalid mapping paths
produce errors. A changed original cannot inherit the old observation hash.

One existing bounded coordinator and engine worker are reused. No new executor,
classloader, network client, mod discovery or runtime registration is added.
Inspection is mutually exclusive with conversion, inventory and installation;
it is never enqueued as a `JobRecord`. Existing jobs and installation state are
untouched. The UI polls only immutable summaries, not files or full engine maps.
Only one inspection summary (up to eight operation summaries, not their complete
claims/class bytes) is retained. Engine limits remain those of COMPOSE-01; these
are cooperative workload limits, not a hard process-wide heap sandbox.

## Report contract and publication

Successful and blocked proofs save an operation-owned
`legacybridge/reports/<UUID>/composition-preview.json`. No staging JAR or
transaction is created. The engine requires a prospective output path; the
controller uses an unused UUID-based path in staging but never publishes there.

The new envelope has schemaVersion 1 and reportType
PRODUCT_COMPOSITION_INSPECTION. It records status, fixed
publicConversionAllowed=false / installationAllowed=false, runtime ID/version,
remaining gates, and the original engine report **without rewriting its schemas**.
That nested report includes source-data hashes/provenance, exact implemented
adapter bindings and the proposed TransformationLog when the corresponding
proof reached them. Failed partial proofs do not invent absent evidence.

The report's UTF-8 payload is capped at 2 MiB and written using the existing
create-new atomic report writer. The controller's cancellation/close gate is
checked before report publication; once that phase has won, cancellation is
refused, not relabeled successful. Before that boundary, accepted cancellation
does not expose an inspection result/report. Cleanup removes only an empty
operation directory; unexpected files/changed paths are not deleted recursively.
Serialization has transient memory cost before the report-size check; this is
not claimed as a hard allocator limit. Failure does not produce a partial JAR.

Displayed paths are sanitized text and can be copied with details. This slice
does not add an OS opener for preview reports; users can inspect the named JSON
manually. Reports may contain private local provenance paths and should be
reviewed before sharing. The UI wraps bounded text with at most 1,024 rows plus
an explicit limit notice. Resize preserves scroll/focus by native reconstruction;
visual and keyboard behavior still require native QA.

## Admission design

`CompositionInspectionView` is a typed, bounded, immutable presentation contract,
not an executable plan or approved installation. Its permission methods always
return false, including for a manually constructed Java value. No installer
accepts this type or the JSON envelope.

| Remaining gate | Meaning |
| --- | --- |
| STATIC_PROOF_REQUIRED | This selection has not passed the finite proof (omitted only on PREVIEW_VALIDATED). |
| PUBLIC_PROFILE_NOT_PROMOTED | No composed profile is publicly admitted. |
| INSTALLER_ADMISSION_NOT_IMPLEMENTED | The public installer still accepts only its existing simple-content contract. |
| NATIVE_COMPARISON_PENDING | Original/target isolated behavior comparison is not established by inspection. |
| FINAL_PACKAGE_QUALIFICATION_PENDING | Current packaged client/server/native UI qualification is not inherited. |

Future public promotion must bind exact operation revisions, data hashes,
adapter implementations/descriptors and candidate identity, re-prove the
original and output, close all required dependencies, and obtain installer/UI/
packaging/native evidence. A preview report must never become the approval token
or bypass those checks. Existing `legacybridge_runtime` / `0.1.1-SNAPSHOT` and
adapter signatures are retained; a future incompatible ABI needs explicit
migration/reconversion, not a display-name or JSON change.

## Evidence and remaining work

See [the artifact-bound ledger](evidence/product-composition-2026-09-12.json).
The real fixture is the existing original Forge 36.2.42 reobfuscated JAR with
four classes, a helper/subclass and local recipe. Its original remains intact.
Checks compare the product's nested engine report against a fresh shared-service
preview and verify that ordinary simple conversion still refuses this fixture.
Negative checks include explicit-data refusal, wrong paths, duplicates, changed
input, unsupported body, cancellation/close and isolation from existing jobs.
No original class is executed; static fixture validation is not native behavior.

Both exact source archives were rehashed, and the legacy GuiScreenEvent versus
target Screen focus/removal/resize APIs were re-read. Hashes remain
`bbe2d148f96694271c43fc2ba584463d75d9ffc490abd0469ae66b831293d376` (source) and
`4bd6656a7cbe3809da68f3b709ca0f2fa186575f58538668c58ce30cb38c4932` (target).
No new cross-version mapping equivalence was needed. Native rendering, keyboard,
GUI-scale/resize and exact-JAR dedicated-server startup are NOT_RUN. No game or
screen automation is authorized, and no release/upload is performed.

Implementation of this inspection vertical slice hands off independently of
those pending tests. Next headless development: LB-19 / DEPS-COHORT, first a
bounded authored library+consumer dependency/ownership/linkage slice. Keep its
public admission closed and all prerequisites PARTIAL until their own evidence
exists. Do not reopen the COMPOSE QA-harness expansion as the default next task.
