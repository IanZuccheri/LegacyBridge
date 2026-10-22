# P4 native conversion UI

Historical P4 evidence: the current P5 Apply flow supersedes the add-only UI
below. See [transactional installation](TRANSACTIONAL_INSTALLATION.md) and
[its artifact-bound validation](TRANSACTIONAL_INSTALLATION_VALIDATION.md).

Target: Minecraft 1.20.1, Forge **47.4.23**, Java 17. Source profile and adapter
ABI remain unchanged: 1.16.5 / Forge 36.2.42, `simple-content-v1`,
`legacybridge_runtime` 0.1.1-SNAPSHOT. No new conversion feature is enabled.

## Player workflow

1. While Minecraft is closed, put originals in `legacybridge/input` below the
   actual instance directory, **not in `mods`**. Install only `LegacyBridge.jar`
   as the bridge, not the standalone development runtime alongside it.
2. Open **Convert mods / Converter mods** from the native title screen.
   Refresh scans the immediate input directory; search filters the bounded
   in-memory list. Name, IDs/versions, detected source classification, bytes and
   SHA-256 are shown with scrollable details. No icons are decoded or loaded.
3. Select candidates with checkboxes/Space/Enter. Ctrl+A selects up to 32 matching
   candidates when the list is focused. Supply the explicitly prepared local
   mapping bundle path; a missing bundle is a readable refusal, not a download.
4. Convert queues selected observations. One engine operation executes at a
   time. Each result has its own state, summary and technical details. New
   inputs require Refresh; an input changed since inventory is refused.
5. After success, inspect CompatibilityReport, TransformationLog, conversion
   report or the result folder. Continue converting without changing installed
   or currently loaded mods. Clearing finished rows only forgets UI summaries.
6. **Apply** opens a native confirmation and runs the conservative
   [add-only installer](ADD_ONLY_INSTALLATION.md). Successful installation means
   `PENDING_RESTART`. Quit and restart manually through your launcher. There is
   no hot registration or unverified automatic restart button.

The title screen cannot rescue legacy JARs that already prevent Forge from
starting. Normal Forge discovery is not extended to `input`, `staging` or
`transactions`. There is no bootstrap/ModLauncher service.

## Classification and state semantics

Inventory classifications are distinct from operation results:

| Inventory | Meaning |
|---|---|
| CANDIDATE | Metadata and bytecode provide legacy source evidence; not proof the supported conversion grammar accepts it. |
| INCOMPATIBLE | Target/other/ambiguous source; filename and `.jar` suffix are not evidence of 1.16.5. |
| INVALID | Corrupt archive, incomplete metadata/bytecode or bounded-reader refusal. Size/hash are supplied only when a bounded regular-file read can prove them. |
| ALREADY_CONVERTED | Correlated runtime dependency, manifest profile/catalog and matching content/binding markers. This is not authentication or gameplay proof. |
| DUPLICATE | An inventoried mod ID occurs in more than one input JAR. Installed/loaded conflicts are separately checked during Apply. |

| Job state | Meaning |
|---|---|
| QUEUED / ANALYZING | Waiting, or actual static analysis running. |
| ANALYZED | Analysis finished; no conversion, installation or execution is implied. |
| BLOCKED | A source/profile/mapping prerequisite or supported-rule check refused this job. |
| CONVERTING | The static engine is running; progress reports real stage counts, not fabricated global percentages. |
| CONVERTED | Engine publication completed. Output is in staging, not certified to work in game. Missing required analysis/conversion/transformation reports disable Apply; job-receipt write failures remain explicit. |
| FAILED | Operational/input failure; other jobs and navigation remain usable. |
| CANCELLED | Cancellation won before conversion publication, or a queued job was removed before starting. |
| APPLYING | Explicit add-only filesystem operation in progress; it is intentionally not cancellable. |
| PENDING_RESTART | A complete file was added for later discovery. Current session remains unchanged. |

The publication boundary decides cancellation races: if publication wins, the
record remains CONVERTED even if closing/cancellation follows it. Closing the
main screen cancels waiting jobs and cooperatively cancels active conversion,
never waits on the render thread. Navigation to details/confirmation preserves
the same controller. Reopening is leased until its old workers stop. An accepted
Apply finishes its bounded installation even if the screen subsequently closes.

## Bounds and report ownership

- Inventory: at most 128 JAR candidates and 1,024 directory entries; truncation
  is explicit. No recursive input scanning. Existing bounded ZIP, TOML, ASM and
  resource limits remain in force. Converted markers capture at most 1 MiB per
  metadata entry and decode JSON as strict UTF-8.
- Per screen: 32 queued/running/retained jobs total, one coordinator, one engine
  worker. Path-opening has one separate worker and four queued requests.
- Engine defaults: 64 MiB input, 128 MiB inflated, 64 MiB captured and two-minute
  cooperative deadline per operation. These are byte/work bounds, **not** an OS
  heap sandbox or a promise to interrupt a blocked system call immediately.
- Search: 128 characters; explicit mapping path: 4,096; displayed untrusted text
  sanitized and capped at 512 code points; details at most 256 wrapped rows.
- Summary history does not retain full reports or bytecode. Reports and staging
  use generated operation directories and create-new publication. Existing
  files/originals are never overwritten. Queued jobs cancelled before execution
  may have no disk report because no operation started.
- CompatibilityReport and conversion report retain their existing schema 2;
  TransformationLog retains schema 1. New `LEGACYBRIDGE_JOB` receipts have schema
  1 and append outcomes rather than overwriting an earlier receipt.
- Finished jobs survive on disk as results/reports, not as automatically restored
  UI sessions. Reopen starts a fresh inventory; there is no automatic journal
  recovery/re-application or unlimited in-memory history. Disk evidence is
  intentionally retained for explicit review, not silently deleted as cache.
- Open actions validate on a background worker. The JAR itself is never opened
  via executable file association; only its containing folder is opened. Native
  `Util.OS.openFile` cannot confirm an external application displayed a window;
  the UI says the request was sent and provides copyable paths as a fallback.

## Acceptance and execution evidence

The [source-audited screen APIs](P4_UI_API_BASELINES.md) and
[official Forge screen documentation](https://docs.minecraftforge.net/en/1.20.1/gui/screens/)
define the target integration. Automated and runtime evidence is recorded below
only after execution; compiling a probe is not a passed scenario.

Executed 2026-09-05, serial builds and game processes, Java 17.0.12:

| Gate | Evidence |
|---|---|
| Full build/test | `build/p4-ui/build-global-errors.log`: all 54 tasks rerun, success; 466 tests, 465 passed, one converter symlink-permission skip, zero failures/errors. Includes wording, global-error regression and QA synchronization fixes. |
| Suite breakdown | Converter 358 (one skip), runtime 30, workflow 32, product 46. Product includes 17 retained controller tests, 8 queue/error tests and 21 installer tests. Converter adds 11 converted-marker tests. |
| Packaged audit | `verifyProductJar` passed: one runtime identity, unchanged adapter catalog, one unchanged SRG Utils JiJ, no duplicated platform/logging classes, fixtures, QA harness or private mapping payloads. Both language files have 77 matching keys and matching format placeholders. |
| Ordinary client | `build/p4-ui/client/logs/latest.log`: `LEGACYBRIDGE_P4_CLIENT_OK`; real `forgeclient` bootstrap, Portuguese UI, eight inert inputs, corrupt/multiple-ID/other-source/duplicate cases, real search text/Tab/Space, scales 1/2/3, one converted and one blocked job, report buttons, declined Apply without writes, confirmed Apply pending restart, cancel/close/reopen. 65 client ticks and 186 rendered frames during the tested conversion queue. |
| Cancel race | Cancel button was actually pressed. Original hashes remained unchanged, only the first positive JAR was published, no partial/cancelled output was installed, and the old controller stopped before reopening. The probe does not claim cancellation always wins every possible timing race. |
| Static output | The staged, applied and later-loaded fixture are byte-identical to the preserved baseline: SHA-256 `541cc64b44b97d0e4e79a193dd2ce3a86a5feba6e15714d0ee24ad63751492bb`, 6,979 bytes. Separate schema-versioned analysis/conversion/transformation JSON and installation receipts were inspected. |
| Later activation | Separate ordinary `forgeserver` bootstrap with the exact applied file: `Done` and `LEGACYBRIDGE_P4_ACTIVATION_OK`. Native item/block/BlockItem, stack size, hardness/resistance/instrument, tags/pickaxe speed and actual RecipeManager assembly passed; removing one ingredient correctly refused the recipe. This did not happen in the original client session. |
| Classloading | Final client/server classloading traces contain no class sources under input or `.gradle`. Dedicated-server trace contains no `dev.legacybridge.product.client` class. The private explicit mapping bundle was copied to the instance cache; empty private user homes and normal installed libraries were used, not a userdev classpath. |

Final public artifact: `legacy-product/build/libs/LegacyBridge.jar`, 886,836 bytes,
SHA-256 **`9096088487f54dbd341b280dcc81a31b5d539046e63bfb11d939589978ae64a0`**.
The client and server copies were hash checked against these bytes. The separate
QA-only JAR is **not a player dependency**; its final SHA-256 is
`e272f542b106c22a7a4c9cf07573d0b5f3be0766b31fd121f9cbae549203e6f5`.
Standalone runtime SHA-256 remains
`b03f9d0b888da7677f78d9063770cf401ad88b8c4b8a8a56752ed748adc9c428`.

The final applied file is
`legacybridge-legacybridge_example-70c8bb95-2079-457e-8304-4ad8d84fa3ce.jar`.
Its transaction has `schemaVersion=1`, `state=PENDING_RESTART` and the original
fixture hash `00dd689f53f9631ecf4a8e84da142eb21051e2ca33f1c05403e4060e15b10947`.
It was copied, hash verified, to the separate server instance for activation.
The original client loaded-mod snapshot stayed unchanged after Apply.

[Actual Portuguese UI capture](../build/p4-ui/client/screenshots/legacybridge-p4-pending-restart-fb2edf1a-31aa-4038-b7f4-832a27530bff.png)
was viewed after capture. It shows PENDING_RESTART, not gameplay proof. Resource
content bytes are unchanged; P4 does not claim a new visual in-world asset test
or a multiplayer/soak test of converted content.

### Reproduction and diagnostic history

`Prepare-ProductionClient.ps1 -ValidationRun p4-ui` verified 3,671 existing
artifacts including 3,575 asset objects, with zero new downloads. The isolated
normal installer libraries were copied from the previously verified P3 test
installation; no player launcher/profile/accounts were modified.

```powershell
# Prepare the explicit input and private local bundle first, outside mods.
# First-launch P4 scenario requires only the public JAR + separate QA harness in mods.
.\scripts\Start-ProductionClient.ps1 -ValidationRun p4-ui -JvmArgument @(
  '-Dlegacybridge.product.p4=true',
  '-Dlegacybridge.product.p4.timeoutSeconds=180',
  '-Xlog:class+load=info:file=client-classloading.log'
) -Launch
```

For later server validation, use the normal installed
`@libraries/net/minecraftforge/forge/1.20.1-47.4.23/win_args.txt` with Java 17,
`-Dlegacybridge.product.p4=true -Dlegacybridge.product.p4.relaunch=true` and the
**exact applied** file, on a separate loopback-only instance. The QA harness
stops each owned game process after its assertions. This is a development test,
not an implemented player restart provider.

Two earlier client attempts are preserved under `build/p4-ui/attempt-1` and
`attempt-2`. Both passed conversion and Apply, but the QA harness initially
pressed controls before the screen tick/title rendered-frame update. Its waits
were corrected; the public artifact bytes were unchanged. Their failures are
not counted as successful full runs. The third run passed the complete chain
and is archived under `attempt-3-success-before-global-error-fix`. Final review
then separated global inventory/workspace errors from historical job errors,
so a failed Refresh cannot be hidden by an older selected job. A regression
test proves visibility, successful retry and no contamination of that job.
The fourth client run passed the complete chain on the final public bytes;
the global-error details branch also has controller regression coverage, not
a separately claimed injected-filesystem-failure in-game scenario.

The first server bootstrap passed content activation but vanilla Yggdrasil's
key fetch logged a Java certificate-store PKIX failure. Evidence remains in
`server-first-start.log`. Repeating with process-scoped
`-Djavax.net.ssl.trustStore=NONE -Djavax.net.ssl.trustStoreType=Windows-ROOT`
passed again; certificate validation was not disabled and no global store was
changed. Normal Forge/vanilla initialization warnings are not hidden.

## Remaining limits

No universal conversion, new supported categories, replacement/update/uninstall,
automatic restart provider or automatic recovery. Installed inventories with
unverified nested dependency closures or unrecognized layouts are refused, not
guessed. See the installer document for exact filesystem and recovery limits.

en_us and pt_br cover player-facing controls and states; diagnostic codes,
engine explanations and some technical labels remain English. Full narrator
audio, IME, touch/controller, all DPI/window-resize/ultrawide combinations and
third-party menu replacements require separate manual QA. OS application
associations are environment-specific and not certified by a button test.

Explicit private local mapping/API data is still necessary. Licensing and
corresponding-source review remain public-release gates; this implementation
does not grant redistribution permission or claim CurseForge release clearance.
