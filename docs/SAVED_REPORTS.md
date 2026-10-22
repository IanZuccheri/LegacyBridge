# LB-04: reopen saved reports without restoring installation authority

2026-09-12, `UI-SAVED-REPORTS`. This finite product cut adds persistent report
associations and a native, read-only viewer. It does not add a conversion profile,
change a mapping/adapter, enable composed Apply or prove gameplay compatibility.
Implementation is headless-validated; native acceptance remains **PARTIAL / NOT_RUN**.
Licensing/monetization was explicitly postponed by the user. LICENSE is unchanged;
no royalty, payment integration, publication or final licensing choice was added.

## Player flow

Open **Converter mods → History → select a job → Saved reports**. Choose
Compatibility, Conversion or Transformations. The worker reads and checks one
saved report, then the screen displays literal text in pages. Previous/next,
scrolling, keyboard focus, cancel, copy-this-page and Back use native widgets;
en_us and pt_br are included. Resizing retains the selected page and scroll.

The view explicitly distinguishes matching a saved hash from authenticity,
conversion success, installation and activation. A JSON `clickEvent` is text,
not a command or hyperlink. It cannot invoke Apply, open historical paths through
the OS, read/execute a mod JAR, discover new mods or modify the loaded set.
Native rendering/focus/resize/accessibility has not been exercised this turn.

Only **new controller jobs** create an index. Existing schema-1/2 receipts still
open as receipts, but old report associations are not guessed from filenames,
directories or modification time. CLI-only operations do not synthesize UI job
receipts. An absent index/report, changed hash, bad JSON/encoding, unsafe path,
unsupported index schema or limit produces an explicit error; the view stays usable.

## Separate index schema

The new `LEGACYBRIDGE_SAVED_REPORTS` schema is **1**, at
`legacybridge/reports/<operation UUID>/reports-<job UUID>.json` beside the job
receipt. Existing CompatibilityReport, TransformationLog, job receipt schemas
1/2 and installation journal formats are unchanged. Analysis and conversion can
have different operation directories; the index records their actual association.

Fields are exactly `schemaVersion`, `reportType`, `jobId`, `inputSha256`, and
`reports`. Each of 1–3 distinct entries has exactly `kind`, `path` and `sha256`.
Kind is COMPATIBILITY, CONVERSION or TRANSFORMATIONS; its file is respectively
`analysis.json`, `conversion.json` or `transformation-log.json`. Paths must be
`legacybridge/reports/<canonical UUID>/<fixed filename>` relative to gameDir.
Only lowercase 64-digit SHA-256 and canonical UUID spellings are accepted.

The writer hashes bounded snapshots twice and atomically publishes a new index
without overwriting an existing file or the original. Installation callbacks do
not rewrite it. If indexing fails, the controller records the failure while
retaining the conversion's real state, artifact and job receipt when that receipt
can be written. It does not erase a committed JAR or imply index failure undid it.
A process interruption can leave no index or an orphan index before the receipt;
the reader does not search for/recover such associations or create Apply authority.

## Limits and trust boundary

`SavedReportStore` runs on the existing single bounded controller worker, not the
render thread. Calls refuse while another operation/queue is active or history
is incomplete. It retains only one report snapshot, never a second engine or queue.

| Boundary | Limit / behavior |
| --- | --- |
| One report | 1 MiB raw UTF-8; larger files refused, not truncated |
| Index / selected receipt | 16 KiB / 64 KiB |
| JSON parser | Existing depth 64, 20,000 nodes, 16,384-character strings, 1,024-character numbers |
| Display page | 4,096 UTF-16 units, at most one extra unit to keep a surrogate pair intact |
| Work | 10-second cooperative deadline; cancellation checkpoints around path and 4 KiB read operations |
| Path scope | Explicit reports directory; canonical descendant paths, regular files, no observed symlink/junction aliases or traversal |

Stable reads compare file identity, size and modification time before/after.
The reader binds job/input identity, validates all index entries, verifies the
selected report's hash, parses bounded JSON without chat-component deserialization,
then rechecks the receipt/index bytes before publication. It displays a snapshot,
not a guarantee that the file cannot change afterwards. Actual control/format
characters and Minecraft section-sign formatting are removed **only for display**;
the UI says when this occurred and the hash always describes the untouched raw bytes.

This is **not authentication or an OS sandbox**. A party able to rewrite both index
and report can choose matching hashes. Arbitrary external hardlinks are not globally
enumerated. Concurrent hostile filesystem replacement, OS I/O stalls and hard heap
isolation are not solved by these checks. The bounded parsed report is data, never
an independently validated installation contract. History limits remain unchanged;
new indexes also count toward its bounded directory-entry scan, and no automatic
retention, migration, garbage collection or silent deletion was added.

Cancel/close requests do not block the render thread. The controller checks its
cancellation state again under the publication lock, refusing even a late successful
read. Back cancels an outstanding viewer read and returns to History; an unexpected
screen removal closes its owning session. Native teardown remains a separate QA gate.

## APIs and verification

The screen uses the existing exact **1.20.1 / Forge 47.4.23** sources and
[recorded API baseline](P4_UI_API_BASELINES.md): `Screen` lifecycle/focus/resize,
`Button.builder`, `ObjectSelectionList`, `Component.literal` and physical
`Dist.CLIENT` isolation. See also the official
[Forge screen documentation](https://docs.minecraftforge.net/en/1.20.1/gui/screens/).
The legacy **1.16.5 / Forge 36.2.42** `GuiScreenEvent` remains a source baseline,
not a proposed equivalent API or a new GUI conversion rule.

[Versioned evidence](evidence/saved-reports-2026-09-12.json) separates focused
unit/controller checks, static conversion of the real authored legacy fixture,
all-module assemble, packaged-artifact audits and structural contract checks.
Negative fixtures include changed/missing reports, invalid index/schema/paths,
duplicate kinds, oversized data, malformed UTF-8/JSON, cancellation and file races.
The source fixture is scanned/converted, never executed. Client-side bytecode
inspection does not load screen classes or count as dedicated-server startup.

Still pending: real en_us/pt_br rendering, keyboard/narration, GUI scales and resize,
cancel/Back/reopen in Minecraft, dedicated-server loading and full current-artifact
end-to-end/multiplayer QA. No screen was opened and no screenshot is claimed.
The six existing profiles and simple-content installation semantics stay unchanged.

Next development: **LB-05 / INSTALL-PROMOTION**, beginning with finite independent
admission for a precisely audited preserved-code contract. Its acceptance
dependencies remain COMPOSE-01 and DEPS-COHORT; static preparation must not silently
promote public composed conversion, migrate journals or expose new Apply operations.
