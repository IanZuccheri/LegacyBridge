# In-game conversion product architecture

2026-09-19: [guarded CLIENT config](CLIENT_CONFIG_SCOPE.md) adds a separate typed
private proof/hash operation to the bounded engine. Source/target/readback guards,
native schema and post-load queue are independently checked. No client game APIs,
new public profile, CLI exporter, UI/Apply change or installation authority.
CLIENT snapshot is not config reload or client-server synchronization.

2026-09-19: [finite bound receiver inspection](OWNED_RECEIVER_HANDLES.md)
extends the shared engine through a private typed report/hash operation. The
worker rederives source/target/readback receiver/linkage proof and deletes scratch;
no public profile, CLI exporter, UI control or installation authority is added.

2026-09-19: [preserved update screen](PRESERVED_UPDATE_SCREEN.md) now connects
that coordinator to the existing developer-only native host: explicit predecessor
and incoming selection, observed versions/hashes, separate confirmation, recovery
and inert history. No public promotion, extra worker or new conversion grammar.
Native qualification remains NOT_RUN for this artifact.

2026-09-19: [preserved update coordination](PRESERVED_UPDATE_COORDINATOR.md) reuses
the bounded controller for two-sided review, separate one-use confirmation,
managed updates/recovery and inert history. This remains developer-only;
That checkpoint did not deliver native forms; the follow-up above does not imply
runtime acceptance or public promotion.

Latest 2026-09-12: [cohort target preview](PRODUCT_COHORT_PREVIEW.md) adds explicit
multi-input review and the shared finite private pair proof. No installable job,
public cohort profile or additional Apply authority. [Included finite cohort
data](FINITE_COHORT_DATA.md) now supports the exact pair with a blank data field;
explicit invalid data never falls back. Other advanced operations retain full
explicit data requirements.

2026-09-12 product follow-up: [finite composition inspection](PRODUCT_COMPOSITION_INSPECTION.md)
now connects the native screen/controller to the existing engine preview. It
produces a report only, never an installable job/JAR or public profile promotion.

Status: P1 contracts, P2 reusable engine, P3 single-artifact packaging, P4 native
inventory/queue/details UI and P5 transactional installation are implemented.
Recovery is explicit; upgrades are restricted to managed, unloaded artifacts.
No automatic startup recovery or automatic launcher restart is enabled.
See [current installation limits](TRANSACTIONAL_INSTALLATION.md),
[P5 validation](TRANSACTIONAL_INSTALLATION_VALIDATION.md),
[historical P4 evidence](NATIVE_UI_VALIDATION.md) and [historical P3 evidence](PACKAGING_VALIDATION.md).
`legacy-workflow` holds
the architectural guards; `legacy-converter` now exposes typed asynchronous
jobs and explicit portable mapping data. Runtime identity, adapter ABI,
existing report schemas and `simple-content-v1` acceptance grammar are unchanged.
Default simple conversion now uses [included finite contracts](EMBEDDED_SIMPLE_CONTENT_DATA.md).
Broad CLI mappings and development profiles require explicit full data, never
implicit cache discovery. See [implementation and acceptance gates](PRODUCT_IMPLEMENTATION_PLAN.md).

2026-09-19: the opt-in [preserved preparation workflow](PRESERVED_PREPARATION_WORKFLOW.md)
now connects original analysis/conversion or existing-candidate revalidation to
private admission through the shared worker. The separate developer review and
consent remain mandatory; neither readiness nor a report installs a mod. Native
qualification and public profile promotion remain pending.

## Product and release boundary

The product candidate is one mandatory **LegacyBridge.jar** installed in the
exact Forge 47.4.23 / Minecraft 1.20.1 instance's `mods` directory. It supplies
the existing runtime, conversion engine and client-only conversion interface.
Players do not need a CLI distribution or a separately installed compatibility-API JAR.
Input mods, generated mods, cache and reports are still separate files; "one JAR"
means one required LegacyBridge download, not one file for the whole modpack.

P3 now generates `legacy-product/build/libs/LegacyBridge.jar`; the CLI and
standalone runtime remain development artifacts. The internal workflow JAR is
**not** a mod to install. Licensing and approved mapping-data distribution remain
release gates. The player profile no longer needs a local mapping bundle;
the artifact is still not a rights-cleared CurseForge release.

Preserve `legacybridge_runtime` as the Forge mod ID and preserve all adapter
implementation packages, signatures, revisions and exact binding checks. A
display name or archive filename is not a replacement mod ID. Keep the current
`0.1.1-SNAPSHOT` binding intact through P3; any later ABI/version change needs an
explicit migration or reconversion policy. Installing the combined JAR
alongside the old runtime would duplicate the same mod ID. Pre-install checks
must prevent creating that conflict in operations controlled by the product;
release instructions must explain replacing the old runtime while the game is
closed. A profile that already contains both may fail before reaching the menu
and need correction outside the game. The UI is not guaranteed recovery for that
case, and must never silently delete or bypass either artifact.

The pinned source is 1.16.5 / Forge 36.2.42 and the target is 1.20.1 / Forge
47.4.23 / Java 17. Broader Forge 47.x testing is a separate release gate, not an
assumption from a metadata range.

## Internal modules, current and intended

| Component | Now | Intended consumer / responsibility |
|---|---|---|
| `legacy-converter` | Static engine, reports and typed asynchronous `LegacyEngineService`; CLI and product controller delegate to it | One conversion-rule implementation, no CLI `main` or stdout integration. |
| `legacy-runtime` | Existing Forge mod and concrete adapters | Common execution behavior; unchanged adapter ABI and supported profile. |
| `legacy-compat-api` | Neutral JDK-only catalog/descriptors/selection and offline operations, moved without changing packages/ABI | Both engine and runtime consume the same contracts without an engine-to-Forge dependency. Not a player mod. |
| `legacy-workflow` | Independent JDK-only path, state and restart-decision contracts | Shared vocabulary for the future controller/installer; no Forge classes, no discovery hooks and no install side effects. |
| `legacy-product` | Forge assembly, physical-client screens, neutral bounded controller, transactional installer and shutdown guard | One required player JAR; no fixtures or private mappings. |

Current integration (`legacy-workflow` remains independent of Forge):

```text
native client screens (legacy-product) --> controller --> service (legacy-converter)
               |
               +----------> legacy-workflow
legacy-runtime + legacy-converter --> neutral legacy-compat-api
```

P2 removes the runtime compatibility source set/consumable variant and moves
the same definitions into `legacy-compat-api`. The runtime packages that output
and uses it in development; the engine does not resolve the Forge runtime.
Both `legacy-runtime` and `legacy-product` use ForgeGradle. The engine's normal dependency variant
contains SLF4J API, not `slf4j-simple`; the latter is CLI-launcher/distribution
and test-only wiring. P3 verified embedding, ASM and module/classloader behavior
in the actual Forge artifact; P4 repeats runtime validation for its final bytes.
The CLI launcher is not a game entrypoint.

## Instance-owned storage

`gameDir` is supplied by the actual instance, not the process working directory,
user home, a global `.minecraft` assumption, or the project directory.
`BridgeWorkspace.inspect` validates a trusted, existing absolute game directory
read-only; constructing the contract creates nothing.

| Location below `gameDir` | Purpose | Rules |
|---|---|---|
| `legacybridge/input` | Original candidate JARs | Read-only inputs; never add to classpath, module path, Forge discovery or nested-JAR discovery. |
| `legacybridge/staging` | Private conversion candidates and validated outputs not installed | Per-job owned paths; unknown/failed/partial outputs cannot become selectable installations. |
| `legacybridge/reports` | Compatibility, mapping and transformation reports plus job summaries | Bounded inert data; report success is not activation or behavioral proof. |
| `legacybridge/cache` | Regenerable mapping/API indexes and other derived data | Key by original hash, engine/profile, mapping provenance, target namespace/version and adapter ABI. No originals or only copy of a result. |
| `legacybridge/transactions` | Immutable V2 plans/checkpoints, private payloads, independent upgrade backups and recovery evidence; V1 receipts retained | Never ordinary cache; no automatic cleanup of recovery evidence. |
| `mods` | Installed target mods including LegacyBridge | Installer-controlled writes only after explicit user selection and validation. No legacy input discovery hook. |

All five workspace areas are siblings outside `mods`. Existing unsafe aliases,
symlinks/junctions and file-instead-of-directory collisions must reject layout
validation. Read-only inspection is only a snapshot: future writers must
revalidate handles, ownership and paths at commit time. It is not a filesystem
sandbox and cannot eliminate concurrent path-replacement races.

Never use metadata strings, ZIP entries or a displayed filename as an unchecked
output path. Later job directories use generated identifiers. Inputs are opened
with the existing bounded snapshot reader; no recursive extraction or arbitrary
native/plugin/service execution is added. Clearing cache must not touch input,
staging, reports, transactions, installed mods or worlds.

## Discovery and menu lifecycle

The implemented physical-client hook adds **Converter mods** to the main title screen using
the target's actual screen initialization API. It opens a normal native screen,
keeps the parent screen and respects resize/reinitialization without duplicating
buttons. Do not replace the whole menu or initialize client classes on a
dedicated server. Exact API evidence belongs in [product API baselines](PRODUCT_API_BASELINES.md).

Opening the screen performs a bounded inventory of `legacybridge/input` and
offers refresh, selection and explicit conversion. Classification correlates
metadata and bytecode evidence; the extension, filename or permissive Forge
loader range alone does not prove 1.16.5 compatibility. Third-party input
service descriptors remain data, never executable extension points.

The normal Forge loader does not scan this new input folder, and LegacyBridge
must not register it. A legacy JAR placed directly in `mods` can make dependency
validation fail **before** the title screen is available. The menu is not an
early-loader recovery mechanism. Initial instructions must tell the player to
move their originals to `legacybridge/input` while the game is closed; P1 does
not automatically move, hide or quarantine anything.

No transformation service, locator, coremod or Mixin is necessary for this
input-folder product. Adding a service descriptor can affect the loader layer
in which its containing JAR is discovered; it is not a shortcut to in-menu
conversion. Direct-`mods` legacy interception is deferred to a separately
justified, source-audited experiment and must preserve all existing ownership
and non-execution rules.

## Phase separation and state contract

```text
DISCOVERED -> ANALYZING -> ANALYZED -> CONVERTING -> VALIDATING
                                                       |
                                                       v
             explicit Apply <-                   READY_TO_APPLY
                    |
                    v
                 APPLYING -> PENDING_RESTART
                    |                |
                    v                | a different launch + verified discovery
            RECOVERY_REQUIRED        v
                                 ACTIVATED
```

`ConversionWorkflow` is an immutable protocol guard, not an executor or a
durable transaction implementation. Its factory records the current launch
identity. Ordinary transitions cannot activate a mod; `observeActivated`
requires a different launch identity. The future trusted controller must verify
the exact installed hash, mod identity, runtime binding and actual discovery
before submitting that observation. A new UUID alone is **not** evidence that a
launcher restarted or Forge loaded anything.

| Phase | Operation / evidence | Does not imply |
|---|---|---|
| Analysis | Existing bounded inventory and independent reports | Authorization to transform or install |
| Conversion | Existing finite proof, ASM transformation and resource processing | Arbitrary legacy behavior is supported |
| Validation | Existing pre-emission bytecode/resource checks, followed by staged artifact/report/binding and install-preflight checks | Full JVM linkage, world safety or gameplay correctness |
| Installation | Explicit authorized transaction creates the validated target file in `mods` | Discovery in the already-running process |
| Activation | Next launch discovers the exact installed mod with required runtime | Correct behavior in every scenario |

The existing converter already validates candidate bytecode **before** writing
its output JAR. The planned `VALIDATING` workflow stage adds artifact-level
postconditions/preflight; it never authorizes writing malformed bytecode first.
Existing report schemas stay analyzer v2, mappings v1, conversion v2 and
TransformationLog v1. The P2 non-publishing `LEGACY_CONVERSION_PLAN` report has
its own schema 1. Future transaction envelopes reference those reports;
do not reinterpret `PLANNED` as a published output or `CONVERTED` as `ACTIVATED`.

Analysis, conversion and validation may end `BLOCKED`: the finite conversion
proof can discover unsupported behavior not established by analysis. Errors before installation become
`FAILED`; user cancellation is allowed only before `APPLYING`. An interrupted or
failed application is `RECOVERY_REQUIRED`, not a clean cancellation. A new job
is required for retry after terminal failure/block/cancellation. The enum alone
does not authorize an operation: successful transitions need actual evidence
from the relevant engine/installer adapter.

## UI, threading and results

P2 implements the service side, not the screen/controller side. The exact
public request/job/outcome contracts are listed in
[Reusable service API](../ARCHITECTURE.md#reusable-service-api-p2).
`inventory`, `analyze`, `mappings`, `plan`, `convert` and `prepareMappings` run
through one bounded worker per service. A long-lived service has a bounded
request queue; overload is `QUEUE_FULL`, not an unbounded allocation.

`plan` executes the existing proof and releases the private candidate without
publishing it. `convert` repeats that proof and publishes a complete validated
output only to its explicit requested path. P2 does not select an instance,
automatically create staging directories, install results or integrate
`ConversionWorkflow`; those are controller/installer responsibilities.

UI responsibilities: render bounded metadata, manage selection, show real phase
progress, preserve focus, request cancellation and show outcomes. Engine
responsibilities: execute pure/static work off the render thread with explicit
limits; close all handles; report typed progress and diagnostics. Controller
responsibilities: coordinate the two and marshal screen updates onto the client
thread, including safe handling when the player closes the screen.

Jobs expose the latest typed stage/counters, an asynchronous outcome and
cooperative cancellation. Unknown totals remain unknown. Payload/deadline limits
bound work but are not a hard JVM heap or wall-clock sandbox. Accepted
cancellation cannot cross the publication barrier; a cancellation requested
after that barrier returns false. `close()` is nonblocking and requests
cancellation without interrupting an active publication. See
[execution controls](ENGINE_EXECUTION_CONTROLS.md) for cleanup and latency limits.

Do not call CLI `main`, parse stdout as IPC, call `System.exit` from the engine,
register content, reload a classloader or rebuild game registries in the menu.
"Continue converting" keeps the launch's loaded-mod set unchanged. It may
create more staged results but cannot install them as a side effect.

Present independent outcomes per job, including unsupported features, missing
mapping data, malformed input, I/O errors and cancellation. No compatibility
percentage derived from risk score; no global success when only some jobs pass.
The player must be able to open the report and continue using the screen after
a job fails. `READY_TO_APPLY` is a validated conversion result, not a game test.

## Installation and recovery (P5)

Before applying, rehash source and staged output and check target/profile/ABI,
mod IDs, dependency closure, destination collisions, available space and path
ownership. The transaction records selected artifacts, fingerprints, allowed
destinations, prior state, operations completed and recovery requirements.
Do not trust an editable JSON report as authority to copy arbitrary files.

The implemented publisher uses a forced, validated private payload on the same
filesystem and a non-replacing hard link into `mods`, not an overwrite-capable
move. A forced immutable V2 plan precedes the first mod-set mutation. Per-file
checkpoints and revalidated identities/hashes describe partial commits.
Upgrades require a previous managed receipt and an independent validated backup;
loaded files/IDs, unknown ownership, unsupported dependency closure and unsafe
paths are refusals. Multiple JARs are never called one atomic transaction.

An interrupted recovery must be handled at a valid lifecycle point before the
affected installed set is loaded. An ordinary mod constructor may be too late.
The supported batch has independent generated mods; there is no bootstrap
recovery service. Recovery cannot promise to repair a profile that fails before
the menu, and cannot remove an already loaded result. See the exact restrictions
and recovery state table in [transactional installation](TRANSACTIONAL_INSTALLATION.md).
No automatic changes to worlds or migration of persisted mod data are included.

## Restart policy

Restart is separate from installation. Automatic restart is offered only for a
concrete provider with evidence tied to the exact launch environment and a
validated orderly shutdown/relaunch handshake. The pure `RestartPolicy` makes
that decision; it implements no restart provider and authenticates no caller
evidence. P5 still has no enabled restart integration.

Without such a provider, the honest action is **Apply and close - reopen through
your launcher**, not a successful restart animation. Preserve a pending outcome
across process exit and distinguish requested, started and observed activation.
Never reconstruct a launch by copying the full JVM command line. Do not log or
persist access tokens, account credentials, arbitrary launch arguments or shell
commands supplied by input metadata. Do not kill unrelated Java processes.

## Mapping portability and release limits

The service/CLI supports scoped included simple-content data as well as
explicit local profiles/portable bundles. `prepare-mappings` exports an explicit profile or, with
`--from-development-cache`, already prepared pinned development inputs.
`--mapping-bundle` or the explicit `LEGACYBRIDGE_MAPPING_BUNDLE` environment
configuration selects a bundle for consumption. Missing configuration defaults
only simple conversion to included data; broad operations still produce
`MAPPING_DATA_REQUIRED`. Explicit invalid data never triggers fallback.
No hidden cache lookup or network download occurs.

Bundles carry versioned inert mapping/API declarations, exact namespace/version
anchors, source provenance and payload hashes. They contain no old game/Forge
implementations. They are marked local-use only: public redistribution is not
approved by this milestone. The UI's advanced local path remains optional for
the included simple profile; a full bundle is never silently imported/downloaded. See
[portable mapping data](PORTABLE_MAPPING_DATA.md) for the licensing decision and
trust boundary. A complete offline claim requires separate artifact-bound,
network-disabled execution as well as clean-cache evidence; this architecture
document is not that evidence.

P2 service implementation does not certify a public combined artifact, button,
installer, crash recovery or restart. P3 must check ASM, logging and
module/classloader conflicts in a normal launcher installation. The
[acceptance plan](PRODUCT_IMPLEMENTATION_PLAN.md) remains the source of truth
for those later gates; historical fixture checks do not validate new packaging.
