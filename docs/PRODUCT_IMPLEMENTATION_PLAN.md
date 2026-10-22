# Product implementation sequence and acceptance criteria

Latest 2026-09-19: [preserved update coordinator](PRESERVED_UPDATE_COORDINATOR.md)
connects the backend to typed two-sided review, separate one-use confirmation,
shared worker/gate/lease, real recovery results and inert update receipts/history.
Next: LB-04 / UI-PROMOTION / PRESERVED_UPDATE_SCREEN. Do not repeat the backend
or coordinator; native forms/QA remain separate, public Convert/Apply unchanged.

Earlier 2026-09-19: [preserved managed updates](PRESERVED_MANAGED_UPDATES.md)
implement a separate developer API for an owned unloaded predecessor, independent
backup and explicit recovery. Public Apply, simple V1/V2 and conversion profiles
are unchanged. Next: LB-01 / PRESERVED_UPDATE_COORDINATOR; the worker/review/
consent/receipt integration for updates is not implied by this backend API.

2026-09-19: [preserved preparation](PRESERVED_PREPARATION_WORKFLOW.md)
connects the existing engine/inspector/admission store to developer forms,
progress/cancellation and reports. It requires a separate fresh install review
and confirmation. Headless implementation is delivered; no native acceptance or
public promotion is implied. Next: LB-05 / PRESERVED_MANAGED_UPDATES, owned and
unloaded preserved replacements with recoverable backup. The contract's cursor
supersedes historical "next" statements below.

2026-09-12: [cohort target preview](PRODUCT_COHORT_PREVIEW.md) implements
review of checked inputs, worker progress/cancellation and per-artifact evidence.
No cohort JAR/receipt/Apply. Native acceptance remains pending; next headless
development has delivered [FINITE_COHORT_DATA](FINITE_COHORT_DATA.md) for this
pair. Next is LB-02 / DATA-EXPAND / COMPOSITION_DATA_SCOPE for the individual
preview's data, not more launch harnesses or public Convert/Apply promotion.

2026-09-12: [PRODUCT-COMPOSED inspection slice](PRODUCT_COMPOSITION_INSPECTION.md)
implements typed preview/gates and a native inspection screen. It does not mark
the broader public composition/installation/native acceptance milestones done.

This plan implements the [in-game architecture](PRODUCT_ARCHITECTURE.md) without
widening `simple-content-v1`. Every later milestone keeps the earlier regression
suite and refuses unknown behavior. A checked contract is not a shipped UI.

2026-09-07 product correction: [DATA-SIMPLE-01](EMBEDDED_SIMPLE_CONTENT_DATA.md)
adds the finite simple-profile vocabulary to the same public artifact and the
shared service/UI default. Full local bundles remain development-only data;
historical milestone runtime claims below stay tied to their recorded hashes.

| Milestone | Deliverable | Implementation status |
|---|---|---|
| P1 | Storage/state/restart contracts, module boundary, source-audited architecture and acceptance plan | Implemented; build evidence in [P1 validation](PRODUCT_ARCHITECTURE_VALIDATION.md) |
| P2 | Typed reusable conversion service, neutral compatibility API, progress/cancellation and portable mapping inputs | Implemented and [engine/cacheless validated](ENGINE_SERVICE_VALIDATION.md); local-data redistribution remains a release gate |
| P3 | One public LegacyBridge JAR, common/client separation and explicit dependency packaging | Implemented; [ordinary final-JAR client/server checks passed](PACKAGING_VALIDATION.md); legal/data release gates remain |
| P4 | Actual title button, inventory screen, selection, bounded jobs and report/error UI | Native inventory, bounded batch queue and details implemented; see [P4 evidence and remaining gates](NATIVE_UI_VALIDATION.md) |
| P5 | User-authorized installation, crash recovery and validated restart/manual fallback | Bounded V2 batches, managed unloaded upgrades/backups, explicit resume/restore and safe manual close implemented; [P5 final-package evidence](TRANSACTIONAL_INSTALLATION_VALIDATION.md) passed; loaded updates, pre-discovery repair and automatic restart remain unsupported |
| P6 | Packaged end-to-end client/server acceptance and release documentation | [Public-only native workflow, next-launch fixture behavior and separate server checks passed](PUBLIC_ONLY_END_TO_END_VALIDATION.md); public-client kill mid-commit, fully offline networking, broad accessibility/modpack/launcher coverage and legal/data release acceptance remain open |

## P1 - executable architectural boundaries

- P1.1: Existing absolute instance directory yields exactly the five workspace
  areas outside `mods`, with no file/directory creation or enumeration.
- P1.2: Invalid/unsafe layouts reject; revalidation detects tested mutations.
  Document that the check is read-only and cannot eliminate TOCTOU races.
- P1.3: Workflow tests enforce analysis -> conversion -> validation -> explicit
  application -> pending restart; direct or same-launch activation rejects.
- P1.4: Cancellation after installation begins rejects; partial application is
  recovery-required, not reported cancelled or successfully activated.
- P1.5: Missing/mismatched restart-provider evidence selects a truthful manual
  fallback. The policy does not start processes or claim a provider exists.
- P1.6: Root build includes the JDK-only module, its tests and an archive/dependency
  boundary audit. Existing converter/runtime baseline tests still pass.
- P1.7: No runtime entrypoint, adapter binding, transformation, converted fixture
  content, Forge bootstrap service or conversion support category changes.

## P2 - reusable service and portable data

The unchanged JDK-only compatibility definitions now live in neutral
`legacy-compat-api`; the engine no longer consumes the Forge runtime's variant.
`LegacyEngineService` provides typed asynchronous operations and the CLI calls
that facade. P2 originally deferred the runtime-to-service/controller integration;
the P3/P4 staging slice below now supplies it.
Existing report/adapter contracts and `simple-content-v1` remain unchanged.

Implemented P2 boundaries:

- Typed inventory, analysis, mappings, non-publishing plan, conversion and
  explicit data preparation; requests/outcomes documented in the
  [service API](../ARCHITECTURE.md#reusable-service-api-p2).
- One worker per service, bounded requests/inventory, structured overload,
  progress and cooperative limits/cancellation. No render-thread integration
  or hard process/heap isolation is claimed.
- Planning executes the same proof and discards private bytes; conversion
  redoes the proof before no-replace publication. Cancellation is refused after
  the publication barrier, so a finished output is never mislabeled cancelled.
- Explicit profile/bundle selection, or an explicit environment bundle default.
  Unconfigured conversion fails with `MAPPING_DATA_REQUIRED`; development-cache
  access is a separately requested preparation action.
- Versioned, hashed, inert local mapping data with provenance and no embedded
  game/Forge implementation. Redistribution remains unapproved; this milestone
  supplies no public downloadable mapping pack and makes no hidden downloads.
- CLI logging backend separated from normal engine dependencies. No public
  combined JAR or automatic installation introduced.

Acceptance gates remain evidence requirements, not conclusions from the list above:

- P2.1: CLI and service produce equivalent reports and fixture output hashes.
- P2.2: Cancellation/time/resource limits close inputs and remove only owned
  temporaries; no partial result is published as ready to apply.
- P2.3: All source JAR markers, plugins and entrypoints remain inert data.
- P2.4: Mapping/API indexes record target namespace, versions, checksums and
  provenance; package only data approved for redistribution. No implicit network.
- P2.5: Conversion works outside the repository without its Gradle caches, or
  presents a precise explicit data-acquisition requirement. An offline claim
  requires an actual offline clean-cache test, not a warm developer machine.

## P3 - public artifact

The implementation now lives in `legacy-product`. The chosen
[source-audited packaging strategy](PACKAGING_ARCHITECTURE.md) is exact-platform
provided libraries plus a single unchanged Jar-in-Jar dependency, not broad
shading or private library isolation:

- Exact Minecraft 1.20.1 / Forge 47.4.23 / Java 17 metadata; host-provided ASM
  9.9.1, NightConfig 3.6.4 and logging with zero bundled copies.
- One nested `net.minecraftforge:srgutils:0.6.2`, negotiated with range
  `[0.6.2,0.6.3)`. Other nested implementations are prohibited.
- Own module classes combined at the root once; original `legacybridge_runtime`
  identity and `0.1.1-SNAPSHOT` adapter bindings preserved. No extra LegacyBridge
  installation, service/bootstrap hook or replacement game class.
- CLI classes/backend excluded from the player artifact; all development CLI
  commands retained in their separate application distribution.
- Public archive audit rejects fixtures, probe/harness classes, duplicate
  entries, private mapping payloads and unexpected library/provider metadata.

Production probes are separate test artifacts. Their existence does not mean
they have executed successfully. The public JAR is not approved for publication
while mapping-data redistribution and third-party licensing/corresponding-source
review remain open. The [included simple-content contract](EMBEDDED_SIMPLE_CONTENT_DATA.md)
removes local bundle preparation for the player profile. Broader development
profiles still need explicit full data; no full public bundle or hidden download is introduced.

Acceptance requirements, evaluated separately against the final artifact hash:

- P3.1: Exactly one mandatory public LegacyBridge artifact supplies
  `legacybridge_runtime`; preserve ABI and reject a duplicate old runtime.
- P3.2: Audit metadata, licenses, services, duplicate classes, ASM/logging
  conflicts and forbidden fixture/test/private/game files.
- P3.3: Final archive loads from an ordinary Forge instance without development
  source sets or extra internal LegacyBridge JARs.
- P3.4: The same archive loads on dedicated server without resolving screen or
  other `net.minecraft.client` classes. Record the exact tested Forge patch.

## P4 - menu workflow

The implemented vertical slice adds a physical-client native title button and
`ConversionScreen`, delegating to the existing service through
`ConversionController`. It inventories the reserved input directory, permits
up to 32 selected jobs per screen session, uses included simple-content data by default
with an optional advanced explicit local mapping directory,
and exposes per-job analysis/conversion progress, cancellation and result/error paths.
Background workers perform filesystem/engine/report operations; the native
screen polls immutable snapshots. Results are new operation-owned staging files,
not installed mods until a separate, explicit add-only Apply confirmation.
English/Portuguese labels are present; technical engine
diagnostics are not all translated.

There is no hot activation, replacement, automatic crash recovery or automatic
restart button. Full accessibility/input-method/DPI coverage remains manual;
the concrete automated UI matrix and evidence are recorded in the P4 document.
Do not mark P4 complete merely because a button and controller compile.

- P4.1: One localized button appears on the title screen; no duplicate after
  resize/reinitialization. Parent navigation, keyboard, focus and GUI scales work.
- P4.2: The screen scans only the designated input directory. Show source
  evidence, IDs, hashes, duplicates, invalid files and unsupported candidates;
  never infer support from filenames or risk score.
- P4.3: Selection and conversion are explicit. Bounded workers do not block the
  render thread. Progress refers to actual work and tolerates screen closure.
- P4.4: Corrupt/signed/unsupported/missing-data fixtures have readable diagnostics
  and detailed reports; one failed job does not falsely mark the batch successful.
- P4.5: Cancel before application leaves no installed file. Continue converting
  leaves installed and loaded mod sets unchanged; staged output is not active.
- P4.6: `en_us` and `pt_br` strings distinguish converted/ready/pending/activated
  from tested behavior, including restart unavailable and recovery required.

## P5 - installation and restart

Implementation: bounded per-file commits, immutable V2 journals, explicit
inspection/resume/restore, and upgrades of managed **unloaded** artifacts with
independent backups. The controller serializes installation with conversion;
its shutdown lease survives ordinary JVM exit. The native UI never calls stop
until its exact confirmed operation reports safe completion with no open world.
No launcher provider is enabled, no early recovery service is introduced, and
forced termination still relies on journal recovery. See
[implemented contracts](TRANSACTIONAL_INSTALLATION.md) and the
[evidence ledger](TRANSACTIONAL_INSTALLATION_VALIDATION.md); acceptance below is
not a claim that every failure mode or launcher has been tested.

- P5.1: Revalidate source/output hashes, bindings, target, dependency closure,
  duplicate mod IDs and canonical paths immediately before authorized writes.
- P5.2: Unknown destination, symlink/alias, changed input and locked active file
  cases cannot overwrite the original, runtime or unrelated mod.
- P5.3: Durable journal identifies owned files and completed operations; test
  disk full, permission loss, partial batch, interruption and repeated recovery.
- P5.4: Prove recovery timing relative to Forge discovery. If a safe mechanism
  for replacement/batch recovery is unavailable, refuse those operations instead
  of pretending an ordinary screen can fix an unloadable instance.
- P5.5: Continue does not install. Apply requires explicit selection and produces
  pending-restart status; the current launch never registers the new content.
- P5.6: Test each automatic restart provider against the exact launcher context,
  orderly shutdown and new-process handshake without copying secrets/command lines.
- P5.7: Without a proven provider, offer apply-and-close/manual relaunch, retain
  pending state and never claim automatic restart occurred.
- P5.8: A new launch checks exact discovery/binding/hash before observing
  activation. Missing/rejected mods stay diagnostic outcomes; a changed launch ID
  or process exit alone cannot establish activation or correct gameplay.

## P6 - production end-to-end proof

The [P6 execution record](PUBLIC_ONLY_END_TO_END_VALIDATION.md) now documents
the unchanged final JAR without a QA mod, Java agent or userdev classpath.
Native inventory/conversion, negative inputs, accepted cancellation and real
write denial/retry were exercised. Explicit Apply and close preceded a separate
client launch proving discovery, actual crafting/placement, assets and
English/Portuguese names; a separate ordinary server passed native content/save/exit checks using
the exact applied JAR. These observations do not claim a launcher restart.
Real separate-JVM halt/recovery tests remain separate: killing the public-only
Minecraft client mid-commit is untested. Full network isolation is uncertified;
licensing and broader coverage gates remain open. The later DATA-SIMPLE-01
correction removes explicit local mapping setup for the simple player profile;
it has its own final-artifact evidence, not an inherited P6 pass.

Use a disposable Forge instance, the final public artifact and original fixture:

1. Install only the public LegacyBridge JAR and open its title-screen interface.
2. Place the original Forge 1.16.5 fixture in input; discover and select it.
3. Convert via UI; verify report, staging output and preserved source SHA-256.
4. Continue with another job and prove the first result is not loaded/installed.
5. Explicitly apply; use a validated restart or the correctly labeled fallback.
6. On next launch verify discovery and the existing item/block/recipe/assets
   behavior probes using the exact final installed hashes.
7. Exercise malformed input, unsupported behavior, duplicates, cancellation,
   I/O failure and crash recovery, then repeat on a dedicated server as applicable.
8. Record real UI captures, game logs and unperformed manual/multiplayer gates.

Build, static validation, package discovery, actual behavior and restart are
separate entries in the evidence ledger. `runClient` using development source
sets is useful regression evidence, not final packaged-launcher proof. Do not
publish or upload artifacts automatically; prepare truthful experimental/Beta
documentation only after recording release prerequisites and remaining limits.

## Deferred, not implied by this plan

Interception of legacy JARs directly in `mods`, hot-loading, universal launcher
restart, new conversion categories and existing-world migration are excluded.
Any direct-`mods` experiment needs an independent loader audit and cannot run
legacy services or suppress Forge validation globally. Any future API support
retains fixture-backed semantic proof and the protected-class boundary.
