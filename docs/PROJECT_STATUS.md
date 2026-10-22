# LegacyBridge: prompt checkpoint and safe closeout

## Current priority (2026-10-22): free open-source experimental release

The owner authorized publication of LegacyBridge source on GitHub under
GPL-3.0-only with the narrow Minecraft/Forge linking exception. No 5% royalty
or mandatory payment remains. The owner will submit CurseForge manually.
See [publication scope and qualification](PUBLICATION_2026-10-22.md),
[license terms](../LICENSING.md) and [English project page](../README.md).

Repository: https://github.com/IanZuccheri/LegacyBridge . Feature development is
deferred for this handoff; the remaining contract is not declared complete.
The public workflow still admits only simple-content-v1. Current native
client/server/multiplayer qualification is NOT_RUN; older results below are
historical and do not certify a newly packaged hash. Publication build results
and exact artifact hashes belong in the publication record, not the old ledgers.
The [publication evidence](evidence/publication-2026-10-22.json) identifies the
new legal-only JAR packaging: 707 class entries remain byte-identical. Selected
headless checks passed; the full default build was interrupted and remains
incomplete. No current-hash client/server/gameplay acceptance is claimed.

The feature cursor remains LB-10 / ITEM-EQUIPMENT, with
IRON_HELMET_INTEGRATED_SUCCESSOR next. Owner license selection resolves only
that rights decision: PACKAGE-RIGHTS is PARTIAL. Contract totals are now
15 ACCEPTED / 20 PARTIAL / 1 BLOCKED / 45 NOT_STARTED across 81 cuts;
these are workflow states, not percentages of compatible mods.

## Historical delivery (2026-10-05): closed helmet dispenser

**LB-10 / ITEM-EQUIPMENT / IRON_HELMET_DISPENSER_CLOSED_RECEIVERS** is implemented
as a [separate versioned operation](IRON_HELMET_DISPENSER.md). It retains native
query/predicates and first eligible selection for exact ServerPlayer or Zombie.
It splits one, then invokes the unchanged player HEAD effect or writes Zombie
HEAD silently, followed by drop chance 2 and persistence. Unknown first receivers
throw before consumption; they are not skipped or turned into no-target fallback.

The prior item/HEAD classes and all 68 previous descriptors remain unchanged.
A new authored Java-8 original exposes the real legacy dispenser helper. The
runtime operation is not registered for an item and has no constructor recipe,
candidate, new public profile or Apply. Armor conversion remains **UNSUPPORTED**.

[Artifact-bound evidence](evidence/iron-helmet-dispenser-2026-10-05.json) separates
compiled-bytecode/synthetic endpoint checks from native behavior. The two world
query engines differ: retaining the first returned result is not proof of equal
spatial enumeration. Native client/server/behavior/multiplayer remain NOT_RUN.

Validation: **744 selected engine tests passed**, including 50 new tests, with
zero failures/errors/skips. Original helmet/dispenser fixture archives passed
four/five tests respectively. Compile, assemble, package/runtime audits and
installed CLI passed. The initial 50-test focused run overlaps final regression
and is not counted twice. The CLI rejects the new four-method fixture rather
than widening the previous inspection grammar; no partial output is produced.
Contract verification passed for 23 prompts/81 cuts. Its 75 passing tests and
two native-symlink platform skips are separate; neither qualifies native gameplay.

Public JAR SHA-256: `536d9d5da318c92a6ee02f198e777d18668514f60d22c685740b1a8383a7e68e`.
Both originals and all four previous equipment candidates were hash-checked.
Prior ledgers are historical, not qualification of this new artifact.

**Next: LB-10 / ITEM-EQUIPMENT / IRON_HELMET_INTEGRATED_SUCCESSOR.**
The [next finite implementation plan](IRON_HELMET_INTEGRATED_ITEM_PLAN.md)
composes a new explicitly owned item/use/dispenser contract without changing
old identities or guards. Other vanilla receivers (including ArmorStand),
fallback, Equipable, hooks/rendering and native comparison still block lowering.
Do not repeat independent helpers or treat a throwing callback as a valid port.

ITEM-EQUIPMENT remains PARTIAL. No game/server/screen was opened, no mod acquired
or published, and no license changed. Counts remain 15 ACCEPTED / 19 PARTIAL /
two BLOCKED / 45 NOT_STARTED across 81 cuts; not a compatibility percentage.

Previous equipment delivery: [guarded repair through item use](OWNED_STACK_REPAIR_USE_CONTRACT.md).
Its private two-class/six-method candidate preserves received-hand lookup,
client SUCCESS without mutation and a server helper with same-stack CONSUME/PASS.
Creative restoration, cancellation, caller threads and synchronization still
need native qualification; the earlier [ledger](evidence/owned-stack-repair-use-candidate-2026-10-05.json)
is historical evidence, not qualification of this new public artifact.

Canonical execution contract: [LB-00 through LB-22](contract/README.md), with
[machine-readable states, dependencies and cursor](contract/status.json).
This is the newly consolidated numbering, not a recovered verbatim historical
list. Previous foundation: [PLAIN_BLOCK_WITH_ITEM](PLAIN_BLOCK_CONTRACT.md)
preserves the native Block/BlockItem registration graph and exact STONE expression.
Its [historical ledger](evidence/plain-block-2026-09-26.json) remains available.
Current block variants extend private proof, not that public grammar.
No game launch or prerequisite acceptance is inferred.

Previous LB-10 delivery: [durable Item and iron repair predicate](DURABLE_ITEM_CONTRACT.md);
general damage/breakage, anvil qualification, tools/armor/general attributes and native QA remain.
Previous delivery: **LB-09 integrated lifecycle suite**: COMMON native
Loading/Reloading, exact spec/type filters, volatile per-field observations,
queued late registry access, owned event priority/cancellation and fresh
integer/boolean command trees in one seven-class/thirty-four-method graph.
Source/target/readback proof, ASM and offline access/linkage run without executing
original classes. The private service returns proof/hash, not install authority.
All six existing grammars and public Convert/Apply remain unchanged.
See [implementation and remaining scope](LIFECYCLE_SUITE.md).
The selected implementation milestone is delivered; LB-09 remains PARTIAL for
native comparison, prerequisites and broader unsupported patterns.
Current headless development: **LB-10 / ITEM-EQUIPMENT / IRON_HELMET_INTEGRATED_SUCCESSOR**.
Use implemented static parts of COMPOSE-01/LIFE-EVENTS only as development
prerequisites, never as accepted gates. No game launch or public promotion.
LB-04 / UI-SAVED-REPORTS is implemented and headless-validated; native acceptance
remains PARTIAL. The user explicitly postponed licensing/monetization until later,
including considering fully free distribution. No license or billing was changed.
LB-02 delivered data review/binding and aggregate controls; its acceptance is
still PARTIAL, not only for native QA: retained-structure/RSS/adversarial memory
qualification also remains. PACKAGE-RIGHTS remains a release gate, not the active
implementation cursor; technical preparation does not authorize relicensing or publication.
LB-02 previously delivered FINITE_COHORT_DATA for the exact pair without a bundle.
LB-01 delivered COHORT_TARGET_PREVIEW; LB-19 delivered finite private target
assembly/validation. Public cohort conversion and native acceptance remain pending.
LB-01 / PRODUCT-COMPOSED
now delivers its bounded typed static inspection/screen, not public composition promotion. The bounded
LB-04 / UI-HISTORY-READ implementation is delivered, with native acceptance pending.
COMPOSE-01 remains PARTIAL but no longer monopolizes the implementation cursor. JOB-INSTALL-RECEIPTS and
JOB-HISTORY-READ passed only their finite headless acceptance. LB-06 / PUBLIC-CURRENT-E2E remains a pending native gate; the finite native UI cut
LB-04 / DATA-SIMPLE-01-NATIVE_UI passed on its historical public artifact, not the current JAR.
The deferred handshake blocker belongs to LB-12. The planning audit itself did
not advance gates; the [new native evidence](EMBEDDED_SIMPLE_NATIVE_UI_VALIDATION.md)
now justifies this bounded transition. No conversion features were enabled.

Review date: 2026-10-01. Status: **experimental/Beta, not approved for publication**.
This is a cross-prompt index, not a replacement for the artifact-bound validation
ledgers. Historical runtime observations are not automatically transferred to a
new public JAR. Test counts and resolved-symbol counts are not compatible-mod counts.

Exact baselines remain Minecraft 1.16.5 / Forge 36.2.42 to Minecraft 1.20.1 /
Forge 47.4.23 / Java 17. Public runtime identity remains
`legacybridge_runtime` / `0.1.1-SNAPSHOT`. No version upgrade was made.

## Previous delivery (2026-09-19): LB-10 durable item candidate

[Implementation and oracle](DURABLE_ITEM_CONTRACT.md);
[artifact-bound ledger](evidence/durable-item-2026-09-19.json).
Native Item.Properties.durability and isValidRepairItem are preserved, with exact
Minecraft/Forge mappings, declarations, opcodes and finite control-flow checks.
49 new cases cover service/direct parity, unchanged profile refusals, alternate
owned names/literals, 28 incompatible patterns, ten API-drift cases, immutable
originals and cancellation at serialization/reopen/linkage/completion.
All 631 final engine regressions passed with no failures/errors/skips.
Contract: 75 passed, two existing Windows symlink assumptions skipped; 23 prompts /
81 cuts validated, cursor LB-10/BLOCK-STATE. Final assemble/audits/CLI passed again;
public hash remained unchanged. Full unfiltered/native suites were not run.

Public JAR: 1,799,643 bytes / 713 ZIP entries,
698 unchanged, 12 changed, 3 added, none removed.
All 45 prior descriptors, executable runtime adapters, UI/installers/data/JiJ
remain unchanged; two OFFLINE metadata operations added.
SHA-256: `240a35f90be74e74d0d06eb25f04202c6030d003235819d02c93d344a37d8daf`.
Original: `6d14e862d077d873d6f85e351e3c06f4913089ad33f70a011af16cec76e2b07a` (5883 bytes).
Private discarded candidate: `a1d513a0434870de8d933badc4f5be87f5206644d168802ac0f586ed872964ae` (4678 bytes).

The new candidate uses explicit full mapping/API inputs. No data/cache independence
or in-game claim is added. Full unfiltered tests, native client/server, actual
anvil repair/consumption, save/load and multiplayer remain NOT_RUN. Tools, tiers,
armor, attributes and damage mutation remain unsupported.
The next step at that checkpoint was **PLAIN_BLOCK_WITH_ITEM**, now implemented:
finite native preserved Block/BlockItem
registration/property/tag contract. ITEM-EQUIPMENT remains PARTIAL with remaining
implementation and QA listed; no cut or prerequisite is falsely accepted.

## Previous delivery: LB-09 integrated lifecycle suite

[Implementation/limits](LIFECYCLE_SUITE.md) and
[artifact-bound ledger](evidence/lifecycle-suite-2026-09-19.json).
582 focused regressions passed, including 55 new suite cases; 72 overlapping
final engine checks passed after the final diagnostic/report refinements.
Zero failures/errors/skips in these final engine runs. Contract verification passed:
75 cases passed, two existing Windows symlink assumptions skipped; 23 prompts /
81 cuts, current LB-10/ITEM-EQUIPMENT. This is structural, not native proof.
All-module assemble, public and
runtime JAR audits, engine boundary and installed CLI smoke passed.
Full unfiltered tests, game startup, native reload/commands and multiplayer:
NOT_RUN. Brigadier API-only isolated probes agree, but do not validate the mod.

Public JAR: 1,788,455 bytes / 710 ZIP entries. Against the previous public JAR:
692 entries identical, 15 changed, three added, none removed. All 42 prior
adapter descriptors and executable runtime adapters remain unchanged; three
implemented OFFLINE operations added. Public UI/installers/data/JiJ unchanged.
SHA-256: `08f5cdfc8bd8b30df3206000f6dd4256777e3a5dcc769bf305ae5ebaa4ee1963`.

Fixture source JAR: `2332eb0aa080143c8d00c027a810e090fc5c6bc96148c7fcfcdf28d5c5fa414a`.
Private candidate: `c5978a81f1ac37e69a5811c8d2a10f40ac171e6da0ac563628551e4e96a2dc71` (11,295 bytes).
Seven original classes / 34 methods retained, zero replaced, ten lambda sites;
exact source/target/readback and ASM/access/linkage checks passed.
No original classes executed, no candidate exported, no game launched.
Next **LB-10 / ITEM-EQUIPMENT / DURABILITY_REPAIR**. LB-09 remains PARTIAL,
with native gates and broader unsupported patterns explicitly retained.

## Previous delivery: LB-09 world-scoped SERVER config

[Implementation/limits](SERVER_CONFIG_SCOPE.md) and
[artifact-bound ledger](evidence/server-config-2026-09-19.json).
463 focused checks passed; 46/47 profile/adapter regressions passed with one
missing opt-in skip. The final 61-case SERVER/adapter run explicitly enabled
that test and passed without skips. Across overlapping runs: **510 distinct
engine cases passed, 54 new**. Full unfiltered/native suites NOT_RUN.

All-module assemble, public/runtime JAR audits, engine consumer boundary and
installed CLI smoke passed. The artifact has 1,766,064 bytes / 707 ZIP entries:
689 identical, 15 changed, three added, none removed against the previous JAR.
All 40 existing adapter descriptors, executable runtime adapters, public
UI/installers, bundled data and JiJ bytes are unchanged. Two real OFFLINE metadata
operations were added; runtime identity/version stays unchanged.

Public SHA-256:
`42eb62ef337fd4bc08b7aebb6f5d8e634eec682e378172c102e8a2d791e906f4`

Fixture: `cc7cf3d985a64844f86c55b584aa5fb8236e246ea6a9ed1b882bf6b8f1fde3d1` (8,886 bytes).
Private candidate: `ec7415c85b9d264ee7a1c731dd2b8a5ef88c7e71ed60f64b58a5180dc0cd4e34` (7,332 bytes).
No candidate export, public promotion or Minecraft execution.
Contract: 75 checks passed / two existing Windows symlink privilege skips;
repository verifier passed (paths/state only, not evidence execution/freshness).
Next: **CONFIG_RELOAD_SCOPE**, not an assertion of native SERVER/sync acceptance.

## Previous delivery: LB-09 guarded CLIENT config

[Implementation/limits](CLIENT_CONFIG_SCOPE.md) and
[artifact-bound ledger](evidence/client-config-2026-09-19.json).
409 focused checks, 35 profile/adapter regressions and 58 final client-config/CLI
checks passed: overlapping runs, **456 distinct engine cases, 46 new**. The initial
eight failures exposed a missing metadata-operation integration; corrected without
relaxing validation. Two additional negatives cover same-size early effects and
aliased observation state. Final fixture metadata/pt_br wording was corrected and
all 46 cases rerun on the final hash. Full unfiltered/native suites NOT_RUN.

All-module assemble, public/runtime JAR audits, engine consumer boundary and
installed CLI smoke passed twice with identical artifact hash. Contract: 75 passed /
two existing Windows symlink privilege skips (not passed); repository verifier
passed. No new executable runtime adapter; all 38 earlier
catalog descriptors, public UI/installers, bundled data and JiJ bytes unchanged.
Public SHA-256:
`7de49d8fd0781033aadd7904f108600168d99e7dcfd3f76d53687b5686c6268b`
(1,750,922 bytes / 704 ZIP entries including directories: 685 unchanged,
16 changed, three added, none removed).
Counts: **15 ACCEPTED / 17 PARTIAL / 2 BLOCKED / 47 NOT_STARTED**.
Next: SERVER_CONFIG_SCOPE, not reload/sync acceptance or public promotion.

## Previous delivery: LB-09 event priority/cancellation

[Implementation/limits](EVENT_PRIORITY_CANCELLATION.md) and
[artifact-bound ledger](evidence/event-priority-2026-09-19.json).
361 focused checks and 88 final profile/CLI/adapter checks passed: overlapping
runs, **410 distinct engine cases, 41 new**, no failures/errors/skips in these
successful runs. The initial fixture recipe referenced an undefined external
tag and was correctly rejected; it now defines a closed local tag. Initial
failures are retained. Readback-cancellation tests were corrected to distinguish
stage starts from entry/byte progress; the final follow-up records the actual
scratch lifetime and cleanup, not just an absent output.
All-module assemble, public/runtime JAR audits, consumer boundary and installed
CLI smoke passed twice with identical final hash. The corrected cancellation
follow-ups passed 57 cases plus four exact composition cases; these overlap the
410 cases above. Contract: 75 passed / two existing Windows symlink privilege
skips (not passed), and repository verifier passed.
No unfiltered full suite or native qualification.

Public SHA-256:
`e15e418d4b6a6da9b35934ef1d631a4d62d2faea548dc9d06d77dbe4992ec0ba`
(1,732,050 bytes / 701 ZIP entries including directories: 684 unchanged,
14 changed, three added, none removed). The 36 earlier adapter descriptors,
executable runtime ABI, public UI/installers, bundled data and JiJ remain intact;
two OFFLINE metadata operations are additive.

Counts: **15 ACCEPTED / 16 PARTIAL / 2 BLOCKED / 48 NOT_STARTED**.
15/81 = 18.5% unweighted accepted cuts, NOT engineering effort or compatible mods.
Native client/server/discovery/behavior/multiplayer on this JAR remain NOT_RUN.
No acquisition, publication, licensing or monetization change.

## Previous delivery: LB-08 bound receiver handles

[Implementation/limits](OWNED_RECEIVER_HANDLES.md) and
[artifact-bound ledger](evidence/owned-receiver-2026-09-19.json).
274 focused engine checks + 47 profile/CLI/adapter regressions passed, zero
failures/errors/skips. 60 cases are new; those 60 passed again after correcting
fixture descriptions/translations. Repeated runs are not additional coverage.
All-module assemble, public/runtime JAR audits, engine consumer boundary and
installed CLI smoke passed. Contract: 75 passed / two existing Windows symlink
privilege skips, not PASS. No unfiltered full-suite or native qualification.

Public SHA-256:
`d6d95d6f9eca0992c95d87d4920d2d56b2810e9b37f07bfe74321bb112e74018`
(1,714,780 bytes / 698 entries; 683 unchanged, 12 changed, three added, none removed).
Two OFFLINE metadata operations are additive; all 34 earlier catalog entries,
executable runtime adapters/ABI, public UI/installers, included data and JiJ
library are unchanged. Older private candidate hashes are unchanged.
Contract counts remain **15 ACCEPTED / 15 PARTIAL / 2 BLOCKED / 49 NOT_STARTED**.
15/81 = 18.5% unweighted accepted cuts, not total engineering effort or compatible
mods. LB-09/LIFE-EVENTS is the next development cursor, still NOT_STARTED.
No Minecraft/client/server/screen automation, acquisition, publication or licensing
change occurred. Runtime/behavior/multiplayer remain NOT_RUN on the current JAR.

## Previous delivery: LB-04 preserved update screen

[Implementation and limits](PRESERVED_UPDATE_SCREEN.md),
[artifact-bound evidence](evidence/preserved-update-screen-2026-09-19.json).
The existing developer-only native host now selects ADD/UPDATE, an explicit
predecessor transaction/plan pin and incoming admission/data, displays reviewed
versions/artifact hashes, and requests update-specific one-use confirmation.
Resume/restore distinguish the UPDATE transaction from its ADD predecessor;
history is separate, searchable, paginated and inert. English/Portuguese and
existing keyboard/focus/resize controls are wired; native observation is pending.
Same controller/session/navigation/shutdown guards and bounded worker; no render
filesystem I/O, duplicate installer or public Convert/Apply promotion.

**115 focused cases passed, then 39 final changed-code rechecks passed; 23 new
cases total, zero failures/errors/skips.** Runs overlap and are not added as
distinct mod coverage. The actual authored fixture exercises the native form
through review/continue, real update, fresh consent and restore, retaining
original/staged hashes. These are headless checks, not clicking a running UI.
All-module assemble, public/runtime JAR audits, engine boundary and CLI smoke passed.
Fresh contract tests: 75 passed, two existing Windows symlink-privilege skips;
structural verification confirms 23 prompts/81 cuts and LB-08/LINKAGE-GENERAL.
Public SHA-256:
`19d26553e8708d2d63b2362d2147ba54dcfca7c8d8d79ea36e7a0805c98b7079`
(1,664,041 bytes / 682 entries; 669 unchanged, 11 changed, two added, none removed).
Engine/six profiles, runtime/ABI, coordinator/installers/journals, public UI and
included data/libraries are byte-identical to the previous artifact.

No Minecraft/client/server/GUI automation was started. Native UI, startup,
discovery, behavior/multiplayer and final-artifact cacheless runtime are NOT_RUN.
Full data remains local explicit for the developer preserved route. Rights and
public promotion gates remain; experimental/Beta, no publication/license changes.
UI-PROMOTION stays PARTIAL; counts remain 15 ACCEPTED / 14 PARTIAL / two BLOCKED /
50 NOT_STARTED. Next **LB-08 / LINKAGE-GENERAL / OWNED_CONSTRUCTOR_DELEGATION**,
not another update-screen/backend rewrite or native harness. Native prerequisites
are deferred, never silently satisfied by this development transition.

## Previous delivery: LB-01 preserved update coordinator

[Implementation and limits](PRESERVED_UPDATE_COORDINATOR.md),
[artifact-bound evidence](evidence/preserved-update-coordinator-2026-09-19.json).
Typed predecessor/incoming selection, read-only old/new preflight, update-specific
single-use confirmation and actual update/inspect/resume/restore now share the
existing worker, reservation gate and shutdown lease. Separate schema-1 update
receipts and bounded inert history reuse the existing safe filesystem transport.
No saved report, stale review or add-only confirmation authorizes an update.

**94 focused regression cases passed, 37 new; zero failures/errors/skips.**
The real authored 1.0.1 conversion reproduces the pinned candidate hash. Tests
cover actual update/reopen/resume/restore, changed inputs/session, loaded IDs and
aliases, malformed receipts, cancellation of history, close during review/commit,
receipt write failures and explicit recovery after injected partial publication.
This is not a process-kill/power-loss experiment or a full root test campaign.
Earlier 18-case and 54-case focused runs also passed; counts are not summed as
distinct supported mods or compatible behaviors.

Serial all-module assemble, public/runtime JAR audits, engine boundary and installed
CLI smoke passed. Public SHA-256:
`990e029ab1a0182408878e151261f05e16582a527172da37ee0a1e206d05184e`
(1,658,480 bytes / 680 entries; 650 unchanged, 18 changed, 12 added, none removed).
Fresh contract checks passed 75/77, with two existing Windows symlink-privilege
skips. Structural verification confirmed 23 prompts/81 cuts and LB-04/UI-PROMOTION;
post-build artifact hash matches the ledger. This is not native acceptance.
Engine/six profiles, runtime/adapters/ABI, native screens/host/translations,
simple installers, journal schemas, included data and libraries remain intact.
Original fixtures and LICENSE hashes were rechecked unchanged.

This completes coordinator integration, **not the native update screen** or public
profile promotion. Local full data remains explicit and unredistributed. Native
client/server/UI/discovery/gameplay/multiplayer and final-artifact cacheless runtime:
**NOT_RUN**. No Minecraft opened, acquisition, license/billing change or publication.
Next: **LB-04 / UI-PROMOTION / PRESERVED_UPDATE_SCREEN**. Do not repeat this backend
or coordinator. Counts remain 15 ACCEPTED / 14 PARTIAL / two BLOCKED / 50 NOT_STARTED;
all native/promotion prerequisites remain, experimental/Beta unchanged.

## Previous delivery: LB-05 preserved managed updates

[Implementation and limits](PRESERVED_MANAGED_UPDATES.md),
[artifact-bound evidence](evidence/preserved-updates-2026-09-19.json).
The developer backend can now replace one owned, unloaded `logic-preserving-v1`
artifact installed by the preserved add-only installer. It independently
revalidates both admissions and their original/candidate/data pins, checks the
projected installed set, preserves an independent old-byte backup, and records
retirement/publication/restoration in a separate exact journal. Explicit resume
and restore are deterministic and idempotent; live successors block conflicting
predecessor recovery. Unknown/loaded files and mandatory inbound consumers refuse.

**All 50 new cases passed across serial runs.** The broader 145-case run had
142 passes and three child-process failures caused by a missing helper runtime
classpath entry. The correction and an initial compile-only correction are
documented, not hidden: all three failed cases and nine existing process cases
then passed (12/12). This is not a claim of one clean 145-case rerun. Actual Windows
delete-sharing denial and fresh-JVM halt/resume/restore were exercised; injected
write errors do not prove physical disk-full or power-loss safety.

Serial all-module assemble, public/runtime audits, engine boundary and installed
CLI smoke passed. Final public JAR SHA-256:
`5123677d79126509f338eeb6a00bb7c742ae0eb7ce4cc8fde56d5547d50d2440`
(1,639,625 bytes / 668 entries; 658 unchanged, two changed, eight added, none removed).
Fresh contract tests passed 75/77 with two existing Windows symlink-privilege
skips; the structural verifier confirmed 23 prompts/81 cuts and the new cursor.
The engine/six profiles, runtime/adapters/ABI, public/developer screens, coordinator,
simple installer, included data, libraries, license and billing are unchanged.

This is a **worker-only API**, not an update option already wired into the screen.
Update-of-update chains, simple predecessors and composed/cohort updates refuse.
Recovery still requires explicit local full data and the old/new pinned files;
the backup does not authorize guessing a lost managed identity. Client/server,
UI, discovery, behavior, multiplayer and final-artifact cacheless runtime are
**NOT_RUN**; no Minecraft process was started. Experimental/Beta remains.

Next: **LB-01 / PRODUCT-COMPOSED / PRESERVED_UPDATE_COORDINATOR**, without repeating
the backend or preparation flow, opening the game or promoting public Apply.
Native integration/acceptance and COMPOSE-01/DEPS-COHORT prerequisites remain.
Contract totals remain 15 ACCEPTED / 14 PARTIAL / two BLOCKED / 50 NOT_STARTED
(81 cuts), not a compatibility percentage.

## Previous delivery: LB-01 preserved preparation workflow

[Implementation and limits](PRESERVED_PREPARATION_WORKFLOW.md),
[artifact-bound evidence](evidence/preserved-preparation-2026-09-19.json).
The developer screen now starts from an original or an existing candidate:
explicit source/candidate/data hashes, real analysis, optional conversion by
the existing logic-preserving engine, independent inspection and private
admission preparation. Shared workers, real stage progress, controlled
cancellation, typed results/errors and up to five versioned reports are wired
through the product controller. No original classes are executed.

**196 regression cases passed, 23 new, zero failures/errors/skips.** The real
authored original converts to the exact known candidate hash. A separate fresh
review and explicit confirmation are required for installation; preparation
alone never installs, activates or grants approval. Tests cover changed pins,
corrupt candidate, unsafe paths/session, cancellation/close, retained recovery
material and report write failure without overwriting unknown files.

Serial assemble, public/runtime audits, engine consumer boundary and installed
CLI smoke passed. The final 52-case focused repeat passed without skips/errors;
fresh contract checks passed 75 of 77 with two existing Windows symlink-privilege
skips. The structural verifier confirmed 23 prompts/81 cuts and the next cursor.
The public JAR SHA-256 is
`6adff85b4a8d716188b49498c3112e2062bfc1374b13a519d2199adea5adc165`
(1,618,829 bytes / 660 entries; 627 unchanged, 20 changed, 13 added, none removed).
An initial compile error in the new report helper
was fixed; the failure log is retained. No rules, adapters, profiles, included
data, platform versions, public simple-content flow, license or billing changed.

Only explicit local full data supports this developer route; no hidden download
or Gradle-cache dependency was introduced. en_us/pt_br and native widget wiring
are implemented, **not visually/runtime validated**. Current-artifact client,
server, UI, discovery, behavior, multiplayer and cacheless game qualification:
**NOT_RUN**. Beta and all native/promotion prerequisites remain.

At that checkpoint, next: **LB-05 / INSTALL-PROMOTION / PRESERVED_MANAGED_UPDATES**. Do not repeat
the delivered preparation/coordinator/screens. Extend only managed, unloaded
preserved replacement with explicit approval and recoverable backup; never
overwrite loaded/unknown files or infer pre-discovery recovery. Contract totals
remain 15 ACCEPTED / 14 PARTIAL / two BLOCKED / 50 NOT_STARTED (81 cuts).
This implementation does not turn 18.5% accepted cuts into a compatibility metric.

## Previous delivery: LB-04 preserved developer screen

[Implementation and limits](PRESERVED_DEVELOPER_SCREEN.md),
[artifact-bound evidence](evidence/preserved-developer-screen-2026-09-19.json).
An explicitly enabled, client-only screen now connects the real preserved
coordinator: bounded admission/data/recovery selection, fresh review, one-use
confirmation, actual result/errors, searchable inert history and safe manual
close. Host snapshots are immutable and published on the client thread; workers
never query Minecraft. en_us/pt_br, native widgets, focus/cursors/resize and
scrolling are implemented, but **not visually/runtime validated**.

**103 focused product cases passed, 18 new; zero failures/errors/skips.** Serial
all-module assemble, final public/runtime audits, engine consumer boundary and
installed CLI smoke passed. Two initial cycles each had one false-positive in
the new ASM boundary test (String.join confused with Thread.join, then inert DTO
accessors confused with installer invocation); both logs/XML are retained. The
final check distinguishes exact owners/allowed read-only accessors without
permitting worker waits, filesystem I/O or direct installer calls from widgets.

After documentation/status changes, the fresh contract suite passed 75 of 77
cases with two existing Windows symlink-privilege skips, zero failures/errors.
The structural verifier confirmed 23 prompts/81 cuts and LB-01/PRODUCT-COMPOSED;
serial assemble/package audits/CLI passed again in 41s with the same JAR hash.

Public JAR SHA-256 for that historical delivery:
`b1d8e46e6270bcb8e0034c80bdf00420f4469e163c8c58b5792b163a13af878e`
(1,583,691 bytes / 647 entries): 625 old entries identical, 11 changed, 11 added,
none removed. Engine/six profiles, runtime/ABI, installers/journals, data/libraries
and existing conversion/history screens remain byte-identical. Changes are the
opt-in menu integration, new screen/form/host, translations, and preservation of
the SESSION_UNAVAILABLE diagnostic in the existing coordinator.

At that checkpoint the developer screen **started from an already prepared admission**;
the preparation workflow above now removes that manual handoff. Full local reviewed data
remains required only for this developer route; public simple-content still uses
its included finite data. Default public Convert/Apply behavior is unchanged.
Current client/server/UI/discovery/behavior/multiplayer and outside-project
cacheless runtime qualification: **NOT_RUN**. No games, acquisition, publication,
license changes or billing. UI-PROMOTION is PARTIAL, not ACCEPTED.

Contract totals: 23 prompts / 81 cuts, 15 ACCEPTED, 14 PARTIAL, two BLOCKED and
50 NOT_STARTED. 15/81 = 18.5% of unweighted accepted cuts, **not** total engineering
completion, compatible mods or a model's progress rate. There is no reliable
fixed percentage per prompt. Its next slice was **PRESERVED_PREPARATION_WORKFLOW**,
now delivered above without marking native gates passed.

## Previous delivery: LB-01 preserved installation coordinator

[Implementation and limits](PRESERVED_INSTALL_COORDINATOR.md),
[artifact-bound evidence](evidence/preserved-coordinator-2026-09-13.json): explicit
developer factory, fresh typed review and single-use current-session confirmation,
existing installer/coordinator/shared shutdown lease, real per-operation results
and separate versioned receipts with bounded inert history. Original/result/data,
dependency/ABI and loaded-file checks remain in the unchanged installer; no fake
simple-content job or historical approval. Ordinary public UI remains unchanged.

**83 focused product cases passed, 27 new; zero failures/errors/skips.** Serial
all-module assemble, public/runtime archive audits, engine boundary and installed
CLI smoke passed (3m31s). The first run exposed a refusal-receipt dependency on a
missing admission path, now fixed. A second cycle hit six existing 15-second test
deadlines; a worker trace showed class-loader file reads. The identical full test
selection subsequently passed without changing thresholds or code. Both failed
cycles remain recorded; no hard OS latency guarantee or complete OS diagnosis.

After the contract update, a separate clean contract-test rerun passed 75 cases
with two Windows symlink-privilege skips (77 total, zero failures/errors). Serial
assemble/audits/CLI and the structural verifier passed again in 1m22s, confirming
23 prompts/81 cuts, cursor LB-04/UI-PROMOTION and the unchanged final JAR hash.

Previous delivery JAR: `e5a0d7e70bb2c3915d5142e6d8653624d91ea50fc8a6a6c7cb45b0735a5b640f`
(1,554,835 bytes / 636 entries): 613 prior entries identical, nine controller
classes recompiled and 14 new types. Engine/six profiles, all runtime/ABI bytes,
installers/journals, old history readers, native UI, data and libraries unchanged.
No original/converted class was executed by these tests; installed platform
metadata was synthetic. Native client/server/UI/activation/behavior/multiplayer
and outside-project JVM validation of this new hash: **NOT_RUN**. Beta; no public
profile promotion, game launch, acquisition, publication or license/billing change.

Next: **LB-04 / UI-PROMOTION / PRESERVED_DEVELOPER_SCREEN**. Implement the developer
screen/host consuming this API, with real thread-safe session capture and owned
world/navigation/closing guards. Do not repeat the coordinator or installer, wire
ordinary public Apply, open Minecraft or infer native/package acceptance. The
host must enforce the documented world gate; this JDK-only API does not enforce
all native/QuickPlay/OS paths by itself. Requires and native gates stay pending.

## Previous delivery: LB-05 actual preserved add-only installation

[Implementation and limits](PRESERVED_ADD_ONLY_INSTALL.md),
[artifact-bound evidence](evidence/preserved-install-2026-09-13.json): explicit
developer approval now installs one independently inspected logic-preserving-v1
artifact for the next launch. Fresh original/result/data/ABI inspection, current
installed-set/dependency/loaded-file guards, separate journal and owned resume/restore.
Restore intent is durable before unlink; invalid RESUME cannot reinstall it or
clean retained evidence. No public profile, composition/cohort or update promotion.

**112 focused product cases passed, 41 new; zero failures/errors/skips.** Separate
contract suite: 75 passed/two Windows symlink-privilege skips. Serial all-module
assemble/public/runtime/engine audits and installed CLI smoke passed in 6m40s.
These are tests, not supported-mod counts or game behavior evidence.

The actual public JAR passed recovery in separate Java17 helpers outside the
project: halt after publication (81), repeated resume; halt after removal (82),
invalid resume refusing without changing files, repeated restore. Each interrupted
operation retained 14 guards, cleaned by its correct recovery; repeated successful
calls changed no path/hash tree. Original/result intact, configured caches empty.
An initial overly early guard cleanup was found by the process oracle and fixed;
both the failed attempt and corrected evidence are retained. No user process stopped.

Previous JAR: `8dbd54f4e6139a223e1e8d6665e2818baf3c0877189b0ec9ea607034857f3a35`
(1,525,546 bytes / 622 entries): 611 prior entries identical, two shared admission
classes changed, nine installer/approval/journal/result classes added. Engine, six
profiles, runtime/ABI, UI, V1/V2 installers/journals, data and libraries intact.
Native client/server/UI/activation/behavior/multiplayer on this hash: **NOT_RUN**.
No Forge/game was launched; no licensing, acquisition or publication change. Beta.

Next: **LB-01 / PRODUCT-COMPOSED / PRESERVED_INSTALL_COORDINATOR**. Reuse the existing
bounded coordinator for developer review/jobs, approval/session/shutdown exclusion,
receipts and history. Do not duplicate the installer, repeat binding/readback or
open games. Public Apply stays simple-content-v1. LB-05/INSTALL-PROMOTION remains
PARTIAL; composition/cohort/preserved updates and native promotion gates remain.

## Previous delivery: LB-05 persistent admission binding and recovery

[Implementation and limits](PRESERVED_ADMISSION_JOURNAL.md),
[versioned evidence](evidence/journal-admission-2026-09-13.json): worker API prepares
an independent private payload, persists exact original/result/data pins and
profile/ABI in a separate schema, and revalidates real bytes on every inspect/resume.
Missing observations can be completed explicitly; unknown/partial/tampered files
are retained and refused. A saved PASS is never installation authority. V1/V2
remain unchanged and do not migrate or accept these records as installations.

**71 focused checks passed, zero failures/errors/skips; 28 new cases.** A separate
public-JAR Java17 helper halted with exit 81 after binding. Two fresh JVMs resumed
successfully; 14 retained guard names were cleaned, the second resume changed no
files, mods stayed empty and configured homes/Gradle caches stayed empty. This is
process-interruption/readback evidence, not Minecraft discovery or gameplay.
The probe exposed a JDK17 Windows long-path FileStore comparison refusal; the new
API now refuses excessive derived guard paths before any write, with a shorter-path
remediation. Existing V1/V2 filesystem checks were not weakened or changed.

Previous public JAR: `4e41fd7e69eac085e9932690d7eee8e7aa68335b754ac1fbaa94979610abd545`
(1,504,202 bytes / 613 entries). All 605 prior payloads remain identical; eight
new admission classes, none replaced/removed. Engine, six profiles, runtime/ABI,
UI, old installers/journals, included data and libraries unchanged. Build/package
audits pass; LICENSE unchanged. No Minecraft, server, UI, acquisition or publication.
Native behavior/activation/multiplayer on this hash: **NOT_RUN**. Status remains Beta.
Separate contract validation: 75 passed/two Windows symlink-privilege skips;
23 prompts/81 cuts structurally verified. These are not native or compatibility counts.

Historical handoff (now implemented above): **LB-05 / INSTALL-PROMOTION / PRESERVED_ADD_ONLY_INSTALL** — an explicit
developer-only add-only route for the exact profile, with approval provenance,
current installed-set/dependency/loaded-file checks, versioned commit ownership
and recovery. Do not repeat admission/binding or enable public/composed/cohort Apply.
COMPOSE-01/DEPS-COHORT and native promotion gates remain required; LB-05 PARTIAL.

## Previous delivery: LB-05 independent preserved-artifact readback

[Implementation and limits](PRESERVED_ARTIFACT_ADMISSION.md),
[versioned evidence](evidence/preserved-admission-2026-09-13.json): typed engine API
and developer `inspect-preserved` CLI independently inspect an existing original/
candidate pair for the exact logic-preserving-v1 contract. No converter publisher,
mod execution, report-based authority or new installation path is used. Actual
class projection, source/target offline bytecode/linkage, resources, dependencies,
adapter binding and selected hashes must pass. Unknown contracts remain refused.

**71 focused checks passed, zero failures/errors/skips; 22 new cases.** The actual
six-class/25-method fixture and its existing converted JAR remain unchanged.
Matching-hash mutations are refused; 42 simple installer regressions passed and
the preserved artifact is still refused by the public installer. All modules
assemble; packaging/runtime/engine/installed-CLI audits pass. Separate Java 17
public-JAR engine and CLI processes, outside the repository with empty configured
Gradle caches, produced identical reports using the explicit reviewed local bundle.
This is not a native Forge/classloader or behavior test. The bundle is not embedded.
Separate contract checks: 75 passed, two Windows symlink-privilege skips;
23 prompts/81 cuts structurally verified, not newly accepted.

Current public JAR: `903a8803f1bc2fa379648beab10c8635b527bde25f0e567e41a20b7db5cfa7be`
(1,487,147 bytes / 605 entries). Against the prior saved-report artifact: 593
payloads unchanged, two engine-service payloads changed, ten inspector/API classes
added, none removed. Six profiles, runtime/adapters, UI, data, installer/journal
and libraries remain byte-identical. LICENSE/billing unchanged; no acquisition
or publication. Current native client/server/behavior/multiplayer QA: **NOT_RUN**.

At that checkpoint, next was **LB-05 / INSTALL-PROMOTION / JOURNAL_ADMISSION_BINDING** — bind exact
profile/original/candidate/reviewed-data pins to versioned installation and recovery,
with fresh readback rather than editable PASS authority. Keep V1/V2 simple behavior;
do not repeat this inspector or enable composed/cohort Apply by inference.
COMPOSE-01/DEPS-COHORT and native acceptance remain required; LB-05 stays PARTIAL.

## Previous delivery: LB-04 saved report history

[Implementation and limits](SAVED_REPORTS.md),
[versioned evidence](evidence/saved-reports-2026-09-12.json): new jobs save a
schema-1 index linking CompatibilityReport, ConversionReport and TransformationLog
by explicit scoped paths and SHA-256. History can reopen these reports as bounded,
paged literal text on the existing worker. Cancel/close refuses late publication.
No historical Apply authority, JAR execution, OS path opening or activation claim.
Old jobs without an index retain their receipt view; associations are not guessed.

**100 focused checks passed, zero failures/errors, one Windows symlink-privilege
skip** (101 cases; 22 new cases, of which 21 passed). The exact compiled 1.16.5
fixture still converts to `541cc64b44b97d0e4e79a193dd2ce3a86a5feba6e15714d0ee24ad63751492bb`;
original bytes stay intact and all three reports reopen without writes or Apply.
All modules assemble; product/runtime/engine/installed-CLI audits pass. These are
static/controller checks, not a click-through of the real Minecraft UI.
Separate contract validation: 75 passed, two symlink-privilege skips; 23 prompts
and 81 cuts structurally verified. No native acceptance was advanced.

Public JAR: `b89a4a7795309f2ff70a05348a200bcafb1afe048c3c332910bf727663b4b5d9`
(1,466,678 bytes / 595 entries). Compared to the archived baseline, 574 payloads
are unchanged, 12 product/UI payloads changed, nine were added and none removed.
Engine, runtime/adapters, six profiles, data, installer and bundled libraries
remain byte-identical. LICENSE remains unchanged. Native client/server,
keyboard/resize/rendering and current-artifact end-to-end QA remain **NOT_RUN**.

At that checkpoint, next was finite independent admission in **LB-05 / INSTALL-PROMOTION**, not blanket
enablement of preserved-code/composed Apply. COMPOSE-01 and DEPS-COHORT remain
acceptance prerequisites. Do not repeat completed report infrastructure, ask for
licensing decisions now, launch games or advance native gates from build success.

## Previous delivery: LB-03 technical source rebuild and relinking

[Implementation and limits](SRGUTILS_RELINKING.md),
[versioned evidence](evidence/relink-2026-09-12.json): developer-only
`prepare`, `build` and `replace` operations now export exact SRG Utils 0.6.2
sources, rebuild with JDK17/release8 and create a separate private copy with
the replacement library/source/notice. All 583 unrelated payloads are unchanged.
No new conversion rule, profile, game class, adapter or public artifact changed.

**20 focused tests passed, zero failures/errors/skips.** All modules assemble;
product/runtime/engine/installed-CLI audits pass. The 41 library classes pass
the finite declaration/ASM gate. A controlled source modification is observed
in a separate library loader; the actual public-JAR engine produces identical
simple-fixture bytes/reports and refuses the corrupt JAR. Mod classes are never
executed. Separate installed CLI runs, outside the repository with explicit
inputs and empty homes/Gradle caches, reproduce the library/source hashes across
JVMs. An initial checked-exception compile failure and cross-process report
ordering defect were corrected and revalidated; see the ledger.

Contract closeout: 75 passed / two symlink-privilege skips; 23 prompts and
80 cuts structurally verified, without promoting acceptance. All 20 new relink
cases remain separate from these 77 existing contract cases and from native QA.

At that checkpoint the public JAR remained `76189bcfb81012d1bce3f63c2b43a819a1ef1473b357d35df295e570c3f39c22`
(1,441,049 bytes / 586 entries), not the private relink candidate. LICENSE is
unchanged. This is an independent build recipe, **not** upstream release
reproduction or legal clearance. Native Forge JiJ/JPMS selection, client/server,
game behavior and multiplayer of the rebuilt dependency remain **NOT_RUN**.

PACKAGE-RIGHTS stays BLOCKED and LB-03 stays PARTIAL. The subsequent user decision
postponed monetization/terms and authorized continuing unrelated development.
Do not choose/apply terms automatically or treat free pricing as license clearance.
Corresponding-source/release-material review and native validation remain separate
release gates. Do not repeat this technical slice or ask again for the postponed
choice as the default next task. No game, acquisition or publication was performed.

## Previous delivery: LB-02 data review and aggregate engine controls

[Delivery and limits](PROMPT_02_HEADLESS.md): shared typed data inspection,
CLI `inspect-data`, client Data/Dados action, scope/provenance/counts and
reviewed-manifest binding. Changed reviewed data blocks with
`MAPPING_DATA_CHANGED`; an explicit invalid bundle never falls back.
The individual composition preview still requires locally prepared full data
after auditing namespace ownership/hierarchy closure. No generic mapping pack
was embedded, and the included simple/cohort data is unchanged.

Service instances in one classloader now share one active / 33 admitted jobs,
with typed waiting, safe permit release, memory-pressure refusal and separate
work/shared-heap metrics. This is **cooperative protection, not hard isolation**.
No process worker, retained-size attribution or native/RSS guarantee was added.

Final focused validation: **101 passed, zero failures/errors, one symlink
privilege skip** (102 cases, 19 new). Six existing fixture profiles passed
installed CLI/service equivalence without developer-cache/network access;
individual composition and exact-cohort private previews passed too. Original
hashes and owned-scratch cleanup passed. A 64 MiB CLI correctly refused full
data with `MEMORY_PRESSURE`. Initial overstrict memory admission and a wrong
lifecycle bundle selection were corrected and revalidated, without raising
limits or discarding dependencies. Full/native QA remains deferred, not passed.

All modules assemble and product/runtime/engine/installed-CLI audits pass.
Closeout added 75 passed contract checks / two native-symlink privilege skips.
Across the final focused and contract rounds: 176 distinct passed / three
skipped, zero failures/errors. Up-to-date tests are not counted twice.
Current JAR: `76189bcfb81012d1bce3f63c2b43a819a1ef1473b357d35df295e570c3f39c22`
(1,441,049 bytes / 586 entries). Versus the previous artifact: 550 entries
unchanged, 25 changed, 11 added, none removed. Runtime/adapters/profiles/data,
installer and libraries remain byte-identical. Engine/controller/screen changed.
[Exact evidence, commands, hashes and sampled memory](evidence/prompt02-headless-2026-09-12.json).

Minecraft/client/server/UI/behavior/multiplayer validation of this hash:
**NOT_RUN**. No game was opened. LB-02 remains PARTIAL for COMPOSE-01 acceptance,
native coexistence and deeper memory qualification. At that checkpoint development moved to
**prompt 3: corresponding-source/relinking technical preparation** in separate
copies. Do not change LICENSE, publish, declare rights clearance or repeat the
same LB-02 matrix as the sole next task. The rights-holder decision stays blocked.

## Historical data delivery: LB-02 / FINITE_COHORT_DATA

[Included cohort data](FINITE_COHORT_DATA.md) removes the separate bundle from
the exact authored library/consumer preview. UI blank field and the two cohort
CLI commands use 537 bytes of hashed, source-verified Forge annotation declarations.
An invalid explicit bundle never falls back. Full versus finite candidate hashes,
graphs, checks and logs match; data scope/provenance remain explicitly different.
No public cohort profile, installable JAR, receipt or Apply permission was added.
The single composition preview and five advanced profiles still require full data.

**167 distinct focused JUnit cases passed, zero final failures/errors/skips**:
130 converter + 35 product, then two heavy existing real-data/simple-conversion
regressions in a separate serial run. There are 15 new tests. Initial negative
ZIP construction and an exhausted full-oracle test worker were corrected and
revalidated without raising product limits. The simple fixture output remains
byte-identical between its full and built-in paths. Extensive QA is deferred.

A separate Java17 process outside the repository, with no bundle, empty homes
and a 128 MiB heap cap, produced identical CLI/service reports. Its test-only
guard observed zero unexpected file/network/process/exit attempts. Originals
were preserved and scratch directories stayed empty. This is not an OS sandbox,
an offline Minecraft test or a production hard-heap guarantee.

All modules assemble; product/runtime/API/engine/installed-CLI audits passed.
Historical JAR SHA-256: `f494cdf56327777f7421fbb5e2a11dd18c8fc8e95a5b84d097a11627e85a1df7`
(1,420,513 bytes / 575 entries). 540 entries unchanged, 29 changed, six added,
none removed versus the archived previous JAR. Runtime/adapters/catalog, simple
data, installer and libraries are byte-identical; engine scope/gates changed.
[Commands, source/data/original hashes and proof](evidence/finite-cohort-data-2026-09-12.json).

Client/server/screens/behavior/multiplayer/current-JAR native qualification:
**NOT_RUN**. No game was opened. Rights and public-promotion gates stay pending.
Next **COMPOSITION_DATA_SCOPE** audits the existing individual logic+recipes
preview's real data needs, then implements a separate finite included route only
if permitted. The annotation-only contract must not be reused as a general API.
The cohort-data implementation is finished; DATA-EXPAND is still PARTIAL.

## Historical product delivery: LB-01 / COHORT_TARGET_PREVIEW

[Cohort preview](PRODUCT_COHORT_PREVIEW.md) connects explicitly checked input
rows to the existing typed `planCohort` service. Review all selected paths/hashes
before Inspect, including checked rows hidden by search. The client screen adds
real worker progress/cancel, bounded source/dependency/target diagnostics,
en_us/pt_br and report access. No automatic request on opening or resizing.

The preview report is not a job/receipt/Apply selection. Original hashes and
ordinary jobs are preserved; the exact private target pair matches the shared
service. Missing dependencies, extra unreviewed artifacts and unknown contracts
remain blocked. Full local data is still explicit for this advanced action.

**34 focused tests passed, no failures/errors/skips**, including 15 new tests.
Initial command supplied a wrong old fixture filename: three NoSuchFileException
failures were corrected by using the observed path, then revalidated. All modules
assembled; public JAR/runtime/API/engine/installed-CLI audits passed. Existing
dev-only validation probes compiled, not executed (one deprecation warning).

Public JAR SHA-256 at that checkpoint (superseded above):
`b069df666292a1d833c5c8f18f455608cbeb5961e67570032e575a63d2fc029d`
(1,403,347 bytes / 569 entries). Compared with the archived previous JAR:
545 entries unchanged, 12 UI/controller/localization entries changed, 12 display
types added, none removed. Engine, runtime/adapters, six profiles, data, installer
and libraries are byte-identical. No fixture/CLI/harness/private bundle packaged.
[Exact commands, hashes and evidence](evidence/product-cohort-preview-2026-09-12.json).

Client/server, behavior, multiplayer, real GUI focus/scale/resize and packaged
native qualification: **NOT_RUN**. No Minecraft was opened. Full regression
is deferred; build/static passes are not a completed port or native approval.

**Next development: LB-02 / DATA-EXPAND / FINITE_PREVIEW_DATA.** Minimize/version
the source/target data required by finite previews, audit provenance and rights,
then implement only a permitted included or explicit-data route. Never embed
or relabel the full local-only bundle. Acceptance dependencies remain required;
the implementation cursor advances without promoting public cohort conversion.

## Historical engine delivery: LB-19 / FINITE_TARGET_PAIR

[Finite target pair](DEPENDENCY_COHORT_TARGET.md) now **serializes and reopens two
private target JAR candidates**, preserving all three authored class byte arrays
and the required library dependency. Separate per-JAR ASM/linkage/access and
CLIENT/SERVER dependency graphs pass. Six resource changes have exact recipes
and concrete OFFLINE adapters. No source or target class is executed.

The typed `planCohort` service and `plan-cohort` CLI produce identical proofs;
neither returns output paths, installer authority or a public conversion profile.
A failed second candidate or cancellation approves no partial pair. Both original
hashes remain intact. 136 focused checks passed, no failures/errors/skips in the
final results (25 added here); the initial run skipped one old environment test,
which was explicitly enabled and passed, including the real simple-content fixture.
All modules assembled and product/runtime/API/engine/installed-CLI audits passed.

Historical public JAR SHA-256:
`27f09773ec6b2a275269adb3c788ec99504659b9638d6283c4ffdf756822d6aa`
(1,373,067 bytes / 557 entries). Comparison: 545 unchanged, four changed (service
facade/helpers and catalog) and eight added. Existing profiles, runtime behavior
implementations, data, UI, installer and libraries are byte-identical. New metadata
operations are additive; runtime ABI/version did not change. No fixture/harness,
CLI or private mapping data entered the public package.

The installed CLI also returned matching candidate hashes with empty user.home
and GRADLE_USER_HOME plus the explicit prepared bundle. This is not host-level
filesystem isolation or a native offline Forge test. [Commands and evidence](evidence/deps-cohort-target-2026-09-12.json).
Minecraft/client/server, screen automation, source/target behavior and multiplayer:
**NOT_RUN**. DEPS-COHORT remains PARTIAL; not approved for publication.

**Historical next, now implemented: LB-01 / PRODUCT-COMPOSED / COHORT_TARGET_PREVIEW.** Connect
multiple explicit selections to the typed private pair preview, progress,
cancellation and per-artifact diagnostics. No duplicate rules, public Convert/Apply
promotion or another launch-harness loop. Native acceptance remains required.

## Historical engine delivery: LB-19 / source inspection (earlier 2026-09-12)

[Source cohort inspection](DEPENDENCY_COHORT_SOURCE.md) is implemented through
the shared typed service and `inspect-cohort` CLI. Explicit hashes, per-JAR class
and package ownership, source API inventories, dependencies on CLIENT/SERVER and
offline linkage/access are checked. Separate JARs are never merged into one
owned-class set. Undeclared libraries, including unused constant-pool references,
cannot borrow access merely by being selected. Unknown contracts block inspection.

The authored Forge 36.2.42 library/consumer compile into separate original JARs.
The real source pair passed static linkage and exact CLI/service JSON parity;
no original class was loaded/executed. 98 focused checks passed (37 new, no
failures/errors/skips). Final commands, exact hashes and
package audits are recorded in the [ledger](evidence/deps-cohort-source-2026-09-12.json).
No target pair, public conversion profile, adapter or Apply support was added.
Minecraft, dedicated server, screen automation and native comparison: NOT_RUN.

All modules assembled; package/runtime/engine boundaries, installed CLI help and
contract audits passed. Historical public JAR SHA-256:
`362b6757cde2ec0a935ddbd3d0112b2060719f9881ab8a993f3f3e907dd83d45`
(1,349,997 bytes / 549 entries). Only the engine facade and ten new inspection
types changed/appeared. Runtime, adapters, existing profiles, bundled data, UI and
libraries remain byte-identical to the archived previous artifact. No fixtures,
CLI or harnesses were added to the public package. This is not native qualification.

**Next development at that checkpoint: FINITE_TARGET_PAIR within LB-19 / DEPS-COHORT.** Implement
the exact target recipes and validate both private candidates as one proposed
set without stripping mandatory dependencies. Native acceptance remains deferred;
do not loop back into launching/QA harness work. The simple-content profile,
runtime identity, mappings and existing installer admission remain unchanged.

## Historical product delivery: LB-01 / PRODUCT-COMPOSED (PARTIAL, earlier 2026-09-12)

[Advanced composition inspection](PRODUCT_COMPOSITION_INSPECTION.md) now connects
the native Preview button to the existing engine's finite logic+recipes proof.
Typed states/operation summaries, actual worker progress, cancellation, blocking
gates and a versioned JSON report are implemented. No JAR, installer authority,
new public profile or mapping is produced. The explicit full local bundle remains
required for this advanced path; ordinary simple conversion still uses included data.

23 focused checks passed, zero failures/skips, including the actual reobfuscated
fixture and equality with the shared service. A first negative-test setup failed
on CREATE_NEW and was corrected without altering the original. See
[commands, hashes and package outcomes](evidence/product-composition-2026-09-12.json).
All modules assembled and product/runtime archive audits passed. Public JAR:
`bbeff878e50c69d42418f4ed2341d5a161330aef450cc73dc2801078207f48b1`
(1,322,909 bytes / 539 entries). Entry-hash comparison against the archived
previous artifact found only product controller/UI/translations changed or added;
engine/runtime/adapters/data/libraries are unchanged. The original fixture and
explicit bundle manifest were rehashed unchanged after the checks.
Native UI/keyboard/resize, dedicated-server and behavior validation are NOT_RUN.
Older JAR evidence is preserved, not rebound to these changes.

Next development: **LB-19 / DEPS-COHORT**, bounded authored library/consumer
ownership, dependency API and offline linkage. Public composition/admission and
COMPOSE-01 acceptance remain PARTIAL; their static implementation can support
headless development under the recorded policy, not public promotion.

## Historical product delivery: LB-04 / UI-HISTORY-READ (PARTIAL, 2026-09-10)

[Read-only native history](UI_HISTORY.md) is implemented in the public product:
History button, bounded asynchronous refresh, local search, receipt pagination,
reader diagnostics, safe literal copy and en_us/pt_br. It reuses the existing
controller reader. Receipts cannot approve Apply or shutdown, open stale paths,
or prove activation. No new conversion category, mapping or adapter is enabled.

Focused projection/boundary/controller checks passed; see the exact commands,
public hash and evidence dimensions in [the ledger](evidence/ui-history-2026-09-10.json).
Current JAR SHA-256: `b3c6c1f98b2af0d91141b9b48f21ec3fd7e1baba8af878a083ce0dc20c5a6330`
(1,303,286 bytes / 532 entries). Eleven focused product cases passed; contract
verification had 75 passed and 2 existing native-symlink capability skips. All
modules assembled and public/runtime package audits passed. Compared with the
archived previous JAR, 523 entries are unchanged, three changed (screen/languages),
six added (history presentation/screens), none removed. Engine/runtime bytes and
included data/libraries are unchanged; this is not broader mod compatibility.
No Minecraft client/server or screen automation was run. Native keyboard/resize,
visual behavior and dedicated-server qualification of the changed JAR remain
NOT_RUN, not inherited from older artifacts. Extensive corpus QA is deferred.

Contract 2026.09.10.1 separates the development cursor from pending acceptance:
next **LB-01 / PRODUCT-COMPOSED**, starting with a typed static preview/admission
boundary using existing composition code. COMPOSE-01 still lacks native comparison;
that does not authorize broader public conversion or installation. Do not resume
expanding its harnesses automatically. The previous artifact is preserved locally;
older evidence/staging must not be silently rebound to the new product hash.

## Historical headless checkpoint: LB-08 / COMPOSE-01 (PARTIAL, 2026-09-08)

The [restricted configuration proposal](COMPOSITION_RESTRICTED_SETTINGS.md)
is now implemented, bound to a rechecked unarmed stage, with immutable authored
files/hashes and exact recipes. It preserves every explicit baseline property
or refuses the plan. Active properties are exactly 50 SOURCE / 56 TARGET;
server templates use `defaultconfigs` without creating a world. No stage setting,
receipt, native argument, original or runtime API is changed.

Full serial build passed in 33m17s: **1,620 corpus cases / 1,613 passed / seven
existing Windows symlink-privilege skips**, zero failures/errors. All **456 new
cases passed without skips** (439 finite-settings + 17 bound-plan cases).
Other module tests were UP-TO-DATE, not rerun. The earlier focused run passed
682 cases without skips; the full run additionally covered four explicit
baseline-conflict/preservation cases. XMLs/hashes/logs are in the
[current ledger](evidence/compose-restricted-settings-2026-09-08.json).

The opt-in command passed against both actual unarmed stages: SOURCE111 /
TARGET223 immutable inputs and 10/11 preserved explicit properties; receipts
and original stage properties remain unchanged, with no world/config/template/
marker created. Result is only RESTRICTED_CONFIGURATION_PLANNED_ONLY. Public
JAR remains byte-identical (SHA-256 `4e74a86169bb05d8ce299c000d4945c1bcf3746f53c8b3684ee37fe67a9b1b84`),
1,280,333 bytes/526 entries, with no corpus/helper. The five observation-index
pins remain unchanged and `runs: null`. No Minecraft or UI automation was started.
Native execution, consumption, save and behavior remain NOT_RUN/unverified.

The earlier "no-network configuration" next-step wording was too broad: exact
TARGET authlib can fetch service keys at startup independently of Forge's
version checker. Restricted Forge properties are not process-wide concurrency
or OS egress isolation. Next **same COMPOSE-01**: a bound materialization/recovery
and post-save content contract, then remaining lookup/containment checks;
no arming, native authorization, public composition or LINKAGE-GENERAL promotion.

## Previous headless checkpoint: native default configuration review

The [finite native configuration review](COMPOSITION_NATIVE_CONFIG_REVIEW.md)
now checks exact generated defaults, refuses unknown/non-default TOML and rejects
TARGET's unregistered empty Common config. It preserves bytes, checks hashes
between complete layout reviews and reports native version-check/LAN/thread
concerns without granting launch authority. The necessary-output inventory has
14 SOURCE / 13 TARGET paths including staged properties; missing files remain
MISSING, while all present means only PRESENT_ONLY, never successful save/exit.
World/log/observation content remains unchecked. No Minecraft was started.

Full build passed in 29m20s: 1,164 corpus cases / 1,157 passed / seven existing
Windows symbolic-link privilege skips, zero failures/errors. All 230 new cases
passed without skips, after one corrected initial compile error in the report
digest. Other module tests were UP-TO-DATE, not rerun. Both actual unarmed stages
passed read-only review, reporting 13 SOURCE / 12 TARGET generated paths still
missing, as expected without execution. No native output or arming was invented.
The public JAR is byte-identical (SHA-256 `4e74a86169bb05d8ce299c000d4945c1bcf3746f53c8b3684ee37fe67a9b1b84`);
the five observation-index inputs rehash unchanged, with `runs: null`.
Hashes/XMLs/logs and limitations: [current ledger](evidence/compose-config-review-2026-09-08.json).
Six profiles and public runtime ABI remain unchanged. At that checkpoint the next COMPOSE-01 work was a
finite restricted configuration/native-property launch contract with inert
fixtures, plus required content/save checks; no arming or native authority.
SOURCE lookups/Java 17 and original/target comparison remain pending separately.

## Previous headless checkpoint: native output layout and launcher audit

The [finite native output-layout policy](COMPOSITION_NATIVE_OUTPUT_POLICY.md)
now separates immutable originals/staged pins, semantically fixed properties,
empty no-player lists and explicitly recognized bounded opaque outputs. Two
read-only inventories compare files and directories; unknown paths, aliases,
changed pins or property values block the whole result. No marker is created,
no command is executed and no output is adopted. `BOUNDED_LAYOUT_REVIEW_ONLY`
is not validation of NBT/config/log contents or native startup/save/exit.

The [exact launcher lookup audit](COMPOSITION_NATIVE_LOOKUP_AUDIT.md) rehashed
96 selected platform files in templates and stages without drift. SOURCE has
two conditional absent-sibling URL lookups, not proven missing requirements or
startup failures. The inspected TARGET module route does not expand its stale
Nashorn index. No platform JAR or invocation was patched.

Full build/package audit passed: fresh corpus **934 cases / 927 passed / seven
existing symbolic-link privilege skips**, zero failures/errors. All **241 new
cases passed without skips**, including real hard links and oversized world-file
refusal. Converter/product/runtime/workflow test tasks were UP-TO-DATE, not rerun.
SOURCE/TARGET actual unarmed stages passed read-only layout review: **111/223
immutable inputs, 71/147 files and 85/176 directories**. Independent receipt-file
rechecks found no drift, marker, world or native logs. Empty unchecked-content
lists in these unused stages are not content validation. The native observation
index remains NOT_RUN / `runs: null` (five files / 1,402,016 bytes checked).
The public JAR remains byte-identical, 1,280,333 bytes / 526 entries, no corpus/helper:
`4e74a86169bb05d8ce299c000d4945c1bcf3746f53c8b3684ee37fe67a9b1b84`.
The [current ledger](evidence/compose-output-policy-2026-09-08.json) records XMLs,
hashes, source audits and limitations. Six profiles and runtime ABI are unchanged.

Next, same **LB-08 / COMPOSE-01**: finite configuration/content semantics and
the required successful-output set, with inert fixtures. Native arming/session,
SOURCE late-lookup decision/Java 17 qualification and original/target comparison
are separate pending gates. No game authorization, COMPOSE-01 acceptance,
LINKAGE-GENERAL or installation/UI promotion is granted.

## Previous headless checkpoint: closed inert sessions and stage admission

The [closed inert session and read-only stage verifier](COMPOSITION_INERT_SESSIONS.md)
now link a materialized receipt/current reviewed selectors to an exact test-helper
command, bounded actual exit/EOF collection and create-new post-exit evidence.
Recovery classifies unsigned complete/incomplete records without repairing,
replaying, installing or granting native authority. Original/native argv is never
used by the helper session; no arming marker is created. The helper is test-only
and is not distributed. Six profiles and the public runtime ABI remain unchanged.

The first integration failures exposed HotSpot's default `hsperfdata` directory;
the helper now disables only that monitoring facility, without accepting extra
work-tree paths or changing native arguments. Cancellation across component
boundaries and late post-publication integrity/interrupt checks are covered.
A test-only 10-second Future wait was too short for the complete filesystem
audits/publication (19 seconds observed in the failing case); its separate
allowance is now 60 seconds. Collector execution limits and no-queue admission
were not relaxed. The focused correction and full regression passed. Both actual
existing stages passed the opt-in read-only verifier: SOURCE 111 immutable inputs,
TARGET 223; independent post-build hash audits found no drift, marker, world or
native log. This is STAGED_INPUTS_VERIFIED_ONLY, not native execution.

Full build/package audit passed: fresh corpus **693 cases / 686 passed / seven
skipped**, zero failures/errors. The delta is **141 cases / 139 passed / two
symlink-privilege skips**; five skips predate this continuation. Converter tests
ran in the first full round: **831 cases / 748 passed / 83 skipped** (82 opt-in
environment gates, one symlink privilege), zero failures/errors; they were
UP-TO-DATE in the final regression. Product/runtime/workflow tests were also
UP-TO-DATE, not rerun. Corrected failures, XMLs and final hashes are retained in
the [current ledger](evidence/compose-sessions-2026-09-08.json).
The public JAR remains byte-identical, 1,280,333 bytes / 526 entries, without QA:
`4e74a86169bb05d8ce299c000d4945c1bcf3746f53c8b3684ee37fe67a9b1b84`.
No Minecraft client/server, screen interaction, new mod acquisition, installation
or publication occurred. The native index rechecked five files / 1,402,016 bytes
and remains NOT_RUN / `runs: null`; helper results never populate it.

At that checkpoint, the next step was to implement/test the finite native mutable-output
policy and audit late launcher lookups against the exact platform artifacts,
still without a game. Native original/target comparison and source Java 17
qualification remain required and unauthorized. Do not accept COMPOSE-01,
unlock LINKAGE-GENERAL or promote installation/UI from inert-helper evidence.

## Previous headless checkpoint: unarmed staging and process mechanics

The [unarmed stager and internal process collector](COMPOSITION_STAGING_COLLECTION.md)
are implemented in the dev-only corpus module. Staging requires a reviewed plan
digest, independent create-new copies, journal and closed-tree/hash audits, and
withholds the probe authorization marker. Failures retain private, unapproved
evidence, including a possible receipt file after a late failure; no original
or unknown file is replaced. The opt-in staging command never starts Java.

The package-private collector runs only inert test helpers in this continuation.
It bounds heap/streams/concurrency, applies a minimum environment and records
actual exit/EOF, cancellation, timeout and post-exit input drift. Late cancellation
is checked again at completion. Successful capture is PROCESS_COLLECTED_ONLY:
it does not prove association with an approved native plan or game behavior.

The full build/package audit passed: fresh corpus **547 passed / five skipped**,
zero failures/errors. The delta is **113 new passes / one symlink-privilege skip**;
the other four skips predate this change. Converter/product/runtime/workflow
test tasks were UP-TO-DATE, not rerun. The public JAR remains byte-identical:
`4e74a86169bb05d8ce299c000d4945c1bcf3746f53c8b3684ee37fe67a9b1b84`.
Both actual prepared templates were copied through the opt-in staging task:
SOURCE 33 files / 54,483,655 bytes, TARGET 71 files / 71,890,536 bytes, both
STAGED_UNARMED. Receipt/input rechecks found no drift. No marker, world, native
log or observation was generated. These copies remain private under `build/`.
Build/test/copy evidence and precise skips are recorded in the
[new ledger](evidence/compose-staging-2026-09-08.json). No Minecraft client/server,
screen interaction, new mod acquisition or publication is authorized. Native
composition observations remain NOT_RUN; helper tests do not populate `runs`.
Six profiles/runtime ABI are preserved. This is QA infrastructure, not increased
player compatibility or permission to install compositions.

At that checkpoint, the next step was to bind the materialized receipt and exact argv/
immutable artifact pins to a closed session, then bounded post-exit publication
and recovery, first tested end-to-end with inert helpers. Native comparison,
including the source Java 17 contract, is still required and unauthorized.
Do not accept COMPOSE-01, unlock LINKAGE-GENERAL or promote installation/UI.

## Previous headless checkpoint: COMPOSE-01 read-only planning

The [read-only disposable planner and recorded exit checker](COMPOSITION_RUN_PLANNING.md)
are implemented in the dev-only corpus module. Both real prepared Forge templates
produce bounded, hash-bound **PLANNED_ONLY** directives; neither creates a game
directory, copies files, starts a process or grants execution/installation authority.
The exit checker rejects incomplete/reordered save logs, late failures, stale
bindings and recorded timeout/cancellation, but only returns **RECORDED_EXIT_ONLY**
for supplied matching records. It does not observe a live process or prove EOF.

All **146 new tests passed, no failures/errors/skips**. Full build/package audit
passed: fresh corpus **434 passed / four existing symlink-privilege skips**;
converter/product/runtime/workflow test tasks were UP-TO-DATE, not rerun.
The real observation index rechecked five files / 1,402,016 bytes and remains
**NOT_RUN**, with `runs: null`. No native exit records were fabricated. Original,
candidate and public JAR remain unchanged; public SHA-256 is still
`4e74a86169bb05d8ce299c000d4945c1bcf3746f53c8b3684ee37fe67a9b1b84`.

The [ledger](evidence/compose-planning-2026-09-07.json) records actual launcher/API
inspection, all 96 platform-file pins, build/tests and corrected failures: valid
manifest EOF handling, JDK metadata UTF-8 rejection and Windows test-junction
cleanup. The prior successful source logic run used Java 8; it does not validate
the composition source Java 17 contract. No production rules/adapters changed.

At that checkpoint, the next step was owned create-new staging and a bounded live
process collector tied to the selected plan. Test with inert helper JVMs only:
EOF/exit, timeout/cancellation, partial writes, unknown files, locks and post-exit
hashes. These pieces are not implemented by the read-only tools. Real native
comparison remains required but unauthorized. Do not open games, accept
COMPOSE-01, unlock LINKAGE-GENERAL or promote installation/UI.

## Previous headless checkpoint: COMPOSE-01 capture probes

The separate [source/target capture probes](COMPOSITION_PROBES.md) are now
implemented and compiled against both exact Forge baselines. Each completed
**123 tests, zero failures/errors/skips**, including the real packaged QA archive,
Windows junctions/hard links, bounded writes, artifact drift and failure handling.
Five additional synthetic recorder-to-checker integration tests passed: wrong
helper/foil/output observations are retained and rejected, not corrected to the
oracle. These are test mechanics, not native behavior or additional supported mods.

Full root build/package audit passed. Fresh corpus: **288 passed / four existing
symlink-privilege skips**. Converter/product/runtime/workflow test tasks were
UP-TO-DATE, not rerun. Probes and shared recorder are absent from public and corpus
main JARs. Public SHA-256 remains
`4e74a86169bb05d8ce299c000d4945c1bcf3746f53c8b3684ee37fe67a9b1b84`.
The real index rechecked five files / 1,402,016 bytes and remains **NOT_RUN**;
original, retained candidate, proof and receipt are unchanged. No games were run.

The [new ledger](evidence/compose-probes-2026-09-07.json) records hashes, source
audit, builds, test counts/skips and compiler deprecation warnings. No production
rules, adapters, profile promotion, user worlds or public dependencies changed.

At that checkpoint, next in **LB-08 / COMPOSE-01** was the external disposable run planner
and post-exit collector headlessly. It must control launch arguments/cwd and
verify complete logs, exit and post-exit bindings. Native Forge events occur too
late to stop arbitrary world-option overrides before world loading; a capture
marker also precedes completed save/close. Neither is a sandbox or runtime PASS.
Native comparison remains required but unauthorized. Keep COMPOSE-01 PARTIAL;
do not unlock LINKAGE-GENERAL, installation/UI or start Minecraft.

## Previous headless checkpoint: COMPOSE-01 observation checker

The [artifact-bound observation checker](COMPOSITION_OBSERVATIONS.md) now validates
the finite original/target foil, helper/super and furnace observation contract in
the developer-only corpus tool. All **77 new synthetic tests passed**, zero failures
or skips, including stale bindings/logs, missing ticks, wrong side/thread/cohort,
lost short-circuit, duplicated output and a real hard-link alias. These are checker
tests, not executed mod behavior or 77 supported mods.

The real reviewed index checks five actual files (1,402,016 bytes), including the
unchanged original, retained candidate and public JAR. It returns **NOT_RUN**
because no native observations are supplied. Even a matching pair returns only
RECORDED_MATCH_ONLY, never installation authority or independent execution proof.
The [ledger](evidence/compose-observations-2026-09-07.json) separates serial build,
synthetic tests, real-file integrity and unperformed runtime dimensions.
Full build/package audit passed: the freshly executed corpus suite has **283
passed / four skipped** (pre-existing symlink-privilege cases). Converter, product,
runtime and workflow tests were UP-TO-DATE, not new executions. The final public
JAR remains byte-identical at SHA-256
`4e74a86169bb05d8ce299c000d4945c1bcf3746f53c8b3684ee37fe67a9b1b84`;
its 526 entries exclude the checker/QA tools. Native checks remain NOT_RUN.

No conversion rules, adapters, runtime capability admission or original fixture
changed. Next remains **LB-08 / COMPOSE-01**: implement and compile the separate
original/target capture probes, loaded-artifact binding and isolated tick/failure
collection. Native execution remains unauthorized and required later. Keep
COMPOSE-01 PARTIAL, without LINKAGE-GENERAL or public composition promotion.
Do not open Minecraft or automate the screen.

## Previous headless checkpoint: COMPOSE-01 controlled QA export

The [controlled developer QA export](COMPOSITION_QA_EXPORT.md) now retains the
already proved candidate in `build/composition-qa/run-20260907-export1` with a
canonical proof and separate hash-bound receipt. The explicit developer task
revalidates source/candidate selection, owns all private writes, and refuses
unsafe paths, replaced files and incomplete handoffs. It never executes mod code,
installs into a game, or promotes a new public conversion profile.

All **96 composition tests passed**, including **40 new cases**: nine real-fixture
integration cases and 31 filesystem cases. The genuine Windows junction test
passed. Fresh full root validation: converter 778 passed / 66 skipped; corpus
206 passed / 4 skipped; product 155 passed / zero skipped. Unchanged runtime and
workflow tests were UP-TO-DATE. Seven CLI fixture outputs across all six existing
profiles are byte-identical. The retained composition candidate is also identical
to the previously private candidate: 5,913 bytes, four classes, ten methods.
Its 13 file-entry hashes were checked against the actual retained ZIP.

Build, explicit QA export and package audit passed. Current public SHA-256:
`4e74a86169bb05d8ce299c000d4945c1bcf3746f53c8b3684ee37fe67a9b1b84`.
Only four engine class entries differ (one new) from the archived `9248ba...` JAR.
QA tools are excluded from the public/installed CLI artifacts; adapter catalog,
runtime capability admission, libraries and simple-content data are unchanged.
**Native startup/behavior/GUI/server validation was NOT_RUN on this hash.**

The [export ledger](evidence/compose-export-2026-09-07.json) pins inputs, outputs,
sources, tests/skips and limitations. Injected I/O faults do not prove handling of
a physically full disk. Crashes or late move/cleanup failures can leave untrusted
private remainders; there is no automatic QA recovery or product installation
approval. Ordinary service inspection still returns only proof/hash.

Next remains **LB-08 / COMPOSE-01**: implement original/target comparison harnesses
and artifact-bound observations for foil/helper/super and cooking behavior.
Native comparison remains required but not authorized now. COMPOSE-01 stays
PARTIAL; do not unlock LINKAGE-GENERAL or promote public installer/UI support.
Continue headlessly, without games or screen automation.

## Previous headless checkpoint: COMPOSE-01 private serialized candidate

The [private composition candidate](COMPOSITION_CANDIDATE.md) now serializes the
combined fixture, reopens the actual ZIP and repeats file ownership, metadata,
resource, ASM and offline linkage checks on its bytes. All 13 output files have
implemented operations; distinct composition metadata and bindings prevent it
from masquerading as an existing profile. Four classes and ten methods are
preserved, with four necessary resource rewrites recorded. The original is intact
and was never executed. The temporary candidate is deleted; the typed service
returns only its proof/hash, with no export or installation authority.

All **56 composition tests passed**, including **24 new cases** in this continuation.
Fresh root validation: converter 738 passed / 66 skipped; corpus 206 passed /
4 skipped; product 155 passed / zero skipped. Runtime's 30 tests passed in the
first root run and were UP-TO-DATE in the second. Skips and evidence dimensions
are recorded in the [candidate ledger](evidence/compose-candidate-2026-09-07.json).
Seven CLI fixture outputs across the six existing profiles remain byte-identical.
These are fixture regressions, not seven compatible real mods.

Build and public packaging passed. Public SHA-256 at that checkpoint:
`9248ba60a1b64bba986fe40033272746eef624978ad6f475f7b33c8c5feffda8`.
The changes are the candidate engine and two implemented offline metadata/binding
adapters. Existing adapter revisions, runtime identity, public capability admission,
libraries, assets and embedded simple-content data are unchanged.
**Native startup/behavior/GUI/server validation was NOT_RUN on this hash.**

Next remains **LB-08 / COMPOSE-01**: controlled developer-only QA export and
original/target comparison harnesses. The later native behavior comparison remains
required but is not authorized now. No new public profile or installer/UI promotion
is enabled; COMPOSE-01 remains PARTIAL and does not unlock LINKAGE-GENERAL.
Do not open games or automate the screen; continue only eligible headless work.

## Previous headless checkpoint: COMPOSE-01 in-memory preview

The [static composition preview](COMPOSITION_PREVIEW.md) now combines the existing
owned-body logic proof and datapack recipe contract without a bootstrap. Its
immutable graph rejects conflicts/cycles/platform ownership; the shared engine
checks exact mappings, ASM and source/target linkage in memory. A new authored
Forge 36.2.42 fixture retains four classes and ten methods, plus a local-tag
smelting recipe. No original classes were executed and no composed JAR was emitted.

All **32 new tests passed**, with no skips. Fresh root runs: converter 714 passed /
66 skipped; corpus 206 passed / 4 skipped; product 155 passed / zero skipped.
Other unchanged module tests were UP-TO-DATE or NO-SOURCE. Seven CLI fixture
outputs across all six previous profiles remain byte-identical. This is regression
evidence, not a count of supported real mods. The first failed run and corrections
(fixture tag typo and test-retained duplicate API inventory) are retained in the
[ledger](evidence/compose-01-2026-09-07.json); production limits were not increased.

Build and public packaging passed. Public SHA-256 at that checkpoint:
`4ebc190b29cb0a53507ef4a6b63c0c0cfacab402ef003077535535d5c895eaad`.
Only engine classfiles differ from the archived preceding public JAR; adapters,
runtime identity, libraries, resources and default simple-content data are unchanged.
**Native startup/behavior/GUI/server validation was NOT_RUN on this hash.**

Next remains **LB-08 / COMPOSE-01**: composed metadata/bindings, complete output
ownership and private serialization/validation. The later original-versus-converted
native comparison remains required but is not authorized now. This preview cannot
publish/install, promote a public profile or satisfy LINKAGE-GENERAL's prerequisite.
Do not open games or automate the screen; continue only eligible headless work.

## Previous headless checkpoint: LB-05 receipts/history → LB-08 composition

The user explicitly requested continued implementation without opening Minecraft,
not repeated requests for screen permission. The [receipt correction](JOB_INSTALL_RECEIPTS.md)
fixes the missing append after Apply/recovery: every affected job gets a versioned
per-attempt observation before the coordinator becomes idle. Existing receipts
remain intact; failures are visible and do not erase a committed installation.
The [bounded read-only history API](JOB_HISTORY_READ.md) now accepts strict
schemas 1/2 on the same coordinator. Invalid, ambiguous, oversized or unsafe
receipts are diagnosed; partial scans do not select a highest receipt. Historical
jobs never become actionable jobs or installation approvals. Native history UI,
journal/discovery reconciliation and artifact requalification remain pending.

Fresh root validation passed **155 product tests (50 new history cases), zero
failures/errors/skips**, and public packaging checks. Other modules' unchanged
tests were UP-TO-DATE. Public SHA-256 for that preceding checkpoint:
`f041efcb92a1a2727b55918a8604cf58435a35b6eedbecb9c9e76c292673a940`.
It has not been launched in Minecraft. The first targeted run exposed missing
Windows hard-link detection when file keys were absent; bounded isSameFile
fallback fixed it. Real junction/deny-read tests passed as well. The old
192-file/61-assertion evidence remains historical, bound to archived `3749da...`.

See the [preceding slice ledger](evidence/job-history-read-2026-09-07.json) for
actual tests/build and artifact hashes. Changes to the product mean the older
native acceptance below must not be inherited by the newly built public JAR.
No transformation profile, adapter identity or Minecraft/Forge API call changed.
ZIP-entry comparison against retained `6e168...` found no changes to engine,
runtime, mappings or assets; only controller/history classfiles changed.
Next: **LB-08 / COMPOSE-01**, immutable composition planning and a bounded
combined fixture. Its eventual native behavior comparison is still required;
do not promote compositions into the public installer/UI without their gates.

## Deferred native gate: LB-06 / PUBLIC-CURRENT-E2E

Current user restriction: **headless work only, no games or screen automation**.
Do not use focus/mouse/keyboard controls or launch a client until explicitly
authorized again. The native observations below ended before this restriction;
the later continuation only audited files and ran headless tests.

The user explicitly prioritized removing the separate mapping/API bundle setup.
[Included simple-content contracts](EMBEDDED_SIMPLE_CONTENT_DATA.md) implement
that bounded product correction without widening any conversion profile. CLI
and UI default to the same byte-pinned finite provider; broader developer
profiles still require explicit full data. Full real-API build, final packaging,
CLI/service conversion without bundle/cache and a clean ordinary dedicated
server check passed for public hash `3749da06606f36b7e9bcbb97e4d9558f91d0bfa1c60e5da533767fd722607df6`.
The output fixture remains byte-identical to the previous conversion.

The [new native acceptance](EMBEDDED_SIMPLE_NATIVE_UI_VALIDATION.md) closes the
earlier foreground gap. On the same public hash, real UI conversion with the data
field blank produced three identical staged outputs; continuing left the title at
three loaded mods. One player-approved ADD committed before clean Apply/close.
A separate launch discovered the fixture (four loaded mods); actual crafting
consumed nine ingots for one block, real placement consumed that block, assets
rendered and pt_br/en_us tooltips worked. The original and all 12 original
assets/data entries are intact. Both clients exited zero, the new disposable
world saved all dimensions, and no owned native process remains.

This accepts only DATA-SIMPLE-01-NATIVE_UI, not all LB-04 or PUBLIC-CURRENT-E2E.
The [current-hash negative continuation](PUBLIC_CURRENT_NEGATIVE_VALIDATION.md)
now records corrupted/duplicate inputs, worldgen and missing-dependency refusals,
two independent queued results, actual Windows staging write denial/restoration,
accepted Cancel and active-screen Escape with no incomplete output. Both clients
exited zero without opening a world. Failed/late cancellation attempts remain
CONVERTED and are not called cancellation passes. A new bounded data-load QA
fixture preserves every original class/resource byte; it is not packaged and
adds no conversion capability.

Four headless interruption/recovery and Windows sharing-handle cases freshly
passed afterward. Those use installer test code and inert declarations; they
do not close the **public-only client interruption during commit** gate. This
remains the next runtime gap, now requiring explicit renewed UI authorization.
The [headless evidence consolidation](PUBLIC_CURRENT_QUALIFICATION.md) now pins
192 files and 61 exact recorded JSON assertions across positive/negative UI and
static/server evidence. Its dev-only verifier detects drift without approving
qualification or executing games; a reload log is not a dedicated screenshot.
The [next-session recovery continuation](PUBLIC_NEXT_SESSION_RECOVERY_VALIDATION.md)
adds seven headless crash/recovery cases with synthetic loaded-ID/file/world
snapshots. They cover safe resume of proven publications, missing final commit
and refusals to remove loaded artifacts; no production behavior was changed.
These are test-classpath results, not actual Forge discovery or native interruption.
Input-duplicate refusal is not installed-ID conflict qualification; broader
GUI scale/resize/queue and native interrupted-commit qualification remain limited.
Persisted job receipts remain CONVERTED/null install fields despite a
COMMITTED journal: reopenable job/install linkage is still UI-PROMOTION work.
Do not claim persisted PENDING_RESTART or automatic restart. Runtime, static
validation and the earlier same-hash server/cacheless evidence remain separate.
Nothing was published or acquired from new real mods.

The earlier network cursor is deferred, not resolved:
**NET-QA-02-HANDSHAKE / CLIENT_PROTOCOL missing normal native oracle**. Resume
its documented investigation after PUBLIC-CURRENT-E2E; do not restart
positive controls or advance capabilities based on the included-data change.

## Previous network cursor and historical evidence

The [stable prompt continuation index](PROMPT_CONTINUATION.md) records every
planned area, acceptance dependencies and remaining native gates. Repeating
the continuation request resumes this cursor; ten requests are not a promise
that every feature family or real mod will become compatible. New famous-mod
acquisition is deferred until the planned work and quality gates are complete.

### Initial continuation (historical)

The first handshake continuation implemented the five-case target-only handshake QA
peer/observer and serial runner, not a new converter profile. The public JAR,
original/converted fixture and old NET-QA-01 probe retain their exact hashes.
The [handshake record](NETWORK_HANDSHAKE_VALIDATION.md) and
[dated evidence](evidence/network-handshake-2026-09-07.json) keep the outcomes separate:

- `handshake-matched1` failed overall despite receiving 1/427: an exclusive
  Windows writer exposed the finish marker before close. Fixed the QA controller
  with forced temporary write, close and no-replace same-directory rename.
- `handshake-matched2` passed: native channel acceptance on both sides, one
  snapshot 1/427 applied on the real client thread, no server handler effects,
  both exits zero and server save complete. All installed/input hashes intact.
- `handshake-client-absent1` and `handshake-client-absent2` did **not** pass. Both
  disconnected without the required native version/channel/termination decisions.
  The server logged 22 handshake dispatches, the client no LoginWrapper receives.
  No login/effect occurred, but generic disconnection is not refusal evidence.
  Owned-only thread sampling in attempt 2 did not establish the cause.
- CLIENT_PROTOCOL, SERVER_ABSENT and SERVER_PROTOCOL remain NOT_RUN. The serial
  batch stopped on failure. No remaining owned native process or automatic retry.

The standalone probe's seven inert tests passed. Root build after the marker fix
passed in 79 seconds: 128 pure handshake cases, six marker filesystem cases,
186 earlier diagnostic cases and 81 installation cases executed freshly;
existing Java tests were UP-TO-DATE. These counts are not supported-mod counts.
NET-QA-02 is split into HANDSHAKE and COMPARISON; neither is closed by this partial
positive control. Diagnose the failed handshake before advancing that gate.
Do not rerun the matched control on unchanged inputs merely to accumulate passes.

### Latest continuation: clear-return diagnostic complete, normal gate still open

The [clear-return investigation](NETWORK_HANDSHAKE_CLEAR_RETURN.md) and
[versioned ledger](evidence/network-handshake-clear-return-2026-09-07.json) add
one opt-in, bounded QA mode. The helper binds the actual native Connection through
pipeline handler types and observes only clearReadPending0 at its verified RETURN.
It does not pause sendPacket/setProtocol/setAutoRead/interestOps. This is not a
converter rule, runtime adapter or production networking repair.

`handshake-cp-clearreturn1` passed the diagnostic-only CLIENT_PROTOCOL oracle in
65 seconds. Its one selected sample has autoRead=false, interestOps=0,
readPending=false and a valid key. The client VM disconnected before the required
three-second later observation (390 ms since the sample); the trace correctly
reports no later state and OBSERVED_NO_WITNESS. Actual Forge refusal and
ModMismatchDisconnectedScreen are recorded, but do not certify default behavior.
The latest DEFAULT failure from the preceding round remains authoritative.

Client, dedicated server and separate debugger exited zero, the server saved,
and an independent owned-process/port audit found no remaining actors/listeners.
The public/original/converted/in-game probe hashes are unchanged. Seven new inert
tests bring this fixture to 42 passing Java tests. The handshake controller now
has 204 passing PowerShell cases, including strict JSON type rejection; the actual
trace also passes that post-run validator. Executed scripts are archived separately
from this validation-only hardening. Root builds and earlier diagnostic/install
checks are recorded separately in the ledger, not counted as supported mods.

The narrower breakpoint still changes scheduling and did not reproduce the normal
failure. The next investigation needs a discriminating hypothesis and a source-
verified observation strategy without suspending the activation/send/clear path;
do not automatically retry these diagnostics. Any required platform/version change
needs explicit direction. SERVER_ABSENT/SERVER_PROTOCOL, source/target comparison,
NET-QA-03 and capabilities remain pending. No famous mods were acquired.

### Earlier continuation: initial protocol sampled, normal CLIENT_PROTOCOL still fails

The [protocol observation](NETWORK_HANDSHAKE_PROTOCOL_OBSERVATION.md) and
[versioned ledger](evidence/network-handshake-protocol-2026-09-07.json) extend the
QA debugger to trace schema 2, without changing the public/converted/in-game probe
JARs. Actual Forge-patched declarations, not vanilla SRG local names, authorize
reading two protocol locals; unknown metadata fails the diagnostic. No packet
arguments, payloads, credentials or target methods are accessed.

`handshake-cp-protocoltrace1` passed only the diagnostic refusal oracle in 64 seconds.
The first intention send captured UNSET -> HANDSHAKING; hello captured HANDSHAKING
-> LOGIN. The first null protocol is now a native fact for this observation, not
a claim inherited from the earlier failed run. The trace again saw read interest
restored, not the failing stale-clear sequence. A bounded Netty-only regression
also proves connect-future success does not guarantee completed channelActive.

The paired DEFAULT run `handshake-cp-protocol-pair1` failed the exact native oracle:
22 server handshake dispatches, no client LoginWrapper entries, no native snapshot
version decisions/termination. Generic DisconnectedScreen and zero effects do not
certify refusal. The runner reached its log oracle only after clean-exit/save checks;
the failure journal and an independent process/port audit confirm no owned actor
remained. The serial batch stopped; the two server-side negatives remain NOT_RUN.

35 isolated Java tests passed (eight new JDI-mirror cases and one real-Netty
activation-order case). Root checks execute 181 handshake, 186 earlier diagnostic
and 81 installation PowerShell cases, with root Java tests UP-TO-DATE. The product
and conversion rules remain unchanged; this is not a networking repair or support
extension. Next localize the stale clear on the now-identified initial path with
less scheduling perturbation. Do not rediscover the same initial-protocol fact or
rerun positive controls merely for counts. All later prompt gates remain open.

### Earlier continuation: read-interest callers observed, failing interleaving still unproven

Implemented a separate bounded JDK/JDI QA helper, not an installed mod or adapter.
It observes the owned disposable client's actual Netty/selector setter calls;
the public JAR, original, converted fixture and in-game probe retain their hashes.
The [trace contract and observations](NETWORK_HANDSHAKE_READ_TRACE.md) and
[new ledger](evidence/network-handshake-read-trace-2026-09-07.json) preserve this
round separately from the earlier failed normal run.

CLIENT_PROTOCOL passed the native refusal oracle in READ_INTEREST_TRACE mode in
64 seconds. Suspending event threads perturbs scheduling, so this is explicitly
DIAGNOSTIC_ORACLE_PASSED, not a normal-mode pass or repair. Eight samples identify
the initial intention/hello sends in ConnectScreen and queued Netty re-enables.
The observed clear ran with autoRead=false, and OP_READ was restored. The trace
reports OBSERVED_NO_WITNESS: the persistent-loss interleaving was not captured.
The prior protocol at the first send was not read and must not be asserted as null.

The standalone fixture has 26 passing inert Java tests, including seven new trace
classifier tests. Root checks execute 171 handshake, 186 earlier diagnostic and
81 installation PowerShell cases; root Java tests remain UP-TO-DATE. Native game
and helper exits were zero, server save completed, and this round added no normal
retry, platform patch, forced I/O, version upgrade or conversion feature.

Next: inspect first-send protocol/activation ordering and the concurrent clear,
then return to exact normal-mode refusal gates. CLIENT_PROTOCOL remains open;
SERVER_ABSENT and SERVER_PROTOCOL remain NOT_RUN. Capabilities, further feature
families and famous-mod acquisition have not been enabled. Status stays Beta.

### Earlier continuation: platform control and first normal CLIENT_ABSENT pass

The [new control report](NETWORK_HANDSHAKE_PLATFORM_CONTROL.md) and
[ledger](evidence/network-handshake-platform-2026-09-07.json) keep three results
separate. An independent target-native QA channel, with no LegacyBridge or fixture
installed, was rejected correctly in 60 seconds. That control did not reproduce
the stall and does not establish a platform-only cause.

CLIENT_ABSENT then passed the exact native oracle in DEFAULT mode in 61 seconds,
using the same rebuilt probe and untouched public/converted JARs. No production
repair was made. This normal pass justified collecting the next finite negative
before completing causal tracing; it did not close the intermittent transport gate.
CLIENT_PROTOCOL subsequently failed without native refusal evidence. The serial
batch stopped; SERVER_ABSENT and SERVER_PROTOCOL were not prepared or launched.

Added explicit platform-only inventory/channel separation, no conversion-PASS
promotion, and a third Netty-only regression for a later no-op protocol re-enable.
19 isolated Java tests passed, and the root checks ran 165 handshake, 186 earlier
diagnostic and 81 installation cases freshly. Existing root Java tests were
UP-TO-DATE. Only the QA metadata permits absence of the runtime in the platform
control; normal scenarios still enforce its exact version and fixture inventory.
No converted dependency, converter profile or public artifact changed.

Next: diagnose the CLIENT_PROTOCOL failure and the shared transport intermittence,
then resume the remaining negatives. Earlier missing OP_READ remains an actual
observation, but the new failed default run had no selector sample. No capabilities,
new famous-mod acquisition or publication was enabled. Overall status stays Beta.

### Earlier continuation: native selector mismatch localized

The [JFR/selector report](NETWORK_HANDSHAKE_TRANSPORT_DIAGNOSTICS.md) and
[new ledger](evidence/network-handshake-transport-2026-09-07.json) supersede the
generic investigation cursor, not the unclosed native gate. Three new attempts:

- Jfr1 failed the diagnostic collector: default-enabled Minecraft ServerTickTime
  appeared unexpectedly. Fixed explicit disabling before the event allowlist;
  added the corresponding regression instead of ignoring the event.
- Jfr2 passed **diagnostic-only**: native ABSENT refusal and matching two-direction
  socket byte totals (1075 S2C, 294 C2S). This does not certify a normal-mode fix.
- Selector1 failed the native oracle but exposed the stalled client's actual
  selector: autoRead=true, valid key, interestOps=0, readPending=false. Server
  OP_READ remained set. An enabled config flag was not an active read subscription.

Two bounded Netty-only tests reproduce the inconsistent state under a controlled
queued-enable/off-loop-disable interleaving and verify the same-loop control.
The full native trigger is not yet traced; this is localization and a reproducible
library scheduling hazard, not a production repair. No platform class or I/O state
was patched in Minecraft, and no library/version/security setting changed.

Sixteen isolated probe tests pass. Typed recording reports, unknown/default event
refusal, bounded aggregates and temporary cleanup are tested separately from native
approval. Root builds passed serially; the latest root adds 17 recording-related
PowerShell cases to the prior checks, with root Java tests UP-TO-DATE. All games
were closed. Original, converted fixture and public JAR retain their exact hashes.

Next: trace the actual read-interest removal trigger and evaluate a remedy within
the platform constraints, then run the required normal-mode cases. No capability
profile or next feature family was enabled. Famous-mod downloads remain deferred.

### Earlier continuation: explicit handshake diagnostics

The [new diagnostic ledger](evidence/network-handshake-diagnostics-2026-09-07.json)
extends, but does not rewrite, that evidence. The normal CLIENT_ABSENT attempt 3
again failed before the exact native refusal decisions; the remaining batch was
not launched. Mojang DEBUG and packet-marker diagnostics established actual
ReadTimeoutException in two separate runs. A third diagnostic run produced the
correct native ABSENT refusal, but **DIAGNOSTIC_ORACLE_PASSED is not normal PASS**:
debug settings may change code paths/timing, and the following default run failed.
Neither a root cause nor a platform/converter fix is established.

Implemented a bounded, explicit, read-only transport observation in the QA-only
probe. A real client sample with default logging showed LOGIN, active/open,
autoRead=true, pending outbound bytes 0 and empty splitter/decoder buffers at the
sampled instant. This rules out neither an earlier transient nor a server-side
problem; encoder-entry logs alone do not prove bytes were serialized/delivered.
The final two-endpoint run also failed but captured both roles on their actual
event loops: both LOGIN/autoRead=true/open/writable with empty sampled decoder
buffers and no pending Netty outbound bytes. The server observer uses the actual
pinned ServerConnectionListener inventory, observes only its single owned loopback
connection, and shares no client class. Ten inert probe tests passed; no native
process remains. Next: locate the gap between server encoder entry and client
decoder without treating these instantaneous snapshots as proof of wire delivery.
No platform class, connection setting, timeout or converted predicate is changed.
Diagnostics are off by default and never grant native approval.

Serial root builds and probe inert tests pass; the ledger records actual fresh
versus UP-TO-DATE tasks, not supported-mod counts. Public/converted/original bytes,
runtime ABI and simple-content-v1 remain unchanged. The active cursor does not
advance around the normal handshake failure. No new mods were acquired and no
publication, capability exposure or automatic retry was scheduled.

### Earlier NET-QA-01 continuation (retained evidence)

The 2026-09-07 continuation resumed actual two-client networking. comparison3
failed during B's login handshake; its cause is not proven. comparison4 reached
both connections, distinct snapshots and malformed-packet checks, then exposed
a source-verified QA oracle error: Forge handles an unknown discriminator first
on Netty, again on the render thread, then emits a vanilla fallback warning.
The corrected oracle requires that exact sequence, not arbitrary duplicates.
The invalid QA client simulation distance was also corrected from 2 to the
pinned native minimum 5. The detailed [native ledger](NETWORK_SNAPSHOT_VALIDATION.md)
retains both failures. No converter recipe, adapter or public JAR changed.

`comparison5` passed the complete NET-QA-01 finite scenario: 57 native receipts,
two clients and one ordinary dedicated server, isolated snapshots, main-thread
application, duplicate/gap/malformed rejection, unknown-ID fallback, reconnect
with fresh sequence, and native wrong-direction disconnect while B remained usable.
All three exit codes were zero; native server save markers and unchanged original,
converted/public/probe hashes were verified. This is a final-artifact-bound target
fixture pass, not a whole-mod, source/target comparison, UI or soak approval.
Receipt SHA-256: `5c624edff112284190d02ea9d787895d58ff72fb997a2a6425465057a0cbaa67`.

The pre-native code verification passed in 34 seconds: 186 fresh diagnostic cases/377
assertions, 76 fresh installation/options cases/254 assertions, packaging and
pinned API checks; Java test tasks were UP-TO-DATE. The changed test-only probe
separately built with six inert tests. Evidence and hashes are retained in the
[2026-09-07 continuation receipt](evidence/network-native-continuation-2026-09-07.json).

Post-run audit found an overbroad identity field in that immutable QA receipt.
The ledger records an erratum: native Forge debug logs can retain synthetic
player identities, whereas safe argument files exclude them and captured
launcher-argument lines are redacted. Tokens observed in native logs were masked;
no real account/credential was used. Future success receipt schema 2 uses scoped
booleans; its metadata helper has five new pure cases (81 installation/options/
identity cases total). This does not change the native behavior just validated.
The final serial root build passed after this correction (27 seconds, Java tests
UP-TO-DATE, 186+81 pure QA cases freshly passed); its log and hash are in the
dated continuation receipt. Public bytes still match the native-tested artifact.

Do not reuse consumed comparison1/2/3/4/5 or handshake installations. Complete
NET-QA-02-HANDSHAKE and NET-QA-02-COMPARISON, then NET-QA-03 before capability
exposure: negative-peer, source comparison and queue/session contracts are not
covered by the target two-client scenario.
The earlier B handshake failure remains unexplained; no stability fix or repeated
soak pass is inferred. No background launch, new acquisition or publication is scheduled.

## Historical 2026-09-06 handoff: runtime RAM gate

The requested finalization on 2026-09-06 is a safe handoff, not a multiplayer or
capability-support approval. The final comparison3 invocation stopped before
launch: 5,850,796 KiB free versus the unchanged 6,291,456 KiB requirement. No
native process, mod installation, command receipt or world was created in this
retained prepared run. A separate read-only check passed for all ten client
discovery-library copies against the official installer SHA-1 and sizes. That
check does not substitute for native discovery or packet behavior.

Original, converted, probe and public JAR hashes were rechecked unchanged.
The earlier failed comparison2 and both RAM-blocked comparison3 attempts are
retained in the [network continuation ledger](evidence/network-native-continuation-2026-09-06.json).
No converter/runtime code or supported profile changed in this finalization.
No background retry or automatic restart is scheduled. The next native attempt
requires a separately resumed run with sufficient memory; no pending capability
work is silently advertised as supported. Final verification log:
`build/network-snapshot-v1/network-requested-closeout-build.log`.

## What was in progress

The last implemented extension was `network-snapshot-v1`: a closed authored
server-to-client snapshot recipe. Static conversion, ASM, scoped offline linkage,
CLI/service parity, cacheless engine execution and ordinary dedicated-server
discovery have evidence. The 2026-09-07 continuation adds partial native packet
delivery; see the current cursor for the final scenario's actual status. Earlier
sections below remain historical and are not commands to reuse consumed runs.

The next request started a capability API audit, considering a separate
`energy-item-v1` fixture with per-ItemStack state before inventory, fluids and
player data. That implementation was interrupted before any fixture, exact
mapping recipe, admission proof or native lifecycle comparison existed.

The audit found premature draft exposure in `ConversionProfile`, the service
dispatch and adapter catalog. The draft constructor would have selected the
existing logic path rather than a capability-specific proof. In this closeout,
only that unfinished draft was removed, including its newly created
`EnergyItemContractAdapter`. There was no validated capability implementation or
user fixture to delete. Existing converter profiles and adapter ABI were retained.

`CapabilityAdmissionBoundaryTest` now checks that energy/inventory/fluid/player
draft profile names are rejected explicitly, leave the original intact and create
no output or temporary; the catalog must not advertise a nonexistent capability
fixture/provider. These are safety regressions, **not capability behavior tests**.

## Position in the general prompts

| Area | Implemented scope and evidence | Still open / not implied |
| --- | --- | --- |
| Foundation, analyzer, reports | Modular Gradle, bounded inert JAR/ASM analysis, typed findings, schema-versioned CompatibilityReport and TransformationLog | Analysis is not execution or conversion permission |
| Mappings and ASM pipeline | Explicit official/SRG/API inputs, exact independently reviewed recipes, implemented operation bindings, output gates | Unknown contracts remain blocked; HIGH is not automatically VERIFIED; no public-cleared mapping bundle |
| Initial supported subset | `simple-content-v1`, finite item/plain-STONE-block registration and bounded resources; existing native fixture evidence | No arbitrary item/block logic, all recipes or all assets |
| Product P1-P3 | Reusable bounded engine, workflow separation and one mandatory public JAR; exact platform library/JiJ packaging; finite simple-content data included | Full data still explicit for developer profiles; licensing/data distribution remain release gates |
| Product P4-P5 | Native client menu, queue/results, explicit staged application, durable per-file journals, managed unloaded updates, resume/restore | No hot-loading, loaded-JAR replacement, automatic pre-discovery repair or universal restart |
| Product P6 | Historical public-only UI conversion/apply/manual close/next-launch item, block, recipe, assets, en_us/pt_br and separate server evidence | Full UI rerun on the current included-data public hash remains open; kill-mid-commit public-client and fully offline game claims remain open |
| Versioned corpus/backlog | `2026.09.06.1`: three authorized real releases, six fixture cases; original binaries remain local; source/rights and stage separation recorded | Zero complete real-mod ports verified; later authored profiles have separate ledgers, not a silently updated corpus version |
| Preserved owned logic | `logic-preserving-v1`: six classes/25 methods; separate original/converted native traces match | Not arbitrary overrides, dependencies, interfaces or custom gameplay |
| Lifecycle/config/commands | `lifecycle-preserving-v1`: closed registration/late lookup/common setup, COMMON bool/int config and bounded server command; original/target native comparisons | Not all buses, events, priorities/cancellation, client/server config scopes or command contracts |
| Item/block expansion | Existing simple content plus `food-item-v1`; native consumption and finite drop/pickup comparisons | Tools, armor, effects, custom block states, ticks, redstone, growth and arbitrary interactions unsupported |
| Resource/datapack expansion | `datapack-recipes-v1`: bounded vanilla cooking/stonecutting and local tag references, native comparative/reload evidence | Custom serializers/ingredients, loot/GLM, advancements, functions/commands, arbitrary models/animation/sounds unsupported |
| Networking | `network-snapshot-v1`: exact authored fixed-size S2C codec/session contract; NET-QA-01 passed on comparison5; matched control and later CLIENT_ABSENT DEFAULT passed in their recorded scopes | CLIENT_PROTOCOL still lacks a normal native oracle; SERVER_ABSENT/SERVER_PROTOCOL NOT_RUN. COMPARISON and NET-QA-03 remain; no C2S mutations or cross-version play |
| Capabilities/persistent data | Preliminary API audit only; profiles and adapters remain unavailable | Inventory, energy, fluids, player capabilities, custom registration, lifecycle/clone/respawn tests and SavedData not implemented; no old-world migration |

The player UI/installer still uses **simple-content-v1**. Other implemented
profiles are independent developer opt-ins; they cannot be freely combined to
claim that a mod using several feature families is supported.

The versioned real corpus records remain Clumps 6.0.0.28, Controlling 7.0.0.29
and AppleSkin 2.5.1-mc1.16.4. All three are analyzable and blocked for conversion
under the recorded workload. AppleSkin's adjacent source baseline is recorded,
not relabeled as a Forge36 build. No real binaries were downloaded or published
in this closeout.

## Capability audit facts, not implemented equivalences

The pinned merged source archives were read inertly and rehashed:

| Baseline source archive | SHA-256 |
| --- | --- |
| Forge 1.16.5-36.2.42 mapped official sources | `bbe2d148f96694271c43fc2ba584463d75d9ffc490abd0469ae66b831293d376` |
| Forge 1.20.1-47.4.23 mapped official sources | `4bd6656a7cbe3809da68f3b709ca0f2fa186575f58538668c58ce30cb38c4932` |

Source `CapabilityManager.register(Class, Capability.IStorage, Callable)` and
`Capability.getStorage/readNBT/writeNBT/getDefaultInstance` are not a target
rename recipe. Target registration/token acquisition must be reviewed separately.
`IForgeItem.initCapabilities` exists in both pinned archives with different NBT
owners. This alone does not prove equivalent host lifecycle or serialization.

Source `EnergyStorage` implements `IEnergyStorage`; target additionally implements
`INBTSerializable<Tag>`. Target `deserializeNBT` assigns the integer without
the constructor's clamp. Treating a newly available serializer as an old API
rename would therefore be unjustified. Exact provider ownership, accepted NBT
schema, face behavior and retained LazyOptional references still need fixtures.

The official [1.16 capability documentation](https://docs.minecraftforge.net/en/1.16.x/datastorage/capabilities/)
and [1.20.1 capability documentation](https://docs.minecraftforge.net/en/1.20.1/datastorage/capabilities/)
were consulted. Persistence is separate from synchronization. Invalidation and
copying player state around death/clone require explicit owner-lifecycle contracts;
closing a UI cannot be assumed to end an ItemStack provider's lifetime.

## Historical interrupted-draft closeout

The interrupted-draft root verification passed, as did the installed-CLI byte
regressions. That earlier closeout did not start Minecraft; runtime results in
this historical section are the previously recorded hash-bound observations.
The later comparison2 attempt did start the three isolated native processes,
as recorded separately in the networking continuation below. The
[machine-readable closeout receipt](evidence/prompt-closeout-2026-09-06.json)
records build/evidence hashes and separates cached test state from fresh runs.

Existing evidence hashes were rechecked successfully: the logic and food native
traces; three lifecycle traces; datapack source/target traces and stdout logs;
network cacheless receipt, dedicated-server receipt and its stdout and discovery
debug log. The network receipt explicitly says `SINGLE_SERVER_ONLY`; packet
delivery and multiplayer remain `NOT_RUN`.

The first forced root build (`build/prompt-closeout-build1.log`, 9m49s) failed in
`ConversionControllerTest.changingSelectionClearsPreviousCandidateErrorWhileSameRowRetainsIt`:
the initial `inventoried()` wait exceeded its unchanged 15-second test deadline
(15.026 seconds). It failed before the selection/conversion assertions, not in a
capability recipe. The log is retained with SHA-256
`f123d9503b45cbf01deb163071f7a189ceed267403cfe0c8d45491ce82dac2a2`.

An isolated rerun of all 17 controller tests passed without code or timeout
changes (`build/prompt-closeout-controller-check.log`, 46 seconds); the previously
failing case took 8.518 seconds. Log SHA-256:
`78df7b75987ddc7e6bd59929405e9fcf806389d608067c9255e18f0de56fc737`.
The cause of that timing outlier is not established. A passing retry is not a
claim that a proven UI defect was fixed or that responsiveness under load was
certified. The subsequent root verification is recorded separately below.

`build/prompt-closeout-build2.log` passed in **2m23s**, 63 tasks (19 executed,
44 up-to-date), SHA-256
`f1ff0e522fa2be9a05d1110dac3903716388a506721093e9bd7fbb4027201f12`.
The same six exact-baseline integration flags were enabled as in the networking
full-build command. Converter/corpus tests ran in the forced first attempt and
were up-to-date in the second; all 82 product tests reran successfully in the
second, including the formerly timed-out case. Runtime/workflow tests were
up-to-date. Do not describe this as 929 fresh executions in one passing invocation.

| Module | Aggregate tests | Passed | Skipped | Failures/errors |
| --- | ---: | ---: | ---: | ---: |
| legacy-converter | 701 | 700 | 1 | 0 |
| legacy-corpus | 84 | 83 | 1 | 0 |
| legacy-product | 82 | 82 | 0 | 0 |
| legacy-runtime | 30 | 30 | 0 | 0 |
| legacy-workflow | 32 | 32 | 0 | 0 |
| Total | 929 | 927 | 2 | 0 |

The two unchanged skips require native Windows symlink creation privileges.
The five new refusal cases are included in the converter total. The isolated
17-test diagnostic run is not counted again as additional coverage or mods.

The existing serialized regression runner completed six genuine-fixture CLI
conversions in 32 seconds: simple, logic, lifecycle explicit/inferred, food and
datapack. All six outputs remain byte-identical to their approved baselines;
all original hashes remain unchanged. Receipt:
`build/network-snapshot-v1/regressions/prompt-closeout/regression-receipt.json`,
SHA-256 `aef7b0aa68c4b9832c73911128b94bf6a2c81ec19a6e1fff462d422096717056`.
Networking CLI/service/plan and negative cases also passed in the forced
converter run. Its existing converted output was rehashed unchanged.

Final public artifact: `legacy-product/build/libs/LegacyBridge.jar`, **1,190,628
bytes**, SHA-256
`8daae3478a1b59482c438b843311c4160b5bdc8e4721f8e6067a1a87b81bbf88`.
It is byte-identical to the networking-era public artifact. The packaging audit
passed: one runtime identity, one unchanged JiJ library, no platform/logging
duplicates or private mappings. No second mandatory LegacyBridge JAR was added,
no input installed and no worlds or user launcher configuration were modified.

## Next gates, in order

1. NET-QA-02-HANDSHAKE: investigate CLIENT_ABSENT's missing native version decisions
   using the retained logs/thread samples; cause is not established. Then execute
   CLIENT_PROTOCOL/SERVER_ABSENT/SERVER_PROTOCOL. Do not accept a disconnect screen
   or timeout alone. The matched control already passed on handshake-matched2.
   NET-QA-02-COMPARISON still requires separate original/target native behavior.
   Preserve NET-QA-01 and all failed attempts. Retain RAM gates and fresh labels;
   do not repeat unchanged passed controls without a concrete reason.
2. NET-QA-03: pending-work/disconnect race, queue/flood bounds and session ownership.
   Do not treat fixed payload size as proof of bounded pending work or one native
   pass as connection stability/soak certification.
3. CAP-API-01/CAP-E01: implement and review a genuine energy ItemStack fixture, then exact
   source/target recipes, offline linkage/access, positive/negative conversion
   tests and separate native save/load/copy/face/simulation/invalidation tests.
   Only then expose a capability profile. Inventory, fluids and player data remain
   separate future slices; player lifecycle and synchronization are not inferred
   from item tests.
4. Keep corpus-driven whole-mod closure separate from fixture progress. The
   [current prioritized backlog](../corpus/BACKLOG.md) still selects metadata/side
   admission, finite lifecycle delivery and exact Clumps behavior closure; each
   has prerequisites and may remain unsupported after feasibility review.
   Retain LOGIC-QA-01, LIFE-QA-01, ITEM-QA-01, DATA-QA-01 and each deferred
   expansion in the continuation index; finite fixture approval does not close
   the full requested family.
5. Before publication, resolve RELEASE-DATA/RIGHTS and rerun public-only
   client/server acceptance against the exact release hash (RELEASE-PUBLIC-QA).
   Only after all planned quality gates close may INTAKE-FINAL obtain new real
   mods, with current official version/authorization checks and local-only files.

## Evidence index

Networking continuation: [native QA continuation receipt](evidence/network-native-continuation-2026-09-06.json)
is historical; current failures, corrections and retained hashes are in
[networking validation](NETWORK_SNAPSHOT_VALIDATION.md).
The 2026-09-06 Windows-only QA-helper suites passed: 90 diagnostic cases/273 assertions
and 71 installation-inventory cases/152 assertions. Root build passed after both
changes (`network-installation-build2.log`, 1m01s, Java tests up-to-date).
The public JAR remains byte-identical to the hash above. No new adapter, fixture
behavior, capability profile or whole-mod compatibility was enabled.

- [Product milestones and acceptance criteria](PRODUCT_IMPLEMENTATION_PLAN.md)
- [Public-only UI/server evidence and real screenshot paths](PUBLIC_ONLY_END_TO_END_VALIDATION.md)
- [Corpus validation](CORPUS_VALIDATION.md)
- [Preserved-logic validation](LOGIC_PRESERVING_VALIDATION.md)
- [Lifecycle validation](LIFECYCLE_PRESERVING_VALIDATION.md)
- [Food validation](FOOD_ITEM_VALIDATION.md)
- [Datapack validation](DATAPACK_RECIPES_VALIDATION.md)
- [Networking validation and pending native gates](NETWORK_SNAPSHOT_VALIDATION.md)

This closes the interrupted draft safely; it does **not** mark all prompts,
capability support, multiplayer or publication complete.
