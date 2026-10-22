# Validation

Continuation and planned acceptance gates are maintained in the
[canonical LB-00–LB-22 contract](docs/contract/README.md) and
[versioned status](docs/contract/status.json). Historical observations below
remain tied to their original artifacts; the planning/validator audit is
[recorded separately](docs/contract/AUDIT_2026-09-07.md).

Current headless continuation is LB-08 / COMPOSE-01:
[bound restricted settings proposals](docs/COMPOSITION_RESTRICTED_SETTINGS.md),
[finite native configuration review and necessary output inventory](docs/COMPOSITION_NATIVE_CONFIG_REVIEW.md),
[finite native output-layout policy](docs/COMPOSITION_NATIVE_OUTPUT_POLICY.md),
following [closed inert sessions and read-only stage admission](docs/COMPOSITION_INERT_SESSIONS.md).
Recognized opaque world/log/observation paths are not content validation. Only
the finite exact-default and separate restricted-value config gates validate
only their closed payload dialects;
native-default networking and unbounded native loader thread selection are not
launch approval. Necessary files being present is explicitly not successful output.
Restricted proposals preserve reviewed baseline properties but never change them.
Proposed server templates are not proof of native consumption or post-save content;
loopback and disabled Forge checks/LAN do not establish no-egress operation.
Actual-stage read-only checks, synthetic file tests, inert JVM exit/EOF/publication tests
and build/package evidence are separate from native **NOT_RUN**. There is no
approved native session or installation of the composed candidate. Follow the current
[checkpoint](docs/PROJECT_STATUS.md), not the historical "current" labels below.

The newest resource milestone has a separate
[datapack-recipes-v1 evidence ledger](docs/DATAPACK_RECIPES_VALIDATION.md).
Do not reuse historical client screenshots, machine behavior or multiplayer
claims for this opt-in recipe-data profile or its newly packaged artifact.

## Latest developer-profile evidence

[Food item validation](docs/FOOD_ITEM_VALIDATION.md) is the current item batch.
Its native endpoint comparison is separate from full use ticks, client visuals,
Forge finish-use events and multiplayer. The existing profile evidence below
stays tied to its own artifacts; no historical client result certifies the
current package.

[Lifecycle preservation validation](docs/LIFECYCLE_PRESERVING_VALIDATION.md) records
the new exact-artifact static and dedicated-server comparisons. Its runtime
probes are isolated QA mods; this is **not** a fresh public-only client/UI test.
The older P6/logic-profile receipts below remain historical and bound to their
own hashes. Neither their client evidence nor this step's server evidence may
be transferred automatically to a later public JAR.

## Evidence boundary

The [versioned corpus validation](docs/CORPUS_VALIDATION.md) adds a separate
development evidence gate: exact authorized real releases versus project fixture
cases, hash-bound static/runtime stages and a dependency-aware backlog. Corpus
checks are not new game executions or additional supported features.

LegacyBridge keeps compilation, static analysis, packaging, Forge startup, and
actual mod behavior as separate validation levels. Evidence from one level
must not be reported as proof of a later level.

Historical public-only evidence for the P6 artifact is [P6 end-to-end validation](docs/PUBLIC_ONLY_END_TO_END_VALIDATION.md):
that hash-bound public artifact passed native conversion/negative/cancel/
write-denial flows without a QA mod, explicit Apply and close, and a separately
launched client proving discovery, native crafting/placement, assets and
English/Portuguese names. A separate ordinary server loaded the exact applied
fixture and passed its native content/save/exit checks without QA. These are
distinct stages, not one inferred success or an automatic launcher restart.
Public-only client termination mid-commit remains untested; fully offline
networking and general launcher/modpack/accessibility coverage are not certified.
Mapping setup and licensing remain release gates.

P6 also reran the separate guarded Java 17 engine consumer: three canonical
CLI/service report pairs and the converted output matched, with zero forbidden
file/network/process/exit attempts and empty redirected caches. This standalone
engine test is not network-isolation evidence for the running Minecraft client.

The preceding [P5 transactional installation validation](docs/TRANSACTIONAL_INSTALLATION_VALIDATION.md) records:
502 tests (501 passed, one existing Windows symlink-privilege skip), ordinary
packaged-client transaction/recovery/manual-close flow, and a separate normal
dedicated-server fixture activation/content check, both using a separate QA mod.
Real Windows sharing-denial
and separate-JVM crash recovery were tested independently of the GUI. No
automatic restart provider, loaded-mod upgrade or universal power-loss guarantee
is implemented. P1/P2 and earlier runtime counts below are historical snapshots.

| Level | What it can prove | Current status |
|---|---|---|
| Gradle configuration | The internal modules can be configured with pinned dependencies | Required foundation gate |
| Java compilation | Source compiles for Java 17 | Required foundation gate |
| Unit tests | Tested record, CLI, and descriptor invariants hold | Required foundation gate |
| Package audit | Produced project JARs contain expected metadata and the exact enabled capability state | Automated for `legacy-runtime` and the combined `legacy-product` |
| Converter analysis | A legacy JAR was statically inventoried and reported | Implemented; security hardening remains |
| Symbol mappings | Requested symbols resolve against recorded local mappings/API declarations | Implemented; structural evidence only |
| Transformation verification | A proven graph becomes a target bootstrap/plan with exact adapter bindings and preserved source resources | Implemented for the narrowed `simple-content-v1` profile only |
| Forge discovery/startup | Forge discovers the converted fixture and matching runtime | Current 0.1.1 dedicated-server and client gates passed |
| Client/server behavior | A supported mod behaves correctly across real scenarios | Current fixture server registration/crafting/mining/instrument and client asset/tab checks passed; broader QA remains separate |

## Reusable engine and portable local data: P2

The P2 root suite had **409 tests: 408 passed, one native Windows symlink
test skipped, zero failures/errors**. CLI and typed asynchronous service produced
equal analysis/mapping/conversion JSON and identical fixture bytes in an isolated
installation with empty redirected caches, blocked external file/network access,
and a 512 MiB Java heap. Runtime and converted fixture hashes remain unchanged.
See [P2 evidence, exact artifacts and limitations](docs/ENGINE_SERVICE_VALIDATION.md).
This is engine/static validation, not a new game/UI or public combined-JAR test.

## Product architecture contracts: P1

The in-game product has [acceptance gates](docs/PRODUCT_IMPLEMENTATION_PLAN.md)
and [separate P1 build evidence](docs/PRODUCT_ARCHITECTURE_VALIDATION.md).
Workflow/path/restart-policy tests do not prove a title-screen button, portable
combined JAR, installation, recovery or automatic restart. None is enabled by P1.
The converter/runtime ABI and finite conversion profile are unchanged.

## ASM-pipeline game evidence: 0.1.1-SNAPSHOT

The root build passed **309 tests** (279 converter + 30 runtime), with no
failures, errors or skips. The new pipeline journals 35 changes for the genuine
fixture, verifies its candidate bytes offline and preserves all 12 accepted
source resources. Repeat conversion is byte/log deterministic. Fresh dedicated
server and client probes passed at 15:11:43 and 15:13:13 America/Sao_Paulo on
2026-09-05. See [exact artifacts, commands and limits](docs/ASM_PIPELINE_VALIDATION.md).

## Earlier compatibility-layer evidence: 0.1.1-SNAPSHOT before ASM pipeline

The root build passed **257 tests**: 227 converter and 30 runtime, with zero
failures, errors or skips. The reconverted original fixture carries ten exact
adapter bindings and twelve byte-preserved source resources. Its dedicated
server emitted `LEGACYBRIDGE_SERVER_VALIDATION_OK` on 2026-09-05 at 14:08:06
(America/Sao_Paulo), including `ironPickaxe=legacySpeed`, `instrument=BASEDRUM`,
three tags and the positive/negative crafting checks. The matching client emitted
`LEGACYBRIDGE_CLIENT_VALIDATION_OK` at 14:09:52, checking three baked models,
two atlas sprites, two languages, resolved sound and two creative tabs. Both
instances completed orderly shutdown and their Gradle marker checks passed.
See [current commands and artifact evidence](docs/COMPAT_LAYER_VALIDATION.md).

## Historical simple-content validation: 0.1.0-SNAPSHOT

On 2026-09-05, the prior 0.1.0 root build passed **224 tests** (214 converter, 10 runtime),
and the separately compiled Forge 1.16.5 fixture passed **4 tests**: **228 total,
zero failures and zero skips**. Target development client and dedicated-server
runs on Java 17 / Forge 47.4.23 also passed their explicit fixture markers and
completed orderly shutdown. This establishes the named example scenario, not
universal mod compatibility, multiplayer/relog coverage, or production-loader
parity beyond what the execution record documents.

Commands, artifacts, hashes and target-side checks are recorded separately in
[Simple-content validation](docs/SIMPLE_CONTENT_VALIDATION.md).

The current implementation is `0.1.1-SNAPSHOT`, with cataloged adapters, exact
bindings, narrower properties and a necessary STONE mining-tag transformation.
The above totals and game runs do not establish its current validation status.
Fresh results belong in the separate [compatibility-layer validation record](docs/COMPAT_LAYER_VALIDATION.md),
including hashes of reconverted input/output/runtime artifacts; the current
build/server results above are separate from that historical run.

## Foundation build gate

Use a 64-bit JDK 17 and run from the project root:

```powershell
.\gradlew.bat clean build --no-daemon --no-parallel
```

The root `build` lifecycle combines all three modules. Parallel execution is
disabled in project properties and should not be re-enabled for validation.

On the populated development environment, require real mapping verification:

```powershell
.\gradlew.bat -p fixtures/simple-content-1.16.5 clean build --no-daemon --no-parallel
.\gradlew.bat clean build -PverifyMappingEnvironment=true --no-daemon --no-parallel
```

The property enables `MappingEnvironmentIntegrationTest` and real source-fixture
conversion integration; missing/invalid local baselines or an unbuilt source
fixture fail those tests. Portable builds explicitly skip environment-specific
tests while retaining synthetic tests. Skips are not real-baseline evidence.

A successful command is necessary before accepting implementation changes,
but it does not authorize a runtime compatibility claim. Preserve
the complete command, exit code, Gradle version, Java version, and test
summaries when recording a build.

## Current automated-test scope

The scaffold tests cover foundation invariants including:

- help text exposes `analyze`, `mappings`, and strict `convert` command shapes;
- invalid command shapes return exit code `2`;
- a synthetic Forge-shaped fixture is analyzed to JSON on stdout;
- an existing report is preserved unless `--overwrite` is explicit;
- immutable and deterministically ordered report components;
- bounded archive reading, stable snapshots, canonical classic/ZIP64 preflight,
  unsafe-path rejection, and aggregate resource limits;
- Forge `mods.toml` shape validation, including IDs, duplicates, dependency
  ownership, explicit booleans, real ordering/side enums and defaults, and
  conservative numeric ranges;
- static ASM traversal and separate namespace definition/reference facts;
- raw JVM-header retention and candidate denial for unreadable classes;
- canonical JSON generation plus strict writer/schema root parity and structural
  validation against every schema keyword used by report v2;
- the installed application launcher starts with its complete dependency set;
- selected real registry API differences in the pinned Forge artifacts;
- synthetic official/TSRG mapping composition, immutable symbol/rule lookup,
  constructors, descriptors, aliases/conflicts, adapter/refusal policy, and
  no generated automatic rewrite approvals;
- mapping profile validation, bounded local input reads, declaration-only API
  indexing, source provenance, missing cache behavior, and mapping JSON CLI
  output;
- the finite static registration interpreter, production-SRG source aliases,
  item/block/sound plans, and rejection of unaccounted code or APIs;
- strict resource validation, preserved recipe/asset bytes, deterministic
  generated bootstrap/plan JARs, and refusal without an artifact;
- conversion metadata restrictions, manifest-injection refusal, existing-output
  and input-alias protection;
- shared JDK-only adapter contracts, exact catalog entrypoints, all five required
  documentation fields, fixture motivation and explicit unsupported areas;
- exact binding schema/version/IDs/revisions/method descriptors and coverage,
  with missing, tampered, duplicated, unknown or incomplete bindings rejected;
- narrowed item/material/tab policy, source vanilla-tag refusal, deterministic
  additive STONE mining tags and structured schema-v2 conversion traces;
- the runtime descriptor and typed capability catalog target Minecraft 1.20.1 /
  Forge major 47 with only `simple-content-v1` enabled and runtime bytecode
  transformations disabled;
- the processed runtime capability resource declares the same exact state.

Subset integration tests produce converted mod JARs and inspect their class and
resource structure. They do not launch Forge or prove item registration,
crafting, texture/model baking, sound loading, or broader gameplay behavior.
The separately built source Forge fixture has four source/resource tests;
its unconverted code is never executed by the offline converter.

## Package checks

After a successful build, the project artifacts should be audited without
launching Minecraft:

### `legacy-converter`

- the installed application launcher invokes
  `dev.legacybridge.converter.cli.LegacyBridgeCli` with its runtime dependencies;
- implementation version matches the project version;
- classes target Java 17;
- archive ordering and timestamps are reproducible;
- no Forge or Minecraft classes are bundled.

The distribution consumes `legacy-runtime-compat-api-0.1.1-SNAPSHOT.jar`, the
JDK-only shared/offline variant. It must not consume the Forge player JAR or
bundle game classes. The compatibility API artifact is not a player mod.

The plain module JAR is intentionally thin and is not advertised as a
standalone `java -jar` artifact. `legacy-converter:smokeInstalledCli`, wired
into `check`, executes `--help` through the installed launcher.

### `legacy-runtime`

- `META-INF/mods.toml` is present and placeholders are expanded;
- mod id is `legacybridge_runtime`;
- loader range is `[47,48)`;
- Forge dependency begins at the pinned `47.4.23` and remains below 48;
- Minecraft dependency is `[1.20.1,1.20.2)`;
- `RuntimeDescriptor` declares runtime transformations disabled and the sole
  aggregate `simple-content-v1` capability;
- the implementation catalog at `META-INF/legacybridge/adapters.json` contains
  11 exact operations, separate from the aggregate capability catalog;
- the full player JAR includes the shared contract and concrete runtime adapter
  classes, but never the validation harness;
- the project license is present under `META-INF`;
- classes target Java 17;
- the final reobfuscated JAR, rather than only an intermediate development JAR,
  is the artifact inspected.

`legacy-runtime:verifyRuntimeJar`, wired into that module's `check`, automates
the runtime entries, expanded resources, aggregate capability state, manifest
diagnostics, forbidden transformation infrastructure, and expected artifact
name. The remaining bytecode-major and reproducibility checks are review
requirements until separately automated.
Catalog/entrypoint and binding tests complement this package audit; no resource
listing by itself proves semantic equivalence or successful game registration.

`legacy-converter:verifyForgeApiBaselines`, also wired into `check`, validates
selected registry anchors against the exact 36.2.42 and 47.4.23 Forge
artifacts. It does not claim to compare the complete APIs.

The optional real mapping integration checks `ResourceLocation` across the
pinned official/MCP/Forge development inputs: class owner, `getPath` production
member names, public CODEC field, and constructors. It also checks the private
namespace field's raw name composition while requiring manual resolution for
its visibility. No part of that test loads Minecraft classes.

## Analyzer validation

The implemented test suite covers the core analyzer components separately:

- bounded valid-archive scanning, canonical ZIP64, central-directory mismatch,
  nonzero disk, prefixed layout, input mutation, and selected limit rejections;
- Forge metadata parsing, including missing and malformed metadata;
- classfile headers plus full ASM traversal of supported standard structures;
- separation and deterministic aggregation of namespace definitions and
  references, including bounded retention with honest occurrence counts and
  explicit truncation;
- immutable report ordering and canonical JSON serialization;
- selected real registry-API differences in the exact pinned Forge artifacts.

Remaining adversarial-hardening work includes:

- broader corpus coverage for duplicate names, Unicode/case collisions,
  encrypted entries, decompression bombs, and unusual valid ZIP producers;
- mutation/fuzz testing for truncated TOML, JSON, manifest, ZIP, and classfile
  structures under measured memory/time budgets;
- repeatability tests across locale, timezone, and working-directory changes
  (the input basename is intentionally part of the report);
- additional integration coverage for stderr/stdout isolation and every exit
  code.

Synthetic fixtures and expected findings are described in
[fixtures/README.md](fixtures/README.md). Third-party mods are never required
for the repeatable test suite.

## Analyzer exit codes

The CLI implements this process contract:

| Code | Meaning |
|---:|---|
| 0 | Analysis completed without findings at the failure threshold |
| 1 | Report produced, but it contains compatibility errors or blockers |
| 2 | Invalid or unavailable command/CLI usage |
| 3 | Missing, unreadable, malformed, or security-rejected input |
| 4 | Report-output failure |
| 70 | Unexpected internal software failure |

For `mappings`, code `1` means a report was produced with unresolved symbols,
missing required sources, unreadable classes, or truncated symbol inventory.
Code `0` means none of these review conditions was detected; `HIGH` structural
matches still do not authorize rewriting. Mapping-source/profile read errors
use code `3`. Full behavior and input limitations are in
[docs/MAPPINGS.md](docs/MAPPINGS.md).

For `convert`, code `0` means the strict profile produced an artifact and report;
code `1` means `ABORTED` / compatibility `UNSUPPORTED` with no artifact.
Successful reports contain structured transformations and exact bindings, not
an assertion of runtime success. Output JARs must not already exist;
`--overwrite` applies only to reports. Full syntax, profile restrictions and
conversion-report fields are in [Simple content](docs/SIMPLE_CONTENT.md).

Each branch must remain covered by CLI integration tests before release. Code
`0` will never mean "runnable on Forge 1.20.1."

## Runtime validation policy

The original foundation/analyzer tests deliberately never launched Minecraft.
The simple-content milestone adds a separate target Forge fixture-validation
workflow; it must use final converted/runtime artifacts and distinguish at least:

- clean Forge 1.20.1 dedicated-server discovery and startup;
- client discovery, menus, rendering, input, and resource reload where relevant;
- multiplayer with separate client/server state;
- relog and full process restart;
- failure behavior when `legacy-runtime` is missing or version-incompatible;
- exact adapter bindings before registration, and native pickaxe speed and
  note-block instrument behavior for the STONE fixture;
- feature-specific persistence, desync, duplication, and corruption tests.

None of those runtime gates can be replaced by a successful Gradle build.
Consult [Simple content](docs/SIMPLE_CONTENT.md) for fixture execution commands
and [the historical execution record](docs/SIMPLE_CONTENT_VALIDATION.md) for the
earlier discovery, registration, crafting and client-asset checks. The current
adapter-bound run belongs in [its own record](docs/COMPAT_LAYER_VALIDATION.md).
Multiplayer, relog, soak testing and arbitrary external mods are not implied.

## Documentation-only changes

Documentation edits do not establish a fresh build result. If Gradle is not run
in the same change, the handoff must say so and the next implementation step
must rerun the full foundation build before code work continues.
